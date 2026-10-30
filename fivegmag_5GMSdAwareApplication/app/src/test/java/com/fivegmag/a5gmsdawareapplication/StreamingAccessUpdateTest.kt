package com.fivegmag.a5gmsdawareapplication

import com.fivegmag.a5gmscommonlibrary.helpers.ContentTypes
import com.fivegmag.a5gmscommonlibrary.models.EntryPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingAccessUpdateTest {
    private fun entry(locator: String, type: String?) = EntryPoint(locator, type, null)

    @Test
    fun theFirstDashEntryPointIsPlayedBeforeHls() {
        val list = listOf(entry("a.m3u8", ContentTypes.HLS), entry("b.mpd", ContentTypes.DASH), entry("c.mpd", ContentTypes.DASH))
        assertEquals("b.mpd", StreamingAccessUpdate.entryPointToPlay(list)!!.locator)
    }

    @Test
    fun hlsIsPlayedWithoutDash() {
        assertEquals("a.m3u8", StreamingAccessUpdate.entryPointToPlay(listOf(entry("a.m3u8", ContentTypes.HLS)))!!.locator)
    }

    @Test
    fun nothingIsPlayedWithoutAnEntryPointThePlayerHandles() {
        assertNull(StreamingAccessUpdate.entryPointToPlay(listOf(entry("x", null))))
        assertNull(StreamingAccessUpdate.entryPointToPlay(null))
    }

    @Test
    fun thePlayerIsAttachedAgainOnlyForANewLocator() {
        val next = entry("new.mpd", ContentTypes.DASH)
        assertTrue(StreamingAccessUpdate.needsReattach("old.mpd", next))
        assertFalse(StreamingAccessUpdate.needsReattach("new.mpd", next))
        assertFalse(StreamingAccessUpdate.needsReattach("old.mpd", null))
    }
}
