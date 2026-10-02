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
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.fivegmag.dvbiclient.guide.GuideEvent
import com.fivegmag.dvbiclient.guide.ProgrammeInfo
import com.fivegmag.dvbiclient.http.ServiceListFetch
import com.fivegmag.dvbiclient.servicelist.ServiceListRules
import java.text.DateFormat
import java.util.Date

/**
 * The schedule of one service (ETSI TS 103 770 V1.2.1 clause 6.5.2), from an hour ago to twelve
 * hours ahead, and the programme information of a selected event (clause 6.6).
 */
class ScheduleActivity : AppCompatActivity() {

    private var events: List<GuideEvent> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule)
        val status = findViewById<TextView>(R.id.scheduleStatus)
        val listView = findViewById<ListView>(R.id.scheduleList)
        val service = DvbiSession.serviceList?.services?.firstOrNull { it.uid == intent.getStringExtra(EXTRA_SERVICE_UID) }
        if (service == null) {
            status.text = getString(R.string.service_not_found)
            return
        }
        title = "${service.name}: ${getString(R.string.schedule)}"
        status.text = getString(R.string.loading)
        val now = System.currentTimeMillis()
        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        DvbiRepository.background({ DvbiRepository.guide.schedule(service, now - HOUR_MS, now + 12 * HOUR_MS) }) { r ->
            val evs = r.value
            if (evs == null) {
                status.text = "No schedule: " + (r.error ?: r.http?.let { ServiceListFetch.describe(it) } ?: "no content guide source")
                return@background
            }
            events = evs
            status.text = if (evs.isEmpty()) "No events in the schedule." else "${evs.size} events"
            listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
                evs.map { e -> "${fmt.format(Date(e.start))}  ${e.title}" + (if (e.start <= now && e.end > now) "  (now)" else "") })
        }
        listView.setOnItemClickListener { _, _, position, _ -> showProgramme(service, events[position]) }
    }

    // Clause 6.6.2, <ProgramInfoEndpoint>?pid=<program_id>; what the schedule carried when the
    // service has no ProgramInfoEndpoint or the request fails.
    private fun showProgramme(service: com.fivegmag.dvbiclient.servicelist.Service, e: GuideEvent) {
        DvbiRepository.background({ DvbiRepository.guide.programme(service, e.crid) }) { r ->
            val info: ProgrammeInfo? = r.value ?: e.info
            val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
            val country = Settings(this).country.ifEmpty { null }
            val age = info?.let { ServiceListRules.minimumAgeFor(it.ratings, country) }
            AlertDialog.Builder(this)
                .setTitle(info?.title?.ifEmpty { null } ?: e.title)
                .setMessage(buildString {
                    append("${fmt.format(Date(e.start))} to ${fmt.format(Date(e.end))}\n")
                    info?.genre?.let { append("Genre: $it\n") }
                    age?.let { append("Minimum age: $it\n") }
                    append("\n").append(info?.synopsis ?: "")
                    if (r.value == null && service.guide?.program != null) append("\n\n(Programme information request failed; shown from the schedule.)")
                })
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    companion object {
        private const val HOUR_MS = 3_600_000L
        const val EXTRA_SERVICE_UID = "serviceUid"

        fun intent(context: Context, uid: String): Intent = Intent(context, ScheduleActivity::class.java).putExtra(EXTRA_SERVICE_UID, uid)
    }
}
