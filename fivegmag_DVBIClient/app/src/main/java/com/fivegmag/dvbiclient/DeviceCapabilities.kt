/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient

import android.media.MediaDrm
import com.fivegmag.dvbiclient.servicelist.Instances
import java.util.UUID

/** What this device can play, for instance selection (TS 103 770 V1.2.1 clause 5.2.13). */
object DeviceCapabilities {

    /** The UUID of a DRMSystemId of the form urn:uuid:<uuid> (clause 5.5.20), or null. */
    fun drmUuid(drmSystemId: String): UUID? {
        val s = drmSystemId.trim()
        if (!s.lowercase().startsWith("urn:uuid:")) return null
        return try {
            UUID.fromString(s.substring("urn:uuid:".length))
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Media3 plays DASH and HLS; DRM systems are those the device's MediaDrm supports. */
    fun current(packages: List<String>): Instances.Capabilities = Instances.Capabilities(
        dash = true,
        hls = true,
        drmSupported = { id -> drmUuid(id)?.let { MediaDrm.isCryptoSchemeSupported(it) } ?: false },
        packages = packages,
        mbms = DvbiSession.mbmsRegistered,
    )
}
