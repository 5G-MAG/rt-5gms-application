/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.fivegmag.dvbiclient.R
import com.fivegmag.dvbiclient.servicelist.Delivery
import com.fivegmag.dvbiclient.servicelist.Service

/** One row of the channel list. */
data class ChannelRow(val service: Service, val lcn: Int?, val minimumAge: Int?, var nowNext: String = "")

/** The channel list: channel number, logo, name, 5G Broadcast badge, rating and now/next. */
class ChannelAdapter(
    private val onSelect: (ChannelRow) -> Unit,
    private val onSchedule: (ChannelRow) -> Unit,
) : RecyclerView.Adapter<ChannelAdapter.Holder>() {

    private var rows: List<ChannelRow> = emptyList()

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val lcn: TextView = v.findViewById(R.id.lcn)
        val logo: ImageView = v.findViewById(R.id.logo)
        val name: TextView = v.findViewById(R.id.name)
        val badge5g: TextView = v.findViewById(R.id.badge5g)
        val rating: TextView = v.findViewById(R.id.rating)
        val nowNext: TextView = v.findViewById(R.id.nowNext)
        val schedule: Button = v.findViewById(R.id.schedule)
    }

    fun submit(newRows: List<ChannelRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    fun updateNowNext(uid: String, text: String) {
        val i = rows.indexOfFirst { it.service.uid == uid }
        if (i < 0) return
        rows[i].nowNext = text
        notifyItemChanged(i)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false))

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(h: Holder, position: Int) {
        val row = rows[position]
        val s = row.service
        h.lcn.text = row.lcn?.toString() ?: ""
        h.name.text = s.name
        h.badge5g.visibility = if (s.instances.any { it.delivery is Delivery.Mbms }) View.VISIBLE else View.GONE
        h.rating.text = row.minimumAge?.takeIf { it > 0 }?.let { "$it+" } ?: ""
        h.nowNext.text = row.nowNext
        h.nowNext.visibility = if (row.nowNext.isEmpty()) View.GONE else View.VISIBLE
        val logo = s.logo
        if (logo != null) h.logo.load(logo.url) { crossfade(false) } else h.logo.setImageDrawable(null)
        h.itemView.setOnClickListener { onSelect(row) }
        h.schedule.visibility = if (s.guide != null) View.VISIBLE else View.INVISIBLE
        h.schedule.setOnClickListener { onSchedule(row) }
    }
}
