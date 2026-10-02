/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Ported from rt-dvb-i-application test/dvbi-http.test.js: a fake transport and a fake clock. */
class DvbiHttpClientTest {

    private class Queued(val status: Int = 200, val headers: Map<String, String> = emptyMap(), val body: String = "", val throws: String? = null)

    private class FakeTransport(vararg responses: Queued) : HttpTransport {
        val queue = ArrayDeque(responses.toList())
        val calls = ArrayList<Pair<String, Map<String, String>>>()
        override fun get(url: String, headers: Map<String, String>): HttpResponse {
            calls.add(url to headers)
            val r = queue.removeFirstOrNull() ?: throw AssertionError("no response queued")
            if (r.throws != null) throw IOException(r.throws)
            return HttpResponse(r.status, r.headers, r.body)
        }
    }

    private class Clock(var t: Long = 1_000_000) {
        fun advance(ms: Long) { t += ms }
    }

    @Test
    fun clause4_3_3_7MinwaitAndMaxwaitForEachRetryCappedAtTheTenth() {
        assertEquals(100L to 400L, DvbiHttpClient.backoffRange(1))        // "up to 400ms before the first retry"
        assertEquals(400L to 1600L, DvbiHttpClient.backoffRange(2))       // "up to 1 600 ms before the second"
        assertEquals(26_214_400L to 104_857_600L, DvbiHttpClient.backoffRange(10)) // "104 857 600 ms"
        assertEquals("CurrentRetry is not incremented past 10", DvbiHttpClient.backoffRange(10), DvbiHttpClient.backoffRange(11))
    }

    @Test
    fun clause4_3_3_7TheWaitIsRandomBetweenMinwaitAndMaxwait() {
        assertEquals(1600L, DvbiHttpClient.backoffDelay(3) { 0.0 })
        assertEquals(1600L + 2400L, DvbiHttpClient.backoffDelay(3) { 0.5 })
        assertTrue(DvbiHttpClient.backoffDelay(3) { 0.999999 } < 6400)
    }

    @Test
    fun maxAgeAndRetryAfterAreParsed() {
        assertEquals(3_600_000L, DvbiHttpClient.maxAgeMs("max-age=3600"))
        assertEquals(5000L, DvbiHttpClient.maxAgeMs("public, max-age=5"))
        assertNull(DvbiHttpClient.maxAgeMs("no-cache"))
        assertNull("the quoted form is not generated, and not accepted", DvbiHttpClient.maxAgeMs("max-age=\"5\""))
        assertNull(DvbiHttpClient.maxAgeMs(null))
        assertEquals("RFC 9111 clause 1.2.2: too large is 2^31 seconds", 2_147_483_648_000L, DvbiHttpClient.maxAgeMs("max-age=99999999999999999999"))
        assertEquals(120_000L, DvbiHttpClient.retryAfterMs("120", 0))
        val now = ZonedDateTime.parse("Fri, 31 Dec 1999 23:58:59 GMT", DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        assertEquals(60_000L, DvbiHttpClient.retryAfterMs("Fri, 31 Dec 1999 23:59:59 GMT", now))
        assertNull(DvbiHttpClient.retryAfterMs("soon", 0))
    }

    @Test
    fun clause4_3_2_2IfModifiedSinceIsOmittedWithoutALastModifiedTimeThenSentWithIt() {
        val lm = "Wed, 19 Jun 2019 19:43:31 GMT"
        val f = FakeTransport(Queued(200, mapOf("Last-Modified" to lm), "<a/>"), Queued(304))
        val c = DvbiHttpClient(f)
        assertEquals(200, c.get("https://sl.example/list.xml").status)
        assertNull(f.calls[0].second["If-Modified-Since"])
        val second = c.get("https://sl.example/list.xml")
        assertEquals(lm, f.calls[1].second["If-Modified-Since"])
        assertEquals(304, second.status)
        assertTrue(second.notModified)
        assertEquals("a 304 keeps the cached body", "<a/>", second.body)
    }

    @Test
    fun ts102796Clause7_3_2_6IfNoneMatchIsOmittedWithoutAnETagThenSentWithIt() {
        val f = FakeTransport(
            Queued(200, body = "<a/>"),
            Queued(200, mapOf("ETag" to "\"v1\""), "<a/>"),
            Queued(304, mapOf("ETag" to "\"v2\"")),
            Queued(304),
        )
        val c = DvbiHttpClient(f)
        c.get("https://sl.example/list.xml")
        assertNull("none held: omitted", f.calls[0].second["If-None-Match"])
        c.get("https://sl.example/list.xml")
        assertNull("the first response had no ETag", f.calls[1].second["If-None-Match"])
        val third = c.get("https://sl.example/list.xml")
        assertEquals("\"v1\"", f.calls[2].second["If-None-Match"])
        assertTrue(third.notModified)
        assertEquals("<a/>", third.body)
        c.get("https://sl.example/list.xml")
        assertEquals("an ETag on a 304 replaces the one held", "\"v2\"", f.calls[3].second["If-None-Match"])
    }

    @Test
    fun clause4_3_2_1NoRequestWhileMaxAgeHasNotPassedAndTheHeaderIsReadOnEveryResponse() {
        val t = Clock()
        val f = FakeTransport(
            Queued(200, mapOf("Cache-Control" to "max-age=60"), "v1"),
            Queued(200, mapOf("cache-control" to "max-age=10"), "v2"),
            Queued(200, body = "v3"),
        )
        val c = DvbiHttpClient(f, now = { t.t })
        c.get("https://cg.example/s")
        t.advance(59_000)
        val cached = c.get("https://cg.example/s")
        assertEquals("answered from the local cache while fresh", 1, f.calls.size)
        assertTrue(cached.fromCache)
        assertEquals("v1", cached.body)
        assertEquals(1000L, c.freshFor("https://cg.example/s"))
        t.advance(1000)
        assertEquals("requested once expired", "v2", c.get("https://cg.example/s").body)
        t.advance(10_000)
        assertEquals("the shorter max-age of the second response applies", "v3", c.get("https://cg.example/s").body)
        assertEquals(3, f.calls.size)
    }

    @Test
    fun clause4_3_3_2After400Or406TheSameRequestIsNotSentAgain() {
        for (status in listOf(400, 406)) {
            val f = FakeTransport(Queued(status))
            val c = DvbiHttpClient(f)
            assertTrue(c.get("https://cg.example/bad").final)
            val again = c.get("https://cg.example/bad")
            assertFalse(again.ok)
            assertTrue(again.skipped)
            assertEquals("nothing sent after $status", 1, f.calls.size)
            assertEquals(Long.MAX_VALUE, c.nextAllowed("https://cg.example/bad"))
        }
    }

    @Test
    fun clause4_3_3_3After401Or403TheRequestWaitsForRetryAfter() {
        val t = Clock()
        val f = FakeTransport(Queued(401, mapOf("Retry-After" to "120")), Queued(200, body = "ok"))
        val c = DvbiHttpClient(f, now = { t.t })
        val r = c.get("https://sl.example/private.xml")
        assertEquals(401, r.status)
        assertEquals(t.t + 120_000, r.retryAt)
        t.advance(119_000)
        assertTrue(c.get("https://sl.example/private.xml").skipped)
        assertEquals("not sent before Retry-After", 1, f.calls.size)
        t.advance(1000)
        assertEquals("ok", c.get("https://sl.example/private.xml").body)

        val f403 = FakeTransport(Queued(403), Queued(403))
        val c403 = DvbiHttpClient(f403, now = { t.t })
        c403.get("https://sl.example/x")
        c403.get("https://sl.example/x")
        assertEquals("without Retry-After nothing holds the request back", 2, f403.calls.size)
    }

    @Test
    fun clause4_3_3_5ServerErrorsAndConnectionFailureRetryNoFasterThanTheBackOff() {
        for (q in listOf(Queued(500), Queued(502), Queued(504), Queued(throws = "ECONNREFUSED"))) {
            val t = Clock()
            val f = FakeTransport(q, q, Queued(200, body = "ok"))
            val c = DvbiHttpClient(f, now = { t.t }, random = { 1.0 })
            assertEquals("first retry within 100 to 400 ms", t.t + 400, c.get("https://sl.example/list.xml").retryAt)
            t.advance(399)
            assertTrue(c.get("https://sl.example/list.xml").skipped)
            t.advance(1)
            assertEquals("second retry within 400 to 1 600 ms", t.t + 1600, c.get("https://sl.example/list.xml").retryAt)
            t.advance(1600)
            assertEquals("ok", c.get("https://sl.example/list.xml").body)
            assertEquals(3, f.calls.size)
        }
    }

    @Test
    fun clause4_3_3_7BackOffAppliedOnRequestByTheCallerCountedPerRequestKey() {
        val t = Clock()
        val c = DvbiHttpClient(FakeTransport(), now = { t.t }, random = { 0.0 })
        assertEquals(t.t + 100, c.backOff("cg|svc"))
        assertEquals(t.t + 400, c.backOff("cg|svc"))
        assertEquals("another request has its own count", t.t + 100, c.backOff("other"))
        repeat(20) { c.backOff("cg|svc") }
        assertEquals("held at CurrentRetry 10", t.t + 26_214_400, c.nextAllowed("cg|svc"))
    }

    @Test
    fun clause4_3_3ServiceListFetchTriesTheNextListedUri() {
        val f = FakeTransport(Queued(404), Queued(throws = "ECONNREFUSED"), Queued(200, mapOf("Content-Type" to "application/vnd.dvb.dvbisl+xml"), "<ServiceList/>"))
        val out = ServiceListFetch.fetch(DvbiHttpClient(f), listOf("http://a/1.xml", "http://b/2.xml", "http://c/3.xml"))
        assertEquals(ServiceListFetch.Outcome.Fetched("http://c/3.xml", "<ServiceList/>", "application/vnd.dvb.dvbisl+xml", false), out)
        val failed = ServiceListFetch.fetch(DvbiHttpClient(FakeTransport(Queued(403), Queued(500))), listOf("http://a/1.xml", "http://b/2.xml"))
        assertTrue(failed is ServiceListFetch.Outcome.Failed)
        assertEquals(listOf("http://a/1.xml: HTTP 403", "http://b/2.xml: HTTP 500"), (failed as ServiceListFetch.Outcome.Failed).reasons)
    }
}
