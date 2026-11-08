package com.prism.music

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.audio.AudioProcessor
import com.prism.music.data.model.Song
import com.prism.music.playback.MixPoint
import com.prism.music.playback.sing.StemStore
import com.prism.music.playback.sing.VocalStemProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Veloura's mashups with separated vocals (Sing itself off): the outgoing song's acapella, the incoming song's beat. */
class MashupStemsTest {
    private val rate = 48_000
    private val seconds = 10
    private val n = rate * seconds

    private class OneSong(private val id: String, private val durationUs: Long) : Timeline() {
        override fun getWindowCount() = 1
        override fun getPeriodCount() = 1
        override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window =
            window.set(id, MediaItem.Builder().setMediaId(id).build(), null, 0, 0, 0, true, false, null, 0, durationUs, 0, 0, 0)
        override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period = period.set(id, id, 0, durationUs, 0)
        override fun getIndexOfPeriod(uid: Any) = if (uid == id) 0 else C.INDEX_UNSET
        override fun getUidOfPeriod(periodIndex: Int): Any = id
    }

    private val rnd = Random(5)
    private val backing = FloatArray(n * 2) { i -> val t = (i / 2).toDouble() / rate; ((rnd.nextFloat() - 0.5f) * 0.12f + 0.15 * sin(2 * PI * 55 * t) + 0.08 * sin(2 * PI * 330 * t)).toFloat() }
    private val voice = FloatArray(n * 2) { i -> val t = (i / 2).toDouble() / rate; (0.25 * sin(2 * PI * (440 + 6 * sin(2 * PI * 5 * t)) * t) * (0.6 + 0.4 * sin(2 * PI * 0.3 * t))).toFloat() }
    private val music = FloatArray(n * 2) { backing[it] + voice[it] }

    private fun run(mode: Int): FloatArray {
        val root = kotlin.io.path.createTempDirectory("mash").toFile()
        val store = StemStore(root)
        store.writeMeta("song1", StemStore.Meta(rate, n.toLong(), complete = true))
        store.pcm("song1").outputStream().buffered().use { out ->
            for (i in 0 until n * 2) {
                val s = (voice[i] * 32767f).toInt().coerceIn(-32768, 32767)
                out.write(s and 0xff); out.write((s shr 8) and 0xff)
            }
        }
        val source = object : VocalStemProcessor.Source {
            override val onNow = false
            override val levelNow = 1f
            override val store = store
        }
        val p = VocalStemProcessor(source)
        p.mashMode = mode
        p.mashAmount = 1f
        p.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_FLOAT))
        p.flush(AudioProcessor.StreamMetadata.Builder().setTimeline(OneSong("song1", seconds * 1_000_000L)).setPeriodUid("song1").setPositionOffsetUs(0).build())
        val out = FloatArray(n * 2)
        var at = 0
        val block = 1024
        while (at < n) {
            val frames = minOf(block, n - at)
            val buf = ByteBuffer.allocateDirect(frames * 8).order(ByteOrder.nativeOrder())
            for (i in 0 until frames * 2) buf.putFloat(music[at * 2 + i])
            buf.flip()
            p.queueInput(buf)
            p.output.order(ByteOrder.nativeOrder()).asFloatBuffer().get(out, at * 2, frames * 2)
            at += frames
            if (at >= rate * 4 && at < rate * 4 + block) {
                val until = System.currentTimeMillis() + 5_000
                while (p.alignment.first == null && System.currentTimeMillis() < until) Thread.sleep(50)
            }
        }
        p.release()
        root.deleteRecursively()
        return out
    }

    private fun rms(a: FloatArray, from: Int, to: Int) = sqrt((from * 2 until to * 2).sumOf { (a[it] * a[it]).toDouble() } / ((to - from) * 2))

    @Test fun soloIsTheAcapella() {
        val out = run(VocalStemProcessor.MASH_SOLO)
        val from = rate * 1; val to = n - rate / 10
        // What's left besides the voice is the backing, gone (16-bit vocals: rounding only).
        val diff = FloatArray(n * 2) { out[it] - voice[it] }
        assertTrue("backing left ${rms(diff, from, to)}", rms(diff, from, to) / rms(backing, from, to) < 0.01)
    }

    @Test fun cutLeavesTheBeat() {
        val out = run(VocalStemProcessor.MASH_CUT)
        val from = rate * 7; val to = n - rate / 10
        val diff = FloatArray(n * 2) { out[it] - backing[it] }
        assertTrue("voice left ${rms(diff, from, to)}", rms(diff, from, to) / rms(voice, from, to) < 0.01)
    }

    @Test fun primeChangesNothing() {
        val out = run(VocalStemProcessor.MASH_PRIME)
        val diff = FloatArray(n * 2) { out[it] - music[it] }
        assertTrue(rms(diff, 0, n) < 1e-6)
    }

    private fun song(id: String) = Song(id = id, title = id, artists = emptyList())

    @Test fun savedMixKeepsEachNeighboursLastTransition() {
        val songs = listOf(song("a"), song("b"), song("c"), song("d"))
        val history = listOf(
            MixPoint("a", "b", 100_000, 20_000, 8_000, "CLUB"),
            MixPoint("x", "y", 1, 1, 1_000, "PLAIN"),
            MixPoint("b", "c", 90_000, 0, 12_000, "MASHUP"),
            MixPoint("a", "b", 110_000, 22_000, 9_000, "ECHO"),
        )
        val points = SavedMixes.pointsFor(songs, history)
        assertEquals(listOf("ECHO", "MASHUP"), points.map { it.dj })
        assertEquals(110_000L, points[0].startMs)
    }
}
