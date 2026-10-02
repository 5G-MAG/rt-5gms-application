/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.mbms

/**
 * MBMS URL check, 3GPP TS 26.347 V18.1.0 clause 8.2.2:
 *
 *     mbms-URI = "mbms:" "//" authority path-abempty *( "&" mid-label "=" mid-value ) [ "&label=" resourceURI ]
 *
 * "There are no currently defined mid-part pairs; they shall not be present in URLs", except the
 * Receive-only Mode form of clause 8.2.4 on mbms://rom.3gpp.org, whose pairs are not checked here.
 * The prefix "is the serviceId of the service", which only the MBMS Client can confirm.
 *
 * A service list carries the URL in IdentifierBasedDeliveryParameters (TS 103 770 V1.2.1 clause
 * 5.5.4, table 16). Ported from the browser client (rt-dvb-i-application 000e460,
 * public/mbms-url.js).
 */
object MbmsUrl {

    private const val ROM_AUTHORITY = "rom.3gpp.org"

    // RFC 3986 character classes for the parts of the MBMS URL (clauses 2.1 to 2.3, 3.2 and 3.3).
    // The prefix "shall not contain the character "&"" (TS 26.347 clause 8.2.2), so "&" is taken
    // out of sub-delims there; mid-value is TS 26.347's own uchar set.
    private const val U = "A-Za-z0-9\\-._~"                 // unreserved
    private const val PCT = "%[0-9A-Fa-f]{2}"               // pct-encoded
    private const val SUB = "!$'()*+,;="                    // sub-delims without "&"
    private const val USERINFO = "(?:[$U$SUB:]|$PCT)*"
    private const val REG_NAME = "(?:[$U$SUB]|$PCT)+"
    private const val IPV4 = "(?:\\d{1,3}\\.){3}\\d{1,3}"
    private const val IP_LITERAL = "\\[[0-9A-Fa-f:.]+\\]"   // IPv6address; IPvFuture is not accepted
    private const val HOST = "(?:$IP_LITERAL|$IPV4|$REG_NAME)"
    private const val AUTHORITY = "(?:$USERINFO@)?$HOST(?::\\d*)?"
    private const val PATH_ABEMPTY = "(?:/(?:[$U$SUB:@]|$PCT)*)*"
    private const val MID_VALUE = "(?:[$U;?:@=+$,/]|$PCT)+"
    private const val RESOURCE_URI = "[A-Za-z][A-Za-z0-9+.\\-]*:(?:[$U:/?#\\[\\]@!$&'()*+,;=]|$PCT)*"

    private val PREFIX = Regex("^mbms://($AUTHORITY)$PATH_ABEMPTY$")
    private val MID = Regex("^[A-Za-z][A-Za-z0-9]*=$MID_VALUE$")
    private val LABEL = Regex("^$RESOURCE_URI$")

    /** Null for a valid MBMS URL, otherwise a sentence saying what is wrong. */
    fun problem(url: String?): String? {
        val u = url ?: ""
        if (!u.startsWith("mbms://")) return "is not an MBMS URL: it must start with mbms://"
        val at = u.indexOf("&label=")
        val head = if (at < 0) u else u.substring(0, at)
        val label = if (at < 0) null else u.substring(at + "&label=".length)
        val parts = head.split('&')
        val prefix = parts[0]
        val mid = parts.drop(1)
        val m = PREFIX.find(prefix)
            ?: return "is not an MBMS URL: after mbms:// it needs an RFC 3986 authority and an optional path, with no \"&\", query or fragment"
        val authorityHost = m.groupValues[1].replace(Regex("^[^@]*@"), "").replace(Regex(":\\d*$"), "")
        if (mid.isNotEmpty() && authorityHost != ROM_AUTHORITY) {
            return "carries &name=value pairs, which are not allowed outside the Receive-only Mode form on mbms://$ROM_AUTHORITY"
        }
        if (mid.any { !MID.matches(it) }) return "has a mid-part that is not &name=value"
        if (label != null && !LABEL.matches(label)) return "has an &label= suffix that is not a URI"
        return null
    }

    /** The serviceId is the prefix: "the substring of the URI before the first "&"" (clause 8.2.2). */
    fun serviceId(url: String): String = url.substringBefore('&')
}
