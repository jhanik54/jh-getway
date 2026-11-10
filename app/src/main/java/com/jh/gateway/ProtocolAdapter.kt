package com.jh.gateway

import org.json.JSONArray
import org.json.JSONObject

enum class ClientProtocol { OPENAI, ANTHROPIC, RESPONSES }

object ProtocolAdapter {
    fun translateStreamData(data: String, event: String?, client: ClientProtocol, provider: ProviderProtocol): String? {
        if (data == "[DONE]") return when (client) {
            ClientProtocol.ANTHROPIC -> "event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n"
            ClientProtocol.RESPONSES -> "data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\"}}\n\ndata: [DONE]\n\n"
            ClientProtocol.OPENAI -> "data: [DONE]\n\n"
        }
        val input = runCatching { JSONObject(data) }.getOrNull() ?: return null
        if (client == ClientProtocol.OPENAI && provider == ProviderProtocol.ANTHROPIC) {
            val delta = input.optJSONObject("delta")
            val text = delta?.optString("text").orEmpty()
            if (text.isBlank()) return null
            return JSONObject().apply { put("id", input.optString("message_id", "chatcmpl_jh_gateway")); put("object", "chat.completion.chunk"); put("choices", JSONArray().put(JSONObject().put("index", 0).put("delta", JSONObject().put("content", text)).put("finish_reason", JSONObject.NULL))) }.let { "data: $it\n\n" }
        }
        if (client == ClientProtocol.ANTHROPIC && provider == ProviderProtocol.OPENAI) {
            val choice = input.optJSONArray("choices")?.optJSONObject(0) ?: return null
            val delta = choice.optJSONObject("delta") ?: return null
            val text = delta.optString("content").orEmpty()
            if (text.isBlank()) return null
            return "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":${JSONObject.quote(text)}}}\n\n"
        }
        if (client == ClientProtocol.RESPONSES && provider == ProviderProtocol.OPENAI) {
            val text = input.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty()
            if (text.isBlank()) return null
            return "data: {\"type\":\"response.output_text.delta\",\"delta\":${JSONObject.quote(text)}}\n\n"
        }
        return "data: $data\n\n"
    }

    fun toProvider(body: String, provider: StoredProvider): String {
        val mapped = runCatching {
            val input = JSONObject(body)
            val requested = input.optString("model", "auto")
            if (requested == "auto" || requested in provider.aliases) input.put("model", provider.model)
            input.toString()
        }.getOrDefault(body)
        return if (provider.protocol == ProviderProtocol.ANTHROPIC) toAnthropic(mapped, provider.model) else mapped
    }
    fun fromProvider(body: String, provider: StoredProvider): String = if (provider.protocol == ProviderProtocol.ANTHROPIC) fromAnthropic(body) else body

    fun toOpenAi(body: String, protocol: ClientProtocol): String {
        if (protocol == ClientProtocol.OPENAI) return body
        val input = JSONObject(body)
        val messages = JSONArray()
        input.optString("system").takeIf { it.isNotBlank() }?.let { messages.put(JSONObject().put("role", "system").put("content", it)) }
        if (protocol == ClientProtocol.ANTHROPIC) {
            input.optJSONArray("messages")?.let { source -> for (i in 0 until source.length()) messages.put(source.getJSONObject(i)) }
        } else {
            when (val source = input.opt("input")) {
                is String -> messages.put(JSONObject().put("role", "user").put("content", source))
                is JSONArray -> for (i in 0 until source.length()) messages.put(source.getJSONObject(i))
            }
        }
        return JSONObject().apply {
            put("model", input.optString("model", "auto"))
            put("messages", messages)
            put("temperature", input.optDouble("temperature", 1.0))
            if (input.has("stream")) put("stream", input.optBoolean("stream"))
            input.optJSONArray("tools")?.let { put("tools", it) }
            input.opt("tool_choice")?.let { put("tool_choice", it) }
            input.opt("parallel_tool_calls")?.let { put("parallel_tool_calls", it) }
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
                else if (message.optString("role") == "assistant" && message.has("tool_calls")) {
                    val blocks = JSONArray()
                    message.optString("content").takeIf { it.isNotBlank() }?.let { blocks.put(JSONObject().put("type", "text").put("text", it)) }
                    message.optJSONArray("tool_calls")?.let { calls -> for (i in 0 until calls.length()) { val call = calls.getJSONObject(i); val fn = call.optJSONObject("function") ?: JSONObject(); blocks.put(JSONObject().put("type", "tool_use").put("id", call.optString("id", "tool_$i")).put("name", fn.optString("name")).put("input", runCatching { JSONObject(fn.optString("arguments", "{}")) }.getOrDefault(JSONObject()))) } }
                    messages.put(JSONObject().put("role", "assistant").put("content", blocks))
                } else if (message.optString("role") == "tool") {
                    messages.put(JSONObject().put("role", "user").put("content", JSONArray().put(JSONObject().put("type", "tool_result").put("tool_use_id", message.optString("tool_call_id")).put("content", message.optString("content")))))
                } else {
                    messages.put(JSONObject().put("role", if (message.optString("role") == "assistant") "assistant" else "user").put("content", message.opt("content") ?: ""))
                }
            }
        }
        return JSONObject().apply {
            put("model", if (input.optString("model", "auto") == "auto") providerModel else input.optString("model")); put("max_tokens", input.optInt("max_tokens", 4096)); put("messages", messages)
            if (system.isNotBlank()) put("system", system.trim())
            if (input.has("temperature")) put("temperature", input.optDouble("temperature"))
            input.optJSONArray("tools")?.let { put("tools", it) }
            input.opt("tool_choice")?.let { put("tool_choice", it) }
            if (input.has("stream")) put("stream", input.optBoolean("stream"))
        }.toString()
    }

    private fun fromAnthropic(body: String): String {
        val input = runCatching { JSONObject(body) }.getOrNull() ?: return body
        val blocks = input.optJSONArray("content")
        val text = blocks?.let { (0 until it.length()).joinToString("") { index -> it.optJSONObject(index)?.optString("text", "") ?: "" } } ?: ""
        val toolCalls = JSONArray()
        blocks?.let { for (index in 0 until it.length()) { val block = it.optJSONObject(index); if (block?.optString("type") == "tool_use") toolCalls.put(JSONObject().put("id", block.optString("id")).put("type", "function").put("function", JSONObject().put("name", block.optString("name")).put("arguments", block.optJSONObject("input")?.toString() ?: "{}"))) } }
        val message = JSONObject().put("role", "assistant").put("content", text)
        if (toolCalls.length() > 0) message.put("tool_calls", toolCalls)
        return JSONObject().apply { put("id", input.optString("id", "chatcmpl_jh_gateway")); put("object", "chat.completion"); put("model", input.optString("model", "auto")); put("choices", JSONArray().put(JSONObject().put("index", 0).put("message", message).put("finish_reason", if (toolCalls.length() > 0) "tool_calls" else input.optString("stop_reason", "end_turn")))); put("usage", input.optJSONObject("usage") ?: JSONObject()) }.toString()
    }
}
