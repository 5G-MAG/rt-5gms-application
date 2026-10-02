/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.http

import com.fivegmag.dvbiclient.http.TlsCheck.LocalAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Clause 7.3 and its private subnet exception (IETF RFC 1918 clause 3). */
class TlsCheckTest {

    private val wifi = listOf(LocalAddress("192.168.1.37", 24))

    @Test
    fun rfc1918Clause3PrivateBlocks() {
        for (a in listOf("10.0.0.1", "10.255.255.255", "172.16.0.1", "172.31.255.255", "192.168.0.1", "192.168.255.255")) assertTrue(a, TlsCheck.isPrivate(a))
        for (a in listOf("9.255.255.255", "11.0.0.0", "172.15.255.255", "172.32.0.0", "192.167.255.255", "192.169.0.0", "8.8.8.8", "not.an.ip", "256.1.1.1")) assertFalse(a, TlsCheck.isPrivate(a))
    }

    @Test
    fun samePrivateSubnetUsesTheDevicePrefix() {
        assertTrue(TlsCheck.onSamePrivateSubnet("192.168.1.202", wifi))
        assertFalse("another /24", TlsCheck.onSamePrivateSubnet("192.168.2.202", wifi))
        assertTrue("a /16 holds it", TlsCheck.onSamePrivateSubnet("192.168.2.202", listOf(LocalAddress("192.168.1.37", 16))))
        assertFalse("a public address is never on a private subnet", TlsCheck.onSamePrivateSubnet("8.8.8.9", listOf(LocalAddress("8.8.8.8", 24))))
        assertFalse("no local address known", TlsCheck.onSamePrivateSubnet("192.168.1.202", emptyList()))
    }

    @Test
    fun warningForPlainHttpOnlyQuotingClause7_3() {
        assertNull(TlsCheck.plainHttpWarning("https://sl.example/list.xml", wifi))
        val w = TlsCheck.plainHttpWarning("http://192.168.1.202:4000/service-list.xml", wifi)!!
        assertTrue(w.startsWith("Not over TLS: http://192.168.1.202:4000 is fetched with plain HTTP."))
        assertTrue(w.contains(TlsCheck.PRIVATE_SUBNET_EXCEPTION))
        assertTrue(w.endsWith("192.168.1.202 is on this device's private subnet."))
        assertTrue(TlsCheck.plainHttpWarning("http://192.168.7.1/x", wifi)!!.contains("NOT on this device's private subnet"))
        assertTrue(TlsCheck.plainHttpWarning("http://laptop.local/x", wifi)!!.contains("cannot be told from its name"))
    }
}
