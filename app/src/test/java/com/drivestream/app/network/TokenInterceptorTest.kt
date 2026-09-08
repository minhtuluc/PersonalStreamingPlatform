package com.drivestream.app.network

import com.drivestream.app.auth.AuthTokens
import com.drivestream.app.auth.Clock
import com.drivestream.app.auth.TokenManager
import com.drivestream.app.auth.TokenRefresher
import com.drivestream.app.auth.TokenStorage
import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class TokenInterceptorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var fakeStorage: FakeStorage
    private lateinit var fakeRefresher: FakeRefresher
    private lateinit var tokenManager: TokenManager
    private lateinit var client: OkHttpClient

    class FakeStorage : TokenStorage {
        var storedTokens: AuthTokens? = null
        var storedUser: com.drivestream.app.auth.AuthUser? = null

        override fun saveTokens(tokens: AuthTokens) { this.storedTokens = tokens }
        override fun getTokens(): AuthTokens? = storedTokens
        override fun saveUser(user: com.drivestream.app.auth.AuthUser) { this.storedUser = user }
        override fun getUser(): com.drivestream.app.auth.AuthUser? = storedUser
        override fun clear() {
            storedTokens = null
            storedUser = null
        }
    }

    class FakeRefresher : TokenRefresher {
        var refreshResult: Result<AuthTokens> = Result.success(AuthTokens("refreshed_token", "rt", 5000L))
        var refreshCallCount = 0

        override suspend fun refreshAccessToken(refreshToken: String?): Result<AuthTokens> {
            refreshCallCount++
            return refreshResult
        }
    }

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        fakeStorage = FakeStorage()
        fakeRefresher = FakeRefresher()
        tokenManager = TokenManager(
            tokenStorage = fakeStorage,
            tokenRefresher = fakeRefresher,
            clock = Clock { 1000L }
        )

        val interceptor = TokenInterceptor(tokenManager)
        client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    @DisplayName("attaches Authorization header with Bearer token on outgoing request")
    fun `attaches Bearer token header on request`() {
        fakeStorage.storedTokens = AuthTokens("my_access_token", "my_refresh", 5000L)
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        val request = Request.Builder()
            .url(mockWebServer.url("/drive/v3/files"))
            .build()

        val response = client.newCall(request).execute()
        assertThat(response.isSuccessful).isTrue()

        val recordedRequest = mockWebServer.takeRequest()
        assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer my_access_token")
    }

    @Test
    @DisplayName("when 401 received, automatically refreshes token and retries with new token")
    fun `when 401 received, refreshes token and retries request`() {
        fakeStorage.storedTokens = AuthTokens("expired_token", "my_refresh", 5000L)

        // First call returns 401 Unauthorized, second retry call returns 200 OK
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"files":[]}"""))

        val request = Request.Builder()
            .url(mockWebServer.url("/drive/v3/files"))
            .build()

        val response = client.newCall(request).execute()
        assertThat(response.isSuccessful).isTrue()
        assertThat(response.code).isEqualTo(200)

        // 1st request with expired token
        val req1 = mockWebServer.takeRequest()
        assertThat(req1.getHeader("Authorization")).isEqualTo("Bearer expired_token")

        // 2nd request retried with refreshed token
        val req2 = mockWebServer.takeRequest()
        assertThat(req2.getHeader("Authorization")).isEqualTo("Bearer refreshed_token")
        assertThat(fakeRefresher.refreshCallCount).isEqualTo(1)
    }

    @Test
    @DisplayName("when 401 received and retry also fails, returns final 401 response")
    fun `when 401 retry fails, returns 401 response`() {
        fakeStorage.storedTokens = AuthTokens("bad_token", "my_refresh", 5000L)

        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("Still Unauthorized"))

        val request = Request.Builder()
            .url(mockWebServer.url("/drive/v3/files"))
            .build()

        val response = client.newCall(request).execute()
        assertThat(response.code).isEqualTo(401)
    }
}
