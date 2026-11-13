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
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.load
import com.fivegmag.a5gmscommonlibrary.models.EntryPoint
import com.fivegmag.a5gmsdawareapplication.model.EntryPointJson
import com.fivegmag.a5gmscommonlibrary.models.StreamingAccess
import com.fivegmag.a5gmscommonlibrary.models.ServiceListEntry
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerBundleKeys
import com.fivegmag.a5gmscommonlibrary.helpers.SessionHandlerEvents
import com.fivegmag.a5gmsmediastreamhandler.MediaSessionHandlerAdapter
import com.fivegmag.a5gmscommonlibrary.models.DynamicPolicyStatus
import com.fivegmag.a5gmscommonlibrary.models.PolicyTemplateBinding
import com.fivegmag.a5gmsmediastreamhandler.service.BdtInfoReply
import com.fivegmag.a5gmsmediastreamhandler.service.BitRateRecommendationReply
import com.fivegmag.a5gmsmediastreamhandler.service.DeliveryBoostReply
import com.fivegmag.a5gmsmediastreamhandler.service.DynamicPolicyListener
import com.fivegmag.a5gmsmediastreamhandler.service.GeneralSessionListener
import com.fivegmag.a5gmsmediastreamhandler.service.NetworkAssistanceListener
import com.fivegmag.a5gmsmediastreamhandler.service.PolicyActivationReply
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
        const val EXTRA_USE_CREATE_MEDIA_DELIVERY_SESSION = "detail_use_create_media_delivery_session"
        const val EXTRA_ACCESS_TOKEN = "detail_access_token"
        const val EXTRA_SECOND_SERVICE_ID = "detail_second_service_id"
        const val EXTRA_SECOND_PLAYS = "detail_second_plays"
        // A 3GPP Service URL (TS 26.510 V19.2.0 clause 6): when present, the Media Session Handler launches the session and playback from it
        const val EXTRA_SERVICE_URL = "detail_service_url"

        // The Dynamic Policy and Network Assistance controls (TS 26.510 V19.2.0 clauses 11.3, 11.4): all optional
        const val EXTRA_SERVICE_OPERATION_POINT = "detail_service_operation_point"
        // The request per Service Operation Point: "Standard=3 Mbps/1 Mbps;Premium=8 Mbps/1 Mbps" (point=downlink/uplink), TS 26.510 clause 5.3.3.2 item 4
        const val EXTRA_POINT_TARGETS = "detail_point_targets"
        const val EXTRA_COMPONENT_REFERENCE = "detail_component_reference"
        const val EXTRA_ESTIMATED_TRANSFER_VOLUME = "detail_estimated_transfer_volume"
        const val EXTRA_TARGET_DOWNLINK_BIT_RATE = "detail_target_downlink_bit_rate"
        const val EXTRA_TARGET_UPLINK_BIT_RATE = "detail_target_uplink_bit_rate"

        // The Media Player capabilities (TS 26.512 V19.3.0 table 13.2.4-2): the sample player declares none unless an extra says so
        const val EXTRA_L4S_CAPABLE = "detail_l4s_capable"
        const val EXTRA_QOS_MONITORING = "detail_qos_monitoring"
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
    private val serviceUrl: String? get() = intent.getStringExtra(EXTRA_SERVICE_URL)

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
        if (serviceListEntryJson.isEmpty() && serviceUrl == null) return

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
        adapter.setL4sTransportStackPresent(intent.getBooleanExtra(EXTRA_L4S_CAPABLE, false))
        if (intent.getBooleanExtra(EXTRA_QOS_MONITORING, false)) {
            adapter.setQosMonitoringResultsConsumer { id, results -> Log.i("5GMS-App", "QoS monitoring results of [$id]: $results") }
        }
        adapter.initialize(this) {
            onConnectionToMediaSessionHandlerEstablished()
        }
    }

    // The answers and notification events of the general client API (TS 26.510 V19.2.0 clause 11.2)
    private var mediaDeliverySessionIdentifier: String? = null
    private val generalSessionListener = object : GeneralSessionListener {
        override fun onMediaDeliverySessionCreated(mediaDeliverySessionIdentifier: String?, sessionHandlingState: String?) {
            Log.i("5GMS-App", "createMediaDeliverySession: [$mediaDeliverySessionIdentifier] $sessionHandlingState")
        }

        override fun onSessionStatus(mediaDeliverySessionIdentifier: String?, contentIdentifier: String?, sessionHandlingState: String?) {
            Log.i("5GMS-App", "status of [$mediaDeliverySessionIdentifier]: content [$contentIdentifier] $sessionHandlingState")
        }

        override fun onServiceUrlLaunched(httpStatus: Int) {
            Log.i("5GMS-App", "launchServiceUrl: HTTP $httpStatus")
        }

        override fun onConsumptionReportingStatus(mediaDeliverySessionIdentifier: String?, consumptionReport: String?) {
            Log.i("5GMS-App", "consumption report of [$mediaDeliverySessionIdentifier]: $consumptionReport")
        }

        override fun onMetricsReportingStatus(
            mediaDeliverySessionIdentifier: String?,
            provisioningSessionId: String?,
            metricsReportingConfigurationId: String?,
            scheme: String?,
            metricsReport: String?
        ) {
            Log.i("5GMS-App", "last metrics report of [$mediaDeliverySessionIdentifier]: $provisioningSessionId $metricsReportingConfigurationId $scheme ${metricsReport?.length}")
        }

        override fun onNotification(event: String, payload: Bundle) {
            val id = payload.getString(SessionHandlerBundleKeys.MEDIA_DELIVERY_SESSION_IDENTIFIER)
            Log.i("5GMS-App", "notification $event [$id] " + payload.keySet().filter { it != SessionHandlerBundleKeys.EVENT && it != SessionHandlerBundleKeys.MEDIA_DELIVERY_SESSION_IDENTIFIER }.joinToString { k -> k + "=" + payload.get(k) })
            if (event == SessionHandlerEvents.ConsumptionReporting.NEW_CONSUMPTION_REPORT && id != null) {
                mediaSessionHandlerAdapter?.getConsumptionReportingStatus(id)
            }
            if (event == SessionHandlerEvents.MetricsReporting.NEW_METRICS_REPORT && id != null) {
                mediaSessionHandlerAdapter?.getMetricsReportingStatus(id)
            }
            if (event == SessionHandlerEvents.Notification.SESSION_HANDLING_ACTIVATED && id != null) {
                mediaDeliverySessionIdentifier = id
                mediaSessionHandlerAdapter?.getSessionStatus(id)
                showPolicyPanel()
            }
            if (event == SessionHandlerEvents.Notification.STREAMING_ACCESS_UPDATED && id != null && id == mediaDeliverySessionIdentifier) {
                // the streaming access has changed: the player is attached to the entry point that the new streaming access offers
                payload.classLoader = StreamingAccess::class.java.classLoader
                @Suppress("DEPRECATION")
                val streamingAccess: StreamingAccess? = payload.getParcelable(SessionHandlerBundleKeys.STREAMING_ACCESS)
                val next = StreamingAccessUpdate.entryPointToPlay(streamingAccess?.entryPoints)
                if (next != null && StreamingAccessUpdate.needsReattach(runCatching { exoPlayerAdapter?.getCurrentManifestUri() }.getOrNull(), next)) {
                    exoPlayerAdapter?.attach(next.locator, next.contentType!!)
                    exoPlayerAdapter?.preload()
                    exoPlayerAdapter?.play()
                }
            }
            if (event == SessionHandlerEvents.NetworkAssistance.BIT_RATE_RECOMMENDATION) {
                // a recommendation that the Media AF pushed: the player is kept to the representations that fit it, as for a requested one
                BitRateRecommendationUse.ceilingOfNotification(id, mediaDeliverySessionIdentifier, payload.getString(SessionHandlerBundleKeys.RECOMMENDED_DOWNLINK_BIT_RATE))
                    ?.let { limitVideoBitrate(it) }
            }
            if (event in policyEvents) {
                logPolicy(event + " " + payload.keySet().filter { it != SessionHandlerBundleKeys.EVENT && it != SessionHandlerBundleKeys.MEDIA_DELIVERY_SESSION_IDENTIFIER }
                    .joinToString { k -> k + "=" + payload.get(k) })
            }
        }
    }

    // The events of tables 11.3.2-2, 11.3.2-3, 11.4.2-2 and 11.4.2-3 are shown in the panel
    private val policyEvents = setOf(
        SessionHandlerEvents.DynamicPolicy.POLICY_ACTIVATED,
        SessionHandlerEvents.DynamicPolicy.POLICY_DEACTIVATED,
        SessionHandlerEvents.DynamicPolicy.BACKGROUND_DATA_TRANSFER_OPPORTUNITY,
        SessionHandlerEvents.DynamicPolicy.L4S_ENABLED,
        SessionHandlerEvents.DynamicPolicy.QOS_MONITORING_ENABLED,
        SessionHandlerEvents.DynamicPolicy.QOS_MONITORING_RESULTS,
        SessionHandlerEvents.Error.ERROR_INVALID_SERVICE_OPERATION_POINT,
        SessionHandlerEvents.Error.ERROR_UNAUTHORISED,
        SessionHandlerEvents.Error.ERROR_BACKGROUND_DATA_TRANSFER,
        SessionHandlerEvents.NetworkAssistance.BIT_RATE_RECOMMENDATION,
        SessionHandlerEvents.Error.ERROR_BIT_RATE_RECOMMENDATION,
        SessionHandlerEvents.Error.ERROR_DELIVERY_BOOST
    )

    // The Service Operation Points of the media delivery session; the one that the controls use is that of the intent or the first
    private var serviceOperationPoints: List<PolicyTemplateBinding> = emptyList()
    private val policyLines = ArrayDeque<String>()

    // The point chosen in the selector wins over the one of the intent, which wins over the first point
    private var chosenServiceOperationPoint: String? = null

    // The bit rates (downlink, uplink) that the launch extra gives for a point, or null
    private fun pointTarget(point: String): Pair<String?, String?>? =
        intent.getStringExtra(EXTRA_POINT_TARGETS)?.split(';')?.map { it.trim() }
            ?.firstOrNull { it.substringBefore('=').trim() == point }
            ?.substringAfter('=', "")?.split('/')
            ?.let { it.getOrNull(0)?.trim()?.ifEmpty { null } to it.getOrNull(1)?.trim()?.ifEmpty { null } }

    private fun selectedServiceOperationPoint(): String? =
        chosenServiceOperationPoint ?: intent.getStringExtra(EXTRA_SERVICE_OPERATION_POINT) ?: serviceOperationPoints.firstOrNull()?.externalReference

    private fun fillPointSelector(points: List<PolicyTemplateBinding>) {
        val spinner = findViewById<Spinner>(R.id.policyPointSpinner) ?: return
        val names = points.map { it.externalReference }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        val current = selectedServiceOperationPoint()
        names.indexOf(current).takeIf { it >= 0 }?.let { spinner.setSelection(it) }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                chosenServiceOperationPoint = names.getOrNull(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun logPolicy(line: String) {
        Log.i("5GMS-App", line)
        policyLines.addLast(line)
        while (policyLines.size > 14) policyLines.removeFirst()
        findViewById<TextView>(R.id.policyLog)?.text = policyLines.joinToString("\n")
    }

    private val dynamicPolicyListener = object : DynamicPolicyListener {
        override fun onServiceOperationPoints(mediaDeliverySessionId: String?, points: List<PolicyTemplateBinding>) {
            serviceOperationPoints = points
            fillPointSelector(points)
            logPolicy("points: " + points.joinToString { "[" + it.externalReference + (if (it.l4SEnablementPreference) " L4S" else "") +
                (if (it.qoSMonitoringEnablementPreference) " QoSmon" else "") + (if (!it.bdtWindows.isNullOrEmpty()) " BDT" else "") + "]" })
        }

        override fun onBdtInfo(reply: BdtInfoReply) =
            logPolicy("getBDTInfo [" + reply.serviceOperationPointReference + "]: " + if (reply.succeeded) "${reply.windowStart} to ${reply.windowEnd}" else "null (" + reply.reason + ")")

        override fun onPolicyActivation(reply: PolicyActivationReply) = logPolicy(
            "activatePolicy [" + reply.serviceOperationPointReference + "]: " + if (reply.succeeded)
                "dl ${reply.recommendedDownlinkBitRate} ul ${reply.recommendedUplinkBitRate} windows ${reply.grantedBackgroundDataTransferWindows.size} L4S ${reply.l4SEnabled} QoSmon ${reply.qoSMonitoringEnabled}"
            else "null (" + reply.reason + ")"
        )

        override fun onPolicyDeactivation(mediaDeliverySessionId: String?, succeeded: Boolean, reason: String?) {
            logPolicy("deactivatePolicy: $succeeded" + (reason?.let { " ($it)" } ?: ""))
            // the policy that the recommendation came with has ended: the player is no longer held to it
            if (succeeded) limitVideoBitrate(BitRateRecommendationUse.NO_CEILING)
        }

        override fun onDynamicPolicyStatus(mediaDeliverySessionId: String?, status: DynamicPolicyStatus?) =
            logPolicy("currentDynamicPolicies: " + (status?.let { "[" + it.serviceOperationPointReference + "] windows ${it.grantedBackgroundDataTransferWindows.size} L4S ${it.l4SEnabled} QoSmon ${it.qoSMonitoringEnabled} results ${it.qoSMonitoringResults}" } ?: "none"))
    }

    private val networkAssistanceListener = object : NetworkAssistanceListener {
        override fun onBitRateRecommendation(reply: BitRateRecommendationReply) {
            logPolicy("requestBitRateRecommendation: " + if (reply.succeeded) "dl ${reply.recommendedDownlinkBitRate} ul ${reply.recommendedUplinkBitRate}" else "null (" + reply.reason + ")")
            // the player is kept to the representations that fit the recommended bit rate (TS 26.501 clause 4.0.5)
            val ceiling = if (reply.succeeded) BitRateRecommendationUse.maxVideoBitrate(reply.recommendedDownlinkBitRate) else null
            if (ceiling != null) limitVideoBitrate(ceiling)
        }

        override fun onDeliveryBoost(reply: DeliveryBoostReply) =
            logPolicy("requestDeliveryBoost: ${reply.granted}" + (reply.reason?.let { " ($it)" } ?: ""))
    }

    private fun limitVideoBitrate(bitsPerSecond: Int) = runOnUiThread {
        val player = runCatching { exoPlayerAdapter?.getPlayerInstance() }.getOrNull() ?: return@runOnUiThread
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setMaxVideoBitrate(bitsPerSecond).build()
        logPolicy(if (bitsPerSecond == BitRateRecommendationUse.NO_CEILING) "player: video bit rate no longer limited" else "player: video bit rate limited to $bitsPerSecond bit/s")
    }

    private fun showPolicyPanel() {
        findViewById<View>(R.id.policyPanel).visibility = View.VISIBLE
        val adapter = mediaSessionHandlerAdapter ?: return
        val component = { intent.getStringExtra(EXTRA_COMPONENT_REFERENCE) ?: selectedServiceOperationPoint() ?: "" }
        fun withSession(action: (MediaSessionHandlerAdapter, String) -> Unit) = View.OnClickListener {
            val id = mediaDeliverySessionIdentifier
            if (id != null) action(adapter, id)
        }
        fun withPoint(action: (MediaSessionHandlerAdapter, String, String) -> Unit) = withSession { a, id ->
            val point = selectedServiceOperationPoint()
            if (point == null) logPolicy("no Service Operation Point: press Points first") else action(a, id, point)
        }
        findViewById<Button>(R.id.policyPointsButton).setOnClickListener(withSession { a, id -> a.getServiceOperationPoints(id) })
        findViewById<Button>(R.id.policyBdtButton).setOnClickListener(withPoint { a, id, point -> a.getBDTInfo(id, point) })
        findViewById<Button>(R.id.policyActivateButton).setOnClickListener(withPoint { a, id, point ->
            a.activatePolicy(
                id, point,
                if (intent.hasExtra(EXTRA_ESTIMATED_TRANSFER_VOLUME)) intent.getLongExtra(EXTRA_ESTIMATED_TRANSFER_VOLUME, 0L) else null,
                intent.getStringExtra(EXTRA_COMPONENT_REFERENCE),
                pointTarget(point)?.first ?: intent.getStringExtra(EXTRA_TARGET_DOWNLINK_BIT_RATE),
                pointTarget(point)?.second ?: intent.getStringExtra(EXTRA_TARGET_UPLINK_BIT_RATE)
            )
        })
        findViewById<Button>(R.id.policyDeactivateButton).setOnClickListener(withSession { a, id -> a.deactivatePolicy(id) })
        findViewById<Button>(R.id.policyStatusButton).setOnClickListener(withSession { a, id -> a.getDynamicPolicyStatus(id) })
        findViewById<Button>(R.id.policyRecommendationButton).setOnClickListener(withSession { a, id -> a.requestBitRateRecommendation(id, component()) })
        findViewById<Button>(R.id.policyBoostButton).setOnClickListener(withSession { a, id -> a.requestDeliveryBoost(id, component()) })
        adapter.getServiceOperationPoints(mediaDeliverySessionIdentifier ?: return)
    }

    // A second media delivery session of the same application, with a Media Stream Handler instance of its own (TS 26.510 V19.2.0
    // clause 11.2.2.1: every call of createMediaDeliverySession() makes a new session)
    private var secondAdapter: MediaSessionHandlerAdapter? = null
    private var secondSessionIdentifier: String? = null

    private fun startSecondSession(serviceId: String) {
        val second = MediaSessionHandlerAdapter()
        secondAdapter = second
        second.initialize(this) {
            second.generalSessionListener = object : GeneralSessionListener {
                override fun onMediaDeliverySessionCreated(mediaDeliverySessionIdentifier: String?, sessionHandlingState: String?) {
                    Log.i("5GMS-App2", "second session [$mediaDeliverySessionIdentifier] $sessionHandlingState")
                    secondSessionIdentifier = mediaDeliverySessionIdentifier
                }

                override fun onSessionStatus(mediaDeliverySessionIdentifier: String?, contentIdentifier: String?, sessionHandlingState: String?) {
                    Log.i("5GMS-App2", "status of the second session [$mediaDeliverySessionIdentifier]: $sessionHandlingState")
                }

                override fun onNotification(event: String, payload: Bundle) {
                    Log.i("5GMS-App2", "notification $event [" + payload.getString(SessionHandlerBundleKeys.MEDIA_DELIVERY_SESSION_IDENTIFIER) + "]")
                }
            }
            second.setM5Endpoint(m5BaseUrl)
            if (intent.getBooleanExtra(EXTRA_SECOND_PLAYS, false)) {
                // a second player in the same process, with a view of its own that is not on the screen
                second.getExoPlayerAdapter().initialize(androidx.media3.ui.PlayerView(this), this)
                val locator = deserializeServiceListEntry(serviceListEntryJson).entryPoints?.firstOrNull()?.locator
                second.createMediaDeliverySession(serviceId, "second", locator)
            } else {
                second.createMediaDeliverySession(serviceId)
            }
        }
    }

    private fun onConnectionToMediaSessionHandlerEstablished() {
        val adapter = mediaSessionHandlerAdapter ?: return
        intent.getStringExtra(EXTRA_SECOND_SERVICE_ID)?.let { startSecondSession(it) }
        adapter.generalSessionListener = generalSessionListener
        adapter.dynamicPolicyListener = dynamicPolicyListener
        adapter.networkAssistanceListener = networkAssistanceListener
        val playerAdapter = adapter.getExoPlayerAdapter()
        exoPlayerAdapter = playerAdapter
        playerAdapter.initialize(playerView, this)

        serviceUrl?.let {
            adapter.launchServiceUrl(it)
            return
        }
        val serviceListEntry = deserializeServiceListEntry(serviceListEntryJson)
        adapter.setM5Endpoint(m5BaseUrl)
        if (intent.getBooleanExtra(EXTRA_USE_CREATE_MEDIA_DELIVERY_SESSION, false)) {
            // the explicit method of TS 26.510 V19.2.0 clause 11.2.2.1
            adapter.createMediaDeliverySession(
                serviceListEntry.externalServiceId,
                serviceListEntry.name,
                serviceListEntry.entryPoints?.firstOrNull()?.locator,
                null,
                // the access token that the Media Session Handler presents to the Media AF at M5, if the Media AF asks for one
                intent.getStringExtra(EXTRA_ACCESS_TOKEN)
            )
        } else {
            adapter.initializePlaybackByServiceListEntry(serviceListEntry)
        }
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
            mediaDeliverySessionIdentifier?.let { mediaSessionHandlerAdapter?.destroyMediaDeliverySession(it) }
            mediaDeliverySessionIdentifier = null
            // destroying the first session leaves the second one active; the second is destroyed afterwards
            secondSessionIdentifier?.let { id ->
                secondAdapter?.getSessionStatus(id)
                secondAdapter?.destroyMediaDeliverySession(id)
            }
            secondSessionIdentifier = null
            mediaSessionHandlerAdapter?.reset()
            isPlaying = false
        }
    }

    private fun deserializeServiceListEntry(json: String): ServiceListEntry {
        val jsonObject = Json.parseToJsonElement(json).jsonObject
        val externalServiceId =
            jsonObject["externalServiceId"]?.jsonPrimitive?.content ?: ""
        val name = jsonObject["name"]?.jsonPrimitive?.content ?: ""

        val entryPoints = ArrayList<EntryPoint>()
        val entryPointsArray = jsonObject["entryPoints"]?.jsonArray
        if (entryPointsArray != null) {
            for (ep in entryPointsArray) {
                entryPoints.add(EntryPointJson.fromJson(ep.jsonObject))
            }
        }

        return ServiceListEntry(externalServiceId, name, entryPoints)
    }
}
