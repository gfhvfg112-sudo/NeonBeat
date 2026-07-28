package com.neonbeat.data.di

import com.neonbeat.data.repository.BackupRepositoryImpl
import com.neonbeat.data.repository.BookmarkRepositoryImpl
import com.neonbeat.data.repository.LyricsRepositoryImpl
import com.neonbeat.data.repository.MusicRepositoryImpl
import com.neonbeat.data.repository.PlaylistRepositoryImpl
import com.neonbeat.data.repository.SearchRepositoryImpl
import com.neonbeat.data.repository.StatsRepositoryImpl
import com.neonbeat.domain.repository.BackupRepository
import com.neonbeat.domain.repository.BookmarkRepository
import com.neonbeat.domain.repository.LyricsRepository
import com.neonbeat.domain.repository.MusicRepository
import com.neonbeat.domain.repository.PlaylistRepository
import com.neonbeat.domain.repository.SearchRepository
import com.neonbeat.domain.repository.StatsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds every domain repository interface to its data-layer implementation.
 *
 * The domain module never depends on the data module: features inject the
 * interfaces, and only this binding module knows the concrete classes. That is
 * what lets tests swap in fakes by replacing a single Hilt module.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindMusicRepository(impl: MusicRepositoryImpl): MusicRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindLyricsRepository(impl: LyricsRepositoryImpl): LyricsRepository

    @Binds
    @Singleton
    abstract fun bindStatsRepository(impl: StatsRepositoryImpl): StatsRepository

    @Binds
    @Singleton
    abstract fun bindBookmarkRepository(impl: BookmarkRepositoryImpl): BookmarkRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository
}
