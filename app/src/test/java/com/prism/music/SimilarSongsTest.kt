package com.prism.music

import com.prism.music.playback.MusicKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarSongsTest {
    private fun lang(vararg t: String) = LanguageGuess.detect(t.toList()).language

    @Test
    fun scriptsDecideAtOnce() {
        assertEquals(SongLanguage.PUNJABI, lang("ਤੇਰੇ ਨਾਲ ਨਾਲ"))
        assertEquals(SongLanguage.HINDI, lang("तुम ही हो"))
        assertEquals(SongLanguage.OTHER, lang("사랑해요 그대"))
    }

    @Test
    fun romanisedPunjabiAndHindiAreToldApart() {
        assertEquals(SongLanguage.PUNJABI, lang("Kudi Tenu Vekh Ke", "mainu tere nal pyar ho gaya kudiye ni"))
        assertEquals(SongLanguage.HINDI, lang("Tum Hi Ho", "hum tere bin ab reh nahi sakte, tere bina kya wajood mera, mujhe kya hai"))
        // Shared words only ("dil", "yaar", "tera"): Punjabi or Hindi, the same group either way.
        assertEquals(LanguageGroup.DESI, lang("Dil Yaar Tera").group)
    }

    @Test
    fun englishIsItsOwnGroup() {
        assertEquals(SongLanguage.ENGLISH, lang("Love Me Like You Do", "you're the light, you're the night, you're the color of my blood"))
        assertEquals(LanguageGroup.ENGLISH, lang("Shape of You").group)
    }

    @Test
    fun aBhangraRemixWithEnglishWordsStaysDesi() {
        assertEquals(LanguageGroup.DESI, lang("Jatt Da Muqabala (Bhangra Remix)", "tenu pata jatt da muqabala, the party is on, jatti nachdi").group)
    }

    @Test
    fun titlesWithNothingToGoByAreUnknown() {
        // One Punjabi word is enough when nothing else is said.
        assertEquals(LanguageGroup.DESI, lang("Brown Munde 2").group)
        // A Punjabi song with an English-word title, or a number: unknown from the title (its artist's other songs, or
        // its lyrics, settle it in the finder).
        assertEquals(SongLanguage.UNKNOWN, lang("Excuses"))
        assertEquals(SongLanguage.UNKNOWN, lang("295"))
    }

    @Test
    fun soundsAlikeScoreHigherThanSoundsApart() {
        val k8a = MusicKey(9, true)
        val seed = SoundFeatures(122.0, k8a, -9f)
        val twin = SoundFeatures(61.0, k8a, -9.5f) // half-time is the same beat
        val far = SoundFeatures(90.0, MusicKey(3, false), -18f)
        val unknown = SoundFeatures(null, null, null)
        val a = SoundMatch.score(seed, twin); val b = SoundMatch.score(seed, unknown); val c = SoundMatch.score(seed, far)
        assertTrue("$a > $b > $c", a > b && b > c)
        assertEquals(0.5f, b, 1e-4f)
        // YouTube's own order counts too: the same sound ranked first beats it ranked last.
        assertTrue(SoundMatch.combined(0.7f, 0, 20) > SoundMatch.combined(0.7f, 19, 20))
    }
}
