package com.neonbeat.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.neonbeat.core.database.dao.BookmarkDao
import com.neonbeat.core.database.dao.FolderDao
import com.neonbeat.core.database.dao.LyricsDao
import com.neonbeat.core.database.dao.PlaylistDao
import com.neonbeat.core.database.dao.QueueDao
import com.neonbeat.core.database.dao.SongDao
import com.neonbeat.core.database.dao.StatsDao
import com.neonbeat.core.database.entity.BookmarkEntity
import com.neonbeat.core.database.entity.FolderEntity
import com.neonbeat.core.database.entity.LyricsEntity
import com.neonbeat.core.database.entity.PlayHistoryEntity
import com.neonbeat.core.database.entity.PlaylistEntity
import com.neonbeat.core.database.entity.PlaylistSongEntity
import com.neonbeat.core.database.entity.QueueItemEntity
import com.neonbeat.core.database.entity.SongEntity
import com.neonbeat.core.database.entity.SongFtsEntity
import com.neonbeat.core.database.entity.SongStatsEntity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The single Room database backing the whole app.
 *
 * Write-ahead logging plus a relaxed sync mode keeps the initial 100k-song
 * import fast without risking the index: a corrupted cache is always
 * recoverable by re-scanning MediaStore.
 */
@Database(
    entities = [
        SongEntity::class,
        SongFtsEntity::class,
        SongStatsEntity::class,
        PlayHistoryEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        QueueItemEntity::class,
        FolderEntity::class,
        LyricsEntity::class,
        BookmarkEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class NeonBeatDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun statsDao(): StatsDao
    abstract fun queueDao(): QueueDao
    abstract fun folderDao(): FolderDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        const val NAME = "neonbeat.db"
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NeonBeatDatabase =
        Room.databaseBuilder(context, NeonBeatDatabase::class.java, NeonBeatDatabase.NAME)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            // Room 2.6 exposes the no-argument overload; the cache is always
            // rebuildable from MediaStore, so a destructive fallback is safe.
            .fallbackToDestructiveMigration()
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.execSQL("PRAGMA synchronous = NORMAL")
                        db.execSQL("PRAGMA temp_store = MEMORY")
                        db.execSQL("PRAGMA cache_size = -8000")
                    }
                },
            )
            .build()

    @Provides fun songDao(db: NeonBeatDatabase): SongDao = db.songDao()
    @Provides fun playlistDao(db: NeonBeatDatabase): PlaylistDao = db.playlistDao()
    @Provides fun statsDao(db: NeonBeatDatabase): StatsDao = db.statsDao()
    @Provides fun queueDao(db: NeonBeatDatabase): QueueDao = db.queueDao()
    @Provides fun folderDao(db: NeonBeatDatabase): FolderDao = db.folderDao()
    @Provides fun lyricsDao(db: NeonBeatDatabase): LyricsDao = db.lyricsDao()
    @Provides fun bookmarkDao(db: NeonBeatDatabase): BookmarkDao = db.bookmarkDao()
}
