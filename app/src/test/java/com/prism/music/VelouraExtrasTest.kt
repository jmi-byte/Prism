package com.prism.music

import com.prism.music.data.prefs.DjStyle
import com.prism.music.playback.DjBlend
import com.prism.music.playback.DjBlends
import com.prism.music.playback.MidCut
import com.prism.music.playback.MixEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class VelouraExtrasTest {
    @Test fun tempoLockStretchesWithinLimits() {
        assertEquals(1.05f, TempoLock.speedFor(160.0, 168)!!, 1e-4f)
        // Octave-free: half-time songs run at double.
        assertEquals(84f / 82f, TempoLock.speedFor(82.0, 168)!!, 1e-4f)
        assertNull(TempoLock.speedFor(120.0, 168))
        assertNull(TempoLock.speedFor(null, 168))
        assertNull(TempoLock.speedFor(128.0, 0))
    }

    @Test fun moodFollowsTheMoment() {
        assertEquals(SetShape.BUILD, ContextMood.of(ListeningContext(8, inCar = true, headphones = false)).shape)
        assertEquals(TransitionStyle.CROSSFADE, ContextMood.of(ListeningContext(7, false, false)).style)
        assertEquals("Daytime focus", ContextMood.of(ListeningContext(13, false, true)).label)
        assertEquals("Daytime flow", ContextMood.of(ListeningContext(13, false, false)).label)
        assertEquals(TransitionStyle.DJ_CLUB, ContextMood.of(ListeningContext(19, false, false)).style)
        assertEquals(SetShape.WIND_DOWN, ContextMood.of(ListeningContext(2, false, false)).shape)
        assertEquals(SetShape.WIND_DOWN, ContextMood.of(ListeningContext(23, false, false)).shape)
    }

    private fun rms(cut: MidCut, hz: Double): Double {
        val rate = 48_000
        val buf = FloatArray(rate * 2) { i -> (0.5 * sin(2 * PI * hz * (i / 2) / rate)).toFloat() }
        cut.process(buf, rate)
        var s = 0.0
        for (i in rate until rate * 2) s += buf[i] * buf[i].toDouble()
        return 20 * kotlin.math.log10(sqrt(s / rate) / (0.5 / sqrt(2.0)))
    }

    private fun cut(level: Float) = MidCut().apply { configure(48_000, 2); setTarget(level) }

    @Test fun midCutTakesOutTheVoiceAndKeepsTheBeat() {
        assertTrue(rms(cut(1f), 1_200.0) < -10)
        assertTrue(rms(cut(1f), 60.0) > -1.5)
        val idle = cut(0f)
        val buf = FloatArray(200) { it / 200f }
        val copy = buf.copyOf()
        idle.process(buf, 100)
        assertTrue(buf.contentEquals(copy))
    }

    @Test fun mashupNeedsLockedBeats() {
        assertEquals(DjBlend.MASHUP, DjBlends.resolve(DjStyle.MASHUP, beatsMatched = true, endingBeatKnown = true))
        assertEquals(DjBlend.CLUB, DjBlends.resolve(DjStyle.MASHUP, beatsMatched = false, endingBeatKnown = true))
    }

    @Test fun mashupKeepsBothSongsUp() {
        val (in0, out0) = MixEngine.mashupGains(0f)
        assertEquals(0f, in0, 1e-4f)
        assertEquals(0.9f, out0, 1e-4f)
        val (inMid, outMid) = MixEngine.mashupGains(0.5f)
        assertEquals(1f, inMid, 1e-4f)
        assertEquals(0.9f, outMid, 1e-4f)
        assertEquals(0f, MixEngine.mashupGains(1f).second, 1e-4f)
    }
}
