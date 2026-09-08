package com.drivestream.app.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.IOException

class CircuitBreakerTest {

    private lateinit var circuitBreaker: CircuitBreaker

    @BeforeEach
    fun setUp() {
        circuitBreaker = CircuitBreaker()
    }

    @Test
    @DisplayName("execute succeeds and keeps state CLOSED")
    fun executeSuccessClosedState() = runTest {
        val result = circuitBreaker.execute { "success" }
        assertThat(result).isEqualTo("success")
        assertThat(circuitBreaker.state).isEqualTo(CircuitBreaker.State.CLOSED)
    }

    @Test
    @DisplayName("transitions to OPEN after threshold failures")
    fun transitionsToOpenAfterThreshold() = runTest {
        val threshold = 3

        repeat(threshold) {
            runCatching {
                circuitBreaker.execute(failureThreshold = threshold) {
                    throw IOException("Simulated network failure")
                }
            }
        }

        assertThat(circuitBreaker.state).isEqualTo(CircuitBreaker.State.OPEN)

        // Further calls should fail immediately with CircuitBreakerOpenException
        assertThrows(CircuitBreakerOpenException::class.java) {
            runTest {
                circuitBreaker.execute(failureThreshold = threshold) {
                    "should_not_run"
                }
            }
        }
    }

    @Test
    @DisplayName("transitions to HALF_OPEN after reset timeout and CLOSED on success")
    fun transitionsToHalfOpenAndRecovers() = runTest {
        val threshold = 2
        val shortTimeoutMs = 10L

        repeat(threshold) {
            runCatching {
                circuitBreaker.execute(failureThreshold = threshold, resetTimeoutMs = shortTimeoutMs) {
                    throw IOException("Failure")
                }
            }
        }

        assertThat(circuitBreaker.state).isEqualTo(CircuitBreaker.State.OPEN)

        // Wait for reset timeout
        Thread.sleep(20L)

        // Next call should execute in HALF_OPEN and recover to CLOSED on success
        val result = circuitBreaker.execute(failureThreshold = threshold, resetTimeoutMs = shortTimeoutMs) {
            "recovered"
        }

        assertThat(result).isEqualTo("recovered")
        assertThat(circuitBreaker.state).isEqualTo(CircuitBreaker.State.CLOSED)
    }
}
