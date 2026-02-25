/*
License: 5G-MAG Public License (v1.0)
Author: Daniel Silhavy
Copyright: (C) 2024 Fraunhofer FOKUS
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.a5gmsdawareapplication

import android.content.res.AssetManager
import android.util.Log
import com.fivegmag.a5gmsdawareapplication.model.ContentMetadata
import com.fivegmag.a5gmsdawareapplication.network.IConfigApi
import kotlinx.serialization.json.*
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

const val TAG_METADATA_PROVIDER = "MetadataProvider"

/**
 * Loads content metadata from local asset files or remote endpoints.
 * Metadata is mapped to M8 service list entries via the entry name.
 */
class MetadataProvider {

    private var metadataMap: HashMap<String, ContentMetadata> = HashMap()

    /**
     * Tries to load metadata for a given M8 config key from local assets.
     * The naming convention is: m8/<key>.json -> metadata/<key>_metadata.json
     */
    fun loadFromAssets(assets: AssetManager, m8ConfigPath: String) {
        try {
            val metadataPath = deriveMetadataPath(m8ConfigPath)
            val inputStream = assets.open(metadataPath)
            val json = inputStream.bufferedReader().use { it.readText() }
            parseMetadataJson(json)
        } catch (e: Exception) {
            Log.d(TAG_METADATA_PROVIDER, "No local metadata found for $m8ConfigPath")
            metadataMap.clear()
        }
    }

    /**
     * Loads metadata from a remote endpoint.
     * Expects the metadata JSON to be at <baseUrl>/metadata.json
     */
    fun loadFromEndpoint(m8Url: String, iConfigApi: IConfigApi, callback: () -> Unit) {
        try {
            val metadataUrl = m8Url.substringBeforeLast('/') + "/metadata.json"
            val call: Call<ResponseBody>? = iConfigApi.fetchConfiguration(metadataUrl)
            call?.enqueue(object : Callback<ResponseBody?> {
                override fun onResponse(
                    call: Call<ResponseBody?>,
                    response: Response<ResponseBody?>
                ) {
                    val resource: String? = response.body()?.string()
                    if (resource != null) {
                        try {
                            parseMetadataJson(resource)
                        } catch (e: Exception) {
                            Log.d(TAG_METADATA_PROVIDER, "Failed to parse remote metadata")
                            metadataMap.clear()
                        }
                    }
                    callback()
                }

                override fun onFailure(call: Call<ResponseBody?>, t: Throwable) {
                    Log.d(TAG_METADATA_PROVIDER, "Failed to fetch remote metadata")
                    metadataMap.clear()
                    call.cancel()
                    callback()
                }
            })
        } catch (_: Exception) {
            metadataMap.clear()
            callback()
        }
    }

    fun getMetadataForEntry(entryName: String): ContentMetadata? {
        return metadataMap[entryName]
    }

    private fun parseMetadataJson(json: String) {
        metadataMap.clear()
        val jsonObject: JsonObject = Json.parseToJsonElement(json).jsonObject

        for ((key, value) in jsonObject) {
            val entryObject = value.jsonObject
            val title = entryObject["title"]?.jsonPrimitive?.content ?: key
            val description = entryObject["description"]?.jsonPrimitive?.content ?: ""
            val posterUrl = entryObject["posterUrl"]?.jsonPrimitive?.content ?: ""
            val mediaType = entryObject["mediaType"]?.jsonPrimitive?.content ?: "movie"

            metadataMap[key] = ContentMetadata(
                title = title,
                description = description,
                posterUrl = posterUrl,
                mediaType = mediaType
            )
        }
    }

    private fun deriveMetadataPath(m8ConfigPath: String): String {
        // m8/config_multi_media.json -> metadata/config_multi_media_metadata.json
        val filename = m8ConfigPath.substringAfterLast("/").substringBeforeLast(".")
        return "metadata/${filename}_metadata.json"
    }
}
