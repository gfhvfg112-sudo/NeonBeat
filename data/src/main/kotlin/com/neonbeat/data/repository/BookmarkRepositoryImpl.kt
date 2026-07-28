package com.neonbeat.data.repository

import com.neonbeat.core.common.di.IoDispatcher
import com.neonbeat.core.database.dao.BookmarkDao
import com.neonbeat.core.database.entity.BookmarkEntity
import com.neonbeat.core.model.Bookmark
import com.neonbeat.domain.repository.BookmarkRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Position bookmarks, mainly useful for long files such as mixes, DJ sets,
 * audiobooks and podcasts.
 */
@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BookmarkRepository {

    override fun bookmarks(): Flow<List<Bookmark>> =
        bookmarkDao.observeAll().map { list -> list.map { it.toBookmark() } }

    override fun bookmarksForSong(songId: Long): Flow<List<Bookmark>> =
        bookmarkDao.observeForSong(songId).map { list -> list.map { it.toBookmark() } }

    override suspend fun addBookmark(songId: Long, positionMs: Long, label: String?): Long =
        withContext(ioDispatcher) {
            bookmarkDao.insert(
                BookmarkEntity(
                    songId = songId,
                    positionMs = positionMs,
                    label = label,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }

    override suspend fun deleteBookmark(bookmarkId: Long) = withContext(ioDispatcher) {
        bookmarkDao.deleteById(bookmarkId)
    }

    private fun BookmarkEntity.toBookmark() = Bookmark(
        id = id,
        songId = songId,
        positionMs = positionMs,
        label = label,
        createdAt = createdAt,
    )
}
