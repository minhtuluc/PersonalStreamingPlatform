package com.drivestream.app.player

import androidx.media3.datasource.DataSource
import com.drivestream.app.auth.TokenManager
import com.drivestream.app.network.RateLimiter
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GDriveDataSourceFactory @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val tokenManager: TokenManager,
    private val rateLimiter: RateLimiter
) : DataSource.Factory {

    internal var endpointTemplate: String = GDriveDataSource.DEFAULT_DRIVE_DOWNLOAD_ENDPOINT

    override fun createDataSource(): DataSource {
        return GDriveDataSource(
            okHttpClient = okHttpClient,
            tokenManager = tokenManager,
            rateLimiter = rateLimiter,
            endpointTemplate = endpointTemplate
        )
    }
}
