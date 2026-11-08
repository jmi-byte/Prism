package com.prism.music

import com.prism.music.playback.DjTrack
import com.prism.music.playback.MusicKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VelouraFeaturesTest {
    private val a8 = MusicKey(9, true)

    @Test
    fun reasonsSayWhatWasMeasuredAndWhereTheSongCameFrom() {
        val seed = SoundFeatures(98.0, a8, -9f)
        assertEquals(
            "Same key, same tempo as Brown Munde · Punjabi · Related on YouTube Music",
            SimilarReasons.why("Brown Munde", SongLanguage.PUNJABI, seed, SoundFeatures(98.5, a8, -9f), related = true),
        )
        // 9A blends with 8A; 104 BPM is about 6% faster.
        assertEquals(
            "A key that blends, close tempo to Brown Munde · Hindi · On Brown Munde's radio",
            SimilarReasons.why("Brown Munde", SongLanguage.HINDI, seed, SoundFeatures(104.0, MusicKey(4, true), null), related = false),
        )
        // Nothing measured: only where it came from.
        assertEquals("Related on YouTube Music", SimilarReasons.why("X", SongLanguage.UNKNOWN, seed, SoundFeatures(null, null, null), true))
    }

    @Test
    fun theLanguageMixSteersTowardsTheChosenBalance() {
        val d = LanguageGroup.DESI; val e = LanguageGroup.ENGLISH
        assertEquals(e, LanguageMix.nextGroup(listOf(d, d, d, d), 70))
        assertEquals(d, LanguageMix.nextGroup(listOf(d, e, e, e), 70))
        // Unknown and other languages don't count.
        assertEquals(d, LanguageMix.nextGroup(listOf(LanguageGroup.UNKNOWN, LanguageGroup.OTHER), 70))
        assertEquals(e, LanguageMix.nextGroup(emptyList(), 30))
        assertEquals("70% Punjabi & Hindi · 30% English", LanguageMix.describe(70))
        assertEquals("Follows each song's language", LanguageMix.describe(-1))
    }

    private fun track(id: String, loud: Float, bpm: Double = 110.0) = DjTrack(id, null, bpm, loud, id)

    @Test
    fun setsFollowTheirShapeOfEnergy() {
        val start = track("now", -10f)
        val songs = (1..9).map { track("s$it", -20f + it * 1.5f) } // s1 calmest … s9 loudest
        fun loudness(list: List<DjTrack>) = list.map { it.loudness!! }
        val build = EnergySets.arrange(start, songs.shuffled(java.util.Random(4)), SetShape.BUILD)
        assertTrue(loudness(build).first() < loudness(build).last())
        val down = EnergySets.arrange(start, songs.shuffled(java.util.Random(5)), SetShape.WIND_DOWN)
        assertTrue(loudness(down).first() > loudness(down).last())
        val peak = EnergySets.arrange(start, songs.shuffled(java.util.Random(6)), SetShape.PEAK)
        val l = loudness(peak)
        val top = l.indexOf(l.max())
        // The most intense song sits in the middle third, and both ends are calmer than it.
        assertTrue("peak at $top of ${l.size}: $l", top in 2..6 && l.first() < l.max() && l.last() < l.max())
        assertEquals(songs.map { it.id }.toSet(), peak.map { it.id }.toSet())
    }
}
