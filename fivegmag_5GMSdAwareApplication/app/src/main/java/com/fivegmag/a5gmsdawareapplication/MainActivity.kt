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
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fivegmag.a5gmscommonlibrary.models.EntryPoint
import com.fivegmag.a5gmscommonlibrary.models.M8Model
import com.fivegmag.a5gmscommonlibrary.models.ServiceListEntry
import com.fivegmag.a5gmsdawareapplication.adapter.CategoryRowAdapter
import com.fivegmag.a5gmsdawareapplication.model.AppConfig
import com.fivegmag.a5gmsdawareapplication.model.ContentCategory
import com.fivegmag.a5gmsdawareapplication.model.ContentItem
import com.fivegmag.a5gmsdawareapplication.model.M8Source
import com.fivegmag.a5gmsdawareapplication.network.IConfigApi
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.serialization.json.*
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import java.io.InputStream
import java.net.URI

const val TAG_AWARE_APPLICATION = "5GMS Aware Application"

/**
 * Landing page of the application. Displays available content items
 * in horizontal carousels grouped by media type.
 * Configuration and M8 input selection has been moved to SettingsActivity.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var contentGrid: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var categoryRowAdapter: CategoryRowAdapter
    private lateinit var iConfigApi: IConfigApi
    private lateinit var m8Data: M8Model

    private val configProvider = ConfigProvider()
    private val metadataProvider = MetadataProvider()
    private var appConfig: AppConfig = AppConfig(emptyList())
    private var currentConfigUrl: String = ""
    private var currentSource: M8Source? = null

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val configUrl = data.getStringExtra(SettingsActivity.EXTRA_CONFIG_URL) ?: ""
            val sourceName = data.getStringExtra(SettingsActivity.EXTRA_SELECTED_SOURCE_NAME) ?: ""
            val m8Url = data.getStringExtra(SettingsActivity.EXTRA_SELECTED_M8_URL) ?: ""
            val metadataUrl = data.getStringExtra(SettingsActivity.EXTRA_SELECTED_METADATA_URL)

            currentConfigUrl = configUrl

            if (m8Url.isNotEmpty()) {
                val source = M8Source(sourceName, m8Url, metadataUrl)
                currentSource = source
                loadM8Source(source)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupToolbar()
        setupLogo()
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

    private fun setupLogo() {
        val logoText = findViewById<TextView>(R.id.logoText)
        val part1 = getString(R.string.logo_text_5gmag)
        val part2 = getString(R.string.logo_text_flix)
        val full = "$part1$part2"
        val spannable = SpannableString(full)
        spannable.setSpan(
            ForegroundColorSpan(getColor(R.color.fivegmag_blue)),
            0, part1.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            ForegroundColorSpan(getColor(R.color.flix_red)),
            part1.length, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        logoText.text = spannable
    }

    private fun setupContentGrid() {
        contentGrid = findViewById(R.id.contentGrid)
        emptyStateText = findViewById(R.id.emptyStateText)

        categoryRowAdapter = CategoryRowAdapter { item ->
            val intent = Intent(this, DetailActivity::class.java)
            intent.putExtra(DetailActivity.EXTRA_TITLE, item.title)
            intent.putExtra(DetailActivity.EXTRA_DESCRIPTION, item.description)
            intent.putExtra(DetailActivity.EXTRA_POSTER_URL, item.posterUrl)
            intent.putExtra(DetailActivity.EXTRA_MEDIA_TYPE, item.mediaType)
            intent.putExtra(DetailActivity.EXTRA_SERVICE_LIST_ENTRY_JSON, item.serviceListEntryJson)
            intent.putExtra(DetailActivity.EXTRA_M5_BASE_URL, m8Data.m5BaseUrl)
            startActivity(intent)
        }

        contentGrid.layoutManager = LinearLayoutManager(this)
        contentGrid.adapter = categoryRowAdapter
    }

    private fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.actionSettings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                intent.putExtra(SettingsActivity.EXTRA_CONFIG_URL, currentConfigUrl)
                intent.putExtra(
                    SettingsActivity.EXTRA_SELECTED_SOURCE_NAME,
                    currentSource?.name ?: ""
                )
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
     * Reads persisted config URL and source name from SharedPreferences.
     */
    private fun initialize() {
        try {
            initializeRetrofitForConfigInterfaceApi()

            val prefs = getSharedPreferences(
                SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE
            )
            val persistedUrl = prefs.getString(SettingsActivity.PREF_CONFIG_URL, null)

            // Determine config URL: persisted remote URL, or local
            currentConfigUrl = if (!persistedUrl.isNullOrEmpty()) {
                persistedUrl
            } else {
                getString(R.string.m8_config_input)
            }

            val persistedSourceName = prefs.getString(
                SettingsActivity.PREF_SELECTED_SOURCE_NAME, null
            )
            loadAppConfig(persistedSourceName)
            printDependenciesVersionNumbers()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadAppConfig(preferredSourceName: String? = null) {
        if (currentConfigUrl == getString(R.string.m8_config_input)) {
            appConfig = configProvider.loadFromAssets(assets)
            loadSourceByName(preferredSourceName)
        } else {
            configProvider.loadFromEndpoint(currentConfigUrl, iConfigApi) { config ->
                appConfig = config
                runOnUiThread { loadSourceByName(preferredSourceName) }
            }
        }
    }

    /**
     * Selects and loads a source by name. Falls back to the first source
     * if the named source is not found in the current config.
     */
    private fun loadSourceByName(sourceName: String?) {
        if (appConfig.sources.isEmpty()) return

        val source = if (!sourceName.isNullOrEmpty()) {
            appConfig.sources.find { it.name == sourceName } ?: appConfig.sources[0]
        } else {
            appConfig.sources[0]
        }
        currentSource = source
        loadM8Source(source)
    }

    /**
     * Loads M8 data and metadata for the given source.
     */
    private fun loadM8Source(source: M8Source) {
        val m8Url = source.m8Url
        val selectedUri = URI(m8Url)
        if (selectedUri.isAbsolute) {
            setM8DataViaEndpoint(m8Url, source)
        } else {
            setM8DataViaJson(m8Url, source)
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

    private fun initializeRetrofitForConfigInterfaceApi() {
        val retrofitInterface: Retrofit = Retrofit.Builder()
            .baseUrl("http://localhost/")
            .addConverterFactory(SimpleXmlConverterFactory.create())
            .build()

        iConfigApi =
            retrofitInterface.create(IConfigApi::class.java)
    }

    private fun onM8DataChanged(source: M8Source) {
        loadMetadataAndDisplayGrid(source)
    }

    private fun loadMetadataAndDisplayGrid(source: M8Source) {
        metadataProvider.loadMetadata(
            m8Url = source.m8Url,
            explicitMetadataUrl = source.metadataUrl,
            assets = assets,
            iConfigApi = iConfigApi
        ) {
            runOnUiThread { displayContentGrid() }
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
            val categories = groupItemsByMediaType(contentItems)
            categoryRowAdapter.updateCategories(categories)
        }
    }

    private fun groupItemsByMediaType(items: List<ContentItem>): List<ContentCategory> {
        val grouped = LinkedHashMap<String, ArrayList<ContentItem>>()
        for (item in items) {
            val type = item.mediaType
            if (!grouped.containsKey(type)) {
                grouped[type] = ArrayList()
            }
            grouped[type]?.add(item)
        }

        // Define display order: movies first, then tv_show, then any others
        val orderedTypes = ArrayList<String>()
        if (grouped.containsKey("movie")) orderedTypes.add("movie")
        if (grouped.containsKey("tv_show")) orderedTypes.add("tv_show")
        for (key in grouped.keys) {
            if (!orderedTypes.contains(key)) orderedTypes.add(key)
        }

        val categories = ArrayList<ContentCategory>()
        for (type in orderedTypes) {
            val label = when (type) {
                "movie" -> getString(R.string.category_movies)
                "tv_show" -> getString(R.string.category_tv_shows)
                else -> type.replaceFirstChar { it.uppercase() }
            }
            val categoryItems = grouped[type]
            if (categoryItems != null && categoryItems.isNotEmpty()) {
                categories.add(ContentCategory(label, type, categoryItems))
            }
        }

        return categories
    }

    private fun setM8DataViaEndpoint(m8HostingEndpoint: String, source: M8Source) {
        try {
            val call: Call<ResponseBody>? =
                iConfigApi.fetchConfiguration(m8HostingEndpoint)
            call?.enqueue(object : Callback<ResponseBody?> {
                override fun onResponse(
                    call: Call<ResponseBody?>,
                    response: Response<ResponseBody?>
                ) {
                    val resource: String? = response.body()?.string()
                    if (resource != null) {
                        try {
                            val jsonObject: JsonObject =
                                Json.parseToJsonElement(resource).jsonObject
                            val m5BaseUrl: String =
                                replaceDoubleTicks(jsonObject["m5BaseUrl"].toString())
                            val jsonServiceList = jsonObject["serviceList"]?.jsonArray
                            m8Data = jsonServiceList?.let { createM8Model(m5BaseUrl, it) }!!
                            onM8DataChanged(source)
                        } catch (e: Exception) {
                            Log.e(TAG_AWARE_APPLICATION, "Failed to parse M8 data: ${e.message}")
                            runOnUiThread { showEmptyState() }
                        }
                    } else {
                        Log.e(TAG_AWARE_APPLICATION, "M8 response body was null")
                        runOnUiThread { showEmptyState() }
                    }
                }

                override fun onFailure(call: Call<ResponseBody?>, t: Throwable) {
                    Log.e(TAG_AWARE_APPLICATION, "Failed to fetch M8 data: ${t.message}")
                    call.cancel()
                    runOnUiThread { showEmptyState() }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG_AWARE_APPLICATION, "Error loading M8 endpoint: ${e.message}")
            showEmptyState()
        }
    }

    private fun showEmptyState() {
        emptyStateText.visibility = View.VISIBLE
        contentGrid.visibility = View.GONE
    }

    private fun setM8DataViaJson(url: String, source: M8Source) {
        val json: String?
        try {
            val inputStream: InputStream = assets.open(url)
            json = inputStream.bufferedReader().use { it.readText() }
            val jsonObject: JsonObject = Json.parseToJsonElement(json).jsonObject
            val m5BaseUrl: String = replaceDoubleTicks(jsonObject["m5BaseUrl"].toString())
            val jsonServiceList = jsonObject["serviceList"]?.jsonArray
            m8Data = jsonServiceList?.let { createM8Model(m5BaseUrl, it) }!!
            onM8DataChanged(source)
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
