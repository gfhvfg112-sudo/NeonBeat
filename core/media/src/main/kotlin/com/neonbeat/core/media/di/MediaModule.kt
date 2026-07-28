package com.neonbeat.core.media.di

import android.content.Context
import com.neonbeat.core.media.service.NeonMediaSourceFactory
import com.neonbeat.core.media.service.NeonPlayerFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/** Wiring for the playback layer. */
@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    /**
     * Application-lifetime scope used by playback controllers.
     *
     * [SupervisorJob] means a failure in one controller (say, the sleep timer)
     * never cancels statistics writing.
     */
    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun providePlayerFactory(
        @ApplicationContext context: Context,
        mediaSourceFactory: NeonMediaSourceFactory,
    ): NeonPlayerFactory = NeonPlayerFactory(context, mediaSourceFactory)
}
