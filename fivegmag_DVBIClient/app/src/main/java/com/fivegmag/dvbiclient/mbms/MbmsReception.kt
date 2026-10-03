/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.mbms

/**
 * The DVB-I side of receiving a 5G Broadcast service instance, ETSI TS 103 770 V1.2.1 clause 9.3.3:
 * "The application entry point document (e.g. DVB-DASH Media Presentation Description) referenced
 * by the MBMS User Service Description shall be passed to the media player".
 */
object MbmsReception {

    /**
     * The entry point (ServiceMimeType, ManifestURI) of the MBMS User Service that [locator] names,
     * among [services] from getStreamingServices(), or null. The service is found by its serviceId,
     * which is the locator's prefix (TS 26.347 V18.1.0 clause 8.2.2). A DASH MPD is preferred to
     * other formats.
     */
    fun entryPoint(services: List<IMbmsStreamingClient.StreamingServiceInfo>, locator: String): Pair<String, String>? {
        val id = MbmsUrl.serviceId(locator)
        val svc = services.firstOrNull { it.serviceId == id } ?: return null
        return svc.formats.firstOrNull { it.first == "application/dash+xml" } ?: svc.formats.firstOrNull()
    }
}
