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
 * Service list handling rules of ETSI TS 103 770 V1.2.1 applied after parsing: channel numbers
 * (clauses 5.5.10 to 5.5.12 and 5.5.29), service regions (table 15), subscription packages (clause
 * 5.1.5, table 16), service parental rating (clause 5.5.28) and the content guide source of a
 * service (clauses 6.1 and 6.5.2.2). Pure functions, ported from the browser client
 * (rt-dvb-i-application public/servicelist.js and public/guide.js).
 */
object ServiceListRules {

    /** A channel number with the flags of table 23. */
    data class Numbering(val lcn: Int, val visible: Boolean, val selectable: Boolean)

    // ── Regions and packages ────────────────────────────────────────────────────────────────

    /**
     * Table 15, TargetRegion: "If not specified, no regional constraints exist and the service can
     * be received anywhere." A service may name several regions; any one of them matches.
     */
    fun inRegion(targetRegions: List<String>, region: String?): Boolean =
        region.isNullOrEmpty() || targetRegions.isEmpty() || region in targetRegions

    /**
     * Table 16, SubscriptionPackage: "If present, this service instance is selectable only by a
     * DVB-I client that is associated to one of the SubscriptionPackage elements listed here."
     */
    fun packageAllows(instancePackages: List<String>, clientPackages: List<String>): Boolean =
        instancePackages.isEmpty() || instancePackages.any { it in clientPackages }

    // ── Channel numbers ─────────────────────────────────────────────────────────────────────

    /**
     * Clause 5.5.12: "a single LCNTable shall be selected. Different LCN tables are not intended to
     * be combined by the DVB-I client." The table for the selected region (table 25, TargetRegion)
     * and subscription packages (deprecated at table level, still honoured), else the table without
     * either constraint. Returns the table or null.
     */
    fun selectLcnTable(tables: List<LcnTable>, region: String?, clientPackages: List<String> = emptyList()): LcnTable? {
        fun regionScore(t: LcnTable): Int? = when {
            t.targetRegions.isEmpty() -> 0
            !region.isNullOrEmpty() && region in t.targetRegions -> 2
            else -> null
        }
        fun packageScore(t: LcnTable): Int? = when {
            t.packages.isEmpty() -> 0
            t.packages.any { it in clientPackages } -> 1
            else -> null
        }
        var best: LcnTable? = null
        var bestScore = -1
        for (t in tables) {
            val r = regionScore(t) ?: continue
            val p = packageScore(t) ?: continue
            if (r + p > bestScore) {
                best = t
                bestScore = r + p
            }
        }
        return best
    }

    /**
     * Channel numbers from one LCN table. [services] are (uid, serviceType, genres) in service list
     * document order. Returns uid to [Numbering] for the services the table numbers, explicitly (LCN,
     * table 23) or through LCNRange (table 37g); services left over get no number ("the client may
     * choose the LCN mapping strategy to use for any remaining services", clause 5.5.29).
     */
    fun assignChannelNumbers(table: LcnTable?, services: List<Service>): Map<String, Numbering> =
        assignChannelNumbers(table, services.map { Triple(it.uid, it.serviceType, it.genres) })

    /** As [assignChannelNumbers], on (uid, serviceType, genres) triples. */
    @JvmName("assignChannelNumbersFor")
    fun assignChannelNumbers(table: LcnTable?, services: List<Triple<String, String, List<String>>>): Map<String, Numbering> {
        val out = LinkedHashMap<String, Numbering>()
        if (table == null) return out
        val known = services.map { it.first }.toSet()
        val used = HashSet<Int>()
        for (e in table.entries) {
            if (e.serviceRef !in known || out.containsKey(e.serviceRef)) continue
            out[e.serviceRef] = Numbering(e.channelNumber, e.visible, e.selectable)
            used.add(e.channelNumber)
        }
        // "Lower values of this attribute indicate a higher priority. Higher priority LCN ranges
        // shall be used first, until all their LCNs are assigned. When LCN ranges have the same
        // priority, the range with the lowest @start value shall be used first."
        val ranges = table.ranges.sortedWith(compareBy<LcnRange> { it.priority }.thenBy { it.start })
        // "Clients should apply LCNRange mapping rules to DVB-I services in the order they are
        // defined in the service list XML."
        var remaining = services.filter { !out.containsKey(it.first) }
        for (r in ranges) {
            // dvbi and any map DVB-I services; targetBroadcast and otherBroadcast concern non-DVB-I
            // broadcast services, which this client has none of.
            if (r.serviceOrigin != "dvbi" && r.serviceOrigin != "any") continue
            val step = if (r.end != null && r.end < r.start) -1 else 1
            // "When undefined, the range shall be ascending. A client shall map channel numbers to
            // services in sequence until reaching the client's maximum supported channel number".
            // This client's maximum is the largest Int.
            val last = r.end ?: Int.MAX_VALUE
            fun inRange(n: Long): Boolean = if (step > 0) n >= r.start && n <= last else n <= r.start && n >= last
            var next = r.start.toLong()
            if (r.fillMethod == "startFromHighest" && step > 0) {
                // "map any unassigned LCNs in ascending order, starting from the highest LCN
                // already assigned in the range"
                val assigned = used.filter { inRange(it.toLong()) }
                if (assigned.isNotEmpty()) next = assigned.max().toLong() + 1
            }
            val left = ArrayList<Triple<String, String, List<String>>>()
            for (s in remaining) {
                if (r.serviceType != null && r.serviceType != s.second) { left.add(s); continue }
                if (r.serviceGenre != null && s.third.none { it == r.serviceGenre }) { left.add(s); continue }
                while (inRange(next) && used.contains(next.toInt())) next += step
                if (!inRange(next)) { left.add(s); continue }
                out[s.first] = Numbering(next.toInt(), visible = true, selectable = true)
                used.add(next.toInt())
                next += step
            }
            remaining = left
        }
        return out
    }

    /**
     * Table 23: a service with @visible false is left out of normal navigation; it stays reachable by
     * direct entry of its channel number unless @selectable is false ("This flag is only
     * interpreted when the visible flag is set to false.").
     */
    fun directlySelectable(numbering: Numbering?): Boolean =
        numbering == null || numbering.visible || numbering.selectable

    // ── Parental rating ─────────────────────────────────────────────────────────────────────

    /**
     * The MinimumAge that applies in [country] (ISO 3166 alpha-3), clause 5.5.28, table 37f: "When
     * no country is defined, the minimum age rating shall apply irrespective of the country." With
     * no country known to the client, a country-specific rating cannot be ruled out, so the most
     * restrictive one is taken. Returns null when no rating applies.
     */
    fun minimumAgeFor(ratings: List<ParentalRating>, country: String?): Int? {
        if (ratings.isEmpty()) return null
        val general = ratings.filter { it.countries.isEmpty() }
        if (!country.isNullOrEmpty()) {
            ratings.firstOrNull { country in it.countries }?.let { return it.age }
            return general.maxOfOrNull { it.age }
        }
        return ratings.maxOf { it.age }
    }

    /**
     * The client's parental criterion: [threshold] 0 means none; otherwise content rated at or above
     * it is restricted. Clause 5.5.28: the content guide's rating of the programme being shown,
     * where there is one, takes precedence over Service.ParentalRating.
     */
    fun restricted(threshold: Int, serviceAge: Int?, programmeAge: Int?): Boolean {
        if (threshold <= 0) return false
        val age = programmeAge ?: serviceAge
        return age != null && age >= threshold
    }

    // ── Content guide source ────────────────────────────────────────────────────────────────

    /**
     * Clause 6.1, "In descending order of precedence": the service's own ContentGuideSource, the
     * ContentGuideSourceList entry whose @CGSID its ContentGuideSourceRef names, the service list's
     * ContentGuideSource.
     */
    fun resolveGuideSource(
        own: ContentGuideSource?,
        ref: String,
        list: Map<String, ContentGuideSource>,
        listLevel: ContentGuideSource?,
    ): ContentGuideSource? {
        if (own != null) return own
        if (ref.isNotEmpty()) list[ref]?.let { return it }
        return listLevel
    }

    /** Clause 6.5.2.2: "ContentGuideServiceRef, when specified, takes precedence over UniqueIdentifier" */
    fun guideServiceId(uniqueIdentifier: String, contentGuideServiceRef: String): String =
        contentGuideServiceRef.ifEmpty { uniqueIdentifier }
}
