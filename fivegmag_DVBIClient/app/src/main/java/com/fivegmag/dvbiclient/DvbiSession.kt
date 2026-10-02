/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient

import com.fivegmag.dvbiclient.mbms.IMbmsStreamingClient
import com.fivegmag.dvbiclient.mbms.NoMbmsClient
import com.fivegmag.dvbiclient.servicelist.ServiceList

/**
 * State shared by the activities while the process runs: the installed service list and the MBMS
 * Client in use.
 */
object DvbiSession {
    @Volatile
    var serviceList: ServiceList? = null

    /** Replaced by an Android MBMS Client implementation when one exists. */
    var mbmsClient: IMbmsStreamingClient = NoMbmsClient

    /** Whether [mbmsClient] accepted this client's registration (TS 26.347 clause 6.3.3.3). */
    @Volatile
    var mbmsRegistered: Boolean = false
}
