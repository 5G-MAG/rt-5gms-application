/*
License: 5G-MAG Public License (v1.0)
Author: Daniel Silhavy
Copyright: (C) 2023 Fraunhofer FOKUS
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.a5gmsdawareapplication

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmscommonlibrary.models.EntryPoint
import com.fivegmag.a5gmscommonlibrary.models.M8Model
import com.fivegmag.a5gmscommonlibrary.models.ServiceListEntry
import com.fivegmag.a5gmsdawareapplication.adapter.ContentGridAdapter
import com.fivegmag.a5gmsdawareapplication.model.ContentItem
import com.fivegmag.a5gmsdawareapplication.network.IConfigApi
import com.fivegmag.a5gmsdawareapplication.network.IM8InterfaceApi
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.serialization.json.*
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import java.io.InputStream
import java.net.URI
import java.util.*

const val TAG_AWARE_APPLICATION = "5GMS Aware Application"

/**
 * Landing page of the application. Displays available content items in a grid of poster cards.
 * Configuration and M8 input selection has been moved to SettingsActivity.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_SETTINGS = 1001
    }

    private lateinit var contentGrid: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var contentGridAdapter: ContentGridAdapter
    private lateinit var iM8InterfaceApi: IM8InterfaceApi
    private lateinit var iConfigApi: IConfigApi
    private lateinit var configProperties: Properties
    private lateinit var m8Data: M8Model

    private val metadataProvider = MetadataProvider()
    private var currentConfigUrl: String = ""
    private var currentSelectedM8Key: String = ""
    private var currentM8Path: String = ""

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            if (data != null) {
                val configUrl = data.getStringExtra(SettingsActivity.EXTRA_CONFIG_URL) ?: ""
                val m8Key = data.getStringExtra(SettingsActivity.EXTRA_SELECTED_M8_KEY) ?: ""
                val resolvedValue = data.getStringExtra("resolved_m8_value") ?: ""

                currentConfigUrl = configUrl
                currentSelectedM8Key = m8Key

                if (resolvedValue.isNotEmpty()) {
                    val selectedUri = URI(resolvedValue)
                    if (selectedUri.isAbsolute) {
                        setM8DataViaEndpoint(selectedUri.toString())
                    } else {
                        setM8DataViaJson(selectedUri.toString())
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupToolbar()
        setupContentGrid()
        requestUserPermissions()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.mainToolbar)
        toolbar.inflateMenu(R.menu.menu_main)
        toolbar.setOnMenuItemClickListener { item ->
            onMenuItemSelected(item)
        }
    }

    private fun setupContentGrid() {
        contentGrid = findViewById(R.id.contentGrid)
        emptyStateText = findViewById(R.id.emptyStateText)

        contentGridAdapter = ContentGridAdapter { item ->
            val intent = Intent(this, DetailActivity::class.java)
            intent.putExtra(DetailActivity.EXTRA_TITLE, item.title)
            intent.putExtra(DetailActivity.EXTRA_DESCRIPTION, item.description)
            intent.putExtra(DetailActivity.EXTRA_POSTER_URL, item.posterUrl)
            intent.putExtra(DetailActivity.EXTRA_MEDIA_TYPE, item.mediaType)
            intent.putExtra(DetailActivity.EXTRA_SERVICE_LIST_ENTRY_JSON, item.serviceListEntryJson)
            intent.putExtra(DetailActivity.EXTRA_M5_BASE_URL, m8Data.m5BaseUrl)
            startActivity(intent)
        }

        val spanCount = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2
        contentGrid.layoutManager = GridLayoutManager(this, spanCount)
        contentGrid.adapter = contentGridAdapter
    }

    private fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.actionSettings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                intent.putExtra(SettingsActivity.EXTRA_CONFIG_URL, currentConfigUrl)
                intent.putExtra(SettingsActivity.EXTRA_SELECTED_M8_KEY, currentSelectedM8Key)
                settingsLauncher.launch(intent)
                return true
            }

            R.id.actionLicense -> {
                val dialogView = LayoutInflater.from(this).inflate(R.layout.activity_license, null)
                val textView = dialogView.findViewById<TextView>(R.id.licenseTextView)
                val formattedText = getString(R.string.license_text)
                textView.text = Html.fromHtml(formattedText, Html.FROM_HTML_MODE_LEGACY)
                val builder = AlertDialog.Builder(this)
                    .setView(dialogView)
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                val dialog = builder.create()
                dialog.show()
                return true
            }

            R.id.actionAbout -> {
                val dialogView = LayoutInflater.from(this).inflate(R.layout.activity_about, null)
                addVersionNumber(dialogView)
                setClickListeners(dialogView)
                formatAboutText(dialogView)
                val builder = AlertDialog.Builder(this)
                    .setView(dialogView)
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                val dialog = builder.create()
                dialog.show()
                return true
            }

            R.id.actionAttribution -> {
                OssLicensesMenuActivity.setActivityTitle(getString(R.string.action_attribution_notice))
                val licensesIntent = Intent(this, OssLicensesMenuActivity::class.java)
                startActivity(licensesIntent)
                return true
            }
        }
        return false
    }

    private fun addVersionNumber(dialogView: View) {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionName = packageInfo.versionName
        val versionTextView = dialogView.findViewById<TextView>(R.id.versionNumberView)
        val versionText = getString(R.string.version_text_field, versionName)
        versionTextView.text = versionText
    }

    private fun setClickListeners(dialogView: View) {
        val githubTextView = dialogView.findViewById<TextView>(R.id.githubLink)
        githubTextView.setOnClickListener {
            val url = getString(R.string.github_url)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        val twitterTextView = dialogView.findViewById<TextView>(R.id.twitterLink)
        twitterTextView.setOnClickListener {
            val url = getString(R.string.twitter_url)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        val linkedInView = dialogView.findViewById<TextView>(R.id.linkedInLink)
        linkedInView.setOnClickListener {
            val url = getString(R.string.linked_in_url)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        val slackView = dialogView.findViewById<TextView>(R.id.slackLink)
        slackView.setOnClickListener {
            val url = getString(R.string.slack_url)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }

        val websiteView = dialogView.findViewById<TextView>(R.id.websiteLink)
        websiteView.setOnClickListener {
            val url = getString(R.string.website_url)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }
    }

    private fun formatAboutText(dialogView: View) {
        val textView = dialogView.findViewById<TextView>(R.id.descriptionText)
        val formattedText = getString(R.string.description_text)
        textView.text = Html.fromHtml(formattedText, Html.FROM_HTML_MODE_LEGACY)
    }

    private fun requestUserPermissions() {
        val permissionLst = arrayListOf<String>()

        val requestPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) {
                initialize()
            }

        // Register the cell info callback
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLst.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_NUMBERS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLst.add(Manifest.permission.READ_PHONE_NUMBERS)
        }

        if (permissionLst.size > 0) {
            requestPermissionLauncher.launch(permissionLst.toTypedArray())
        } else {
            initialize()
        }
    }

    /**
     * Initialization is performed after the user permissions have been requested.
     */
    private fun initialize() {
        try {
            initializeRetrofitForConfigInterfaceApi()
            currentConfigUrl = getString(R.string.m8_config_input)
            handleConfigurationChange()
            printDependenciesVersionNumbers()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleConfigurationChange() {
        if (currentConfigUrl == getString(R.string.m8_config_input)) {
            configProperties = Utils().loadConfiguration(this.assets, "config.properties.xml")
            loadFirstM8Entry()
        } else {
            setConfigViaEndpoint(currentConfigUrl)
        }
    }

    private fun loadFirstM8Entry() {
        val propertyNames = configProperties.propertyNames()
        if (propertyNames.hasMoreElements()) {
            val firstKey = propertyNames.nextElement() as String
            currentSelectedM8Key = firstKey
            val value = configProperties.getProperty(firstKey)
            val selectedUri = URI(value)
            if (selectedUri.isAbsolute) {
                setM8DataViaEndpoint(selectedUri.toString())
            } else {
                setM8DataViaJson(selectedUri.toString())
            }
        }
    }

    private fun setConfigViaEndpoint(configurationUrl: String) {
        try {
            val call: Call<ResponseBody>? =
                iConfigApi.fetchConfiguration(configurationUrl)
            call?.enqueue(object : Callback<ResponseBody?> {
                override fun onResponse(
                    call: Call<ResponseBody?>,
                    response: Response<ResponseBody?>
                ) {
                    val resource: String? = response.body()?.string()
                    if (resource != null) {
                        configProperties = Properties()
                        configProperties.loadFromXML(resource.byteInputStream())
                        loadFirstM8Entry()
                    }
                }

                override fun onFailure(call: Call<ResponseBody?>, t: Throwable) {
                    call.cancel()
                }
            })
        } catch (_: Exception) {
        }
    }

    private fun printDependenciesVersionNumbers() {
        Log.d(
            TAG_AWARE_APPLICATION,
            "5GMS Common Library Version: ${BuildConfig.LIB_VERSION_a5gmscommonlibrary}"
        )
        Log.d(
            TAG_AWARE_APPLICATION,
            "5GMS Media Stream Handler Version: ${BuildConfig.LIB_VERSION_a5gmsmediastreamhandler}"
        )
    }

    private fun initializeRetrofitForM8InterfaceApi(url: String) {
        val retrofitM8Interface: Retrofit = Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        iM8InterfaceApi =
            retrofitM8Interface.create(IM8InterfaceApi::class.java)
    }

    private fun initializeRetrofitForConfigInterfaceApi() {
        val retrofitInterface: Retrofit = Retrofit.Builder()
            .baseUrl("http://localhost/")
            .addConverterFactory(SimpleXmlConverterFactory.create())
            .build()

        iConfigApi =
            retrofitInterface.create(IConfigApi::class.java)
    }

    private fun onM8DataChanged() {
        loadMetadataAndDisplayGrid()
    }

    private fun loadMetadataAndDisplayGrid() {
        if (currentM8Path.isNotEmpty()) {
            val selectedUri = URI(currentM8Path)
            if (!selectedUri.isAbsolute) {
                // Local M8 config: try to load matching metadata from assets
                metadataProvider.loadFromAssets(assets, currentM8Path)
                displayContentGrid()
            } else {
                // Remote M8 config: try to load metadata from remote endpoint
                metadataProvider.loadFromEndpoint(currentM8Path, iConfigApi) {
                    runOnUiThread { displayContentGrid() }
                }
            }
        } else {
            displayContentGrid()
        }
    }

    private fun displayContentGrid() {
        val contentItems = ArrayList<ContentItem>()
        for (serviceListEntry in m8Data.serviceList) {
            val metadata = metadataProvider.getMetadataForEntry(serviceListEntry.name)
            contentItems.add(ContentItem(serviceListEntry, metadata))
        }

        if (contentItems.isEmpty()) {
            emptyStateText.visibility = View.VISIBLE
            contentGrid.visibility = View.GONE
        } else {
            emptyStateText.visibility = View.GONE
            contentGrid.visibility = View.VISIBLE
            contentGridAdapter.updateItems(contentItems)
        }
    }

    private fun setM8DataViaEndpoint(m8HostingEndpoint: String) {
        try {
            currentM8Path = m8HostingEndpoint
            initializeRetrofitForM8InterfaceApi(m8HostingEndpoint)
            val call: Call<ResponseBody>? =
                iM8InterfaceApi.fetchServiceAccessInformationList()
            call?.enqueue(object : Callback<ResponseBody?> {
                override fun onResponse(
                    call: Call<ResponseBody?>,
                    response: Response<ResponseBody?>
                ) {
                    val resource: String? = response.body()?.string()
                    if (resource != null) {
                        val jsonObject: JsonObject =
                            Json.parseToJsonElement(resource).jsonObject
                        val m5BaseUrl: String =
                            replaceDoubleTicks(jsonObject["m5BaseUrl"].toString())
                        val jsonServiceList = jsonObject["serviceList"]?.jsonArray
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
            currentM8Path = url
            val inputStream: InputStream = assets.open(url)
            json = inputStream.bufferedReader().use { it.readText() }
            val jsonObject: JsonObject = Json.parseToJsonElement(json).jsonObject
            val m5BaseUrl: String = replaceDoubleTicks(jsonObject["m5BaseUrl"].toString())
            val jsonServiceList = jsonObject["serviceList"]?.jsonArray
            m8Data = jsonServiceList?.let { createM8Model(m5BaseUrl, it) }!!
            onM8DataChanged()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun replaceDoubleTicks(value: String): String {
        return value.replace("\"", "")
    }

    private fun createM8Model(m5BaseUrl: String, jsonServiceList: JsonArray): M8Model {
        val serviceList = ArrayList<ServiceListEntry>()

        for (serviceListEntry in jsonServiceList) {
            val itemAsJsonObject = Json.parseToJsonElement(serviceListEntry.toString()).jsonObject
            val name: String =
                replaceDoubleTicks(itemAsJsonObject["name"].toString())
            val provisioningSessionId =
                replaceDoubleTicks(itemAsJsonObject["provisioningSessionId"].toString())

            val entryPoints = ArrayList<EntryPoint>()
            val entryPointList = itemAsJsonObject["entryPoints"]?.jsonArray

            if (entryPointList != null) {
                for (entryPoint in entryPointList) {
                    val entryPointAsJsonObject =
                        Json.parseToJsonElement(entryPoint.toString()).jsonObject
                    val locator = replaceDoubleTicks(entryPointAsJsonObject["locator"].toString())
                    val contentType =
                        replaceDoubleTicks(entryPointAsJsonObject["contentType"].toString())
                    val profiles = ArrayList<String>()
                    val profileList = entryPointAsJsonObject["profiles"]?.jsonArray
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
}
