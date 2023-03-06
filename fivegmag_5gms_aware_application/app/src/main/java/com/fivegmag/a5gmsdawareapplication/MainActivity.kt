package com.fivegmag.a5gmsdawareapplication

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonObject
import java.io.InputStream
import kotlin.collections.ArrayList

import com.google.android.exoplayer2.ui.StyledPlayerView

import com.fivegmag.a5gmsmediastreamhandler.ExoPlayerAdapter
import com.fivegmag.a5gmsmediastreamhandler.MediaSessionHandlerAdapter
import com.fivegmag.a5gmscommonlibrary.models.ServiceAccessInformation
import com.fivegmag.a5gmscommonlibrary.models.StreamingAccess
import com.fivegmag.a5gmsdawareapplication.R
import com.fivegmag.a5gmscommonlibrary.models.M8Model

const val M8_DATA = "m8/config.json"
const val TAG ="5GMS Aware Application"

class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {

    private val mediaSessionHandlerAdapter = MediaSessionHandlerAdapter()
    private val exoPlayerAdapter = ExoPlayerAdapter()
    private lateinit var m8Data: M8Model
    private lateinit var exoPlayerView: StyledPlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        exoPlayerView = findViewById(R.id.idExoPlayerVIew)

        try {
            setM8Data()
            mediaSessionHandlerAdapter.initialize(
                this,
                exoPlayerAdapter,
                ::onConnectionToMediaSessionHandlerEstablished
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStop() {
        super.onStop()
        // Unbind from the service
        mediaSessionHandlerAdapter.reset(this)
    }

    private fun onConnectionToMediaSessionHandlerEstablished() {
        mediaSessionHandlerAdapter.setM5Endpoint(m8Data.m5Url)
        mediaSessionHandlerAdapter.updateLookupTable(m8Data)
        exoPlayerAdapter.initialize(exoPlayerView, this, mediaSessionHandlerAdapter)
        populateSpinner()
    }

    private fun setM8Data() {
        val json: String?
        try {
            val inputStream: InputStream = assets.open(M8_DATA)
            json = inputStream.bufferedReader().use { it.readText() }
            val jsonObject: JsonObject = Json.parseToJsonElement(json).jsonObject
            val serviceAccessInformation = mutableListOf<ServiceAccessInformation>()
            var m5Url: String = jsonObject.get("m5Url").toString()
            m5Url = m5Url.replace("\"", "");
            val jsonSai = jsonObject.get("serviceAccessInformation")?.jsonArray

            if (jsonSai != null) {
                for (item in jsonSai) {
                    val streamingAccess =
                        Json.parseToJsonElement(item.toString()).jsonObject["streamingAccess"]
                    var mediaPlayerEntry : String = streamingAccess!!.jsonObject["mediaPlayerEntry"].toString()
                    var provisioningSessionId =
                        Json.parseToJsonElement(item.toString()).jsonObject["provisioningSessionId"].toString()
                    mediaPlayerEntry = mediaPlayerEntry.replace("\"", "");
                    provisioningSessionId = provisioningSessionId.replace("\"", "");
                    val entry = ServiceAccessInformation(
                        provisioningSessionId,
                        "",
                        StreamingAccess(mediaPlayerEntry)
                    )
                    serviceAccessInformation.add(entry)
                }
            }

            m8Data = M8Model(m5Url, serviceAccessInformation)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun populateSpinner() {
        try {
            val spinner: Spinner = findViewById(R.id.idSaiSpinner)
            val spinnerOptions: ArrayList<String> = ArrayList()

            val iterator = m8Data.serviceAccessInformation.iterator()
            while (iterator.hasNext()) {
                spinnerOptions.add(iterator.next().streamingAccess.mediaPlayerEntry)
            }
            val adapter: ArrayAdapter<String> = ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item, spinnerOptions
            )
            spinner.adapter = adapter
            spinner.onItemSelectedListener = this
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        var mediaPlayerEntry: String = parent?.getItemAtPosition(position) as String
        exoPlayerAdapter.stop()
        mediaSessionHandlerAdapter.initializePlaybackByMediaPlayerEntry(mediaPlayerEntry)
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        TODO("Not yet implemented")
    }
}