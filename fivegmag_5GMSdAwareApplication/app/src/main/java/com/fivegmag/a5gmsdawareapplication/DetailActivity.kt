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
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.load
import com.fivegmag.a5gmscommonlibrary.models.EntryPoint
import com.fivegmag.a5gmscommonlibrary.models.ServiceListEntry
import com.fivegmag.a5gmsmediastreamhandler.MediaSessionHandlerAdapter
import com.fivegmag.a5gmsmediastreamhandler.player.exoplayer.ExoPlayerAdapter
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.serialization.json.*
import org.greenrobot.eventbus.EventBus

/**
 * Detail screen for a content item. Shows the poster image, title, description,
 * and a Play button. When Play is tapped the poster is replaced with an inline
 * ExoPlayer view and playback begins.
 */
@UnstableApi
class DetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TITLE = "detail_title"
        const val EXTRA_DESCRIPTION = "detail_description"
        const val EXTRA_POSTER_URL = "detail_poster_url"
        const val EXTRA_MEDIA_TYPE = "detail_media_type"
        const val EXTRA_SERVICE_LIST_ENTRY_JSON = "detail_service_list_entry_json"
        const val EXTRA_M5_BASE_URL = "detail_m5_base_url"
    }

    private lateinit var posterImage: ImageView
    private lateinit var playerView: PlayerView
    private lateinit var representationInfo: TextView
    private lateinit var posterGradient: View
    private lateinit var playButton: Button

    private var mediaSessionHandlerAdapter: MediaSessionHandlerAdapter? = null
    private var exoPlayerAdapter: ExoPlayerAdapter? = null
    private val mediaStreamHandlerEventHandler = MediaStreamHandlerEventHandler()
    private var isPlaying = false

    private var serviceListEntryJson: String = ""
    private var m5BaseUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val description = intent.getStringExtra(EXTRA_DESCRIPTION) ?: ""
        val posterUrl = intent.getStringExtra(EXTRA_POSTER_URL) ?: ""
        val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE) ?: "movie"
        serviceListEntryJson = intent.getStringExtra(EXTRA_SERVICE_LIST_ENTRY_JSON) ?: ""
        m5BaseUrl = intent.getStringExtra(EXTRA_M5_BASE_URL) ?: ""

        if (title.isEmpty()) {
            finish()
            return
        }

        posterImage = findViewById(R.id.detailPosterImage)
        playerView = findViewById(R.id.detailPlayerView)
        representationInfo = findViewById(R.id.representation_info)
        posterGradient = findViewById(R.id.posterGradient)
        playButton = findViewById(R.id.playButton)

        setupToolbar(title)
        displayContent(title, description, posterUrl, mediaType)
        setupPlayButton()
    }

    private fun setupToolbar(title: String) {
        val toolbar = findViewById<MaterialToolbar>(R.id.detailToolbar)
        toolbar.title = title
        toolbar.setNavigationOnClickListener {
            stopPlaybackIfActive()
            finish()
        }
    }

    private fun displayContent(
        title: String,
        description: String,
        posterUrl: String,
        mediaType: String
    ) {
        val titleView = findViewById<TextView>(R.id.detailTitle)
        val descriptionView = findViewById<TextView>(R.id.detailDescription)
        val mediaTypeBadge = findViewById<TextView>(R.id.detailMediaTypeBadge)

        titleView.text = title

        if (description.isNotEmpty()) {
            descriptionView.text = description
        } else {
            descriptionView.text = getString(R.string.no_description_available)
        }

        // Set media type badge
        if (mediaType == "tv_show") {
            mediaTypeBadge.text = getString(R.string.media_type_tv_show)
            mediaTypeBadge.setBackgroundResource(R.drawable.bg_badge_tv)
        } else {
            mediaTypeBadge.text = getString(R.string.media_type_movie)
            mediaTypeBadge.setBackgroundResource(R.drawable.bg_badge_movie)
        }

        // Load poster image
        if (posterUrl.isNotEmpty()) {
            posterImage.load(posterUrl) {
                crossfade(true)
                placeholder(R.drawable.bg_poster_placeholder)
                error(R.drawable.bg_poster_placeholder)
            }
        }
    }

    private fun setupPlayButton() {
        playButton.setOnClickListener {
            if (!isPlaying) {
                startPlayback()
            }
        }
    }

    private fun startPlayback() {
        if (serviceListEntryJson.isEmpty()) return

        // Swap poster for player
        posterImage.visibility = View.GONE
        posterGradient.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        representationInfo.visibility = View.VISIBLE
        playButton.isEnabled = false
        playButton.text = getString(R.string.player_title)

        isPlaying = true

        // Initialize event handler and register for EventBus events
        mediaStreamHandlerEventHandler.initialize(representationInfo, this)
        EventBus.getDefault().register(mediaStreamHandlerEventHandler)

        // Initialize MediaSessionHandler and start playback
        val adapter = MediaSessionHandlerAdapter()
        mediaSessionHandlerAdapter = adapter
        adapter.initialize(this) {
            onConnectionToMediaSessionHandlerEstablished()
        }
    }

    private fun onConnectionToMediaSessionHandlerEstablished() {
        val adapter = mediaSessionHandlerAdapter ?: return
        val playerAdapter = adapter.getExoPlayerAdapter()
        exoPlayerAdapter = playerAdapter
        playerAdapter.initialize(playerView, this)

        val serviceListEntry = deserializeServiceListEntry(serviceListEntryJson)
        adapter.setM5Endpoint(m5BaseUrl)
        adapter.initializePlaybackByServiceListEntry(serviceListEntry)
    }

    override fun onStart() {
        super.onStart()
        if (isPlaying && !EventBus.getDefault().isRegistered(mediaStreamHandlerEventHandler)) {
            EventBus.getDefault().register(mediaStreamHandlerEventHandler)
        }
    }

    override fun onStop() {
        if (EventBus.getDefault().isRegistered(mediaStreamHandlerEventHandler)) {
            EventBus.getDefault().unregister(mediaStreamHandlerEventHandler)
        }
        super.onStop()
        stopPlaybackIfActive()
    }

    private fun stopPlaybackIfActive() {
        if (isPlaying) {
            try {
                exoPlayerAdapter?.stop()
            } catch (_: Exception) {
            }
            mediaSessionHandlerAdapter?.reset()
            isPlaying = false
        }
    }

    private fun deserializeServiceListEntry(json: String): ServiceListEntry {
        val jsonObject = Json.parseToJsonElement(json).jsonObject
        val provisioningSessionId =
            jsonObject["provisioningSessionId"]?.jsonPrimitive?.content ?: ""
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
