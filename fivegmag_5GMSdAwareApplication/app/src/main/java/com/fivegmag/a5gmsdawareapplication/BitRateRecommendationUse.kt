package com.fivegmag.a5gmsdawareapplication

/**
 * What the sample application does with a bit rate recommendation (TS 26.501 clause 4.0.5): the recommended downlink bit rate becomes the
 * ceiling of the video bit rate that the player may choose, so that it switches to a representation that fits.
 */
object BitRateRecommendationUse {
    /** the maximum video bit rate that leaves the player unrestricted (the default of its track selection parameters) */
    const val NO_CEILING = Int.MAX_VALUE

    private val bitRate = Regex("""^(\d+(?:\.\d+)?) (bps|Kbps|Mbps|Gbps|Tbps)$""")
    private val factors = mapOf("bps" to 1.0, "Kbps" to 1e3, "Mbps" to 1e6, "Gbps" to 1e9, "Tbps" to 1e12)

    /** The ceiling in bit/s for a BitRate string such as "3 Mbps" (TS 29.571 BitRate); null when it is not one or exceeds what the player takes (Int) */
    fun maxVideoBitrate(recommended: String?): Int? {
        val match = bitRate.matchEntire(recommended ?: return null) ?: return null
        val bps = match.groupValues[1].toDouble() * factors.getValue(match.groupValues[2])
        return if (bps >= 1 && bps <= Int.MAX_VALUE) bps.toInt() else null
    }
}
