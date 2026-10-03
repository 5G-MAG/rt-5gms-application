/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.mbms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Ported from rt-dvb-i-application 000e460 test/mbms-url.test.js. */
class MbmsUrlTest {

    @Test
    fun theFormsOfTs26347Clauses8_2_3And8_2_4AreValid() {
        for (u in listOf(
            "mbms://example.com/userservice/1",
            "mbms://www.example.com/",
            "mbms://service1000.mbms.operator.com&label=http://www.example.com/videos/sample.mp4",
            "mbms://rom.3gpp.org&tmgi=901056&serviceArea=40201&frequency=68616&subCarrierSpacing=1.25&bandwidth=8",
            "mbms://rom.3gpp.org&serviceArea=40201&frequency=68616&subCarrierSpacing=1.25&bandwidth=8&serviceId=%22television-service%22",
        )) assertNull(u, MbmsUrl.problem(u))
    }

    @Test
    fun whatClause8_2_2DoesNotAllowIsReported() {
        for (u in listOf(
            "urn:3gpp:mbms:service:hybrid",
            "https://example.com/manifest.mpd",
            "mbms://",
            "mbms://example.com/a?x=1",
            "mbms://example.com&foo=1",
            "mbms://example.com&label=not a uri",
            "mbms://example.com/a b",
            "mbms://exa mple.com",
            "mbms://example.com#f",
            "",
            null,
        )) assertNotNull(u, MbmsUrl.problem(u))
    }

    @Test
    fun theServiceIdIsThePartBeforeTheFirstAmpersand() {
        assertEquals("mbms://service1000.mbms.operator.com", MbmsUrl.serviceId("mbms://service1000.mbms.operator.com&label=http://www.example.com/v.mp4"))
        assertEquals("mbms://example.com/userservice/1", MbmsUrl.serviceId("mbms://example.com/userservice/1"))
    }

    @Test
    fun theEntryPointOfTheNamedUserServiceIsPassedToThePlayer() {
        val services = listOf(
            IMbmsStreamingClient.StreamingServiceInfo("mbms://other.example.com", IMbmsStreamingClient.DVBI_SERVICE_INSTANCE_CLASS,
                listOf("application/dash+xml" to "http://localhost/other.mpd")),
            IMbmsStreamingClient.StreamingServiceInfo("mbms://service1000.mbms.operator.com", IMbmsStreamingClient.DVBI_SERVICE_INSTANCE_CLASS,
                listOf("application/vnd.apple.mpegurl" to "http://localhost/s.m3u8", "application/dash+xml" to "http://localhost/s.mpd")),
        )
        assertEquals("application/dash+xml" to "http://localhost/s.mpd",
            MbmsReception.entryPoint(services, "mbms://service1000.mbms.operator.com&label=http://www.example.com/hybrid.mpd"))
        assertNull(MbmsReception.entryPoint(services, "mbms://absent.example.com"))
    }

    @Test
    fun withoutAnMbmsClientRegistrationFails() {
        var answer: Boolean? = null
        NoMbmsClient.registerStreamingApp("app", listOf(IMbmsStreamingClient.DVBI_SERVICE_INSTANCE_CLASS), object : IMbmsStreamingClient.Callback {
            override fun registerStreamingResponse(success: Boolean, message: String) { answer = success }
            override fun streamingServiceListUpdate() {}
            override fun serviceStarted(serviceId: String) {}
            override fun streamingServiceError(serviceId: String, message: String) {}
        })
        assertEquals(false, answer)
    }
}
