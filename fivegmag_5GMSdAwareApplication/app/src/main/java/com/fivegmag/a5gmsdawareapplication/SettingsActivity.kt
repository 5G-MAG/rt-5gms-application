/*
License: 5G-MAG Public License (v1.0)
Author: Daniel Silhavy
Copyright: (C) 2024 Fraunhofer FOKUS
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.a5gmsdawareapplication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.fivegmag.a5gmsdawareapplication.model.AppConfig
import com.fivegmag.a5gmsdawareapplication.model.M8Source
import com.fivegmag.a5gmsdawareapplication.network.IConfigApi
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory

/**
 * Settings screen that allows the user to configure the app configuration source.
 *
 * The user can either:
 * - Leave the URL field empty to use the built-in local app_config.json
 * - Enter a remote URL pointing to a JSON file in app_config.json format
 *
 * After loading a configuration, the user selects a content source from the spinner.
 * The selected config URL and source are persisted in SharedPreferences so the app
 * remembers the settings across restarts.
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CONFIG_URL = "config_url"
        const val EXTRA_SELECTED_SOURCE_NAME = "selected_source_name"
        const val EXTRA_SELECTED_M8_URL = "selected_m8_url"
        const val EXTRA_SELECTED_METADATA_URL = "selected_metadata_url"

        const val PREFS_NAME = "5gmagflix_settings"
        const val PREF_CONFIG_URL = "config_url"
        const val PREF_SELECTED_SOURCE_NAME = "selected_source_name"
    }

    private lateinit var configUrlInput: TextInputEditText
    private lateinit var m8Spinner: Spinner
    private lateinit var loadButton: Button
    private lateinit var resetButton: Button
    private lateinit var loadingContainer: LinearLayout
    private lateinit var configStatusText: TextView
    private lateinit var iConfigApi: IConfigApi
    private lateinit var prefs: SharedPreferences

    private val configProvider = ConfigProvider()
    private var appConfig: AppConfig = AppConfig(emptyList())

    /** The source name that was active when this activity was opened */
    private var receivedSourceName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        receivedSourceName = intent.getStringExtra(EXTRA_SELECTED_SOURCE_NAME) ?: ""

        setupToolbar()
        setupLogo()
        initializeRetrofitForConfigInterfaceApi()
        initializeViews()
        loadInitialConfig()
        registerButtonListeners()
        setVersionNumber()
        setupBackNavigation()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.settingsToolbar)
        toolbar.setNavigationOnClickListener { returnResultAndFinish() }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                returnResultAndFinish()
            }
        })
    }

    private fun setupLogo() {
        val logoText = findViewById<TextView>(R.id.settingsLogoText)
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

    private fun initializeViews() {
        configUrlInput = findViewById(R.id.configUrlInput)
        m8Spinner = findViewById(R.id.m8Spinner)
        loadButton = findViewById(R.id.loadConfigButton)
        resetButton = findViewById(R.id.resetConfigButton)
        loadingContainer = findViewById(R.id.loadingContainer)
        configStatusText = findViewById(R.id.configStatusText)

        // Populate the input field: use the persisted config URL if available,
        // otherwise check the intent extra, otherwise leave empty (local config).
        val persistedUrl = prefs.getString(PREF_CONFIG_URL, null)
        val intentUrl = intent.getStringExtra(EXTRA_CONFIG_URL)

        val urlToShow = when {
            persistedUrl != null && persistedUrl.isNotEmpty() -> persistedUrl
            intentUrl != null && intentUrl != getString(R.string.m8_config_input) -> intentUrl
            else -> ""
        }
        configUrlInput.setText(urlToShow)
    }

    private fun loadInitialConfig() {
        val configUrl = configUrlInput.text.toString().trim()
        if (configUrl.isEmpty()) {
            appConfig = configProvider.loadFromAssets(assets)
            populateSourceSpinner()
            showStatus(getString(R.string.settings_using_local), isError = false)
        } else {
            showLoading(true)
            configProvider.loadFromEndpoint(configUrl, iConfigApi) { config ->
                runOnUiThread {
                    showLoading(false)
                    appConfig = config
                    if (config.sources.isNotEmpty()) {
                        populateSourceSpinner()
                        showStatus(
                            getString(R.string.settings_load_success, config.sources.size),
                            isError = false
                        )
                    } else {
                        populateSourceSpinner()
                        showStatus(getString(R.string.settings_load_empty), isError = true)
                    }
                }
            }
        }
    }

    private fun populateSourceSpinner() {
        try {
            val spinnerOptions = ArrayList<String>()
            for (source in appConfig.sources) {
                spinnerOptions.add(source.name)
            }

            val adapter = ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item, spinnerOptions
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            m8Spinner.adapter = adapter

            // Restore previous selection: first try the received source name,
            // then the persisted source name
            val sourceToSelect = if (receivedSourceName.isNotEmpty()) {
                receivedSourceName
            } else {
                prefs.getString(PREF_SELECTED_SOURCE_NAME, null) ?: ""
            }

            if (sourceToSelect.isNotEmpty()) {
                val position = spinnerOptions.indexOf(sourceToSelect)
                if (position >= 0) {
                    m8Spinner.setSelection(position)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun registerButtonListeners() {
        loadButton.setOnClickListener {
            val configUrl = configUrlInput.text.toString().trim()
            if (configUrl.isEmpty()) {
                appConfig = configProvider.loadFromAssets(assets)
                receivedSourceName = ""
                populateSourceSpinner()
                showStatus(getString(R.string.settings_using_local), isError = false)
            } else {
                showLoading(true)
                loadButton.isEnabled = false
                configProvider.loadFromEndpoint(configUrl, iConfigApi) { config ->
                    runOnUiThread {
                        showLoading(false)
                        loadButton.isEnabled = true
                        appConfig = config
                        receivedSourceName = ""
                        if (config.sources.isNotEmpty()) {
                            populateSourceSpinner()
                            showStatus(
                                getString(R.string.settings_load_success, config.sources.size),
                                isError = false
                            )
                            Toast.makeText(
                                this,
                                getString(R.string.settings_load_success, config.sources.size),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            populateSourceSpinner()
                            showStatus(getString(R.string.settings_load_empty), isError = true)
                        }
                    }
                }
            }
        }

        resetButton.setOnClickListener {
            configUrlInput.setText("")
            appConfig = configProvider.loadFromAssets(assets)
            receivedSourceName = ""
            populateSourceSpinner()

            // Clear persisted settings
            prefs.edit()
                .remove(PREF_CONFIG_URL)
                .remove(PREF_SELECTED_SOURCE_NAME)
                .apply()

            showStatus(getString(R.string.settings_reset_done), isError = false)
            Toast.makeText(this, getString(R.string.settings_reset_done), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLoading(show: Boolean) {
        loadingContainer.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            configStatusText.visibility = View.GONE
        }
    }

    private fun showStatus(message: String, isError: Boolean) {
        configStatusText.text = message
        configStatusText.setTextColor(
            getColor(if (isError) R.color.flix_red else R.color.fivegmag_blue)
        )
        configStatusText.visibility = View.VISIBLE
    }

    /**
     * Persists the current settings and returns the selected source to MainActivity.
     */
    private fun returnResultAndFinish() {
        val configUrl = configUrlInput.text.toString().trim()
        val resultIntent = Intent()

        // Persist the config URL
        prefs.edit().putString(PREF_CONFIG_URL, configUrl).apply()

        // For MainActivity: pass back the config URL marker
        // Empty string means local config
        if (configUrl.isEmpty()) {
            resultIntent.putExtra(EXTRA_CONFIG_URL, getString(R.string.m8_config_input))
        } else {
            resultIntent.putExtra(EXTRA_CONFIG_URL, configUrl)
        }

        val selectedIndex = m8Spinner.selectedItemPosition
        if (selectedIndex >= 0 && selectedIndex < appConfig.sources.size) {
            val selectedSource = appConfig.sources[selectedIndex]
            resultIntent.putExtra(EXTRA_SELECTED_SOURCE_NAME, selectedSource.name)
            resultIntent.putExtra(EXTRA_SELECTED_M8_URL, selectedSource.m8Url)
            if (selectedSource.metadataUrl != null) {
                resultIntent.putExtra(EXTRA_SELECTED_METADATA_URL, selectedSource.metadataUrl)
            }

            // Persist selected source name
            prefs.edit().putString(PREF_SELECTED_SOURCE_NAME, selectedSource.name).apply()
        }

        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun initializeRetrofitForConfigInterfaceApi() {
        val retrofitInterface: Retrofit = Retrofit.Builder()
            .baseUrl("http://localhost/")
            .addConverterFactory(SimpleXmlConverterFactory.create())
            .build()

        iConfigApi = retrofitInterface.create(IConfigApi::class.java)
    }

    private fun setVersionNumber() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            val versionTextView = findViewById<TextView>(R.id.settingsVersionNumber)
            val versionText = getString(R.string.version_text_field, versionName)
            versionTextView.text = versionText
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
    }
}
