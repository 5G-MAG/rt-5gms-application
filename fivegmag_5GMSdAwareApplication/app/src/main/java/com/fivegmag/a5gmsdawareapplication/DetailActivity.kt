/*
License: 5G-MAG Public License (v1.0)
Author: Daniel Silhavy
Copyright: (C) 2024 Fraunhofer FOKUS
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.a5gmsdawareapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.google.android.material.appbar.MaterialToolbar

/**
 * Detail screen for a content item. Shows the poster image, title, description,
 * and a Play button to launch playback via PlayerActivity.
 */
class DetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TITLE = "detail_title"
        const val EXTRA_DESCRIPTION = "detail_description"
        const val EXTRA_POSTER_URL = "detail_poster_url"
        const val EXTRA_MEDIA_TYPE = "detail_media_type"
        const val EXTRA_SERVICE_LIST_ENTRY_JSON = "detail_service_list_entry_json"
        const val EXTRA_M5_BASE_URL = "detail_m5_base_url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val description = intent.getStringExtra(EXTRA_DESCRIPTION) ?: ""
        val posterUrl = intent.getStringExtra(EXTRA_POSTER_URL) ?: ""
        val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE) ?: "movie"
        val serviceListEntryJson = intent.getStringExtra(EXTRA_SERVICE_LIST_ENTRY_JSON) ?: ""
        val m5BaseUrl = intent.getStringExtra(EXTRA_M5_BASE_URL) ?: ""

        if (title.isEmpty()) {
            finish()
            return
        }

        setupToolbar(title)
        displayContent(title, description, posterUrl, mediaType)
        setupPlayButton(serviceListEntryJson, m5BaseUrl, title)
    }

    private fun setupToolbar(title: String) {
        val toolbar = findViewById<MaterialToolbar>(R.id.detailToolbar)
        toolbar.title = title
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun displayContent(
        title: String,
        description: String,
        posterUrl: String,
        mediaType: String
    ) {
        val posterImage = findViewById<ImageView>(R.id.detailPosterImage)
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

    private fun setupPlayButton(serviceListEntryJson: String, m5BaseUrl: String, title: String) {
        val playButton = findViewById<Button>(R.id.playButton)
        playButton.setOnClickListener {
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra(PlayerActivity.EXTRA_SERVICE_LIST_ENTRY_JSON, serviceListEntryJson)
            intent.putExtra(PlayerActivity.EXTRA_M5_BASE_URL, m5BaseUrl)
            intent.putExtra(PlayerActivity.EXTRA_TITLE, title)
            startActivity(intent)
        }
    }
}
