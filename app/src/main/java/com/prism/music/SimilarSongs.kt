package com.prism.music

import com.prism.music.data.model.Song
import com.prism.music.playback.MusicKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.ln

/** A song's language, as far as its title, artist and lyrics show. */
enum class SongLanguage(val label: String) {
    PUNJABI("Punjabi"), HINDI("Hindi"), ENGLISH("English"), OTHER("Other language"), UNKNOWN("Language unknown");

    /** Punjabi and Hindi are suggested together; English on its own. */
    val group: LanguageGroup
        get() = when (this) {
            PUNJABI, HINDI -> LanguageGroup.DESI
            ENGLISH -> LanguageGroup.ENGLISH
            OTHER -> LanguageGroup.OTHER
            UNKNOWN -> LanguageGroup.UNKNOWN
        }
}

enum class LanguageGroup(val label: String) {
    DESI("Punjabi & Hindi"), ENGLISH("English"), OTHER("Other languages"), UNKNOWN("Any language")
}

/**
 * Guesses a song's language from its words: the script first (Gurmukhi is Punjabi, Devanagari Hindi), then, for the
 * romanised titles and lyrics most Punjabi and Hindi songs carry, the everyday words each language leans on ("tenu",
 * "mainu", "kudi" for Punjabi; "hai", "mujhe", "kya" for Hindi; "the", "you" for English). Pure, so it's unit-tested.
 */
object LanguageGuess {
    data class Guess(val language: SongLanguage, val confidence: Float)

    private val PUNJABI = setOf(
        "tenu", "mainu", "sanu", "tuhanu", "tainu", "kudi", "kudiye", "kuriye", "munda", "munde", "jatt", "jatti",
        "nal", "naal", "gallan", "galla", "hun", "hunn", "kithe", "kiven", "kive", "sohni", "sohniye", "sohneya", "pind", "akhan",
        "akhiyan", "vich", "assi", "asi", "tusi", "tussi", "tuhade", "sade", "gabru", "yaariyan", "sajjna", "nachdi", "nachde",
        "gidha", "giddha", "bhangra", "balle", "shava", "jande", "jaave", "laggi", "lagge", "karde", "kardi", "chaldi", "rehnda",
        "rehndi", "hoye", "honda", "hondi", "dekh", "vekh", "vekhi", "jaandi", "aunda", "aundi", "kaali", "patiala", "punjab",
        "punjabi", "desi", "sardar", "dhol", "tappe", "boliyan", "kurta", "jutti", "chunni", "kalla", "kalli", "baapu", "bebe",
    )
    private val HINDI = setOf(
        "hai", "hain", "mein", "mujhe", "tujhe", "tum", "tumhe", "tumhari", "tumhara", "hum", "humko", "kya", "nahi", "nahin",
        "kyun", "kyon", "kaise", "bhi", "tha", "thi", "hoon", "aur", "yeh", "woh", "jab", "tab", "kabhi", "apna", "apni", "raha",
        "rahi", "rahe", "saath", "zindagi", "khwab", "aankhein", "aankhon", "raat", "baat", "baatein", "chahiye", "lagta",
        "lagti", "kuch", "sab", "kahin", "jahan", "yahan", "wahan", "dard", "mohabbat", "deewana", "deewani", "sapne", "duniya",
        "bollywood", "hindi", "filmi", "pyaara", "jaanam", "sanam", "khuda", "tujhse", "mujhse", "hota", "hoti", "karta",
    )
    /** Words both use (romanised the same way): they say "Punjabi or Hindi", not which. */
    private val DESI = setOf(
        "dil", "pyaar", "pyar", "ishq", "yaar", "yaara", "jaan", "tera", "teri", "tere", "mera", "meri", "mere", "sajna", "mahi",
        "maahi", "rabba", "rab", "sohna", "chann", "naina", "nain", "dilbar", "haye", "mehbooba", "soniye", "jaaneman", "dildar",
        "ankhiyan", "kudiyan", "jind", "zaalim", "bawli", "nachle", "nachna", "thumka", "lehenga", "mundeya",
    )
    private val ENGLISH = setOf(
        "the", "and", "you", "your", "i", "me", "my", "love", "baby", "night", "know", "just", "like", "don't", "i'm", "it's",
        "we", "they", "this", "that", "with", "for", "what", "when", "all", "never", "feel", "want", "girl", "heart", "tonight",
        "yeah", "be", "is", "are", "was", "in", "on", "of", "to", "can't", "won't", "gonna", "wanna", "got", "out", "up", "down",
        "every", "time", "way", "say", "could", "would", "should", "remix", "official", "feat",
    )
    /** Words that are featured in titles but not language: never counted. */
    private val NEUTRAL = setOf("ft", "feat", "remix", "official", "audio", "video", "lyrics", "lyrical", "version", "live")

    fun detect(texts: List<String>): Guess {
        val text = texts.joinToString(" ")
        var gurmukhi = 0; var devanagari = 0; var arabic = 0; var otherScript = 0; var latin = 0
        for (ch in text) {
            when (ch.code) {
                in 0x0A00..0x0A7F -> gurmukhi++
                in 0x0900..0x097F -> devanagari++
                in 0x0600..0x06FF -> arabic++
                in 0x0041..0x005A, in 0x0061..0x007A -> latin++
                else -> if (Character.isLetter(ch)) otherScript++
            }
        }
        if (gurmukhi >= 3 && gurmukhi >= devanagari) return Guess(SongLanguage.PUNJABI, 0.95f)
        if (devanagari >= 3) return Guess(SongLanguage.HINDI, 0.9f)
        // Shahmukhi Punjabi and Urdu share Arabic script; neither is told from Arabic here, so it's left as another language.
        if (arabic + otherScript >= 3 && arabic + otherScript > latin) return Guess(SongLanguage.OTHER, 0.85f)
        val words = Regex("[a-z']+").findAll(text.lowercase()).map { it.value.trim('\'') }.filter { it.isNotEmpty() && it !in NEUTRAL }.toList()
        var p = 0f; var h = 0f; var d = 0f; var e = 0f
        for (w in words) {
            when (w) {
                in PUNJABI -> p += 1f
                in HINDI -> h += 1f
                in DESI -> d += 1f
                in ENGLISH -> e += 1f
            }
        }
        val desi = p + h + d
        return when {
            desi >= 2 && desi > e * 1.2f -> {
                val confidence = (desi / (desi + e)).coerceIn(0.5f, 0.95f)
                when {
                    p > h * 1.3f -> Guess(SongLanguage.PUNJABI, confidence)
                    h > p * 1.3f -> Guess(SongLanguage.HINDI, confidence)
                    // Shared words only: Punjabi or Hindi, which doesn't matter for suggestions (they're one group).
                    else -> Guess(if (p >= h) SongLanguage.PUNJABI else SongLanguage.HINDI, confidence * 0.8f)
                }
            }
            e >= 3 && e > desi * 1.5f -> Guess(SongLanguage.ENGLISH, (e / (e + desi)).coerceIn(0.5f, 0.95f))
            // A short English title ("Love Me Like You Do") with nothing desi in it.
            e >= 2 && desi == 0f -> Guess(SongLanguage.ENGLISH, 0.55f)
            desi >= 1 && e == 0f -> Guess(if (h > p) SongLanguage.HINDI else SongLanguage.PUNJABI, 0.5f)
            else -> Guess(SongLanguage.UNKNOWN, 0f)
        }
    }
}

/** What's measured of a song's sound ([com.prism.music.playback.MixTempo]): tempo, key and loudness, each if known. */
data class SoundFeatures(val bpm: Double?, val key: MusicKey?, val loudness: Float?)

/** How alike two songs sound, 0..1, from what's measured of both; an unknown measure counts as neither like nor unlike. */
object SoundMatch {
    fun score(seed: SoundFeatures, other: SoundFeatures): Float {
        val tempo = if (seed.bpm != null && other.bpm != null && seed.bpm > 0 && other.bpm > 0) {
            // Octave-free: 85 and 170 BPM share a beat.
            val b = listOf(other.bpm, other.bpm * 2, other.bpm / 2).minBy { abs(ln(it / seed.bpm)) }
            (1 - abs(ln(b / seed.bpm)) / ln(1.15)).toFloat().coerceIn(0f, 1f)
        } else 0.5f
        val key = if (seed.key != null && other.key != null) (1 - MusicKey.clash(seed.key, other.key)).toFloat() else 0.5f
        val energy = if (seed.loudness != null && other.loudness != null) (1 - abs(seed.loudness - other.loudness) / 8f).coerceIn(0f, 1f) else 0.5f
        return 0.45f * tempo + 0.3f * key + 0.25f * energy
    }

    /** With YouTube Music's own order ([rank] of [total], best first): its sense of "related" counts, the sound more. */
    fun combined(sound: Float, rank: Int, total: Int): Float =
        0.65f * sound + 0.35f * (1f - rank.toFloat() / total.coerceAtLeast(1))
}

/**
 * Finds songs like one that's playing, from all of YouTube Music (not the listener's playlists): its Related songs and
 * its radio, kept to the same language group (Punjabi and Hindi together, English apart), then ranked by how alike they
 * sound (tempo, key, energy from a quick scan of each) with YouTube's own order. Used by "More like this" and by
 * "Keep the mix going".
 */
class SimilarSongs(private val c: AppContainer) {
    data class Pick(
        val song: Song,
        val language: SongLanguage,
        val bpm: Double?,
        val key: String?,
        /** 0..100. */
        val match: Int,
        val analysed: Boolean,
        /** "Same key, same tempo as Brown Munde · Punjabi · Related on YouTube Music" ([SimilarReasons]). */
        val why: String = "",
    )

    data class Result(val seed: Song, val seedLanguage: SongLanguage, val picks: List<Pick>)

    /** What the search is doing now, for the screen. */
    data class Progress(val stage: String, val done: Int = 0, val total: Int = 0)

    /** Artists' languages, learnt from their songs (a romanised title alone often says nothing). */
    private val artistLanguage = ConcurrentHashMap<String, SongLanguage>()

    private fun artistKey(song: Song) = song.artists.firstOrNull()?.id?.takeIf { it.isNotBlank() } ?: song.primaryArtist.lowercase()

    /** [song]'s language from its title, album and lyrics ([fetchLyrics]: may look them up), else its artist's. */
    suspend fun languageOf(song: Song, fetchLyrics: Boolean): SongLanguage {
        val lyrics = runCatching {
            withContext(Dispatchers.IO) { c.lyrics.best(song, fetch = fetchLyrics)?.lines?.joinToString(" ") { it.text } }
        }.getOrNull().orEmpty()
        val guess = LanguageGuess.detect(listOfNotNull(song.title, song.album?.title, lyrics.take(4_000)))
        val key = artistKey(song)
        if (guess.language != SongLanguage.UNKNOWN && guess.confidence >= 0.6f) artistLanguage[key] = guess.language
        return if (guess.language != SongLanguage.UNKNOWN) guess.language else artistLanguage[key] ?: SongLanguage.UNKNOWN
    }

    /** Whether audio may be fetched to analyse songs (Data saver, and "Measure songs on mobile data"). */
    private fun mayFetchAudio() = !c.dataSaving() && (c.settings.current.mixTempoOnCellular || !c.network.metered.value)

    private fun features(id: String) = SoundFeatures(c.mixTempo.djBpm(id), c.mixTempo.key(id), c.mixTempo.djLoudness(id))

    suspend fun find(
        seed: Song,
        exclude: Set<String> = emptySet(),
        limit: Int = 20,
        analyse: Int = 12,
        /** The language group to suggest from; null: the seed's own (Language mix can ask for the other). */
        group: LanguageGroup? = null,
        onProgress: (Progress) -> Unit = {},
    ): Result {
        onProgress(Progress("Reading ${seed.title}…"))
        val seedLanguage = languageOf(seed, fetchLyrics = true)
        onProgress(Progress("Finding songs on YouTube Music…"))
        val related = runCatching { withContext(Dispatchers.IO) { c.ytm.related(seed.id, seed.title).songs } }.getOrDefault(emptyList())
        val relatedIds = related.map { it.id }.toSet()
        val radio = runCatching { withContext(Dispatchers.IO) { c.radio(seed.id, "RDAMVM${seed.id}").songs } }.getOrDefault(emptyList())
        // Related first (YouTube's sense of "like this"), then the radio, each in its own order.
        val seen = HashSet<String>()
        val candidates = (related + radio).filter { s ->
            !s.isEpisode && !s.isLive && s.id != seed.id && s.id !in exclude && s.id.isNotBlank() &&
                seen.add(s.id) && seen.add("${s.title.lowercase()}|${s.primaryArtist.lowercase()}")
        }
        if (candidates.isEmpty()) return Result(seed, seedLanguage, emptyList())

        // Language: from the titles first; songs still unknown near the top get their lyrics looked up.
        onProgress(Progress("Checking languages…", 0, candidates.size))
        val languages = HashMap<String, SongLanguage>()
        var lookedUp = 0
        candidates.forEachIndexed { i, s ->
            var lang = languageOf(s, fetchLyrics = false)
            if (lang == SongLanguage.UNKNOWN && seedLanguage != SongLanguage.UNKNOWN && lookedUp < 15) {
                lookedUp++
                lang = languageOf(s, fetchLyrics = true)
            }
            languages[s.id] = lang
            onProgress(Progress("Checking languages…", i + 1, candidates.size))
        }
        val group = group ?: seedLanguage.group
        val same = candidates.filter { languages[it.id]?.group == group }
        // With the seed's language known, only its group; songs still unknown fill in when there are too few.
        val kept = when (group) {
            LanguageGroup.UNKNOWN -> candidates
            else -> same + if (same.size < limit) candidates.filter { languages[it.id] == SongLanguage.UNKNOWN }.take(limit - same.size) else emptyList()
        }

        // Sound: a quick scan of the seed and the leading candidates (three short stretches of each).
        val net = mayFetchAudio()
        val toScan = listOf(seed) + kept.take(analyse)
        toScan.forEachIndexed { i, s ->
            onProgress(Progress("Analysing sound…", i, toScan.size))
            runCatching { c.mixTempo.quickScan(s.id, net) }
        }
        val seedSound = features(seed.id)
        val picks = kept.mapIndexed { rank, s ->
            val f = features(s.id)
            val analysed = f.bpm != null || f.key != null || f.loudness != null
            val score = SoundMatch.combined(SoundMatch.score(seedSound, f), rank, kept.size)
            val lang = languages[s.id] ?: SongLanguage.UNKNOWN
            Pick(
                s, lang, f.bpm, f.key?.camelot, (score * 100).toInt().coerceIn(1, 99), analysed,
                SimilarReasons.why(seed.title, lang, seedSound, f, related = s.id in relatedIds),
            )
        }.sortedWith(compareByDescending<Pick> { it.language.group == group }.thenByDescending { it.match }).take(limit)
        return Result(seed, seedLanguage, picks)
    }
}
