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
import com.fivegmag.a5gmsdawareapplication.model.AppConfig
import com.fivegmag.a5gmsdawareapplication.model.M8Source
import com.fivegmag.a5gmsdawareapplication.network.IConfigApi
import kotlinx.serialization.json.*
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

const val TAG_CONFIG_PROVIDER = "ConfigProvider"
const val DEFAULT_CONFIG_FILE = "app_config.json"

/**
 * Loads and parses the application configuration (app_config.json)
 * from local assets or a remote endpoint.
 */
class ConfigProvider {

    /**
     * Loads app_config.json from the local assets directory.
     */
    fun loadFromAssets(assets: AssetManager): AppConfig {
        return try {
            val inputStream = assets.open(DEFAULT_CONFIG_FILE)
            val json = inputStream.bufferedReader().use { it.readText() }
            parseConfigJson(json)
        } catch (e: Exception) {
            Log.e(TAG_CONFIG_PROVIDER, "Failed to load local config: ${e.message}")
            AppConfig(emptyList())
        }
    }

    /**
     * Loads app_config.json from a remote URL.
     */
    fun loadFromEndpoint(
        configUrl: String,
        iConfigApi: IConfigApi,
        callback: (AppConfig) -> Unit
    ) {
        try {
            val call: Call<ResponseBody>? = iConfigApi.fetchConfiguration(configUrl)
            call?.enqueue(object : Callback<ResponseBody?> {
                override fun onResponse(
                    call: Call<ResponseBody?>,
                    response: Response<ResponseBody?>
                ) {
                    val resource: String? = response.body()?.string()
                    if (resource != null) {
                        try {
                            val config = parseConfigJson(resource)
                            callback(config)
                        } catch (e: Exception) {
                            Log.e(TAG_CONFIG_PROVIDER, "Failed to parse remote config: ${e.message}")
                            callback(AppConfig(emptyList()))
                        }
                    } else {
                        callback(AppConfig(emptyList()))
                    }
                }

                override fun onFailure(call: Call<ResponseBody?>, t: Throwable) {
                    Log.e(TAG_CONFIG_PROVIDER, "Failed to fetch remote config: ${t.message}")
                    call.cancel()
                    callback(AppConfig(emptyList()))
                }
            })
        } catch (e: Exception) {
            Log.e(TAG_CONFIG_PROVIDER, "Error loading remote config: ${e.message}")
            callback(AppConfig(emptyList()))
        }
    }

    private fun parseConfigJson(json: String): AppConfig {
        val jsonObject = Json.parseToJsonElement(json).jsonObject
        val sourcesArray = jsonObject["sources"]?.jsonArray ?: return AppConfig(emptyList())

        val sources = ArrayList<M8Source>()
        for (sourceElement in sourcesArray) {
            val sourceObj = sourceElement.jsonObject
            val name = sourceObj["name"]?.jsonPrimitive?.content ?: continue
            val m8Url = sourceObj["m8Url"]?.jsonPrimitive?.content ?: continue
            val metadataUrl = if (sourceObj.containsKey("metadataUrl")) {
                sourceObj["metadataUrl"]?.jsonPrimitive?.content
            } else {
                null
            }
            sources.add(M8Source(name, m8Url, metadataUrl))
        }

        return AppConfig(sources)
    }
}
