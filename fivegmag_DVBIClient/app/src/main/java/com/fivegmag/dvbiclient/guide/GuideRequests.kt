/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.guide

/**
 * Content guide request URLs of ETSI TS 103 770 V1.2.1 clauses 6.5 and 6.6, encoded as clause 6.2.2
 * says. Ported from the browser client (rt-dvb-i-application public/guide.js).
 */
object GuideRequests {

    /** Clause 6.5.2.1: "the Unix timestamp shall be a whole multiple of 10 800" */
    const val THREE_HOURS_S = 10_800L

    /** Clause 6.5.2.1: end is start plus 21 600 or 43 200 seconds; the longer window is used. */
    const val WINDOW_S = 43_200L

    /** A schedule window in Unix seconds. */
    data class Window(val start: Long, val end: Long)

    /**
     * One key or value of a query string. Clauses 5.1.3.2 and 6.2.2: any "reserved" characters of
     * IETF RFC 3986 clause 2.2 within key/value pairs "shall be percent-encoded as defined in clause
     * 2.1 of IETF RFC 3986". Everything but the unreserved characters of RFC 3986 clause 2.3 is
     * encoded, as UTF-8 octets with the uppercase hexadecimal digits clause 2.1 recommends.
     */
    fun encodeQueryComponent(v: String): String {
        val sb = StringBuilder()
        for (b in v.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '.' || ch == '_' || ch == '~') {
                sb.append(ch)
            } else {
                sb.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 0xF])
            }
        }
        return sb.toString()
    }

    /** Query string of (name, value) pairs, each encoded as above; empty values are left out. */
    fun query(pairs: List<Pair<String, String?>>): String =
        pairs.filter { !it.second.isNullOrEmpty() }
            .joinToString("&") { "${encodeQueryComponent(it.first)}=${encodeQueryComponent(it.second!!)}" }

    fun withQuery(base: String, pairs: List<Pair<String, String?>>): String {
        val q = query(pairs)
        return if (q.isEmpty()) base else base + (if (base.contains('?')) "&" else "?") + q
    }

    /**
     * The 12-hour windows, each starting on a 3-hour boundary, that together cover [fromMs, toMs]
     * (clause 6.5.2.1). "Combining the results of multiple calls is the responsibility of the DVB-I
     * client." (clause 6.5.2.2).
     */
    fun scheduleWindows(fromMs: Long, toMs: Long): List<Window> {
        val out = ArrayList<Window>()
        var start = Math.floorDiv(Math.floorDiv(fromMs, 1000L), THREE_HOURS_S) * THREE_HOURS_S
        val last = toMs / 1000.0
        do {
            out.add(Window(start, start + WINDOW_S))
            start += WINDOW_S
        } while (start < last)
        return out
    }

    /** Clause 6.5.2.2: <ScheduleInfoEndpoint>?start=<start_unixtime>&end=<end_unixtime>&sid=<service_id> */
    fun scheduleUrl(endpoint: String, sid: String, win: Window): String =
        withQuery(endpoint, listOf("start" to win.start.toString(), "end" to win.end.toString(), "sid" to sid))

    /** Clause 6.5.3.1: <ScheduleInfoEndpoint>?sid=<service_id>&now_next=<window_type>, true or window. */
    fun nowNextUrl(endpoint: String, sid: String, windowType: String = "true"): String =
        withQuery(endpoint, listOf("sid" to sid, "now_next" to windowType))

    /** Clause 6.6.2: <ProgramInfoEndpoint>?pid=<program_id> */
    fun programUrl(endpoint: String, pid: String): String = withQuery(endpoint, listOf("pid" to pid))
}
