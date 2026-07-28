package com.neonbeat.core.media.service

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the [MediaSource.Factory] used by the player.
 *
 * Local files go straight through [DefaultDataSource]. Network sources (SMB,
 * WebDAV, FTP, DLNA and generic HTTP streams surfaced by the NAS browser) are
 * wrapped in a bounded disk cache so scrubbing a remote track does not re-fetch
 * the file, and short dropouts never interrupt playback.
 */
@Singleton
class NeonMediaSourceFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cache: SimpleCache by lazy {
        SimpleCache(
            File(context.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        )
    }

    fun create(): MediaSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setUserAgent("NeonBeat/1.0")

        val upstream: DataSource.Factory = DefaultDataSource.Factory(context, http)

        val cached = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return DefaultMediaSourceFactory(context).setDataSourceFactory(cached)
    }

    fun release() = cache.release()

    private companion object {
        const val MAX_CACHE_BYTES = 512L * 1024 * 1024
    }
}
