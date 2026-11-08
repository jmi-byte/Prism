package com.prism.music

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin

private val VelouraBlue = Color(0xFF087DFF)
private val VelouraCyan = Color(0xFF12CFE4)
private val VelouraOrange = Color(0xFFFF8A34)
private val VelouraNavy = Color(0xFF071E41)
private val VelouraBackground = Color(0xFFF4F9FF)
private val VelouraMuted = Color(0xFF63758B)

private val VelouraColors = lightColorScheme(
    primary = VelouraBlue, onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEEFF), onPrimaryContainer = VelouraNavy,
    secondary = VelouraCyan, tertiary = VelouraOrange,
    background = VelouraBackground, surface = Color.White,
    surfaceVariant = Color(0xFFEAF4FF), onSurface = VelouraNavy, onSurfaceVariant = VelouraMuted,
)

private enum class VelouraTab { HOME, SEARCH, LIBRARY, DJ, ACCOUNT }
private sealed interface DiscoveryFeed {
    data object Loading : DiscoveryFeed
    data class Ready(val songs: List<Song>) : DiscoveryFeed
    data class Error(val message: String) : DiscoveryFeed
}

/** Veloura's UI. Prism's playback and YouTube Music engines remain underneath. */
@Composable
fun AutoMixDjRoot(c: AppContainer, deepLink: StateFlow<Uri?>) {
    var tab by remember { mutableStateOf(VelouraTab.HOME) }
    var showLogin by remember { mutableStateOf(false) }
    val link by deepLink.collectAsState()
    var linkMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(link) {
        val uri = link ?: return@LaunchedEffect
        linkMessage = "Opening YouTube Music link…"
        val error = playYouTubeLink(c, uri)
        linkMessage = error
        if (error == null) tab = VelouraTab.DJ
    }

    MaterialTheme(colorScheme = VelouraColors) {
        if (showLogin) {
            com.prism.music.ui.screens.LoginScreen(
                onDone = { showLogin = false; tab = VelouraTab.HOME },
                onBack = { showLogin = false },
            )
            return@MaterialTheme
        }
        Surface(Modifier.fillMaxSize(), color = VelouraBackground) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    when (tab) {
                        VelouraTab.HOME -> BrandHeader(linkMessage) { tab = VelouraTab.ACCOUNT }
                        VelouraTab.SEARCH -> PageHeader("Search YouTube Music")
                        VelouraTab.LIBRARY -> PageHeader("Your Music")
                        VelouraTab.DJ -> PageHeader("Now Playing")
                        VelouraTab.ACCOUNT -> PageHeader("YouTube Music Account")
                    }
                },
                bottomBar = {
                    Row(
                        Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                    ) {
                        NavItem(tab == VelouraTab.HOME, "Home", PrismIcons.Home, Modifier.weight(1f)) { tab = VelouraTab.HOME }
                        NavItem(tab == VelouraTab.SEARCH, "Search", PrismIcons.Search, Modifier.weight(1f)) { tab = VelouraTab.SEARCH }
                        NavItem(tab == VelouraTab.LIBRARY, "Library", PrismIcons.Library, Modifier.weight(1f)) { tab = VelouraTab.LIBRARY }
                        NavItem(tab == VelouraTab.DJ, "DJ", PrismIcons.Turntable, Modifier.weight(1f)) { tab = VelouraTab.DJ }
                    }
                },
            ) { padding ->
                Box(
                    Modifier.fillMaxSize().padding(padding).background(
                        Brush.verticalGradient(listOf(Color(0xFFEAF6FF), Color.White, VelouraBackground)),
                    ),
                ) {
                    when (tab) {
                        VelouraTab.HOME -> Discover(c, { tab = VelouraTab.SEARCH }, { tab = VelouraTab.DJ })
                        VelouraTab.SEARCH -> Search(c) { tab = VelouraTab.DJ }
                        VelouraTab.LIBRARY -> VelouraLibrary(c, onConnect = { showLogin = true }, onPlayed = { tab = VelouraTab.DJ })
                        VelouraTab.DJ -> NowPlaying(c) { tab = VelouraTab.SEARCH }
                        VelouraTab.ACCOUNT -> Account(c, onConnect = { showLogin = true }, onBack = { tab = VelouraTab.HOME })
                    }
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.background(if (selected) Color(0xFFDCEEFF) else Color.Transparent, CircleShape)
                .padding(horizontal = 18.dp, vertical = 5.dp),
        ) {
            Icon(icon, label, tint = if (selected) VelouraBlue else VelouraMuted, modifier = Modifier.size(22.dp))
        }
        Text(
            label,
            color = if (selected) VelouraBlue else VelouraMuted,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun BrandHeader(message: String?, onAccount: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFCFFFFFF)).statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(44.dp)
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text("Veloura", color = VelouraNavy, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 24.sp)
            Text(message ?: "Music that never stops moving", color = VelouraMuted, fontSize = 12.sp, maxLines = 1)
        }
        IconButton(onClick = onAccount) { Icon(PrismIcons.Person, "YouTube Music account", tint = VelouraNavy) }
    }
}

@Composable
private fun PageHeader(title: String) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFCFFFFFF)).statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(30.dp)
        Text(title, Modifier.padding(start = 12.dp), color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 20.sp)
    }
}

@Composable
private fun BrandMark(size: androidx.compose.ui.unit.Dp) {
    Icon(
        painterResource(R.drawable.ic_veloura_crossfade_v), "Veloura",
        Modifier.size(size).clip(RoundedCornerShape(size / 3)), tint = Color.Unspecified,
    )
}

@Composable
private fun Discover(c: AppContainer, onSearch: () -> Unit, onNowPlaying: () -> Unit) {
    val current by c.player.currentSong.collectAsState()
    val settings by c.settings.flow.collectAsState()
    val feed by produceState<DiscoveryFeed>(DiscoveryFeed.Loading) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                (c.ytm.explore() + c.ytm.home().shelves).flatMap { it.items }
                    .filterIsInstance<SongItem>().map { it.song }
                    .filter { !it.isEpisode && !it.isLive }.distinctBy { it.id }.take(12)
            }.fold(
                { if (it.isEmpty()) DiscoveryFeed.Error("YouTube Music returned no songs. Try Search.") else DiscoveryFeed.Ready(it) },
                { DiscoveryFeed.Error(it.message ?: "Could not reach YouTube Music") },
            )
        }
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Text("Discover Music", color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 28.sp)
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(18.dp)).background(Color.White)
                    .border(1.dp, Color(0xFFD9E7F5), RoundedCornerShape(18.dp)).clickable(onClick = onSearch).padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PrismIcons.Search, null, tint = VelouraMuted)
                Text("Search songs, artists, playlists", Modifier.padding(start = 11.dp), color = VelouraMuted)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeatureTile("AutoMix\nParty", PrismIcons.FlameFilled, listOf(Color(0xFF8C55E8), Color(0xFFB54DE1)), Modifier.weight(1f)) {
                    c.settings.setIntelligentAutoMix(true); c.settings.setMix(false); c.dj.setOn(false)
                    if (current == null) onSearch() else onNowPlaying()
                }
                FeatureTile("DJ\nMixes", PrismIcons.Headphones, listOf(Color(0xFF127CFF), Color(0xFF14B6F0)), Modifier.weight(1f)) {
                    c.dj.setOn(true); c.settings.setIntelligentAutoMix(false)
                    if (current == null) onSearch() else onNowPlaying()
                }
                FeatureTile("Genres", PrismIcons.Mood, listOf(Color(0xFF16C8A8), Color(0xFF11B8DA)), Modifier.weight(1f), onSearch)
                FeatureTile("For You", PrismIcons.StarFilled, listOf(Color(0xFFFF9A32), Color(0xFFFF6D3A)), Modifier.weight(1f), onSearch)
            }
        }
        if (!settings.isLoggedIn) item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF6FF)), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("Connect YouTube Music", color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 19.sp)
                    Text("Bring in your liked songs, playlists and recommendations. Veloura never sees your Google password.", color = VelouraMuted, modifier = Modifier.padding(top = 5.dp))
                    Text("Open the account button above to connect.", color = VelouraBlue, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
        item { Text("Trending now", color = VelouraNavy, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp) }
        when (val result = feed) {
            DiscoveryFeed.Loading -> item { Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            is DiscoveryFeed.Error -> item { ActionCard("Couldn’t load Discover", result.message, "Open Search", onSearch) }
            is DiscoveryFeed.Ready -> itemsIndexed(result.songs.take(6), key = { _, song -> song.id }) { index, song ->
                SongRow(song, clock(song.durationSec * 1000L)) {
                    c.player.playQueue(result.songs, index, QueueSource(QueueKind.OTHER, "Veloura Discover")); onNowPlaying()
                }
            }
        }
        item {
            Text("Quick Mixes", color = VelouraNavy, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Card(
                Modifier.fillMaxWidth().padding(top = 9.dp).clickable {
                    val on = !settings.intelligentAutoMix
                    c.settings.setIntelligentAutoMix(on)
                    if (on) { c.settings.setMix(false); c.dj.setOn(false) }
                    if (current == null) onSearch() else onNowPlaying()
                }, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    WaveBadge()
                    Column(Modifier.padding(horizontal = 13.dp).weight(1f)) {
                        Text("Intelligent AutoMix", color = VelouraNavy, fontWeight = FontWeight.ExtraBold)
                        Text("Real songs · natural, non-stop mixing", color = VelouraMuted, fontSize = 12.sp)
                    }
                    Box(Modifier.size(42.dp).clip(CircleShape).background(VelouraBlue), contentAlignment = Alignment.Center) {
                        Icon(PrismIcons.Play, null, tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureTile(label: String, icon: ImageVector, colors: List<Color>, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(104.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(colors)).clickable(onClick = onClick).padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(25.dp))
        Text(label, color = Color.White, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WaveBadge() {
    Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFFE9F5FF)), contentAlignment = Alignment.Center) {
        Icon(PrismIcons.Equalizer, null, tint = VelouraBlue, modifier = Modifier.size(25.dp))
    }
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

/**
 * Veloura's DJ workspace inside Prism's complete navigation graph. Home, Search, Library, account connection,
 * settings, lyrics and the full player remain Prism's real screens; this is an additional AutoMix destination.
 */
@Composable
fun VelouraDjScreen(c: AppContainer, bottomPadding: androidx.compose.ui.unit.Dp, onFindMusic: () -> Unit) {
    var plan by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(VelouraBackground)) {
        PageHeader(if (plan) "Queue & Transition Plan" else "Intelligent AutoMix")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(24.dp)).background(Color(0xFFE9F2FE)).padding(4.dp),
        ) {
            SegmentButton("Now Playing", !plan, Modifier.weight(1f)) { plan = false }
            SegmentButton("AutoMix Plan", plan, Modifier.weight(1f)) { plan = true }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = bottomPadding)) {
            if (plan) QueuePlan(c) { plan = false } else NowPlaying(c, onFindMusic)
        }
    }
}

@Composable
private fun NowPlaying(c: AppContainer, onFindMusic: () -> Unit) {
    val song by c.player.currentSong.collectAsState()
    val playing by c.player.isPlaying.collectAsState()
    val settings by c.settings.flow.collectAsState()
    val decision by c.intelligentAutoMixShadow.decision.collectAsState()
    val queue by c.player.queue.collectAsState()
    val upNext by c.player.upNext.collectAsState()
    var position by remember(song?.id) { mutableLongStateOf(c.player.position) }
    LaunchedEffect(song?.id, playing) { while (true) { position = c.player.position; delay(if (playing) 250 else 700) } }

    val next = upNext.firstOrNull()?.let(queue::getOrNull)?.song
    val exactDecision = decision?.takeIf { it.songId == song?.id && it.nextSongId == next?.id }
    val plan = exactDecision?.plan
    val totalDuration = c.player.duration.coerceAtLeast(1)
    val autoStatus = when {
        !settings.intelligentAutoMix -> "Off — tap to turn on"
        plan != null -> "Next mix in ~${clock((plan.transitionAtMs - position).coerceAtLeast(0))} · ${plan.boundary.label()} · ${if (plan.estimated) "Longer blend" else "Short blend"}"
        next == null -> "Add another song for a transition"
        exactDecision != null -> "Analysis complete — no safe early cue for this pair"
        else -> "Analysing the current and next song…"
    }

    if (song == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            ActionCard("Choose music to start AutoMix", "Pick music from your personalised Home, Search or Library. The player and transition plan will populate automatically.", "Find music", onFindMusic)
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                AsyncImage(song!!.thumbnail, null, Modifier.fillMaxWidth(0.72f).aspectRatio(1f).clip(RoundedCornerShape(26.dp)))
                Spacer(Modifier.height(17.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(song!!.title, color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(song!!.artistText, color = VelouraMuted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(PrismIcons.Heart, "Like", tint = VelouraNavy, modifier = Modifier.size(28.dp))
                }
                Slider(position.coerceIn(0, totalDuration).toFloat(), { c.player.seekTo(it.toLong()) }, valueRange = 0f..totalDuration.toFloat(), modifier = Modifier.padding(top = 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(clock(position), color = VelouraMuted, fontSize = 12.sp)
                    Text(clock(totalDuration), color = VelouraMuted, fontSize = 12.sp)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val on = !settings.intelligentAutoMix
                        c.settings.setIntelligentAutoMix(on)
                        if (on) { c.settings.setMix(false); c.dj.setOn(false) }
                    }.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    WaveBadge()
                    Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                        Text("Intelligent AutoMix", color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 17.sp)
                        Text(autoStatus, color = VelouraBlue, fontSize = 12.sp)
                    }
                    Switch(settings.intelligentAutoMix, {
                        c.settings.setIntelligentAutoMix(it)
                        if (it) { c.settings.setMix(false); c.dj.setOn(false) }
                    })
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton({ c.player.previous() }) { Icon(PrismIcons.Previous, "Previous", Modifier.size(31.dp), tint = VelouraNavy) }
                IconButton(
                    { c.player.togglePlay() }, Modifier.size(72.dp).clip(CircleShape).background(Brush.linearGradient(listOf(VelouraBlue, Color(0xFF1265E8)))),
                ) { Icon(if (playing) PrismIcons.Pause else PrismIcons.Play, if (playing) "Pause" else "Play", Modifier.size(34.dp), tint = Color.White) }
                IconButton({ c.player.next() }) { Icon(PrismIcons.Next, "Next", Modifier.size(31.dp), tint = VelouraNavy) }
            }
        }
        item {
            Waveform(
                progress = (position.toFloat() / totalDuration).coerceIn(0f, 1f),
                transitionAt = plan?.let { (it.transitionAtMs.toFloat() / totalDuration).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(58.dp),
            )
        }
        next?.let { following -> item { SongRow(following, plan?.entryAtMs?.let { "Cue ${clock(it)}" } ?: "Up next") { c.player.next() } } }
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
    }
}

@Composable
private fun VelouraLibrary(c: AppContainer, onConnect: () -> Unit, onPlayed: () -> Unit) {
    val settings by c.settings.flow.collectAsState()
    val liked by c.library.likedShown.collectAsState()
    if (!settings.isLoggedIn) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            ActionCard(
                "Connect your YouTube Music library",
                "Your liked songs and account library will appear here after you connect.",
                "Connect YouTube Music",
                onConnect,
            )
        }
        return
    }
    if (liked.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            ActionCard("Your liked songs are syncing", "This can take a moment after the first connection.", "Sync now") {
                c.library.syncInBackground()
            }
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
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
private fun QueuePlan(c: AppContainer, onPicked: () -> Unit) {
    val queue by c.player.queue.collectAsState()
    val currentIndex by c.player.currentIndex.collectAsState()
    val decision by c.intelligentAutoMixShadow.decision.collectAsState()
    // Play order, not list order: with shuffle on, the next song is not necessarily the row below the current one.
    // This is the same order PlaybackService plans for, so the card and the actual transition always agree.
    val upNext by c.player.upNext.collectAsState()
    val current = queue.getOrNull(currentIndex)?.song
    val coming = upNext.mapNotNull(queue::getOrNull)
    val next = coming.firstOrNull()?.song
    val exactDecision = decision?.takeIf { it.songId == current?.id && it.nextSongId == next?.id }
    val plan = exactDecision?.plan
    var showPlan by remember { mutableStateOf(true) }

    if (queue.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Your queue is empty", color = VelouraNavy, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Choose a song from Home, Search or Library. Veloura will build the transition plan here.", color = VelouraMuted, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFFE9F2FE)).padding(4.dp)) {
                SegmentButton("Up Next", !showPlan, Modifier.weight(1f)) { showPlan = false }
                SegmentButton("AutoMix Plan", showPlan, Modifier.weight(1f)) { showPlan = true }
            }
        }
        current?.let { playing -> item { CurrentPlanCard(playing, plan?.transitionAtMs, analysed = exactDecision != null) } }
        if (showPlan && next != null) item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF6FF)), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp)) {
                    Text(plan?.let { "→ Transition to (${it.boundary.label()})" } ?: "→ Transition to", color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(next.thumbnail, null, Modifier.size(60.dp).clip(RoundedCornerShape(13.dp)))
                        Column(Modifier.padding(horizontal = 11.dp).weight(1f)) {
                            Text(next.title, color = VelouraNavy, fontWeight = FontWeight.Black, maxLines = 1)
                            Text(next.artistText, color = VelouraMuted, maxLines = 1)
                            Text(
                                when {
                                    plan != null -> "Incoming cue ${clock(plan.entryAtMs)}${if (plan.entryEstimated) " (best guess)" else ""}"
                                    exactDecision != null -> "No safe early cue — normal playback"
                                    else -> "Waiting for a safe cue"
                                },
                                color = VelouraBlue, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
        item { Text("Up Next", color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 20.sp) }
        itemsIndexed(coming, key = { _, entry -> entry.uid }) { offset, entry ->
            SongRow(entry.song, "#${offset + 2}") { c.player.skipTo(entry.index); onPicked() }
        }
    }
}

@Composable
private fun CurrentPlanCard(song: Song, transitionAtMs: Long?, analysed: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(song.thumbnail, null, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)))
                Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                    Text("NOW PLAYING", color = VelouraOrange, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text(song.title, color = VelouraNavy, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(song.artistText, color = VelouraMuted, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Mix out at", color = VelouraMuted, fontSize = 10.sp)
                    Text(transitionAtMs?.let(::clock) ?: if (analysed) "Song end" else "Analysing", color = VelouraBlue, fontWeight = FontWeight.Black)
                }
            }
            Waveform(
                progress = 0f,
                transitionAt = transitionAtMs?.let { (it.toFloat() / (song.durationSec * 1000L).coerceAtLeast(1)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0:00", color = VelouraMuted, fontSize = 11.sp)
                Text("IN", color = VelouraCyan, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text("OUT", color = VelouraOrange, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text(clock(song.durationSec * 1000L), color = VelouraMuted, fontSize = 11.sp)
            }
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

@Composable
private fun Waveform(progress: Float, transitionAt: Float? = null, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val bars = 48
        val step = size.width / bars
        repeat(bars) { index ->
            val x = step * index + step / 2
            val envelope = 0.28f + 0.72f * kotlin.math.abs(sin(index * 0.47)).toFloat()
            val h = size.height * envelope
            val fraction = index.toFloat() / bars
            val color = when {
                fraction < progress -> VelouraBlue
                transitionAt != null && fraction >= transitionAt -> VelouraOrange
                else -> Color(0xFFD6E1ED)
            }
            drawLine(color, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), strokeWidth = step * 0.55f, cap = StrokeCap.Round)
        }
    }
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
