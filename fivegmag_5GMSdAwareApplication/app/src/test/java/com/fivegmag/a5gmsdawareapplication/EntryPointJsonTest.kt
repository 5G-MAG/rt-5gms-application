package com.fivegmag.a5gmsdawareapplication

import com.fivegmag.a5gmscommonlibrary.models.ServiceListEntry
import com.fivegmag.a5gmsdawareapplication.model.EntryPointJson
import com.fivegmag.a5gmsdawareapplication.model.ServiceListEntrySerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** The entry points of the M8 service list are those of the Service Access Information (TS 26.510 V19.2.0 table 9.2.3.1-1): contentType or protocol */
class EntryPointJsonTest {
    @Test
    fun anEntryPointWithAContentTypeKeepsItsProfilesWithoutQuotes() {
        val ep = EntryPointJson.fromJson(Json.parseToJsonElement(
            """{"locator":"https://m4.example/a.mpd","contentType":"application/dash+xml","profiles":["urn:mpeg:dash:profile:isoff-live:2011"]}"""
        ).jsonObject)
        assertEquals("application/dash+xml", ep.contentType)
        assertNull(ep.protocol)
        assertEquals(listOf("urn:mpeg:dash:profile:isoff-live:2011"), ep.profiles)
    }

    @Test
    fun anEntryPointWithAProtocolAndNoContentTypeIsKept() {
        val ep = EntryPointJson.fromJson(Json.parseToJsonElement(
            """{"locator":"https://m4.example/b","protocol":"urn:3gpp:5gms:content-protocol:http-pull"}"""
        ).jsonObject)
        assertNull(ep.contentType)
        assertEquals("urn:3gpp:5gms:content-protocol:http-pull", ep.protocol)
    }

    @Test
    fun theSerializedEntryPointsReadBackAndCarryTheProtocolOnlyWhenThereIsOne() {
        val entry = ServiceListEntry("com.example.svc", "name", arrayListOf(
            com.fivegmag.a5gmscommonlibrary.models.EntryPoint("https://m4.example/a.mpd", "application/dash+xml", arrayListOf("p")),
            com.fivegmag.a5gmscommonlibrary.models.EntryPoint("https://m4.example/b", null, arrayListOf(), "urn:3gpp:5gms:content-protocol:http-pull")
        ))
        val json = Json.parseToJsonElement(ServiceListEntrySerializer.toJson(entry)).jsonObject
        val eps = json["entryPoints"]!!.jsonArray.map { it.jsonObject }
        assertFalse(eps[0].containsKey("protocol"))
        assertFalse(eps[1].containsKey("contentType"))
        assertEquals(entry.entryPoints, eps.map { EntryPointJson.fromJson(it) })
    }
}
