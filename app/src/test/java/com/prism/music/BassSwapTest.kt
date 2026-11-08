package com.prism.music

import com.prism.music.playback.BassKill
import com.prism.music.playback.MixEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class BassSwapTest {
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
}
