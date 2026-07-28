package com.neonbeat.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.neonbeat.core.common.di.IoDispatcher
import com.neonbeat.core.database.dao.FolderDao
import com.neonbeat.core.database.dao.SongDao
import com.neonbeat.core.database.dao.StatsDao
import com.neonbeat.core.database.entity.FolderEntity
import com.neonbeat.core.datastore.SettingsRepository
import com.neonbeat.data.mapper.toAlbum
import com.neonbeat.data.mapper.toArtist
import com.neonbeat.data.mapper.toFolder
import com.neonbeat.data.mapper.toGenre
import com.neonbeat.data.mapper.toSong
import com.neonbeat.data.scanner.MediaStoreScanner
import com.neonbeat.data.tag.TagEditor
import com.neonbeat.domain.repository.MusicRepository
import com.neonbeat.domain.repository.ScanResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

/**
 * Offline-first implementation of [MusicRepository].
 *
 * Room is the single source of truth: the UI always renders from the local
 * index, and MediaStore is only consulted during a scan. That keeps the library
 * instant on cold start even with six-figure song counts.
 */
@Singleton
class MusicRepositoryImpl @Inject constructor(
    private val songDao: SongDao,
    private val statsDao: StatsDao,
    private val folderDao: FolderDao,
    private val scanner: MediaStoreScanner,
    private val tagEditor: TagEditor,
    private val settingsRepository: SettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : MusicRepository {

    override fun songs(): Flow<PagingData<com.neonbeat.core.model.Song>> =
        pager { songDao.pagingSongsByTitle() }.map { data -> data.map { it.toSong() } }

    override fun recentlyAdded() =
        pager { songDao.pagingRecentlyAdded() }.map { data -> data.map { it.toSong() } }

    override fun recentlyPlayed() =
        pager { songDao.pagingRecentlyPlayed() }.map { data -> data.map { it.toSong() } }

    override fun mostPlayed() =
        pager { songDao.pagingMostPlayed() }.map { data -> data.map { it.toSong() } }

    override fun favorites() =
        pager { songDao.pagingFavorites() }.map { data -> data.map { it.toSong() } }

    override fun albums() =
        pager { songDao.pagingAlbums() }.map { data -> data.map { it.toAlbum() } }

    override fun artists() =
        pager { songDao.pagingArtists() }.map { data -> data.map { it.toArtist() } }

    override fun genres() = songDao.genres().map { list -> list.map { it.toGenre() } }

    override fun folders() = songDao.folders().map { list -> list.map { it.toFolder() } }

    override fun songsInAlbum(albumId: Long) =
        songDao.songsInAlbum(albumId).map { list -> list.map { it.toSong() } }

    override fun songsByArtist(artistId: Long) =
        songDao.songsByArtist(artistId).map { list -> list.map { it.toSong() } }

    override fun songsInGenre(genre: String) =
        songDao.songsInGenre(genre).map { list -> list.map { it.toSong() } }

    override fun songsInFolder(path: String) =
        songDao.songsInFolder(path).map { list -> list.map { it.toSong() } }

    override fun songCount() = songDao.songCount()

    override suspend fun songById(id: Long) = withContext(io) { songDao.songById(id)?.toSong() }

    override suspend fun songsByIds(ids: List<Long>) = withContext(io) {
        // Preserve caller ordering; SQL IN() does not guarantee it.
        val byId = songDao.songsByIds(ids).associateBy { it.id }
        ids.mapNotNull { byId[it]?.toSong() }
    }

    override suspend fun allSongIds() = withContext(io) { songDao.allSongIds() }

    /**
     * Reconciles the Room index with MediaStore.
     *
     * Rows are applied batch by batch, so the library becomes usable while the
     * scan is still running, and anything MediaStore no longer reports is
     * pruned at the end in a single statement.
     */
    override suspend fun rescan(force: Boolean): ScanResult = withContext(io) {
        val settings = settingsRepository.settings.first()
        val seenIds = ArrayList<Long>(INITIAL_CAPACITY)
        val folders = LinkedHashSet<String>()
        var added = 0

        val elapsed = measureTimeMillis {
            scanner.scan(minDurationSeconds = settings.ignoreShortTracksSeconds) { batch ->
                songDao.applyScanBatch(batch.songs)
                batch.songs.forEach {
                    seenIds += it.id
                    folders += it.folderPath
                }
                added += batch.songs.size
            }

            val noMedia = if (settings.respectNoMedia) scanner.findNoMediaFolders(folders) else emptySet()
            folderDao.upsertAll(
                folders.map { path ->
                    FolderEntity(
                        path = path,
                        name = path.substringAfterLast('/'),
                        isHidden = path in settings.hiddenFolders,
                        hasNoMedia = path in noMedia,
                    )
                },
            )

            if (seenIds.isNotEmpty()) songDao.deleteMissing(seenIds)
        }

        ScanResult(added = added, updated = 0, removed = 0, durationMs = elapsed)
    }

    override suspend fun setFolderHidden(path: String, hidden: Boolean) = withContext(io) {
        folderDao.setHidden(path, hidden)
        val current = settingsRepository.settings.first().hiddenFolders
        settingsRepository.setHiddenFolders(if (hidden) current + path else current - path)
    }

    override suspend fun toggleFavorite(songId: Long) = withContext(io) {
        statsDao.toggleFavorite(songId)
    }

    override fun isFavorite(songId: Long): Flow<Boolean> =
        statsDao.isFavorite(songId).map { it == true }

    override suspend fun deleteSongs(songIds: List<Long>): Int = withContext(io) {
        val deleted = tagEditor.deleteFiles(songsByIdsRaw(songIds))
        songDao.deleteByIds(songIds)
        deleted
    }

    override suspend fun renameFile(songId: Long, newName: String): Boolean = withContext(io) {
        val song = songDao.songById(songId) ?: return@withContext false
        tagEditor.rename(song, newName)
    }

    override suspend fun moveFiles(songIds: List<Long>, targetFolder: String): Int = withContext(io) {
        tagEditor.move(songsByIdsRaw(songIds), targetFolder)
    }

    override suspend fun updateTags(songId: Long, tags: Map<String, String>): Boolean = withContext(io) {
        val song = songDao.songById(songId) ?: return@withContext false
        tagEditor.writeTags(song, tags)
    }

    override suspend fun findDuplicates(): List<List<com.neonbeat.core.model.Song>> = withContext(io) {
        songDao.findDuplicates()
            .groupBy { Triple(it.titleKey, it.artist, it.durationMs / 1000) }
            .values
            .map { group -> group.map { it.toSong() } }
            .filter { it.size > 1 }
    }

    override suspend fun findMissingArtwork() = withContext(io) {
        songDao.findMissingArtwork().map { it.toSong() }
    }

    private suspend fun songsByIdsRaw(ids: List<Long>) = songDao.songsByIds(ids)

    /**
     * Shared Paging configuration.
     *
     * A small page with a generous prefetch keeps scrolling smooth at 120 Hz
     * while never holding more than a few hundred rows in memory.
     */
    private fun <T : Any> pager(source: () -> androidx.paging.PagingSource<Int, T>) = Pager(
        config = PagingConfig(
            pageSize = 60,
            prefetchDistance = 120,
            initialLoadSize = 120,
            maxSize = 600,
            enablePlaceholders = true,
        ),
        pagingSourceFactory = source,
    ).flow

    private companion object {
        const val INITIAL_CAPACITY = 4096
    }
}
