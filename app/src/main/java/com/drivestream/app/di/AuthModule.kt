package com.drivestream.app.di

import android.content.Context
import com.drivestream.app.auth.EncryptedTokenStorage
import com.drivestream.app.auth.GoogleAuthManager
import com.drivestream.app.auth.TokenManager
import com.drivestream.app.auth.TokenRefresher
import com.drivestream.app.auth.TokenStorage
import com.drivestream.app.network.TokenInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideTokenStorage(
        @ApplicationContext context: Context
    ): TokenStorage = EncryptedTokenStorage(context)

    @Provides
    @Singleton
    fun provideGoogleAuthManager(
        @ApplicationContext context: Context
    ): GoogleAuthManager = GoogleAuthManager(context)

    @Provides
    @Singleton
    fun provideTokenRefresher(
        googleAuthManager: GoogleAuthManager
    ): TokenRefresher = googleAuthManager

    @Provides
    @Singleton
    fun provideTokenManager(
        tokenStorage: TokenStorage,
        tokenRefresher: TokenRefresher
    ): TokenManager = TokenManager(tokenStorage, tokenRefresher)

    @Provides
    @Singleton
    fun provideTokenInterceptor(
        tokenManager: TokenManager
    ): TokenInterceptor = TokenInterceptor(tokenManager)
}
