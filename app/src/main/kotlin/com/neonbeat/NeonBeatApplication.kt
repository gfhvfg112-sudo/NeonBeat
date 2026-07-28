package com.neonbeat

import android.app.Application
import android.os.StrictMode
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point.
 *
 * Startup is kept lean on purpose: nothing here touches disk or the network on
 * the main thread, the media session is created by the service on first use,
 * and the library scan runs in WorkManager. Coil is configured through
 * [ImageLoaderFactory] so the loader is constructed lazily on first image
 * request rather than during `onCreate`.
 */
@HiltAndroidApp
class NeonBeatApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
    }

    /**
     * Artwork loader tuned for very large libraries.
     *
     * - Memory cache is capped at a share of the app heap so fast scrolling
     *   through thousands of covers cannot trigger an OOM.
     * - A bounded disk cache keeps decoded covers across restarts, which is what
     *   makes the grid feel instant on the second launch.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.20)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("artwork"))
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        // Hardware bitmaps keep artwork off the Java heap entirely.
        .allowHardware(true)
        .respectCacheHeaders(false)
        .build()

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .build(),
        )
    }
}
