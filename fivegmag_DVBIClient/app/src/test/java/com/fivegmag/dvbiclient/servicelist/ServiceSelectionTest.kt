/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.servicelist

import com.fivegmag.dvbiclient.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Instance selection on the service list fixtures, clause 5.2.13. */
class ServiceSelectionTest {

    private val list = ServiceListParser.parse(Fixtures.read("service-list-5g.xml"))
    private fun svc(suffix: String) = list.services.first { it.uid.endsWith(suffix) }
    private val noMbms = Instances.Capabilities()
    private val now = Instant.parse("2026-10-02T12:00:00Z").toEpochMilli()

    @Test
    fun hybridServicePlaysDashWhenThereIsNoMbmsClient() {
        val sel = ServiceSelection(svc(":hybrid"), noMbms)
        assertEquals(1, sel.select(now))
        assertTrue(sel.reasons(now)[0]!!.contains("needs an MBMS Client"))
        assertNull(sel.reasons(now)[1])
    }

    @Test
    fun hybridServiceTakesTheMbmsInstanceFirstWithAnMbmsClient() {
        val sel = ServiceSelection(svc(":hybrid"), noMbms.copy(mbms = true))
        assertEquals(0, sel.select(now))
        assertEquals("on error, the next instance by priority", 1, sel.fail(0, now))
        assertNull("nothing left", sel.fail(1, now))
    }

    @Test
    fun fiveGOnlyServicesHaveNothingToPlayAndSayWhy() {
        assertNull(ServiceSelection(svc(":5gonly"), noMbms).select(now))
        val bad = ServiceSelection(svc(":bad5g"), noMbms.copy(mbms = true))
        assertNull(bad.select(now))
        assertTrue(bad.reasons(now)[0]!!.contains("Receive-only Mode"))
    }

    @Test
    fun hlsByIdentifierBasedDeliveryParametersPlays() {
        assertEquals(0, ServiceSelection(svc(":hls-id"), noMbms).select(now))
    }

    @Test
    fun controllingApplicationIsDiscardedAndThePartTimeInstanceFollowsItsHours() {
        val sel = ServiceSelection(svc(":app"), noMbms)
        // Friday 2 October 2026, 18:30 UTC is in week 38 after the week of 5 January 2026: even.
        val onAir = Instant.parse("2026-10-02T18:30:00Z").toEpochMilli()
        assertEquals(1, sel.select(onAir))
        assertNull("12:00 is outside 18:00 to 02:00", sel.select(now))
        assertTrue(sel.reasons(now)[0]!!.contains("application controlling media presentation"))
        assertTrue(sel.reasons(now)[1]!!.contains("scheduled service hours"))
        assertEquals("re-evaluated when it comes on air", Instant.parse("2026-10-02T18:00:00Z").toEpochMilli(), sel.nextReevaluation(now))
    }
}
