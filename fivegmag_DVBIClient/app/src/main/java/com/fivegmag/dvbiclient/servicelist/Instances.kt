/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.servicelist

import com.fivegmag.dvbiclient.mbms.MbmsUrl

/**
 * Service instance precedence, ETSI TS 103 770 V1.2.1 clause 5.2.13, and the scheduled service
 * hours it depends on (clause 5.2.5.2, Availability as typed in clause 5.5.15, table 26). Ported
 * from the browser client (rt-dvb-i-application public/instances.js).
 */
object Instances {

    private const val DAY_MS = 86_400_000L
    private const val WEEK_MS = 7 * DAY_MS

    /**
     * What this client can play.
     * @param drmSupported whether the device has a DRM system for a DRMSystemId (for example
     *        "urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")
     * @param mbms whether an MBMS Client is registered with this client (TS 103 770 clause 9.3.3)
     */
    data class Capabilities(
        val dash: Boolean = true,
        val hls: Boolean = true,
        val drmSupported: (String) -> Boolean = { false },
        val packages: List<String> = emptyList(),
        val mbms: Boolean = false,
    )

    // 1 (Monday) .. 7 (Sunday) of the UTC day containing [ms].
    private fun isoDay(ms: Long): Int = (Math.floorDiv(ms, DAY_MS) + 3).mod(7) + 1
    private fun utcMidnight(ms: Long): Long = Math.floorDiv(ms, DAY_MS) * DAY_MS
    // Start (UTC Monday 00:00) of the week containing [ms].
    private fun weekStart(ms: Long): Long = utcMidnight(ms) - (isoDay(ms) - 1) * DAY_MS

    // The occurrence of [iv] that starts on the UTC day [dayStart], as (start, end), or null.
    private fun occurrence(iv: Interval, period: Period, dayStart: Long): Pair<Long, Long>? {
        if (isoDay(dayStart) !in iv.days) return null
        if (iv.recurrence > 1) {
            // "The cadence starts in the week indicated by the @validFrom attribute" (clause 5.2.5.2).
            val from = period.validFrom ?: return null
            val weeks = Math.round((weekStart(dayStart) - weekStart(from)).toDouble() / WEEK_MS)
            if (weeks < 0 || weeks % iv.recurrence != 0L) return null
        }
        val start = dayStart + iv.start
        // "If this value is less than or equal to the value of @startTime the service ends on the
        // following day." (table 26, @endTime)
        val end = if (iv.end <= iv.start) dayStart + DAY_MS + iv.end else dayStart + iv.end
        return start to end
    }

    // "the Interval element shall be ignored if @recurrence is specified but no @validFrom is
    // specified in the containing Period element" (clause 5.2.5.2); a Period left with no Interval
    // covers the whole of its validity.
    private fun usableIntervals(period: Period): List<Interval> =
        period.intervals.filter { !(it.recurrenceGiven && period.validFrom == null) }

    private fun periodActive(period: Period, ms: Long): Boolean {
        if (period.validFrom != null && ms < period.validFrom) return false
        if (period.validTo != null && ms >= period.validTo) return false
        val intervals = usableIntervals(period)
        if (intervals.isEmpty()) return true
        val today = utcMidnight(ms)
        return intervals.any { iv ->
            listOf(today, today - DAY_MS).any { d ->
                val o = occurrence(iv, period, d)
                o != null && ms >= o.first && ms < o.second
            }
        }
    }

    /** "The union of these intervals cumulatively define the availability of the service instance." */
    fun isAvailable(av: Availability?, ms: Long): Boolean = av == null || av.periods.any { periodActive(it, ms) }

    /**
     * The first instant after [ms] at which [isAvailable] changes, or null if it never does.
     * Changes happen only at a period bound or an interval bound; the interval bounds repeat with
     * the longest recurrence, so one cycle of it, plus a day for an interval that runs past
     * midnight, holds every distinct one.
     */
    fun nextChange(av: Availability?, ms: Long): Long? {
        if (av == null) return null
        val now = isAvailable(av, ms)
        val candidates = ArrayList<Long>()
        for (p in av.periods) {
            p.validFrom?.let { candidates.add(it) }
            p.validTo?.let { candidates.add(it) }
            val intervals = usableIntervals(p)
            if (intervals.isEmpty()) continue
            val from = maxOf(ms, p.validFrom ?: ms)
            val cycleDays = 7 * maxOf(1, intervals.maxOf { it.recurrence }) + 1
            var d = utcMidnight(from) - DAY_MS
            for (n in 0..cycleDays) {
                for (iv in intervals) occurrence(iv, p, d)?.let { candidates.add(it.first); candidates.add(it.second) }
                d += DAY_MS
            }
        }
        candidates.sort()
        return candidates.firstOrNull { it > ms && isAvailable(av, it) != now }
    }

    /**
     * Why an instance can be known in advance not to play on this client, or null (clause 5.2.13:
     * "Service instances that contain video where the DVB-I client can determine in advance that it
     * would not be able to display any video shall be discarded").
     */
    fun cannotPlay(inst: ServiceInstance, caps: Capabilities): String? {
        // Table 16, SubscriptionPackage: "If present, this service instance is selectable only by a
        // DVB-I client that is associated to one of the SubscriptionPackage elements listed here."
        if (!ServiceListRules.packageAllows(inst.packages, caps.packages)) {
            return "only in subscription packages this client is not associated with"
        }
        when (val d = inst.delivery) {
            // "Service instances with a linked "application controlling media presentation" that
            // cannot be started shall be discarded." (clause 5.2.13); NOTE 1 i): this client has no
            // linked application engine.
            is Delivery.ControllingApplication ->
                return "its application controlling media presentation is of type ${d.contentType.ifEmpty { "(none)" }}, which this client cannot start"
            is Delivery.Mbms -> {
                MbmsUrl.problem(d.locator)?.let { return "its 5G Broadcast locator $it (3GPP TS 26.347 clause 8.2.2)" }
                if (!caps.mbms) return "5G Broadcast reception needs an MBMS Client (TS 103 770 clause 9.3.3), and none is available on this device"
            }
            is Delivery.Dash -> if (!caps.dash) return "no DASH player is available"
            is Delivery.Hls -> if (!caps.hls) return "no HLS player is available"
            is Delivery.DashPlaylist -> return "it is delivered through a playlist server (clause 5.2.7.2), which this client does not support"
            is Delivery.Broadcast -> return "${d.system} broadcast cannot be received by this client"
            is Delivery.Managed -> return "${d.system} delivery cannot be received by this client"
            is Delivery.Other -> return "${d.what} is not a delivery this client knows"
            Delivery.None -> return "it has no delivery parameters"
        }
        val p = inst.protection
        if (p != null) {
            if (p.drmSystems.isEmpty() && p.caSystems.isNotEmpty()) return "protected by conditional access only, which this client cannot descramble"
            if (p.drmSystems.isNotEmpty() && p.drmSystems.none { caps.drmSupported(it) }) {
                return "protected only by DRM systems this device does not support"
            }
        }
        return null
    }

    /**
     * The instances to try, as indices into [instances], in order: available now, not known in
     * advance to fail, not [excluded], then by ascending @priority ("Lower values of this attribute
     * indicate a higher priority.", table 16), keeping document order between equal priorities.
     */
    fun candidates(instances: List<ServiceInstance>, ms: Long, caps: Capabilities, excluded: Set<Int> = emptySet()): List<Int> =
        instances.indices
            .filter { it !in excluded && isAvailable(instances[it].availability, ms) && cannotPlay(instances[it], caps) == null }
            .sortedWith(compareBy<Int> { instances[it].priority }.thenBy { it })
}
