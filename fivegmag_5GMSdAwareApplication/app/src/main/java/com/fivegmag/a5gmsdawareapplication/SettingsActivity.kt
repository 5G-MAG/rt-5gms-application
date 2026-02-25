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
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.fivegmag.a5gmscommonlibrary.helpers.Utils
import com.fivegmag.a5gmsdawareapplication.network.IConfigApi
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import java.util.*

/**
 * Settings screen that allows the user to configure the M8 data source.
 * Replaces the config URL input and M8 spinner that were previously on the main screen.
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CONFIG_URL = "config_url"
        const val EXTRA_SELECTED_M8_KEY = "selected_m8_key"
        const val EXTRA_CONFIG_CHANGED = "config_changed"
    }

    private lateinit var configUrlInput: TextInputEditText
    private lateinit var m8Spinner: Spinner
    private lateinit var iConfigApi: IConfigApi
    private lateinit var configProperties: Properties

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setupToolbar()
        initializeRetrofitForConfigInterfaceApi()
        initializeViews()
        loadInitialConfig()
        registerButtonListeners()
        setVersionNumber()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.settingsToolbar)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun initializeViews() {
        configUrlInput = findViewById(R.id.configUrlInput)
        m8Spinner = findViewById(R.id.m8Spinner)

        val configUrl = intent.getStringExtra(EXTRA_CONFIG_URL)
        if (configUrl != null) {
            configUrlInput.setText(configUrl)
        }
    }

    private fun loadInitialConfig() {
        val configUrl = configUrlInput.text.toString()
        if (configUrl == getString(R.string.m8_config_input)) {
            configProperties = Utils().loadConfiguration(this.assets, "config.properties.xml")
            populateM8Spinner()
        } else {
            loadConfigFromEndpoint(configUrl)
        }
    }

    private fun loadConfigFromEndpoint(configurationUrl: String) {
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
                        populateM8Spinner()
                    }
                }

                override fun onFailure(call: Call<ResponseBody?>, t: Throwable) {
                    call.cancel()
                }
            })
        } catch (_: Exception) {
        }
    }

    private fun populateM8Spinner() {
        try {
            val spinnerOptions: ArrayList<String> = ArrayList()
            val propertyNames = configProperties.propertyNames()

            while (propertyNames.hasMoreElements()) {
                spinnerOptions.add(propertyNames.nextElement() as String)
            }

            val adapter: ArrayAdapter<String> = ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item, spinnerOptions
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            m8Spinner.adapter = adapter

            // Restore previous selection if available
            val previousKey = intent.getStringExtra(EXTRA_SELECTED_M8_KEY)
            if (previousKey != null) {
                val position = spinnerOptions.indexOf(previousKey)
                if (position >= 0) {
                    m8Spinner.setSelection(position)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun registerButtonListeners() {
        findViewById<Button>(R.id.loadConfigButton).setOnClickListener {
            applySettings()
        }

        findViewById<Button>(R.id.resetConfigButton).setOnClickListener {
            configUrlInput.setText(getString(R.string.m8_config_input))
            configProperties = Utils().loadConfiguration(this.assets, "config.properties.xml")
            populateM8Spinner()
        }
    }

    private fun applySettings() {
        val configUrl = configUrlInput.text.toString()

        if (configUrl == getString(R.string.m8_config_input)) {
            configProperties = Utils().loadConfiguration(this.assets, "config.properties.xml")
            populateM8Spinner()
        } else {
            loadConfigFromEndpoint(configUrl)
        }

        returnResult()
    }

    private fun returnResult() {
        val resultIntent = Intent()
        resultIntent.putExtra(EXTRA_CONFIG_URL, configUrlInput.text.toString())
        val selectedM8Key = m8Spinner.selectedItem as? String
        if (selectedM8Key != null) {
            resultIntent.putExtra(EXTRA_SELECTED_M8_KEY, selectedM8Key)
            // Pass the resolved URL/path for the selected M8 key
            val resolvedValue = configProperties.getProperty(selectedM8Key)
            resultIntent.putExtra("resolved_m8_value", resolvedValue)
        }
        resultIntent.putExtra(EXTRA_CONFIG_CHANGED, true)
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
            val versionTextView = findViewById<android.widget.TextView>(R.id.settingsVersionNumber)
            val versionText = getString(R.string.version_text_field, versionName)
            versionTextView.text = versionText
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
    }
}
