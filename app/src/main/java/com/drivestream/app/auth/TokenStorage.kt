package com.drivestream.app.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

interface TokenStorage {
    fun saveTokens(tokens: AuthTokens)
    fun getTokens(): AuthTokens?
    fun saveUser(user: AuthUser)
    fun getUser(): AuthUser?
    fun clear()
}

class EncryptedTokenStorage(
    private val sharedPreferences: SharedPreferences
) : TokenStorage {

    constructor(context: Context) : this(
        createEncryptedPrefs(context)
    )

    companion object {
        private const val PREFS_FILE_NAME = "drivestream_secure_tokens"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_PHOTO = "user_photo"

        @Suppress("TooGenericExceptionCaught")
        private fun createEncryptedPrefs(context: Context): SharedPreferences {
            return try {
                buildEncryptedPrefs(context)
            } catch (e: Exception) {
                timber.log.Timber.e(e, "EncryptedSharedPreferences corrupted, resetting storage file")
                context.deleteSharedPreferences(PREFS_FILE_NAME)
                buildEncryptedPrefs(context)
            }
        }

        private fun buildEncryptedPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    override fun saveTokens(tokens: AuthTokens) {
        sharedPreferences.edit()
            .putString(KEY_ACCESS_TOKEN, tokens.accessToken)
            .putString(KEY_REFRESH_TOKEN, tokens.refreshToken)
            .putLong(KEY_EXPIRES_AT, tokens.expiresAtEpochSeconds)
            .apply()
    }

    override fun getTokens(): AuthTokens? {
        val accessToken = sharedPreferences.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val refreshToken = sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
        val expiresAt = sharedPreferences.getLong(KEY_EXPIRES_AT, 0L)
        return AuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtEpochSeconds = expiresAt
        )
    }

    override fun saveUser(user: AuthUser) {
        sharedPreferences.edit()
            .putString(KEY_USER_EMAIL, user.email)
            .putString(KEY_USER_NAME, user.displayName)
            .putString(KEY_USER_PHOTO, user.photoUrl)
            .apply()
    }

    override fun getUser(): AuthUser? {
        val email = sharedPreferences.getString(KEY_USER_EMAIL, null) ?: return null
        val name = sharedPreferences.getString(KEY_USER_NAME, null)
        val photo = sharedPreferences.getString(KEY_USER_PHOTO, null)
        return AuthUser(email = email, displayName = name, photoUrl = photo)
    }

    override fun clear() {
        sharedPreferences.edit().clear().apply()
    }
}
