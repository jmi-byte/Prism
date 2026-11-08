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
        val colors = (0 until 40).map { bmp.getPixel(bmp.width * (it % 8 + 1) / 9, bmp.height * (it / 8 + 1) / 6) }.toSet()
        assertTrue("$name drew", colors.size > 3)
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

    @Test fun queue() = shoot("queue") {
        VelouraQueue(sample, 0, {}, {}, emptyList(), {}, {}, {}, {})
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
        // The ribbon is there: the middle is lit, the corner is the dark background.
        val mid = bmp.getPixel(px * 46 / 108, px * 50 / 108)
        assertTrue("ribbon drawn", android.graphics.Color.blue(mid) > 150)
    }
}
