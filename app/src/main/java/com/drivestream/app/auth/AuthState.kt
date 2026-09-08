package com.drivestream.app.auth

import com.drivestream.app.AppError

data class AuthUser(
    val email: String,
    val displayName: String?,
    val photoUrl: String?
)

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochSeconds: Long
)

sealed interface AuthState {
    data object Unauthenticated : AuthState
    data object Authenticating : AuthState
    data class Authenticated(val user: AuthUser) : AuthState
    data class Error(val error: AppError) : AuthState
}
