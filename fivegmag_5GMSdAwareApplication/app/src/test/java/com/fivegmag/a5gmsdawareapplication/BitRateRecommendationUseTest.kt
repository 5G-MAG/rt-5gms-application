package com.fivegmag.a5gmsdawareapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BitRateRecommendationUseTest {
    @Test
    fun theUnitsOfABitRateAreConverted() {
        assertEquals(3_000_000, BitRateRecommendationUse.maxVideoBitrate("3 Mbps"))
        assertEquals(500_000, BitRateRecommendationUse.maxVideoBitrate("500 Kbps"))
        assertEquals(2_000_000, BitRateRecommendationUse.maxVideoBitrate("2000000 bps"))
        assertEquals(1_500_000, BitRateRecommendationUse.maxVideoBitrate("1.5 Mbps"))
    }

    @Test
    fun whatIsNotABitRateGivesNoCeiling() {
        assertNull(BitRateRecommendationUse.maxVideoBitrate(null))
        assertNull(BitRateRecommendationUse.maxVideoBitrate(""))
        assertNull(BitRateRecommendationUse.maxVideoBitrate("fast"))
        assertNull(BitRateRecommendationUse.maxVideoBitrate("3Mbps"))
        assertNull(BitRateRecommendationUse.maxVideoBitrate("0 bps"))
        assertNull(BitRateRecommendationUse.maxVideoBitrate("-3 Mbps"))
    }

    @Test
    fun noCeilingIsTheUnrestrictedValueOfThePlayer() {
        assertEquals(Int.MAX_VALUE, BitRateRecommendationUse.NO_CEILING)
    }

    @Test
    fun aRateAboveWhatThePlayerTakesGivesNoCeiling() {
        assertNull(BitRateRecommendationUse.maxVideoBitrate("3 Gbps"))
    }
}
