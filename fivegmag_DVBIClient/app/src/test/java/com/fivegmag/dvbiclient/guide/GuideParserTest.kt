/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.guide

import com.fivegmag.dvbiclient.Fixtures
import com.fivegmag.dvbiclient.servicelist.ParentalRating
import com.fivegmag.dvbiclient.servicelist.ServiceListRules
import com.fivegmag.dvbiclient.xml.XmlFormatException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class GuideParserTest {

    private fun t(s: String) = Instant.parse(s).toEpochMilli()

    @Test
    fun durations() {
        assertEquals(3_600_000L, GuideParser.parseDuration("PT1H"))
        assertEquals(5_400_000L, GuideParser.parseDuration("PT1H30M"))
        assertEquals(15_000L, GuideParser.parseDuration("PT15S"))
        assertEquals((86_400L + 7_200L) * 1000, GuideParser.parseDuration("P1DT2H"))
        assertEquals(5_400_000L, GuideParser.parseDuration("pt1.5h"))
        for (bad in listOf("", null, "not a duration", "PTX")) assertEquals(0L, GuideParser.parseDuration(bad))
    }

    @Test
    fun clause6_5_4_4NowNextOrderFromTheStructuralCrids() {
        val events = GuideParser.parseSchedule(Fixtures.read("now-next.xml"))
        assertEquals(listOf("Inside the Reference Tools", "Showcase Highlights"), events.map { it.title })
        val now = events[0]
        assertEquals("ActualStartTime over PublishedStartTime", t("2026-10-02T21:32:00Z"), now.start)
        assertEquals(t("2026-10-02T22:16:00Z"), now.end)
        assertEquals(t("2026-10-02T23:00:00Z"), events[1].end)
        val (current, next) = GuideParser.nowNext(events, t("2026-10-02T23:30:00Z"))
        assertEquals("the structural group says what is on air, whatever the clock", "Inside the Reference Tools", current?.title)
        assertEquals("Showcase Highlights", next?.title)
    }

    @Test
    fun clause6_10ProgrammeFields() {
        val info = GuideParser.parseSchedule(Fixtures.read("now-next.xml"))[0].info!!
        assertEquals("Factual", info.genre)
        assertEquals("http://192.168.1.202:4000/logos/now.png", info.image)
        assertEquals(listOf(ParentalRating(12, emptyList()), ParentalRating(16, listOf("DEU", "AUT"))), info.ratings)
        assertEquals("table 61 by country", 16, ServiceListRules.minimumAgeFor(info.ratings, "DEU"))
        assertEquals("now", info.structural)
        val later = GuideParser.parseSchedule(Fixtures.read("now-next.xml"))[1].info!!
        assertEquals("the longest synopsis", "The shortest tour of the stand, end to end.", later.synopsis)
    }

    @Test
    fun clause6_5_4_1ScheduleInStartOrderJoinedByCrid() {
        val events = GuideParser.parseSchedule(Fixtures.read("schedule.xml"))
        assertEquals("no GroupInformationTable: by PublishedStartTime", listOf("Morning News", "Documentary"), events.map { it.title })
        assertEquals("the main title, not the secondary", "Morning News", events[0].info?.title)
        assertEquals(t("2026-10-02T13:00:00Z"), events[0].end)
        assertEquals(t("2026-10-02T14:30:00Z"), events[1].end)
        assertEquals("3.1.3", events[1].info?.genre)
        val (current, next) = GuideParser.nowNext(events, t("2026-10-02T12:10:00Z"))
        assertEquals("Morning News", current?.title)
        assertEquals("Documentary", next?.title)
        val (gapNow, gapNext) = GuideParser.nowNext(events, t("2026-10-02T11:00:00Z"))
        assertNull(gapNow)
        assertEquals("before the first event: next is the first upcoming one", "Morning News", gapNext?.title)
        assertEquals(null to null, GuideParser.nowNext(events, t("2026-10-03T11:00:00Z")))
    }

    @Test
    fun clause6_6_3ProgrammeInformationByCrid() {
        val info = GuideParser.parseProgramme(Fixtures.read("schedule.xml"), "crid://example.com/prog/1")
        assertEquals("The news of the morning, with the weather.", info?.synopsis)
        assertNull(GuideParser.parseProgramme(Fixtures.read("schedule.xml"), "crid://absent"))
    }

    @Test
    fun notAGuideResponseIsRefused() {
        try {
            GuideParser.parseSchedule("<ServiceList xmlns=\"urn:dvb:metadata:servicediscovery:2024\"/>")
            fail("accepted")
        } catch (_: XmlFormatException) {
        }
    }
}
