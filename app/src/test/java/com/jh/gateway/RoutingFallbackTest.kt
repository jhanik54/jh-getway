package com.jh.gateway

import android.content.Context
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/**
 * Regression tests for the audit fixes:
 *  - a provider that cannot represent a request must not abort the whole request
 *  - unsupported client-protocol fields must be rejected, never silently dropped
 *  - stale sticky routes must not accumulate
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowKeyGenParameterSpecBuilder::class])
class RoutingFallbackTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var openAiServer: MockWebServer
    private lateinit var anthropicServer: MockWebServer

    private val successBody = """{"id":"chatcmpl-test","object":"chat.completion","model":"auto","choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],"usage":{"prompt_tokens":5,"completion_tokens":7,"total_tokens":12}}"""

    @Before
    fun setUp() {
        TestAndroidKeyStore.install()
        for (name in listOf("gateway_providers", "provider_health", "gateway_chain", "gateway_settings", "gateway_stats")) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        openAiServer = MockWebServer().also { it.start(0) }
        anthropicServer = MockWebServer().also { it.start(0) }
    }

    @After
    fun tearDown() {
        if (::openAiServer.isInitialized) openAiServer.shutdown()
        if (::anthropicServer.isInitialized) anthropicServer.shutdown()
    }

    private fun MockWebServer.enqueueOk() = enqueue(
        MockResponse().setResponseCode(200).setBody(successBody).setHeader("Content-Type", "application/json")
    )

    private fun store(): ProviderStore {
        val store = ProviderStore(context)
        // The Anthropic provider is first (lower priority number wins) and it cannot
        // represent response_format; the OpenAI provider is the fallback.
        store.add("Anthropic", "http://127.0.0.1:${anthropicServer.url("/").port}", "auto", "key-anthropic", ProviderProtocol.ANTHROPIC)
        store.add("OpenAI", "http://127.0.0.1:${openAiServer.url("/").port}", "auto", "key-openai", ProviderProtocol.OPENAI)
        return store
    }

    private fun router(store: ProviderStore) = ProviderRouter(store, ChainStore(context), GatewaySettings(context), ProviderHealthStore(context))

    @Test
    fun providerSpecificRejectionFallsBackToNextProvider() {
        openAiServer.enqueueOk()
        val store = store()
        val body = """{"model":"auto","response_format":{"type":"json_object"},"messages":[{"role":"user","content":"ping"}]}"""
        val result = router(store).forward(body)
        assertEquals(200, result.status)
        assertEquals("OpenAI", result.provider)
        assertEquals(0, anthropicServer.requestCount)
        assertEquals(1, openAiServer.requestCount)
    }

    @Test
    fun validationErrorIsReturnedWhenEveryProviderRejectsTheBody() {
        val store = store()
        val body = """{"model":"auto","n":3,"messages":[{"role":"user","content":"ping"}]}"""
        val result = router(store).forward(body)
        assertEquals(400, result.status)
        assertEquals(0, anthropicServer.requestCount)
        assertEquals(0, openAiServer.requestCount)
    }

    @Test
    fun streamReportsValidationErrorOnlyAfterEveryProviderWasTried() {
        val store = store()
        val body = """{"model":"auto","n":3,"messages":[{"role":"user","content":"ping"}]}"""
        val output = ByteArrayOutputStream()
        val result = router(store).stream(body, output)
        assertEquals(400, result.status)
        val emitted = output.toString()
        assertTrue("expected an SSE error event, got: $emitted", emitted.contains("\"error\""))
        assertTrue("expected the stream to be terminated, got: $emitted", emitted.contains("[DONE]"))
    }

    @Test
    fun anthropicEndpointRejectsUnsupportedFieldInsteadOfDroppingIt() {
        val body = """{"model":"auto","frequency_penalty":0.5,"messages":[{"role":"user","content":"ping"}]}"""
        val error = runCatching { ProtocolAdapter.toOpenAi(body, ClientProtocol.ANTHROPIC) }.exceptionOrNull()
        assertTrue("expected UnsupportedFieldException, got $error", error is UnsupportedFieldException)
        assertEquals("frequency_penalty", (error as UnsupportedFieldException).field)
    }

    @Test
    fun responsesEndpointRejectsUnsupportedFieldInsteadOfDroppingIt() {
        val body = """{"model":"auto","logprobs":true,"input":"ping"}"""
        val error = runCatching { ProtocolAdapter.toOpenAi(body, ClientProtocol.RESPONSES) }.exceptionOrNull()
        assertTrue("expected UnsupportedFieldException, got $error", error is UnsupportedFieldException)
        assertEquals("logprobs", (error as UnsupportedFieldException).field)
    }

    @Test
    fun supportedAnthropicBodyStillTranslates() {
        val body = """{"model":"auto","max_tokens":100,"messages":[{"role":"user","content":"ping"}]}"""
        val translated = ProtocolAdapter.toOpenAi(body, ClientProtocol.ANTHROPIC)
        assertTrue(translated.contains("\"max_tokens\""))
        assertTrue(translated.contains("ping"))
    }

    @Test(timeout = 60_000)
    fun stickyRoutesAreBounded() {
        val store = store()
        // Enqueue enough responses so no request ever waits for a socket timeout.
        repeat(700) { openAiServer.enqueueOk() }
        val instance = router(store)
        // Distinct conversation ids create distinct sticky routes. The model is a
        // fixed alias so every request is served by the same provider.
        repeat(600) { index ->
            val body = """{"model":"auto","user":"session-$index","messages":[{"role":"user","content":"ping"}]}"""
            instance.forward(body)
        }
        // The map must stay bounded instead of growing with every distinct session id.
        val field = ProviderRouter::class.java.getDeclaredField("sticky")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val sticky = field.get(instance) as Map<String, Any>
        assertTrue("sticky routes grew to ${sticky.size}", sticky.size <= 500)
        assertNotEquals(0, sticky.size)
    }
}
