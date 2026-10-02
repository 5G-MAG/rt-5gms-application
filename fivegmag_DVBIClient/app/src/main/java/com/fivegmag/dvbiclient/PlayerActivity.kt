/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient

import android.content.Context
import android.content.Intent
import android.media.MediaDrm
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.fivegmag.dvbiclient.mbms.IMbmsStreamingClient
import com.fivegmag.dvbiclient.mbms.MbmsReception
import com.fivegmag.dvbiclient.mbms.MbmsUrl
import com.fivegmag.dvbiclient.servicelist.Delivery
import com.fivegmag.dvbiclient.servicelist.Service
import com.fivegmag.dvbiclient.servicelist.ServiceInstance
import com.fivegmag.dvbiclient.servicelist.ServiceSelection

const val TAG_PLAYER = "DVB-I Player"

/**
 * Plays one service of the installed service list with Media3 ExoPlayer, choosing the instance by
 * the precedence of ETSI TS 103 770 V1.2.1 clause 5.2.13 and falling back to the next instance when
 * one fails to play.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var instanceText: TextView
    private lateinit var discardedText: TextView
    private var player: ExoPlayer? = null
    private var selection: ServiceSelection? = null
    private var current: Int? = null
    private val handler = Handler(Looper.getMainLooper())
    private val reevaluate = Runnable { onScheduledHoursChanged() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.playerView)
        instanceText = findViewById(R.id.instanceText)
        discardedText = findViewById(R.id.discardedText)

        val uid = intent.getStringExtra(EXTRA_SERVICE_UID)
        val service = DvbiSession.serviceList?.services?.firstOrNull { it.uid == uid }
        if (service == null) {
            instanceText.text = getString(R.string.service_not_found)
            return
        }
        title = service.name
        findViewById<TextView>(R.id.serviceTitle).text = service.name
        showFiveG(service)
        selection = ServiceSelection(service, DeviceCapabilities.current(clientPackages()))
    }

    /** Subscription packages the user associated this client with; none until settings set them. */
    private fun clientPackages(): List<String> = intent.getStringArrayListExtra(EXTRA_PACKAGES) ?: emptyList()

    override fun onStart() {
        super.onStart()
        val sel = selection ?: return
        val exo = ExoPlayer.Builder(this).build()
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onInstanceFailed(error)
            }
        })
        playerView.player = exo
        player = exo
        play(sel.select(System.currentTimeMillis()))
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(reevaluate)
        playerView.player = null
        player?.release()
        player = null
        current = null
    }

    // 5G Broadcast instances: the locator, its check against TS 26.347 clause 8.2.2, and why it is
    // not received.
    private fun showFiveG(service: Service) {
        val mbms = service.instances.mapNotNull { it.delivery as? Delivery.Mbms }
        if (mbms.isEmpty()) return
        findViewById<View>(R.id.fiveGBadge).visibility = View.VISIBLE
        val detail = findViewById<TextView>(R.id.fiveGDetail)
        detail.visibility = View.VISIBLE
        detail.text = mbms.joinToString("\n\n") { d ->
            val problem = MbmsUrl.problem(d.locator)
            buildString {
                append("Locator: ${d.locator}\n")
                append("serviceId: ${MbmsUrl.serviceId(d.locator)}\n")
                append(if (problem == null) "Valid MBMS URL (3GPP TS 26.347 V18.1.0 clause 8.2.2)" else "Invalid: $problem")
                if (!DvbiSession.mbmsRegistered) append("\nNot received: no MBMS Client is available on this device.")
            }
        }
    }

    private fun play(index: Int?) {
        val sel = selection ?: return
        val exo = player ?: return
        val now = System.currentTimeMillis()
        current = index
        showDiscarded(sel, now)
        scheduleReevaluation(sel, now)
        if (index == null) {
            exo.stop()
            instanceText.text = getString(R.string.no_playable_instance)
            return
        }
        val inst = sel.service.instances[index]
        instanceText.text = "Playing: ${inst.label}\n${describe(inst.delivery)}"
        when (val d = inst.delivery) {
            is Delivery.Dash -> start(exo, d.url, MimeTypes.APPLICATION_MPD, inst)
            is Delivery.Hls -> start(exo, d.url, MimeTypes.APPLICATION_M3U8, inst)
            is Delivery.Mbms -> startMbms(exo, d, inst)
            else -> play(sel.fail(index, now))
        }
    }

    private fun start(exo: ExoPlayer, url: String, mimeType: String, inst: ServiceInstance) {
        val item = MediaItem.Builder().setUri(url).setMimeType(mimeType)
        // A DRM system the device supports (clause 5.2.13 discarded the instance otherwise). The
        // service list carries no licence server, so the one the media signals is used.
        inst.protection?.drmSystems?.firstNotNullOfOrNull { DeviceCapabilities.drmUuid(it)?.takeIf { u -> MediaDrm.isCryptoSchemeSupported(u) } }
            ?.let { item.setDrmConfiguration(MediaItem.DrmConfiguration.Builder(it).build()) }
        exo.setMediaItem(item.build())
        exo.prepare()
        exo.playWhenReady = true
    }

    // TS 103 770 clause 9.3.3, through the MBMS Client. Only reached when an MBMS Client accepted
    // the registration; with NoMbmsClient the instance is discarded before.
    private fun startMbms(exo: ExoPlayer, d: Delivery.Mbms, inst: ServiceInstance) {
        val client = DvbiSession.mbmsClient
        val serviceId = MbmsUrl.serviceId(d.locator)
        client.registerStreamingApp(packageName, listOf(IMbmsStreamingClient.DVBI_SERVICE_INSTANCE_CLASS), object : IMbmsStreamingClient.Callback {
            override fun registerStreamingResponse(success: Boolean, message: String) {
                if (success) client.startStreamingService(serviceId) else runOnUiThread { onInstanceFailed(null) }
            }

            override fun streamingServiceListUpdate() {}

            override fun serviceStarted(serviceId: String) {
                val entry = MbmsReception.entryPoint(client.getStreamingServices(), d.locator)
                runOnUiThread {
                    if (entry == null) onInstanceFailed(null)
                    else start(exo, entry.second, entry.first, inst)
                }
            }

            override fun streamingServiceError(serviceId: String, message: String) {
                Log.w(TAG_PLAYER, "MBMS service $serviceId: $message")
                runOnUiThread { onInstanceFailed(null) }
            }
        })
    }

    private fun onInstanceFailed(error: PlaybackException?) {
        val sel = selection ?: return
        val index = current ?: return
        Log.w(TAG_PLAYER, "Instance ${sel.service.instances[index].label} failed: ${error?.errorCodeName}", error)
        play(sel.fail(index, System.currentTimeMillis()))
    }

    // Clause 5.2.13: "When one of the service instances of the currently selected service changes
    // from being inside their scheduled service hours to being outside or vice-versa, the selected
    // service instance shall be re-evaluated."
    private fun scheduleReevaluation(sel: ServiceSelection, now: Long) {
        handler.removeCallbacks(reevaluate)
        val at = sel.nextReevaluation(now) ?: return
        handler.postDelayed(reevaluate, maxOf(0L, at - now) + 1)
    }

    private fun onScheduledHoursChanged() {
        val sel = selection ?: return
        val next = sel.select(System.currentTimeMillis())
        if (next != current) play(next) else scheduleReevaluation(sel, System.currentTimeMillis())
    }

    private fun showDiscarded(sel: ServiceSelection, now: Long) {
        val reasons = sel.reasons(now)
        discardedText.text = sel.service.instances.indices
            .filter { reasons[it] != null }
            .joinToString("\n") { "Not played: ${sel.service.instances[it].label} (priority ${sel.service.instances[it].priority}): ${reasons[it]}" }
    }

    private fun describe(d: Delivery): String = when (d) {
        is Delivery.Dash -> "DASH: ${d.url}"
        is Delivery.Hls -> "HLS (${d.signalledBy}): ${d.url}"
        is Delivery.Mbms -> "5G Broadcast: ${d.locator}"
        else -> d.toString()
    }

    companion object {
        const val EXTRA_SERVICE_UID = "serviceUid"
        const val EXTRA_PACKAGES = "packages"

        fun intent(context: Context, uid: String, packages: List<String>): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_SERVICE_UID, uid)
                .putStringArrayListExtra(EXTRA_PACKAGES, ArrayList(packages))
    }
}
