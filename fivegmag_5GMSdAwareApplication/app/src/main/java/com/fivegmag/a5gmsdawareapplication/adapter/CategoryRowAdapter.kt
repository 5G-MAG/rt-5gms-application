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
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fivegmag.a5gmsdawareapplication.R
import com.fivegmag.a5gmsdawareapplication.model.ContentCategory
import com.fivegmag.a5gmsdawareapplication.model.ContentItem

/**
 * Outer vertical RecyclerView adapter for the landing page.
 * Each item is a category row containing a header label and a
 * horizontal carousel of content cards.
 */
class CategoryRowAdapter(
    private val onItemClick: (ContentItem) -> Unit
) : RecyclerView.Adapter<CategoryRowAdapter.CategoryViewHolder>() {

    private val categories: ArrayList<ContentCategory> = ArrayList()
    private val viewPool = RecyclerView.RecycledViewPool()

    fun updateCategories(newCategories: List<ContentCategory>) {
        categories.clear()
        categories.addAll(newCategories)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_row, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount(): Int = categories.size

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
