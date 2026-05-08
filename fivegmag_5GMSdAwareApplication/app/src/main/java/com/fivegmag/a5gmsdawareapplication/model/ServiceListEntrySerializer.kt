/*
License: 5G-MAG Public License (v1.0)
Author: Daniel Silhavy
Copyright: (C) 2024 Fraunhofer FOKUS
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.a5gmsdawareapplication.model

import com.fivegmag.a5gmscommonlibrary.models.ServiceListEntry
import kotlinx.serialization.json.*

/**
 * Serializes a ServiceListEntry to a JSON string for passing via Intent extras.
 * This is needed because ServiceListEntry does not implement Serializable/Parcelable.
 */
object ServiceListEntrySerializer {

    fun toJson(entry: ServiceListEntry): String {
        val entryPoints = buildJsonArray {
            val eps = entry.entryPoints
            if (eps != null) {
                for (ep in eps) {
                    add(buildJsonObject {
                        put("locator", ep.locator)
                        put("contentType", ep.contentType)
                        val profs = ep.profiles
                        put("profiles", buildJsonArray {
                            if (profs != null) {
                                for (profile in profs) {
                                    add(profile)
                                }
                            }
                        })
                    })
                }
            }
        }

        val jsonObject = buildJsonObject {
            put("provisioningSessionId", entry.provisioningSessionId)
            put("name", entry.name)
            put("entryPoints", entryPoints)
        }

        return jsonObject.toString()
    }
}
