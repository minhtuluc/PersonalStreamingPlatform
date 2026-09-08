package com.drivestream.app.di

import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import com.drivestream.app.player.BufferConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun provideLoadControl(): LoadControl {
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                BufferConfig.MIN_BUFFER_MS,
                BufferConfig.MAX_BUFFER_MS,
                BufferConfig.BUFFER_FOR_PLAYBACK_MS,
                BufferConfig.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setBackBuffer(
                BufferConfig.BACK_BUFFER_DURATION_MS,
                BufferConfig.RETAIN_BACK_BUFFER
            )
            .build()
    }
}
