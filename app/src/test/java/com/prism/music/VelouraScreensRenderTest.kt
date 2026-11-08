package com.prism.music

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background as androidxBackground
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Veloura's screens drawn with sample data (the songs in the owner's design), to app/build/veloura-previews/, so they
 * can be compared with the design without a phone. Covers have no network here, so they show their gradient stand-ins.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w392dp-h850dp-xxhdpi")
class VelouraScreensRenderTest {
    @get:Rule val compose = createComposeRule()

    private fun shoot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            MaterialTheme(colorScheme = VelouraColors) {
                Box(Modifier.size(392.dp, 850.dp).testTag("screen")) { content() }
            }
        }
        compose.waitForIdle()
        val bmp: Bitmap = compose.onNodeWithTag("screen").captureToImage().asAndroidBitmap()
        val dir = File("build/veloura-previews").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Not blank: the screen drew something other than its background.
        val colors = HashSet<Int>()
        for (y in 0 until bmp.height step 9) for (x in 0 until bmp.width step 9) colors += bmp.getPixel(x, y)
        assertTrue("$name drew", colors.size > 20)
    }

    private val sample = VelouraQueueUi(
        current = QueueSongUi("a", 0, "Feel Alive", "Nova Drive", null, 222_000),
        mixOut = "2:10", positionMs = 68_000, durationMs = 222_000, inMs = 28_000, outMs = 130_000,
        next = QueueSongUi("b", 1, "Sunlight Again", "Echo Pacific", null, 204_000),
        transitionKind = "Beat matched", mixIn = "0:18", match = "BPM 122 → 122 · Key 8A → 8A ✓",
        planDetails = listOf("Leaves this song at 2:10: on a measured beat, after its highlight.", "The next song comes in at 0:18.", "Blend length: automatic.", "Beats lined up: both tempos are clear and close.", "DJ effects: Auto."),
        upNext = listOf(
            QueueSongUi("b", 1, "Sunlight Again", "Echo Pacific", null, 204_000),
            QueueSongUi("c", 2, "Higher Ground", "Atlas Lane", null, 160_000),
            QueueSongUi("d", 3, "Keep It Moving", "Solar State", null, 175_000),
        ),
    )

    @Test fun home() = shoot("home") {
        Column(Modifier.fillMaxSize()) {
            VelouraBrandBar(null, {}, {})
            Box(Modifier.weight(1f)) {
                VelouraHome(
                    VelouraHomeUi(
                        loggedIn = true,
                        playlists = listOf(
                            PlaylistCard("1", "Party Hits", "120 songs", null), PlaylistCard("2", "Road Trip", "90 songs", null),
                            PlaylistCard("3", "Workout", "80 songs", null), PlaylistCard("4", "Chill", "64 songs", null),
                        ),
                        autoMixOn = true,
                    ),
                    {}, {}, {}, {}, {}, {},
                )
            }
            VelouraTabBar(VelouraTabItem.HOME) {}
        }
    }

    @Test fun nowPlaying() = shoot("now-playing") {
        VelouraNowPlaying(
            VelouraNowPlayingUi(
                "Feel Alive", "Nova Drive", null, liked = false, playing = true, positionMs = 68_000, durationMs = 222_000,
                autoMixOn = true, status = "Next mix in ~1:02 · Beat-aligned · Smooth blend · DJ: Auto", shuffle = false, repeatMode = 0,
            ),
            {}, emptyList(), {}, {}, {}, {}, {}, {}, {}, {},
        )
    }

    /** The cover switched to lyrics: the panel's place and ground (Prism's own lyrics panel needs the app, so a stand-in). */
    @Test fun nowPlayingLyrics() = shoot("now-playing-lyrics") {
        VelouraNowPlaying(
            VelouraNowPlayingUi(
                "Feel Alive", "Nova Drive", null, liked = true, playing = true, positionMs = 68_000, durationMs = 222_000,
                autoMixOn = true, status = "Next mix in ~1:02 · Phrase-aligned · DJ · Auto · ~8 s", shuffle = false, repeatMode = 0,
            ),
            {}, emptyList(), {}, {}, {}, {}, {}, {}, {}, {},
            showLyrics = true, onLyrics = {},
            lyrics = { m ->
                Column(m.padding(22.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                    androidx.compose.material3.Text("I can feel it in the night", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.45f), fontSize = 22.sp)
                    androidx.compose.material3.Text("Every light is coming alive", color = androidx.compose.ui.graphics.Color.White, fontSize = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
                    androidx.compose.material3.Text("Never stop, never stop moving", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.45f), fontSize = 22.sp)
                }
            },
        )
    }

    @Test fun similar() = shoot("more-like-this") {
        VelouraSimilar(
            VelouraSimilarUi(
                "Brown Munde", "AP Dhillon", null, "Punjabi", "Punjabi & Hindi songs", null, null,
                listOf(
                    SimilarRowUi("1", "Excuses", "AP Dhillon", null, "Punjabi", "98 BPM", "8A", 91, "Same key, same tempo as Brown Munde · Punjabi · Related on YouTube Music"),
                    SimilarRowUi("2", "Lover", "Diljit Dosanjh", null, "Punjabi", "100 BPM", "9A", 86),
                    SimilarRowUi("3", "Kesariya", "Arijit Singh", null, "Hindi", "93 BPM", "8B", 78),
                    SimilarRowUi("4", "Insane", "AP Dhillon", null, "Punjabi", null, null, 71),
                ),
            ),
            {}, {}, {}, {},
        )
    }

    @Test fun storyCard() = shoot("story-card") {
        Box(Modifier.fillMaxSize().padding(40.dp)) {
            VelouraStoryCard("Brown Munde", "AP Dhillon, Gurinder Gill, Shinda Kahlon", null, "Brown munde, now we roll in style")
        }
    }

    @Test fun mixSettingsAll() = shoot("mix-settings-full") {
        VelouraMixSettings(VelouraMixSettingsUi(true, TransitionStyle.DJ_CLUB, false, 8, 12, true, true, 70), {}, {}, {}, {})
    }

    @Test fun queue() = shoot("queue") {
        VelouraQueue(sample, 0, {}, {}, emptyList(), {}, {}, {}, {})
    }

    @Test fun mixSettings() = shoot("mix-settings") {
        Column(Modifier.fillMaxSize()) {
            VelouraTitleBar("Mix Settings", com.prism.music.ui.theme.PrismIcons.ArrowBack, "Back", {})
            Box(Modifier.weight(1f)) {
                VelouraMixSettings(VelouraMixSettingsUi(true, TransitionStyle.DJ_AUTO, true, 6, 12), {}, {}, {}, {})
            }
        }
    }

    @Test fun transitionStylesMapToTheSettings() {
        // One choice over two stored settings: crossfade is DJ effects off, whatever style was last picked.
        assertTrue(TransitionStyle.of(false, com.prism.music.data.prefs.DjStyle.CLUB) == TransitionStyle.CROSSFADE)
        com.prism.music.data.prefs.DjStyle.entries.forEach { s ->
            val t = TransitionStyle.of(true, s)
            assertTrue("$s", t.dj == s)
        }
        assertTrue(TransitionStyle.entries.size == com.prism.music.data.prefs.DjStyle.entries.size + 1)
        assertTrue(transitionLength(false, 9, true, false) == "9 s")
        assertTrue(transitionLength(true, 9, true, false) == "~8 s")
        assertTrue(transitionLength(true, 9, false, true) == "~6 s")
        assertTrue(transitionLength(true, 9, false, false) == "~3 s")
    }

    @Test fun party() = shoot("party") {
        VelouraParty(
            VelouraPartyUi(
                "http://192.168.1.20:8765/p/k7m2qx",
                listOf(
                    PartyAddedUi("1", "Brown Munde", "AP Dhillon", null, "Simran"),
                    PartyAddedUi("2", "Levitating", "Dua Lipa", null, "Jas"),
                ),
                null,
            ),
            {}, {}, {}, {}, {},
        )
    }

    @Test fun savedMixes() = shoot("saved-mixes") {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            SavedMixRow(SavedMixUi("1", "Friday night", "18 songs · DJ · Club · 7 Nov", listOf(null, null, null, null)), {}, {}, {})
            SavedMixRow(SavedMixUi("2", "Road trip", "32 songs · Crossfade · 2 Nov", listOf(null)), {}, {}, {})
        }
    }

    private fun dark(block: () -> Unit) { VelouraTheme.dark = true; try { block() } finally { VelouraTheme.dark = false } }

    @Test fun homeDark() = dark {
        shoot("home-dark") {
            Column(Modifier.fillMaxSize().androidxBackground(VelouraBackground)) {
                VelouraBrandBar(null, {}, {})
                Box(Modifier.weight(1f)) {
                    VelouraHome(
                        VelouraHomeUi(
                            loggedIn = true,
                            playlists = listOf(PlaylistCard("1", "Party Hits", "120 songs", null), PlaylistCard("2", "Road Trip", "90 songs", null), PlaylistCard("3", "Workout", "80 songs", null)),
                            autoMixOn = true, mood = "Evening build" to "Energy rising into the night, Club blends.", moodDone = "DJ · Club transitions. Arranged as a set: Build up.",
                        ),
                        {}, {}, {}, {}, {}, {},
                    )
                }
                VelouraTabBar(VelouraTabItem.HOME) {}
            }
        }
    }

    @Test fun mixSettingsDark() = dark {
        shoot("mix-settings-dark") {
            Box(Modifier.fillMaxSize().androidxBackground(VelouraBackground)) {
                VelouraMixSettings(VelouraMixSettingsUi(true, TransitionStyle.DJ_MASHUP, true, 6, 12), {}, {}, {}, {}, theme = 2)
            }
        }
    }

    @Test fun nowPlayingDark() = dark {
        shoot("now-playing-dark") {
            VelouraNowPlaying(
                VelouraNowPlayingUi("Desi Da Drum", "Amrit Maan", null, liked = true, playing = true, positionMs = 80_000, durationMs = 213_000, autoMixOn = true, status = "Next mix in ~1:08 · Phrase-aligned · DJ · Club · 9 s", shuffle = false, repeatMode = 0),
                {}, emptyList(), {}, {}, {}, {}, {}, {}, {}, {},
            )
        }
    }

    @Test fun fullLyrics() = shoot("lyrics-full-screen") {
        VelouraFullLyrics(
            "Desi Da Drum", "Amrit Maan", null, playing = true, positionMs = 80_000, durationMs = 213_000,
            {}, {}, {}, {}, {}, onTextSize = {},
            lyrics = { m ->
                Column(m, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(22.dp, androidx.compose.ui.Alignment.CenterVertically)) {
                    androidx.compose.material3.Text("Kehnde lok pind de", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f), fontSize = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    androidx.compose.material3.Text("Akh vaunda si purana jehde tham ni", color = androidx.compose.ui.graphics.Color.White, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
                    androidx.compose.material3.Text("Pavve jidde kad waaliye", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f), fontSize = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    androidx.compose.material3.Text("Dhol gayi ae desi da", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f), fontSize = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
        )
    }

    @Test fun queuePlan() = shoot("queue-plan") {
        VelouraQueue(sample, 1, {}, {}, emptyList(), {}, {}, {}, {})
    }

    /** The launcher icon from its real drawables (background under foreground), as a launcher's mask crops it. */
    @Test fun launcherIcon() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val px = 432
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        for (id in listOf(R.drawable.ic_veloura_background, R.drawable.ic_veloura_foreground)) {
            ctx.getDrawable(id)!!.apply { setBounds(0, 0, px, px); draw(canvas) }
        }
        // The visible part: the middle 72 of 108.
        val shown = Bitmap.createBitmap(bmp, px / 6, px / 6, px * 2 / 3, px * 2 / 3)
        val dir = File("build/veloura-previews").apply { mkdirs() }
        File(dir, "launcher-icon.png").outputStream().use { shown.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val mono = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        ctx.getDrawable(R.drawable.ic_veloura_monochrome)!!.apply { setBounds(0, 0, px, px); draw(android.graphics.Canvas(mono)) }
        File(dir, "launcher-icon-mono.png").outputStream().use { mono.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // The status bar icon: white on nothing, the V inside its 24 dp.
        val note = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        ctx.getDrawable(R.drawable.ic_notification_veloura)!!.apply { setBounds(0, 0, 96, 96); draw(android.graphics.Canvas(note)) }
        File(dir, "notification-icon.png").outputStream().use { note.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("V drawn", (0 until 96).any { x -> android.graphics.Color.alpha(note.getPixel(x, 48)) > 200 })
        assertTrue("fits", (0 until 96).all { android.graphics.Color.alpha(note.getPixel(it, 0)) < 10 && android.graphics.Color.alpha(note.getPixel(0, it)) < 10 })
        // The ribbon is there: the middle is lit, the corner is the dark background.
        val mid = bmp.getPixel(px * 46 / 108, px * 50 / 108)
        assertTrue("ribbon drawn", android.graphics.Color.blue(mid) > 150)
    }
}
