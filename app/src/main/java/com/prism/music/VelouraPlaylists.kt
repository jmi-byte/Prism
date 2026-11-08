package com.prism.music

import com.prism.music.data.PrismLibrary
import com.prism.music.data.model.BrowseItem
import com.prism.music.data.model.PlaylistItem
import com.prism.music.data.model.Shelf
import com.prism.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Veloura's view of YouTube Music playlists: the ones already in the account's library, and the ones YouTube Music
 * offers on Home (its auto-generated mixes such as "My Supermix" or "Discover Mix", and recommended playlists).
 *
 * Saving works in two ways, because YouTube treats the two kinds differently:
 * - An ordinary playlist is saved to the account's library, so it keeps following the original.
 * - An auto-generated mix is not a real playlist (it only exists as a watch queue and changes over time), so its
 *   current songs are saved as a new private playlist in the account, named after the mix and the day it was saved.
 */
object VelouraPlaylists {
    /** How many songs a played or saved playlist takes. */
    const val SONG_LIMIT = 1000

    /** YouTube's auto-generated mixes and radios ("RD…"); its editorial playlists ("RDCLAK…") are ordinary ones. */
    fun isMix(item: PlaylistItem): Boolean = item.isMix || PrismLibrary.isMix(item.id)

    /** The account's own playlists, without Liked Music (Veloura shows liked songs separately). */
    fun accountPlaylists(library: List<BrowseItem>): List<PlaylistItem> =
        library.filterIsInstance<PlaylistItem>().filter { normal(it.id) != "LM" }.distinctBy { normal(it.id) }

    /**
     * Playlists offered on Home that are not already in the account's library, mixes first (they are the
     * auto-generated ones people most want to keep).
     */
    fun suggestions(shelves: List<Shelf>, library: List<BrowseItem>): List<PlaylistItem> {
        val saved = library.map { normal(it.id) }.toSet()
        return shelves.flatMap { it.items }.filterIsInstance<PlaylistItem>()
            .filter { normal(it.id) !in saved && !PrismLibrary.isPrismList(it.id) && normal(it.id) != "LM" }
            .distinctBy { normal(it.id) }
            .sortedBy { if (isMix(it)) 0 else 1 }
    }

    /** The name a saved copy of a mix gets: "My Supermix · 8 Nov 2026". */
    fun copyTitle(mixTitle: String, on: LocalDate): String =
        "$mixTitle · ${on.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))}"

    /** The songs that can go into a YouTube playlist (no podcast episodes or live streams), each once. */
    fun saveableIds(songs: List<Song>): List<String> =
        songs.filter { !it.isEpisode && !it.isLive && it.id.isNotBlank() }.map { it.id }.distinct()

    fun normal(id: String) = id.removePrefix("VL")

    /** The songs of [item], in its order: a mix's current watch queue, or the playlist's own songs. */
    suspend fun songs(c: AppContainer, item: PlaylistItem): List<Song> = withContext(Dispatchers.IO) {
        if (isMix(item)) c.radio(item.seedVideoId, item.id, item.params).songs
        else c.ytm.playlistAll(item.id, SONG_LIMIT).songs
    }

    sealed interface SaveResult {
        /** Added to the library; it follows the original playlist. */
        data object Followed : SaveResult
        /** A mix's current songs saved as a new playlist of the account's own. */
        data class Copied(val title: String, val songs: Int) : SaveResult
        data class Failed(val reason: String) : SaveResult
    }

    /** Saves [item] into the signed-in account (see the class comment for how), then refreshes the library. */
    suspend fun saveToAccount(c: AppContainer, item: PlaylistItem, today: LocalDate = LocalDate.now()): SaveResult {
        if (!c.settings.current.isLoggedIn) return SaveResult.Failed("Connect YouTube Music first")
        if (!isMix(item)) {
            return if (c.library.setSaved(item.id, true)) SaveResult.Followed
            else SaveResult.Failed("YouTube Music didn't accept the save")
        }
        val ids = runCatching { saveableIds(songs(c, item)) }.getOrElse {
            return SaveResult.Failed(it.message ?: "Couldn't load ${item.title}")
        }
        if (ids.isEmpty()) return SaveResult.Failed("${item.title} has no songs to save")
        val title = copyTitle(item.title, today)
        val created = runCatching {
            withContext(Dispatchers.IO) {
                c.ytm.createPlaylist(title, ids, description = "Saved from Veloura: YouTube Music's ${item.title} as it was on this day.")
            }
        }.getOrElse { return SaveResult.Failed(it.message ?: "YouTube Music didn't create the playlist") }
            ?: return SaveResult.Failed("YouTube Music didn't create the playlist")
        c.library.playlistChanged(created)
        return SaveResult.Copied(title, ids.size)
    }
}
