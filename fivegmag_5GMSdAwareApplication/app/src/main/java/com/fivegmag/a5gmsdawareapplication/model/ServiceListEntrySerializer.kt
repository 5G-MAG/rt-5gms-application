/*
License: 5G-MAG Public License (v1.0)
Author: Daniel Silhavy
Copyright: (C) 2024 Fraunhofer FOKUS
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.a5gmsdawareapplication.model

import com.fivegmag.a5gmscommonlibrary.models.EntryPoint
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
                        // an AbsoluteMediaEntryPoint has a contentType or a protocol (TS 26.510 V19.2.0 table 9.2.3.1-1)
                        ep.contentType?.let { put("contentType", it) }
                        ep.protocol?.let { put("protocol", it) }
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
            put("externalServiceId", entry.externalServiceId)
            put("name", entry.name)
            put("entryPoints", entryPoints)
        }

        return jsonObject.toString()
    }
}

/** Reads an entry point of the M8 service list or of the Intent extra: locator, contentType or protocol, profiles */
object EntryPointJson {
    fun fromJson(entryPoint: JsonObject): EntryPoint {
        val locator = entryPoint["locator"]?.jsonPrimitive?.contentOrNull ?: ""
        val contentType = entryPoint["contentType"]?.jsonPrimitive?.contentOrNull
        val protocol = entryPoint["protocol"]?.jsonPrimitive?.contentOrNull
        val profiles = ArrayList<String>()
        entryPoint["profiles"]?.jsonArray?.forEach { profiles.add(it.jsonPrimitive.content) }
        return EntryPoint(locator, contentType, profiles, protocol)
    }
}
