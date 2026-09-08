package com.drivestream.app.auth

import com.drivestream.app.AppError
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

fun interface Clock {
    fun currentTimeSeconds(): Long
}

class SystemClock : Clock {
    override fun currentTimeSeconds(): Long = System.currentTimeMillis() / MILLIS_PER_SECOND

    companion object {
        private const val MILLIS_PER_SECOND = 1000L
    }
}

fun interface TokenRefresher {
    suspend fun refreshAccessToken(refreshToken: String?): Result<AuthTokens>
}

class TokenManager(
    private val tokenStorage: TokenStorage,
    private val tokenRefresher: TokenRefresher,
    private val clock: Clock = SystemClock()
) {
    private val refreshMutex = Mutex()

    companion object {
        const val REFRESH_THRESHOLD_SECONDS = 300L
    }

    suspend fun getValidAccessToken(): Result<String> {
        val currentTokens = tokenStorage.getTokens()
            ?: return Result.failure(AppError.TokenExpired(IllegalStateException("No token found in storage")))

        val remainingSeconds = currentTokens.expiresAtEpochSeconds - clock.currentTimeSeconds()
        return if (remainingSeconds > REFRESH_THRESHOLD_SECONDS) {
            Result.success(currentTokens.accessToken)
        } else {
            Timber.d("Token remaining lifetime is below threshold. Refreshing proactively.")
            refreshAccessToken()
        }
    }

    suspend fun refreshAccessToken(): Result<String> = refreshMutex.withLock {
        // Double-check after acquiring lock to deduplicate concurrent requests
        val freshTokens = tokenStorage.getTokens()
        if (freshTokens != null) {
            val remainingSeconds = freshTokens.expiresAtEpochSeconds - clock.currentTimeSeconds()
            if (remainingSeconds > REFRESH_THRESHOLD_SECONDS) {
                Timber.d("Token was already refreshed by another concurrent job. Reusing.")
                return Result.success(freshTokens.accessToken)
            }
        }

        val storedTokens = freshTokens ?: tokenStorage.getTokens()
        val refreshToken = storedTokens?.refreshToken

        Timber.d("Executing token refresh call...")
        val refreshResult = tokenRefresher.refreshAccessToken(refreshToken)

        refreshResult.fold(
            onSuccess = { newTokens ->
                tokenStorage.saveTokens(newTokens)
                Timber.i("Token refreshed successfully and updated in secure storage.")
                Result.success(newTokens.accessToken)
            },
            onFailure = { error ->
                Timber.e("Token refresh failed: %s", error.message)
                if (error is AppError.TokenExpired) {
                    tokenStorage.clear()
                }
                Result.failure(error)
            }
        )
    }

    fun hasValidSession(): Boolean {
        val tokens = tokenStorage.getTokens() ?: return false
        val remaining = tokens.expiresAtEpochSeconds - clock.currentTimeSeconds()
        return remaining > 0 || !tokens.refreshToken.isNullOrBlank()
    }

    fun getStoredUser(): AuthUser? = tokenStorage.getUser()

    fun saveSession(user: AuthUser, tokens: AuthTokens) {
        tokenStorage.saveUser(user)
        tokenStorage.saveTokens(tokens)
        Timber.i("User session saved securely for: %s", user.email)
    }

    fun signOut() {
        tokenStorage.clear()
        Timber.i("User session cleared from secure storage.")
    }
}
