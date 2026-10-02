/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.servicelist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Ported from rt-dvb-i-application test/instances.test.js. */
class InstancesTest {

    private val h = 3_600_000L
    private val d = 24 * h
    private fun t(s: String) = Instant.parse(s).toEpochMilli()
    private fun iv(days: List<Int> = (1..7).toList(), recurrence: Int = 1, recurrenceGiven: Boolean = false, start: Long = 0, end: Long = d - 1) =
        Interval(days, recurrence, recurrenceGiven, start, end)
    private fun period(validFrom: Long? = null, validTo: Long? = null, intervals: List<Interval> = emptyList()) = Period(validFrom, validTo, intervals)

    @Test
    fun noAvailabilityElementAlwaysAvailable() {
        assertTrue(Instances.isAvailable(null, t("2026-10-02T12:00:00Z")))
        assertNull(Instances.nextChange(null, 0))
    }

    @Test
    fun clause5_2_5_2ExampleOnAirOnlyInJulyAndSeptember2019() {
        val av = Availability(listOf(
            period(t("2019-07-01T00:00:00Z"), t("2019-07-31T23:59:59Z")),
            period(t("2019-09-01T00:00:00Z"), t("2019-09-30T23:59:59Z")),
        ))
        assertTrue(Instances.isAvailable(av, t("2019-07-15T10:00:00Z")))
        assertFalse(Instances.isAvailable(av, t("2019-08-15T10:00:00Z")))
        assertTrue(Instances.isAvailable(av, t("2019-09-15T10:00:00Z")))
        assertEquals(t("2019-09-01T00:00:00Z"), Instances.nextChange(av, t("2019-08-15T10:00:00Z")))
    }

    @Test
    fun clause5_2_5_2ExampleMondaysAndWednesdays() {
        val av = Availability(listOf(period(intervals = listOf(iv(days = listOf(1, 3), start = 16 * h, end = 16 * h + h / 2)))))
        assertTrue("Monday 16:10", Instances.isAvailable(av, t("2026-09-28T16:10:00Z")))
        assertFalse("Monday 16:30 is the end", Instances.isAvailable(av, t("2026-09-28T16:30:00Z")))
        assertFalse("Tuesday", Instances.isAvailable(av, t("2026-09-29T16:10:00Z")))
        assertTrue("Wednesday", Instances.isAvailable(av, t("2026-09-30T16:10:00Z")))
        assertEquals(t("2026-09-30T16:00:00Z"), Instances.nextChange(av, t("2026-09-29T09:00:00Z")))
        assertEquals(t("2026-09-30T16:30:00Z"), Instances.nextChange(av, t("2026-09-30T16:10:00Z")))
    }

    @Test
    fun table26EndTimeAtOrBeforeTheStartRunsIntoTheFollowingDay() {
        val av = Availability(listOf(period(intervals = listOf(iv(days = listOf(5), start = 22 * h, end = 2 * h)))))
        assertTrue("Friday 23:00", Instances.isAvailable(av, t("2026-10-02T23:00:00Z")))
        assertTrue("Saturday 01:00, from Friday", Instances.isAvailable(av, t("2026-10-03T01:00:00Z")))
        assertFalse(Instances.isAvailable(av, t("2026-10-03T02:00:00Z")))
        assertFalse("Saturday is not a start day", Instances.isAvailable(av, t("2026-10-03T23:00:00Z")))
    }

    @Test
    fun clause5_2_5_2RecurrenceCountsWeeksFromTheWeekOfValidFrom() {
        val av = Availability(listOf(period(t("2026-10-01T00:00:00Z"),
            intervals = listOf(iv(days = listOf(4), recurrence = 2, recurrenceGiven = true, start = 20 * h, end = 21 * h)))))
        assertTrue("week 0", Instances.isAvailable(av, t("2026-10-01T20:30:00Z")))
        assertFalse("week 1", Instances.isAvailable(av, t("2026-10-08T20:30:00Z")))
        assertTrue("week 2", Instances.isAvailable(av, t("2026-10-15T20:30:00Z")))
        assertEquals(t("2026-10-15T20:00:00Z"), Instances.nextChange(av, t("2026-10-02T00:00:00Z")))
    }

    @Test
    fun clause5_2_5_2AnIntervalWithRecurrenceButNoValidFromIsIgnored() {
        val av = Availability(listOf(period(intervals = listOf(iv(days = listOf(1), recurrence = 2, recurrenceGiven = true, start = 0, end = h)))))
        assertTrue("the Period without usable Intervals covers its validity", Instances.isAvailable(av, t("2026-09-29T12:00:00Z")))
    }

    @Test
    fun periodBoundsLimitItsIntervals() {
        val av = Availability(listOf(period(t("2026-10-05T00:00:00Z"), intervals = listOf(iv(start = 9 * h, end = 10 * h)))))
        assertFalse(Instances.isAvailable(av, t("2026-10-04T09:30:00Z")))
        assertTrue(Instances.isAvailable(av, t("2026-10-05T09:30:00Z")))
    }

    private val widevine = "urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"
    private val caps = Instances.Capabilities(drmSupported = { it == widevine })
    private fun inst(
        delivery: Delivery = Delivery.Dash("http://h/m.mpd"),
        priority: Int = 0,
        availability: Availability? = null,
        protection: Protection? = null,
        packages: List<String> = emptyList(),
    ) = ServiceInstance(priority, "i", delivery, availability, packages, protection)

    @Test
    fun clause5_2_13InstancesOutsideTheirScheduledHoursAreNotEvaluated() {
        val now = t("2026-10-02T12:00:00Z")
        val past = Availability(listOf(period(validTo = t("2026-10-01T00:00:00Z"))))
        assertEquals(listOf(1), Instances.candidates(listOf(inst(priority = 1, availability = past), inst(priority = 2)), now, caps))
    }

    @Test
    fun clause5_2_13InstancesKnownInAdvanceNotToPlayAreDiscarded() {
        val cases = listOf(
            Triple(inst(Delivery.Managed("multicast")), "multicast", caps),
            Triple(inst(), "no DASH player", caps.copy(dash = false)),
            Triple(inst(Delivery.Hls("http://h/p.m3u8", "OtherDeliveryParameters")), "no HLS player", caps.copy(hls = false)),
            Triple(inst(protection = Protection(emptyList(), listOf("0x0B00"))), "conditional access only", caps),
            Triple(inst(protection = Protection(listOf("urn:uuid:unknown"), emptyList())), "DRM systems this device does not support", caps),
            Triple(inst(Delivery.ControllingApplication("http://a/p.html", "application/x-other")),
                "application controlling media presentation is of type application/x-other, which this client cannot start", caps),
            Triple(inst(packages = listOf("Gold")), "subscription packages", caps),
            Triple(inst(Delivery.DashPlaylist("http://h/pl.xml")), "playlist server", caps),
            Triple(inst(Delivery.Broadcast("DVB-T")), "DVB-T broadcast", caps),
            Triple(inst(Delivery.Mbms("mbms://example.com/userservice/1")), "needs an MBMS Client", caps),
            Triple(inst(Delivery.Mbms("mbms://example.com&foo=1")), "TS 26.347 clause 8.2.2", caps.copy(mbms = true)),
        )
        for ((i, why, c) in cases) {
            val reason = Instances.cannotPlay(i, c)
            assertTrue("$reason should mention $why", reason?.contains(why) == true)
            assertEquals(emptyList<Int>(), Instances.candidates(listOf(i), 0, c))
        }
        assertNull("one usable DRM system is enough, whatever else is listed",
            Instances.cannotPlay(inst(protection = Protection(listOf("urn:uuid:unknown", widevine), listOf("0x0B00"))), caps))
        assertNull("with an MBMS Client, a valid locator is a candidate",
            Instances.cannotPlay(inst(Delivery.Mbms("mbms://example.com/userservice/1")), caps.copy(mbms = true)))
        assertNull(Instances.cannotPlay(inst(packages = listOf("Gold")), caps.copy(packages = listOf("Gold"))))
    }

    @Test
    fun clause5_2_13OtherwisePriorityIsRespectedLowerFirstTiesInDocumentOrder() {
        assertEquals(listOf(1, 3, 0, 2), Instances.candidates(listOf(inst(priority = 3), inst(priority = 0), inst(priority = 3), inst(priority = 1)), 0, caps))
        assertEquals("a failed instance is set aside", listOf(1), Instances.candidates(listOf(inst(priority = 0), inst(priority = 1)), 0, caps, setOf(0)))
    }
}
