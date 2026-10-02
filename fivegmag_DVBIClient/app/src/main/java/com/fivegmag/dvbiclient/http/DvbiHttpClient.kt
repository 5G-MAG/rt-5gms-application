/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.http

import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/** A response as the transport returns it. Header names are matched without regard to case. */
class HttpResponse(val status: Int, headers: Map<String, String>, val body: String) {
    private val headers = headers.mapKeys { it.key.lowercase() }
    fun header(name: String): String? = headers[name.lowercase()]
}

/** Sends one GET. Throws [IOException] on a connection failure. Redirects are followed by the transport. */
fun interface HttpTransport {
    fun get(url: String, headers: Map<String, String>): HttpResponse
}

/** The outcome of [DvbiHttpClient.get]. */
data class HttpResult(
    val ok: Boolean,
    val status: Int,
    val body: String = "",
    val contentType: String = "",
    /** Answered from the local cache without a request, the response being fresh by its max-age. */
    val fromCache: Boolean = false,
    /** 304, or fresh in the cache: the cached body is unchanged. */
    val notModified: Boolean = false,
    /** After 400 or 406: this request is not sent again. */
    val final: Boolean = false,
    /** Not sent, because of an earlier answer (final, or a wait not yet over). */
    val skipped: Boolean = false,
    /** When the request may be sent again (ms since the epoch), or null. */
    val retryAt: Long? = null,
    val error: String? = null,
)

/**
 * HTTP behaviour towards DVB-I endpoints, ETSI TS 103 770 V1.2.1 clause 4.3 (and clauses 6.2.3 and
 * 6.2.4, which refer to it for content guide requests). Ported from the browser client
 * (rt-dvb-i-application public/dvbi-http.js).
 *
 * - 4.3.2.1: Cache-Control: max-age is honoured per response: a repeated request is answered from
 *   the local cache while the response is fresh, and no update is requested before it expires.
 * - 4.3.2.2: If-Modified-Since carries the Last-Modified time held for that document, and is
 *   omitted when none is held; a 304 keeps the cached body.
 * - 4.3.2.1 also has the client follow clause 7.3.2.6 of ETSI TS 102 796, which adds the
 *   If-None-Match header "where a server provides an ETag header": the ETag held for that
 *   document is sent as If-None-Match, and omitted when none is held.
 * - 4.3.3.2: after 400 or 406 the same request is not sent again.
 * - 4.3.3.3: after 401 or 403 the request is not sent again before the Retry-After period. How to
 *   re-authenticate is outside the scope of the clause, and this client has no credentials.
 * - 4.3.3.5: after 500, 502, 504 or a connection failure the request is not sent again before the
 *   back-off wait of clause 4.3.3.7.
 * - 4.3.3.6: a redirect that ends in 4xx or 5xx is a failure of the request; the transport follows
 *   redirects and the final status is the one acted on.
 *
 * The state is per request and held in memory only, so it starts empty when the application
 * starts (clause 4.3.3.7: "The retry count shall be reset when the device is powered off or
 * restarted and does not need to be persisted.").
 */
class DvbiHttpClient(
    private val transport: HttpTransport,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val random: () -> Double = { Random.nextDouble() },
) {
    private class Cached(
        val body: String,
        val contentType: String,
        var lastModified: String?,
        var etag: String?,
        var expiresAt: Long,
    )

    private class State(var final: Boolean = false, var notBefore: Long = 0, var retry: Int = 0, var status: Int = 0)

    private val cache = HashMap<String, Cached>()
    private val state = HashMap<String, State>()

    /** GET [url]. [key] names the request for the retry state (default: the URL). */
    @Synchronized
    fun get(url: String, key: String = url): HttpResult {
        state[key]?.let { s ->
            if (s.final) return HttpResult(ok = false, status = s.status, final = true, skipped = true)
            if (s.notBefore > now()) return HttpResult(ok = false, status = s.status, retryAt = s.notBefore, skipped = true)
        }

        val cached = cache[url]
        if (cached != null && cached.expiresAt > now()) {
            return HttpResult(ok = true, status = 200, body = cached.body, contentType = cached.contentType, fromCache = true, notModified = true)
        }

        val headers = LinkedHashMap<String, String>()
        cached?.lastModified?.let { headers["If-Modified-Since"] = it }
        cached?.etag?.let { headers["If-None-Match"] = it }

        val res = try {
            transport.get(url, headers)
        } catch (e: IOException) {
            // Connection failure, clause 4.3.3.5.
            failure(key, 0)
            return HttpResult(ok = false, status = 0, error = e.message ?: e.toString(), retryAt = backOff(key))
        }

        val maxAge = maxAgeMs(res.header("Cache-Control"))
        val expiresAt = if (maxAge != null) now() + maxAge else 0L

        if (res.status == 304 && cached != null) {
            cached.expiresAt = expiresAt
            res.header("Last-Modified")?.let { cached.lastModified = it }
            res.header("ETag")?.let { cached.etag = it }
            state.remove(key)
            return HttpResult(ok = true, status = 304, body = cached.body, contentType = cached.contentType, notModified = true)
        }

        val contentType = res.header("Content-Type") ?: ""
        if (res.status in 200..299) {
            cache[url] = Cached(res.body, contentType, res.header("Last-Modified"), res.header("ETag"), expiresAt)
            state.remove(key)
            return HttpResult(ok = true, status = res.status, body = res.body, contentType = contentType)
        }

        return when (res.status) {
            400, 406 -> {
                failure(key, res.status).final = true
                HttpResult(ok = false, status = res.status, body = res.body, contentType = contentType, final = true)
            }
            401, 403 -> {
                val s = failure(key, res.status)
                val wait = retryAfterMs(res.header("Retry-After"), now())
                if (wait != null) s.notBefore = now() + wait
                HttpResult(ok = false, status = res.status, body = res.body, contentType = contentType, retryAt = wait?.let { now() + it })
            }
            500, 502, 504 -> {
                failure(key, res.status)
                HttpResult(ok = false, status = res.status, body = res.body, contentType = contentType, retryAt = backOff(key))
            }
            else -> {
                failure(key, res.status)
                HttpResult(ok = false, status = res.status, body = res.body, contentType = contentType)
            }
        }
    }

    private fun failure(key: String, status: Int): State {
        val s = state.getOrPut(key) { State() }
        s.status = status
        return s
    }

    /**
     * Applies the back-off of clause 4.3.3.7 to [key]: the next request waits a random period
     * between minwait and maxwait for the current retry count. Returns when it may be sent.
     */
    @Synchronized
    fun backOff(key: String): Long {
        val s = state.getOrPut(key) { State() }
        s.retry = minOf(s.retry + 1, BACKOFF_MAX_RETRY)
        s.notBefore = now() + backoffDelay(s.retry, random)
        return s.notBefore
    }

    /** Milliseconds until the cached response for [url] expires; 0 when it has no max-age or is stale. */
    @Synchronized
    fun freshFor(url: String): Long = maxOf(0L, (cache[url]?.expiresAt ?: 0L) - now())

    /** When the request named [key] may next be sent (0: now), or [Long.MAX_VALUE] after a 400 or 406. */
    @Synchronized
    fun nextAllowed(key: String): Long {
        val s = state[key] ?: return 0
        return if (s.final) Long.MAX_VALUE else s.notBefore
    }

    companion object {
        /** Clause 4.3.3.7: minwait is 4 to the power CurrentRetry-1 times 100 ms, CurrentRetry "up to a maximum value of 10". */
        const val BACKOFF_UNIT_MS = 100L
        const val BACKOFF_MAX_RETRY = 10

        /** Bounds (minwait, maxwait) of the random wait before a retry, clause 4.3.3.7. [retry] is CurrentRetry. */
        fun backoffRange(retry: Int): Pair<Long, Long> {
            val r = retry.coerceIn(1, BACKOFF_MAX_RETRY)
            var min = BACKOFF_UNIT_MS
            repeat(r - 1) { min *= 4 }
            return min to min * 4
        }

        fun backoffDelay(retry: Int, random: () -> Double = { Random.nextDouble() }): Long {
            val (min, max) = backoffRange(retry)
            return min + (random() * (max - min)).toLong()
        }

        /**
         * max-age of a Cache-Control response header in milliseconds, or null when absent. RFC 9111
         * clause 5.2.2.1: "This directive uses the token form of the argument syntax", so the quoted
         * form is ignored; a value too large to hold is taken as 2147483648 seconds (clause 1.2.2).
         */
        fun maxAgeMs(cacheControl: String?): Long? {
            if (cacheControl == null) return null
            for (part in cacheControl.split(',')) {
                val m = Regex("^max-age=(\\d+)$", RegexOption.IGNORE_CASE).find(part.trim()) ?: continue
                val seconds = m.groupValues[1].toBigInteger().min(2_147_483_648L.toBigInteger()).toLong()
                return seconds * 1000
            }
            return null
        }

        /** Retry-After in milliseconds from [nowMs], or null. RFC 9110 clause 10.2.3: "Retry-After = HTTP-date / delay-seconds". */
        fun retryAfterMs(value: String?, nowMs: Long): Long? {
            val v = value?.trim() ?: return null
            // A delay too large to hold in milliseconds is not a usable value, and is ignored.
            if (v.matches(Regex("^\\d+$"))) return v.toLongOrNull()?.takeIf { it <= Long.MAX_VALUE / 1000 }?.times(1000)
            return try {
                val t = ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
                maxOf(0L, t - nowMs)
            } catch (_: Exception) {
                null
            }
        }
    }
}
