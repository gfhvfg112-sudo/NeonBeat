package com.neonbeat.core.media.service

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.neonbeat.core.media.browse.BrowseTree
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serves the media browse tree consumed by Android Auto, Wear OS, Assistant and
 * any other `MediaBrowser` client, and registers NeonBeat's custom session
 * commands (A-B repeat, sleep timer, favorite toggle).
 */
@Singleton
class MediaLibrarySessionCallback @Inject constructor(
    private val browseTree: BrowseTree,
) : MediaLibrarySession.Callback {

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val available = SessionCommands.Builder()
            .add(SessionCommand(COMMAND_TOGGLE_FAVORITE, android.os.Bundle.EMPTY))
            .add(SessionCommand(COMMAND_SLEEP_TIMER, android.os.Bundle.EMPTY))
            .add(SessionCommand(COMMAND_AB_REPEAT, android.os.Bundle.EMPTY))
            .add(SessionCommand(COMMAND_SMART_SHUFFLE, android.os.Bundle.EMPTY))
            .build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(available)
            .build()
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(LibraryResult.ofItem(browseTree.root(), params))

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        Futures.immediateFuture(
            LibraryResult.ofItemList(browseTree.children(parentId, page, pageSize), params),
        )

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val item = browseTree.item(mediaId)
            ?: return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
        return Futures.immediateFuture(LibraryResult.ofItem(item, null))
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> {
        session.notifySearchResultChanged(browser, query, browseTree.searchCount(query), params)
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        Futures.immediateFuture(
            LibraryResult.ofItemList(browseTree.search(query, page, pageSize), params),
        )

    companion object {
        const val COMMAND_TOGGLE_FAVORITE = "com.neonbeat.TOGGLE_FAVORITE"
        const val COMMAND_SLEEP_TIMER = "com.neonbeat.SLEEP_TIMER"
        const val COMMAND_AB_REPEAT = "com.neonbeat.AB_REPEAT"
        const val COMMAND_SMART_SHUFFLE = "com.neonbeat.SMART_SHUFFLE"

        /** Root ids of the browse tree. */
        const val ROOT_ID = "root"
        const val NODE_ALBUMS = "albums"
        const val NODE_ARTISTS = "artists"
        const val NODE_PLAYLISTS = "playlists"
        const val NODE_FAVORITES = "favorites"
        const val NODE_RECENT = "recent"

        fun browsableNode(id: String, title: String): MediaItem = MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build(),
            )
            .build()
    }
}
