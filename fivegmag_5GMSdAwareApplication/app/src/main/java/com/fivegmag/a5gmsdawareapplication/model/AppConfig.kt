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
 * Represents a single M8 data source entry in the application config.
 *
 * @param name Display label shown in the Settings spinner
 * @param m8Url Either a full URL (http://...) for remote, or a relative path (m8/...) for local assets
 * @param metadataUrl Optional explicit metadata location. null = derive from m8Url, "" = no metadata
 */
data class M8Source(
    val name: String,
    val m8Url: String,
    val metadataUrl: String? = null
)

/**
 * Top-level application configuration loaded from app_config.json.
 * Contains the list of available M8 data sources.
 */
data class AppConfig(
    val sources: List<M8Source>
)
