package com.drivestream.app.di

import android.content.Context
import androidx.room.Room
import com.drivestream.app.data.AppDatabase
import com.drivestream.app.data.DownloadedVideoDao
import com.drivestream.app.data.FileCacheDao
import com.drivestream.app.data.WatchHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideWatchHistoryDao(database: AppDatabase): WatchHistoryDao {
        return database.watchHistoryDao()
    }

    @Provides
    @Singleton
    fun provideFileCacheDao(database: AppDatabase): FileCacheDao {
        return database.fileCacheDao()
    }

    @Provides
    @Singleton
    fun provideDownloadedVideoDao(database: AppDatabase): DownloadedVideoDao {
        return database.downloadedVideoDao()
    }
}
