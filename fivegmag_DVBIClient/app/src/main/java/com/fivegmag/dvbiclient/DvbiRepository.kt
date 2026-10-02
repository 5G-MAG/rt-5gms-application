/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient

import android.os.Handler
import android.os.Looper
import com.fivegmag.dvbiclient.discovery.Discovery
import com.fivegmag.dvbiclient.discovery.Offering
import com.fivegmag.dvbiclient.guide.ContentGuide
import com.fivegmag.dvbiclient.http.DvbiHttpClient
import com.fivegmag.dvbiclient.http.OkHttpTransport
import com.fivegmag.dvbiclient.http.ServiceListFetch
import com.fivegmag.dvbiclient.servicelist.ServiceList
import com.fivegmag.dvbiclient.servicelist.ServiceListParser
import com.fivegmag.dvbiclient.xml.XmlFormatException
import java.util.concurrent.Executors

/**
 * Requests to DVB-I endpoints, off the main thread, through one [DvbiHttpClient] so that the rules
 * of ETSI TS 103 770 V1.2.1 clause 4.3 apply to all of them. Results are delivered on the main
 * thread.
 */
object DvbiRepository {

    val http = DvbiHttpClient(OkHttpTransport())
    val guide = ContentGuide(http)

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** The outcome of loading a service list. */
    data class ListResult(val list: ServiceList?, val url: String?, val unchanged: Boolean, val problems: List<String>)

    /** Runs [work] off the main thread and hands its result to [done] on the main thread. */
    fun <T> background(work: () -> T, done: (T) -> Unit) {
        executor.execute {
            val r = work()
            main.post { done(r) }
        }
    }

    /**
     * Fetches and parses the service list from [urls] (the next URI on failure, clause 4.3.3), and
     * checks its @id against [expectedId] (table 12, ServiceListId). A list unchanged since the
     * last load (304, or fresh by max-age) is kept as installed.
     */
    fun loadServiceList(urls: List<String>, expectedId: String, language: String, done: (ListResult) -> Unit) =
        background({
            when (val out = ServiceListFetch.fetch(http, urls)) {
                is ServiceListFetch.Outcome.Failed -> ListResult(null, null, false, out.reasons)
                is ServiceListFetch.Outcome.Fetched -> {
                    val installed = DvbiSession.serviceList
                    if (out.notModified && installed != null && DvbiSession.serviceListUrl == out.url) {
                        ListResult(installed, out.url, true, listOfNotNull(Discovery.idProblem(expectedId, installed.id)))
                    } else {
                        try {
                            val list = ServiceListParser.parse(out.body, language)
                            DvbiSession.serviceList = list
                            DvbiSession.serviceListUrl = out.url
                            ListResult(list, out.url, false, listOfNotNull(Discovery.idProblem(expectedId, list.id)))
                        } catch (e: XmlFormatException) {
                            ListResult(null, out.url, false, listOf("${out.url}: ${e.message}"))
                        }
                    }
                }
            }
        }, done)

    /** The outcome of a registry query: the offerings arranged for display, or why there are none. */
    data class RegistryResult(val url: String, val offerings: List<Offering>, val error: String?)

    /** Queries the registry (clause 5.1.3.2) and arranges the offerings (table 83 NOTE 2). */
    fun queryRegistry(endpoint: String, country: String, language: String, done: (RegistryResult) -> Unit) =
        background({
            val url = Discovery.queryUrl(endpoint, country)
            val r = http.get(url)
            if (!r.ok) {
                RegistryResult(url, emptyList(), ServiceListFetch.describe(r))
            } else {
                try {
                    RegistryResult(url, Discovery.arrange(Discovery.parse(r.body), country.ifEmpty { null }, language), null)
                } catch (e: XmlFormatException) {
                    RegistryResult(url, emptyList(), e.message)
                }
            }
        }, done)
}
