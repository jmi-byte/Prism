package com.prism.music.playback

import com.prism.music.data.lyrics.LyricLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.ceil

/** Why Intelligent AutoMix chose a transition window. */
enum class AutoMixReason { HIGHLIGHT_HEARD, NATURAL_WINDOW }

/**
 * The musical landmark used to place a transition. [ESTIMATED] is a best guess (a lyric line start, a loosely measured
 * beat, or just the time after the highlight) used when the listener wants every pair mixed mid-song: MixEngine gives
 * it a longer blend so the seam is covered, and the UI never presents it as phrase- or beat-aligned.
 */
enum class AutoMixBoundary { PHRASE, BEAT, ESTIMATED }

/** A proposed transition. Playback does not act on this until the integration stage. */
data class AutoMixPlan(
    val transitionAtMs: Long,
    val reason: AutoMixReason,
    val boundary: AutoMixBoundary,
    /** Where the incoming song starts. Zero means its natural opening. */
    val entryAtMs: Long = 0,
    /** The incoming cue is a best guess rather than a phrase restart or measured beat. */
    val entryEstimated: Boolean = false,
) {
    /** Either side of this transition is a best guess, so it needs the longer covering blend. */
    val estimated: Boolean get() = boundary == AutoMixBoundary.ESTIMATED || entryEstimated
}

/**
 * Picks a conservative place for an early DJ transition.
 *
 * A song's main highlight is allowed to play before a transition. The proposal then lands on a known phrase boundary
 * (from timed lyrics or later structure analysis), or a bar when the beat grid is clear. When neither exists, the
 * planner returns null and normal playback continues rather than forcing an awkward cut.
 */
object IntelligentAutoMixPlanner {
    const val EARLIEST_MS = 45_000L
    const val IDEAL_MS = 90_000L
    const val PREFERRED_LATEST_MS = 150_000L
    const val ABSOLUTE_LATEST_MS = 180_000L
    const val HIGHLIGHT_PLAY_MS = 28_000L
    const val KEEP_TAIL_MS = 20_000L
    const val MIN_DURATION_MS = 120_000L
    private const val PHRASE_WINDOW_MS = 14_000L
    const val MIN_BEAT_CONFIDENCE = 4.0
    // The tempo scanner analyses the whole track and supplies anchors at both ends. A two-minute reach is enough for
    // the middle of ordinary songs while still rejecting a beat grid whose nearest measured anchor is extreme.
    private const val MAX_BEAT_EXTRAPOLATION_MS = 120_000L
    const val ENTRY_LEAD_MS = 20_000L
    const val EARLIEST_ENTRY_MS = 8_000L
    const val LATEST_ENTRY_MS = 90_000L
    const val ENTRY_KEEP_MS = 60_000L
    private const val ENTRY_WINDOW_MS = 14_000L

    // Best-guess tier, only with `estimate = true`.
    /** Songs shorter than this still play through: there is no real middle to leave from. */
    const val ESTIMATE_MIN_DURATION_MS = 90_000L
    /** A lyric line start this close to the target is a better seam than an arbitrary moment. */
    private const val LINE_SNAP_MS = 8_000L
    /** Without a known highlight, the incoming song is entered about here (past a typical intro). */
    const val ESTIMATE_ENTRY_MS = 30_000L

    fun plan(
        durationMs: Long,
        bestPartStartMs: Long? = null,
        phraseBoundariesMs: List<Long> = emptyList(),
        tempo: Tempo? = null,
        nextReady: Boolean = true,
        nextDurationMs: Long = 0,
        nextBestPartStartMs: Long? = null,
        nextPhraseBoundariesMs: List<Long> = emptyList(),
        nextTempo: Tempo? = null,
        /** Allow the best-guess tier: every analysed pair gets a mid-song exit and, where possible, a mid-song entry. */
        estimate: Boolean = false,
        /** Start times of every timed lyric line of the outgoing song, for the best-guess tier. */
        lineStartsMs: List<Long> = emptyList(),
        nextLineStartsMs: List<Long> = emptyList(),
    ): AutoMixPlan? {
        if (!nextReady) return null
        val strict = if (durationMs < MIN_DURATION_MS) null else strictPlan(
            durationMs, bestPartStartMs, phraseBoundariesMs, tempo,
            nextDurationMs, nextBestPartStartMs, nextPhraseBoundariesMs, nextTempo,
        )
        if (!estimate) return strict
        if (strict != null) {
            if (strict.entryAtMs > 0) return strict
            val guess = estimatedEntry(nextDurationMs, nextBestPartStartMs, nextLineStartsMs, nextTempo)
            return if (guess > 0) strict.copy(entryAtMs = guess, entryEstimated = true) else strict
        }
        return estimatedPlan(
            durationMs, bestPartStartMs, tempo, lineStartsMs,
            nextDurationMs, nextBestPartStartMs, nextPhraseBoundariesMs, nextTempo, nextLineStartsMs,
        )
    }

    private fun strictPlan(
        durationMs: Long,
        bestPartStartMs: Long?,
        phraseBoundariesMs: List<Long>,
        tempo: Tempo?,
        nextDurationMs: Long,
        nextBestPartStartMs: Long?,
        nextPhraseBoundariesMs: List<Long>,
        nextTempo: Tempo?,
    ): AutoMixPlan? {

        val highlightEnd = bestPartStartMs?.takeIf { it in 0 until durationMs }?.plus(HIGHLIGHT_PLAY_MS)
        val target = maxOf(EARLIEST_MS, highlightEnd ?: IDEAL_MS)
        // 150 s is preferred, not a hard cut-off: a late highlight may finish after it, up to a conservative ceiling.
        val latest = minOf(durationMs - KEEP_TAIL_MS, maxOf(PREFERRED_LATEST_MS, target + PHRASE_WINDOW_MS), ABSOLUTE_LATEST_MS)
        if (target > latest) return null

        val earliest = maxOf(EARLIEST_MS, highlightEnd ?: EARLIEST_MS)
        val phrases = usablePhraseBoundaries(durationMs, bestPartStartMs, phraseBoundariesMs)
        if (phrases.isNotEmpty()) {
            val at = phrases.minWith(compareBy<Long> { abs(it - target) }.thenBy { if (it < target) 1 else 0 }.thenBy { it })
            return AutoMixPlan(
                at,
                if (highlightEnd != null) AutoMixReason.HIGHLIGHT_HEARD else AutoMixReason.NATURAL_WINDOW,
                AutoMixBoundary.PHRASE,
                entryPoint(nextDurationMs, nextBestPartStartMs, nextPhraseBoundariesMs, nextTempo),
            )
        }

        // Tempo only gives beat anchors, not downbeats. Use a single beat from the nearest measured anchor and decline
        // when reaching the target would require a long extrapolation that can drift audibly.
        val t = tempo?.takeIf(::hasUsableBeat) ?: return null
        val anchor = listOf(t.firstBeatMs, t.outroBeatMs).minBy { abs(it - target) }
        if (abs(anchor - target) > MAX_BEAT_EXTRAPOLATION_MS) return null
        val beats = ceil((target - anchor) / t.periodMs)
        val at = (anchor + beats * t.periodMs).toLong()
        if (at !in earliest..latest) return null
        return AutoMixPlan(
            at,
            if (highlightEnd != null) AutoMixReason.HIGHLIGHT_HEARD else AutoMixReason.NATURAL_WINDOW,
            AutoMixBoundary.BEAT,
            entryPoint(nextDurationMs, nextBestPartStartMs, nextPhraseBoundariesMs, nextTempo),
        )
    }

    /**
     * A conservative incoming cue: lead into the next song's known highlight from a nearby phrase restart, or a
     * measured beat when no phrase is available. Unknown structure returns zero so an arbitrary verse is never cut.
     */
    fun entryPoint(
        durationMs: Long,
        bestPartStartMs: Long?,
        phraseBoundariesMs: List<Long> = emptyList(),
        tempo: Tempo? = null,
    ): Long {
        val best = bestPartStartMs?.takeIf { it in EARLIEST_ENTRY_MS until durationMs }
        val latest = minOf(
            LATEST_ENTRY_MS,
            durationMs - ENTRY_KEEP_MS,
            best?.minus(2_000L) ?: FALLBACK_LATEST_ENTRY_MS,
        )
        if (latest < EARLIEST_ENTRY_MS) return 0
        // A known highlight gets a lead-in. Without one, prefer a conservative phrase restart around twenty seconds;
        // this lets AutoMix skip a long opening instead of silently reverting every such song to 0:00.
        val target = (best?.minus(ENTRY_LEAD_MS) ?: FALLBACK_ENTRY_MS).coerceIn(EARLIEST_ENTRY_MS, latest)
        phraseBoundariesMs.filter { it in EARLIEST_ENTRY_MS..latest && abs(it - target) <= ENTRY_WINDOW_MS }
            .distinct().minWithOrNull(compareBy<Long> { abs(it - target) }.thenBy { it })?.let { return it }

        // Without a known highlight, a beat near twenty seconds is not necessarily a bar or phrase start. Preserve the
        // opening unless a real phrase restart is known instead of cutting into an arbitrary verse.
        if (best == null) return 0
        val t = tempo?.takeIf(::hasUsableBeat) ?: return 0
        val anchor = listOf(t.firstBeatMs, t.outroBeatMs).minBy { abs(it - target) }
        if (abs(anchor - target) > MAX_BEAT_EXTRAPOLATION_MS) return 0
        val beats = Math.round((target - anchor) / t.periodMs)
        return (anchor + beats * t.periodMs).toLong().takeIf { it in EARLIEST_ENTRY_MS..latest } ?: 0
    }

    /**
     * The best-guess exit: after the highlight (or around [IDEAL_MS]), moved to the nearest lyric line start, else onto
     * a loosely measured beat, else taken as is. Null only for a song too short to have a middle.
     */
    private fun estimatedPlan(
        durationMs: Long,
        bestPartStartMs: Long?,
        tempo: Tempo?,
        lineStartsMs: List<Long>,
        nextDurationMs: Long,
        nextBestPartStartMs: Long?,
        nextPhraseBoundariesMs: List<Long>,
        nextTempo: Tempo?,
        nextLineStartsMs: List<Long>,
    ): AutoMixPlan? {
        if (durationMs < ESTIMATE_MIN_DURATION_MS) return null
        val highlightEnd = bestPartStartMs?.takeIf { it in 0 until durationMs }?.plus(HIGHLIGHT_PLAY_MS)
        val earliest = maxOf(EARLIEST_MS, highlightEnd ?: EARLIEST_MS)
        val latest = minOf(durationMs - KEEP_TAIL_MS, ABSOLUTE_LATEST_MS)
        // A highlight so late that it runs into the ending: leave as late as the kept tail allows instead of not at all.
        val lo = if (earliest <= latest) earliest else EARLIEST_MS
        if (lo > latest) return null
        val target = (highlightEnd ?: IDEAL_MS).coerceIn(lo, latest)
        val at = snapToLine(target, lineStartsMs, lo, latest)
            ?: tempo?.let { looseBeat(target, it, lo, latest) }
            ?: target
        var entry = entryPoint(nextDurationMs, nextBestPartStartMs, nextPhraseBoundariesMs, nextTempo)
        var entryGuess = false
        if (entry == 0L) {
            entry = estimatedEntry(nextDurationMs, nextBestPartStartMs, nextLineStartsMs, nextTempo)
            entryGuess = entry > 0
        }
        return AutoMixPlan(
            at,
            if (highlightEnd != null) AutoMixReason.HIGHLIGHT_HEARD else AutoMixReason.NATURAL_WINDOW,
            AutoMixBoundary.ESTIMATED,
            entry,
            entryGuess,
        )
    }

    /**
     * The best-guess incoming cue, used only when [entryPoint] found no phrase or beat: the incoming song's highlight
     * itself (BestParts already pulled it back to a lyric line or bar), else a lyric line start near [ESTIMATE_ENTRY_MS],
     * else a loosely measured beat there, else that time as is. Zero when the song is too short to enter late.
     */
    fun estimatedEntry(durationMs: Long, bestPartStartMs: Long?, lineStartsMs: List<Long> = emptyList(), tempo: Tempo? = null): Long {
        val latest = minOf(LATEST_ENTRY_MS, durationMs - ENTRY_KEEP_MS)
        if (latest < EARLIEST_ENTRY_MS) return 0
        bestPartStartMs?.takeIf { it in EARLIEST_ENTRY_MS..latest }?.let { return it }
        val target = ESTIMATE_ENTRY_MS.coerceIn(EARLIEST_ENTRY_MS, latest)
        return snapToLine(target, lineStartsMs, EARLIEST_ENTRY_MS, latest)
            ?: tempo?.let { looseBeat(target, it, EARLIEST_ENTRY_MS, latest) }
            ?: target
    }

    private fun snapToLine(target: Long, lineStartsMs: List<Long>, lo: Long, hi: Long): Long? =
        lineStartsMs.filter { it in lo..hi && abs(it - target) <= LINE_SNAP_MS }
            .minWithOrNull(compareBy<Long> { abs(it - target) }.thenBy { it })

    /** A beat from the nearest anchor, accepted at Mix's lower "steady" confidence and any extrapolation distance. */
    private fun looseBeat(target: Long, t: Tempo, lo: Long, hi: Long): Long? {
        if (!t.steady || t.bpm <= 0 || t.firstBeatMs < 0 || t.outroBeatMs < 0) return null
        val anchor = listOf(t.firstBeatMs, t.outroBeatMs).minBy { abs(it - target) }
        val at = (anchor + Math.round((target - anchor) / t.periodMs) * t.periodMs).toLong()
        return at.takeIf { it in lo..hi }
    }

    private const val FALLBACK_ENTRY_MS = 20_000L
    private const val FALLBACK_LATEST_ENTRY_MS = 45_000L

    /** Phrase boundaries that could actually be selected for this song, used by both planning and diagnostics. */
    fun usablePhraseBoundaries(durationMs: Long, bestPartStartMs: Long?, phraseBoundariesMs: List<Long>): List<Long> {
        val highlightEnd = bestPartStartMs?.takeIf { it in 0 until durationMs }?.plus(HIGHLIGHT_PLAY_MS)
        val target = maxOf(EARLIEST_MS, highlightEnd ?: IDEAL_MS)
        val latest = minOf(durationMs - KEEP_TAIL_MS, maxOf(PREFERRED_LATEST_MS, target + PHRASE_WINDOW_MS), ABSOLUTE_LATEST_MS)
        if (target > latest) return emptyList()
        val earliest = maxOf(EARLIEST_MS, highlightEnd ?: EARLIEST_MS)
        return phraseBoundariesMs.filter { it in earliest..latest && abs(it - target) <= PHRASE_WINDOW_MS }.distinct()
    }

    fun hasUsableBeat(tempo: Tempo): Boolean =
        tempo.confidence >= MIN_BEAT_CONFIDENCE && tempo.bpm > 0 && tempo.firstBeatMs >= 0 && tempo.outroBeatMs >= 0
}

/** The latest read-only decision made from a real playing song. */
data class AutoMixShadowDecision(
    val songId: String,
    val nextSongId: String?,
    val plan: AutoMixPlan?,
    val bestPartStartMs: Long?,
    val phraseBoundaryCount: Int,
    val hasUsableBeat: Boolean,
)

/** The final fail-closed gate between a shadow decision and playback. */
object IntelligentAutoMixActivation {
    fun transitionAt(
        enabled: Boolean,
        decision: AutoMixShadowDecision?,
        fromSongId: String,
        toSongId: String,
    ): Long? = decision?.plan?.transitionAtMs?.takeIf {
        enabled && decision.songId == fromSongId && decision.nextSongId == toSongId && fromSongId != toSongId
    }

    fun entryAt(enabled: Boolean, decision: AutoMixShadowDecision?, fromSongId: String, toSongId: String): Long? =
        decision?.plan?.entryAtMs?.takeIf {
            it > 0 && enabled && decision.songId == fromSongId && decision.nextSongId == toSongId && fromSongId != toSongId
        }
}

/**
 * Feeds real Prism signals into [IntelligentAutoMixPlanner] without controlling the player.
 *
 * This is intentionally state only: the standalone build's playback integration observes and guards these decisions.
 */
class IntelligentAutoMixShadow {
    private val _decision = MutableStateFlow<AutoMixShadowDecision?>(null)
    val decision: StateFlow<AutoMixShadowDecision?> = _decision

    fun evaluate(
        songId: String,
        durationMs: Long,
        nextSongId: String?,
        bestPartStartMs: Long?,
        lyrics: List<LyricLine>,
        tempo: Tempo?,
        nextDurationMs: Long = 0,
        nextBestPartStartMs: Long? = null,
        nextLyrics: List<LyricLine> = emptyList(),
        nextTempo: Tempo? = null,
        /** Every analysed pair gets a mid-song transition, best-guess when no phrase or beat is reliable. */
        estimate: Boolean = false,
    ): AutoMixShadowDecision {
        val boundaries = phraseBoundaries(lyrics, durationMs)
        val nextBoundaries = phraseBoundaries(nextLyrics, nextDurationMs)
        val usableBoundaries = IntelligentAutoMixPlanner.usablePhraseBoundaries(durationMs, bestPartStartMs, boundaries)
        val nextReady = nextSongId != null && nextSongId != songId
        return AutoMixShadowDecision(
            songId = songId,
            nextSongId = nextSongId,
            plan = IntelligentAutoMixPlanner.plan(
                durationMs, bestPartStartMs, boundaries, tempo, nextReady,
                nextDurationMs, nextBestPartStartMs, nextBoundaries, nextTempo,
                estimate, lineStarts(lyrics, durationMs), lineStarts(nextLyrics, nextDurationMs),
            ),
            bestPartStartMs = bestPartStartMs,
            phraseBoundaryCount = usableBoundaries.size,
            hasUsableBeat = tempo?.let(IntelligentAutoMixPlanner::hasUsableBeat) == true,
        ).also { _decision.value = it }
    }

    fun clear() { _decision.value = null }

    companion object {
        private const val MIN_MEASURED_GAP_MS = 6_000L
        /** With plain LRC only line starts are known; a long interval is conservative evidence of a phrase restart. */
        private const val MIN_UNMEASURED_LINE_INTERVAL_MS = 10_000L

        /** Where sung lines begin: weaker evidence than [phraseBoundaries], used only by the best-guess tier. */
        fun lineStarts(lines: List<LyricLine>, durationMs: Long): List<Long> =
            lines.filter { it.timeMs in 0..durationMs && !it.isGap }.map { it.timeMs }.distinct().sorted()

        /**
         * Safe outgoing-song boundaries from timed lyrics: the edges of explicit instrumental gaps, the measured end
         * of a line followed by a long silence, or a phrase restart after an unusually long plain-LRC line interval.
         * Ordinary line changes and synthetic `endMs == nextStart` values are not mistaken for measured vocal ends.
         */
        fun phraseBoundaries(lines: List<LyricLine>, durationMs: Long): List<Long> {
            val timed = lines.filter { it.timeMs >= 0 }.sortedBy { it.timeMs }
            if (timed.isEmpty()) return emptyList()
            val out = mutableSetOf<Long>()
            for ((i, line) in timed.withIndex()) {
                val nextStart = timed.getOrNull(i + 1)?.timeMs
            // Enhanced LRC measures the final word even though its line end is inferred from the next line.
            val measuredWordEnd = line.words.lastOrNull()?.endMs?.takeIf { it > line.timeMs }
            val end = measuredWordEnd ?: line.endMs.takeIf { it > line.timeMs } ?: nextStart ?: durationMs
                if (line.isGap) {
                    out += line.timeMs
                    if (end > line.timeMs) out += end
                } else if (nextStart != null) {
                    when {
                        // TTML and word-timed sources can give a real vocal end before the next line starts.
                        end < nextStart && nextStart - end >= MIN_MEASURED_GAP_MS -> out += end
                        // Plain LRC synthesizes endMs from the next line. A long start-to-start interval still gives a
                        // reliable phrase restart, without pretending to know where the previous vocal ended.
                        measuredWordEnd == null && line.endEstimated && end == nextStart && nextStart - line.timeMs >= MIN_UNMEASURED_LINE_INTERVAL_MS -> out += nextStart
                    }
                }
            }
            return out.filter { it in 0..durationMs }.sorted()
        }
    }
}
