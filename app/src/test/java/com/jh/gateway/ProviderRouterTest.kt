package com.jh.gateway

import android.content.Context
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowKeyGenParameterSpecBuilder::class.java])
class ProviderRouterTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var providerA: MockWebServer
    private lateinit var providerB: MockWebServer

    private val body = """{"model":"auto","messages":[{"role":"user","content":"ping"}]}"""
    private val successBody = """{"id":"chatcmpl-test","object":"chat.completion","model":"auto","choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],"usage":{"prompt_tokens":5,"completion_tokens":7,"total_tokens":12}}"""

    private fun MockWebServer.enqueueJson(status: Int, body: String = successBody, retryAfter: String? = null) {
        val response = MockResponse().setResponseCode(status).setBody(body).setHeader("Content-Type", "application/json")
        retryAfter?.let { response.setHeader("Retry-After", it) }
        enqueue(response)
    }

    @Before
    fun setUp() {
        TestAndroidKeyStore.install()
        for (name in listOf("gateway_providers", "provider_health", "gateway_chain", "gateway_settings", "gateway_stats")) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        providerA = MockWebServer().also { it.start(0) }
        providerB = MockWebServer().also { it.start(0) }
    }

    @After
    fun tearDown() {
        if (::providerA.isInitialized) providerA.shutdown()
        if (::providerB.isInitialized) providerB.shutdown()
    }

    private fun store(): ProviderStore {
        val store = ProviderStore(context)
        store.add("A", "http://127.0.0.1:${providerA.url("/").port}", "auto", "key-a", ProviderProtocol.OPENAI)
        store.add("B", "http://127.0.0.1:${providerB.url("/").port}", "auto", "key-b", ProviderProtocol.OPENAI)
        return store
    }

    private fun router(store: ProviderStore, settings: GatewaySettings = GatewaySettings(context)) =
        ProviderRouter(store, ChainStore(context), settings, ProviderHealthStore(context))

    private fun provider(store: ProviderStore, name: String) = store.list().first { it.name == name }

    @Test
    fun successfulRequestNeverTouchesBackupProvider() {
        providerA.enqueueJson(200)
        providerB.enqueueJson(200)
        val store = store()
        val result = router(store).forward(body)
        assertEquals(200, result.status)
        assertEquals("A", result.provider)
        assertEquals(1, providerA.requestCount)
        assertEquals(0, providerB.requestCount)
    }

    @Test
    fun rateLimitCoolsDownProviderAndFallsBack() {
        providerA.enqueueJson(429, retryAfter = "45")
        providerB.enqueueJson(200)
        val store = store()
        val result = router(store).forward(body)
        assertEquals(200, result.status)
        assertEquals("B", result.provider)
        assertTrue(provider(store, "A").isCoolingDown)
    }

    @Test
    fun retryAfterHeaderParsesToMillis() {
        providerA.enqueueJson(429, retryAfter = "45")
        val store = store()
        store.remove(provider(store, "B").id)
        val result = router(store).forward(body)
        assertEquals(429, result.status)
        assertEquals(45_000L, result.retryAfterMillis)
    }

    @Test
    fun serverErrorCoolsDownProviderAndFallsBack() {
        providerA.enqueueJson(500)
        providerB.enqueueJson(200)
        val store = store()
        val result = router(store).forward(body)
        assertEquals("B", result.provider)
        assertTrue(provider(store, "A").isCoolingDown)
    }

    @Test
    fun authFailureDisablesProviderWithoutCooldown() {
        providerA.enqueueJson(401)
        providerB.enqueueJson(200)
        val store = store()
        val result = router(store).forward(body)
        assertEquals("B", result.provider)
        assertFalse(provider(store, "A").enabled)
        assertFalse(provider(store, "A").isCoolingDown)
    }

    @Test
    fun clientErrorReturnsImmediatelyWithoutCooldown() {
        providerA.enqueueJson(400)
        val store = store()
        val result = router(store).forward(body)
        assertEquals(400, result.status)
        assertEquals(0, providerB.requestCount)
        assertTrue(provider(store, "A").enabled)
        assertFalse(provider(store, "A").isCoolingDown)
    }

    @Test
    fun noProvidersReturns503() {
        val store = ProviderStore(context)
        assertEquals(503, router(store).forward(body).status)
        assertEquals(0, providerA.requestCount)
        assertEquals(0, providerB.requestCount)
    }

    @Test
    fun allProvidersFailingReturnsLastStatus() {
        providerA.enqueueJson(500)
        providerB.enqueueJson(500)
        val store = store()
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
        assertEquals(0, providerA.requestCount)
        assertEquals(0, providerB.requestCount)
    }

    @Test
    fun stickySessionPinsConversationToFirstProvider() {
        val store = store()
        val settings = GatewaySettings(context).apply { routingStrategy = "ROUND_ROBIN" }
        val r = router(store, settings)
        val stickyBody = """{"model":"auto","messages":[{"role":"user","content":"ping"}],"user":"conv-1"}"""
        providerA.enqueueJson(200)
        providerA.enqueueJson(200)
        assertEquals("A", r.forward(stickyBody).provider)
        assertEquals("A", r.forward(stickyBody).provider)
        assertEquals(2, providerA.requestCount)
        assertEquals(0, providerB.requestCount)
    }

    @Test
    fun unsupportedFieldIsRejectedWithZeroNetworkCalls() {
        val store = ProviderStore(context)
        store.add("A", "http://127.0.0.1:${providerA.url("/").port}", "auto", "key-a", ProviderProtocol.ANTHROPIC)
        val invalid = """{"model":"auto","messages":[{"role":"user","content":"ping"}],"frequency_penalty":0.5}"""
        val result = router(store).forward(invalid)
        assertEquals(400, result.status)
        assertEquals(ProviderFailure.INVALID_REQUEST, result.failure)
        assertEquals(0, providerA.requestCount)
    }

    @Test
    fun modelMissSkipsProviderAndFallsBack() {
        val store = ProviderStore(context)
        store.add("A", "http://127.0.0.1:${providerA.url("/").port}", "gpt-fixed", "key-a", ProviderProtocol.OPENAI)
        store.add("B", "http://127.0.0.1:${providerB.url("/").port}", "auto", "key-b", ProviderProtocol.OPENAI)
        val result = router(store).simulateRoute("other-model")
        assertEquals("B", result.first().name)
    }

    @Test
    fun streamPassesOpenAiEventsThrough() {
        providerA.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream").setBody("data: {\"choices\":[{\"delta\":{\"content\":\"he\"}}]}\n\ndata: [DONE]\n\n"))
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
