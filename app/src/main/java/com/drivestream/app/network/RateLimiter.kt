package com.drivestream.app.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RateLimiter @Inject constructor() {

    private val mutex = Mutex()
    private val requestTimestamps = ArrayDeque<Long>()

    suspend fun acquire() {
        mutex.withLock {
            var now = System.currentTimeMillis()
            while (requestTimestamps.isNotEmpty() && (now - requestTimestamps.first()) >= WINDOW_DURATION_MS) {
                requestTimestamps.removeFirst()
            }
            if (requestTimestamps.size >= MAX_REQUESTS_PER_SECOND) {
                val waitTime = WINDOW_DURATION_MS - (now - requestTimestamps.first())
                if (waitTime > 0L) {
                    delay(waitTime)
                }
                now = System.currentTimeMillis()
                while (requestTimestamps.isNotEmpty() && (now - requestTimestamps.first()) >= WINDOW_DURATION_MS) {
                    requestTimestamps.removeFirst()
                }
            }
            requestTimestamps.addLast(System.currentTimeMillis())
        }
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun <T> executeWithRetry(
        policy: RetryPolicy = RetryPolicy(),
        isRetryable: (Throwable) -> Boolean = ::isRetryableException,
        block: suspend () -> T
    ): T {
        var currentDelay = policy.initialDelayMs
        var attempts = 0
        while (true) {
            attempts++
            try {
                acquire()
                return block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempts > policy.maxRetries || !isRetryable(e)) {
                    throw e
                }
                delay(currentDelay)
                currentDelay = (currentDelay * policy.factor).toLong().coerceAtMost(policy.maxDelayMs)
            }
        }
    }

    companion object {
        const val MAX_REQUESTS_PER_SECOND = 5
        const val WINDOW_DURATION_MS = 1000L
        const val DEFAULT_MAX_RETRIES = 3
        const val INITIAL_BACKOFF_MS = 1000L
        const val MAX_BACKOFF_MS = 32000L
        const val BACKOFF_FACTOR = 2.0

        fun isRetryableException(throwable: Throwable): Boolean {
            return when (throwable) {
                is RateLimitException -> true
                is IOException -> true
                else -> false
            }
        }
    }
}

class RateLimitException(
    message: String = "Rate limit exceeded, please retry later",
    cause: Throwable? = null
) : IOException(message, cause)

data class RetryPolicy(
    val maxRetries: Int = RateLimiter.DEFAULT_MAX_RETRIES,
    val initialDelayMs: Long = RateLimiter.INITIAL_BACKOFF_MS,
    val maxDelayMs: Long = RateLimiter.MAX_BACKOFF_MS,
    val factor: Double = RateLimiter.BACKOFF_FACTOR
)
