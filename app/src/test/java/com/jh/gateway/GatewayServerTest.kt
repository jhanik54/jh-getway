package com.jh.gateway

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.URL

/**
 * Verifies the gateway HTTP surface over a real loopback socket: authentication, the
 * concurrency permit accounting (a leaked permit used to wedge the whole gateway after
 * a handful of GET requests), and the health endpoints.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowKeyGenParameterSpecBuilder::class])
class GatewayServerTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var server: GatewayServer
    private lateinit var security: GatewaySecurity

    @Before
    fun setUp() {
        TestAndroidKeyStore.install()
        for (name in listOf("gateway_providers", "provider_health", "gateway_chain", "gateway_settings", "gateway_stats", "gateway_security")) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        val settings = GatewaySettings(context)
        settings.maxConcurrent = 2
        security = GatewaySecurity(context)
        server = GatewayServer(ProviderStore(context), security, GatewayStats(context), settings, ChainStore(context), ProviderHealthStore(context))
        server.start()
        // The CIO engine binds asynchronously; give it a moment to accept connections.
        Thread.sleep(600)
    }

    @After
    fun tearDown() {
        if (::server.isInitialized) runCatching { server.stop() }
    }

    private fun get(path: String, key: String? = null): Pair<Int, String> {
        val connection = (URL("http://127.0.0.1:${GatewaySettings(context).port}$path").openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000; readTimeout = 5_000
            if (key != null) setRequestProperty("Authorization", "Bearer $key")
        }
        val status = connection.responseCode
        val stream = if (status >= 400) connection.errorStream else connection.inputStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return status to body
    }

    @Test
    fun healthEndpointRespondsWithoutAuthentication() {
        val (status, body) = get("/health")
        assertEquals(200, status)
        assertTrue(body.contains("\"status\":\"ok\""))
    }

    @Test
    fun healthzAliasResponds() {
        val (status, body) = get("/healthz")
        assertEquals(200, status)
        assertTrue(body.contains("\"status\":\"ok\""))
    }

    @Test
    fun protectedEndpointRejectsMissingKey() {
        val (status, body) = get("/v1/models")
        assertEquals(401, status)
        assertTrue(body.contains("authentication_error"))
    }

    @Test
    fun protectedEndpointAcceptsValidKey() {
        val (status, _) = get("/v1/models", security.gatewayKey())
        assertEquals(200, status)
    }

    @Test
    fun getRequestsDoNotLeakConcurrencyPermits() {
        val key = security.gatewayKey()
        // The permit limit is 2. If GET /v1/models or /v1/gateway/logs leaked their
        // permit, the server would wedge after a few calls and start rejecting
        // authorized requests with 429.
        repeat(12) {
            val (status, _) = get("/v1/models", key)
            assertEquals("request $it was rejected", 200, status)
            val (logsStatus, _) = get("/v1/gateway/logs", key)
            assertEquals("logs request $it was rejected", 200, logsStatus)
        }
    }
}
