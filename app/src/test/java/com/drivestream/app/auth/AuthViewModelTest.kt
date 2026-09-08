package com.drivestream.app.auth

import android.content.Intent
import app.cash.turbine.test
import com.drivestream.app.AppError
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val googleAuthManager: GoogleAuthManager = mockk(relaxed = true)
    private val tokenManager: TokenManager = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("restores authenticated state when existing valid session is found")
    fun `restores authenticated state on init when valid session exists`() = runTest(testDispatcher) {
        val user = AuthUser("user@gmail.com", "Test User", null)
        every { tokenManager.getStoredUser() } returns user
        every { tokenManager.hasValidSession() } returns true

        val viewModel = AuthViewModel(googleAuthManager, tokenManager)

        viewModel.authState.test {
            // Initial emit or restored emit
            val state = awaitItem()
            testScheduler.advanceUntilIdle()
            val finalState = if (state is AuthState.Authenticated) state else awaitItem()

            assertThat(finalState).isInstanceOf(AuthState.Authenticated::class.java)
            assertThat((finalState as AuthState.Authenticated).user.email).isEqualTo("user@gmail.com")
        }
    }

    @Test
    @DisplayName("emits Unauthenticated when no stored session exists")
    fun `emits Unauthenticated when no session exists`() = runTest(testDispatcher) {
        every { tokenManager.getStoredUser() } returns null
        every { tokenManager.hasValidSession() } returns false

        val viewModel = AuthViewModel(googleAuthManager, tokenManager)
        testScheduler.advanceUntilIdle()

        assertThat(viewModel.authState.value).isEqualTo(AuthState.Unauthenticated)
    }

    @Test
    @DisplayName("handleSignInResult on success saves session and emits Authenticated")
    fun `handleSignInResult on success transitions to Authenticated`() = runTest(testDispatcher) {
        every { tokenManager.getStoredUser() } returns null
        every { tokenManager.hasValidSession() } returns false

        val viewModel = AuthViewModel(googleAuthManager, tokenManager)
        testScheduler.advanceUntilIdle()

        val mockIntent = mockk<Intent>()
        val user = AuthUser("new@gmail.com", "New User", null)
        val tokens = AuthTokens("acc", "ref", 5000L)
        coEvery { googleAuthManager.handleSignInResult(mockIntent) } returns Result.success(Pair(user, tokens))

        viewModel.handleSignInResult(mockIntent)
        testScheduler.advanceUntilIdle()

        assertThat(viewModel.authState.value).isInstanceOf(AuthState.Authenticated::class.java)
        coVerify { tokenManager.saveSession(user, tokens) }
    }

    @Test
    @DisplayName("handleSignInResult on failure emits Error state")
    fun `handleSignInResult on failure transitions to Error`() = runTest(testDispatcher) {
        every { tokenManager.getStoredUser() } returns null
        every { tokenManager.hasValidSession() } returns false

        val viewModel = AuthViewModel(googleAuthManager, tokenManager)
        testScheduler.advanceUntilIdle()

        val mockIntent = mockk<Intent>()
        val error = AppError.AuthFailed(RuntimeException("Canceled by user"))
        coEvery { googleAuthManager.handleSignInResult(mockIntent) } returns Result.failure(error)

        viewModel.handleSignInResult(mockIntent)
        testScheduler.advanceUntilIdle()

        assertThat(viewModel.authState.value).isInstanceOf(AuthState.Error::class.java)
        val errorState = viewModel.authState.value as AuthState.Error
        assertThat(errorState.error).isEqualTo(error)
    }

    @Test
    @DisplayName("signOut clears google client and token storage, emits Unauthenticated")
    fun `signOut clears sessions and transitions to Unauthenticated`() = runTest(testDispatcher) {
        val user = AuthUser("user@gmail.com", "User", null)
        every { tokenManager.getStoredUser() } returns user
        every { tokenManager.hasValidSession() } returns true
        coEvery { googleAuthManager.signOut() } returns Result.success(Unit)

        val viewModel = AuthViewModel(googleAuthManager, tokenManager)
        testScheduler.advanceUntilIdle()

        viewModel.signOut()
        testScheduler.advanceUntilIdle()

        assertThat(viewModel.authState.value).isEqualTo(AuthState.Unauthenticated)
        coVerify { googleAuthManager.signOut() }
        coVerify { tokenManager.signOut() }
    }
}
