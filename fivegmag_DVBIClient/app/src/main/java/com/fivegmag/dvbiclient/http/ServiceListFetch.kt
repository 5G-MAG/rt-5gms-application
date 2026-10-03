/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.http

/**
 * Fetching a service list from the ServiceListURI elements of one offering, ETSI TS 103 770 V1.2.1
 * clause 4.3.3: after 401 or 403 (clause 4.3.3.3), 404 (clause 4.3.3.4), 500, 502, 504 or a
 * connection failure (clause 4.3.3.5), or a redirect ending in 4xx or 5xx (clause 4.3.3.6), "the
 * DVB-I client shall attempt to make a connection to the next listed URI". The other failures
 * (400 and 406 among them) go to the next URI as well, since the next URI is a different request.
 */
object ServiceListFetch {

    sealed class Outcome {
        /** [url] answered; [notModified] when the body held is unchanged (304, or fresh by max-age). */
        data class Fetched(val url: String, val body: String, val contentType: String, val notModified: Boolean) : Outcome()

        /** Every URI failed; one line per URI saying why. */
        data class Failed(val reasons: List<String>) : Outcome()
    }

    fun fetch(http: DvbiHttpClient, urls: List<String>): Outcome {
        val reasons = ArrayList<String>()
        for (url in urls) {
            val r = http.get(url)
            if (r.ok) return Outcome.Fetched(url, r.body, r.contentType, r.notModified)
            reasons.add("$url: ${describe(r)}")
        }
        return Outcome.Failed(reasons)
    }

    /** A short description of a failed result, for the user. */
    fun describe(r: HttpResult): String = when {
        r.skipped && r.final -> "not requested again after HTTP ${r.status} (clause 4.3.3.2)"
        r.skipped -> "not requested again yet (clause 4.3.3), waiting until ${r.retryAt}"
        r.status == 0 -> "connection failed: ${r.error ?: "unknown error"}"
        else -> "HTTP ${r.status}"
    }
}
