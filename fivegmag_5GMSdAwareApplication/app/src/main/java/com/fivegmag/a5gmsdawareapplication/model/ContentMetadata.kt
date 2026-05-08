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
 * Holds display metadata for a content item, loaded from a metadata JSON file.
 * The metadata is mapped to M8 service list entries via the entry name.
 */
data class ContentMetadata(
    val title: String,
    val description: String,
    val posterUrl: String,
    val mediaType: String
)
