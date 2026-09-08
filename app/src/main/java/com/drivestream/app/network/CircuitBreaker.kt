package com.drivestream.app.network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

class CircuitBreakerOpenException(message: String) : IOException(message)

@Singleton
class CircuitBreaker @Inject constructor() {

    enum class State { CLOSED, OPEN, HALF_OPEN }

    private val mutex = Mutex()
    private var failureCount = 0
    private var lastFailureTime = 0L
    private var _state = State.CLOSED

    val state: State
        get() = _state

    companion object {
        const val DEFAULT_FAILURE_THRESHOLD = 5
        const val DEFAULT_RESET_TIMEOUT_MS = 60_000L
        private const val MILLIS_PER_SECOND = 1000L
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun <T> execute(
        failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD,
        resetTimeoutMs: Long = DEFAULT_RESET_TIMEOUT_MS,
        block: suspend () -> T
    ): T {
        mutex.withLock {
            checkStateTransition(resetTimeoutMs)
        }

        return try {
            val result = block()
            mutex.withLock {
                onSuccess()
            }
            result
        } catch (e: Exception) {
            mutex.withLock {
                onFailure(failureThreshold)
            }
            throw e
        }
    }

    private fun checkStateTransition(resetTimeoutMs: Long) {
        if (_state == State.OPEN) {
            val now = System.currentTimeMillis()
            if (now - lastFailureTime > resetTimeoutMs) {
                _state = State.HALF_OPEN
                Timber.d("[CIRCUIT_BREAKER] Transitioning from OPEN to HALF_OPEN")
            } else {
                val remainingSec = (resetTimeoutMs - (now - lastFailureTime)) / MILLIS_PER_SECOND
                throw CircuitBreakerOpenException("Circuit breaker is OPEN. Retry after ${remainingSec}s")
            }
        }
    }

    private fun onSuccess() {
        failureCount = 0
        if (_state != State.CLOSED) {
            Timber.i("[CIRCUIT_BREAKER] Success recorded. Transitioning to CLOSED")
            _state = State.CLOSED
        }
    }

    private fun onFailure(failureThreshold: Int) {
        failureCount++
        lastFailureTime = System.currentTimeMillis()
        if (failureCount >= failureThreshold) {
            _state = State.OPEN
            Timber.w("[CIRCUIT_BREAKER] Failure threshold reached ($failureCount). State: OPEN")
        }
    }

    internal fun resetForTesting() {
        failureCount = 0
        lastFailureTime = 0L
        _state = State.CLOSED
    }
}
