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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.fivegmag.a5gmsdawareapplication.R
import com.fivegmag.a5gmsdawareapplication.model.ContentCategory
import com.fivegmag.a5gmsdawareapplication.model.ContentItem

/**
 * Outer vertical RecyclerView adapter for the landing page.
 * Supports two view types:
 * - Hero banner (position 0 when a featured item is set)
 * - Category rows (horizontal carousels of content cards)
 */
class CategoryRowAdapter(
    private val onItemClick: (ContentItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HERO = 0
        private const val VIEW_TYPE_CATEGORY = 1
    }

    private val categories: ArrayList<ContentCategory> = ArrayList()
    private val viewPool = RecyclerView.RecycledViewPool()
    private var heroItem: ContentItem? = null

    /**
     * Sets the featured/hero item shown at the top of the list.
     * Pass null to remove the hero banner.
     */
    fun setHeroItem(item: ContentItem?) {
        heroItem = item
    }

    fun updateCategories(newCategories: List<ContentCategory>) {
        categories.clear()
        categories.addAll(newCategories)
        notifyDataSetChanged()
    }

    private fun hasHero(): Boolean = heroItem != null

    private fun heroOffset(): Int = if (hasHero()) 1 else 0

    override fun getItemViewType(position: Int): Int {
        return if (hasHero() && position == 0) VIEW_TYPE_HERO else VIEW_TYPE_CATEGORY
    }

    override fun getItemCount(): Int = categories.size + heroOffset()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HERO -> {
                val view = inflater.inflate(R.layout.item_hero_banner, parent, false)
                HeroViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_category_row, parent, false)
                CategoryViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeroViewHolder -> {
                heroItem?.let { holder.bind(it) }
            }
            is CategoryViewHolder -> {
                val categoryIndex = position - heroOffset()
                holder.bind(categories[categoryIndex])
            }
        }
    }

    /**
     * ViewHolder for the hero/featured banner at the top of the home screen.
     */
    inner class HeroViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val posterImage: ImageView = itemView.findViewById(R.id.heroPosterImage)
        private val titleText: TextView = itemView.findViewById(R.id.heroTitle)
        private val descriptionText: TextView = itemView.findViewById(R.id.heroDescription)

        fun bind(item: ContentItem) {
            titleText.text = item.title
            descriptionText.text = item.description

            if (item.posterUrl.isNotEmpty()) {
                posterImage.load(item.posterUrl) {
                    crossfade(true)
                    placeholder(R.drawable.bg_poster_placeholder)
                    error(R.drawable.bg_poster_placeholder)
                    transformations(RoundedCornersTransformation(12f))
                }
            } else {
                posterImage.setImageDrawable(null)
                posterImage.setBackgroundResource(R.drawable.bg_poster_placeholder)
            }

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    /**
     * ViewHolder for a category row containing a header label
     * and a horizontal carousel of content cards.
     */
    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val categoryLabel: TextView = itemView.findViewById(R.id.categoryLabel)
        private val categoryRecyclerView: RecyclerView =
            itemView.findViewById(R.id.categoryRecyclerView)

        fun bind(category: ContentCategory) {
            categoryLabel.text = category.label

            val horizontalAdapter = ContentCardAdapter(onItemClick)
            categoryRecyclerView.layoutManager = LinearLayoutManager(
                itemView.context, LinearLayoutManager.HORIZONTAL, false
            )
            categoryRecyclerView.adapter = horizontalAdapter
            categoryRecyclerView.setRecycledViewPool(viewPool)
            horizontalAdapter.updateItems(category.items)
        }
    }
}
