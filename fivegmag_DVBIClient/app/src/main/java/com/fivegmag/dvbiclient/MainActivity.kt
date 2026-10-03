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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fivegmag.dvbiclient.guide.GuideEvent
import com.fivegmag.dvbiclient.guide.GuideParser
import com.fivegmag.dvbiclient.http.TlsCheck
import com.fivegmag.dvbiclient.servicelist.ServiceList
import com.fivegmag.dvbiclient.servicelist.ServiceListRules
import com.fivegmag.dvbiclient.ui.ChannelAdapter
import com.fivegmag.dvbiclient.ui.ChannelRow
import java.text.DateFormat
import java.util.Date

/**
 * The channel list of the installed service list: services numbered by the LCN table of the
 * user's region (ETSI TS 103 770 V1.2.1 clause 5.5.12), with logo, now/next (clause 6.5.3) and the
 * 5G Broadcast badge. Services with LCN@visible false are left out of the list and reached by
 * entering their channel number (table 23).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var settings: Settings
    private lateinit var adapter: ChannelAdapter
    private lateinit var statusText: TextView
    private lateinit var tlsWarning: TextView
    private var numbering: Map<String, ServiceListRules.Numbering> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = Settings(this)
        statusText = findViewById(R.id.statusText)
        tlsWarning = findViewById(R.id.tlsWarning)
        adapter = ChannelAdapter(onSelect = { select(it) }, onSchedule = { startActivity(ScheduleActivity.intent(this, it.service.uid)) })
        findViewById<RecyclerView>(R.id.channelList).also {
            it.layoutManager = LinearLayoutManager(this)
            it.adapter = adapter
        }
        val number = findViewById<EditText>(R.id.channelNumber)
        val go = { goToChannel(number.text.toString()) }
        findViewById<Button>(R.id.channelGo).setOnClickListener { go() }
        number.setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_GO) { go(); true } else false }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_reload -> { load(); true }
        R.id.action_settings -> { startActivity(android.content.Intent(this, SettingsActivity::class.java)); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun load() {
        val urls = settings.serviceListUrls
        if (urls.isEmpty()) {
            statusText.text = getString(R.string.no_service_list)
            return
        }
        if (DvbiSession.serviceList == null) statusText.text = getString(R.string.loading)
        DvbiRepository.loadServiceList(urls, settings.expectedListId, settings.language) { r ->
            val list = r.list
            if (list == null) {
                showTls(urls.first(), null)
                statusText.text = "Could not load the service list:\n" + r.problems.joinToString("\n")
                return@loadServiceList
            }
            showTls(r.url ?: urls.first(), list)
            statusText.text = buildString {
                append(list.name.ifEmpty { "(unnamed service list)" })
                list.version?.let { append(" \u00b7 version $it") }
                for (p in r.problems) append("\n").append(p)
            }
            show(list)
        }
    }

    // Clause 7.3 warnings for the service list URL and each content guide endpoint on plain HTTP.
    private fun showTls(listUrl: String, list: ServiceList?) {
        val local = NetworkInfo.localAddresses(this)
        val urls = listOf(listUrl) + (list?.services?.mapNotNull { it.guide?.schedule } ?: emptyList())
        val warnings = urls.distinctBy { origin(it) }.mapNotNull { TlsCheck.plainHttpWarning(it, local) }
        tlsWarning.text = warnings.joinToString("\n\n")
        tlsWarning.visibility = if (warnings.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun origin(url: String): String = try {
        java.net.URI(url).let { "${it.scheme}://${it.authority}" }
    } catch (_: Exception) {
        url
    }

    private fun show(list: ServiceList) {
        val table = ServiceListRules.selectLcnTable(list.lcnTables, settings.region, settings.packages)
        numbering = ServiceListRules.assignChannelNumbers(table, list.services)
        val rows = list.services
            .filter { ServiceListRules.inRegion(it.targetRegions, settings.region) }
            // Table 23, @visible: hidden services are not listed.
            .filter { numbering[it.uid]?.visible != false }
            .sortedWith(compareBy({ numbering[it.uid]?.lcn ?: Int.MAX_VALUE }, { it.docOrder }))
            .map { ChannelRow(it, numbering[it.uid]?.lcn, ServiceListRules.minimumAgeFor(it.ratings, settings.country.ifEmpty { null })) }
        adapter.submit(rows)
        for (row in rows) loadNowNext(row)
    }

    private fun loadNowNext(row: ChannelRow) {
        if (row.service.guide == null) return
        DvbiRepository.background({ DvbiRepository.guide.nowNext(row.service) }) { r ->
            if (r.reacquireServiceList) load()
            val events = r.value ?: return@background
            adapter.updateNowNext(row.service.uid, nowNextText(events))
        }
    }

    private fun nowNextText(events: List<GuideEvent>): String {
        val (now, next) = GuideParser.nowNext(events, System.currentTimeMillis())
        val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
        return listOfNotNull(
            now?.let { "Now: ${it.title}" },
            next?.let { "Next ${fmt.format(Date(it.start))}: ${it.title}" },
        ).joinToString("  \u00b7  ")
    }

    // Table 23: a service with @visible false is reachable by its number unless @selectable is false.
    private fun goToChannel(text: String) {
        val n = text.trim().toIntOrNull() ?: return
        val list = DvbiSession.serviceList ?: return
        val uid = numbering.entries.firstOrNull { it.value.lcn == n }?.key
        val service = list.services.firstOrNull { it.uid == uid }
        if (service == null || !ServiceListRules.directlySelectable(numbering[uid])) {
            Toast.makeText(this, "No selectable service on channel $n", Toast.LENGTH_SHORT).show()
            return
        }
        select(ChannelRow(service, n, ServiceListRules.minimumAgeFor(service.ratings, settings.country.ifEmpty { null })))
    }

    // The parental check is made by the player, where the programme's rating is known (clause 5.5.28).
    private fun select(row: ChannelRow) {
        startActivity(PlayerActivity.intent(this, row.service.uid, settings.packages))
    }
}
