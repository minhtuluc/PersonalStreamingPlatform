package com.drivestream.app.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.IOException

class RateLimiterTest {

    private lateinit var rateLimiter: RateLimiter

    @BeforeEach
    fun setUp() {
        rateLimiter = RateLimiter()
    }

    @Test
    @DisplayName("acquire succeeds without errors under normal conditions")
    fun acquireSucceeds() = runTest {
        rateLimiter.acquire()
        rateLimiter.acquire()
    }

    @Test
    @DisplayName("executeWithRetry succeeds immediately when block succeeds")
    fun executeWithRetrySuccess() = runTest {
        var callCount = 0
        val result = rateLimiter.executeWithRetry(
            policy = RetryPolicy(maxRetries = 3, initialDelayMs = 10L)
        ) {
            callCount++
            "success"
        }

        assertThat(result).isEqualTo("success")
        assertThat(callCount).isEqualTo(1)
    }

    @Test
    @DisplayName("executeWithRetry retries on RateLimitException and succeeds on subsequent attempt")
    fun executeWithRetryRecovers() = runTest {
        var callCount = 0
        val result = rateLimiter.executeWithRetry(
            policy = RetryPolicy(maxRetries = 3, initialDelayMs = 10L)
        ) {
            callCount++
            if (callCount < 3) {
                throw RateLimitException("Rate limit hit")
            }
            "recovered"
        }

        assertThat(result).isEqualTo("recovered")
        assertThat(callCount).isEqualTo(3)
    }

    @Test
    @DisplayName("executeWithRetry fails when retries are exhausted")
    fun executeWithRetryExhausts() = runTest {
        var callCount = 0
        assertThrows(RateLimitException::class.java) {
            runTest {
                rateLimiter.executeWithRetry(
                    policy = RetryPolicy(maxRetries = 2, initialDelayMs = 10L)
                ) {
                    callCount++
                    throw RateLimitException("Always failing")
                }
            }
        }
        assertThat(callCount).isEqualTo(3) // 1 initial + 2 retries
    }

    @Test
    @DisplayName("executeWithRetry does not retry non-retryable exceptions")
    fun executeWithRetryNonRetryable() = runTest {
        var callCount = 0
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                rateLimiter.executeWithRetry(
                    policy = RetryPolicy(maxRetries = 3, initialDelayMs = 10L)
                ) {
                    callCount++
                    throw IllegalArgumentException("Non-retryable")
                }
            }
        }
        assertThat(callCount).isEqualTo(1)
    }

    @Test
    @DisplayName("isRetryableException identifies IOException and RateLimitException")
    fun isRetryableCheck() {
        assertThat(RateLimiter.isRetryableException(RateLimitException())).isTrue()
        assertThat(RateLimiter.isRetryableException(IOException("Network error"))).isTrue()
        assertThat(RateLimiter.isRetryableException(IllegalStateException())).isFalse()
    }
}
