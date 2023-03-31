/*
License: 5G-MAG Public License (v1.0)
Author: Daniel Silhavy
Copyright: (C) 2023 Fraunhofer FOKUS
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.a5gmsdawareapplication

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.fivegmag.a5gmscommonlibrary.models.*
import com.fivegmag.a5gmsdawareapplication.network.M8InterfaceApi
import com.fivegmag.a5gmsmediastreamhandler.ExoPlayerAdapter
import com.fivegmag.a5gmsmediastreamhandler.MediaSessionHandlerAdapter
import com.google.android.exoplayer2.ui.StyledPlayerView
import kotlinx.serialization.json.*
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.InputStream
import java.util.*
import kotlin.collections.ArrayList

const val TAG = "5GMS Aware Application"

class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {

    private val mediaSessionHandlerAdapter = MediaSessionHandlerAdapter()
    private val exoPlayerAdapter = ExoPlayerAdapter()
    private var  currentSelectedDropdownIndex: Int = 0
    private lateinit var m8InterfaceApi: M8InterfaceApi
    private lateinit var m8Data: M8Model
    private lateinit var exoPlayerView: StyledPlayerView
    private lateinit var configProperties: Properties
    private lateinit var m8HostingEndpoint: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        loadConfiguration()
        exoPlayerView = findViewById(R.id.idExoPlayerVIew)

        try {
            registerButtonListener()
            initializeRetrofitForM8InterfaceApi(m8HostingEndpoint)
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

    private fun loadConfiguration() {
        try {
            val inputStream: InputStream = this.assets.open("config.properties")
            configProperties = Properties()
            configProperties.load(inputStream)
            m8HostingEndpoint =
                configProperties.getProperty("m8HostingEndpoint", "https://rt.5g-mag.com/")

            inputStream.close()
        } catch (e: Exception) {

        }
    }

    private fun initializeRetrofitForM8InterfaceApi(url: String) {
        val retrofitM8Interface: Retrofit = Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        m8InterfaceApi =
            retrofitM8Interface.create(M8InterfaceApi::class.java)
    }

    private fun onConnectionToMediaSessionHandlerEstablished() {
        exoPlayerAdapter.initialize(exoPlayerView, this, mediaSessionHandlerAdapter)
    }

    private fun registerButtonListener() {
        findViewById<Button>(R.id.loadButton)
            .setOnClickListener {
                loadStream()
            }
        findViewById<Button>(R.id.localInterfaceSingle)
            .setOnClickListener {
                val url = configProperties.getProperty(
                    "m8StaticSingleJsonUrl",
                    "m8/config_single_media.json"
                )
                setM8DataViaJson(url)
            }
        findViewById<Button>(R.id.localInterfaceMulti)
            .setOnClickListener {
                val url = configProperties.getProperty(
                    "m8StaticMultiJsonUrl",
                    "m8/config_multi_media.json"
                )
                setM8DataViaJson(url)
            }
        findViewById<Button>(R.id.hostedInterface)
            .setOnClickListener {
                setM8DataViaEndpoint()
            }
    }

    private fun onM8DataChanged() {
        mediaSessionHandlerAdapter.setM5Endpoint(m8Data.m5BaseUrl)
        populateSpinner()
    }

    private fun loadStream() {
        exoPlayerAdapter.stop()
        val serviceListEntry : ServiceListEntry = m8Data.serviceList[currentSelectedDropdownIndex]
        mediaSessionHandlerAdapter.initializePlaybackByServiceListEntry(serviceListEntry)
    }

    private fun setM8DataViaEndpoint() {
        try {
            val call: Call<ResponseBody>? =
                m8InterfaceApi.fetchServiceAccessInformationList()
            call?.enqueue(object : retrofit2.Callback<ResponseBody?> {
                override fun onResponse(
                    call: Call<ResponseBody?>,
                    response: Response<ResponseBody?>
                ) {
                    val resource: String? = response.body()?.string()
                    if (resource != null) {
                        val jsonObject: JsonObject =
                            Json.parseToJsonElement(resource).jsonObject
                        val m5BaseUrl: String = replaceDoubleTicks(jsonObject.get("m5BaseUrl").toString())
                        val jsonServiceList = jsonObject.get("serviceList")?.jsonArray
                        m8Data = jsonServiceList?.let { createM8Model(m5BaseUrl, it) }!!
                        onM8DataChanged()
                    }
                }

                override fun onFailure(call: Call<ResponseBody?>, t: Throwable) {
                    call.cancel()
                }
            })
        } catch (_: Exception) {

        }
    }

    private fun setM8DataViaJson(url: String) {
        val json: String?
        try {
            val inputStream: InputStream = assets.open(url)
            json = inputStream.bufferedReader().use { it.readText() }
            val jsonObject: JsonObject = Json.parseToJsonElement(json).jsonObject
            val m5BaseUrl: String = replaceDoubleTicks(jsonObject.get("m5BaseUrl").toString())
            val jsonServiceList = jsonObject.get("serviceList")?.jsonArray
            m8Data = jsonServiceList?.let { createM8Model(m5BaseUrl, it) }!!
            onM8DataChanged()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createM8Model(m5BaseUrl: String, jsonServiceList: JsonArray): M8Model {
        val serviceList = ArrayList<ServiceListEntry>()

        for (serviceListEntry in jsonServiceList) {
            val itemAsJsonObject = Json.parseToJsonElement(serviceListEntry.toString()).jsonObject
            var name: String =
                replaceDoubleTicks(itemAsJsonObject["name"].toString())
            var provisioningSessionId =
                replaceDoubleTicks(itemAsJsonObject["provisioningSessionId"].toString())

            val entryPoints = ArrayList<EntryPoint>()
            val entryPointList = itemAsJsonObject.get("entryPoints")?.jsonArray

            if (entryPointList != null) {
                for (entryPoint in entryPointList) {
                    val entryPointAsJsonObject =
                        Json.parseToJsonElement(entryPoint.toString()).jsonObject
                    val locator = replaceDoubleTicks(entryPointAsJsonObject["locator"].toString())
                    val contentType =
                        replaceDoubleTicks(entryPointAsJsonObject["contentType"].toString())
                    val profiles = ArrayList<String>()
                    val profileList = entryPointAsJsonObject.get("profiles")?.jsonArray
                    if (profileList != null) {
                        for (profileEntry in profileList) {
                            profiles.add(profileEntry.toString())
                        }
                    }
                    entryPoints.add(
                        EntryPoint(
                            locator,
                            contentType,
                            profiles
                        )
                    )
                }
            }
            val entry = ServiceListEntry(
                provisioningSessionId,
                name,
                entryPoints
            )
            serviceList.add(entry)
        }

        return M8Model(m5BaseUrl, serviceList)
    }

    private fun replaceDoubleTicks(value: String): String {
        return value.replace("\"", "");
    }

    private fun populateSpinner() {
        try {
            val spinner: Spinner = findViewById(R.id.idSaiSpinner)
            val spinnerOptions: ArrayList<String> = ArrayList()

            val iterator = m8Data.serviceList.iterator()
            while (iterator.hasNext()) {
                spinnerOptions.add(iterator.next().name)
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
        currentSelectedDropdownIndex = position
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        TODO("Not yet implemented")
    }
}