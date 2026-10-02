/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.servicelist

import com.fivegmag.dvbiclient.xml.Xml
import com.fivegmag.dvbiclient.xml.XmlFormatException
import com.fivegmag.dvbiclient.xml.attr
import com.fivegmag.dvbiclient.xml.boolAttr
import com.fivegmag.dvbiclient.xml.child
import com.fivegmag.dvbiclient.xml.childText
import com.fivegmag.dvbiclient.xml.children
import com.fivegmag.dvbiclient.xml.descendants
import com.fivegmag.dvbiclient.xml.local
import com.fivegmag.dvbiclient.xml.text
import com.fivegmag.dvbiclient.xml.xmlLang
import org.w3c.dom.Element
import java.time.OffsetDateTime

/**
 * Parses a DVB-I service list, ETSI TS 103 770 V1.2.1 clause 5.5, into [ServiceList]. The element
 * structure follows the schema of annex A.1 (dvbi_v6.0.xsd).
 */
object ServiceListParser {

    private const val SD_NS_PREFIX = "urn:dvb:metadata:servicediscovery:"
    private const val SERVICE_LOGO = "urn:dvb:metadata:cs:HowRelatedCS:2021:1001.2"
    private const val LINKED_APP_CS = "urn:dvb:metadata:cs:LinkedApplicationCS:2019:"

    /** Table 15, ServiceType: "If not specified, the service contains linear television." */
    const val LINEAR_TV = "urn:dvb:metadata:cs:ServiceTypeCS:2019:linear"

    /** Annex G.2.2: the extension name and MIME type that identify an HLS playlist. */
    private const val HLS_EXTENSION = "vnd.apple.mpegurl"
    private const val HLS_TYPE = "application/vnd.apple.mpegurl"

    /**
     * Parses [text]. [preferredLanguage] (an ISO 639 code, may be empty) picks among multilingual
     * ServiceName elements. Throws [XmlFormatException] when the text is not a DVB-I service list.
     */
    fun parse(text: String, preferredLanguage: String = ""): ServiceList {
        val root = Xml.parse(text).documentElement
        val ns = root.namespaceURI ?: ""
        if (root.local != "ServiceList" || !ns.startsWith(SD_NS_PREFIX)) {
            throw XmlFormatException("not a DVB-I service list: the root element is ${root.local} in ${ns.ifEmpty { "no namespace" }}")
        }

        val cgsList = LinkedHashMap<String, ContentGuideSource>()
        for (listEl in root.children("ContentGuideSourceList")) {
            for (cgs in listEl.children("ContentGuideSource")) {
                parseCgs(cgs)?.let { if (it.cgsid.isNotEmpty()) cgsList[it.cgsid] = it }
            }
        }
        val listLevelCgs = root.child("ContentGuideSource")?.let { parseCgs(it) }

        val services = ArrayList<Service>()
        for (svc in root.children("Service")) {
            parseService(svc, preferredLanguage, cgsList, listLevelCgs, services.size)?.let { services.add(it) }
        }

        return ServiceList(
            id = root.attr("id") ?: "",
            version = root.attr("version"),
            name = root.childText("Name"),
            services = services,
            lcnTables = parseLcnTables(root),
            regions = parseRegions(root),
            subscriptionPackages = root.child("SubscriptionPackageList")?.let { spl ->
                SubscriptionPackageList(
                    packages = spl.children("SubscriptionPackage").map { it.text }.filter { it.isNotEmpty() },
                    allowNoPackage = spl.boolAttr("allowNoPackage", true),
                )
            },
        )
    }

    // LCNTableList/LCNTable (clauses 5.5.10 to 5.5.12, 5.5.29).
    private fun parseLcnTables(root: Element): List<LcnTable> {
        val tables = ArrayList<LcnTable>()
        for (list in root.children("LCNTableList")) {
            for (tbl in list.children("LCNTable")) {
                tables.add(
                    LcnTable(
                        targetRegions = tbl.children("TargetRegion").map { it.text }.filter { it.isNotEmpty() },
                        packages = tbl.children("SubscriptionPackage").map { it.text }.filter { it.isNotEmpty() },
                        entries = tbl.children("LCN").mapNotNull { l ->
                            val n = l.attr("channelNumber")?.trim()?.toIntOrNull()
                            val ref = l.attr("serviceRef")?.trim()
                            if (n == null || ref.isNullOrEmpty()) null
                            else LcnEntry(n, ref, l.boolAttr("visible", true), l.boolAttr("selectable", true))
                        },
                        ranges = tbl.children("LCNRange").mapNotNull { r ->
                            val start = r.attr("start")?.trim()?.toIntOrNull() ?: return@mapNotNull null
                            LcnRange(
                                start = start,
                                end = r.attr("end")?.trim()?.toIntOrNull(),
                                priority = r.attr("priority")?.trim()?.toIntOrNull() ?: 0,
                                fillMethod = r.attr("fillMethod") ?: "startFromHighest",
                                serviceOrigin = r.attr("serviceOrigin") ?: "dvbi",
                                serviceType = r.attr("serviceType"),
                                serviceGenre = r.attr("serviceGenre"),
                            )
                        },
                    )
                )
            }
        }
        return tables
    }

    // RegionList/Region (clause 5.6.2), including nested regions.
    private fun parseRegions(root: Element): List<Region> {
        val list = root.child("RegionList") ?: return emptyList()
        return list.descendants("Region").mapNotNull { r ->
            val id = r.attr("regionID") ?: return@mapNotNull null
            Region(
                regionId = id,
                name = r.childText("RegionName").ifEmpty { id },
                countryCodes = (r.attr("countryCodes") ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() },
            )
        }
    }

    // ContentGuideSource (clause 5.5.7): a source without ScheduleInfoEndpoint gives no schedule.
    private fun parseCgs(cgs: Element): ContentGuideSource? {
        fun endpoint(name: String): String? = cgs.child(name)?.let { ep -> ep.child("URI")?.text?.ifEmpty { null } }
        val schedule = endpoint("ScheduleInfoEndpoint") ?: return null
        return ContentGuideSource(
            cgsid = cgs.attr("CGSID") ?: "",
            schedule = schedule,
            program = endpoint("ProgramInfoEndpoint"),
            group = endpoint("GroupInfoEndpoint"),
            moreEpisodes = endpoint("MoreEpisodesEndpoint"),
        )
    }

    // RelatedMaterial of [parent] whose HowRelated@href is [href]: its MediaUri elements.
    private fun relatedImages(parent: Element, href: String): List<Image> =
        parent.children("RelatedMaterial")
            .filter { it.child("HowRelated")?.attr("href") == href }
            .flatMap { it.descendants("MediaUri") }
            .map { Image(it.text, it.attr("contentType") ?: "") }
            .filter { it.url.isNotEmpty() }

    // Clause 5.2.6.2: "At least one service logo shall be provided with the Media Type image/jpeg or
    // image/png for compatibility purposes". This client renders those two; another format is used
    // only when no JPEG or PNG is signalled.
    private fun pickLogo(images: List<Image>): Image? =
        images.firstOrNull { it.contentType.lowercase() in setOf("image/jpeg", "image/png") } ?: images.firstOrNull()

    // The linked applications of [parent] with LinkedApplicationCS:2019 term [term] (clause 5.2.3.1).
    private fun linkedApps(parent: Element, term: String): List<Image> =
        relatedImages(parent, LINKED_APP_CS + term)

    // ServiceName in the preferred language, else the one without xml:lang, else the first.
    private fun pickName(names: List<Element>, lang: String): String {
        if (names.isEmpty()) return ""
        val pref = lang.lowercase()
        val match = if (pref.isNotEmpty()) names.firstOrNull { it.xmlLang.lowercase().substringBefore('-') == pref } else null
        return (match ?: names.firstOrNull { it.xmlLang.isEmpty() } ?: names[0]).text
    }

    private fun parseService(
        svc: Element,
        lang: String,
        cgsList: Map<String, ContentGuideSource>,
        listLevelCgs: ContentGuideSource?,
        docOrder: Int,
    ): Service? {
        val uid = svc.childText("UniqueIdentifier")
        val name = pickName(svc.children("ServiceName"), lang)
        if (uid.isEmpty() || name.isEmpty()) return null

        val genres = svc.children("ServiceGenre").mapNotNull { it.attr("href") }
        val genreName = svc.child("ServiceGenre")?.child("Name")?.text?.ifEmpty { null }

        // Clause 5.5.28, table 37f: every MinimumAge with its @countryCodes.
        val ratings = svc.child("ParentalRating")?.children("MinimumAge")?.mapNotNull { ma ->
            val age = ma.text.toIntOrNull() ?: return@mapNotNull null
            ParentalRating(age, (ma.attr("countryCodes") ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() })
        } ?: emptyList()

        // An application controlling media presentation signalled for the whole service.
        val serviceControllingApp = linkedApps(svc, "1.2").firstOrNull()

        val instances = svc.children("ServiceInstance").map { parseInstance(it, name, serviceControllingApp) }

        // Clause 6.1, in descending order of precedence, and clause 6.5.2.2 for the identifier.
        val guide = ServiceListRules.resolveGuideSource(
            own = svc.child("ContentGuideSource")?.let { parseCgs(it) },
            ref = svc.childText("ContentGuideSourceRef"),
            list = cgsList,
            listLevel = listLevelCgs,
        )
        val guideSid = ServiceListRules.guideServiceId(uid, svc.childText("ContentGuideServiceRef"))

        return Service(
            uid = uid,
            name = name,
            provider = svc.childText("ProviderName"),
            serviceType = svc.child("ServiceType")?.attr("href") ?: LINEAR_TV,
            genres = genres,
            genreName = genreName,
            logo = pickLogo(relatedImages(svc, SERVICE_LOGO)),
            instances = instances,
            targetRegions = svc.children("TargetRegion").map { it.text }.filter { it.isNotEmpty() },
            ratings = ratings,
            guide = guide,
            guideSid = guideSid,
            docOrder = docOrder,
        )
    }

    private fun parseInstance(inst: Element, serviceName: String, serviceControllingApp: Image?): ServiceInstance {
        // "<attribute name="priority" type="nonNegativeInteger" default="0"/>" (clause 5.5.4)
        val priority = inst.attr("priority")?.trim()?.toIntOrNull() ?: 0
        // Table 16, DisplayName: "When not present, ServiceName is used."
        val label = inst.childText("DisplayName").ifEmpty { serviceName }

        val drm = ArrayList<String>()
        val ca = ArrayList<String>()
        for (cp in inst.children("ContentProtection")) {
            cp.children("DRMSystemId").map { it.text }.filter { it.isNotEmpty() }.forEach { drm.add(it) }
            cp.children("CASystemId").map { it.text }.filter { it.isNotEmpty() }.forEach { ca.add(it) }
        }

        // Clause 5.2.3.2: with an application controlling media presentation, delivery parameters
        // "shall be ignored by the DVB-I client". One at instance level comes first.
        val controlling = linkedApps(inst, "1.2").firstOrNull() ?: serviceControllingApp
        val delivery = if (controlling != null) {
            Delivery.ControllingApplication(controlling.url, controlling.contentType)
        } else {
            parseDelivery(inst)
        }

        return ServiceInstance(
            priority = priority,
            label = label,
            delivery = delivery,
            availability = inst.child("Availability")?.let { parseAvailability(it) },
            packages = inst.children("SubscriptionPackage").map { it.text }.filter { it.isNotEmpty() },
            protection = if (drm.isEmpty() && ca.isEmpty()) null else Protection(drm, ca),
        )
    }

    // The delivery parameters element of a ServiceInstance: one of the choice in its schema type.
    private fun parseDelivery(inst: Element): Delivery {
        inst.child("DASHDeliveryParameters")?.let { dash ->
            val ubl = dash.child("UriBasedLocation")
            val url = ubl?.child("URI")?.text ?: ""
            if (url.isEmpty()) return Delivery.Other("DASHDeliveryParameters without a URI")
            // Clause 5.2.7.2: "If @contentType attribute carries application/xml, the URL refers to an
            // XML file provided by a playlist server"
            return if ((ubl?.attr("contentType") ?: "").trim().lowercase() == "application/xml") {
                Delivery.DashPlaylist(url)
            } else {
                Delivery.Dash(url)
            }
        }
        inst.child("OtherDeliveryParameters")?.let { other ->
            val ext = other.attr("extensionName") ?: ""
            // Annex G.2.2: "The extension name of "vnd.apple.mpegurl" indicates that the delivery
            // parameters identify an HLS playlist which is further confirmed by the MIME type of
            // "application/vnd.apple.mpegurl""
            if (ext == HLS_EXTENSION) {
                val ubl = other.child("UriBasedLocation")
                val url = ubl?.child("URI")?.text ?: ""
                if (url.isNotEmpty() && (ubl?.attr("contentType") ?: "").trim().lowercase() == HLS_TYPE) {
                    return Delivery.Hls(url, "OtherDeliveryParameters")
                }
            }
            return Delivery.Other("OtherDeliveryParameters extension ${ext.ifEmpty { "(unnamed)" }}")
        }
        inst.child("IdentifierBasedDeliveryParameters")?.let { idEl ->
            val locator = idEl.text
            if (locator.lowercase().startsWith("mbms:")) return Delivery.Mbms(locator)
            // Annex G.2.3: "The URL to an HLS playlist adheres to this notion."
            if ((idEl.attr("contentType") ?: "").trim().lowercase() == HLS_TYPE && locator.isNotEmpty()) {
                return Delivery.Hls(locator, "IdentifierBasedDeliveryParameters")
            }
            return Delivery.Other("IdentifierBasedDeliveryParameters ${idEl.attr("contentType") ?: locator}")
        }
        for ((el, system) in listOf(
            "DVBTDeliveryParameters" to "DVB-T",
            "DVBSDeliveryParameters" to "DVB-S",
            "DVBCDeliveryParameters" to "DVB-C",
        )) {
            if (inst.child(el) != null) return Delivery.Broadcast(system)
        }
        if (inst.child("RTSPDeliveryParameters") != null) return Delivery.Managed("RTSP")
        if (inst.child("MulticastTSDeliveryParameters") != null) return Delivery.Managed("multicast")
        return Delivery.None
    }

    /**
     * Availability (clause 5.5.15, table 26). Times of day are ZuluTimeType (clause 5.5.17), read as
     * UTC; @days defaults to every day and @endTime to the schema default 23:59:59.999Z.
     */
    fun parseAvailability(el: Element): Availability {
        fun time(v: String?, dflt: Long): Long {
            val m = Regex("^(\\d{2}):(\\d{2}):(\\d{2}(?:\\.\\d+)?)Z$").find(v?.trim() ?: "") ?: return dflt
            val (h, mi, s) = m.destructured
            return ((h.toLong() * 60 + mi.toLong()) * 60) * 1000 + (s.toDouble() * 1000).toLong()
        }
        val periods = el.children("Period").map { p ->
            Period(
                validFrom = parseInstant(p.attr("validFrom")),
                validTo = parseInstant(p.attr("validTo")),
                intervals = p.children("Interval").map { iv ->
                    val rec = iv.attr("recurrence")
                    Interval(
                        days = (iv.attr("days") ?: "1 2 3 4 5 6 7").trim().split(Regex("\\s+"))
                            .mapNotNull { it.toIntOrNull() }.filter { it in 1..7 },
                        recurrence = maxOf(1, rec?.trim()?.toIntOrNull() ?: 1),
                        recurrenceGiven = rec != null,
                        start = time(iv.attr("startTime"), 0),
                        end = time(iv.attr("endTime"), 86_399_999),
                    )
                },
            )
        }
        return Availability(periods)
    }

    /** An xs:dateTime with a time zone, as milliseconds since the epoch, or null. */
    fun parseInstant(v: String?): Long? {
        if (v.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(v.trim()).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}
