/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.servicelist

/**
 * A parsed DVB-I service list, ETSI TS 103 770 V1.2.1 clause 5.5. Only what this client acts on is
 * kept.
 */
data class ServiceList(
    val id: String,
    val version: String?,
    val name: String,
    val services: List<Service>,
    val lcnTables: List<LcnTable>,
    val regions: List<Region>,
    val subscriptionPackages: SubscriptionPackageList?,
)

/** A Region of the RegionList (clause 5.6.2), for choosing the user's region. */
data class Region(val regionId: String, val name: String, val countryCodes: List<String>)

/** SubscriptionPackageList (clause 5.5.25): the packages a client can be associated with. */
data class SubscriptionPackageList(val packages: List<String>, val allowNoPackage: Boolean)

/** LCNTable (clause 5.5.12, table 25). */
data class LcnTable(
    val targetRegions: List<String>,
    val packages: List<String>,
    val entries: List<LcnEntry>,
    val ranges: List<LcnRange>,
)

/** LCN (table 23). @visible and @selectable default to true. */
data class LcnEntry(
    val channelNumber: Int,
    val serviceRef: String,
    val visible: Boolean = true,
    val selectable: Boolean = true,
)

/** LCNRange (clause 5.5.29, table 37g), with the schema defaults for @priority, @fillMethod and @serviceOrigin. */
data class LcnRange(
    val start: Int,
    val end: Int? = null,
    val priority: Int = 0,
    val fillMethod: String = "startFromHighest",
    val serviceOrigin: String = "dvbi",
    val serviceType: String? = null,
    val serviceGenre: String? = null,
)

/** A MinimumAge of Service.ParentalRating (clause 5.5.28, table 37f) with its @countryCodes. */
data class ParentalRating(val age: Int, val countries: List<String>)

/** The endpoints of a ContentGuideSource (clause 5.5.7). */
data class ContentGuideSource(
    val cgsid: String,
    val schedule: String,
    val program: String?,
    val group: String?,
    val moreEpisodes: String?,
)

/** An image signalled in RelatedMaterial, with its MediaUri@contentType. */
data class Image(val url: String, val contentType: String)

/** One Service (clause 5.5.2, table 15). */
data class Service(
    val uid: String,
    val name: String,
    val provider: String,
    /** ServiceType@href; linear television when the element is absent (table 15). */
    val serviceType: String,
    val genres: List<String>,
    val genreName: String?,
    val logo: Image?,
    val instances: List<ServiceInstance>,
    val targetRegions: List<String>,
    val ratings: List<ParentalRating>,
    /** The content guide source by the precedence of clause 6.1, or null. */
    val guide: ContentGuideSource?,
    /** The identifier content guide requests use (clause 6.5.2.2). */
    val guideSid: String,
    val docOrder: Int,
)

/** Content protection of a service instance (clause 5.5.20). */
data class Protection(val drmSystems: List<String>, val caSystems: List<String>)

/** How a service instance is delivered, from its delivery parameters element (table 16). */
sealed class Delivery {
    /** DASHDeliveryParameters with an MPD (clause 5.5.4, table 16). */
    data class Dash(val url: String) : Delivery()

    /** DASHDeliveryParameters whose UriBasedLocation@contentType is application/xml: a playlist server (clause 5.2.7.2). */
    data class DashPlaylist(val url: String) : Delivery()

    /** An HLS playlist, signalled as annex G.2.2 (OtherDeliveryParameters) or G.2.3 (IdentifierBasedDeliveryParameters) shows. */
    data class Hls(val url: String, val signalledBy: String) : Delivery()

    /** IdentifierBasedDeliveryParameters holding an mbms:// locator: 5G Broadcast (TS 103 770 clause 9.3.3). */
    data class Mbms(val locator: String) : Delivery()

    /** An application controlling media presentation (LinkedApplicationCS:2019:1.2, clause 5.2.3.2). */
    data class ControllingApplication(val url: String, val contentType: String) : Delivery()

    /** DVB-T, DVB-S or DVB-C delivery parameters. */
    data class Broadcast(val system: String) : Delivery()

    /** RTSPDeliveryParameters or MulticastTSDeliveryParameters. */
    data class Managed(val system: String) : Delivery()

    /** OtherDeliveryParameters or IdentifierBasedDeliveryParameters this client does not know. */
    data class Other(val what: String) : Delivery()

    /** No delivery parameters element. */
    object None : Delivery()
}

/** One ServiceInstance (clause 5.5.4, table 16). */
data class ServiceInstance(
    /** @priority, schema default 0; lower values are a higher priority. */
    val priority: Int,
    /** DisplayName, or the ServiceName when absent (table 16). */
    val label: String,
    val delivery: Delivery,
    val availability: Availability?,
    val packages: List<String>,
    val protection: Protection?,
)

/**
 * Availability of a service instance (clause 5.5.15, table 26). Times of day are milliseconds after
 * 00:00 UTC, instants milliseconds since the epoch.
 */
data class Availability(val periods: List<Period>)

data class Period(val validFrom: Long?, val validTo: Long?, val intervals: List<Interval>)

data class Interval(
    /** 1 (Monday) to 7 (Sunday). */
    val days: List<Int>,
    /** Weekly cadence, 1 when absent. */
    val recurrence: Int,
    /** Whether @recurrence was in the document. */
    val recurrenceGiven: Boolean,
    val start: Long,
    val end: Long,
)
