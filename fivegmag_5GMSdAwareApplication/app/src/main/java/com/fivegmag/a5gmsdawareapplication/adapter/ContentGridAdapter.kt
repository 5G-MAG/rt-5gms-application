/*
License: 5G-MAG Public License (v1.0)
Author: Daniel Silhavy
Copyright: (C) 2024 Fraunhofer FOKUS
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.a5gmsdawareapplication.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.fivegmag.a5gmsdawareapplication.R
import com.fivegmag.a5gmsdawareapplication.model.ContentItem

/**
 * RecyclerView adapter for displaying content items in a grid on the landing page.
 * Each item shows a poster image with a title overlay and media type badge.
 */
class ContentGridAdapter(
    private val onItemClick: (ContentItem) -> Unit
) : RecyclerView.Adapter<ContentGridAdapter.ContentViewHolder>() {

    private val items: ArrayList<ContentItem> = ArrayList()

    fun updateItems(newItems: List<ContentItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_content_card, parent, false)
        return ContentViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContentViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    inner class ContentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val posterImage: ImageView = itemView.findViewById(R.id.posterImage)
        private val contentTitle: TextView = itemView.findViewById(R.id.contentTitle)
        private val mediaTypeBadge: TextView = itemView.findViewById(R.id.mediaTypeBadge)

        fun bind(item: ContentItem) {
            contentTitle.text = item.title

            // Set media type badge
            if (item.mediaType == "tv_show") {
                mediaTypeBadge.text = itemView.context.getString(R.string.media_type_tv_show)
                mediaTypeBadge.setBackgroundResource(R.drawable.bg_badge_tv)
            } else {
                mediaTypeBadge.text = itemView.context.getString(R.string.media_type_movie)
                mediaTypeBadge.setBackgroundResource(R.drawable.bg_badge_movie)
            }

            // Load poster image
            if (item.posterUrl.isNotEmpty()) {
                posterImage.load(item.posterUrl) {
                    crossfade(true)
                    placeholder(R.drawable.bg_poster_placeholder)
                    error(R.drawable.bg_poster_placeholder)
                    transformations(RoundedCornersTransformation(8f))
                }
            } else {
                posterImage.setImageResource(0)
                posterImage.setBackgroundResource(R.drawable.bg_poster_placeholder)
            }

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
