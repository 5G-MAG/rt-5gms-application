/*
License: 5G-MAG Public License (v1.0)
Author: Daniel Silhavy
Copyright: (C) 2024 Fraunhofer FOKUS
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.a5gmsdawareapplication.model

/**
 * Represents a category row on the landing page.
 * Each category groups content items of the same media type
 * into a horizontally scrollable carousel.
 */
data class ContentCategory(
    val label: String,
    val mediaType: String,
    val items: List<ContentItem>
)
