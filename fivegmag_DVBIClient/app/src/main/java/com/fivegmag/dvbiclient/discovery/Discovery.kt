/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.discovery

import com.fivegmag.dvbiclient.guide.GuideRequests
import com.fivegmag.dvbiclient.xml.Xml
import com.fivegmag.dvbiclient.xml.XmlFormatException
import com.fivegmag.dvbiclient.xml.attr
import com.fivegmag.dvbiclient.xml.boolAttr
import com.fivegmag.dvbiclient.xml.child
import com.fivegmag.dvbiclient.xml.childElements
import com.fivegmag.dvbiclient.xml.childText
import com.fivegmag.dvbiclient.xml.children
import com.fivegmag.dvbiclient.xml.descendant
import com.fivegmag.dvbiclient.xml.local
import com.fivegmag.dvbiclient.xml.text

/** One delivery type of a ServiceListOffering's Delivery element (table 83a), with @required. */
data class DeliveryType(val name: String, val required: Boolean, val extensionName: String = "")

/** A ServiceListOffering of a registry response (clause 5.3, table 83), the fields this client acts on. */
data class Offering(
    val name: String,
    val urls: List<String>,
    val serviceListId: String,
    val regulatorListFlag: Boolean,
    val languages: List<String>,
    val targetCountries: List<String>,
    val logo: String?,
    val providerName: String,
    val delivery: List<DeliveryType>?,
    /** Why it should not be installed, or null (set by [Discovery.arrange]). */
    val problem: String? = null,
    val isDefault: Boolean = false,
)

/**
 * Service List Registry queries and responses, ETSI TS 103 770 V1.2.1 clauses 5.1.3.2 and 5.3, and
 * the client's use of them (clause 8.5.3.2, tables 83 and 83a). Ported from the browser client
 * (rt-dvb-i-application public/discovery.js, app.js parseEntryPoints).
 */
object Discovery {

    private const val SLD_NS_PREFIX = "urn:dvb:metadata:servicelistdiscovery:"
    private const val SERVICE_LIST_LOGO = "urn:dvb:metadata:cs:HowRelatedCS:2021:1001.1"

    /**
     * The query URL. Clause 5.1.3.2: "Query strings are included as part of the URL, i.e.:
     * <ServiceListRegistryEndpoint>?<parameter1>=value1&<parameter2>=value2", with the value
     * percent-encoded. Only TargetCountry is sent, and only when the user set a country.
     */
    fun queryUrl(endpoint: String, country: String?): String =
        GuideRequests.withQuery(endpoint, listOf("TargetCountry" to country?.trim()?.uppercase()))

    /** Parses a ServiceListEntryPoints response. Throws [XmlFormatException] when it is not one. */
    fun parse(text: String): List<Offering> {
        val root = Xml.parse(text).documentElement
        if (root.local != "ServiceListEntryPoints" || !(root.namespaceURI ?: "").startsWith(SLD_NS_PREFIX)) {
            throw XmlFormatException("not a Service List Registry response: the root element is ${root.local}")
        }
        val out = ArrayList<Offering>()
        for (po in root.children("ProviderOffering")) {
            val providerName = po.child("Provider")?.childText("Name") ?: ""
            for (o in po.children("ServiceListOffering")) {
                val urls = o.children("ServiceListURI").mapNotNull { it.child("URI")?.text?.ifEmpty { null } }.distinct()
                if (urls.isEmpty()) continue
                val delivery = o.child("Delivery")?.let { d ->
                    d.childElements().map { DeliveryType(it.local, it.boolAttr("required", false), it.attr("extensionName") ?: "") }
                }
                out.add(
                    Offering(
                        name = o.childText("ServiceListName").ifEmpty { providerName.ifEmpty { urls[0] } },
                        urls = urls,
                        serviceListId = o.childText("ServiceListId"),
                        regulatorListFlag = o.boolAttr("regulatorListFlag", false),
                        languages = o.children("Language").map { it.text }.filter { it.isNotEmpty() },
                        targetCountries = o.children("TargetCountry").flatMap { it.text.split(',') }.map { it.trim() }.filter { it.isNotEmpty() },
                        logo = o.children("RelatedMaterial")
                            .firstOrNull { it.child("HowRelated")?.attr("href") == SERVICE_LIST_LOGO }
                            ?.descendant("MediaUri")?.text?.ifEmpty { null },
                        providerName = providerName,
                        delivery = delivery,
                    )
                )
            }
        }
        return out
    }

    // What this client can receive: DVB-DASH, and HLS as annex G.2.2 signals it. No tuner, no RTSP,
    // no multicast, and no application engine.
    private val RECEIVABLE = setOf("DASHDelivery")

    /**
     * Why the offering should not be installed, or null. Table 12c, @required: "When set to true,
     * the DVB-I client should only install the service list offering if the broadcast signal, IP
     * network or application related to the delivery type can be used by the client to retrieve
     * DVB services."
     */
    fun deliveryProblem(delivery: List<DeliveryType>?): String? {
        if (delivery == null) return null
        val missing = delivery.filter { it.required }.filter { d ->
            when (d.name) {
                in RECEIVABLE -> false
                "OtherDeliveryParameters" -> d.extensionName != "vnd.apple.mpegurl"
                else -> true
            }
        }.map { d ->
            when (d.name) {
                "DVBTDelivery" -> "DVB-T"
                "DVBCDelivery" -> "DVB-C"
                "DVBSDelivery" -> "DVB-S"
                "RTSPDelivery" -> "RTSP"
                "MulticastTSDelivery" -> "multicast"
                "ApplicationDelivery" -> "an application, and this client has no application engine"
                "OtherDeliveryParameters" -> "delivery extension ${d.extensionName.ifEmpty { "(unnamed)" }}"
                else -> d.name
            }
        }.distinct()
        return if (missing.isEmpty()) null else "requires ${missing.joinToString(", ")}, which this client cannot receive"
    }

    /** Table 12, TargetCountry: "If not specified, no regional constraints exist and the service can be received anywhere." */
    fun targetsCountry(o: Offering, country: String?): Boolean =
        country.isNullOrEmpty() || o.targetCountries.isEmpty() || country in o.targetCountries

    private fun speaks(o: Offering, lang: String): Boolean =
        lang.isNotEmpty() && o.languages.any { it.lowercase().substringBefore('-') == lang.lowercase() }

    /**
     * The offerings in the order offered to the user, each with its problem and whether it is the
     * default. Table 83, NOTE 2: "If a Service List Registry response includes any lists with
     * @regulatorListFlag set to true then DVB-I clients shall either i) select a Service List with
     * @regulatorListFlag set to true or ii) offer the user a choice of Service Lists where the
     * default option is a Service List with @regulatorListFlag set to true." This client takes ii):
     * regulator lists first, and whenever there is one the default is a regulator list, the first
     * installable one or, when none can be installed here, the first one with its problem (owner
     * decision D8); then installable lists before the rest, lists in the preferred language, and
     * otherwise registry order. Offerings for another country are given a problem.
     */
    fun arrange(offerings: List<Offering>, country: String? = null, lang: String = ""): List<Offering> {
        val rated = offerings.mapIndexed { i, o ->
            var problem = deliveryProblem(o.delivery)
            if (problem == null && !targetsCountry(o, country)) problem = "intended for ${o.targetCountries.joinToString(", ")}, not $country"
            Triple(o.copy(problem = problem), i, speaks(o, lang))
        }.sortedWith(
            compareByDescending<Triple<Offering, Int, Boolean>> { it.first.regulatorListFlag }
                .thenBy { it.first.problem != null }
                .thenByDescending { it.third }
                .thenBy { it.second }
        ).map { it.first }
        val theDefault = rated.firstOrNull { it.regulatorListFlag } ?: rated.firstOrNull { it.problem == null }
        return rated.map { it.copy(isDefault = it === theDefault) }
    }

    /**
     * Table 12, ServiceListId: "If the ServiceList@id does not match the
     * ServiceListOffering.ServiceListId it should be considered an error". Null when they match or
     * no id was expected.
     */
    fun idProblem(expectedId: String, listId: String): String? =
        if (expectedId.isEmpty() || expectedId == listId) null
        else "the service list id ${listId.ifEmpty { "(none)" }} does not match the registry's ServiceListId $expectedId"
}
