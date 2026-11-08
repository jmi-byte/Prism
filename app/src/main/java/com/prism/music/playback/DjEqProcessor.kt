package com.prism.music.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A DJ mixer's channel EQ for one player, with two knobs that glide so they never click, and a true pass-through
 * while both are at rest:
 * - [setBass]: 1 leaves the sound untouched, 0 cuts the low end (a low shelf around [BassKill.CORNER_HZ],
 *   [BassKill.CUT_DB] down). Mix's bass swap: the incoming song comes in with its bass cut, then the two swap on a beat.
 * - [setLowPass]: a sweepable low-pass filter ([SweepFilter]); at [SweepFilter.OPEN_HZ] or above it is off. Mix's
 *   filter sweep: the outgoing song's highs roll away as it leaves, while the incoming one opens up.
 */
@UnstableApi
class DjEqProcessor : BaseAudioProcessor() {
    private val kill = BassKill()
    private val sweep = SweepFilter()
    private var float = false
    private var channels = 2
    /** Reused between buffers: nothing is allocated on the audio thread once it's big enough. */
    private var scratch = FloatArray(0)

    /** 1: full bass, 0: bass cut. Safe from any thread. */
    fun setBass(level: Float) = kill.setTarget(level)

    val bass: Float get() = kill.target

    /** The low-pass filter's cutoff in Hz; [SweepFilter.OPEN_HZ] (or more) switches it off. Safe from any thread. */
    fun setLowPass(hz: Float) = sweep.setTarget(hz)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) return AudioProcessor.AudioFormat.NOT_SET
        float = enc == C.ENCODING_PCM_FLOAT
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        kill.configure(inputAudioFormat.sampleRate, channels)
        sweep.configure(inputAudioFormat.sampleRate, channels)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val out = replaceOutputBuffer(bytes)
        if (kill.idle && sweep.idle) {
            out.put(inputBuffer)
            out.flip()
            return
        }
        val samples = bytes / (if (float) 4 else 2)
        if (scratch.size < samples) scratch = FloatArray(samples)
        val buf = scratch
        for (i in 0 until samples) buf[i] = if (float) inputBuffer.float else inputBuffer.short / 32768f
        kill.process(buf, samples / channels)
        sweep.process(buf, samples / channels)
        for (i in 0 until samples) {
            if (float) out.putFloat(buf[i])
            else out.putShort((buf[i].coerceIn(-1f, 0.99997f) * 32767f).roundToInt().toShort())
        }
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) { kill.clearHistory(); sweep.clearHistory() }
    override fun onReset() { kill.clearHistory(); sweep.clearHistory() }
}

/**
 * The sweep's sound, apart from Media3 so it can be unit-tested: a 2nd-order low-pass biquad (RBJ cookbook) per
 * channel, with a touch of resonance ([Q]) for the familiar "whoosh" of a DJ filter. The cutoff glides on a log scale
 * (so a sweep sounds even), and the coefficients follow every [BLOCK] frames while it moves.
 */
class SweepFilter {
    @Volatile var target = OPEN_HZ
        private set
    private var hz = OPEN_HZ
    private var rate = 48_000
    private var channels = 2
    private var x1 = DoubleArray(2); private var x2 = DoubleArray(2)
    private var y1 = DoubleArray(2); private var y2 = DoubleArray(2)
    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
    private var coefFor = Float.NaN
    private var primed = false

    fun configure(sampleRate: Int, channelCount: Int) {
        rate = sampleRate.coerceAtLeast(8_000)
        channels = channelCount.coerceAtLeast(1)
        x1 = DoubleArray(channels); x2 = DoubleArray(channels); y1 = DoubleArray(channels); y2 = DoubleArray(channels)
        coefFor = Float.NaN
        primed = false
    }

    fun setTarget(v: Float) { target = v.coerceIn(MIN_HZ, OPEN_HZ) }

    /** Open and settled: nothing to do. */
    val idle: Boolean get() = target >= OPEN_HZ && hz >= OPEN_HZ

    fun clearHistory() {
        x1.fill(0.0); x2.fill(0.0); y1.fill(0.0); y2.fill(0.0)
        primed = false
    }

    fun process(buf: FloatArray, frames: Int) {
        if (idle) return
        if (!primed && frames > 0) {
            // Coming out of pass-through: start the filter as if it had always been running on this signal, so its
            // first samples don't jump from silence (a click).
            for (ch in 0 until channels) { val v = buf[ch].toDouble(); x1[ch] = v; x2[ch] = v; y1[ch] = v; y2[ch] = v }
            primed = true
        }
        val glide = 1.0 - exp(-1.0 / (GLIDE_SEC * rate))
        var f = 0
        while (f < frames) {
            val n = minOf(BLOCK, frames - f)
            // Gliding in log frequency: an octave takes as long at 200 Hz as at 10 kHz.
            val goal = kotlin.math.ln(target.toDouble())
            val now = kotlin.math.ln(hz.toDouble())
            val next = if (kotlin.math.abs(goal - now) < 1e-3) goal else now + (goal - now) * (1 - (1 - glide).pow(n))
            hz = exp(next).toFloat().let { if (target >= OPEN_HZ && it > OPEN_HZ * 0.995f) OPEN_HZ else it }
            if (hz != coefFor) coefficients(hz)
            for (k in 0 until n) {
                val base = (f + k) * channels
                for (ch in 0 until channels) {
                    val x = buf[base + ch].toDouble()
                    val y = b0 * x + b1 * x1[ch] + b2 * x2[ch] - a1 * y1[ch] - a2 * y2[ch]
                    x2[ch] = x1[ch]; x1[ch] = x
                    y2[ch] = y1[ch]; y1[ch] = y
                    buf[base + ch] = y.toFloat()
                }
            }
            f += n
        }
        if (idle) clearHistory()
    }

    private fun coefficients(cutoff: Float) {
        coefFor = cutoff
        // Never above 45% of the sample rate, where the filter would be unstable.
        val w0 = 2 * PI * minOf(cutoff.toDouble(), rate * 0.45) / rate
        val cw = cos(w0)
        val alpha = sin(w0) / (2 * Q)
        val a0 = 1 + alpha
        b0 = (1 - cw) / 2 / a0
        b1 = (1 - cw) / a0
        b2 = b0
        a1 = -2 * cw / a0
        a2 = (1 - alpha) / a0
    }

    companion object {
        /** At or above this the filter is off (the top of hearing). */
        const val OPEN_HZ = 20_000f
        const val MIN_HZ = 80f
        /** Slightly resonant (Butterworth is 0.707): the sweep is heard as a sweep, without whistling. */
        private const val Q = 0.9
        private const val GLIDE_SEC = 0.05
        private const val BLOCK = 64
    }
}

/**
 * The bass knob's sound, apart from Media3 so it can be unit-tested: a low-shelf biquad (RBJ cookbook, shelf slope 1)
 * per channel on interleaved samples, whose cut follows a gliding level. Coefficients are worked out again every
 * [BLOCK] frames while the level moves.
 */
class BassKill {
    @Volatile var target = 1f
        private set
    private var level = 1f
    private var rate = 48_000
    private var channels = 2
    private var x1 = DoubleArray(2); private var x2 = DoubleArray(2)
    private var y1 = DoubleArray(2); private var y2 = DoubleArray(2)
    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
    private var coefFor = Float.NaN

    fun configure(sampleRate: Int, channelCount: Int) {
        rate = sampleRate.coerceAtLeast(8_000)
        channels = channelCount.coerceAtLeast(1)
        x1 = DoubleArray(channels); x2 = DoubleArray(channels); y1 = DoubleArray(channels); y2 = DoubleArray(channels)
        coefFor = Float.NaN
    }

    fun setTarget(v: Float) { target = v.coerceIn(0f, 1f) }

    /** Full bass and settled: nothing to do. */
    val idle: Boolean get() = target == 1f && level == 1f

    fun clearHistory() {
        x1.fill(0.0); x2.fill(0.0); y1.fill(0.0); y2.fill(0.0)
    }

    /** Filters [frames] interleaved frames of [buf] in place. */
    fun process(buf: FloatArray, frames: Int) {
        // Full bass and settled: exactly the input (even a 0 dB biquad would round it).
        if (idle) return
        val glide = 1f - exp(-1f / (GLIDE_SEC * rate))
        var f = 0
        while (f < frames) {
            val n = minOf(BLOCK, frames - f)
            // The level moves per block; the coefficients follow it.
            val goal = target
            level = if (kotlin.math.abs(goal - level) < 1e-3f) goal else level + (goal - level) * (1f - (1f - glide).pow(n))
            if (level != coefFor) coefficients(level)
            for (k in 0 until n) {
                val base = (f + k) * channels
                for (ch in 0 until channels) {
                    val x = buf[base + ch].toDouble()
                    val y = b0 * x + b1 * x1[ch] + b2 * x2[ch] - a1 * y1[ch] - a2 * y2[ch]
                    x2[ch] = x1[ch]; x1[ch] = x
                    y2[ch] = y1[ch]; y1[ch] = y
                    buf[base + ch] = y.toFloat()
                }
            }
            f += n
        }
        if (idle) clearHistory()
    }

    private fun coefficients(level: Float) {
        coefFor = level
        val db = (1f - level) * CUT_DB
        val a = 10.0.pow(db / 40.0)
        val w0 = 2 * PI * CORNER_HZ / rate
        val cw = cos(w0)
        val alpha = sin(w0) / 2 * sqrt(2.0)
        val sa = 2 * sqrt(a) * alpha
        val a0 = (a + 1) + (a - 1) * cw + sa
        b0 = a * ((a + 1) - (a - 1) * cw + sa) / a0
        b1 = 2 * a * ((a - 1) - (a + 1) * cw) / a0
        b2 = a * ((a + 1) - (a - 1) * cw - sa) / a0
        a1 = -2 * ((a - 1) + (a + 1) * cw) / a0
        a2 = ((a + 1) + (a - 1) * cw - sa) / a0
    }

    companion object {
        /** Where the shelf sits: below it, kick drums and bass lines; above it, vocals and most of the mix. */
        const val CORNER_HZ = 180.0
        /** How far the low end goes down at "bass cut". */
        const val CUT_DB = -26.0f
        /** How long the knob takes to turn (to about two-thirds of the way). */
        const val GLIDE_SEC = 0.04f
        private const val BLOCK = 64
    }
}
