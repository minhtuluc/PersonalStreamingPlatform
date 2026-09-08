package com.drivestream.app.network

import com.drivestream.app.auth.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import java.io.IOException

class TokenInterceptor(
    private val tokenManager: TokenManager
) : Interceptor {

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        private const val HTTP_UNAUTHORIZED = 401
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // 1. Obtain valid access token (proactive refresh if < 300s remaining)
        val tokenResult = runBlocking { tokenManager.getValidAccessToken() }
        val accessToken = tokenResult.getOrNull()

        val isEligible = isEligibleGoogleHost(originalRequest.url.host)
        val authenticatedRequest = if (!accessToken.isNullOrBlank() && isEligible) {
            originalRequest.newBuilder()
                .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$accessToken")
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(authenticatedRequest)

        // 2. Handle 401 Unauthorized with reactive refresh & single retry
        if (response.code == HTTP_UNAUTHORIZED) {
            Timber.w("Received 401 Unauthorized from Drive API. Attempting token refresh and retry...")
            response.close()

            val refreshResult = runBlocking { tokenManager.refreshAccessToken() }
            val newAccessToken = refreshResult.getOrNull()

            if (!newAccessToken.isNullOrBlank()) {
                val retryRequest = originalRequest.newBuilder()
                    .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$newAccessToken")
                    .build()
                return chain.proceed(retryRequest)
            } else {
                Timber.e("Token refresh failed after 401 response.")
            }
        }

        return response
    }

    private fun isEligibleGoogleHost(host: String): Boolean {
        return host.endsWith("googleapis.com") ||
            host.endsWith("googleusercontent.com") ||
            host == "localhost" ||
            host == "127.0.0.1"
    }
}
