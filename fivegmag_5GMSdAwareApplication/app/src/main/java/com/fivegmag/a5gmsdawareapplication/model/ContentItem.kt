/*
License: 5G-MAG Public License (v1.0)
Author: Daniel Silhavy
Copyright: (C) 2024 Fraunhofer FOKUS
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.a5gmsdawareapplication.model

import com.fivegmag.a5gmscommonlibrary.models.ServiceListEntry

/**
 * Combines a ServiceListEntry from the M8 data with optional display metadata.
 * Used as the UI model for the content grid on the landing page.
 */
data class ContentItem(
    val serviceListEntry: ServiceListEntry,
    val metadata: ContentMetadata?
) {

    val title: String
        get() = metadata?.title ?: serviceListEntry.name

    val description: String
        get() = metadata?.description ?: ""

    val posterUrl: String
        get() = metadata?.posterUrl ?: ""

    val mediaType: String
        get() = metadata?.mediaType ?: "movie"

    /**
     * Returns a JSON string representation of the ServiceListEntry
     * for passing via Intent extras.
     */
    val serviceListEntryJson: String
        get() = ServiceListEntrySerializer.toJson(serviceListEntry)
}
