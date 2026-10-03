/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.guide

import com.fivegmag.dvbiclient.servicelist.ParentalRating
import com.fivegmag.dvbiclient.servicelist.ServiceListParser
import com.fivegmag.dvbiclient.xml.Xml
import com.fivegmag.dvbiclient.xml.XmlFormatException
import com.fivegmag.dvbiclient.xml.attr
import com.fivegmag.dvbiclient.xml.child
import com.fivegmag.dvbiclient.xml.childText
import com.fivegmag.dvbiclient.xml.children
import com.fivegmag.dvbiclient.xml.descendant
import com.fivegmag.dvbiclient.xml.descendants
import com.fivegmag.dvbiclient.xml.local
import com.fivegmag.dvbiclient.xml.text
import org.w3c.dom.Element

/** Programme information (clause 6.10), the fields this client shows. */
data class ProgrammeInfo(
    val programId: String,
    val title: String,
    val synopsis: String,
    val genre: String?,
    val image: String?,
    /** ParentalGuidance/MinimumAge with CountryCodes (clause 6.10.15, table 61). */
    val ratings: List<ParentalRating>,
    /** The now/next structural group (clause 6.5.4.4): "now", "later" or "earlier", and its index. */
    val structural: String?,
    val structuralIndex: Int?,
)

/** A scheduled event joined with its programme information by CRID. */
data class GuideEvent(val crid: String, val start: Long, val end: Long, val info: ProgrammeInfo?) {
    val title: String get() = info?.title?.ifEmpty { null } ?: crid
}

/**
 * Parses content guide responses (TV-Anytime TVAMain, ETSI TS 103 770 V1.2.1 clauses 6.5.4, 6.6.3
 * and 6.10). Ported from the browser client (rt-dvb-i-application public/epg.js). Elements are
 * matched by local name, so a response is read whatever its element order and whatever elements it
 * adds (clause 6.5.4.1).
 */
object GuideParser {

    private const val PROMOTIONAL_STILL = "urn:tva:metadata:cs:HowRelatedCS:2012:19"

    private val STRUCTURAL = mapOf(
        "crid://dvb.org/metadata/schedules/now-next/now" to "now",
        "crid://dvb.org/metadata/schedules/now-next/later" to "later",
        "crid://dvb.org/metadata/schedules/now-next/earlier" to "earlier",
    )

    private fun root(text: String): Element {
        val root = Xml.parse(text).documentElement
        if (root.local != "TVAMain" || !(root.namespaceURI ?: "").startsWith("urn:tva:metadata:")) {
            throw XmlFormatException("not a TV-Anytime content guide response: the root element is ${root.local}")
        }
        return root
    }

    /** ISO 8601 duration as used by PublishedDuration (days, hours, minutes, seconds) in ms, or 0. */
    fun parseDuration(s: String?): Long {
        val m = Regex("^P(?:(\\d+(?:\\.\\d+)?)D)?(?:T(?:(\\d+(?:\\.\\d+)?)H)?(?:(\\d+(?:\\.\\d+)?)M)?(?:(\\d+(?:\\.\\d+)?)S)?)?$", RegexOption.IGNORE_CASE)
            .find(s?.trim() ?: "") ?: return 0
        fun g(i: Int) = m.groupValues[i].toDoubleOrNull() ?: 0.0
        return ((g(1) * 86400 + g(2) * 3600 + g(3) * 60 + g(4)) * 1000).toLong()
    }

    private fun parseInfo(pi: Element): ProgrammeInfo {
        val bd = pi.child("BasicDescription") ?: pi
        val titles = bd.children("Title")
        val title = (titles.firstOrNull { (it.attr("type") ?: "main") == "main" } ?: titles.firstOrNull())?.text ?: ""
        // The longest synopsis held; the guide shows one.
        val synopsis = bd.children("Synopsis").map { it.text }.maxByOrNull { it.length } ?: ""
        val genre = bd.child("Genre")?.let { g -> g.child("Name")?.text?.ifEmpty { null } ?: g.attr("href")?.substringAfterLast(':') }
        val image = bd.children("RelatedMaterial")
            .firstOrNull { it.child("HowRelated")?.attr("href") == PROMOTIONAL_STILL }
            ?.descendant("MediaUri")?.text?.ifEmpty { null }
        val ratings = bd.children("ParentalGuidance").mapNotNull { pg ->
            val age = pg.descendant("MinimumAge")?.text?.toIntOrNull() ?: return@mapNotNull null
            ParentalRating(age, pg.childText("CountryCodes").split(',').map { it.trim() }.filter { it.isNotEmpty() })
        }
        // MemberOf is a child of ProgramInformation (clause 6.10.4); a structural now/next group
        // gives the position (clause 6.5.4.4).
        val pos = pi.children("MemberOf").firstOrNull { STRUCTURAL.containsKey(it.attr("crid")) }
        return ProgrammeInfo(
            programId = pi.attr("programId") ?: "",
            title = title,
            synopsis = synopsis,
            genre = genre,
            image = image,
            ratings = ratings,
            structural = pos?.let { STRUCTURAL[it.attr("crid")] },
            structuralIndex = pos?.let { it.attr("index")?.toIntOrNull() ?: 1 },
        )
    }

    /**
     * The events of a schedule or now/next response, joined with their ProgramInformation by CRID
     * (clause 6.5.4.1). ActualStartTime and ActualEndTime are used when present, else
     * PublishedStartTime and PublishedDuration. "When the GroupInformationTable is provided in a
     * response, the order of previous, present and future programs shall be determined by the
     * structural CRIDs" (clause 6.5.4.1): earlier events count back from the current one, later
     * ones forward; otherwise events are in start order.
     */
    fun parseSchedule(text: String): List<GuideEvent> {
        val root = root(text)
        val info = root.descendants("ProgramInformation").associate { (it.attr("programId") ?: "") to parseInfo(it) }
        val events = ArrayList<GuideEvent>()
        for (ev in root.descendants("ScheduleEvent") + root.descendants("BroadcastEvent")) {
            val crid = ev.child("Program")?.attr("crid") ?: ""
            val actualStart = ServiceListParser.parseInstant(ev.childText("ActualStartTime"))
            val actualEnd = ServiceListParser.parseInstant(ev.childText("ActualEndTime"))
            val start = actualStart ?: ServiceListParser.parseInstant(ev.childText("PublishedStartTime")) ?: continue
            var end = if (actualStart != null && actualEnd != null) actualEnd else start + parseDuration(ev.childText("PublishedDuration"))
            if (end <= start) end = start + parseDuration(ev.childText("PublishedDuration"))
            if (end <= start) continue
            events.add(GuideEvent(crid, start, end, info[crid]))
        }
        val structured = events.any { it.info?.structural != null }
        fun rank(e: GuideEvent): Int? {
            val i = e.info ?: return null
            val n = i.structuralIndex ?: 1
            return when (i.structural) {
                "earlier" -> -n
                "now" -> 0
                "later" -> n
                else -> null
            }
        }
        val sorted = if (structured && events.all { rank(it) != null }) events.sortedBy { rank(it) } else events.sortedBy { it.start }
        val seen = HashSet<Long>()
        return sorted.filter { seen.add(it.start) }
    }

    /** The ProgramInformation for [pid] in a programme information response (clause 6.6.3), or null. */
    fun parseProgramme(text: String, pid: String): ProgrammeInfo? =
        root(text).descendants("ProgramInformation").firstOrNull { it.attr("programId") == pid }?.let { parseInfo(it) }

    /** Now and next of [events] at [nowMs]: from the structural groups when present (clause 6.5.4.4), else by time. */
    fun nowNext(events: List<GuideEvent>, nowMs: Long): Pair<GuideEvent?, GuideEvent?> {
        events.firstOrNull { it.info?.structural == "now" }?.let { onAir ->
            return onAir to events.firstOrNull { it.info?.structural == "later" && it.info.structuralIndex == 1 }
        }
        val i = events.indexOfFirst { it.start <= nowMs && it.end > nowMs }
        if (i >= 0) return events[i] to events.getOrNull(i + 1)
        return null to events.firstOrNull { it.start > nowMs }
    }
}
