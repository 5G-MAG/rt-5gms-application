/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.fivegmag.dvbiclient.discovery.Offering
import com.fivegmag.dvbiclient.http.TlsCheck

/**
 * Settings: the service list URL, or a list picked from a Service List Registry (ETSI TS 103 770
 * V1.2.1 clause 5.1.3.2), the region, subscription packages and the parental criterion.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: Settings
    private lateinit var listUrl: EditText
    private lateinit var fallbacks: TextView
    private var pickedUrls: List<String>? = null
    private var pickedId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = getString(R.string.settings)
        settings = Settings(this)
        listUrl = findViewById(R.id.serviceListUrl)
        fallbacks = findViewById(R.id.fallbackUrls)
        val registry = findViewById<EditText>(R.id.registryUrl)
        val country = findViewById<EditText>(R.id.country)
        val region = findViewById<EditText>(R.id.region)
        val packages = findViewById<EditText>(R.id.packages)
        val parental = findViewById<EditText>(R.id.parental)

        val urls = settings.serviceListUrls
        listUrl.setText(urls.firstOrNull() ?: "")
        showFallbacks(urls)
        registry.setText(settings.registryEndpoint)
        country.setText(settings.country)
        region.setText(settings.region)
        packages.setText(settings.packages.joinToString(", "))
        parental.setText(settings.parentalThreshold.takeIf { it > 0 }?.toString() ?: "")

        DvbiSession.serviceList?.let { list ->
            findViewById<TextView>(R.id.regions).text = if (list.regions.isEmpty()) "The service list has no RegionList."
                else "Regions of the list: " + list.regions.joinToString(", ") { "${it.regionId} (${it.name})" }
            findViewById<TextView>(R.id.listPackages).text = list.subscriptionPackages?.let { "Packages of the list: " + it.packages.joinToString(", ") }
                ?: "The service list has no SubscriptionPackageList."
        }

        findViewById<Button>(R.id.queryRegistry).setOnClickListener {
            val status = findViewById<TextView>(R.id.registryStatus)
            status.text = getString(R.string.loading)
            DvbiRepository.queryRegistry(registry.text.toString().trim(), country.text.toString().trim().uppercase(), settings.language) { r ->
                val warn = TlsCheck.plainHttpWarning(r.url, NetworkInfo.localAddresses(this))
                status.text = listOfNotNull("Query: ${r.url}", r.error?.let { "Failed: $it" }, warn).joinToString("\n\n")
                if (r.error == null) pick(r.offerings)
            }
        }

        findViewById<Button>(R.id.save).setOnClickListener {
            val typed = listUrl.text.toString().trim()
            val picked = pickedUrls
            if (picked != null && picked.firstOrNull() == typed) {
                settings.serviceListUrls = picked
                settings.expectedListId = pickedId ?: ""
            } else if (typed != settings.serviceListUrls.firstOrNull()) {
                // A URL typed by hand: no fallbacks and no ServiceListId to check against.
                settings.serviceListUrls = listOf(typed)
                settings.expectedListId = ""
            }
            settings.registryEndpoint = registry.text.toString()
            settings.country = country.text.toString()
            settings.region = region.text.toString()
            settings.packages = packages.text.toString().split(',')
            settings.parentalThreshold = parental.text.toString().trim().toIntOrNull() ?: 0
            finish()
        }
    }

    private fun showFallbacks(urls: List<String>) {
        fallbacks.text = if (urls.size > 1) "Then, if it fails: " + urls.drop(1).joinToString(", ") else ""
    }

    // Table 83 NOTE 2: the offerings with the default first and marked; one this client should not
    // install says why, and is still offered.
    private fun pick(offerings: List<Offering>) {
        if (offerings.isEmpty()) {
            AlertDialog.Builder(this).setMessage("The registry returned no service list offering.").setPositiveButton(android.R.string.ok, null).show()
            return
        }
        val labels = offerings.map { o ->
            buildString {
                append(o.name)
                if (o.isDefault) append("  [default]")
                if (o.regulatorListFlag) append("  [regulator list]")
                if (o.providerName.isNotEmpty()) append("\n").append(o.providerName)
                o.problem?.let { append("\nNot recommended: ").append(it) }
            }
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.query_registry)
            .setSingleChoiceItems(labels, offerings.indexOfFirst { it.isDefault }) { dialog, which ->
                val o = offerings[which]
                pickedUrls = o.urls
                pickedId = o.serviceListId
                listUrl.setText(o.urls.first())
                showFallbacks(o.urls)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
