/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.servicelist

import com.fivegmag.dvbiclient.servicelist.ServiceListRules.Numbering
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ported from rt-dvb-i-application test/servicelist.test.js. */
class ServiceListRulesTest {

    private val linear = "urn:dvb:metadata:cs:ServiceTypeCS:2019:linear"
    private val radio = "urn:dvb:metadata:cs:ServiceTypeCS:2019:linear-radio"

    private fun table(
        targetRegions: List<String> = emptyList(),
        packages: List<String> = emptyList(),
        entries: List<LcnEntry> = emptyList(),
        ranges: List<LcnRange> = emptyList(),
    ) = LcnTable(targetRegions, packages, entries, ranges)

    private fun svc(uid: String, type: String = linear, genres: List<String> = emptyList()) = Triple(uid, type, genres)

    @Test
    fun table15TargetRegionNoneMeansAnywhereSeveralMeanAnyOfThem() {
        assertTrue(ServiceListRules.inRegion(emptyList(), "R1"))
        assertTrue("the second region counts too", ServiceListRules.inRegion(listOf("R1", "R2"), "R2"))
        assertFalse(ServiceListRules.inRegion(listOf("R1", "R2"), "R3"))
        assertTrue("no region chosen", ServiceListRules.inRegion(listOf("R1"), ""))
    }

    @Test
    fun clause5_5_12OneTableIsSelectedByRegionElseTheOneWithoutTargetRegion() {
        val national = table(entries = listOf(LcnEntry(1, "a")))
        val north = table(targetRegions = listOf("N", "NE"), entries = listOf(LcnEntry(5, "a")))
        val south = table(targetRegions = listOf("S"), entries = listOf(LcnEntry(7, "a")))
        val tables = listOf(north, national, south)
        assertSame("a table applies to every region it names", north, ServiceListRules.selectLcnTable(tables, "NE"))
        assertSame(south, ServiceListRules.selectLcnTable(tables, "S"))
        assertSame("no table for the region: the unconstrained one", national, ServiceListRules.selectLcnTable(tables, "W"))
        assertSame(national, ServiceListRules.selectLcnTable(tables, ""))
        assertNull("a region ID that only shares a prefix does not match", ServiceListRules.selectLcnTable(listOf(north), "NEE"))
    }

    @Test
    fun clause5_5_12TablesAreNotCombined() {
        val regional = table(targetRegions = listOf("N"), entries = listOf(LcnEntry(5, "a")))
        val national = table(entries = listOf(LcnEntry(1, "a"), LcnEntry(2, "b")))
        val map = ServiceListRules.assignChannelNumbers(
            ServiceListRules.selectLcnTable(listOf(regional, national), "N"), listOf(svc("a"), svc("b"))
        )
        assertEquals("b has no number from the national table in region N", setOf("a"), map.keys)
        assertEquals(5, map.getValue("a").lcn)
    }

    @Test
    fun deprecatedTableSubscriptionPackageStillSelectsTheTableForTheClientsPackage() {
        val basic = table(entries = listOf(LcnEntry(1, "a")))
        val movies = table(packages = listOf("Movies"), entries = listOf(LcnEntry(9, "a")))
        assertSame(movies, ServiceListRules.selectLcnTable(listOf(basic, movies), "", listOf("Movies")))
        assertSame(basic, ServiceListRules.selectLcnTable(listOf(basic, movies), "", emptyList()))
    }

    @Test
    fun table23VisibleAndSelectableAreKeptWithTheNumber() {
        val t = table(entries = listOf(LcnEntry(1, "a"), LcnEntry(2, "b", visible = false), LcnEntry(3, "c", visible = false, selectable = false)))
        val map = ServiceListRules.assignChannelNumbers(t, listOf(svc("a"), svc("b"), svc("c")))
        assertTrue(ServiceListRules.directlySelectable(map["a"]))
        assertFalse(map.getValue("b").visible)
        assertTrue("hidden, reachable by number", ServiceListRules.directlySelectable(map["b"]))
        assertFalse("hidden and not selectable", ServiceListRules.directlySelectable(map["c"]))
        assertTrue("@selectable is read only when @visible is false", ServiceListRules.directlySelectable(Numbering(1, visible = true, selectable = false)))
    }

    @Test
    fun table37gLcnRangeNumbersServicesWithoutAnLcnInDocumentOrder() {
        val t = table(entries = listOf(LcnEntry(1, "a"), LcnEntry(101, "x")), ranges = listOf(LcnRange(100, 110, fillMethod = "fillGaps")))
        val map = ServiceListRules.assignChannelNumbers(t, listOf(svc("a"), svc("b"), svc("x"), svc("c")))
        assertEquals("fillGaps starts from @start", 100, map.getValue("b").lcn)
        assertEquals("and skips numbers already assigned", 102, map.getValue("c").lcn)
    }

    @Test
    fun table37gStartFromHighestContinuesAfterTheHighestNumberAssignedInTheRange() {
        val t = table(entries = listOf(LcnEntry(105, "x")), ranges = listOf(LcnRange(100, 110)))
        val map = ServiceListRules.assignChannelNumbers(t, listOf(svc("x"), svc("b"), svc("c")))
        assertEquals(106, map.getValue("b").lcn)
        assertEquals(107, map.getValue("c").lcn)
    }

    @Test
    fun table37gDescendingRangeOpenRangePriorityOrderAndFilters() {
        val desc = ServiceListRules.assignChannelNumbers(
            table(ranges = listOf(LcnRange(50, 48, fillMethod = "fillGaps"))), listOf(svc("a"), svc("b"), svc("c"), svc("d"))
        )
        assertEquals(listOf(50, 49, 48), listOf("a", "b", "c").map { desc.getValue(it).lcn })
        assertNull("the range is used up; the rest get no number", desc["d"])

        val open = ServiceListRules.assignChannelNumbers(table(ranges = listOf(LcnRange(900, null, fillMethod = "fillGaps"))), listOf(svc("a"), svc("b")))
        assertEquals("no @end: ascending", listOf(900, 901), listOf(open.getValue("a").lcn, open.getValue("b").lcn))

        val two = ServiceListRules.assignChannelNumbers(
            table(ranges = listOf(LcnRange(10, 10, priority = 1, fillMethod = "fillGaps"), LcnRange(20, 20, priority = 0, fillMethod = "fillGaps"))),
            listOf(svc("a"), svc("b")),
        )
        assertEquals("lower @priority value first", listOf(20, 10), listOf(two.getValue("a").lcn, two.getValue("b").lcn))

        val typed = ServiceListRules.assignChannelNumbers(
            table(ranges = listOf(LcnRange(700, 799, serviceType = radio, fillMethod = "fillGaps"))), listOf(svc("tv"), svc("r", radio))
        )
        assertNull("@serviceType restricts the range", typed["tv"])
        assertEquals(700, typed.getValue("r").lcn)

        val bcast = ServiceListRules.assignChannelNumbers(table(ranges = listOf(LcnRange(100, serviceOrigin = "targetBroadcast"))), listOf(svc("a")))
        assertNull("a range for non-DVB-I broadcast services does not number DVB-I services", bcast["a"])
    }

    @Test
    fun table16SubscriptionPackageSelectableOnlyWithOneOfThePackages() {
        assertTrue(ServiceListRules.packageAllows(emptyList(), emptyList()))
        assertFalse(ServiceListRules.packageAllows(listOf("Gold"), emptyList()))
        assertTrue(ServiceListRules.packageAllows(listOf("Gold", "Silver"), listOf("Silver")))
    }

    @Test
    fun clause5_5_28MinimumAgeByCountryARatingWithoutCountryAppliesEverywhere() {
        val ratings = listOf(ParentalRating(12, emptyList()), ParentalRating(16, listOf("DEU", "AUT")))
        assertEquals(16, ServiceListRules.minimumAgeFor(ratings, "AUT"))
        assertEquals(12, ServiceListRules.minimumAgeFor(ratings, "FRA"))
        assertNull("no rating for France", ServiceListRules.minimumAgeFor(listOf(ParentalRating(16, listOf("DEU"))), "FRA"))
        assertEquals("country unknown: the most restrictive", 16, ServiceListRules.minimumAgeFor(ratings, null))
        assertNull(ServiceListRules.minimumAgeFor(emptyList(), "FRA"))
    }

    @Test
    fun clause5_5_28TheGuideRatingOfTheProgrammeTakesPrecedenceBothWays() {
        // The clause's examples, with a client restricting 16+.
        assertFalse("service 12: permitted", ServiceListRules.restricted(16, 12, null))
        assertTrue("programme 18 on that service: prohibited", ServiceListRules.restricted(16, 12, 18))
        assertTrue("service 18: prohibited", ServiceListRules.restricted(16, 18, null))
        assertFalse("programme 12 on that service: allowed", ServiceListRules.restricted(16, 18, 12))
        assertFalse("no criterion set", ServiceListRules.restricted(0, 18, null))
    }

    @Test
    fun clause6_1ContentGuideSourcePrecedence() {
        val own = ContentGuideSource("own", "https://own/s", null, null, null)
        val listed = ContentGuideSource("x", "https://x/s", null, null, null)
        val listLevel = ContentGuideSource("", "https://list/s", null, null, null)
        assertSame(own, ServiceListRules.resolveGuideSource(own, "x", mapOf("x" to listed), listLevel))
        assertSame(listed, ServiceListRules.resolveGuideSource(null, "x", mapOf("x" to listed), listLevel))
        assertSame("an unknown CGSID falls through", listLevel, ServiceListRules.resolveGuideSource(null, "y", mapOf("x" to listed), listLevel))
        assertNull(ServiceListRules.resolveGuideSource(null, "", emptyMap(), null))
    }

    @Test
    fun clause6_5_2_2ContentGuideServiceRefTakesPrecedence() {
        assertEquals("ref", ServiceListRules.guideServiceId("uid", "ref"))
        assertEquals("uid", ServiceListRules.guideServiceId("uid", ""))
    }
}
