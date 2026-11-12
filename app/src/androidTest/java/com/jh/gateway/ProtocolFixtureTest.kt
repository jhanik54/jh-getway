package com.jh.gateway

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtocolFixtureTest {
    @Test
    fun anthropicUsageMapsToOpenAiUsage() {
        val converted = JSONObject(
            ProtocolAdapter.fromProvider(
                """{"id":"msg_1","model":"claude-test","stop_reason":"end_turn","content":[{"type":"text","text":"hi"}],"usage":{"input_tokens":12,"output_tokens":34}}""",
                StoredProvider("id", "Test", "https://example.com", "claude-test", 1, protocol = ProviderProtocol.ANTHROPIC)
            )
        )
        assertEquals(12, converted.getJSONObject("usage").getInt("prompt_tokens"))
        assertEquals(34, converted.getJSONObject("usage").getInt("completion_tokens"))
        assertEquals(46, converted.getJSONObject("usage").getInt("total_tokens"))
    }

    @Test
    fun anthropicStopReasonMapsToOpenAiFinishReason() {
        val converted = JSONObject(
            ProtocolAdapter.fromProvider(
                """{"model":"claude-test","stop_reason":"tool_use","content":[{"type":"tool_use","id":"toolu_1","name":"lookup","input":{"q":"jh"}}],"usage":{"input_tokens":1,"output_tokens":1}}""",
                StoredProvider("id", "Test", "https://example.com", "claude-test", 1, protocol = ProviderProtocol.ANTHROPIC)
            )
        )
        assertEquals("tool_calls", converted.getJSONArray("choices").getJSONObject(0).getString("finish_reason"))
        assertTrue(converted.getJSONArray("choices").getJSONObject(0).getJSONObject("message").has("tool_calls"))
    }

    @Test
    fun openAiProviderToAnthropicClientMapsResponseUsageAndStopReason() {
        val body = """{"id":"chatcmpl_1","model":"gpt-test","choices":[{"index":0,"message":{"role":"assistant","content":"hello"},"finish_reason":"stop"}],"usage":{"prompt_tokens":5,"completion_tokens":7,"total_tokens":12}}"""
        val converted = JSONObject(ProtocolAdapter.fromOpenAi(body, ClientProtocol.ANTHROPIC))
        assertEquals("end_turn", converted.getString("stop_reason"))
        assertEquals(5, converted.getJSONObject("usage").getInt("input_tokens"))
        assertEquals(7, converted.getJSONObject("usage").getInt("output_tokens"))
    }

    @Test
    fun responsesStreamMapsToResponsesCompleted() {
        val output = ProtocolAdapter.translateStreamData(
            """{"choices":[{"delta":{"content":"Hello"},"finish_reason":null}]}""",
            null,
            ClientProtocol.RESPONSES,
            ProviderProtocol.OPENAI
        )
        assertTrue(output?.contains("response.output_text.delta") == true)
    }

    @Test
    fun anthropicErrorEventBecomesOpenAiErrorChunk() {
        val output = ProtocolAdapter.translateStreamData(
            """{"type":"error","error":{"type":"overloaded_error","message":"Provider overloaded"}}""",
            "error",
            ClientProtocol.OPENAI,
            ProviderProtocol.ANTHROPIC
        )
        assertTrue(output?.contains("\"error\"") == true)
    }

    @Test
    fun unsupportedFieldIsRejectedForAnthropicProvider() {
        val error = runCatching {
            GatewayModels.validate(
                """{"model":"claude-3-5-sonnet-latest","max_tokens":100,"response_format":{"type":"json_object"},"messages":[]}""",
                ProviderProtocol.ANTHROPIC,
                ClientProtocol.OPENAI
            )
        }.exceptionOrNull()
        assertEquals("Unsupported field: response_format", error?.message)
    }

    @Test
    fun openAiProviderAllowsResponseFormat() {
        val parsed = runCatching {
            GatewayModels.validate(
                """{"model":"gpt-4o-mini","response_format":{"type":"json_object"},"messages":[]}""",
                ProviderProtocol.OPENAI,
                ClientProtocol.OPENAI
            )
        }
        assertTrue(parsed.isSuccess)
    }

    @Test
    fun imageUrlHttpIsTranslatedForAnthropicProvider() {
        val body = """{"model":"claude-3-5-sonnet-latest","max_tokens":100,"messages":[{"role":"user","content":[{"type":"text","text":"describe"},{"type":"image_url","image_url":{"url":"https://example.com/a.png"}}]}]}"""
        val validated = runCatching { GatewayModels.validate(body, ProviderProtocol.ANTHROPIC, ClientProtocol.OPENAI) }
        assertTrue(validated.isSuccess)
        val converted = JSONObject(ProtocolAdapter.toProvider(body, StoredProvider("id", "Test", "https://example.com", "claude-test", 1, protocol = ProviderProtocol.ANTHROPIC)))
        val blocks = converted.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals("image", blocks.getJSONObject(1).getString("type"))
        assertEquals("url", blocks.getJSONObject(1).getJSONObject("source").getString("type"))
    }

    @Test
    fun imageUrlDataIsTranslatedToBase64ForAnthropicProvider() {
        val body = """{"model":"claude-3-5-sonnet-latest","max_tokens":100,"messages":[{"role":"user","content":[{"type":"image_url","image_url":{"url":"data:image/png;base64,QUJD"}}]}]}"""
        val converted = JSONObject(ProtocolAdapter.toProvider(body, StoredProvider("id", "Test", "https://example.com", "claude-test", 1, protocol = ProviderProtocol.ANTHROPIC)))
        val source = converted.getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(0).getJSONObject("source")
        assertEquals("base64", source.getString("type"))
        assertEquals("image/png", source.getString("media_type"))
    }

    @Test
    fun imageUrlUnsupportedSchemeIsRejected() {
        val error = runCatching {
            GatewayModels.validate(
                """{"model":"claude-3-5-sonnet-latest","max_tokens":100,"messages":[{"role":"user","content":[{"type":"image_url","image_url":{"url":"ftp://example.com/a.png"}}]}]}""",
                ProviderProtocol.ANTHROPIC,
                ClientProtocol.OPENAI
            )
        }.exceptionOrNull()
        assertTrue(error?.message?.contains("image_url") == true)
    }

    @Test
    fun openAiToolCallsBecomeAnthropicToolUseBlocks() {
        val body = """{"id":"chatcmpl_1","model":"gpt-test","choices":[{"index":0,"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"lookup","arguments":"{\"q\":\"jh\"}"}}]},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":3,"completion_tokens":4}}"""
        val converted = JSONObject(ProtocolAdapter.fromOpenAi(body, ClientProtocol.ANTHROPIC))
        assertEquals("tool_use", converted.getString("stop_reason"))
        val block = converted.getJSONArray("content").getJSONObject(0)
        assertEquals("tool_use", block.getString("type"))
        assertEquals("lookup", block.getString("name"))
        assertEquals("jh", block.getJSONObject("input").getString("q"))
    }

    @Test
    fun responsesResponseCarriesToolCallsAndUsage() {
        val body = """{"id":"resp_1","model":"gpt-test","choices":[{"index":0,"message":{"role":"assistant","content":"hi","tool_calls":[{"id":"call_1","type":"function","function":{"name":"lookup","arguments":"{}"}}]},"finish_reason":"stop"}],"usage":{"prompt_tokens":8,"completion_tokens":9,"total_tokens":17}}"""
        val converted = JSONObject(ProtocolAdapter.fromOpenAi(body, ClientProtocol.RESPONSES))
        assertEquals("completed", converted.getString("status"))
        assertEquals(17, converted.getJSONObject("usage").getInt("total_tokens"))
        val types = (0 until converted.getJSONArray("output").length()).map { converted.getJSONArray("output").getJSONObject(it).getString("type") }
        assertTrue("function_call" in types)
    }

    @Test
    fun anthropicFinishReasonEndsOpenAiStream() {
        val output = ProtocolAdapter.translateStreamData(
            """{"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"input_tokens":2,"output_tokens":3}}""",
            "message_delta",
            ClientProtocol.OPENAI,
            ProviderProtocol.ANTHROPIC
        )
        assertTrue(output?.contains("\"finish_reason\":\"stop\"") == true)
    }

    @Test
    fun openaiToolCallDeltaBecomesAnthropicContentBlock() {
        val output = ProtocolAdapter.translateStreamData(
            """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"lookup","arguments":""}}]}}]}""",
            null,
            ClientProtocol.ANTHROPIC,
            ProviderProtocol.OPENAI
        )
        assertTrue(output?.contains("content_block_start") == true)
        assertTrue(output?.contains("tool_use") == true)
    }

    @Test
    fun canonicalRequestPreservesToolsAndSystem() {
        val body = """{"model":"claude-3-5-sonnet-latest","max_tokens":100,"messages":[{"role":"system","content":"You are surgical"},{"role":"user","content":"hi"}],"tools":[{"type":"function","function":{"name":"lookup","description":"search","parameters":{"type":"object"}}}]}"""
        val converted = JSONObject(ProtocolAdapter.toProvider(body, StoredProvider("id", "Test", "https://example.com", "claude-test", 1, protocol = ProviderProtocol.ANTHROPIC)))
        assertEquals("You are surgical", converted.getString("system"))
        val tool = converted.getJSONArray("tools").getJSONObject(0)
        assertEquals("lookup", tool.getString("name"))
        assertEquals("object", tool.getJSONObject("input_schema").getString("type"))
    }
}
