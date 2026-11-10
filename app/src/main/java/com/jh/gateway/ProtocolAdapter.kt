package com.jh.gateway

import org.json.JSONArray
import org.json.JSONObject

enum class ClientProtocol { OPENAI, ANTHROPIC, RESPONSES }

object ProtocolAdapter {
    fun toProvider(body: String, provider: StoredProvider): String = if (provider.protocol == ProviderProtocol.ANTHROPIC) toAnthropic(body, provider.model) else body
    fun fromProvider(body: String, provider: StoredProvider): String = if (provider.protocol == ProviderProtocol.ANTHROPIC) fromAnthropic(body) else body

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

    private fun toAnthropic(body: String, providerModel: String): String {
        val input = JSONObject(body)
        val messages = JSONArray()
        var system = ""
        input.optJSONArray("messages")?.let { source ->
            for (i in 0 until source.length()) {
                val message = source.getJSONObject(i)
                if (message.optString("role") == "system") system += message.optString("content") + "\n"
                else messages.put(JSONObject().put("role", if (message.optString("role") == "assistant") "assistant" else "user").put("content", message.optString("content")))
            }
        }
        return JSONObject().apply {
            put("model", if (input.optString("model", "auto") == "auto") providerModel else input.optString("model")); put("max_tokens", input.optInt("max_tokens", 4096)); put("messages", messages)
            if (system.isNotBlank()) put("system", system.trim())
            if (input.has("temperature")) put("temperature", input.optDouble("temperature"))
            if (input.has("stream")) put("stream", input.optBoolean("stream"))
        }.toString()
    }

    private fun fromAnthropic(body: String): String {
        val input = runCatching { JSONObject(body) }.getOrNull() ?: return body
        val content = input.optJSONArray("content")?.let { blocks -> (0 until blocks.length()).joinToString("") { blocks.optJSONObject(it)?.optString("text", "") ?: "" } } ?: ""
        return JSONObject().apply { put("id", input.optString("id", "chatcmpl_jh_gateway")); put("object", "chat.completion"); put("model", input.optString("model", "auto")); put("choices", JSONArray().put(JSONObject().put("index", 0).put("message", JSONObject().put("role", "assistant").put("content", content)).put("finish_reason", input.optString("stop_reason", "end_turn")))); put("usage", input.optJSONObject("usage") ?: JSONObject()) }.toString()
    }
}
