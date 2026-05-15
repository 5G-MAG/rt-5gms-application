/*
License: 5G-MAG Public License (v1.0)
Author: GitHub Copilot
Copyright: (C) 2026 Fraunhofer FOKUS
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.a5gmsdawareapplication

import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataProviderTest {

    private val metadataProvider = MetadataProvider()

    @Test
    fun resolvePosterUrl_keepsAbsolutePosterUrlUntouched() {
        val posterUrl = "https://cdn.example.com/posters/livesim.jpg"

        val resolvedPosterUrl = metadataProvider.resolvePosterUrl(
            posterUrl,
            "https://example.com/examples/metadata.json"
        )

        assertEquals(posterUrl, resolvedPosterUrl)
    }

    @Test
    fun resolvePosterUrl_resolvesRemoteRelativePosterAgainstMetadataUrl() {
        val resolvedPosterUrl = metadataProvider.resolvePosterUrl(
            "posters/livesim.jpg",
            "https://example.com/examples/metadata.json"
        )

        assertEquals(
            "https://example.com/examples/posters/livesim.jpg",
            resolvedPosterUrl
        )
    }

    @Test
    fun resolvePosterUrl_resolvesLocalRelativePosterAgainstMetadataAssetPath() {
        val resolvedPosterUrl = metadataProvider.resolvePosterUrl(
            "posters/livesim.jpg",
            "examples/metadata.json"
        )

        assertEquals(
            "file:///android_asset/examples/posters/livesim.jpg",
            resolvedPosterUrl
        )
    }

    @Test
    fun resolvePosterUrl_normalizesParentSegmentsForLocalAssetPaths() {
        val resolvedPosterUrl = metadataProvider.resolvePosterUrl(
            "../posters/livesim.jpg",
            "examples/catalog/metadata.json"
        )

        assertEquals(
            "file:///android_asset/examples/posters/livesim.jpg",
            resolvedPosterUrl
        )
    }
}

