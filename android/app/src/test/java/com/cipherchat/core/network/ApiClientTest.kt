package com.cipherchat.core.network

import com.cipherchat.core.common.AppError
import com.cipherchat.core.common.AppResult
import com.cipherchat.testing.FakeConnectionConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var config: FakeConnectionConfig
    private lateinit var client: ApiClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        config = FakeConnectionConfig(server.url("/").toString(), token = "secret-token")
        client = ApiClient(config)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun <T> call(block: suspend () -> T) = runBlocking { withTimeout(10_000) { apiCall(block) } }

    private val userJson = """{"id":1,"username":"alice","display_name":"Alice","email_verified":false,"discoverable":true,"created_at":"2026-01-01T00:00:00Z"}"""

    @Test
    fun parsesLoginResponseIgnoringUnknownFields() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"t","device_id":7,"user":$userJson,"extra":"ignored"}"""))
        val r = call { client.service().login(LoginRequest("alice", "pw", "Pixel")) } as AppResult.Success
        assertEquals("t", r.data.token)
        assertEquals("alice", r.data.user.username)
        val sent = server.takeRequest()
        assertEquals("/api/v1/auth/login", sent.path)
        assertTrue(sent.body.readUtf8().contains("\"device_name\":\"Pixel\""))
    }

    @Test
    fun attachesBearerTokenOnlyWhenPresent() {
        server.enqueue(MockResponse().setBody("""{"user":$userJson,"device_id":1}"""))
        call { client.service().me() }
        assertEquals("Bearer secret-token", server.takeRequest().getHeader("Authorization"))

        config.token = null
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        call { client.service().health() }
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun invalidCredentialsGetsGenericFriendlyMessage() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"invalid_credentials","message":"Invalid username or password."}}"""))
        val e = (call { client.service().login(LoginRequest("a", "b", "d")) } as AppResult.Failure).error
        assertEquals(AppError.Kind.UNAUTHORIZED, e.kind)
        assertEquals("Invalid username or password.", e.message)
        assertEquals("invalid_credentials", e.code)
    }

    @Test
    fun mapsStatusCodesToKinds() {
        val cases = mapOf(
            403 to AppError.Kind.FORBIDDEN, 404 to AppError.Kind.NOT_FOUND, 409 to AppError.Kind.CONFLICT,
            429 to AppError.Kind.RATE_LIMITED, 400 to AppError.Kind.VALIDATION, 422 to AppError.Kind.VALIDATION, 503 to AppError.Kind.SERVER,
        )
        for ((status, kind) in cases) {
            server.enqueue(MockResponse().setResponseCode(status).setBody("""{"error":{"code":"x","message":"server said no"}}"""))
            val e = (call { client.service().health() } as AppResult.Failure).error
            assertEquals("status $status", kind, e.kind)
            assertEquals(status, e.httpStatus)
        }
    }

    @Test
    fun rateLimitAndServerErrorsAreRetriableButValidationIsNot() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("not json at all"))
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"code":"invalid_field","message":"bad"}}"""))
        assertTrue((call { client.service().health() } as AppResult.Failure).error.isRetriable)
        assertTrue((call { client.service().health() } as AppResult.Failure).error.isRetriable)
        assertEquals(false, (call { client.service().health() } as AppResult.Failure).error.isRetriable)
    }

    @Test
    fun serverErrorDoesNotLeakRawBodyToUser() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Traceback: secret internals"))
        val e = (call { client.service().health() } as AppResult.Failure).error
        assertTrue(!e.message.contains("Traceback"))
    }

    @Test
    fun connectionFailureBecomesNetworkError() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val e = (call { client.service().health() } as AppResult.Failure).error
        assertEquals(AppError.Kind.NETWORK, e.kind)
        assertTrue(e.isRetriable)
    }

    @Test
    fun unauthorizedWithTokenSignalsSessionExpiry() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        val signal = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { client.unauthorized.first() } }
        apiCall { client.service().me() }
        signal.await() // completes only if the 401 was signalled
    }

    @Test
    fun unauthorizedWithoutTokenDoesNotSignal() = runBlocking {
        config.token = null
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        var signalled = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) { client.unauthorized.first(); signalled = true }
        apiCall { client.service().login(LoginRequest("a", "b", "d")) }
        kotlinx.coroutines.delay(100)
        job.cancel()
        assertEquals(false, signalled)
    }

    @Test
    fun rebuildsServiceWhenBaseUrlChanges() {
        val other = MockWebServer().also { it.start() }
        try {
            other.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
            config.baseUrl = other.url("/").toString()
            call { client.service().health() }
            assertEquals(1, other.requestCount)
            assertEquals(0, server.requestCount)
        } finally {
            other.shutdown()
        }
    }

    @Test
    fun disableTimerSendsExplicitJsonNull() {
        server.enqueue(MockResponse().setBody("""{"conversation":{"id":1,"type":"direct","created_at":"2026-01-01T00:00:00Z"}}"""))
        call { client.service().setDisappear(1, JsonObject(mapOf("disappear_after_seconds" to JsonNull))) }
        assertEquals("""{"disappear_after_seconds":null}""", server.takeRequest().body.readUtf8())

        server.enqueue(MockResponse().setBody("""{"conversation":{"id":1,"type":"direct","created_at":"2026-01-01T00:00:00Z"}}"""))
        call { client.service().setDisappear(1, JsonObject(mapOf("disappear_after_seconds" to JsonPrimitive(60)))) }
        assertEquals("""{"disappear_after_seconds":60}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun deleteAccountSendsBodyWithDelete() {
        server.enqueue(MockResponse().setResponseCode(204))
        val r = call { client.service().deleteAccount(DeleteAccountRequest("pw")) }
        assertTrue(r is AppResult.Success)
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertTrue(req.body.readUtf8().contains("\"password\":\"pw\""))
    }

    @Test
    fun normalizeAddsTrailingSlash() {
        assertEquals("https://x.example/", ApiClient.normalize(" https://x.example "))
        assertEquals("https://x.example/", ApiClient.normalize("https://x.example/"))
    }
}
