package com.fivegmag.a5gmsdawareapplication

import com.fivegmag.a5gmscommonlibrary.helpers.ContentTypes
import com.fivegmag.a5gmscommonlibrary.models.EntryPoint

/** What the sample application plays when the streaming access of its media delivery session changes (STREAMING_ACCESS_UPDATED, TS 26.510 V19.2.0 clause 11.2.3) */
object StreamingAccessUpdate {
    /** The entry point to play: the first DASH entry point, else the first HLS entry point; null when the update has none the player handles */
    fun entryPointToPlay(entryPoints: List<EntryPoint>?): EntryPoint? =
        entryPoints?.firstOrNull { it.contentType == ContentTypes.DASH }
            ?: entryPoints?.firstOrNull { it.contentType == ContentTypes.HLS }

    /** Whether the player has to be attached again: the locator that plays now is not the new one */
    fun needsReattach(currentLocator: String?, next: EntryPoint?): Boolean = next != null && next.locator != currentLocator
}
