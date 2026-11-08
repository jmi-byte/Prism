package com.prism.music

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.prism.music.data.ShareLinks
import com.prism.music.data.innertube.SearchFilter
import com.prism.music.data.model.Song
import com.prism.music.data.model.SongItem
import com.prism.music.playback.QueueKind
import com.prism.music.playback.QueueSource
import com.prism.music.ui.theme.PrismIcons
import com.prism.music.ui.components.recordInto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin


private enum class VelouraTab { HOME, SEARCH, LIBRARY, DJ, QUEUE, SIMILAR, PARTY, ACCOUNT, SETTINGS }

/** Veloura's UI (laid out in VelouraScreens.kt). Prism's playback and YouTube Music engines remain underneath. */
@Composable
fun AutoMixDjRoot(c: AppContainer, deepLink: StateFlow<Uri?>) {
    var tab by remember { mutableStateOf(VelouraTab.HOME) }
    // Where Now Playing's ⌄ goes back to.
    var lastTab by remember { mutableStateOf(VelouraTab.HOME) }
    var showLogin by remember { mutableStateOf(false) }
    // "Arrange as a set" and "Share this moment", over whichever page asked.
    var setDialog by remember { mutableStateOf(false) }
    var storyDialog by remember { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }
    // "Adapt to the moment": the transition style follows the time of day and the car, checked each minute.
    val rootContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        var applied: String? = null
        while (true) {
            val s = c.settings.current.sound
            if (s.moodAuto) {
                val mood = ContextMood.of(ContextMood.now(rootContext))
                if (mood.label != applied) {
                    applied = mood.label
                    applyTransitionStyle(c, mood.style)
                }
            } else applied = null
            delay(60_000)
        }
    }
    // Kept while the app is open, so switching tabs doesn't download Home's playlists and the library again.
    val libraryCache = remember { LibraryCache() }
    val link by deepLink.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    fun go(t: VelouraTab) {
        if (t != VelouraTab.DJ && t != VelouraTab.QUEUE && t != VelouraTab.SIMILAR && t != VelouraTab.PARTY) lastTab = t
        tab = t
    }

    LaunchedEffect(link) {
        val uri = link ?: return@LaunchedEffect
        message = "Opening YouTube Music link…"
        val error = playYouTubeLink(c, uri)
        message = error
        if (error == null) go(VelouraTab.DJ)
    }
    // A full-screen page goes back with the system Back, as the design's ⌄ and ← do.
    androidx.activity.compose.BackHandler(enabled = tab != VelouraTab.HOME && !showLogin) {
        go(when (tab) { VelouraTab.QUEUE, VelouraTab.SIMILAR, VelouraTab.PARTY -> VelouraTab.DJ; VelouraTab.DJ -> lastTab; else -> VelouraTab.HOME })
    }

    MaterialTheme(colorScheme = VelouraColors) {
        if (showLogin) {
            com.prism.music.ui.screens.LoginScreen(
                onDone = { showLogin = false; go(VelouraTab.HOME) },
                onBack = { showLogin = false },
            )
            return@MaterialTheme
        }
        val mixMenu = listOf("Mix settings" to { go(VelouraTab.SETTINGS) })
        if (setDialog) SetDialog(c) { setDialog = false }
        if (storyDialog) StoryDialog(c) { storyDialog = false }
        if (saveDialog) SaveMixDialog(c) { saveDialog = false }
        when (tab) {
            // The player and its plan fill the screen, as in the design.
            VelouraTab.DJ -> Surface(Modifier.fillMaxSize(), color = Color.White) {
                NowPlayingScreen(
                    c, onCollapse = { go(lastTab) },
                    menu = listOf(
                        "More like this" to { go(VelouraTab.SIMILAR) },
                        "Share this moment" to { storyDialog = true },
                        "Save this mix" to { saveDialog = true },
                        "Party queue" to { go(VelouraTab.PARTY) },
                        "Arrange queue as a set" to { setDialog = true },
                        "Queue & Transition Plan" to { go(VelouraTab.QUEUE) },
                    ) + mixMenu,
                    onFindMusic = { go(VelouraTab.SEARCH) },
                )
            }
            VelouraTab.QUEUE -> Surface(Modifier.fillMaxSize(), color = VelouraBackground) {
                QueueScreen(c, onBack = { go(VelouraTab.DJ) }, menu = listOf("Arrange queue as a set" to { setDialog = true }) + mixMenu)
            }
            VelouraTab.PARTY -> Surface(Modifier.fillMaxSize(), color = VelouraBackground) {
                PartyScreen(c, onBack = { go(VelouraTab.DJ) })
            }
            VelouraTab.SIMILAR -> Surface(Modifier.fillMaxSize(), color = VelouraBackground) {
                SimilarScreen(c, onBack = { go(VelouraTab.DJ) })
            }
            else -> Surface(Modifier.fillMaxSize(), color = VelouraBackground) {
                Scaffold(
                    containerColor = VelouraBackground,
                    topBar = {
                        when (tab) {
                            VelouraTab.HOME -> VelouraBrandBar(message, onAccount = { go(VelouraTab.ACCOUNT) }, onSettings = { go(VelouraTab.SETTINGS) })
                            VelouraTab.SEARCH -> VelouraTitleBar("Search", null, "", {})
                            VelouraTab.LIBRARY -> VelouraTitleBar("Your Music", null, "", {})
                            VelouraTab.ACCOUNT -> VelouraTitleBar("YouTube Music Account", PrismIcons.ArrowBack, "Back", { go(VelouraTab.HOME) })
                            else -> VelouraTitleBar("Mix Settings", PrismIcons.ArrowBack, "Back", { go(lastTab.takeIf { it != VelouraTab.SETTINGS } ?: VelouraTab.HOME) })
                        }
                    },
                    bottomBar = {
                        VelouraTabBar(
                            when (tab) {
                                VelouraTab.HOME -> VelouraTabItem.HOME
                                VelouraTab.SEARCH -> VelouraTabItem.SEARCH
                                VelouraTab.LIBRARY -> VelouraTabItem.LIBRARY
                                else -> null
                            },
                        ) { t ->
                            go(when (t) {
                                VelouraTabItem.HOME -> VelouraTab.HOME
                                VelouraTabItem.SEARCH -> VelouraTab.SEARCH
                                VelouraTabItem.LIBRARY -> VelouraTab.LIBRARY
                                VelouraTabItem.DJ -> VelouraTab.DJ
                            })
                        }
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        when (tab) {
                            VelouraTab.HOME -> HomeScreen(
                                c, libraryCache,
                                onSearch = { go(VelouraTab.SEARCH) }, onPlayer = { go(VelouraTab.DJ) },
                                onLibrary = { go(VelouraTab.LIBRARY) }, onSettings = { go(VelouraTab.SETTINGS) },
                                onConnect = { showLogin = true }, onMessage = { message = it },
                            )
                            VelouraTab.SEARCH -> Search(c) { go(VelouraTab.DJ) }
                            VelouraTab.LIBRARY -> VelouraLibrary(c, libraryCache, onConnect = { showLogin = true }, onPlayed = { go(VelouraTab.DJ) })
                            VelouraTab.ACCOUNT -> Account(c, onConnect = { showLogin = true }, onBack = { go(VelouraTab.HOME) })
                            else -> MixSettings(c)
                        }
                    }
                }
            }
        }
    }
}

/** Turns Intelligent AutoMix on, on its own (Prism's Crossfade and DJ mode would compete with it). */
private fun autoMixOn(c: AppContainer, on: Boolean = true) {
    c.settings.setIntelligentAutoMix(on)
    if (on) { c.settings.setMix(false); c.dj.setOn(false) }
}

/** Plays [item] (a playlist or a mix) from the start; null when it couldn't, with why. */
private suspend fun playPlaylist(c: AppContainer, item: com.prism.music.data.model.PlaylistItem): String? {
    val songs = runCatching { VelouraPlaylists.songs(c, item) }.getOrNull().orEmpty().filter { !it.isEpisode && !it.isLive }
    if (songs.isEmpty()) return "Couldn't load ${item.title}"
    val kind = if (VelouraPlaylists.isMix(item)) QueueKind.RADIO else QueueKind.PLAYLIST
    c.player.playQueue(songs, 0, QueueSource(kind, item.title, originId = VelouraPlaylists.normal(item.id)))
    return null
}

/** A playlist's song count from YouTube's subtitle ("Playlist • YouTube Music • 50 songs"), or the subtitle's last part. */
private fun playlistDetail(subtitle: String): String {
    val parts = subtitle.split('•', '·').map { it.trim() }.filter { it.isNotEmpty() }
    return parts.firstOrNull { it.any(Char::isDigit) && (it.contains("song", true) || it.contains("track", true)) }
        ?: parts.lastOrNull() ?: "Playlist"
}

@Composable
private fun HomeScreen(
    c: AppContainer,
    cache: LibraryCache,
    onSearch: () -> Unit,
    onPlayer: () -> Unit,
    onLibrary: () -> Unit,
    onSettings: () -> Unit,
    onConnect: () -> Unit,
    onMessage: (String?) -> Unit,
) {
    val settings by c.settings.flow.collectAsState()
    val current by c.player.currentSong.collectAsState()
    val scope = rememberCoroutineScope()
    // The moment's mood, looked at again every minute (the hour, the car, headphones).
    val homeContext = androidx.compose.ui.platform.LocalContext.current
    var minute by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); minute++ } }
    val mood = remember(minute) { ContextMood.of(ContextMood.now(homeContext)) }
    LaunchedEffect(Unit) {
        if (cache.home.value == null) {
            cache.home.value = withContext(Dispatchers.IO) { runCatching { c.ytm.fullHome(pages = 3) }.getOrDefault(emptyList()) }
        }
    }
    val shelves by cache.home
    val items = shelves?.flatMap { it.items }?.filterIsInstance<com.prism.music.data.model.PlaylistItem>()
        ?.filter { VelouraPlaylists.normal(it.id) != "LM" }?.distinctBy { VelouraPlaylists.normal(it.id) }?.take(12)
    fun play(item: com.prism.music.data.model.PlaylistItem) {
        onMessage("Loading ${item.title}…")
        scope.launch {
            val error = playPlaylist(c, item)
            onMessage(error)
            if (error == null) onPlayer()
        }
    }
    // Something to mix: what's playing, else the first trending playlist, else Search.
    fun startMixing() {
        autoMixOn(c)
        when {
            current != null -> onPlayer()
            !items.isNullOrEmpty() -> play(items.first())
            else -> onSearch()
        }
    }
    VelouraHome(
        VelouraHomeUi(
            loggedIn = settings.isLoggedIn,
            playlists = items?.map { PlaylistCard(it.id, it.title, playlistDetail(it.subtitle), it.thumbnail) },
            playlistsError = if (shelves?.isEmpty() == true) "Couldn't reach YouTube Music. Try Search." else null,
            autoMixOn = settings.intelligentAutoMix,
            mood = mood.label to mood.detail,
        ),
        onSearch = onSearch,
        onTile = { t ->
            when (t) {
                HomeTile.AUTOMIX_PARTY -> {
                    // Party: every pair mixed, club-style DJ effects.
                    c.settings.setDjEffects(true)
                    c.settings.setDjStyle(com.prism.music.data.prefs.DjStyle.CLUB)
                    startMixing()
                }
                HomeTile.DJ_MIXES -> onSettings()
                HomeTile.GENRES -> onSearch()
                HomeTile.FOR_YOU -> onLibrary()
            }
        },
        onPlaylist = { card -> items?.firstOrNull { it.id == card.id }?.let(::play) },
        onSeeAll = onLibrary,
        onConnect = onConnect,
        onQuickMix = ::startMixing,
        onMood = {
            // The mood's transitions now; its set shape on the songs ahead, when there are some.
            applyTransitionStyle(c, mood.style)
            autoMixOn(c)
            scope.launch {
                val upcoming = c.player.upNext.value.size
                if (upcoming >= 2) {
                    val said = arrangeAsSet(c, mood.shape) { _, _ -> }
                    onMessage("${mood.label}: $said")
                } else {
                    onMessage("${mood.label}: ${mood.style.label} transitions")
                    if (current == null && !items.isNullOrEmpty()) play(items.first())
                }
            }
        },
    )
}

/**
 * Veloura's DJ workspace inside Prism's complete navigation graph (the Prism-shell build's DJ tab). The Veloura shell
 * ([AutoMixDjRoot]) shows the same Now Playing full screen.
 */
@Composable
fun VelouraDjScreen(c: AppContainer, bottomPadding: androidx.compose.ui.unit.Dp, onFindMusic: () -> Unit) {
    var queue by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = VelouraColors) {
        Box(Modifier.fillMaxSize().padding(bottom = bottomPadding)) {
            if (queue) QueueScreen(c, onBack = { queue = false }, menu = emptyList())
            else NowPlayingScreen(c, onCollapse = onFindMusic, menu = listOf("Queue & Transition Plan" to { queue = true }), onFindMusic = onFindMusic)
        }
    }
}

/** What AutoMix is doing for the playing song and the next one, in plain words (never more than is planned). */
@Composable
private fun autoMixStatus(c: AppContainer, songId: String?, nextId: String?, position: Long): String {
    val settings by c.settings.flow.collectAsState()
    val decision by c.intelligentAutoMixShadow.decision.collectAsState()
    val blendNow by c.mixState.blend.collectAsState()
    val blendMark by c.mixState.mark.collectAsState()
    val exact = decision?.takeIf { it.songId == songId && it.nextSongId == nextId }
    val plan = exact?.plan
    return when {
        !settings.intelligentAutoMix -> "Off · switch on to mix every song into the next"
        // A blend under way, or about to start (an automatic one, or Next pressed): what's really happening now.
        blendNow != null -> "Mixing into the next song…" + when (blendNow!!.dj) {
            com.prism.music.playback.DjBlend.PLAIN -> ""
            com.prism.music.playback.DjBlend.SMOOTH -> " · DJ: bass swap"
            com.prism.music.playback.DjBlend.CLUB -> " · DJ: bass swap + filter sweep"
            com.prism.music.playback.DjBlend.ECHO -> " · DJ: echo out"
            com.prism.music.playback.DjBlend.MASHUP -> " · DJ: mashup"
        }
        blendMark?.takeIf { it.songId == songId && it.startMs - position in 0..MIX_SOON_MS } != null ->
            "Mixing into the next song in ${((blendMark!!.startMs - position) / 1000) + 1} s"
        plan != null -> buildString {
            append("Next mix in ~${clockOf((plan.transitionAtMs - position).coerceAtLeast(0))} · ")
            append(plan.boundary.label())
            append(" · ")
            append(TransitionStyle.of(settings.sound.djEffects, settings.sound.djStyle).label)
            append(" · ")
            append(transitionLength(settings.mixAuto, settings.mixSeconds, settings.sound.djEffects, plan.estimated))
        }
        nextId == null -> "Add another song for a transition"
        exact != null -> "Analysis complete · no mid-song cue for this pair"
        else -> "Analysing the current and next song…"
    }
}

@Composable
private fun NowPlayingScreen(c: AppContainer, onCollapse: () -> Unit, menu: List<Pair<String, () -> Unit>>, onFindMusic: () -> Unit) {
    val song by c.player.currentSong.collectAsState()
    val playing by c.player.isPlaying.collectAsState()
    val settings by c.settings.flow.collectAsState()
    val queue by c.player.queue.collectAsState()
    val upNext by c.player.upNext.collectAsState()
    val liked by c.library.likedIds.collectAsState()
    val shuffle by c.player.shuffle.collectAsState()
    val repeat by c.player.repeatMode.collectAsState()
    var position by remember(song?.id) { mutableLongStateOf(c.player.position) }
    LaunchedEffect(song?.id, playing) { while (true) { position = c.player.position; delay(if (playing) 250 else 700) } }
    val next = upNext.firstOrNull()?.let(queue::getOrNull)?.song
    val status = autoMixStatus(c, song?.id, next?.id, position)
    // Kept across songs: lyrics stay up from one song to the next until switched back to the cover.
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    val s = song
    if (s == null) {
        Column(Modifier.fillMaxSize()) {
            VelouraTitleBar("Now Playing", PrismIcons.ChevronDown, "Close player", onCollapse, menu)
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                ActionCard("Choose music to start AutoMix", "Pick music from Home, Search or Library. The player and its transition plan fill in from there.", "Find music", onFindMusic)
            }
        }
        return
    }
    VelouraNowPlaying(
        VelouraNowPlayingUi(
            title = s.title, artist = s.artistText, artwork = s.thumbnail, liked = s.id in liked, playing = playing,
            positionMs = position, durationMs = c.player.duration.coerceAtLeast(s.durationSec * 1000L),
            autoMixOn = settings.intelligentAutoMix, status = status, shuffle = shuffle, repeatMode = repeat,
        ),
        onCollapse = onCollapse,
        menu = menu,
        onSeek = { c.player.seekTo(it) },
        onLike = { c.library.toggleLike(s) },
        onAutoMix = { autoMixOn(c, it) },
        onShuffle = { c.player.toggleShuffle() },
        onPrevious = { c.player.previous() },
        onPlayPause = { c.player.togglePlay() },
        onNext = { c.player.next() },
        onRepeat = { c.player.cycleRepeat() },
        showLyrics = showLyrics,
        onLyrics = { showLyrics = !showLyrics },
        lyricsOptions = {
            // Lyrics in Gurmukhi or Devanagari: in Latin letters (under each line, or instead), and their English meaning.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val roman = settings.romanize
                FilterChip(
                    roman != com.prism.music.data.prefs.RomanizeMode.OFF,
                    {
                        c.settings.setRomanize(when (roman) {
                            com.prism.music.data.prefs.RomanizeMode.OFF -> com.prism.music.data.prefs.RomanizeMode.UNDER
                            com.prism.music.data.prefs.RomanizeMode.UNDER -> com.prism.music.data.prefs.RomanizeMode.INSTEAD
                            com.prism.music.data.prefs.RomanizeMode.INSTEAD -> com.prism.music.data.prefs.RomanizeMode.OFF
                        })
                    },
                    { Text(when (roman) {
                        com.prism.music.data.prefs.RomanizeMode.OFF -> "Romanised: off"
                        com.prism.music.data.prefs.RomanizeMode.UNDER -> "Romanised: under"
                        com.prism.music.data.prefs.RomanizeMode.INSTEAD -> "Romanised: instead"
                    }) },
                )
                val english = settings.lyricsTranslate && settings.lyricsTranslateTo.ifBlank { "en" } == "en"
                FilterChip(english, {
                    if (english) c.settings.setLyricsTranslate(false)
                    else { c.settings.setLyricsTranslateTo("en"); c.settings.setLyricsTranslate(true) }
                }, { Text("English meaning") })
            }
        },
        lyrics = { modifier ->
            // Prism's own lyrics panel (word-by-word karaoke), with the listener's lyrics settings.
            val lyricsState = com.prism.music.ui.player.rememberLyricsState(s, opened = showLyrics)
            CompositionLocalProvider(com.prism.music.ui.theme.LocalAppSettings provides settings) {
                com.prism.music.ui.player.LyricsPanel(
                    lyricsState, playing, controlsOpen = true, onRevealControls = {}, onHideControls = {},
                    modifier = modifier, lyricsGround = false,
                )
            }
        },
    )
}

/** "BPM 122 → 122 · Key 8A → 8A ✓": what's been measured of the two songs, or null when nothing is yet. */
private fun matchLine(c: AppContainer, fromId: String, toId: String): String? {
    val a = c.mixTempo.cached(fromId)?.takeIf { it.steady }
    val b = c.mixTempo.cached(toId)?.takeIf { it.steady }
    val ka = c.mixTempo.key(fromId)
    val kb = c.mixTempo.key(toId)
    val parts = buildList {
        if (a != null && b != null) add("BPM ${Math.round(a.bpm)} → ${Math.round(b.bpm)}")
        if (ka != null && kb != null) {
            val fits = com.prism.music.playback.MusicKey.clash(ka, kb) <= 0.25
            add("Key ${ka.camelot} → ${kb.camelot}${if (fits) " ✓" else ""}")
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** Whether the two songs' beats can be lined up: steady tempos within 8% of each other, octave-free. */
private fun beatsMatch(c: AppContainer, fromId: String, toId: String): Boolean {
    val a = c.mixTempo.cached(fromId)?.takeIf { it.steady } ?: return false
    val b = c.mixTempo.cached(toId)?.takeIf { it.steady } ?: return false
    val bb = listOf(b.bpm, b.bpm * 2, b.bpm / 2).minBy { kotlin.math.abs(kotlin.math.ln(it / a.bpm)) }
    return kotlin.math.abs(a.bpm / bb - 1) <= 0.08
}

@Composable
private fun QueueScreen(c: AppContainer, onBack: () -> Unit, menu: List<Pair<String, () -> Unit>>) {
    val queue by c.player.queue.collectAsState()
    val currentIndex by c.player.currentIndex.collectAsState()
    val upNext by c.player.upNext.collectAsState()
    val decision by c.intelligentAutoMixShadow.decision.collectAsState()
    val into by c.mixState.into.collectAsState()
    val settings by c.settings.flow.collectAsState()
    val playing by c.player.isPlaying.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(c.player.position) }
    LaunchedEffect(playing) { while (true) { position = c.player.position; delay(if (playing) 500 else 1000) } }
    // Play order, not list order: with shuffle on, the next song is not necessarily the row below the current one.
    // This is the same order PlaybackService plans for, so the card and the actual transition always agree.
    val coming = upNext.mapNotNull(queue::getOrNull)
    val current = queue.getOrNull(currentIndex)?.song
    val next = coming.firstOrNull()?.song
    val exact = decision?.takeIf { it.songId == current?.id && it.nextSongId == next?.id }
    val plan = exact?.plan
    fun row(e: com.prism.music.playback.QueueEntry) =
        QueueSongUi(e.uid, e.index, e.song.title, e.song.artistText, e.song.thumbnail, e.song.durationSec * 1000L)
    val duration = current?.let { maxOf(c.player.duration, it.durationSec * 1000L) } ?: 0L
    val matched = current != null && next != null && beatsMatch(c, current.id, next.id)
    val ui = VelouraQueueUi(
        current = queue.getOrNull(currentIndex)?.let(::row),
        mixOut = plan?.transitionAtMs?.let(::clockOf) ?: if (exact != null) "Song end" else "Analysing",
        positionMs = position,
        durationMs = duration,
        inMs = into?.takeIf { it.songId == current?.id && it.inMs > 0 }?.inMs,
        outMs = plan?.transitionAtMs,
        next = coming.firstOrNull()?.let(::row),
        transitionKind = plan?.let { if (matched) "Beat matched" else it.boundary.label() },
        mixIn = plan?.entryAtMs?.takeIf { it > 0 }?.let { (if (plan.entryEstimated) "≈" else "") + clockOf(it) },
        match = if (current != null && next != null) matchLine(c, current.id, next.id) else null,
        planDetails = buildList {
            if (plan == null) {
                add(if (exact != null) "No mid-song cue for this pair: it crossfades at the end of the song." else "Analysing the two songs…")
            } else {
                add("Leaves this song at ${clockOf(plan.transitionAtMs)}: ${when (plan.boundary) {
                    com.prism.music.playback.AutoMixBoundary.PHRASE -> "at a break between sung phrases"
                    com.prism.music.playback.AutoMixBoundary.BEAT -> "on a measured beat"
                    com.prism.music.playback.AutoMixBoundary.ESTIMATED -> "a best guess (no clear phrase or beat found)"
                }}${if (plan.reason == com.prism.music.playback.AutoMixReason.HIGHLIGHT_HEARD) ", after its highlight" else ""}.")
                add(if (plan.entryAtMs > 0) "The next song comes in at ${clockOf(plan.entryAtMs)}${if (plan.entryEstimated) " (best guess)" else ""}." else "The next song starts from its opening.")
            }
            add("Transition: ${TransitionStyle.of(settings.sound.djEffects, settings.sound.djStyle).label}, " +
                "${transitionLength(settings.mixAuto, settings.mixSeconds, settings.sound.djEffects, plan?.estimated == true)}" +
                (if (settings.mixAuto) " (automatic)." else "."))
            add(if (matched) "Beats lined up: both tempos are clear and close." else "Beats not lined up: a plain blend.")
        },
        upNext = coming.map(::row),
    )
    VelouraQueue(
        ui, tab, { tab = it }, onBack, menu,
        onPlay = { c.player.skipTo(it.index) },
        onRemove = { c.player.removeAt(it.index) },
        onPlayNext = { c.player.moveNext(listOf(it.index)) },
        onReorder = { order -> applyReorder(c, ui.upNext, order) },
    )
}

/**
 * One row dragged in Up Next ([before] → [after], both in play order): moved in the player's queue, or (shuffled) in the
 * play order, beside the row it now sits next to.
 */
private fun applyReorder(c: AppContainer, before: List<QueueSongUi>, after: List<QueueSongUi>) {
    val i = before.indices.firstOrNull { before[it].key != after.getOrNull(it)?.key } ?: return
    val shuffled = c.player.shuffle.value
    val from = before.indexOfFirst { it.key == after[i].key }
    if (from > i) {
        // Moved up to i: it now sits before what was there.
        val moved = after[i]; val beside = before[i]
        if (shuffled) c.player.moveInPlayOrder(moved.index, beside.index, after = false) else c.player.move(moved.index, beside.index)
    } else {
        // The row at i moved down to k.
        val moved = before[i]; val k = after.indexOfFirst { it.key == moved.key }
        val beside = before[k]
        if (shuffled) c.player.moveInPlayOrder(moved.index, beside.index, after = true) else c.player.move(moved.index, beside.index)
    }
}

/**
 * Orders the songs after the one playing into a set of [shape]: each is quick-scanned for its key, tempo and loudness
 * (as Data saver and "Measure songs on mobile data" allow), [EnergySets] orders them, and the queue is moved to match.
 * Shuffle is switched off first (the set is the order). Says what it did.
 */
private suspend fun arrangeAsSet(c: AppContainer, shape: SetShape, onProgress: (Int, Int) -> Unit): String {
    if (c.player.shuffle.value) { c.player.toggleShuffle(); delay(500) }
    val queue = c.player.queue.value
    val current = c.player.currentIndex.value
    val playing = queue.getOrNull(current) ?: return "Play something first"
    val upcoming = queue.drop(current + 1).filter { !it.song.isEpisode && !it.song.isLive }.take(60)
    if (upcoming.size < 2) return "Add a few more songs to the queue first"
    val net = !c.dataSaving() && (c.settings.current.mixTempoOnCellular || !c.network.metered.value)
    val all = listOf(playing) + upcoming
    all.forEachIndexed { i, e ->
        onProgress(i, all.size)
        runCatching { c.mixTempo.quickScan(e.song.id, net) }
    }
    fun track(e: com.prism.music.playback.QueueEntry) = com.prism.music.playback.DjTrack(
        e.uid, c.mixTempo.key(e.song.id), c.mixTempo.djBpm(e.song.id), c.mixTempo.djLoudness(e.song.id), e.song.primaryArtist,
    )
    val order = EnergySets.arrange(track(playing), upcoming.map(::track), shape).map { it.id }
    // Moved one at a time into place after the playing song, tracking where each row is as they move.
    val rows = c.player.queue.value.map { it.uid }.toMutableList()
    order.forEachIndexed { k, uid ->
        val from = rows.indexOf(uid)
        val to = current + 1 + k
        if (from >= 0 && from != to) {
            c.player.move(from, to)
            rows.add(to, rows.removeAt(from))
        }
    }
    val measured = all.count { c.mixTempo.djLoudness(it.song.id) != null }
    return "Arranged as a set: ${shape.label}" + if (measured < all.size / 2) " (only $measured of ${all.size} songs could be measured; connect to Wi-Fi for a better set)" else ""
}

@Composable
private fun SetDialog(c: AppContainer, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = { if (working == null) onClose() },
        title = { Text("Arrange queue as a set") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val w = working
                if (w != null) {
                    Text("Measuring songs… ${w.first}/${w.second}", color = VelouraNavy)
                    LinearProgressIndicator({ w.first.toFloat() / w.second.coerceAtLeast(1) }, Modifier.fillMaxWidth().padding(top = 10.dp))
                } else {
                    Text("The songs after this one, ordered by energy and key. AutoMix blends each step.", color = VelouraMuted, fontSize = 13.sp)
                    SetShape.entries.forEach { shape ->
                        Column(
                            Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, VelouraLine, RoundedCornerShape(14.dp))
                                .clickable {
                                    working = 0 to 1
                                    scope.launch {
                                        val said = arrangeAsSet(c, shape) { i, n -> working = i to n }
                                        android.widget.Toast.makeText(context, said, android.widget.Toast.LENGTH_LONG).show()
                                        working = null
                                        onClose()
                                    }
                                }.padding(12.dp),
                        ) {
                            Text(shape.label, color = VelouraNavy, fontWeight = FontWeight.Bold)
                            Text(shape.detail, color = VelouraMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { if (working == null) TextButton(onClose) { Text("Cancel") } },
    )
}

/** A [TransitionStyle] as settings: crossfade is DJ effects off; a DJ style sets them on with its style. */
private fun applyTransitionStyle(c: AppContainer, style: TransitionStyle) {
    c.settings.setDjEffects(style.dj != null)
    style.dj?.let(c.settings::setDjStyle)
}

/** "Save this mix": the session's songs in the order they play (played, playing, still to come), and its style. */
@Composable
private fun SaveMixDialog(c: AppContainer, onClose: () -> Unit) {
    val queue = c.player.queue.collectAsState().value
    val played = c.player.played.collectAsState().value
    val upNext = c.player.upNext.collectAsState().value
    val current = c.player.currentIndex.collectAsState().value
    val settings by c.settings.flow.collectAsState()
    val songs = remember(queue, played, upNext, current) {
        (played + listOfNotNull(current.takeIf { it in queue.indices }) + upNext).mapNotNull { queue.getOrNull(it)?.song }
            .filter { !it.isEpisode && !it.isLive }.distinctBy { it.id }
    }
    var name by remember { mutableStateOf("Mix · " + java.text.SimpleDateFormat("d MMM", java.util.Locale.ENGLISH).format(java.util.Date())) }
    var toYouTube by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Save this mix") },
        text = {
            Column {
                Text("${songs.size} songs in the order they play, mixed ${TransitionStyle.of(settings.sound.djEffects, settings.sound.djStyle).label}. Find it under Library → Mixes.", color = VelouraMuted, fontSize = 13.sp)
                OutlinedTextField(name, { name = it.take(60) }, Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true, label = { Text("Name") })
                Row(Modifier.padding(top = 8.dp).clickable { toYouTube = !toYouTube }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(toYouTube, { toYouTube = it })
                    Text("Also save as a YouTube Music playlist", color = VelouraNavy, fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            TextButton({
                if (songs.isEmpty()) { onClose(); return@TextButton }
                c.savedMixes.save(name, songs, TransitionStyle.of(settings.sound.djEffects, settings.sound.djStyle))
                if (toYouTube) scope.launch {
                    val made = runCatching { withContext(Dispatchers.IO) { c.ytm.createPlaylist(name, VelouraPlaylists.saveableIds(songs), "A Veloura mix") } }.getOrNull()
                    if (made != null) c.library.playlistChanged(made)
                }
                android.widget.Toast.makeText(context, "Saved $name", android.widget.Toast.LENGTH_SHORT).show()
                onClose()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClose) { Text("Cancel") } },
    )
}

/** The party queue's host page: start or end it, the QR code, and what guests added. */
@Composable
private fun PartyScreen(c: AppContainer, onBack: () -> Unit) {
    val link by c.partyQueue.link.collectAsState()
    val added by c.partyQueue.added.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    VelouraParty(
        VelouraPartyUi(
            link, added.map { PartyAddedUi("${it.at}|${it.song.id}", it.song.title, it.song.artistText, it.song.thumbnail, it.guest) }, message,
        ),
        onBack = onBack,
        onStart = {
            autoMixOn(c)
            message = if (c.partyQueue.start()) null else "Connect to Wi-Fi (or turn on your hotspot) to start a party."
        },
        onStop = { c.partyQueue.stop(); message = "The party's over. Guests can't add songs now." },
        onRemove = { row ->
            added.firstOrNull { "${it.at}|${it.song.id}" == row.key }?.let { a ->
                // Taken out of what's still to come (not the song playing, nor one already played).
                val now = c.player.currentIndex.value
                c.player.queue.value.lastOrNull { it.song.id == a.song.id && it.index > now }?.let { c.player.removeAt(it.index) }
                c.partyQueue.forget(a)
            }
        },
        onBlock = { row ->
            added.firstOrNull { "${it.at}|${it.song.id}" == row.key }?.let { c.partyQueue.block(it.from); message = "That phone can't add songs now." }
        },
    )
}

/** "Share this moment": the story card, and Share. */
@Composable
private fun StoryDialog(c: AppContainer, onClose: () -> Unit) {
    val song = c.player.currentSong.collectAsState().value
    if (song == null) { onClose(); return }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val lyricsState = com.prism.music.ui.player.rememberLyricsState(song)
    // The line being sung now (a sung line, not an instrumental gap); none without timed lyrics.
    val at = remember(song.id) { c.player.position }
    val line = lyricsState.lines.filter { it.timeMs >= 0 && !it.isGap && it.text.isNotBlank() }.lastOrNull { it.timeMs <= at }?.text
    androidx.compose.ui.window.Dialog(onClose) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            VelouraStoryCard(
                song.title, song.artistText, song.thumbnail, line,
                Modifier.width(270.dp).recordInto(layer),
            )
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClose, colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)) { Text("Close") }
                Button({
                    scope.launch {
                        com.prism.music.ui.components.shareLayer(
                            context, layer, "veloura-moment.png",
                            "${song.title} · ${song.artistText} on Veloura https://music.youtube.com/watch?v=${song.id}",
                            "Share this moment",
                        )
                    }
                }) { Text("Share") }
            }
        }
    }
}

/** "More like this": songs like the one playing (when the screen opened), from all of YouTube Music. */
@Composable
private fun SimilarScreen(c: AppContainer, onBack: () -> Unit) {
    val playing by c.player.currentSong.collectAsState()
    // The song it was opened for stays the subject, even as the music moves on.
    val seed = remember { c.player.currentSong.value }
    if (seed == null) {
        Column(Modifier.fillMaxSize()) {
            VelouraTitleBar("More like this", PrismIcons.ArrowBack, "Back", onBack)
            Text("Play something first, and Veloura will find songs like it.", Modifier.padding(24.dp), color = VelouraMuted)
        }
        return
    }
    var progress by remember { mutableStateOf<com.prism.music.SimilarSongs.Progress?>(com.prism.music.SimilarSongs.Progress("Starting…")) }
    var result by remember { mutableStateOf<com.prism.music.SimilarSongs.Result?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(seed.id) {
        val queued = c.player.queue.value.map { it.song.id }.toSet()
        result = runCatching { c.similarSongs.find(seed, queued) { progress = it } }.getOrElse {
            message = "Couldn't reach YouTube Music: ${it.message ?: "try again"}"; null
        }
        progress = null
        if (result?.picks?.isEmpty() == true) message = "YouTube Music didn't suggest any songs like this one."
    }
    val r = result
    val lang = r?.seedLanguage
    VelouraSimilar(
        VelouraSimilarUi(
            seedTitle = seed.title, seedArtist = seed.artistText, seedThumbnail = seed.thumbnail,
            seedLanguage = lang?.label ?: "Reading…",
            showing = when (lang?.group) {
                null -> "Finding…"
                LanguageGroup.DESI -> "Punjabi & Hindi songs"
                LanguageGroup.ENGLISH -> "English songs"
                LanguageGroup.OTHER -> "Songs in its language"
                LanguageGroup.UNKNOWN -> "Songs in any language"
            },
            progress = progress?.let { p -> if (p.total > 0) "${p.stage} ${p.done}/${p.total}" else p.stage },
            progressFraction = progress?.takeIf { it.total > 0 }?.let { it.done.toFloat() / it.total },
            rows = r?.picks.orEmpty().map { p ->
                SimilarRowUi(
                    p.song.id, p.song.title, p.song.artistText, p.song.thumbnail, p.language.label,
                    p.bpm?.let { "${Math.round(it)} BPM" }, p.key, p.match, p.why,
                )
            },
            message = message,
        ),
        onBack = onBack,
        onPlay = { row ->
            r?.picks?.firstOrNull { it.song.id == row.key }?.let { p ->
                // Next, so AutoMix mixes into it as it would any Next.
                c.player.playNext(p.song)
                if (playing != null) c.player.next()
                message = "Mixing into ${p.song.title}…"
            }
        },
        onAdd = { row ->
            r?.picks?.firstOrNull { it.song.id == row.key }?.let { p -> c.player.addToQueue(p.song); message = "Added ${p.song.title} to the queue" }
        },
        onAddAll = {
            r?.picks?.map { it.song }?.takeIf { it.isNotEmpty() }?.let { songs ->
                c.player.playNext(songs, null)
                message = "${songs.size} songs queued next"
            }
        },
    )
}

@Composable
private fun ActionCard(title: String, detail: String, action: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, color = VelouraNavy, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(detail, color = VelouraMuted, modifier = Modifier.padding(top = 5.dp))
            Button(onClick, Modifier.padding(top = 13.dp)) { Text(action) }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Search(c: AppContainer, onPlayed: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }

    LaunchedEffect(submitted) {
        if (submitted.isBlank()) return@LaunchedEffect
        loading = true; error = null
        runCatching { withContext(Dispatchers.IO) { c.ytm.search(submitted, SearchFilter.SONGS).items.filterIsInstance<SongItem>().map { it.song }.distinctBy { it.id } } }
            .onSuccess { results = it; if (it.isEmpty()) error = "No songs found. Try a different search." }
            .onFailure { error = it.message ?: "Could not reach YouTube Music" }
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true, label = { Text("Song, artist or playlist") })
            Button({ submitted = query.trim() }, enabled = query.isNotBlank() && !loading, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Icon(PrismIcons.Search, "Search")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 10.dp)) }
        if (submitted.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Search YouTube Music, then tap a song to play it.", color = VelouraMuted) }
        } else {
            LazyColumn(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(results, key = { _, song -> song.id }) { index, song ->
                    SongRow(song, clock(song.durationSec * 1000L)) {
                        c.player.playQueue(results, index, QueueSource(QueueKind.OTHER, "Search: $submitted")); onPlayed()
                    }
                }
            }
        }
    }
}

/** How Veloura blends songs: Intelligent AutoMix on or off, and how long each crossfade lasts. */
@Composable
private fun MixSettings(c: AppContainer) {
    val settings by c.settings.flow.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    VelouraMixSettings(
        VelouraMixSettingsUi(
            autoMix = settings.intelligentAutoMix,
            style = TransitionStyle.of(settings.sound.djEffects, settings.sound.djStyle),
            automaticLength = settings.mixAuto,
            seconds = settings.mixSeconds.coerceIn(1, CROSSFADE_SLIDER_MAX),
            maxSeconds = CROSSFADE_SLIDER_MAX,
            keepMixGoing = settings.sound.keepMixGoing,
            hookMode = settings.sound.hookMode,
            languageMix = settings.sound.languageMix,
            tempoLock = settings.sound.tempoLock,
            moodAuto = settings.sound.moodAuto,
        ),
        onAutoMix = { autoMixOn(c, it) },
        onStyle = { style ->
            c.settings.setDjEffects(style.dj != null)
            style.dj?.let(c.settings::setDjStyle)
        },
        onAutomaticLength = { c.settings.setMixAuto(it) },
        onSeconds = { c.settings.setMixSeconds(it) },
        onKeepMixGoing = { c.settings.setKeepMixGoing(it) },
        onHookMode = { c.settings.setHookMode(it) },
        onLanguageMix = { c.settings.setLanguageMix(it) },
        onTempoLock = { c.settings.setTempoLock(it) },
        onMoodAuto = { c.settings.setMoodAuto(it) },
        onWeddingMode = {
            autoMixOn(c)
            c.settings.setDjEffects(true)
            c.settings.setDjStyle(com.prism.music.data.prefs.DjStyle.CLUB)
            c.settings.setMixAuto(true)
            c.settings.setHookMode(true)
            c.settings.setLanguageMix(85)
            c.settings.setKeepMixGoing(true)
            android.widget.Toast.makeText(context, "Wedding mode on: DJ · Club, hooks, 85% Punjabi & Hindi. Arrange your queue as a set to build the energy.", android.widget.Toast.LENGTH_LONG).show()
        },
    )
}

private const val CROSSFADE_SLIDER_MAX = 12
/** How close a planned blend must be before the DJ tab counts down to it. */
private const val MIX_SOON_MS = 6_000L

@Composable
private fun Account(c: AppContainer, onConnect: () -> Unit, onBack: () -> Unit) {
    val settings by c.settings.flow.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            settings.accountAvatar.takeIf { it.isNotBlank() }, null,
            Modifier.size(92.dp).clip(CircleShape).background(Color(0xFFDCEEFF)),
        )
        Text(
            if (settings.isLoggedIn) settings.accountName.ifBlank { "YouTube Music connected" } else "Connect YouTube Music",
            color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 24.sp, modifier = Modifier.padding(top = 18.dp),
        )
        if (settings.isLoggedIn) {
            Text(settings.accountEmail, color = VelouraMuted, modifier = Modifier.padding(top = 5.dp))
            Card(
                Modifier.fillMaxWidth().padding(top = 22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Your music is linked", color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text("Veloura can use your YouTube Music likes, playlists, recommendations and playback session.", color = VelouraMuted, modifier = Modifier.padding(top = 6.dp))
                    Button({ c.library.syncInBackground() }, Modifier.padding(top = 14.dp)) { Text("Sync library now") }
                }
            }
        } else {
            Text(
                "Sign in on Google’s own page. Veloura stores only the resulting YouTube Music session on this phone and never receives your password.",
                color = VelouraMuted, modifier = Modifier.padding(top = 10.dp),
            )
            Button(onConnect, Modifier.fillMaxWidth().padding(top = 22.dp)) { Text("Connect YouTube Music") }
        }
        TextButton(onBack, Modifier.padding(top = 10.dp)) { Text("Back to Home") }
        // Veloura is built on Prism (GPL-3.0): the credit stays visible, and the license says how the source is shared.
        Text(
            "Veloura is built on Prism by Yigder, free software under the GNU GPL v3 or later.",
            color = VelouraMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 18.dp),
        )
    }
}

/** What the Library tab has fetched: Home's shelves (for the mixes) and whether the library was refreshed. */
private class LibraryCache {
    val home = mutableStateOf<List<com.prism.music.data.model.Shelf>?>(null)
    var refreshed = false
}

@Composable
private fun VelouraLibrary(c: AppContainer, cache: LibraryCache, onConnect: () -> Unit, onPlayed: () -> Unit) {
    val settings by c.settings.flow.collectAsState()
    val liked by c.library.likedShown.collectAsState()
    val library by c.library.playlists.collectAsState()
    val scope = rememberCoroutineScope()
    // Playlists, saved mixes, liked songs.
    var libTab by remember { mutableIntStateOf(0) }
    val showLiked = libTab == 2
    val savedMixes by c.savedMixes.mixes.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    if (!settings.isLoggedIn) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            ActionCard(
                "Connect your YouTube Music library",
                "Your playlists, YouTube Music's mixes for you and your liked songs will appear here after you connect.",
                "Connect YouTube Music",
                onConnect,
            )
        }
        return
    }
    // The account's playlists are synced by the library; Home supplies the auto-generated mixes and suggestions.
    LaunchedEffect(Unit) {
        if (!cache.refreshed) { cache.refreshed = true; c.library.refreshCollections() }
        if (cache.home.value == null) {
            cache.home.value = withContext(Dispatchers.IO) { runCatching { c.ytm.fullHome(pages = 3) }.getOrDefault(emptyList()) }
        }
    }
    val home by cache.home
    val yours = VelouraPlaylists.accountPlaylists(library)
    val offered = home?.let { VelouraPlaylists.suggestions(it, library) }

    fun play(item: com.prism.music.data.model.PlaylistItem) {
        if (busy != null) return
        busy = item.id
        message = "Loading ${item.title}…"
        scope.launch {
            val songs = runCatching { VelouraPlaylists.songs(c, item) }.getOrNull().orEmpty()
                .filter { !it.isEpisode && !it.isLive }
            busy = null
            if (songs.isEmpty()) { message = "Couldn't load ${item.title}"; return@launch }
            message = null
            val kind = if (VelouraPlaylists.isMix(item)) QueueKind.RADIO else QueueKind.PLAYLIST
            c.player.playQueue(songs, 0, QueueSource(kind, item.title, originId = VelouraPlaylists.normal(item.id)))
            onPlayed()
        }
    }

    fun save(item: com.prism.music.data.model.PlaylistItem) {
        if (busy != null) return
        busy = item.id
        message = "Saving ${item.title} to your account…"
        scope.launch {
            message = when (val r = VelouraPlaylists.saveToAccount(c, item)) {
                VelouraPlaylists.SaveResult.Followed -> "Saved ${item.title} to your YouTube Music library"
                is VelouraPlaylists.SaveResult.Copied -> "Saved \"${r.title}\" (${r.songs} songs) to your YouTube Music playlists"
                is VelouraPlaylists.SaveResult.Failed -> "Couldn't save ${item.title}: ${r.reason}"
            }
            busy = null
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFFE9F2FE)).padding(4.dp)) {
                SegmentButton("Playlists", libTab == 0, Modifier.weight(1f)) { libTab = 0 }
                SegmentButton("Mixes", libTab == 1, Modifier.weight(1f)) { libTab = 1 }
                SegmentButton("Liked", libTab == 2, Modifier.weight(1f)) { libTab = 2 }
            }
        }
        message?.let { text ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF6FF)), shape = RoundedCornerShape(16.dp)) {
                    Text(text, Modifier.fillMaxWidth().padding(12.dp), color = VelouraNavy, fontSize = 13.sp)
                }
            }
        }
        if (libTab == 1) {
            item { SectionTitle("Saved mixes", "Sessions you saved with ⋮ → Save this mix in Now Playing") }
            if (savedMixes.isEmpty()) item {
                Text("No saved mixes yet. While a mix is playing, open Now Playing's ⋮ menu and choose Save this mix.", color = VelouraMuted, fontSize = 13.sp)
            }
            itemsIndexed(savedMixes, key = { _, m -> m.id }) { _, m ->
                val style = runCatching { TransitionStyle.valueOf(m.style) }.getOrDefault(TransitionStyle.DJ_AUTO)
                SavedMixRow(
                    SavedMixUi(
                        m.id, m.name,
                        "${m.songs.size} songs · ${style.label} · " + java.text.SimpleDateFormat("d MMM", java.util.Locale.ENGLISH).format(java.util.Date(m.savedAt)),
                        m.songs.map { it.thumbnail },
                    ),
                    onPlay = {
                        // As it was mixed: its transition style, AutoMix on, the songs in their order.
                        autoMixOn(c)
                        c.settings.setDjEffects(style.dj != null)
                        style.dj?.let(c.settings::setDjStyle)
                        c.player.playQueue(m.songs, 0, QueueSource(QueueKind.PLAYLIST, m.name))
                        onPlayed()
                    },
                    onToYouTube = {
                        scope.launch {
                            val ids = VelouraPlaylists.saveableIds(m.songs)
                            val made = runCatching { withContext(Dispatchers.IO) { c.ytm.createPlaylist(m.name, ids, "A Veloura mix") } }.getOrNull()
                            if (made != null) c.library.playlistChanged(made)
                            android.widget.Toast.makeText(context, if (made != null) "Saved ${m.name} to YouTube Music" else "YouTube Music didn't save it", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDelete = { c.savedMixes.delete(m.id) },
                )
            }
        } else if (showLiked) {
            if (liked.isEmpty()) item {
                ActionCard("Your liked songs are syncing", "This can take a moment after the first connection.", "Sync now") {
                    c.library.syncInBackground()
                }
            } else {
                item {
                    Text("Liked songs", color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 25.sp)
                    Text("${liked.size} songs from YouTube Music", color = VelouraMuted, modifier = Modifier.padding(top = 3.dp, bottom = 8.dp))
                }
                itemsIndexed(liked, key = { _, song -> song.id }) { index, song ->
                    SongRow(song, clock(song.durationSec * 1000L)) {
                        c.player.playQueue(liked, index, QueueSource(QueueKind.LIBRARY, "Liked songs")); onPlayed()
                    }
                }
            }
        } else {
            item { SectionTitle("Your playlists", "Saved in your YouTube Music account") }
            if (yours.isEmpty()) item {
                Text("No playlists in your account yet. Save one below, or pull them in with Sync.", color = VelouraMuted, fontSize = 13.sp)
                TextButton({ c.library.syncInBackground() }) { Text("Sync now") }
            }
            itemsIndexed(yours, key = { _, p -> "own:" + p.id }) { _, p ->
                PlaylistRow(p, busy == p.id, action = null) { play(p) }
            }
            item { SectionTitle("Mixed for you", "YouTube Music's auto-generated mixes and suggested playlists") }
            when {
                offered == null -> item { Text("Loading your mixes…", color = VelouraMuted, fontSize = 13.sp) }
                offered.isEmpty() -> item { Text("YouTube Music didn't offer any mixes right now.", color = VelouraMuted, fontSize = 13.sp) }
                else -> itemsIndexed(offered, key = { _, p -> "offer:" + p.id }) { _, p ->
                    PlaylistRow(p, busy == p.id, action = (if (VelouraPlaylists.isMix(p)) "Save copy" else "Save") to { save(p) }) { play(p) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, detail: String) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(title, color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 21.sp)
        Text(detail, color = VelouraMuted, fontSize = 12.sp)
    }
}

@Composable
private fun PlaylistRow(
    item: com.prism.music.data.model.PlaylistItem,
    busy: Boolean,
    action: Pair<String, () -> Unit>?,
    onClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(item.thumbnail, null, Modifier.size(62.dp).clip(RoundedCornerShape(14.dp)))
            Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                Text(item.title, color = VelouraNavy, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (VelouraPlaylists.isMix(item)) "Mix · ${item.subtitle}" else item.subtitle,
                    color = VelouraMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                busy -> CircularProgressIndicator(Modifier.size(22.dp), color = VelouraBlue, strokeWidth = 2.dp)
                action != null -> OutlinedButton(action.second, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Text(action.first, color = VelouraBlue, fontSize = 12.sp)
                }
                else -> Icon(PrismIcons.Play, "Play", tint = VelouraBlue)
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, trailing: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(song.thumbnail, null, Modifier.size(62.dp).clip(RoundedCornerShape(14.dp)))
            Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                Text(song.title, color = VelouraNavy, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artistText, color = VelouraMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(trailing, color = VelouraBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SegmentButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) VelouraBlue else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 10.dp), contentAlignment = Alignment.Center,
    ) { Text(label, color = if (selected) Color.White else VelouraNavy, fontWeight = FontWeight.Bold) }
}


private suspend fun playYouTubeLink(c: AppContainer, uri: Uri): String? {
    val normalized = ShareLinks.findIn(uri.toString())?.let(Uri::parse) ?: uri
    val id = when (normalized.host?.lowercase()) {
        "youtu.be" -> normalized.pathSegments.firstOrNull()
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" -> normalized.getQueryParameter("v")
            ?: normalized.pathSegments.dropWhile { it != "shorts" }.drop(1).firstOrNull()
        else -> normalized.getQueryParameter("v")
    }
    if (id.isNullOrBlank()) return "That link does not contain a playable YouTube song."
    val songs = runCatching { withContext(Dispatchers.IO) { c.ytm.next(id).songs } }.getOrElse {
        return it.message ?: "Could not open that YouTube link"
    }
    if (songs.isEmpty()) return "YouTube Music did not return a playable song for that link."
    val at = songs.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: 0
    c.player.playQueue(songs, at, QueueSource(QueueKind.RADIO, "YouTube link", seedId = id))
    return null
}

private fun clock(ms: Long): String {
    val all = (ms.coerceAtLeast(0) / 1000).toInt()
    return "%d:%02d".format(all / 60, all % 60)
}

/** What the planner actually aligned the transition to. Beat matching itself is decided later by MixEngine. */
private fun com.prism.music.playback.AutoMixBoundary.label(): String = when (this) {
    com.prism.music.playback.AutoMixBoundary.PHRASE -> "Phrase-aligned"
    com.prism.music.playback.AutoMixBoundary.BEAT -> "Beat-aligned"
    com.prism.music.playback.AutoMixBoundary.ESTIMATED -> "Best-guess point"
}
