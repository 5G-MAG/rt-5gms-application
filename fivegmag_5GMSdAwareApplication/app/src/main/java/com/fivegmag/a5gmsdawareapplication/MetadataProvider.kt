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
import java.net.URI

const val TAG_METADATA_PROVIDER = "MetadataProvider"

/**
 * Loads content metadata from local asset files or remote endpoints.
 * Metadata is mapped to M8 service list entries via the entry name.
 */
class MetadataProvider {

    private var metadataMap: HashMap<String, ContentMetadata> = HashMap()

    /**
     * Single entry point for loading metadata.
     *
     * @param m8Url The M8 URL (used to derive the metadata URL if explicitMetadataUrl is null)
     * @param explicitMetadataUrl Explicit metadata URL from the app config.
     *        null = derive from m8Url by convention.
     *        "" (empty) = skip metadata loading entirely.
     *        Otherwise used as-is (relative path = local asset, absolute URL = remote).
     * @param assets AssetManager for local file loading
     * @param iConfigApi Retrofit API for remote loading
     * @param callback Called when metadata loading completes (success or failure)
     */
    fun loadMetadata(
        m8Url: String,
        explicitMetadataUrl: String?,
        assets: AssetManager,
        iConfigApi: IConfigApi,
        callback: () -> Unit
    ) {
        // Explicit empty string means no metadata
        if (explicitMetadataUrl == "") {
            metadataMap.clear()
            callback()
            return
        }

        // Determine the actual metadata URL to use
        val metadataUrl = explicitMetadataUrl ?: deriveMetadataUrl(m8Url)

        if (metadataUrl.isEmpty()) {
            metadataMap.clear()
            callback()
            return
        }

        // Determine if it's a local asset path or a remote URL
        try {
            val uri = URI(metadataUrl)
            if (uri.isAbsolute) {
                loadFromEndpoint(metadataUrl, iConfigApi, callback)
            } else {
                loadFromAssets(assets, metadataUrl)
                callback()
            }
        } catch (e: Exception) {
            Log.d(TAG_METADATA_PROVIDER, "Failed to load metadata: ${e.message}")
            metadataMap.clear()
            callback()
        }
    }

    fun getMetadataForEntry(entryName: String): ContentMetadata? {
        return metadataMap[entryName]
    }

    private fun loadFromAssets(assets: AssetManager, metadataPath: String) {
        try {
            val inputStream = assets.open(metadataPath)
            val json = inputStream.bufferedReader().use { it.readText() }
            parseMetadataJson(json)
        } catch (e: Exception) {
            Log.d(TAG_METADATA_PROVIDER, "No local metadata found at $metadataPath")
            metadataMap.clear()
        }
    }

    private fun loadFromEndpoint(
        metadataUrl: String,
        iConfigApi: IConfigApi,
        callback: () -> Unit
    ) {
        try {
            val call: Call<ResponseBody>? = iConfigApi.fetchConfiguration(metadataUrl)
            if (call != null) {
                call.enqueue(object : Callback<ResponseBody?> {
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
                        Log.d(TAG_METADATA_PROVIDER, "Failed to fetch remote metadata: ${t.message}")
                        metadataMap.clear()
                        call.cancel()
                        callback()
                    }
                })
            } else {
                Log.d(TAG_METADATA_PROVIDER, "Metadata call was null for: $metadataUrl")
                metadataMap.clear()
                callback()
            }
        } catch (_: Exception) {
            metadataMap.clear()
            callback()
        }
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

    /**
     * Derives the metadata URL from the M8 URL by convention.
     * - Remote: http://host/path/m8.json -> http://host/path/metadata.json
     * - Local: m8/config_multi_media.json -> metadata/config_multi_media_metadata.json
     */
    private fun deriveMetadataUrl(m8Url: String): String {
        return try {
            val uri = URI(m8Url)
            if (uri.isAbsolute) {
                // Remote: replace filename with metadata.json
                m8Url.substringBeforeLast('/') + "/metadata.json"
            } else {
                // Local: m8/<key>.json -> metadata/<key>_metadata.json
                val filename = m8Url.substringAfterLast("/").substringBeforeLast(".")
                "metadata/${filename}_metadata.json"
            }
        } catch (_: Exception) {
            ""
        }
    }
}
