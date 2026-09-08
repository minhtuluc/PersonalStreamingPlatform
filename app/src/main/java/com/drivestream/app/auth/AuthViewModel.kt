package com.drivestream.app.auth

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivestream.app.AppError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        checkInitialAuthState()
    }

    fun checkInitialAuthState() {
        viewModelScope.launch {
            val storedUser = tokenManager.getStoredUser()
            if (storedUser != null && tokenManager.hasValidSession()) {
                Timber.i("Restored active session for: %s", storedUser.email)
                _authState.value = AuthState.Authenticated(storedUser)
            } else {
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    fun getSignInIntent(): Intent = googleAuthManager.getSignInIntent()

    fun handleSignInResult(intent: Intent?) {
        viewModelScope.launch {
            _authState.value = AuthState.Authenticating
            val result = googleAuthManager.handleSignInResult(intent)
            result.fold(
                onSuccess = { (user, tokens) ->
                    tokenManager.saveSession(user, tokens)
                    _authState.value = AuthState.Authenticated(user)
                },
                onFailure = { error ->
                    val appError = (error as? AppError) ?: AppError.AuthFailed(error)
                    _authState.value = AuthState.Error(appError)
                }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _authState.value = AuthState.Authenticating
            googleAuthManager.signOut()
            tokenManager.signOut()
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun clearError() {
        _authState.value = AuthState.Unauthenticated
    }
}
