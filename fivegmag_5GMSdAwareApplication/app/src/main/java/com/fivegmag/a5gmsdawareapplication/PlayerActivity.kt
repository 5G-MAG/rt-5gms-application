/*
License: 5G-MAG Public License (v1.0)
Author: Daniel Silhavy
Copyright: (C) 2024 Fraunhofer FOKUS
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.a5gmsdawareapplication

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.fivegmag.a5gmscommonlibrary.models.EntryPoint
import com.fivegmag.a5gmscommonlibrary.models.ServiceListEntry
import com.fivegmag.a5gmsmediastreamhandler.MediaSessionHandlerAdapter
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.ExoPlayerAdapter
import kotlinx.serialization.json.*
import org.greenrobot.eventbus.EventBus

/**
 * Fullscreen player activity. Initializes the MediaSessionHandlerAdapter,
 * sets up ExoPlayer, and begins playback of the given ServiceListEntry.
 */
@UnstableApi
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SERVICE_LIST_ENTRY_JSON = "service_list_entry_json"
        const val EXTRA_M5_BASE_URL = "m5_base_url"
        const val EXTRA_TITLE = "title"
    }

    private val mediaSessionHandlerAdapter = MediaSessionHandlerAdapter()
    private val mediaStreamHandlerEventHandler = MediaStreamHandlerEventHandler()
    private lateinit var exoPlayerAdapter: ExoPlayerAdapter
    private lateinit var exoPlayerView: PlayerView
    private lateinit var serviceListEntry: ServiceListEntry
    private var m5BaseUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val entryJson = intent.getStringExtra(EXTRA_SERVICE_LIST_ENTRY_JSON)
        if (entryJson == null) {
            finish()
            return
        }

        serviceListEntry = deserializeServiceListEntry(entryJson)
        m5BaseUrl = intent.getStringExtra(EXTRA_M5_BASE_URL) ?: ""

        exoPlayerView = findViewById(R.id.playerView)
        val representationInfoTextView = findViewById<TextView>(R.id.representation_info)
        mediaStreamHandlerEventHandler.initialize(representationInfoTextView, this)

        mediaSessionHandlerAdapter.initialize(
            this,
            ::onConnectionToMediaSessionHandlerEstablished
        )
    }

    private fun onConnectionToMediaSessionHandlerEstablished() {
        exoPlayerAdapter = mediaSessionHandlerAdapter.getExoPlayerAdapter()
        exoPlayerAdapter.initialize(exoPlayerView, this)
        mediaSessionHandlerAdapter.setM5Endpoint(m5BaseUrl)
        mediaSessionHandlerAdapter.initializePlaybackByServiceListEntry(serviceListEntry)
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(mediaStreamHandlerEventHandler)
    }

    override fun onStop() {
        EventBus.getDefault().unregister(mediaStreamHandlerEventHandler)
        super.onStop()
        try {
            exoPlayerAdapter.stop()
        } catch (_: Exception) {
        }
        mediaSessionHandlerAdapter.reset()
    }

    private fun deserializeServiceListEntry(json: String): ServiceListEntry {
        val jsonObject = Json.parseToJsonElement(json).jsonObject
        val provisioningSessionId = jsonObject["provisioningSessionId"]?.jsonPrimitive?.content ?: ""
        val name = jsonObject["name"]?.jsonPrimitive?.content ?: ""

        val entryPoints = ArrayList<EntryPoint>()
        val entryPointsArray = jsonObject["entryPoints"]?.jsonArray
        if (entryPointsArray != null) {
            for (ep in entryPointsArray) {
                val epObj = ep.jsonObject
                val locator = epObj["locator"]?.jsonPrimitive?.content ?: ""
                val contentType = epObj["contentType"]?.jsonPrimitive?.content ?: ""
                val profiles = ArrayList<String>()
                val profilesArray = epObj["profiles"]?.jsonArray
                if (profilesArray != null) {
                    for (profile in profilesArray) {
                        profiles.add(profile.jsonPrimitive.content)
                    }
                }
                entryPoints.add(EntryPoint(locator, contentType, profiles))
            }
        }

        return ServiceListEntry(provisioningSessionId, name, entryPoints)
    }
}
