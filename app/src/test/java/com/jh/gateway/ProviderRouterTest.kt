package com.jh.gateway

import android.content.Context
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class ProviderRouterTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var providerA: MockProvider
    private lateinit var providerB: MockProvider

    private val body = """{"model":"auto","messages":[{"role":"user","content":"ping"}]}"""

    private class MockProvider {
        val hits = AtomicInteger(0)
        @Volatile var status = 200
        @Volatile var body = """{"id":"chatcmpl-test","object":"chat.completion","model":"auto","choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],"usage":{"prompt_tokens":5,"completion_tokens":7,"total_tokens":12}}"""
        @Volatile var retryAfter: String? = null
        private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        val port: Int get() = server.address.port

        init {
            server.createContext("/") { exchange ->
                hits.incrementAndGet()
                val response = body.toByteArray()
                retryAfter?.let { exchange.responseHeaders.add("Retry-After", it) }
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
        }

        fun start() = server.start(0)
        fun stop() = server.stop(0)
    }

    @Before
    fun setUp() {
        for (name in listOf("gateway_providers", "provider_health", "gateway_chain", "gateway_settings", "gateway_stats")) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        providerA = MockProvider().also { it.start() }
        providerB = MockProvider().also { it.start() }
    }

    @After
    fun tearDown() {
        providerA.stop()
        providerB.stop()
    }

    private fun store(): ProviderStore {
        val store = ProviderStore(context)
        store.add("A", "http://127.0.0.1:${providerA.port}", "auto", "key-a", ProviderProtocol.OPENAI)
        store.add("B", "http://127.0.0.1:${providerB.port}", "auto", "key-b", ProviderProtocol.OPENAI)
        return store
    }

    private fun router(store: ProviderStore, settings: GatewaySettings = GatewaySettings(context)) =
        ProviderRouter(store, ChainStore(context), settings, ProviderHealthStore(context))

    private fun provider(store: ProviderStore, name: String) = store.list().first { it.name == name }

    @Test
    fun successfulRequestNeverTouchesBackupProvider() {
        val store = store()
        val result = router(store).forward(body)
        assertEquals(200, result.status)
        assertEquals("A", result.provider)
        assertEquals(1, providerA.hits.get())
        assertEquals(0, providerB.hits.get())
    }

    @Test
    fun rateLimitCoolsDownProviderAndFallsBack() {
        val store = store()
        providerA.status = 429
        providerA.retryAfter = "45"
        val result = router(store).forward(body)
        assertEquals(200, result.status)
        assertEquals("B", result.provider)
        assertTrue(provider(store, "A").isCoolingDown)
    }

    @Test
    fun retryAfterHeaderParsesToMillis() {
        val store = store()
        store.remove(provider(store, "B").id)
        providerA.status = 429
        providerA.retryAfter = "45"
        val result = router(store).forward(body)
        assertEquals(429, result.status)
        assertEquals(45_000L, result.retryAfterMillis)
    }

    @Test
    fun serverErrorCoolsDownProviderAndFallsBack() {
        val store = store()
        providerA.status = 500
        val result = router(store).forward(body)
        assertEquals("B", result.provider)
        assertTrue(provider(store, "A").isCoolingDown)
    }

    @Test
    fun authFailureDisablesProviderWithoutCooldown() {
        val store = store()
        providerA.status = 401
        val result = router(store).forward(body)
        assertEquals("B", result.provider)
        assertFalse(provider(store, "A").enabled)
        assertFalse(provider(store, "A").isCoolingDown)
    }

    @Test
    fun clientErrorReturnsImmediatelyWithoutCooldown() {
        val store = store()
        providerA.status = 400
        val result = router(store).forward(body)
        assertEquals(400, result.status)
        assertEquals(0, providerB.hits.get())
        assertTrue(provider(store, "A").enabled)
        assertFalse(provider(store, "A").isCoolingDown)
    }

    @Test
    fun noProvidersReturns503() {
        val store = ProviderStore(context)
        assertEquals(503, router(store).forward(body).status)
    }

    @Test
    fun allProvidersFailingReturnsLastStatus() {
        val store = store()
        providerA.status = 500
        providerB.status = 500
        assertEquals(500, router(store).forward(body).status)
    }

    @Test
    fun leastLatencyStrategyPrefersFasterProvider() {
        val store = store()
        val health = ProviderHealthStore(context)
        health.recordSuccess(provider(store, "A"), 500)
        health.recordSuccess(provider(store, "B"), 10)
        val settings = GatewaySettings(context).apply { routingStrategy = "LEAST_LATENCY" }
        assertEquals("B", router(store, settings).simulateRoute("auto").first().name)
    }

    @Test
    fun roundRobinAlternatesAcrossCalls() {
        val store = store()
        val settings = GatewaySettings(context).apply { routingStrategy = "ROUND_ROBIN" }
        val r = router(store, settings)
        assertEquals("A", r.simulateRoute("auto").first().name)
        assertEquals("B", r.simulateRoute("auto").first().name)
    }

    @Test
    fun priorityStrategyKeepsDeclaredOrderAndMakesNoNetworkCalls() {
        val store = store()
        val r = router(store)
        assertEquals(listOf("A", "B"), r.simulateRoute("auto").map { it.name })
        r.simulateRoute("auto")
        assertEquals(0, providerA.hits.get())
        assertEquals(0, providerB.hits.get())
    }

    @Test
    fun stickySessionPinsConversationToFirstProvider() {
        val store = store()
        val settings = GatewaySettings(context).apply { routingStrategy = "ROUND_ROBIN" }
        val r = router(store, settings)
        val stickyBody = """{"model":"auto","messages":[{"role":"user","content":"ping"}],"user":"conv-1"}"""
        assertEquals("A", r.forward(stickyBody).provider)
        assertEquals("A", r.forward(stickyBody).provider)
        assertEquals(2, providerA.hits.get())
        assertEquals(0, providerB.hits.get())
    }

    @Test
    fun unsupportedFieldIsRejectedWithZeroNetworkCalls() {
        val store = ProviderStore(context)
        store.add("A", "http://127.0.0.1:${providerA.port}", "auto", "key-a", ProviderProtocol.ANTHROPIC)
        val invalid = """{"model":"auto","messages":[{"role":"user","content":"ping"}],"frequency_penalty":0.5}"""
        val result = router(store).forward(invalid)
        assertEquals(400, result.status)
        assertEquals(ProviderFailure.INVALID_REQUEST, result.failure)
        assertEquals(0, providerA.hits.get())
    }

    @Test
    fun modelMissSkipsProviderAndFallsBack() {
        val store = ProviderStore(context)
        store.add("A", "http://127.0.0.1:${providerA.port}", "gpt-fixed", "key-a", ProviderProtocol.OPENAI)
        store.add("B", "http://127.0.0.1:${providerB.port}", "auto", "key-b", ProviderProtocol.OPENAI)
        val result = router(store).simulateRoute("other-model")
        assertEquals("B", result.first().name)
    }

    @Test
    fun streamPassesOpenAiEventsThrough() {
        providerA.body = "data: {\"choices\":[{\"delta\":{\"content\":\"he\"}}]}\n\ndata: [DONE]\n\n"
        val store = store()
        val out = ByteArrayOutputStream()
        val result = router(store).stream(body, out, ClientProtocol.OPENAI)
        assertEquals(200, result.status)
        assertEquals("A", result.provider)
        val text = out.toString(Charsets.UTF_8.name())
        assertTrue(text.contains("data:"))
        assertTrue(text.contains("he"))
        assertTrue(text.contains("[DONE]"))
    }
}
