package com.prism.music

import android.content.Context
import com.prism.music.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.abs
import kotlin.math.ln

// ---- Tempo lock ----

/** Tempo lock: every song played at one tempo (a running or workout pace), as far as a song can stretch. */
object TempoLock {
    /** How far a song's speed may move (±12%); beyond that it plays at its own tempo rather than sound wrong. */
    const val MAX_STRETCH = 0.12

    /**
     * The speed that brings a song at [songBpm] to [targetBpm] (octave-free: a 82 BPM song runs at 164 just as well),
     * or null when its tempo isn't known or it would have to stretch too far.
     */
    fun speedFor(songBpm: Double?, targetBpm: Int): Float? {
        if (songBpm == null || songBpm <= 0 || targetBpm <= 0) return null
        val b = listOf(songBpm, songBpm * 2, songBpm / 2).minBy { abs(ln(targetBpm / it)) }
        val speed = targetBpm / b
        return speed.toFloat().takeIf { abs(speed - 1) <= MAX_STRETCH }
    }

    fun describe(bpm: Int): String = when {
        bpm <= 0 -> "Off"
        bpm < 110 -> "$bpm BPM · walking"
        bpm < 140 -> "$bpm BPM · brisk walk or cycling"
        bpm < 165 -> "$bpm BPM · jogging"
        else -> "$bpm BPM · running"
    }
}

// ---- Context mood ----

/** What's going on around the listener, as far as the phone knows. */
data class ListeningContext(val hour: Int, val inCar: Boolean, val headphones: Boolean)

/** A mood for the moment: how to shape the queue and blend its songs. */
data class Mood(val label: String, val detail: String, val shape: SetShape, val style: TransitionStyle)

/** The mood for the moment from the time of day, the car and headphones. Pure, so it's unit-tested. */
object ContextMood {
    fun of(ctx: ListeningContext): Mood = when {
        ctx.inCar -> Mood("On the road", "Driving: an energy that builds, Club blends.", SetShape.BUILD, TransitionStyle.DJ_CLUB)
        ctx.hour in 5..10 -> Mood("Morning calm", "Easy energy and gentle crossfades to start the day.", SetShape.SMOOTH, TransitionStyle.CROSSFADE)
        ctx.hour in 11..16 -> Mood(
            if (ctx.headphones) "Daytime focus" else "Daytime flow", "Steady energy, smooth DJ blends.", SetShape.SMOOTH, TransitionStyle.DJ_SMOOTH,
        )
        ctx.hour in 17..21 -> Mood("Evening build", "Energy rising into the night, Club blends.", SetShape.BUILD, TransitionStyle.DJ_CLUB)
        else -> Mood("Late night", "Winding down, soft blends.", SetShape.WIND_DOWN, TransitionStyle.DJ_SMOOTH)
    }

    /**
     * Whether Android Auto is connected (projecting or native), asked as the car app library does: its connection
     * provider's state column (0 not connected). False when there's no Android Auto.
     */
    fun inCar(context: Context): Boolean = runCatching {
        context.contentResolver.query(
            android.net.Uri.parse("content://androidx.car.app.connection"), arrayOf("CarConnectionState"), null, null, null,
        )?.use { c -> c.moveToFirst() && c.getInt(0) != 0 } ?: false
    }.getOrDefault(false)

    /** Headphones (wired, USB, Bluetooth) are the output. */
    fun headphones(context: Context): Boolean = runCatching {
        val am = context.getSystemService(android.media.AudioManager::class.java)
        am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type in setOf(
                android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES, android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
            ) || (android.os.Build.VERSION.SDK_INT >= 31 && it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }.getOrDefault(false)

    fun now(context: Context) = ListeningContext(
        java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY), inCar(context), headphones(context),
    )
}

// ---- Saved mixes ----

@Serializable
data class SavedMix(
    val id: String,
    val name: String,
    val savedAt: Long,
    /** The transition style it was mixed with ([TransitionStyle] name). */
    val style: String,
    val songs: List<Song>,
    /** Each transition as it was mixed (the ones played before saving), replayed the same way. */
    val points: List<com.prism.music.playback.MixPoint> = emptyList(),
)

/**
 * "Save the mix": a session's songs in the order they played, and the transition style they were mixed with, kept on
 * the phone (filesDir/veloura_mixes.json). Played again, AutoMix mixes them anew; with its analysis kept, mostly at the
 * same points.
 */
class SavedMixes(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file get() = File(context.filesDir, "veloura_mixes.json")
    private val _mixes = MutableStateFlow(load())
    val mixes: StateFlow<List<SavedMix>> = _mixes

    private fun load(): List<SavedMix> = runCatching {
        file.takeIf { it.isFile }?.readText()?.let { json.decodeFromString<List<SavedMix>>(it) }
    }.getOrNull().orEmpty()

    @Synchronized
    private fun write(list: List<SavedMix>) {
        _mixes.value = list
        runCatching {
            val tmp = File(context.filesDir, "veloura_mixes.json.tmp")
            tmp.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SavedMix.serializer()), list))
            tmp.renameTo(file)
        }
    }

    fun save(name: String, songs: List<Song>, style: TransitionStyle, history: List<com.prism.music.playback.MixPoint> = emptyList()): SavedMix {
        val mix = SavedMix(java.util.UUID.randomUUID().toString(), name.ifBlank { "My mix" }, System.currentTimeMillis(), style.name, songs, pointsFor(songs, history))
        write(listOf(mix) + _mixes.value)
        return mix
    }

    fun delete(id: String) = write(_mixes.value.filter { it.id != id })

    /** The saved mix playing now: its transitions are replayed as they were. */
    @Volatile private var pins: Map<Pair<String, String>, com.prism.music.playback.MixPoint> = emptyMap()

    /** Plays [mix]'s transitions as saved from now on (the queue itself is the caller's). */
    fun replay(mix: SavedMix) { pins = mix.points.associateBy { it.fromId to it.toId } }

    /** The saved transition from [fromId] into [toId] in the mix being replayed, if it has one. */
    fun pin(fromId: String, toId: String): com.prism.music.playback.MixPoint? = pins[fromId to toId]

    companion object {
        /** For each pair of neighbours in [songs], the last time that pair was mixed in [history]. Pure, so it's tested. */
        fun pointsFor(songs: List<Song>, history: List<com.prism.music.playback.MixPoint>): List<com.prism.music.playback.MixPoint> =
            songs.zipWithNext().mapNotNull { (a, b) -> history.lastOrNull { it.fromId == a.id && it.toId == b.id } }
    }
}
