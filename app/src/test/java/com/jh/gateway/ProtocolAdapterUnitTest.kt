package com.jh.gateway

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProtocolAdapterUnitTest {

    @Test
    fun unsupportedFieldThrowsForAnthropicUpstream() {
        val body = """{"model":"m","messages":[{"role":"user","content":"hi"}],"frequency_penalty":0.5}"""
        val error = assertThrows(UnsupportedFieldException::class.java) {
            GatewayModels.validate(body, ProviderProtocol.ANTHROPIC, ClientProtocol.OPENAI)
        }
        assertEquals("frequency_penalty", error.field)
    }

    @Test
    fun sameFieldIsAllowedForOpenAiUpstream() {
        GatewayModels.validate("""{"model":"m","messages":[{"role":"user","content":"hi"}],"frequency_penalty":0.5}""", ProviderProtocol.OPENAI, ClientProtocol.OPENAI)
    }

    @Test
    fun malformedJsonThrowsCanonicalFormatException() {
        val error = assertThrows(CanonicalFormatException::class.java) {
            GatewayModels.validate("not json at all", ProviderProtocol.OPENAI, ClientProtocol.OPENAI)
        }
        assertEquals("body is not valid JSON", error.message)
    }

    @Test
    fun aliasMapsToUpstreamModel() {
        val provider = StoredProvider("id", "Test", "https://example.com", "upstream-model", 1, aliases = listOf("fast"))
        val converted = JSONObject(ProtocolAdapter.toProvider("""{"model":"fast","messages":[]}""", provider))
        assertEquals("upstream-model", converted.getString("model"))
    }

    @Test
    fun autoModelIsMappedToUpstreamModel() {
        val provider = StoredProvider("id", "Test", "https://example.com", "upstream-model", 1)
        val converted = JSONObject(ProtocolAdapter.toProvider("""{"model":"auto","messages":[]}""", provider))
        assertEquals("upstream-model", converted.getString("model"))
    }

    @Test
    fun anthropicUsageMapsToOpenAiUsage() {
        val body = """{"id":"msg_1","model":"claude-test","stop_reason":"end_turn","content":[{"type":"text","text":"hi"}],"usage":{"input_tokens":12,"output_tokens":34}}"""
        val converted = JSONObject(ProtocolAdapter.fromProvider(body, StoredProvider("id", "Test", "https://example.com", "claude-test", 1, protocol = ProviderProtocol.ANTHROPIC)))
        assertEquals(12, converted.getJSONObject("usage").getInt("prompt_tokens"))
        assertEquals(34, converted.getJSONObject("usage").getInt("completion_tokens"))
        assertEquals(46, converted.getJSONObject("usage").getInt("total_tokens"))
    }

    @Test
    fun streamDoneMarkerIsNormalizedForOpenAiClient() {
        assertEquals("data: [DONE]\n\n", ProtocolAdapter.translateStreamData("[DONE]", null, ClientProtocol.OPENAI, ProviderProtocol.OPENAI))
    }

    @Test
    fun openAiPassThroughPreservesChunk() {
        val chunk = """{"choices":[{"delta":{"content":"hi"}}]}"""
        val out = ProtocolAdapter.translateStreamData(chunk, null, ClientProtocol.OPENAI, ProviderProtocol.OPENAI)
        assertEquals("data: $chunk\n\n", out)
    }
}
