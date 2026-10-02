/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.http

import java.net.URI

/**
 * ETSI TS 103 770 V1.2.1 clause 7.3 requires HTTP over TLS towards DVB-I metadata endpoints
 * (service list registries, service list servers, content guide servers), with one exception,
 * quoted in [PRIVATE_SUBNET_EXCEPTION]. A plain HTTP endpoint is used, with the warning built
 * here shown beside it (owner decision D6), saying whether the endpoint is on the private subnet
 * of this device.
 */
object TlsCheck {

    const val PRIVATE_SUBNET_EXCEPTION = "For the specific case that a DVB-I client connects to a DVB-I metadata " +
        "endpoint located on the same private subnet (see clause 3 of IETF RFC 1918 [27]), HTTP may be used without TLS."

    /** An IPv4 address of this device with its prefix length, for example ("192.168.1.20", 24). */
    data class LocalAddress(val address: String, val prefixLength: Int)

    /** The address as a 32-bit value, or null when [s] is not a dotted-quad IPv4 address. */
    fun ipv4(s: String): Long? {
        val parts = s.split('.')
        if (parts.size != 4) return null
        var v = 0L
        for (p in parts) {
            if (p.isEmpty() || p.length > 3 || !p.all { it.isDigit() }) return null
            val n = p.toInt()
            if (n > 255) return null
            v = (v shl 8) or n.toLong()
        }
        return v
    }

    /**
     * IETF RFC 1918 clause 3: "10.0.0.0 - 10.255.255.255 (10/8 prefix)", "172.16.0.0 -
     * 172.31.255.255 (172.16/12 prefix)", "192.168.0.0 - 192.168.255.255 (192.168/16 prefix)".
     */
    fun isPrivate(address: String): Boolean {
        val a = ipv4(address) ?: return false
        return (a shr 24) == 10L || (a shr 20) == 0xAC1L || (a shr 16) == 0xC0A8L
    }

    /** Whether [address] is a private address inside one of [local]'s subnets. */
    fun onSamePrivateSubnet(address: String, local: List<LocalAddress>): Boolean {
        val a = ipv4(address) ?: return false
        if (!isPrivate(address)) return false
        return local.any { l ->
            val b = ipv4(l.address) ?: return@any false
            if (l.prefixLength !in 1..32) return@any false
            val mask = (0xFFFFFFFFL shl (32 - l.prefixLength)) and 0xFFFFFFFFL
            (a and mask) == (b and mask)
        }
    }

    /**
     * The warning for [url], or null when it is not plain HTTP. [local] are this device's IPv4
     * addresses with their prefix lengths.
     */
    fun plainHttpWarning(url: String, local: List<LocalAddress>): String? {
        val u = try {
            URI(url)
        } catch (_: Exception) {
            return null
        }
        if (!"http".equals(u.scheme, ignoreCase = true)) return null
        val host = u.host ?: return null
        val where = when {
            ipv4(host) == null -> "Whether $host is on this device's private subnet cannot be told from its name."
            onSamePrivateSubnet(host, local) -> "$host is on this device's private subnet."
            else -> "$host is NOT on this device's private subnet, so the exception does not apply."
        }
        return "Not over TLS: ${u.scheme}://${u.authority} is fetched with plain HTTP. ETSI TS 103 770 V1.2.1 " +
            "clause 7.3 requires HTTP over TLS except: “$PRIVATE_SUBNET_EXCEPTION” $where"
    }
}
