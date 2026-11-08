package com.prism.music.playback

import com.prism.music.data.prefs.DjStyle

/** What one blend does, once the listener's [DjStyle] has met the pair of songs. */
enum class DjBlend { PLAIN, SMOOTH, CLUB, ECHO }

object DjBlends {
    /**
     * [style] null: DJ effects are off. [beatsMatched]: the two songs' beats are lined up for this blend.
     * [endingBeatKnown]: the ending song's tempo is clear enough to time an echo by.
     */
    fun resolve(style: DjStyle?, beatsMatched: Boolean, endingBeatKnown: Boolean): DjBlend = when (style) {
        null -> DjBlend.PLAIN
        DjStyle.SMOOTH -> DjBlend.SMOOTH
        DjStyle.CLUB -> DjBlend.CLUB
        DjStyle.ECHO -> DjBlend.ECHO
        // A filter sweep needs no beat, so without one Auto still sweeps (a bass swap alone is lost on a phone's speaker).
        DjStyle.AUTO -> when {
            beatsMatched -> DjBlend.CLUB
            endingBeatKnown -> DjBlend.ECHO
            else -> DjBlend.CLUB
        }
    }

    /**
     * The echo's delay: one beat of the ending song when it's known, else two beats (a very fast song) or half a beat
     * (a very slow one), whichever lands in a usable range; 3/8 s when its beat isn't known.
     */
    fun echoDelayMs(endingBeatMs: Double?): Double {
        val beat = endingBeatMs?.takeIf { it > 0 } ?: return DEFAULT_ECHO_MS
        return listOf(beat, beat * 2, beat / 2).firstOrNull { it in MIN_ECHO_MS..MAX_ECHO_MS } ?: DEFAULT_ECHO_MS
    }

    private const val MIN_ECHO_MS = 250.0
    private const val MAX_ECHO_MS = 1_000.0
    const val DEFAULT_ECHO_MS = 375.0
}
