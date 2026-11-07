package com.prism.music

import com.prism.music.playback.AutoMixBoundary
import com.prism.music.playback.AutoMixPlan
import com.prism.music.playback.AutoMixReason
import com.prism.music.playback.AutoMixShadowDecision
import com.prism.music.playback.IntelligentAutoMixActivation
import com.prism.music.playback.IntelligentAutoMixPlanner
import com.prism.music.playback.IntelligentAutoMixShadow
import com.prism.music.playback.MixEngine
import com.prism.music.playback.Tempo
import com.prism.music.data.lyrics.LyricLine
import com.prism.music.data.lyrics.Lrc
import com.prism.music.data.lyrics.Ttml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligentAutoMixPlannerTest {
    private fun tempo(bpm: Double = 120.0, firstBeatMs: Long = 0, outroBeatMs: Long = 230_000, confidence: Double = 5.0) =
        Tempo(bpm, confidence, 0, 240_000, firstBeatMs, outroBeatMs)

    @Test
    fun letsTheHighlightPlayBeforeATransition() {
        val p = IntelligentAutoMixPlanner.plan(240_000, bestPartStartMs = 70_000, phraseBoundariesMs = listOf(80_000, 96_000, 112_000))!!
        assertEquals(98_000L, 70_000L + IntelligentAutoMixPlanner.HIGHLIGHT_PLAY_MS)
        assertTrue(p.transitionAtMs >= 98_000)
        assertEquals(112_000L, p.transitionAtMs)
        assertEquals(AutoMixReason.HIGHLIGHT_HEARD, p.reason)
    }

    @Test
    fun picksTheNearestPhraseBoundary() {
        val p = IntelligentAutoMixPlanner.plan(220_000, phraseBoundariesMs = listOf(76_000, 88_000, 101_000))!!
        assertEquals(88_000L, p.transitionAtMs)
        assertEquals(AutoMixBoundary.PHRASE, p.boundary)
    }

    @Test
    fun aTiePrefersTheLaterBoundary() {
        val p = IntelligentAutoMixPlanner.plan(220_000, phraseBoundariesMs = listOf(86_000, 94_000))!!
        assertEquals(94_000L, p.transitionAtMs)
    }

    @Test
    fun alignsToANearbyMeasuredBeatWithoutStructureData() {
        // 120 BPM, an anchor at 40.5 s: beats are 0.5 s apart and one lands exactly at 90 s.
        val p = IntelligentAutoMixPlanner.plan(220_000, tempo = tempo(firstBeatMs = 40_500))!!
        assertEquals(90_000L, p.transitionAtMs)
        assertEquals(AutoMixBoundary.BEAT, p.boundary)
    }

    @Test
    fun lateHighlightsMayRunPastThePreferredWindow() {
        val p = IntelligentAutoMixPlanner.plan(240_000, bestPartStartMs = 135_000, phraseBoundariesMs = listOf(160_000, 164_000, 176_000))!!
        assertEquals(164_000L, p.transitionAtMs)
        assertTrue(p.transitionAtMs > IntelligentAutoMixPlanner.PREFERRED_LATEST_MS)
    }

    @Test
    fun neverUsesABoundaryBeforeTheHighlightFinishes() {
        val p = IntelligentAutoMixPlanner.plan(220_000, bestPartStartMs = 100_000, phraseBoundariesMs = listOf(112_000, 128_000, 140_000))!!
        assertEquals(128_000L, p.transitionAtMs)
    }

    @Test
    fun anUnreadyNextTrackMeansNormalPlayback() {
        assertNull(IntelligentAutoMixPlanner.plan(220_000, phraseBoundariesMs = listOf(90_000), nextReady = false))
    }

    @Test
    fun anUncertainAnalysisMeansNormalPlayback() {
        assertNull(IntelligentAutoMixPlanner.plan(220_000))
        assertNull(IntelligentAutoMixPlanner.plan(220_000, tempo = tempo(confidence = 1.0)))
    }

    @Test
    fun shortSongsPlayThrough() {
        assertNull(IntelligentAutoMixPlanner.plan(60_000, phraseBoundariesMs = listOf(45_000)))
        assertNull(IntelligentAutoMixPlanner.plan(70_000, phraseBoundariesMs = listOf(45_000)))
    }

    @Test
    fun keepsEnoughOfTheSongAfterTheTransition() {
        assertNull(IntelligentAutoMixPlanner.plan(100_000, bestPartStartMs = 70_000, phraseBoundariesMs = listOf(98_000)))
    }

    @Test
    fun ignoresDistantPhraseBoundariesAndUsesTheBeatGrid() {
        val p = IntelligentAutoMixPlanner.plan(220_000, phraseBoundariesMs = listOf(45_000, 130_000), tempo = tempo(firstBeatMs = 40_000))!!
        assertEquals(90_000L, p.transitionAtMs)
        assertEquals(AutoMixBoundary.BEAT, p.boundary)
    }

    @Test
    fun shadowModeUsesInstrumentalGapsFromParsedTtml() {
        val lyrics = Ttml.parse(
            """<tt timing="Line"><body><div>
                <p begin="70s" end="82s">Lead-in line</p>
                <p begin="82s" end="86s">Last line</p>
                <p begin="94s" end="98s">Next verse</p>
            </div></body></tt>""".trimIndent(),
        )
        val shadow = IntelligentAutoMixShadow()
        val d = shadow.evaluate("song", 220_000, "next", null, lyrics, tempo())
        assertEquals(listOf(0L, 70_000L, 86_000L, 94_000L), IntelligentAutoMixShadow.phraseBoundaries(lyrics, 220_000))
        assertEquals(94_000L, d.plan!!.transitionAtMs)
        assertEquals(2, d.phraseBoundaryCount)
        assertTrue(d.hasUsableBeat)
        assertEquals(d, shadow.decision.value)
    }

    @Test
    fun shadowModeFindsAPhraseRestartInParsedLrc() {
        val lyrics = Lrc.parse("[01:20.00]Hold the note\n[01:31.00]Next phrase")
        val boundaries = IntelligentAutoMixShadow.phraseBoundaries(lyrics, 220_000)
        assertTrue(91_000L in boundaries)
        val d = IntelligentAutoMixShadow().evaluate("song", 220_000, "next", null, lyrics, tempo())
        assertEquals(91_000L, d.plan!!.transitionAtMs)
    }

    @Test
    fun ordinaryLyricChangesAreNotSafeBoundaries() {
        val lyrics = Lrc.parse("[00:05.00]One\n[00:09.00]Two\n[00:13.00]Three")
        assertEquals(listOf(0L, 5_000L), IntelligentAutoMixShadow.phraseBoundaries(lyrics, 220_000))
    }

    @Test
    fun shadowModeNeverMakesANextTrackReady() {
        val shadow = IntelligentAutoMixShadow()
        val d = shadow.evaluate("song", 220_000, null, null, emptyList(), tempo())
        assertNull(d.plan)
        shadow.clear()
        assertNull(shadow.decision.value)
    }

    @Test
    fun repeatingTheSameSongIsNotANextTrack() {
        val d = IntelligentAutoMixShadow().evaluate("song", 220_000, "song", null, emptyList(), tempo(firstBeatMs = 40_000))
        assertNull(d.plan)
    }

    @Test
    fun anExtremelyDistantBeatAnchorIsTooUncertainToUse() {
        assertNull(IntelligentAutoMixPlanner.plan(320_000, bestPartStartMs = 135_000, tempo = tempo(firstBeatMs = 0, outroBeatMs = 320_000)))
    }

    @Test
    fun boundariesAreSortedUniqueAndTolerateUnknownEnds() {
        val lyrics = listOf(
            LyricLine(94_000, "Next verse", endMs = 98_000),
            LyricLine(86_000, "", endMs = 94_000, isGap = true),
            LyricLine(82_000, "Last line"),
            LyricLine(86_000, "", endMs = 94_000, isGap = true),
        )
        assertEquals(listOf(86_000L, 94_000L), IntelligentAutoMixShadow.phraseBoundaries(lyrics, 220_000))
    }

    @Test
    fun aTrailingGapEndsAtTheSongEnd() {
        val lyrics = listOf(LyricLine(100_000, "", isGap = true))
        assertEquals(listOf(100_000L, 220_000L), IntelligentAutoMixShadow.phraseBoundaries(lyrics, 220_000))
    }

    @Test
    fun liveActivationRequiresTheSwitchAndTheExactSongPair() {
        val decision = AutoMixShadowDecision(
            songId = "from",
            nextSongId = "to",
            plan = AutoMixPlan(94_000, AutoMixReason.HIGHLIGHT_HEARD, AutoMixBoundary.PHRASE),
            bestPartStartMs = 60_000,
            phraseBoundaryCount = 1,
            hasUsableBeat = true,
        )
        assertNull(IntelligentAutoMixActivation.transitionAt(false, decision, "from", "to"))
        assertNull(IntelligentAutoMixActivation.transitionAt(true, decision, "other", "to"))
        assertNull(IntelligentAutoMixActivation.transitionAt(true, decision, "from", "other"))
        assertEquals(94_000L, IntelligentAutoMixActivation.transitionAt(true, decision, "from", "to"))
        assertNull(IntelligentAutoMixActivation.entryAt(true, decision, "from", "to"))
    }

    @Test
    fun incomingSongLeadsIntoItsHighlightFromAPhrase() {
        val entry = IntelligentAutoMixPlanner.entryPoint(
            durationMs = 240_000,
            bestPartStartMs = 72_000,
            phraseBoundariesMs = listOf(32_000, 48_000, 55_000, 80_000),
        )
        assertEquals(55_000L, entry)
    }

    @Test
    fun incomingSongUsesAMeasuredBeatWhenNoPhraseIsKnown() {
        val entry = IntelligentAutoMixPlanner.entryPoint(
            durationMs = 240_000,
            bestPartStartMs = 70_000,
            tempo = tempo(firstBeatMs = 20_000),
        )
        assertEquals(50_000L, entry)
    }

    @Test
    fun incomingSongStillKeepsItsOpeningWhenNoStructureIsKnown() {
        assertEquals(0L, IntelligentAutoMixPlanner.entryPoint(240_000, null))
    }

    @Test
    fun incomingSongUsesAPhraseRestartWhenTheHighlightIsUnknown() {
        assertEquals(22_000L, IntelligentAutoMixPlanner.entryPoint(240_000, null, listOf(6_000, 22_000, 61_000)))
    }

    @Test
    fun incomingSongKeepsItsOpeningWhenOnlyABeatIsKnown() {
        assertEquals(
            0L,
            IntelligentAutoMixPlanner.entryPoint(240_000, null, tempo = tempo(firstBeatMs = 0, outroBeatMs = 180_000)),
        )
    }

    @Test
    fun shadowDecisionCarriesTheIncomingCueAndGuardsItByPair() {
        val nextLyrics = Lrc.parse("[00:30.00]Verse\n[00:50.00]Chorus lead-in\n[01:10.00]Chorus")
        val decision = IntelligentAutoMixShadow().evaluate(
            songId = "from",
            durationMs = 220_000,
            nextSongId = "to",
            bestPartStartMs = null,
            lyrics = emptyList(),
            tempo = tempo(firstBeatMs = 40_000),
            nextDurationMs = 240_000,
            nextBestPartStartMs = 70_000,
            nextLyrics = nextLyrics,
            nextTempo = tempo(firstBeatMs = 20_000),
        )
        assertEquals(50_000L, decision.plan!!.entryAtMs)
        assertEquals(50_000L, IntelligentAutoMixActivation.entryAt(true, decision, "from", "to"))
        assertNull(IntelligentAutoMixActivation.entryAt(true, decision, "from", "other"))
    }

    @Test
    fun aPassedEarlyTransitionFallsBackInsteadOfPoisoningTheSong() {
        assertEquals(90_000L, MixEngine.usableEarlyStart(90_000, 10_000, 220_000))
        assertNull(MixEngine.usableEarlyStart(90_000, 89_000, 220_000))
        assertNull(MixEngine.usableEarlyStart(230_000, 10_000, 220_000))
    }

    @Test
    fun enhancedLrcUsesTheMeasuredLastWordEndAsAPhraseBoundary() {
        val lyrics = Lrc.parse(
            "[01:20.00]<01:20.00>Hold <01:24.00>note<01:26.00>\n" +
                "[01:34.00]<01:34.00>Next phrase",
        )
        assertTrue(lyrics.first { !it.isGap }.endEstimated)
        assertTrue(86_000L in IntelligentAutoMixShadow.phraseBoundaries(lyrics, 220_000))
    }
}
