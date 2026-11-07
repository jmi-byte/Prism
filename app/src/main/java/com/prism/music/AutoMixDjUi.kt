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

private enum class VelouraTab { HOME, SEARCH, LIBRARY, DJ }
private sealed interface DiscoveryFeed {
    data object Loading : DiscoveryFeed
    data class Ready(val songs: List<Song>) : DiscoveryFeed
    data class Error(val message: String) : DiscoveryFeed
}

/** Veloura's UI. Prism's playback and YouTube Music engines remain underneath. */
@Composable
fun AutoMixDjRoot(c: AppContainer, deepLink: StateFlow<Uri?>) {
    var tab by remember { mutableStateOf(VelouraTab.HOME) }
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
        Surface(Modifier.fillMaxSize(), color = VelouraBackground) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    when (tab) {
                        VelouraTab.HOME -> BrandHeader(linkMessage)
                        VelouraTab.SEARCH -> PageHeader("Search YouTube Music")
                        VelouraTab.LIBRARY -> PageHeader("Queue & Transition Plan")
                        VelouraTab.DJ -> PageHeader("Now Playing")
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
                        VelouraTab.LIBRARY -> QueuePlan(c) { tab = VelouraTab.DJ }
                        VelouraTab.DJ -> NowPlaying(c) { tab = VelouraTab.SEARCH }
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
private fun BrandHeader(message: String?) {
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
    val scope = rememberCoroutineScope()
    var linkText by remember { mutableStateOf("") }
    var linkError by remember { mutableStateOf<String?>(null) }
    var openingLink by remember { mutableStateOf(false) }
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
        item {
            Text("YouTube Music link", color = VelouraNavy, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Row(Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(
                    linkText, { linkText = it }, Modifier.weight(1f), singleLine = true,
                    label = { Text("Paste song link") }, placeholder = { Text("music.youtube.com/…") },
                )
                Button(
                    onClick = {
                        openingLink = true; linkError = null
                        scope.launch {
                            val found = ShareLinks.findIn(linkText)
                            linkError = if (found == null) "Paste a YouTube or YouTube Music song link" else playYouTubeLink(c, Uri.parse(found))
                            openingLink = false
                            if (linkError == null) onNowPlaying()
                        }
                    },
                    enabled = linkText.isNotBlank() && !openingLink,
                    contentPadding = PaddingValues(horizontal = 17.dp, vertical = 16.dp),
                ) {
                    if (openingLink) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(PrismIcons.Play, "Open link")
                }
            }
            linkError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
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
    val plan = decision?.takeIf { it.songId == song?.id && it.nextSongId == next?.id }?.plan
    val autoStatus = when {
        !settings.intelligentAutoMix -> "Off — tap to turn on"
        plan != null -> "Next mix in ~${clock((plan.transitionAtMs - position).coerceAtLeast(0))} · Beat match ✓ · Smooth blend"
        next == null -> "Add another song for a transition"
        else -> "Analysing the current and next song…"
    }

    if (song == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            ActionCard("Choose music to start AutoMix", "Search YouTube Music or paste a YouTube link. The player and transition plan will populate automatically.", "Find music", onFindMusic)
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
                val duration = c.player.duration.coerceAtLeast(1)
                Slider(position.coerceIn(0, duration).toFloat(), { c.player.seekTo(it.toLong()) }, valueRange = 0f..duration.toFloat(), modifier = Modifier.padding(top = 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(clock(position), color = VelouraMuted, fontSize = 12.sp)
                    Text(clock(duration), color = VelouraMuted, fontSize = 12.sp)
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
        item { Waveform((position.toFloat() / c.player.duration.coerceAtLeast(1)).coerceIn(0f, 1f), Modifier.fillMaxWidth().height(58.dp)) }
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
    val current = queue.getOrNull(currentIndex)?.song
    val nextIndex = (currentIndex + 1).takeIf { it in queue.indices }
    val next = nextIndex?.let { queue[it].song }
    val plan = decision?.takeIf { it.songId == current?.id && it.nextSongId == next?.id }?.plan
    var showPlan by remember { mutableStateOf(true) }

    if (queue.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Your queue is empty", color = VelouraNavy, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Choose a song from Discover or Search. Veloura will build the transition plan here.", color = VelouraMuted, modifier = Modifier.padding(top = 6.dp))
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
        current?.let { playing -> item { CurrentPlanCard(playing, plan?.transitionAtMs) } }
        if (showPlan && next != null) item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF6FF)), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp)) {
                    Text("→ Transition to (Beat Matched)", color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(next.thumbnail, null, Modifier.size(60.dp).clip(RoundedCornerShape(13.dp)))
                        Column(Modifier.padding(horizontal = 11.dp).weight(1f)) {
                            Text(next.title, color = VelouraNavy, fontWeight = FontWeight.Black, maxLines = 1)
                            Text(next.artistText, color = VelouraMuted, maxLines = 1)
                            Text(if (plan == null) "Waiting for a safe cue" else "Incoming cue ${clock(plan.entryAtMs)}", color = VelouraBlue, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item { Text("Up Next", color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 20.sp) }
        itemsIndexed(queue.drop(currentIndex + 1), key = { _, entry -> entry.uid }) { offset, entry ->
            SongRow(entry.song, "#${currentIndex + offset + 2}") { c.player.skipTo(currentIndex + offset + 1); onPicked() }
        }
    }
}

@Composable
private fun CurrentPlanCard(song: Song, transitionAtMs: Long?) {
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
                    Text(transitionAtMs?.let(::clock) ?: "Analysing", color = VelouraBlue, fontWeight = FontWeight.Black)
                }
            }
            Waveform(0.56f, Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp))
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
private fun Waveform(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val bars = 48
        val step = size.width / bars
        repeat(bars) { index ->
            val x = step * index + step / 2
            val envelope = 0.28f + 0.72f * kotlin.math.abs(sin(index * 0.47)).toFloat()
            val h = size.height * envelope
            val color = when {
                index.toFloat() / bars < progress * 0.78f -> VelouraBlue
                index.toFloat() / bars < progress -> VelouraOrange
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
