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
import com.fivegmag.dvbiclient.xml.XmlFormatException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ServiceListParserTest {

    private val sample = ServiceListParser.parse(Fixtures.read("service-list.xml"))
    private val fiveG = ServiceListParser.parse(Fixtures.read("service-list-5g.xml"), preferredLanguage = "en")

    private fun ServiceList.svc(suffix: String) = services.first { it.uid.endsWith(suffix) }

    @Test
    fun serviceListAttributesAndName() {
        assertEquals("tag:dvbi.example,2024:servicelist:default", sample.id)
        assertEquals("42", sample.version)
        assertEquals("Compliance Sample List", sample.name)
        assertEquals(listOf("tag:sample,2024:service:a", "tag:sample,2024:service:b", "tag:sample,2024:service:c"), sample.services.map { it.uid })
        assertEquals(listOf(0, 1, 2), sample.services.map { it.docOrder })
    }

    @Test
    fun lcnTablesWithTargetRegionAndRegionList() {
        assertEquals(2, sample.lcnTables.size)
        assertEquals(listOf("GBR-ENG"), sample.lcnTables[0].targetRegions)
        assertEquals(LcnEntry(1, "tag:sample,2024:service:a"), sample.lcnTables[0].entries[0])
        assertTrue(sample.lcnTables[1].targetRegions.isEmpty())
        assertEquals(listOf(Region("GBR-ENG", "GBR-ENG", listOf("GBR"))), sample.regions)
        val range = fiveG.lcnTables.single().ranges.single()
        assertEquals(LcnRange(100, 199, priority = 0, fillMethod = "fillGaps", serviceOrigin = "dvbi"), range)
    }

    @Test
    fun clause5_5_12ChannelNumbersOfTheRegionsTable() {
        val inRegion = ServiceListRules.assignChannelNumbers(ServiceListRules.selectLcnTable(sample.lcnTables, "GBR-ENG"), sample.services)
        assertEquals(setOf("tag:sample,2024:service:a"), inRegion.keys)
        val elsewhere = ServiceListRules.assignChannelNumbers(ServiceListRules.selectLcnTable(sample.lcnTables, ""), sample.services)
        assertEquals(setOf("tag:sample,2024:service:b"), elsewhere.keys)
        val ranged = ServiceListRules.assignChannelNumbers(ServiceListRules.selectLcnTable(fiveG.lcnTables, null), fiveG.services)
        assertEquals(1, ranged.getValue("tag:sample,2024:service:hybrid").lcn)
        assertEquals("LCNRange numbers the rest in document order", listOf(100, 101, 102, 103),
            fiveG.services.drop(1).map { ranged.getValue(it.uid).lcn })
    }

    @Test
    fun serviceFields() {
        val a = sample.svc(":a")
        assertEquals("Alpha One", a.name)
        assertEquals("Alpha Media", a.provider)
        assertEquals(ServiceListParser.LINEAR_TV, a.serviceType)
        assertEquals(listOf("urn:tva:metadata:cs:ContentCS:2011:3.1.1"), a.genres)
        assertEquals("News", a.genreName)
        assertEquals(listOf("GBR-ENG"), a.targetRegions)
        assertEquals(listOf(ParentalRating(12, emptyList())), a.ratings)
        assertEquals("Table 15: no ServiceType is linear television", ServiceListParser.LINEAR_TV, fiveG.svc(":hybrid").serviceType)
    }

    @Test
    fun serviceNameInThePreferredLanguage() {
        assertEquals("HLS Channel", fiveG.svc(":hls-id").name)
        val fr = ServiceListParser.parse(Fixtures.read("service-list-5g.xml"), preferredLanguage = "fr")
        assertEquals("Chaine HLS", fr.svc(":hls-id").name)
        assertEquals("Alpha Un", ServiceListParser.parse(Fixtures.read("service-list.xml"), "fr").svc(":a").name)
    }

    @Test
    fun clause5_2_6_2ServiceLogoJpegOrPngFirst() {
        assertEquals(Image("http://192.168.1.202:4000/logos/hybrid.jpg", "image/jpeg"), fiveG.svc(":hybrid").logo)
        assertEquals("only SVG signalled: kept", "image/svg+xml", sample.svc(":a").logo?.contentType)
        assertNull(fiveG.svc(":5gonly").logo)
    }

    @Test
    fun clause5_5_28ParentalRatingWithCountryCodes() {
        assertEquals(listOf(ParentalRating(12, emptyList()), ParentalRating(16, listOf("DEU", "AUT"))), fiveG.svc(":hls-id").ratings)
    }

    @Test
    fun table16InstancesPriorityDisplayNamePackagesProtection() {
        val a = sample.svc(":a").instances
        assertEquals(listOf(1, 2), a.map { it.priority })
        assertEquals("Alpha DASH multi-DRM", a[0].label)
        assertEquals(listOf("Premium"), a[0].packages)
        assertEquals(listOf("urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed", "urn:uuid:9a04f079-9840-4286-ab92-e65be0885f95"), a[0].protection?.drmSystems)
        val hls = fiveG.svc(":hls-id").instances.single()
        assertEquals("@priority default 0", 0, hls.priority)
        assertEquals("no DisplayName: the ServiceName", "HLS Channel", hls.label)
    }

    @Test
    fun deliveryParametersOfEachForm() {
        assertEquals(Delivery.Dash("https://cdn.example.com/alpha/manifest.mpd"), sample.svc(":a").instances[0].delivery)
        assertEquals("annex G.2.2", Delivery.Hls("https://cdn.example.com/alpha/master.m3u8", "OtherDeliveryParameters"), sample.svc(":a").instances[1].delivery)
        assertEquals(Delivery.Managed("multicast"), sample.svc(":b").instances[0].delivery)
        assertEquals(Delivery.Broadcast("DVB-T"), sample.svc(":c").instances[0].delivery)
        assertEquals("annex G.2.3", Delivery.Hls("http://192.168.1.202:3004/hls/ch1.m3u8?auth=foo", "IdentifierBasedDeliveryParameters"),
            fiveG.svc(":hls-id").instances.single().delivery)
        assertEquals(Delivery.Mbms("mbms://service1000.mbms.operator.com&label=http://www.example.com/hybrid.mpd"), fiveG.svc(":hybrid").instances[0].delivery)
        assertEquals(Delivery.Dash("http://192.168.1.202:3004/dash/hybrid.mpd"), fiveG.svc(":hybrid").instances[1].delivery)
    }

    @Test
    fun clause5_2_3_2ControllingApplicationOverridesDeliveryParameters() {
        val app = fiveG.svc(":app").instances
        assertEquals(Delivery.ControllingApplication("http://open.tv/hls-player.html", "text/html"), app[0].delivery)
        assertEquals(Delivery.Dash("http://192.168.1.202:3004/dash/parttime.mpd"), app[1].delivery)
    }

    @Test
    fun clause5_5_15Availability() {
        val av = fiveG.svc(":app").instances[1].availability!!
        val p = av.periods.single()
        assertEquals(ServiceListParser.parseInstant("2026-01-05T00:00:00Z"), p.validFrom)
        assertNull(p.validTo)
        val iv = p.intervals.single()
        assertEquals(listOf(1, 2, 3, 4, 5), iv.days)
        assertEquals(2, iv.recurrence)
        assertTrue(iv.recurrenceGiven)
        assertEquals(18 * 3600_000L, iv.start)
        assertEquals(2 * 3600_000L, iv.end)
        assertNull(fiveG.svc(":app").instances[0].availability)
        val sampleAv = sample.svc(":a").instances[0].availability!!.periods.single()
        assertTrue(sampleAv.intervals.isEmpty())
        assertEquals(ServiceListParser.parseInstant("2026-12-31T23:59:59Z"), sampleAv.validTo)
    }

    @Test
    fun clause6_1GuideSourceAndServiceId() {
        val a = sample.svc(":a")
        assertEquals("ContentGuideSourceRef names a CGSID", "https://example.com/epg/schedule", a.guide?.schedule)
        assertEquals("https://example.com/epg/nownext", a.guide?.program)
        assertEquals("ContentGuideServiceRef is the sid", "sample-epg", a.guideSid)
        assertNull("no source", sample.svc(":c").guide)
        assertEquals("tag:sample,2024:service:c", sample.svc(":c").guideSid)
        assertEquals("list-level source", "http://192.168.1.202:4000/epg/schedule", fiveG.svc(":hybrid").guide?.schedule)
    }

    @Test
    fun notAServiceListIsRefused() {
        for (doc in listOf("<html><body>portal</body></html>", "<ServiceList/>", "not xml")) {
            try {
                ServiceListParser.parse(doc)
                fail("accepted: $doc")
            } catch (_: XmlFormatException) {
            }
        }
    }

    @Test
    fun documentTypeDeclarationsAreRefused() {
        val doc = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>" +
            "<ServiceList xmlns=\"urn:dvb:metadata:servicediscovery:2024\"><Name>&e;</Name></ServiceList>"
        try {
            val list = ServiceListParser.parse(doc)
            assertFalse("an external entity must not be expanded", list.name.contains("root"))
        } catch (_: XmlFormatException) {
        }
    }
}
