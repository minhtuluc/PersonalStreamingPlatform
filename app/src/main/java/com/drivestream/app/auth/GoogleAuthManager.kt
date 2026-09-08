package com.drivestream.app.auth

import android.content.Context
import android.content.Intent
import com.drivestream.app.AppError
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException

class GoogleAuthManager(
    private val context: Context,
    private val serverClientId: String? = null
) : TokenRefresher {

    companion object {
        const val DRIVE_READONLY_SCOPE = "https://www.googleapis.com/auth/drive.readonly"
        private const val OAUTH_SCOPE_PREFIX = "oauth2:"
        private const val DEFAULT_TOKEN_VALIDITY_SECONDS = 3600L
        private const val MILLIS_PER_SECOND = 1000L
    }

    private val googleSignInOptions: GoogleSignInOptions by lazy {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_READONLY_SCOPE))

        if (!serverClientId.isNullOrBlank()) {
            builder.requestServerAuthCode(serverClientId, true)
        }

        builder.build()
    }

    val signInClient: GoogleSignInClient by lazy {
        GoogleSignIn.getClient(context, googleSignInOptions)
    }

    fun getSignInIntent(): Intent = signInClient.signInIntent

    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun handleSignInResult(intent: Intent?): Result<Pair<AuthUser, AuthTokens>> = withContext(Dispatchers.IO) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
            val account = task.getResult(ApiException::class.java)
                ?: return@withContext Result.failure(
                    AppError.AuthFailed(IllegalStateException("GoogleSignInAccount was null"))
                )

            val email = account.email ?: return@withContext Result.failure(
                AppError.AuthFailed(IllegalStateException("No email associated with account"))
            )

            val user = AuthUser(
                email = email,
                displayName = account.displayName,
                photoUrl = account.photoUrl?.toString()
            )

            val androidAccount = account.account ?: return@withContext Result.failure(
                AppError.AuthFailed(IllegalStateException("No android.accounts.Account found"))
            )

            val scopeString = "$OAUTH_SCOPE_PREFIX$DRIVE_READONLY_SCOPE"
            val accessToken = GoogleAuthUtil.getToken(context, androidAccount, scopeString)
            val expiresAt = (System.currentTimeMillis() / MILLIS_PER_SECOND) + DEFAULT_TOKEN_VALIDITY_SECONDS

            val tokens = AuthTokens(
                accessToken = accessToken,
                refreshToken = account.serverAuthCode,
                expiresAtEpochSeconds = expiresAt
            )

            Timber.i("Google Sign-In successful for user: %s", user.email)
            Result.success(Pair(user, tokens))
        } catch (e: ApiException) {
            Timber.e(e, "Google Sign-In ApiException: status code %d", e.statusCode)
            Result.failure(AppError.AuthFailed(e))
        } catch (e: IOException) {
            Timber.e(e, "Network error during Google Sign-In")
            Result.failure(AppError.NetworkUnavailable(e))
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during Google Sign-In")
            Result.failure(AppError.AuthFailed(e))
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun refreshAccessToken(refreshToken: String?): Result<AuthTokens> = withContext(Dispatchers.IO) {
        try {
            val lastAccount = getLastSignedInAccount()
            val account = lastAccount?.account ?: return@withContext Result.failure(
                AppError.TokenExpired(IllegalStateException("No active Google account for token refresh"))
            )

            val scopeString = "$OAUTH_SCOPE_PREFIX$DRIVE_READONLY_SCOPE"
            val token = GoogleAuthUtil.getToken(context, account, scopeString)
            val expiresAt = (System.currentTimeMillis() / MILLIS_PER_SECOND) + DEFAULT_TOKEN_VALIDITY_SECONDS

            Result.success(
                AuthTokens(
                    accessToken = token,
                    refreshToken = refreshToken,
                    expiresAtEpochSeconds = expiresAt
                )
            )
        } catch (e: IOException) {
            Timber.e(e, "Network error refreshing Google token")
            Result.failure(AppError.NetworkUnavailable(e))
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh Google access token")
            Result.failure(AppError.TokenExpired(e))
        }
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Tasks.await(signInClient.signOut())
            Timber.i("Google Sign-Out successful")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sign out from Google client")
            Result.failure(e)
        }
    }
}
