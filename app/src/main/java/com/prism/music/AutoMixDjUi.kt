package com.prism.music

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.prism.music.data.innertube.SearchFilter
import com.prism.music.data.model.Song
import com.prism.music.data.model.SongItem
import com.prism.music.playback.DjStatus
import com.prism.music.playback.QueueKind
import com.prism.music.playback.QueueSource
import com.prism.music.ui.theme.PrismIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

private val VelouraBlue = Color(0xFF1677FF)
private val VelouraSky = Color(0xFF49B4FF)
private val VelouraNavy = Color(0xFF10243E)
private val VelouraBackground = Color(0xFFF4F9FF)
private val VelouraSurface = Color(0xFFFFFFFF)
private val VelouraSurfaceAlt = Color(0xFFE8F3FF)
private val VelouraMuted = Color(0xFF60758D)

private val AutoMixColors = lightColorScheme(
    primary = VelouraBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = VelouraNavy,
    secondary = VelouraSky,
    tertiary = Color(0xFF0066D6),
    background = VelouraBackground,
    surface = VelouraSurface,
    surfaceVariant = VelouraSurfaceAlt,
    onSurface = VelouraNavy,
    onSurfaceVariant = VelouraMuted,
)

private enum class AutoMixTab { MIX, SEARCH, QUEUE }

/** The standalone MVP shell. Playback and YouTube access remain in the shared GPL engine underneath. */
@Composable
fun AutoMixDjRoot(c: AppContainer, deepLink: StateFlow<Uri?>) {
    val current by c.player.currentSong.collectAsState()
    var tab by remember { mutableStateOf(if (current == null) AutoMixTab.SEARCH else AutoMixTab.MIX) }
    val link by deepLink.collectAsState()
    var linkMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(link) {
        val uri = link ?: return@LaunchedEffect
        val id = when (uri.host) {
            "youtu.be" -> uri.pathSegments.firstOrNull()
            else -> uri.getQueryParameter("v")
        }
        if (id.isNullOrBlank()) {
            linkMessage = "That link does not contain a playable song."
            return@LaunchedEffect
        }
        linkMessage = "Opening YouTube link…"
        val result = runCatching { withContext(Dispatchers.IO) { c.ytm.next(id) } }.getOrNull()
        val songs = result?.songs.orEmpty()
        val at = songs.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: 0
        val song = songs.getOrNull(at) ?: Song(id, "Loading from YouTube…")
        withContext(Dispatchers.Main) {
            c.player.playQueue(
                if (songs.isEmpty()) listOf(song) else songs,
                at,
                QueueSource(QueueKind.RADIO, "YouTube link", seedId = id),
            )
            tab = AutoMixTab.MIX
            linkMessage = null
        }
    }

    MaterialTheme(colorScheme = AutoMixColors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = { AutoMixHeader(current, linkMessage) },
                bottomBar = {
                    NavigationBar(containerColor = VelouraSurface) {
                        NavigationBarItem(tab == AutoMixTab.MIX, { tab = AutoMixTab.MIX }, icon = { Icon(PrismIcons.Turntable, null) }, label = { Text("Mix") })
                        NavigationBarItem(tab == AutoMixTab.SEARCH, { tab = AutoMixTab.SEARCH }, icon = { Icon(PrismIcons.Search, null) }, label = { Text("Search") })
                        NavigationBarItem(tab == AutoMixTab.QUEUE, { tab = AutoMixTab.QUEUE }, icon = { Icon(PrismIcons.Queue, null) }, label = { Text("Queue") })
                    }
                },
            ) { pad ->
                Box(
                    Modifier.fillMaxSize().padding(pad).background(
                        Brush.verticalGradient(listOf(Color(0xFFE4F2FF), Color.White, VelouraBackground)),
                    ),
                ) {
                    when (tab) {
                        AutoMixTab.MIX -> AutoMixNowPlaying(c)
                        AutoMixTab.SEARCH -> AutoMixSearch(c) { tab = AutoMixTab.MIX }
                        AutoMixTab.QUEUE -> AutoMixQueue(c) { tab = AutoMixTab.MIX }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoMixHeader(song: Song?, message: String?) {
    Column(Modifier.fillMaxWidth().background(Color(0xFAFFFFFF)).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_veloura_crossfade_v),
                    contentDescription = "Veloura",
                    modifier = Modifier.size(40.dp),
                    tint = Color.Unspecified,
                )
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("Veloura", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(message ?: song?.let { "Now mixing · ${it.primaryArtist}" } ?: "Music that never stops moving", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun AutoMixNowPlaying(c: AppContainer) {
    val song by c.player.currentSong.collectAsState()
    val playing by c.player.isPlaying.collectAsState()
    val settings by c.settings.flow.collectAsState()
    val djOn by c.dj.on.collectAsState()
    val djProgress by c.dj.progress.collectAsState()
    val decision by c.intelligentAutoMixShadow.decision.collectAsState()
    val queue by c.player.queue.collectAsState()
    val upNext by c.player.upNext.collectAsState()
    var position by remember(song?.id) { mutableLongStateOf(c.player.position) }

    LaunchedEffect(song?.id, playing) {
        while (true) {
            position = c.player.position
            delay(if (playing) 250 else 700)
        }
    }

    val lyrics by produceState<com.prism.music.data.lyrics.Lyrics?>(null, song?.id) {
        value = song?.let { s -> runCatching { withContext(Dispatchers.IO) { c.lyrics.best(s, fetch = true) } }.getOrNull() }
    }
    val lyric = lyrics?.lines?.lastOrNull { !it.isGap && it.timeMs <= c.player.lyricPosition }?.text
    val next = upNext.firstOrNull()?.let(queue::getOrNull)?.song
    val exactDecision = decision?.takeIf { it.songId == song?.id && it.nextSongId == next?.id }
    val exactPlan = exactDecision?.plan
    val autoStatus = when {
        !settings.intelligentAutoMix -> "Off"
        exactPlan != null -> "Ready · mix at ${clock(exactPlan.transitionAtMs)} · next from ${if (exactPlan.entryAtMs > 0) clock(exactPlan.entryAtMs) else "opening"}"
        song == null -> "Waiting for a song"
        next == null -> "Add another song to the queue"
        exactDecision != null -> "No safe early transition · playing normally"
        else -> "Analysing this transition…"
    }

    if (song == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(PrismIcons.Turntable, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Pick a song from Search", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("AutoMix will analyse it and the next track.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = VelouraSurface), shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(song!!.thumbnail, null, Modifier.fillMaxWidth(0.72f).aspectRatio(1f).clip(RoundedCornerShape(24.dp)))
                    Spacer(Modifier.height(18.dp))
                    Text(song!!.title, fontWeight = FontWeight.Bold, fontSize = 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(song!!.artistText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!lyric.isNullOrBlank()) Text(lyric, Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.secondary, fontSize = 17.sp, maxLines = 2)
                    val duration = c.player.duration.coerceAtLeast(1)
                    Slider(position.coerceIn(0, duration).toFloat(), { c.player.seekTo(it.toLong()) }, valueRange = 0f..duration.toFloat(), modifier = Modifier.padding(top = 10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(clock(position), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Text("−${clock((duration - position).coerceAtLeast(0))}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        IconButton({ c.player.previous() }) { Icon(PrismIcons.Previous, "Previous", Modifier.size(32.dp)) }
                        IconButton({ c.player.togglePlay() }, Modifier.size(76.dp).clip(CircleShape).background(Brush.linearGradient(listOf(VelouraBlue, VelouraSky)))) {
                            Icon(if (playing) PrismIcons.Pause else PrismIcons.Play, if (playing) "Pause" else "Play", Modifier.size(38.dp), tint = Color.White)
                        }
                        IconButton({ c.player.next() }) { Icon(PrismIcons.Next, "Next", Modifier.size(32.dp)) }
                    }
                }
            }
        }
        item {
            ModeCard(
                title = "Intelligent AutoMix",
                detail = autoStatus,
                enabled = settings.intelligentAutoMix,
                accent = VelouraBlue,
            ) { on ->
                c.settings.setIntelligentAutoMix(on)
                if (on) { c.settings.setMix(false); c.dj.setOn(false) }
            }
        }
        item {
            ModeCard(
                title = "DJ Mode",
                detail = if (djOn) DjStatus.of(djProgress).first else "Key order, tempo matching and continuous blends",
                enabled = djOn,
                accent = VelouraSky,
            ) { on ->
                c.dj.setOn(on)
                if (on) c.settings.setIntelligentAutoMix(false)
            }
        }
        next?.let { n -> item {
            Card(colors = CardDefaults.cardColors(containerColor = VelouraSurface), shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(n.thumbnail, null, Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("NEXT", color = MaterialTheme.colorScheme.tertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(n.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(n.artistText, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        } }
    }
}

@Composable
private fun ModeCard(title: String, detail: String, enabled: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = if (enabled) accent.copy(alpha = 0.12f) else VelouraSurface), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.fillMaxWidth().clickable { onChange(!enabled) }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(accent.copy(alpha = if (enabled) 0.95f else 0.25f)), contentAlignment = Alignment.Center) {
                Icon(PrismIcons.Turntable, null, tint = Color.White)
            }
            Column(Modifier.padding(horizontal = 14.dp).weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            Switch(enabled, onChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoMixSearch(c: AppContainer, onPlayed: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }

    LaunchedEffect(submitted) {
        if (submitted.isBlank()) return@LaunchedEffect
        loading = true; error = null
        runCatching { withContext(Dispatchers.IO) { c.ytm.search(submitted, SearchFilter.SONGS).items.filterIsInstance<SongItem>().map { it.song }.distinctBy { it.id } } }
            .onSuccess { results = it }
            .onFailure { error = it.message ?: "Search failed" }
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Text("Find your next mix", Modifier.padding(top = 18.dp, bottom = 12.dp), fontWeight = FontWeight.Bold, fontSize = 26.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true, label = { Text("Songs on YouTube Music") })
            Button({ submitted = query.trim() }, enabled = query.isNotBlank() && !loading, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(PrismIcons.Search, "Search")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 10.dp)) }
        if (submitted.isBlank()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Search, tap a song, then add more to the queue.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(results, key = { _, s -> s.id }) { i, song ->
                    SongResult(song) {
                        c.player.playQueue(results, i, QueueSource(QueueKind.OTHER, "Search: $submitted"))
                        onPlayed()
                    }
                }
            }
        }
    }
}

@Composable
private fun SongResult(song: Song, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).background(VelouraSurface).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(song.thumbnail, null, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artistText, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(clock(song.durationSec * 1000L), color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
        }
        Icon(PrismIcons.Play, "Play")
    }
}

@Composable
private fun AutoMixQueue(c: AppContainer, onPicked: () -> Unit) {
    val queue by c.player.queue.collectAsState()
    val current by c.player.currentIndex.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Text("Mix queue", Modifier.padding(top = 18.dp, bottom = 12.dp), fontWeight = FontWeight.Bold, fontSize = 26.sp)
        Text("Tap any song to jump to it. AutoMix analyses the current pair.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(queue, key = { _, e -> e.uid }) { i, entry ->
                val active = i == current
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { c.player.skipTo(i); onPicked() }
                        .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else VelouraSurface).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (active) "NOW" else (i + 1).toString(), Modifier.padding(horizontal = 8.dp), color = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    AsyncImage(entry.song.thumbnail, null, Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(entry.song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.song.artistText, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun clock(ms: Long): String {
    val all = (ms.coerceAtLeast(0) / 1000).toInt()
    return "%d:%02d".format(all / 60, all % 60)
}
