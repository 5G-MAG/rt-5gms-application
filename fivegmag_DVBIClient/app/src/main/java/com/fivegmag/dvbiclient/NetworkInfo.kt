/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient

import android.content.Context
import android.net.ConnectivityManager
import com.fivegmag.dvbiclient.http.TlsCheck
import java.net.Inet4Address

/** This device's IPv4 addresses on the active network, with their prefix lengths. */
object NetworkInfo {
    fun localAddresses(context: Context): List<TlsCheck.LocalAddress> {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return emptyList()
        return lp.linkAddresses.mapNotNull { la ->
            val a = la.address as? Inet4Address ?: return@mapNotNull null
            a.hostAddress?.let { TlsCheck.LocalAddress(it, la.prefixLength) }
        }
    }
}
