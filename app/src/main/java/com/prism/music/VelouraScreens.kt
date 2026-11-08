package com.prism.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.prism.music.ui.components.ReorderList
import com.prism.music.ui.theme.PrismIcons
import kotlin.math.abs
import kotlin.math.sin

/*
 * Veloura's screens as the owner's design shows them (Discover, Now Playing, Queue & Transition Plan, the header and the
 * tab bar). They take plain data and report taps, and know nothing of the player, so they can be drawn in tests with
 * sample data (VelouraScreensRenderTest). AutoMixDjUi.kt reads the app's state into them.
 */

internal val VelouraBlue = Color(0xFF1E6BFF)
internal val VelouraCyan = Color(0xFF12CFE4)
internal val VelouraOrange = Color(0xFFFF8A34)
internal val VelouraNavy = Color(0xFF0B1B3F)
internal val VelouraBackground = Color(0xFFF6F9FE)
internal val VelouraMuted = Color(0xFF66768C)
internal val VelouraLine = Color(0xFFE3EAF4)
internal val VelouraPurple = Color(0xFF8C55E8)
internal val VelouraGreen = Color(0xFF22B573)

internal val VelouraColors = lightColorScheme(
    primary = VelouraBlue, onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEEFF), onPrimaryContainer = VelouraNavy,
    secondary = VelouraCyan, tertiary = VelouraOrange,
    background = VelouraBackground, surface = Color.White,
    surfaceVariant = Color(0xFFEAF4FF), onSurface = VelouraNavy, onSurfaceVariant = VelouraMuted,
)

// ---- Shared pieces ----

/** The design's icons Prism's set doesn't have (Material icon shapes, Apache 2.0). */
internal object VelouraIcons {
    private fun icon(name: String, d: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toNodes(), fill = androidx.compose.ui.graphics.SolidColor(Color.Black))
        .build()

    val Gear = icon("Gear", "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z")
    val Bolt = icon("Bolt", "M11,21h-1l1,-7H7.5c-0.88,0 -0.33,-0.75 -0.31,-0.78C8.48,10.94 10.42,7.54 13.01,3h1l-1,7h3.51c0.4,0 0.62,0.19 0.4,0.66C12.97,17.55 11,21 11,21z")
    val Cloud = icon("Cloud", "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96zM19,18H6c-2.21,0 -4,-1.79 -4,-4 0,-2.05 1.53,-3.76 3.56,-3.97l1.07,-0.11 0.5,-0.95C8.08,7.14 9.94,6 12,6c2.62,0 4.88,1.86 5.39,4.43l0.3,1.5 1.53,0.11c1.56,0.1 2.78,1.41 2.78,2.96 0,1.65 -1.35,3 -3,3z")
}

/** The design's seek bar: a thin track and a round handle. Tap or drag to move. */
@Composable
internal fun VelouraSeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val total = durationMs.coerceAtLeast(1)
    var drag by remember { mutableStateOf<Float?>(null) }
    val f = drag ?: (positionMs.toFloat() / total).coerceIn(0f, 1f)
    Canvas(
        modifier.fillMaxWidth().height(26.dp)
            .pointerInput(total) { detectTapGestures { onSeek((it.x / size.width * total).toLong().coerceIn(0, total)) } }
            .pointerInput(total) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let { onSeek((it * total).toLong()) }; drag = null },
                    onDragCancel = { drag = null },
                ) { change, _ -> drag = (change.position.x / size.width).coerceIn(0f, 1f) }
            },
    ) {
        val y = size.height / 2
        val track = 4.dp.toPx()
        val r = 8.dp.toPx()
        drawRoundRect(Color(0xFFDCE4EF), Offset(0f, y - track / 2), Size(size.width, track), CornerRadius(track))
        drawRoundRect(VelouraBlue, Offset(0f, y - track / 2), Size(size.width * f, track), CornerRadius(track))
        val x = (size.width * f).coerceIn(r, size.width - r)
        drawCircle(Color.White, r + 2.dp.toPx(), Offset(x, y))
        drawCircle(VelouraBlue, r, Offset(x, y))
    }
}

/** The ⠿ grip on a row that can be dragged. */
@Composable
internal fun DotsGrip(modifier: Modifier = Modifier) {
    Canvas(modifier.size(14.dp, 20.dp)) {
        val r = 1.9.dp.toPx()
        for (col in 0..1) for (row in 0..2) {
            drawCircle(Color(0xFF8D9AAD), r, Offset(size.width * (0.25f + 0.5f * col), size.height * (0.2f + 0.3f * row)))
        }
    }
}

/** The ribbon V. */
@Composable
internal fun VelouraLogo(size: Dp) {
    Icon(painterResource(R.drawable.ic_veloura_logo), "Veloura", Modifier.size(size, size * 0.83f), tint = Color.Unspecified)
}

/** A cover, or a soft gradient in its place while there's none. */
@Composable
internal fun Artwork(url: String?, modifier: Modifier, corner: Dp = 14.dp, seed: Int = 0) {
    val shape = RoundedCornerShape(corner)
    val tones = listOf(
        listOf(Color(0xFF5B5FEF), Color(0xFFFF8A65)), listOf(Color(0xFFFFA24C), Color(0xFFFF5E7E)),
        listOf(Color(0xFF3A8DFF), Color(0xFF7AD7F0)), listOf(Color(0xFF8C55E8), Color(0xFFFF6FAE)),
    )[abs(seed) % 4]
    Box(modifier.clip(shape).background(Brush.linearGradient(tones))) {
        if (url != null) AsyncImage(url, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    }
}

/** Home's header: the V and "Veloura", then the account and settings. */
@Composable
internal fun VelouraBrandBar(message: String?, onAccount: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(VelouraBackground).statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VelouraLogo(42.dp)
        Column(Modifier.padding(start = 9.dp).weight(1f)) {
            Text("Veloura", color = VelouraNavy, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = 28.sp)
            if (message != null) Text(message, color = VelouraMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onAccount) { Icon(PrismIcons.Person, "YouTube Music account", tint = VelouraNavy) }
        IconButton(onClick = onSettings) { Icon(VelouraIcons.Gear, "Mix settings", tint = VelouraNavy) }
    }
}

/** A screen's header: a way back on the left, its title in the middle, its menu on the right. */
@Composable
internal fun VelouraTitleBar(
    title: String,
    navIcon: ImageVector?,
    navLabel: String,
    onNav: () -> Unit,
    menu: List<Pair<String, () -> Unit>> = emptyList(),
    background: Color = VelouraBackground,
) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().background(background).statusBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp)) {
        if (navIcon != null) IconButton(onNav, Modifier.align(Alignment.CenterStart)) { Icon(navIcon, navLabel, tint = VelouraNavy) }
        Text(title, Modifier.align(Alignment.Center), color = VelouraNavy, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (menu.isNotEmpty()) Box(Modifier.align(Alignment.CenterEnd)) {
            IconButton({ open = true }) { Icon(PrismIcons.MoreVertical, "More", tint = VelouraNavy) }
            DropdownMenu(open, { open = false }) {
                menu.forEach { (label, action) -> DropdownMenuItem({ Text(label) }, { open = false; action() }) }
            }
        }
    }
}

enum class VelouraTabItem(val label: String) { HOME("Home"), SEARCH("Search"), LIBRARY("Library"), DJ("DJ") }

/** The tab bar: Home, Search, Library and DJ. */
@Composable
internal fun VelouraTabBar(selected: VelouraTabItem?, onSelect: (VelouraTabItem) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(VelouraLine))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp)) {
            VelouraTabItem.entries.forEach { t ->
                val on = t == selected
                val icon = when (t) {
                    VelouraTabItem.HOME -> PrismIcons.Home
                    VelouraTabItem.SEARCH -> PrismIcons.Search
                    VelouraTabItem.LIBRARY -> PrismIcons.Library
                    VelouraTabItem.DJ -> PrismIcons.Turntable
                }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { onSelect(t) }.padding(vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(icon, t.label, tint = if (on) VelouraBlue else VelouraMuted, modifier = Modifier.size(24.dp))
                    Text(t.label, color = if (on) VelouraBlue else VelouraMuted, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
internal fun VelouraSegments(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFFEAF1FB)).padding(4.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if (on) VelouraBlue else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (on) Color.White else VelouraNavy, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        }
    }
}

/** The equaliser badge in front of "Intelligent AutoMix". */
@Composable
internal fun MixBars(modifier: Modifier = Modifier, colors: List<Color> = listOf(VelouraBlue, VelouraPurple)) {
    Canvas(modifier) {
        val heights = listOf(0.35f, 0.7f, 1f, 0.55f, 0.85f, 0.45f)
        val step = size.width / heights.size
        heights.forEachIndexed { i, h ->
            val bar = size.height * h
            drawRoundRect(
                Brush.verticalGradient(colors), Offset(i * step + step * 0.22f, (size.height - bar) / 2),
                Size(step * 0.56f, bar), CornerRadius(step * 0.28f),
            )
        }
    }
}

/**
 * A song's waveform as bars: played in blue, what's to come in grey. [colorful] paints it Veloura's blue-to-orange
 * (Now Playing's footer); [outAt] colours the stretch after the mix-out point orange, [inAt] marks where the song came in.
 */
@Composable
internal fun WaveBars(
    progress: Float,
    modifier: Modifier = Modifier,
    outAt: Float? = null,
    inAt: Float? = null,
    colorful: Boolean = false,
    bars: Int = 64,
) {
    Canvas(modifier) {
        val step = size.width / bars
        repeat(bars) { i ->
            val f = i.toFloat() / bars
            val env = 0.22f + 0.78f * abs(sin(i * 0.53) * 0.6 + sin(i * 0.17) * 0.4).toFloat().coerceIn(0f, 1f)
            val h = size.height * env
            val color = when {
                colorful -> lerpColor(VelouraBlue, VelouraOrange, f).let { if (f <= progress) it else it.copy(alpha = 0.55f) }
                f <= progress -> VelouraBlue
                outAt != null && f >= outAt -> VelouraOrange
                inAt != null && f < inAt -> Color(0xFFBFE8D6)
                else -> Color(0xFFD5DEEA)
            }
            drawLine(color, Offset(i * step + step / 2, (size.height - h) / 2), Offset(i * step + step / 2, (size.height + h) / 2), step * 0.5f, androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val m = if (t < 0.5f) lerpRgb(a, VelouraPurple, t * 2) else lerpRgb(VelouraPurple, b, (t - 0.5f) * 2)
    return m
}

private fun lerpRgb(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f,
)

internal fun clockOf(ms: Long): String {
    val all = (ms.coerceAtLeast(0) / 1000).toInt()
    return "%d:%02d".format(all / 60, all % 60)
}

// ---- Discover (Home) ----

data class PlaylistCard(val id: String, val title: String, val detail: String, val thumbnail: String?)

data class VelouraHomeUi(
    val loggedIn: Boolean,
    /** Null while loading. */
    val playlists: List<PlaylistCard>?,
    val playlistsError: String? = null,
    val autoMixOn: Boolean,
)

enum class HomeTile { AUTOMIX_PARTY, DJ_MIXES, GENRES, FOR_YOU }

@Composable
internal fun VelouraHome(
    ui: VelouraHomeUi,
    onSearch: () -> Unit,
    onTile: (HomeTile) -> Unit,
    onPlaylist: (PlaylistCard) -> Unit,
    onSeeAll: () -> Unit,
    onConnect: () -> Unit,
    onQuickMix: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 18.dp)) {
        Text("Discover Music", Modifier.padding(start = 20.dp, top = 14.dp), color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 27.sp)
        Row(
            Modifier.padding(horizontal = 20.dp).padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White)
                .border(1.dp, VelouraLine, RoundedCornerShape(24.dp)).clickable(onClick = onSearch).padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PrismIcons.Search, null, tint = VelouraNavy, modifier = Modifier.size(22.dp))
            Text("Search songs, artists, playlists", Modifier.padding(start = 12.dp), color = VelouraMuted, fontSize = 15.sp)
        }
        Row(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Tile("AutoMix\nParty", VelouraIcons.Bolt, listOf(Color(0xFF9B5BF0), Color(0xFF7A45E0)), Modifier.weight(1f)) { onTile(HomeTile.AUTOMIX_PARTY) }
            Tile("DJ Mixes", PrismIcons.Headphones, listOf(Color(0xFF2C8BFF), Color(0xFF1767E8)), Modifier.weight(1f)) { onTile(HomeTile.DJ_MIXES) }
            Tile("Genres", VelouraIcons.Cloud, listOf(Color(0xFF2FD0B5), Color(0xFF17B3A0)), Modifier.weight(1f)) { onTile(HomeTile.GENRES) }
            Tile("For You", PrismIcons.Star, listOf(Color(0xFFFF9C45), Color(0xFFFF7A2E)), Modifier.weight(1f)) { onTile(HomeTile.FOR_YOU) }
        }
        if (!ui.loggedIn) {
            Card(
                Modifier.padding(horizontal = 20.dp).padding(top = 16.dp).fillMaxWidth().clickable(onClick = onConnect),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3FF)), shape = RoundedCornerShape(18.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Connect YouTube Music", color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text("Your playlists, likes and mixes, mixed non-stop. Veloura never sees your Google password.", color = VelouraMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Row(Modifier.padding(horizontal = 20.dp).padding(top = 22.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Trending Playlists", Modifier.weight(1f), color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 19.sp)
            Text("See All  ›", Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onSeeAll).padding(4.dp), color = VelouraNavy, fontSize = 14.sp)
        }
        when {
            ui.playlists == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = VelouraBlue) }
            ui.playlists.isEmpty() -> Text(ui.playlistsError ?: "No playlists right now. Try Search.", Modifier.padding(20.dp), color = VelouraMuted)
            else -> Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 8.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ui.playlists.forEachIndexed { i, p ->
                    Column(Modifier.width(112.dp).clip(RoundedCornerShape(12.dp)).clickable { onPlaylist(p) }) {
                        Artwork(p.thumbnail, Modifier.size(112.dp, 100.dp), corner = 12.dp, seed = i)
                        Text(p.title, Modifier.padding(top = 7.dp), color = VelouraNavy, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("♫ ${p.detail}", color = VelouraMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Text("Quick Mixes", Modifier.padding(start = 20.dp, top = 22.dp), color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 19.sp)
        Card(
            Modifier.padding(horizontal = 20.dp).padding(top = 10.dp).fillMaxWidth().clickable(onClick = onQuickMix),
            colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp),
            elevation = CardDefaults.cardElevation(2.dp),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                MixBars(Modifier.size(34.dp, 28.dp))
                Column(Modifier.padding(horizontal = 13.dp).weight(1f)) {
                    Text("Intelligent AutoMix", color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text(if (ui.autoMixOn) "Seamless, non-stop mixing" else "Off · tap to mix non-stop", color = VelouraMuted, fontSize = 13.sp)
                }
                Box(Modifier.size(40.dp).clip(CircleShape).background(VelouraBlue), contentAlignment = Alignment.Center) {
                    Icon(PrismIcons.Play, "Play", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, icon: ImageVector, colors: List<Color>, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(98.dp).clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(colors)).clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp))
        Text(label, Modifier.padding(top = 6.dp), color = Color.White, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

// ---- Now Playing ----

data class VelouraNowPlayingUi(
    val title: String,
    val artist: String,
    val artwork: String?,
    val liked: Boolean,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val autoMixOn: Boolean,
    /** What AutoMix is doing for this pair, said plainly (see AutoMixDjUi's status). */
    val status: String,
    val shuffle: Boolean,
    /** Player.REPEAT_MODE_*: 0 off, 1 one, 2 all. */
    val repeatMode: Int,
)

@Composable
internal fun VelouraNowPlaying(
    ui: VelouraNowPlayingUi,
    onCollapse: () -> Unit,
    menu: List<Pair<String, () -> Unit>>,
    onSeek: (Long) -> Unit,
    onLike: () -> Unit,
    onAutoMix: (Boolean) -> Unit,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    /** The cover shows the karaoke lyrics instead ([lyrics] draws them); the lyrics button and the cover switch it. */
    showLyrics: Boolean = false,
    onLyrics: () -> Unit = {},
    lyrics: (@Composable (Modifier) -> Unit)? = null,
) {
    val glow = Brush.verticalGradient(listOf(Color(0xFFE9E6FF), Color(0xFFF7EAF6), Color(0xFFFFF3EC), Color.White))
    Column(Modifier.fillMaxSize().background(glow)) {
        VelouraTitleBar("Now Playing", PrismIcons.ChevronDown, "Close player", onCollapse, menu, background = Color.Transparent)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showLyrics && lyrics != null) {
                // Prism's karaoke lyrics, on a dark ground (they're drawn light), the sung line lighting up word by word.
                Box(
                    Modifier.padding(top = 18.dp).fillMaxWidth().height(290.dp).shadow(18.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF1B2F86), Color(0xFF0B1650), Color(0xFF240E5A))))
                        .clickable(onClick = onLyrics),
                ) { lyrics(Modifier.fillMaxSize()) }
            } else {
                Artwork(
                    ui.artwork,
                    Modifier.padding(top = 18.dp).fillMaxWidth(0.78f).aspectRatio(1f).shadow(18.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp)).clickable(enabled = lyrics != null, onClick = onLyrics),
                    corner = 20.dp, seed = ui.title.hashCode(),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ui.title, color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 23.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(ui.artist, color = VelouraMuted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (lyrics != null) IconButton(onLyrics) {
                    Icon(if (showLyrics) PrismIcons.LyricsFilled else PrismIcons.Lyrics, if (showLyrics) "Show the cover" else "Show lyrics", tint = if (showLyrics) VelouraBlue else VelouraNavy, modifier = Modifier.size(26.dp))
                }
                IconButton(onLike) {
                    Icon(if (ui.liked) PrismIcons.HeartFilled else PrismIcons.Heart, if (ui.liked) "Unlike" else "Like", tint = if (ui.liked) Color(0xFFFF4F79) else VelouraNavy, modifier = Modifier.size(28.dp))
                }
            }
            VelouraSeekBar(ui.positionMs, ui.durationMs, onSeek, Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(clockOf(ui.positionMs), color = VelouraMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(clockOf(ui.durationMs), color = VelouraMuted, fontSize = 12.sp)
            }
            Card(
                Modifier.fillMaxWidth().padding(top = 16.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(3.dp),
            ) {
                Column(Modifier.clickable { onAutoMix(!ui.autoMixOn) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MixBars(Modifier.size(30.dp, 24.dp))
                        Text("Intelligent AutoMix", Modifier.padding(start = 10.dp).weight(1f), color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 17.sp)
                        Switch(ui.autoMixOn, onAutoMix, colors = SwitchDefaults.colors(checkedTrackColor = VelouraBlue))
                    }
                    Text(ui.status, Modifier.padding(top = 2.dp), color = VelouraBlue, fontSize = 13.sp)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onShuffle) { Icon(PrismIcons.Shuffle, "Shuffle", tint = if (ui.shuffle) VelouraBlue else VelouraNavy) }
                IconButton(onPrevious) { Icon(PrismIcons.Previous, "Previous", tint = VelouraNavy, modifier = Modifier.size(30.dp)) }
                Box(
                    Modifier.size(70.dp).shadow(10.dp, CircleShape).clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFF2E7BFF), Color(0xFF1659E6))))
                        .clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center,
                ) { Icon(if (ui.playing) PrismIcons.Pause else PrismIcons.Play, if (ui.playing) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(34.dp)) }
                IconButton(onNext) { Icon(PrismIcons.Next, "Next", tint = VelouraNavy, modifier = Modifier.size(30.dp)) }
                IconButton(onRepeat) {
                    Box {
                        Icon(PrismIcons.Repeat, "Repeat", tint = if (ui.repeatMode != 0) VelouraBlue else VelouraNavy)
                        if (ui.repeatMode == 1) Text("1", Modifier.align(Alignment.Center), color = VelouraBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        WaveBars(
            (ui.positionMs.toFloat() / ui.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f),
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp).navigationBarsPadding(),
            colorful = true, bars = 72,
        )
    }
}

// ---- Queue & Transition Plan ----

data class QueueSongUi(val key: String, val index: Int, val title: String, val artist: String, val thumbnail: String?, val durationMs: Long)

data class VelouraQueueUi(
    val current: QueueSongUi?,
    /** "2:10", "Song end" or "Analysing". */
    val mixOut: String,
    val positionMs: Long,
    val durationMs: Long,
    /** Where this song came in (a mid-song entry), if it did. */
    val inMs: Long?,
    /** Where it mixes out, if there's a plan. */
    val outMs: Long?,
    val next: QueueSongUi?,
    /** "Beat matched", "Phrase-aligned", "Best-guess point", or null without a plan. */
    val transitionKind: String?,
    /** "0:18", or null when the next song starts from its opening. */
    val mixIn: String?,
    /** "BPM 122 → 122 · Key 8A → 8A ✓", from what's been measured; null when nothing is known yet. */
    val match: String?,
    /** Plain words for the AutoMix Plan tab. */
    val planDetails: List<String>,
    val upNext: List<QueueSongUi>,
)

@Composable
internal fun VelouraQueue(
    ui: VelouraQueueUi,
    tab: Int,
    onTab: (Int) -> Unit,
    onBack: () -> Unit,
    menu: List<Pair<String, () -> Unit>>,
    onPlay: (QueueSongUi) -> Unit,
    onRemove: (QueueSongUi) -> Unit,
    onPlayNext: (QueueSongUi) -> Unit,
    onReorder: (List<QueueSongUi>) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(VelouraBackground)) {
        VelouraTitleBar("Queue & Transition Plan", PrismIcons.ArrowBack, "Back", onBack, menu)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
            VelouraSegments(listOf("Up Next", "AutoMix Plan"), tab, onTab, Modifier.padding(top = 10.dp))
            if (ui.current == null) {
                Text("Your queue is empty. Choose music from Home, Search or Library and Veloura will plan its transitions here.", Modifier.padding(top = 24.dp), color = VelouraMuted)
                return@Column
            }
            NowPlayingPlanCard(ui)
            if (ui.next != null) TransitionCard(ui, ui.next)
            if (tab == 0) {
                if (ui.upNext.size > 1) {
                    Text("Up Next", Modifier.padding(top = 20.dp, bottom = 8.dp), color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 19.sp)
                    ReorderList(ui.upNext.drop(1), keyOf = { it.key }, onReorder = { onReorder(listOf(ui.upNext.first()) + it) }) { song, _, held, mod ->
                        QueueRow(song, held, mod, this, onPlay, onRemove, onPlayNext)
                    }
                }
            } else {
                Card(Modifier.fillMaxWidth().padding(top = 14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("How this transition is planned", color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        ui.planDetails.forEach { Text("• $it", Modifier.padding(top = 6.dp), color = VelouraMuted, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingPlanCard(ui: VelouraQueueUi) {
    val song = ui.current ?: return
    val total = ui.durationMs.coerceAtLeast(1).toFloat()
    Card(Modifier.fillMaxWidth().padding(top = 14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(song.thumbnail, Modifier.size(60.dp), corner = 12.dp, seed = song.title.hashCode())
                Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                    Text("Now Playing", Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFF1E8FF)).padding(horizontal = 7.dp, vertical = 2.dp), color = VelouraPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(song.title, Modifier.padding(top = 3.dp), color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = VelouraMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Mix out at", color = VelouraPurple, fontSize = 11.sp)
                    Text(ui.mixOut, color = VelouraBlue, fontWeight = FontWeight.Black, fontSize = 22.sp)
                }
            }
            val inAt = ui.inMs?.let { it / total }
            val outAt = ui.outMs?.let { it / total }
            WaveBars((ui.positionMs / total).coerceIn(0f, 1f), Modifier.fillMaxWidth().height(44.dp).padding(top = 10.dp), outAt = outAt, inAt = inAt, bars = 72)
            // The In and Out marks under the waveform, where they fall.
            BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp)) {
                val w = maxWidth
                Text("0:00", Modifier.align(Alignment.TopStart), color = VelouraMuted, fontSize = 11.sp)
                Text(clockOf(ui.durationMs), Modifier.align(Alignment.TopEnd), color = VelouraMuted, fontSize = 11.sp)
                inAt?.let { Mark("In", clockOf(ui.inMs!!), VelouraGreen, Modifier.offset(x = (w * it - 20.dp).coerceIn(28.dp, w - 70.dp))) }
                outAt?.let { Mark("Out", clockOf(ui.outMs!!), VelouraOrange, Modifier.offset(x = (w * it - 20.dp).coerceIn(28.dp, w - 70.dp))) }
            }
        }
    }
}

@Composable
private fun Mark(label: String, time: String, color: Color, modifier: Modifier) {
    Column(modifier.width(44.dp).padding(top = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, Modifier.clip(RoundedCornerShape(8.dp)).background(color).padding(horizontal = 7.dp, vertical = 1.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(time, color = VelouraMuted, fontSize = 11.sp)
    }
}

@Composable
private fun TransitionCard(ui: VelouraQueueUi, next: QueueSongUi) {
    Card(Modifier.fillMaxWidth().padding(top = 12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF5FF)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("→  Transition to", color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 15.sp)
                ui.transitionKind?.let { Text(" ($it)", color = VelouraPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(next.thumbnail, Modifier.size(56.dp), corner = 12.dp, seed = next.title.hashCode())
                Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                    Text(next.title, color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(next.artist, color = VelouraMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Mix in at", color = VelouraPurple, fontSize = 11.sp)
                    Text(ui.mixIn ?: "0:00", color = VelouraBlue, fontWeight = FontWeight.Black, fontSize = 22.sp)
                }
            }
            ui.match?.let { Text(it, Modifier.padding(top = 10.dp), color = VelouraNavy, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun QueueRow(
    song: QueueSongUi,
    held: Boolean,
    modifier: Modifier,
    scope: com.prism.music.ui.components.ReorderScope,
    onPlay: (QueueSongUi) -> Unit,
    onRemove: (QueueSongUi) -> Unit,
    onPlayNext: (QueueSongUi) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(if (held) Color(0xFFEAF1FB) else Color.White)
            .clickable { onPlay(song) }.padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        with(scope) { Box(Modifier.dragHandle().padding(horizontal = 8.dp, vertical = 6.dp)) { DotsGrip() } }
        Artwork(song.thumbnail, Modifier.size(46.dp), corner = 10.dp, seed = song.title.hashCode())
        Column(Modifier.padding(horizontal = 11.dp).weight(1f)) {
            Text(song.title, color = VelouraNavy, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = VelouraMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("~${clockOf(song.durationMs)}", color = VelouraNavy, fontSize = 13.sp)
        Box {
            IconButton({ open = true }) { Icon(PrismIcons.MoreVertical, "More", tint = VelouraNavy) }
            DropdownMenu(open, { open = false }) {
                DropdownMenuItem({ Text("Play now") }, { open = false; onPlay(song) })
                DropdownMenuItem({ Text("Play next") }, { open = false; onPlayNext(song) })
                DropdownMenuItem({ Text("Remove from queue") }, { open = false; onRemove(song) })
            }
        }
    }
}

// ---- Mix Settings ----

/**
 * How one song becomes the next: a plain crossfade, or one of the DJ styles. One choice, because a blend is one or the
 * other (a DJ blend replaces the crossfade's volume fade with its own, see MixEngine.djGains). Stored as SoundPrefs'
 * djEffects (off for [CROSSFADE]) and djStyle.
 */
enum class TransitionStyle(val label: String, val detail: String, val dj: com.prism.music.data.prefs.DjStyle?) {
    CROSSFADE("Crossfade", "A plain, smooth fade from one song into the next. No effects.", null),
    DJ_AUTO("DJ · Auto", "Picks per pair: Club when the beats can be lined up, Echo out when they can't.", com.prism.music.data.prefs.DjStyle.AUTO),
    DJ_CLUB("DJ · Club", "Bass swap and filter sweep: the old song's highs roll away as the new one opens up.", com.prism.music.data.prefs.DjStyle.CLUB),
    DJ_SMOOTH("DJ · Smooth", "Bass swap only: subtle, best on headphones or speakers with bass.", com.prism.music.data.prefs.DjStyle.SMOOTH),
    DJ_ECHO("DJ · Echo out", "The old song is thrown into a beat-timed echo and trails away as the new one comes in.", com.prism.music.data.prefs.DjStyle.ECHO);

    companion object {
        fun of(djEffects: Boolean, style: com.prism.music.data.prefs.DjStyle): TransitionStyle =
            if (!djEffects) CROSSFADE else entries.first { it.dj == style }
    }
}

/** A transition's length in words: the chosen seconds, or about how long Automatic makes it for this style. */
fun transitionLength(automatic: Boolean, seconds: Int, dj: Boolean, estimated: Boolean): String = when {
    !automatic -> "$seconds s"
    dj -> "~8 s"
    estimated -> "~6 s"
    else -> "~3 s"
}

data class VelouraMixSettingsUi(
    val autoMix: Boolean,
    val style: TransitionStyle,
    val automaticLength: Boolean,
    val seconds: Int,
    val maxSeconds: Int,
)

@Composable
internal fun VelouraMixSettings(
    ui: VelouraMixSettingsUi,
    onAutoMix: (Boolean) -> Unit,
    onStyle: (TransitionStyle) -> Unit,
    onAutomaticLength: (Boolean) -> Unit,
    onSeconds: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Intelligent AutoMix", color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text("Leave each song partway through and bring the next one in at a good moment. Off: songs play in full, one after another.", color = VelouraMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
                }
                Switch(ui.autoMix, onAutoMix, colors = SwitchDefaults.colors(checkedTrackColor = VelouraBlue))
            }
        }
        SettingsCard {
            Text("Transition style", color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Text("How one song turns into the next. Each transition is either a crossfade or a DJ mix, never both.", color = VelouraMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp, bottom = 6.dp))
            TransitionStyle.entries.forEach { style ->
                val on = style == ui.style
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (on) Color(0xFFEAF1FF) else Color.Transparent)
                        .border(1.dp, if (on) VelouraBlue else VelouraLine, RoundedCornerShape(14.dp))
                        .clickable { onStyle(style) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(on, { onStyle(style) }, colors = RadioButtonDefaults.colors(selectedColor = VelouraBlue))
                    Column(Modifier.padding(start = 6.dp).weight(1f)) {
                        Text(style.label, color = VelouraNavy, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(style.detail, color = VelouraMuted, fontSize = 12.sp)
                    }
                }
            }
        }
        SettingsCard {
            Text("Transition length", color = VelouraNavy, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Text("How long the two songs overlap, whichever style is chosen.", color = VelouraMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp).clickable { onAutomaticLength(!ui.automaticLength) }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Automatic", color = VelouraNavy, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        if (ui.style.dj != null) "About 8 s for DJ styles (whole bars when the beats line up), so the effects have room to be heard."
                        else "About 3 s at a clear phrase or beat, about 6 s at a best-guess point.",
                        color = VelouraMuted, fontSize = 12.sp,
                    )
                }
                Switch(ui.automaticLength, onAutomaticLength, colors = SwitchDefaults.colors(checkedTrackColor = VelouraBlue))
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Fixed length", color = if (ui.automaticLength) VelouraMuted else VelouraNavy, modifier = Modifier.weight(1f))
                Text("${ui.seconds} s", color = if (ui.automaticLength) VelouraMuted else VelouraBlue, fontWeight = FontWeight.Black)
            }
            Slider(
                ui.seconds.toFloat(), { onSeconds(kotlin.math.round(it).toInt()) },
                enabled = !ui.automaticLength,
                valueRange = 1f..ui.maxSeconds.toFloat(), steps = ui.maxSeconds - 2,
            )
            Text(
                if (ui.automaticLength) "Turn off Automatic to choose your own length."
                else "Used for every transition, rounded to whole bars when both songs have a clear beat. A transition never takes more than a third of either song.",
                color = VelouraMuted, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

