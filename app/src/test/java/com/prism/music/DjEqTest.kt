package com.prism.music

import com.prism.music.playback.BassKill
import com.prism.music.playback.MixEngine
import com.prism.music.playback.SweepFilter
import com.prism.music.playback.DjBlend
import com.prism.music.playback.DjBlends
import com.prism.music.playback.EchoOut
import com.prism.music.data.prefs.DjStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class DjEqTest {
    private val rate = 48_000

    /** RMS of a stereo sine at [hz] after a second through [kill] (the second half, once the knob has settled). */
    private fun rmsThrough(kill: BassKill, hz: Double): Double {
        val frames = rate
        val buf = FloatArray(frames * 2) { i -> (0.5 * sin(2 * PI * hz * (i / 2) / rate)).toFloat() }
        kill.process(buf, frames)
        var s = 0.0
        for (i in frames until frames * 2) s += buf[i] * buf[i].toDouble()
        return sqrt(s / frames)
    }

    private fun db(rms: Double) = 20 * kotlin.math.log10(rms / (0.5 / sqrt(2.0)))

    private fun kill(level: Float) = BassKill().apply { configure(rate, 2); setTarget(level) }

    @Test
    fun fullBassLeavesTheSoundUntouched() {
        val k = kill(1f)
        val buf = FloatArray(2_000) { (it % 7) / 10f }
        val copy = buf.copyOf()
        k.process(buf, 1_000)
        assertTrue(k.idle)
        assertTrue(buf.contentEquals(copy))
    }

    @Test
    fun aCutTakesTheLowEndDownAndLeavesTheMixAlone() {
        assertTrue(db(rmsThrough(kill(0f), 50.0)) < -20)
        assertTrue(db(rmsThrough(kill(0f), 3_000.0)) > -1)
    }

    @Test
    fun theKnobGlidesInsteadOfJumping() {
        val k = kill(1f)
        k.process(FloatArray(512), 256)
        k.setTarget(0f)
        // 1 ms of a full-scale step: the output can't have dropped all the way yet.
        val buf = FloatArray(96) { 0.5f }
        k.process(buf, 48)
        assertTrue(buf.last() > 0.3f)
    }

    @Test
    fun theSwapLandsOnWholeBarsNearTheMiddle() {
        // 120 BPM (beat 500 ms, bar 2 s), an 8 s blend: 2 bars in, exactly the middle.
        assertEquals(0.5f, MixEngine.bassSwapAt(8_000, 500.0), 1e-6f)
        // A 9 s blend: the middle (4.5 s) moves to 2 bars (4 s).
        assertEquals(4_000f / 9_000f, MixEngine.bassSwapAt(9_000, 500.0), 1e-6f)
        // A 3 s blend: a bar is too long, so whole beats (1.5 s).
        assertEquals(0.5f, MixEngine.bassSwapAt(3_000, 500.0), 1e-6f)
        // No beat known: the middle.
        assertEquals(0.5f, MixEngine.bassSwapAt(6_000, null), 1e-6f)
    }

    // Filter sweep.

    private fun sweep(hz: Float) = SweepFilter().apply { configure(rate, 2); setTarget(hz) }

    /** Like [rmsThrough], for the sweep filter. */
    private fun rmsSwept(f: SweepFilter, hz: Double): Double {
        val frames = rate
        val buf = FloatArray(frames * 2) { i -> (0.5 * sin(2 * PI * hz * (i / 2) / rate)).toFloat() }
        f.process(buf, frames)
        var s = 0.0
        for (i in frames until frames * 2) s += buf[i] * buf[i].toDouble()
        return sqrt(s / frames)
    }

    @Test
    fun anOpenFilterLeavesTheSoundUntouched() {
        val f = sweep(SweepFilter.OPEN_HZ)
        val buf = FloatArray(2_000) { (it % 5) / 10f }
        val copy = buf.copyOf()
        f.process(buf, 1_000)
        assertTrue(f.idle)
        assertTrue(buf.contentEquals(copy))
    }

    @Test
    fun aClosedFilterRollsTheHighsAwayAndKeepsTheLows() {
        assertTrue(db(rmsSwept(sweep(300f), 5_000.0)) < -30)
        assertTrue(db(rmsSwept(sweep(300f), 60.0)) > -1.5)
    }

    @Test
    fun startingTheFilterDoesNotClick() {
        // A constant signal (DC) straight into a filter that has just been switched on: no jump from silence.
        val f = sweep(1_000f)
        val buf = FloatArray(64) { 0.5f }
        f.process(buf, 32)
        assertTrue(buf.all { kotlin.math.abs(it - 0.5f) < 0.01f })
    }

    @Test
    fun theSweepOpensTheIncomingSongByTheSwapAndClosesTheOutgoingOneAtTheEnd() {
        val open = SweepFilter.OPEN_HZ
        val (in0, out0) = MixEngine.sweepHz(0f, 0.5f)
        assertEquals(2_500f, in0, 1f)
        assertEquals(open, out0, 0f)
        val (inMid, _) = MixEngine.sweepHz(0.5f, 0.5f)
        assertEquals(open, inMid, 0f)
        val (_, outEnd) = MixEngine.sweepHz(1f, 0.5f)
        assertEquals(300f, outEnd, 1f)
        // Both move one way only.
        val steps = (0..20).map { MixEngine.sweepHz(it / 20f, 0.5f) }
        assertTrue(steps.zipWithNext().all { (a, b) -> b.first >= a.first && b.second <= a.second })
    }

    // Echo out.

    private fun echo(delayMs: Double) = EchoOut().apply { configure(rate, 2); setDelay(delayMs) }

    @Test
    fun aRestingEchoLeavesTheSoundUntouched() {
        val e = echo(500.0)
        val buf = FloatArray(2_000) { (it % 3) / 10f }
        val copy = buf.copyOf()
        e.process(buf, 1_000)
        assertTrue(e.idle)
        assertTrue(buf.contentEquals(copy))
    }

    @Test
    fun aClickSentIntoTheEchoRepeatsOnTheBeatAndDiesAway() {
        val delay = 4_800 // 100 ms at 48 kHz
        val e = echo(100.0)
        e.setSend(1f)
        // Let the send glide fully open, in silence.
        e.process(FloatArray(delay * 2), delay)
        // One click goes in; then the send closes and the song is cut, so only its echoes remain.
        e.process(floatArrayOf(1f, 1f), 1)
        e.setSend(0f)
        e.setDry(0f)
        val frames = delay * 6
        val rest = FloatArray(frames * 2)
        e.process(rest, frames)
        // The click was frame 0; this buffer starts at frame 1, so repeat k is at k * delay - 1.
        val peaks = (1..4).map { k ->
            val at = k * delay - 1
            (maxOf(0, at - 50) until minOf(frames, at + 50)).maxOf { kotlin.math.abs(rest[it * 2]) }
        }
        assertTrue(peaks[0] > 0.5f)
        assertTrue(peaks.zipWithNext().all { (a, b) -> b < a })
        // Halfway between repeats: silence (an echo, not a smear).
        assertTrue(kotlin.math.abs(rest[(delay + delay / 2) * 2]) < 1e-3f)
    }

    @Test
    fun cuttingTheDryLeavesOnlyTheEchoes() {
        val e = echo(500.0)
        e.setDry(0f)
        val buf = FloatArray(4_800 * 2) { 0.5f }
        e.process(buf, 4_800)
        // After the ~15 ms glide the song itself is gone (nothing was sent, so no echoes either).
        assertTrue(buf.takeLast(200).all { kotlin.math.abs(it) < 1e-3f })
    }

    @Test
    fun autoPicksClubForMatchedBeatsEchoForUnmatchedAndSmoothWithoutABeat() {
        assertEquals(DjBlend.PLAIN, DjBlends.resolve(null, beatsMatched = true, endingBeatKnown = true))
        assertEquals(DjBlend.CLUB, DjBlends.resolve(DjStyle.AUTO, beatsMatched = true, endingBeatKnown = true))
        assertEquals(DjBlend.ECHO, DjBlends.resolve(DjStyle.AUTO, beatsMatched = false, endingBeatKnown = true))
        assertEquals(DjBlend.SMOOTH, DjBlends.resolve(DjStyle.AUTO, beatsMatched = false, endingBeatKnown = false))
        // A chosen style is used as it is.
        assertEquals(DjBlend.ECHO, DjBlends.resolve(DjStyle.ECHO, beatsMatched = true, endingBeatKnown = true))
        assertEquals(DjBlend.SMOOTH, DjBlends.resolve(DjStyle.SMOOTH, beatsMatched = true, endingBeatKnown = true))
    }

    @Test
    fun theEchoRepeatsOnABeatOfTheEndingSong() {
        assertEquals(500.0, DjBlends.echoDelayMs(500.0), 0.0)
        // A very fast beat (180 ms): two beats per repeat.
        assertEquals(360.0, DjBlends.echoDelayMs(180.0), 0.0)
        assertEquals(DjBlends.DEFAULT_ECHO_MS, DjBlends.echoDelayMs(null), 0.0)
        // A very slow beat (55 BPM, ~1091 ms): half a beat per repeat, still on the beat.
        assertEquals(1_091.0 / 2, DjBlends.echoDelayMs(1_091.0), 0.0)
    }
}
