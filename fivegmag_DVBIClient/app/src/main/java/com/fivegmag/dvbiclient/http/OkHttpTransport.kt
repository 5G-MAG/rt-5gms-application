/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.http

import com.fivegmag.dvbiclient.App
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * [HttpTransport] over OkHttp, without OkHttp's own cache: caching and conditional requests are
 * done by [DvbiHttpClient] as clause 4.3.2 describes. Redirects are followed (clause 4.3.3.6
 * judges the final status). Plain HTTP is refused by Android for hosts the network security
 * configuration does not list, which surfaces as a connection failure naming the host. Timeouts
 * are OkHttp's defaults; no DVB-I clause sets them.
 */
class OkHttpTransport(private val client: OkHttpClient = OkHttpClient()) : HttpTransport {

    override fun get(url: String, headers: Map<String, String>): HttpResponse {
        val builder = try {
            Request.Builder().url(url)
        } catch (e: IllegalArgumentException) {
            throw IOException("not an HTTP URL: $url", e)
        }
        builder.header("User-Agent", App.USER_AGENT)
        for ((k, v) in headers) builder.header(k, v)
        client.newCall(builder.build()).execute().use { res ->
            val h = LinkedHashMap<String, String>()
            for (name in res.headers.names()) res.header(name)?.let { h[name] = it }
            return HttpResponse(res.code, h, res.body?.string() ?: "")
        }
    }
}
