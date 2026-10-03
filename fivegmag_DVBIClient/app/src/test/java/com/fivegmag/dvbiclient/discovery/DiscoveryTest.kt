/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.discovery

import com.fivegmag.dvbiclient.Fixtures
import com.fivegmag.dvbiclient.xml.XmlFormatException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Ported from rt-dvb-i-application test/discovery.test.js, plus parsing of a registry response. */
class DiscoveryTest {

    private fun offering(
        name: String,
        regulator: Boolean = false,
        delivery: List<DeliveryType>? = listOf(DeliveryType("DASHDelivery", false)),
        countries: List<String> = emptyList(),
        languages: List<String> = emptyList(),
    ) = Offering(name, listOf("https://sl.example/$name"), "tag:x,2026:$name", regulator, languages, countries, null, "", delivery)

    @Test
    fun clause5_1_3_2QueryUrl() {
        assertEquals("http://192.168.1.202:7000/query?TargetCountry=CHE", Discovery.queryUrl("http://192.168.1.202:7000/query", "che"))
        assertEquals("no parameter, no filtering", "http://192.168.1.202:7000/query", Discovery.queryUrl("http://192.168.1.202:7000/query", ""))
    }

    @Test
    fun clause5_3ServiceListEntryPointsParsed() {
        val o = Discovery.parse(Fixtures.read("registry-response.xml"))
        assertEquals(listOf("5G-MAG", "Official list", "Mirrored list"), o.map { it.name })
        assertEquals(listOf("http://192.168.1.202:4000/service-list.xml"), o[0].urls)
        assertEquals("tag:5g-mag.org,2026:servicelist:local-demo", o[0].serviceListId)
        assertEquals(listOf("CHE", "DEU", "ESP"), o[0].targetCountries)
        assertEquals(listOf("en"), o[0].languages)
        assertEquals("5G-MAG", o[0].providerName)
        assertTrue(o[1].regulatorListFlag)
        assertEquals(listOf(DeliveryType("DASHDelivery", false), DeliveryType("DVBTDelivery", true)), o[1].delivery)
        assertEquals("several ServiceListURI: fallbacks in order", listOf("https://a.example/list.xml", "https://b.example/list.xml"), o[2].urls)
        assertEquals("https://a.example/logo.png", o[2].logo)
        try {
            Discovery.parse("<html/>")
            fail("accepted")
        } catch (_: XmlFormatException) {
        }
    }

    @Test
    fun table12cRequiredAnOfferingIsNotInstalledWhenARequiredDeliveryCannotBeUsed() {
        assertNull(Discovery.deliveryProblem(listOf(DeliveryType("DASHDelivery", true))))
        assertNull("a delivery type that is not required does not stop installation",
            Discovery.deliveryProblem(listOf(DeliveryType("DASHDelivery", true), DeliveryType("DVBTDelivery", false))))
        assertTrue(Discovery.deliveryProblem(listOf(DeliveryType("DVBTDelivery", true), DeliveryType("DVBTDelivery", true)))!!.contains("DVB-T"))
        assertTrue(Discovery.deliveryProblem(listOf(DeliveryType("MulticastTSDelivery", true)))!!.contains("multicast"))
        assertTrue("no application engine in this client",
            Discovery.deliveryProblem(listOf(DeliveryType("ApplicationDelivery", true)))!!.contains("application"))
        assertNull(Discovery.deliveryProblem(null))
    }

    @Test
    fun table83Note2ARegulatorListIsTheDefaultChoice() {
        val list = Discovery.arrange(listOf(offering("a"), offering("reg", regulator = true), offering("b")))
        assertEquals(listOf("reg", "a", "b"), list.map { it.name })
        assertEquals(listOf(true, false, false), list.map { it.isDefault })
    }

    @Test
    fun table83Note2ARegulatorListStaysTheDefaultEvenWhenItCannotBeInstalled() {
        val list = Discovery.arrange(listOf(offering("ok"), offering("reg", true, listOf(DeliveryType("DVBSDelivery", true)))))
        assertEquals("reg", list[0].name)
        assertTrue(list[0].isDefault)
        assertTrue(list[0].problem!!.contains("DVB-S"))
        assertFalse(list[1].isDefault)

        val two = Discovery.arrange(listOf(
            offering("reg-dvbt", true, listOf(DeliveryType("DVBTDelivery", true))),
            offering("ok"),
            offering("reg-ok", true),
        ))
        assertEquals("an installable regulator list is preferred", listOf("reg-ok", "reg-dvbt", "ok"), two.map { it.name })
        assertEquals(listOf(true, false, false), two.map { it.isDefault })

        val parsed = Discovery.arrange(Discovery.parse(Fixtures.read("registry-response.xml")), "CHE", "en")
        assertEquals("the fixture's regulator list is the default though it needs DVB-T", "Official list", parsed.first { it.isDefault }.name)
    }

    @Test
    fun withoutARegulatorListTheFirstInstallableOfferingIsTheDefault() {
        val list = Discovery.arrange(listOf(offering("dvbs", delivery = listOf(DeliveryType("DVBSDelivery", true))), offering("ok")))
        assertEquals(listOf("ok" to true, "dvbs" to false), list.map { it.name to it.isDefault })
    }

    @Test
    fun table83TargetCountryAndLanguageAreActedOn() {
        val list = Discovery.arrange(listOf(
            offering("fr", countries = listOf("FRA"), languages = listOf("fr")),
            offering("any-de", languages = listOf("de")),
            offering("es", countries = listOf("ESP"), languages = listOf("es")),
        ), "ESP", "de")
        assertEquals("preferred language first; another country last", listOf("any-de", "es", "fr"), list.map { it.name })
        assertTrue(list[2].problem!!.contains("intended for FRA, not ESP"))
        assertTrue("no TargetCountry: anywhere", Discovery.targetsCountry(offering("x"), "ESP"))
    }

    @Test
    fun table12ServiceListIdAListWhoseIdDiffersIsAnError() {
        assertNull(Discovery.idProblem("tag:x,2026:a", "tag:x,2026:a"))
        assertNull("nothing expected when the list did not come from a registry", Discovery.idProblem("", "anything"))
        assertTrue(Discovery.idProblem("tag:x,2026:a", "tag:x,2026:b")!!.contains("does not match"))
    }
}
