package com.jh.gateway

import org.json.JSONArray
import org.json.JSONObject

enum class ClientProtocol { OPENAI, ANTHROPIC, RESPONSES }

object ProtocolAdapter {
    fun toOpenAi(body: String, protocol: ClientProtocol): String {
        if (protocol == ClientProtocol.OPENAI) return body
        val input = JSONObject(body)
        val messages = JSONArray()
        input.optString("system").takeIf { it.isNotBlank() }?.let { messages.put(JSONObject().put("role", "system").put("content", it)) }
        if (protocol == ClientProtocol.ANTHROPIC) {
            input.optJSONArray("messages")?.let { source -> for (i in 0 until source.length()) messages.put(source.getJSONObject(i)) }
        } else {
            input.optJSONArray("input")?.let { source -> for (i in 0 until source.length()) messages.put(source.getJSONObject(i)) }
        }
        return JSONObject().apply {
            put("model", input.optString("model", "auto"))
            put("messages", messages)
            put("temperature", input.optDouble("temperature", 1.0))
            if (input.has("stream")) put("stream", input.optBoolean("stream"))
            input.optJSONArray("tools")?.let { put("tools", it) }
            input.optInt("max_tokens", 0).takeIf { it > 0 }?.let { put("max_tokens", it) }
        }.toString()
    }

    fun fromOpenAi(body: String, protocol: ClientProtocol): String {
        if (protocol == ClientProtocol.OPENAI || body.isBlank()) return body
        val input = runCatching { JSONObject(body) }.getOrNull() ?: return body
        val choice = input.optJSONArray("choices")?.optJSONObject(0) ?: return body
        val message = choice.optJSONObject("message") ?: JSONObject()
        val text = message.optString("content", "")
        return if (protocol == ClientProtocol.ANTHROPIC) JSONObject().apply {
            put("id", input.optString("id", "msg_jh_gateway")); put("type", "message"); put("role", "assistant"); put("model", input.optString("model", "auto")); put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text))); put("stop_reason", choice.optString("finish_reason", "end_turn")); put("usage", input.optJSONObject("usage") ?: JSONObject())
        }.toString() else JSONObject().apply {
            put("id", input.optString("id", "resp_jh_gateway")); put("object", "response"); put("model", input.optString("model", "auto")); put("output", JSONArray().put(JSONObject().put("type", "message").put("role", "assistant").put("content", JSONArray().put(JSONObject().put("type", "output_text").put("text", text))))); put("status", "completed")
        }.toString()
    }
}
