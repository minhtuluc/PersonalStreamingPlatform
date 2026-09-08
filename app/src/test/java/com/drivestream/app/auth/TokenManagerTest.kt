package com.drivestream.app.auth

import com.drivestream.app.AppError
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class TokenManagerTest {

    private lateinit var tokenStorage: FakeTokenStorage
    private lateinit var mutableClock: MutableClock

    class FakeTokenStorage : TokenStorage {
        var storedTokens: AuthTokens? = null
        var storedUser: AuthUser? = null

        override fun saveTokens(tokens: AuthTokens) { storedTokens = tokens }
        override fun getTokens(): AuthTokens? = storedTokens
        override fun saveUser(user: AuthUser) { storedUser = user }
        override fun getUser(): AuthUser? = storedUser
        override fun clear() {
            storedTokens = null
            storedUser = null
        }
    }

    class MutableClock(var currentEpochSeconds: Long = 1000L) : Clock {
        override fun currentTimeSeconds(): Long = currentEpochSeconds
    }

    class CountingTokenRefresher(
        var response: Result<AuthTokens>,
        private val delayMs: Long = 0L
    ) : TokenRefresher {
        val callCount = AtomicInteger(0)

        override suspend fun refreshAccessToken(refreshToken: String?): Result<AuthTokens> {
            callCount.incrementAndGet()
            if (delayMs > 0) {
                delay(delayMs)
            }
            return response
        }
    }

    @BeforeEach
    fun setUp() {
        tokenStorage = FakeTokenStorage()
        mutableClock = MutableClock(currentEpochSeconds = 1000L)
    }

    @Nested
    @DisplayName("getValidAccessToken tests")
    inner class GetValidAccessTokenTests {

        @Test
        fun `returns existing valid token when remaining lifetime is well above 300s`() = runTest {
            // expires at 2000, current = 1000 -> 1000s > 300s
            val tokens = AuthTokens("valid_token", "refresh_token", 2000L)
            tokenStorage.saveTokens(tokens)

            val refresher = CountingTokenRefresher(Result.success(tokens))
            val tokenManager = TokenManager(tokenStorage, refresher, mutableClock)

            val result = tokenManager.getValidAccessToken()

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo("valid_token")
            assertThat(refresher.callCount.get()).isEqualTo(0)
        }

        @Test
        fun `proactively triggers refresh when remaining lifetime is less than 300s`() = runTest {
            // expires at 1250, current = 1000 -> 250s remaining (< 300s)
            val oldTokens = AuthTokens("old_token", "refresh_token", 1250L)
            tokenStorage.saveTokens(oldTokens)

            val newTokens = AuthTokens("new_refreshed_token", "refresh_token", 4600L)
            val refresher = CountingTokenRefresher(Result.success(newTokens))
            val tokenManager = TokenManager(tokenStorage, refresher, mutableClock)

            val result = tokenManager.getValidAccessToken()

            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo("new_refreshed_token")
            assertThat(refresher.callCount.get()).isEqualTo(1)
            assertThat(tokenStorage.storedTokens?.accessToken).isEqualTo("new_refreshed_token")
        }

        @Test
        fun `fails when no token exists in storage`() = runTest {
            val refresher = CountingTokenRefresher(Result.success(AuthTokens("tok", "ref", 2000L)))
            val tokenManager = TokenManager(tokenStorage, refresher, mutableClock)

            val result = tokenManager.getValidAccessToken()

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isInstanceOf(AppError.TokenExpired::class.java)
            assertThat(refresher.callCount.get()).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("Concurrent Deduplication Tests")
    inner class ConcurrencyTests {

        @Test
        fun `deduplicates concurrent refresh calls into a single actual refresh invocation`() = runTest {
            // Token expiring in 100s
            val oldTokens = AuthTokens("expiring_token", "refresh_token", 1100L)
            tokenStorage.saveTokens(oldTokens)

            val newTokens = AuthTokens("fresh_token", "refresh_token", 5000L)
            val refresher = CountingTokenRefresher(Result.success(newTokens), delayMs = 50L)
            val tokenManager = TokenManager(tokenStorage, refresher, mutableClock)

            // Launch 5 concurrent calls
            val deferreds = (1..5).map {
                async { tokenManager.getValidAccessToken() }
            }

            val results = deferreds.awaitAll()

            // All 5 should succeed with fresh_token
            results.forEach { result ->
                assertThat(result.isSuccess).isTrue()
                assertThat(result.getOrNull()).isEqualTo("fresh_token")
            }

            // Exactly 1 network refresh call was made!
            assertThat(refresher.callCount.get()).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("Error & Sign-Out Handling")
    inner class ErrorAndSignOutTests {

        @Test
        fun `clears storage and fails when refresh returns TokenExpired error`() = runTest {
            val oldTokens = AuthTokens("expired_token", "invalid_refresh", 1100L)
            tokenStorage.saveTokens(oldTokens)

            val error = AppError.TokenExpired(RuntimeException("OAuth revoked"))
            val refresher = CountingTokenRefresher(Result.failure(error))
            val tokenManager = TokenManager(tokenStorage, refresher, mutableClock)

            val result = tokenManager.getValidAccessToken()

            assertThat(result.isFailure).isTrue()
            assertThat(tokenStorage.storedTokens).isNull()
        }

        @Test
        fun `signOut clears all stored credentials and user info`() {
            val tokens = AuthTokens("t", "r", 2000L)
            val user = AuthUser("user@test.com", "User", null)
            tokenStorage.saveTokens(tokens)
            tokenStorage.saveUser(user)

            val refresher = CountingTokenRefresher(Result.success(tokens))
            val tokenManager = TokenManager(tokenStorage, refresher, mutableClock)

            assertThat(tokenManager.hasValidSession()).isTrue()

            tokenManager.signOut()

            assertThat(tokenStorage.storedTokens).isNull()
            assertThat(tokenStorage.storedUser).isNull()
            assertThat(tokenManager.hasValidSession()).isFalse()
        }
    }
}
