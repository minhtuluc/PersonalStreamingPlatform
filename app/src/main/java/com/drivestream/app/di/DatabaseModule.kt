package com.drivestream.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.drivestream.app.data.AppDatabase
import com.drivestream.app.data.DownloadedVideoDao
import com.drivestream.app.data.FavoriteDao
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

    private const val CREATE_FAVORITES_TABLE = "CREATE TABLE IF NOT EXISTS `favorites` (" +
        "`fileId` TEXT NOT NULL, " +
        "`fileName` TEXT NOT NULL, " +
        "`isFolder` INTEGER NOT NULL, " +
        "`fileSize` INTEGER NOT NULL, " +
        "`thumbnailUrl` TEXT, " +
        "`durationMs` INTEGER, " +
        "`resolution` TEXT, " +
        "`modifiedAtEpochMs` INTEGER NOT NULL, " +
        "`addedAt` INTEGER NOT NULL, " +
        "PRIMARY KEY(`fileId`))"

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_FAVORITES_TABLE)
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
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

    @Provides
    @Singleton
    fun provideFavoriteDao(database: AppDatabase): FavoriteDao {
        return database.favoriteDao()
    }
}
