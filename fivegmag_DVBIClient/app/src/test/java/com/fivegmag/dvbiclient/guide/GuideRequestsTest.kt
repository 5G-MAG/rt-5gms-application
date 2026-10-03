/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Ported from rt-dvb-i-application test/guide.test.js (the requests this client makes). */
class GuideRequestsTest {

    @Test
    fun clause6_5_2_1WindowsStartOn3HourBoundariesAndSpan12Hours() {
        val now = Instant.parse("2015-06-02T13:20:00Z").toEpochMilli()
        val wins = GuideRequests.scheduleWindows(now - 3_600_000, now + 12 * 3_600_000)
        for (w in wins) {
            assertEquals(0L, w.start % 10_800)
            assertEquals(0L, w.end % 10_800)
            assertEquals(43_200L, w.end - w.start)
        }
        assertEquals("12:00, the boundary at or before 12:20", Instant.parse("2015-06-02T12:00:00Z").epochSecond, wins[0].start)
        assertTrue("the requested period is covered", wins.last().end * 1000 >= now + 12 * 3_600_000)
        assertEquals(2, wins.size)
    }

    @Test
    fun requestUrlsOfClauses6_5_2_2And6_5_3_1And6_6_2() {
        assertEquals("the example URL of clause 6.5.2.2", "https://cg.example/schedule?start=1433246400&end=1433268000&sid=12345",
            GuideRequests.scheduleUrl("https://cg.example/schedule", "12345", GuideRequests.Window(1433246400, 1433268000)))
        assertEquals("the example URL of clause 6.5.3.1", "https://cg.example/schedule?sid=12345&now_next=true",
            GuideRequests.nowNextUrl("https://cg.example/schedule", "12345"))
        assertEquals("https://cg.example/schedule?sid=12345&now_next=window", GuideRequests.nowNextUrl("https://cg.example/schedule", "12345", "window"))
        assertEquals("reserved characters percent-encoded", "https://cg.example/program?pid=crid%3A%2F%2Fchannel7.co.uk%2Fn19alr19",
            GuideRequests.programUrl("https://cg.example/program", "crid://channel7.co.uk/n19alr19"))
        assertEquals("an endpoint with a query gets &", "https://cg.example/s?x=1&sid=a",
            GuideRequests.withQuery("https://cg.example/s?x=1", listOf("sid" to "a")))
    }

    @Test
    fun clauses5_1_3_2And6_2_2AllRfc3986ReservedCharactersArePercentEncoded() {
        val reserved = ":/?#[]@" + "!$&'()*+,;="
        assertEquals("%3A%2F%3F%23%5B%5D%40%21%24%26%27%28%29%2A%2B%2C%3B%3D", GuideRequests.encodeQueryComponent(reserved))
        assertEquals("unreserved characters are left as they are", "Az09-._~", GuideRequests.encodeQueryComponent("Az09-._~"))
        assertEquals("https://cg.example/program?pid=crid%3A%2F%2Fx.example%2Fit%27s%281%29%2A%21",
            GuideRequests.programUrl("https://cg.example/program", "crid://x.example/it's(1)*!"))
        assertEquals("UTF-8 octets", "%C3%A9", GuideRequests.encodeQueryComponent("é"))
        assertEquals("a space as %20", "a%20b", GuideRequests.encodeQueryComponent("a b"))
    }
}
