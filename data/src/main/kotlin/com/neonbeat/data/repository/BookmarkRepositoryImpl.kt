package com.neonbeat.data.repository

import com.neonbeat.core.common.di.IoDispatcher
import com.neonbeat.core.database.dao.BookmarkDao
import com.neonbeat.core.database.entity.BookmarkEntity
import com.neonbeat.core.model.Bookmark
import com.neonbeat.domain.repository.BookmarkRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-track bookmarks, used mainly for long recordings such as podcasts,
 * DJ sets and audiobooks where resuming at an exact point matters.
 */
@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) : BookmarkRepository {

    override fun bookmarks(songId: Long): Flow<List<Bookmark>> =
        bookmarkDao.bookmarks(songId)
            .map { rows -> rows.map { it.toBookmark() } }
            .flowOn(io)

    override suspend fun add(songId: Long, positionMs: Long, label: String) {
        withContext(io) {
            bookmarkDao.add(
                BookmarkEntity(
                    songId = songId,
                    positionMs = positionMs.coerceAtLeast(0L),
                    label = label,
                ),
            )
        }
    }

    override suspend fun delete(id: Long) {
        withContext(io) { bookmarkDao.delete(id) }
    }
}

private fun BookmarkEntity.toBookmark() = Bookmark(
    id = id,
    songId = songId,
    positionMs = positionMs,
    label = label,
)
