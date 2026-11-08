package com.prism.music

import com.prism.music.playback.DjFlow
import com.prism.music.playback.DjSequencer
import com.prism.music.playback.DjTrack
import com.prism.music.playback.MusicKey
import kotlin.math.abs
import kotlin.math.ln

/** "Why this song?": a suggestion's reasons in plain words, from what was measured and where it came from. */
object SimilarReasons {
    fun why(
        seedTitle: String,
        language: SongLanguage,
        seed: SoundFeatures,
        other: SoundFeatures,
        related: Boolean,
    ): String {
        val tempo = if (seed.bpm != null && other.bpm != null && seed.bpm > 0 && other.bpm > 0) {
            val b = listOf(other.bpm, other.bpm * 2, other.bpm / 2).minBy { abs(ln(it / seed.bpm)) }
            val apart = abs(b / seed.bpm - 1)
            when {
                apart <= 0.03 -> "same tempo"
                apart <= 0.08 -> "close tempo"
                else -> null
            }
        } else null
        val key = if (seed.key != null && other.key != null) {
            val clash = MusicKey.clash(seed.key, other.key)
            when {
                clash == 0.0 -> "same key"
                clash <= 0.25 -> "a key that blends"
                else -> null
            }
        } else null
        val traits = listOfNotNull(key, tempo)
        val sound = traits.takeIf { it.isNotEmpty() }?.let { t ->
            t.joinToString(", ").replaceFirstChar(Char::uppercase) + if (t.all { it.startsWith("same") }) " as $seedTitle" else " to $seedTitle"
        }
        return listOfNotNull(
            sound,
            language.takeIf { it != SongLanguage.UNKNOWN && it != SongLanguage.OTHER }?.label,
            if (related) "Related on YouTube Music" else "On $seedTitle's radio",
        ).joinToString(" · ")
    }
}

/** The listener's balance of Punjabi & Hindi against English for "Keep the mix going". */
object LanguageMix {
    /**
     * Which group the next songs should come from, to steer [recent] (the queue's last songs' groups) towards
     * [desiPercent] Punjabi & Hindi (the rest English). Songs of other or unknown languages don't count either way.
     */
    fun nextGroup(recent: List<LanguageGroup>, desiPercent: Int): LanguageGroup {
        val counted = recent.filter { it == LanguageGroup.DESI || it == LanguageGroup.ENGLISH }
        if (counted.isEmpty()) return if (desiPercent >= 50) LanguageGroup.DESI else LanguageGroup.ENGLISH
        val desi = counted.count { it == LanguageGroup.DESI } * 100 / counted.size
        return if (desi < desiPercent) LanguageGroup.DESI else LanguageGroup.ENGLISH
    }

    fun describe(desiPercent: Int): String = when {
        desiPercent < 0 -> "Follows each song's language"
        desiPercent >= 100 -> "Punjabi & Hindi only"
        desiPercent <= 0 -> "English only"
        else -> "$desiPercent% Punjabi & Hindi · ${100 - desiPercent}% English"
    }
}

/** The shape of a set's energy, for "Arrange as a set". */
enum class SetShape(val label: String, val detail: String) {
    BUILD("Build up", "Starts gentle and ends at full energy. For a drive, a workout or a party getting going."),
    PEAK("Warm up → Peak → Cool down", "Rises to the most intense songs in the middle, then eases off. A whole night in one queue."),
    WIND_DOWN("Wind down", "Starts lively and settles. For the end of a night or the way home."),
    SMOOTH("Smooth flow", "Even energy, with each song in a key that blends into the next."),
}

/**
 * Orders the songs ahead into a set of the chosen [SetShape], by their measured energy (loudness and tempo) and keys
 * (Camelot), with Prism's DJ sequencer: no one artist twice running, keys that blend wherever the shape allows.
 * Pure, so it's unit-tested.
 */
object EnergySets {
    fun arrange(start: DjTrack, tracks: List<DjTrack>, shape: SetShape): List<DjTrack> {
        if (tracks.size < 2) return tracks
        return when (shape) {
            SetShape.BUILD -> DjSequencer.order(start, tracks, DjFlow.BUILD)
            SetShape.WIND_DOWN -> DjSequencer.order(start, tracks, DjFlow.WIND_DOWN)
            SetShape.SMOOTH -> DjSequencer.order(start, tracks, DjFlow.STEADY)
            SetShape.PEAK -> {
                // A mountain: by energy, alternately up the near side and down the far one, so the most intense songs
                // meet in the middle; each side is then smoothed by key (building, then winding down).
                val energy = DjSequencer.energies(listOf(start) + tracks)
                val ranked = tracks.indices.sortedBy { energy[it + 1] }.map { tracks[it] }
                val rising = ranked.filterIndexed { i, _ -> i % 2 == 0 }
                val falling = ranked.filterIndexed { i, _ -> i % 2 == 1 }
                val up = DjSequencer.order(start, rising, DjFlow.BUILD)
                val down = DjSequencer.order(up.lastOrNull() ?: start, falling, DjFlow.WIND_DOWN)
                up + down
            }
        }
    }
}
