package com.jh.gateway

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolAdapterTest {
    @Test
    fun anthropicRequestPreservesSystemAndTools() {
        val body = """{"model":"claude-test","system":"You are helpful","max_tokens":100,"messages":[{"role":"user","content":"hello"}],"tools":[{"name":"lookup","input_schema":{"type":"object"}}]}"""
        val converted = JSONObject(ProtocolAdapter.toOpenAi(body, ClientProtocol.ANTHROPIC))
        assertEquals("You are helpful", converted.getJSONArray("messages").getJSONObject(0).getString("content"))
        assertTrue(converted.has("tools"))
    }

    @Test
    fun anthropicToolUseBecomesOpenAiToolCall() {
        val body = """{"id":"msg_1","model":"claude-test","content":[{"type":"tool_use","id":"call_1","name":"lookup","input":{"q":"jh"}}],"stop_reason":"tool_use"}"""
        val converted = JSONObject(ProtocolAdapter.fromOpenAi(body, ClientProtocol.ANTHROPIC))
        val call = converted.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getJSONArray("tool_calls").getJSONObject(0)
        assertEquals("lookup", call.getJSONObject("function").getString("name"))
        assertEquals("tool_calls", converted.getJSONArray("choices").getJSONObject(0).getString("finish_reason"))
    }

    @Test
    fun aliasMapsToProviderModel() {
        val provider = StoredProvider("id", "Test", "https://example.com", "upstream-model", 1, aliases = listOf("fast"))
        val converted = JSONObject(ProtocolAdapter.toProvider("""{"model":"fast","messages":[]}""", provider))
        assertEquals("upstream-model", converted.getString("model"))
    }
}
