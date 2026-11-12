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
        // Explicit provider error events, translated to the client's native error shape.
        if (input.optString("type") == "error" || input.optJSONObject("error") != null) {
            val message = input.optJSONObject("error")?.optString("message")?.ifBlank { null }
                ?: input.optString("message").ifBlank { "Provider error" }
            return when (client) {
                ClientProtocol.ANTHROPIC -> "event: error\ndata: {\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":${JSONObject.quote(message)}}}\n\n"
                else -> "data: {\"error\":{\"message\":${JSONObject.quote(message)},\"type\":\"provider_error\"}}\n\n"
            }
        }
        // ---------- Anthropic upstream ----------
        if (provider == ProviderProtocol.ANTHROPIC) {
            when (input.optString("type")) {
                "message_start", "ping", "content_block_stop" -> return null
                "content_block_start" -> {
                    if (client != ClientProtocol.OPENAI) return null
                    val block = input.optJSONObject("content_block")
                    if (block?.optString("type") != "tool_use") return null
                    val toolCall = JSONObject().put("index", input.optInt("index", 0))
                        .put("id", block.optString("id"))
                        .put("type", "function")
                        .put("function", JSONObject().put("name", block.optString("name")).put("arguments", ""))
                    val delta = JSONObject().put("tool_calls", JSONArray().put(toolCall))
                    val choice = JSONObject().put("index", 0).put("delta", delta).put("finish_reason", JSONObject.NULL)
                    val chunk = JSONObject()
                        .put("id", "chatcmpl_jh_gateway")
                        .put("object", "chat.completion.chunk")
                        .put("model", "auto")
                        .put("choices", JSONArray().put(choice))
                    return "data: $chunk\n\n"
                }
                "content_block_delta" -> {
                    val delta = input.optJSONObject("delta") ?: return null
                    if (client == ClientProtocol.OPENAI) {
                        when (delta.optString("type")) {
                            "text_delta" -> {
                                val text = delta.optString("text").orEmpty()
                                return if (text.isNotBlank()) openAiChunk(text = text) else null
                            }
                            "input_json_delta" -> {
                                val partial = delta.optString("partial_json").orEmpty()
                                return if (partial.isNotBlank()) openAiChunk(toolArgs = partial, index = input.optInt("index", 0)) else null
                            }
                            else -> return null
                        }
                    }
                    if (client == ClientProtocol.RESPONSES) {
                        if (delta.optString("type") != "text_delta") return null
                        val text = delta.optString("text").orEmpty()
                        return if (text.isNotBlank()) "data: {\"type\":\"response.output_text.delta\",\"delta\":${JSONObject.quote(text)}}\n\n" else null
                    }
                    return passthroughEvent(data, event) // Anthropic -> Anthropic
                }
                "message_delta" -> {
                    if (client == ClientProtocol.ANTHROPIC) return passthroughEvent(data, event)
                    val stopReason = input.optJSONObject("delta")?.optString("stop_reason").orEmpty().takeIf { it.isNotBlank() && it != "null" }
                    if (client == ClientProtocol.OPENAI) {
                        val usage = input.optJSONObject("usage")
                        val chunk = JSONObject().apply {
                            put("id", "chatcmpl_jh_gateway"); put("object", "chat.completion.chunk"); put("model", "auto")
                            put("choices", JSONArray().put(JSONObject().put("index", 0).put("delta", JSONObject()).put("finish_reason", stopReason?.let { GatewayModels.stopReasonToOpenAi(it) } ?: JSONObject.NULL)))
                            usage?.let { put("usage", JSONObject().put("prompt_tokens", it.optInt("input_tokens", 0)).put("completion_tokens", it.optInt("output_tokens", 0)).put("total_tokens", it.optInt("input_tokens", 0) + it.optInt("output_tokens", 0))) }
                        }
                        return "data: $chunk\n\n"
                    }
                    // RESPONSES client: wait for message_stop to emit response.completed
                    return null
                }
                "message_stop" -> return when (client) {
                    ClientProtocol.OPENAI -> "data: [DONE]\n\n"
                    ClientProtocol.RESPONSES -> "data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\"}}\n\ndata: [DONE]\n\n"
                    ClientProtocol.ANTHROPIC -> passthroughEvent(data, event)
                }
                else -> return passthroughEvent(data, event)
            }
        }
        // ---------- OpenAI upstream ----------
        if (client == ClientProtocol.ANTHROPIC && provider == ProviderProtocol.OPENAI) {
            val choice = input.optJSONArray("choices")?.optJSONObject(0) ?: return null
            val delta = choice.optJSONObject("delta")
            val out = StringBuilder()
            val toolCalls = delta?.optJSONArray("tool_calls")
            if (toolCalls != null) {
                for (i in 0 until toolCalls.length()) {
                    val call = toolCalls.optJSONObject(i) ?: continue
                    val fn = call.optJSONObject("function")
                    val index = call.optInt("index", 0)
                    val callId = call.optString("id").orEmpty()
                    val name = fn?.optString("name").orEmpty()
                    val args = fn?.optString("arguments").orEmpty()
                    if (callId.isNotBlank() || name.isNotBlank()) {
                        out.append("event: content_block_start\ndata: {\"type\":\"content_block_start\",\"index\":$index,\"content_block\":{\"type\":\"tool_use\",\"id\":${JSONObject.quote(callId.ifBlank { "toolu_$index" })},\"name\":${JSONObject.quote(name)},\"input\":{}}}\n\n")
                    }
                    if (args.isNotBlank()) {
                        out.append("event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":$index,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":${JSONObject.quote(args)}}}\n\n")
                    }
                }
            }
            val text = delta?.optString("content").orEmpty()
            if (text.isNotEmpty()) {
                out.append("event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":${JSONObject.quote(text)}}}\n\n")
            }
            val finish = choice.optString("finish_reason").takeIf { it.isNotBlank() && it != "null" }
            if (finish != null) {
                val stop = GatewayModels.stopReasonFromOpenAi(finish)
                out.append("event: message_delta\ndata: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"$stop\",\"stop_sequence\":null},\"usage\":{\"output_tokens\":0}}\n\n")
                out.append("event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n")
            }
            return out.toString().ifEmpty { null }
        }
        if (client == ClientProtocol.RESPONSES && provider == ProviderProtocol.OPENAI) {
            val choice = input.optJSONArray("choices")?.optJSONObject(0) ?: return null
            val out = StringBuilder()
            val text = choice.optJSONObject("delta")?.optString("content").orEmpty()
            if (text.isNotBlank()) out.append("data: {\"type\":\"response.output_text.delta\",\"delta\":${JSONObject.quote(text)}}\n\n")
            val finish = choice.optString("finish_reason").takeIf { it.isNotBlank() && it != "null" }
            if (finish != null) {
                val status = if (finish == "length" || finish == "content_filter") "incomplete" else "completed"
                out.append("data: {\"type\":\"response.completed\",\"response\":{\"status\":\"$status\"}}\n\ndata: [DONE]\n\n")
            }
            return out.toString().ifEmpty { null }
        }
        // Same-protocol pass-through, preserving event lines.
        return passthroughEvent(data, event)
    }

    private fun passthroughEvent(data: String, event: String?): String =
        (if (!event.isNullOrBlank()) "event: $event\n" else "") + "data: $data\n\n"

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
        // Reject fields the client protocol does not support BEFORE translating, so the
        // field is never silently dropped on the way to the provider.
        val unsupported = when (protocol) {
            ClientProtocol.ANTHROPIC -> setOf("frequency_penalty", "presence_penalty", "seed", "logprobs", "top_logprobs", "n", "stream_options", "response_format", "parallel_tool_calls")
            ClientProtocol.RESPONSES -> setOf("logprobs", "top_logprobs", "n", "response_format")
            ClientProtocol.OPENAI -> emptySet()
        }
        input.keys().forEach { key -> if (key in unsupported) throw UnsupportedFieldException(key) }
        input.optInt("n", 1).let { if (it != 1) throw UnsupportedFieldException("n") }
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
            input.optJSONArray("stop_sequences")?.let { put("stop", it) }
            input.optJSONObject("metadata")?.optString("user_id")?.takeIf { it.isNotBlank() }?.let { put("user", it) }
            input.optJSONArray("tools")?.let { put("tools", it) }
            input.opt("tool_choice")?.let { put("tool_choice", it) }
            input.opt("parallel_tool_calls")?.let { put("parallel_tool_calls", it) }
            input.optJSONObject("response_format")?.let { put("response_format", it) }
            input.optInt("max_output_tokens", 0).takeIf { it > 0 }?.let { put("max_tokens", it) } ?: input.optInt("max_tokens", 0).takeIf { it > 0 }?.let { put("max_tokens", it) }
        }.toString()
    }

    fun fromOpenAi(body: String, protocol: ClientProtocol): String {
        if (protocol == ClientProtocol.OPENAI || body.isBlank()) return body
        val input = runCatching { JSONObject(body) }.getOrNull() ?: return body
        if (input.optString("type") == "error" || input.optJSONObject("error") != null) {
            // Provider returned an error envelope: surface it in the client's native error shape.
            val err = input.optJSONObject("error")
            val message = err?.optString("message")?.ifBlank { null } ?: input.optString("message").ifBlank { "Provider error" }
            return if (protocol == ClientProtocol.ANTHROPIC) {
                JSONObject().put("type", "error").put("error", JSONObject().put("type", "api_error").put("message", message)).toString()
            } else body
        }
        val choice = input.optJSONArray("choices")?.optJSONObject(0) ?: return body
        val message = choice.optJSONObject("message") ?: JSONObject()
        val text = message.optString("content", "")
        val toolCalls = GatewayModels.readToolCalls(message.optJSONArray("tool_calls"))
        val finish = choice.optString("finish_reason", "stop")
        val stopReason = if (toolCalls.isNotEmpty()) "tool_use" else GatewayModels.stopReasonFromOpenAi(finish)
        val canonical = CanonicalResponse(
            id = input.optString("id", if (protocol == ClientProtocol.ANTHROPIC) "msg_jh_gateway" else "resp_jh_gateway"),
            model = input.optString("model", "auto"),
            content = text,
            toolCalls = toolCalls,
            stopReason = stopReason,
            usage = GatewayModels.usageFromOpenAiResponse(input.optJSONObject("usage"))
        )
        return when (protocol) {
            ClientProtocol.ANTHROPIC -> canonicalToAnthropic(canonical)
            else -> canonicalToResponses(canonical)
        }
    }

    private fun toAnthropic(body: String, providerModel: String): String {
        val input = JSONObject(body)
        val messages = JSONArray()
        var system = ""
        input.optJSONArray("messages")?.let { source ->
            for (i in 0 until source.length()) {
                val message = source.getJSONObject(i)
                if (message.optString("role") == "system" || message.optString("role") == "developer") system += jsonText(message.opt("content")) + "\n"
                else if (message.optString("role") == "assistant" && message.has("tool_calls")) {
                    val blocks = JSONArray()
                    message.optString("content").takeIf { it.isNotBlank() }?.let { blocks.put(JSONObject().put("type", "text").put("text", it)) }
                    message.optJSONArray("tool_calls")?.let { calls -> for (i in 0 until calls.length()) { val call = calls.getJSONObject(i); val fn = call.optJSONObject("function") ?: JSONObject(); blocks.put(JSONObject().put("type", "tool_use").put("id", call.optString("id", "tool_$i")).put("name", fn.optString("name")).put("input", runCatching { JSONObject(fn.optString("arguments", "{}")) }.getOrDefault(JSONObject()))) } }
                    messages.put(JSONObject().put("role", "assistant").put("content", blocks))
                } else if (message.optString("role") == "tool") {
                    messages.put(JSONObject().put("role", "user").put("content", JSONArray().put(JSONObject().put("type", "tool_result").put("tool_use_id", message.optString("tool_call_id")).put("content", message.optString("content")))))
                } else {
                    val content = message.opt("content")
                    val role = if (message.optString("role") == "assistant") "assistant" else "user"
                    if (content is JSONArray) {
                        val blocks = JSONArray()
                        for (j in 0 until content.length()) {
                            val block = content.optJSONObject(j) ?: continue
                            blocks.put(translateContentBlock(block))
                        }
                        messages.put(JSONObject().put("role", role).put("content", blocks))
                    } else {
                        messages.put(JSONObject().put("role", role).put("content", content ?: ""))
                    }
                }
            }
        }
        return JSONObject().apply {
            put("model", if (input.optString("model", "auto") == "auto") providerModel else input.optString("model")); put("max_tokens", input.optInt("max_tokens", input.optInt("max_completion_tokens", 4096))); put("messages", messages)
            if (system.isNotBlank()) put("system", system.trim())
            if (input.has("temperature")) put("temperature", input.optDouble("temperature"))
            when (val stop = input.opt("stop")) {
                is String -> if (stop.isNotBlank()) put("stop_sequences", JSONArray().put(stop))
                is JSONArray -> if (stop.length() > 0) put("stop_sequences", stop)
            }
            input.optString("user").takeIf { it.isNotBlank() }?.let { put("metadata", JSONObject().put("user_id", it)) }
            input.optJSONArray("tools")?.let { put("tools", openAiToolsToAnthropic(it)) }
            anthropicToolChoice(input.opt("tool_choice"))?.let { put("tool_choice", it) }
            if (input.has("stream")) put("stream", input.optBoolean("stream"))
        }.toString()
    }

    private fun fromAnthropic(body: String): String {
        val input = runCatching { JSONObject(body) }.getOrNull() ?: return body
        if (input.optString("type") == "error" || input.optJSONObject("error") != null) {
            val err = input.optJSONObject("error")
            val message = err?.optString("message")?.ifBlank { null } ?: input.optString("message").ifBlank { "Provider error" }
            val type = err?.optString("type")?.ifBlank { null } ?: "provider_error"
            return JSONObject().put("error", JSONObject().put("message", message).put("type", type)).toString()
        }
        val blocks = input.optJSONArray("content")
        val text = blocks?.let { (0 until it.length()).joinToString("") { index -> it.optJSONObject(index)?.optString("text", "") ?: "" } } ?: ""
        val toolCalls = JSONArray()
        blocks?.let { for (index in 0 until it.length()) { val block = it.optJSONObject(index); if (block?.optString("type") == "tool_use") toolCalls.put(JSONObject().put("id", block.optString("id")).put("type", "function").put("function", JSONObject().put("name", block.optString("name")).put("arguments", block.optJSONObject("input")?.toString() ?: "{}"))) } }
        val stopReason = if (toolCalls.length() > 0) "tool_use" else input.optString("stop_reason", "end_turn")
        val message = JSONObject().put("role", "assistant").put("content", text)
        if (toolCalls.length() > 0) message.put("tool_calls", toolCalls)
        return JSONObject().apply { put("id", input.optString("id", "chatcmpl_jh_gateway")); put("object", "chat.completion"); put("model", input.optString("model", "auto")); put("choices", JSONArray().put(JSONObject().put("index", 0).put("message", message).put("finish_reason", GatewayModels.stopReasonToOpenAi(stopReason)))); put("usage", GatewayModels.usageToOpenAi(input.optJSONObject("usage"))) }.toString()
    }

    private fun openAiChunk(text: String? = null, toolArgs: String? = null, index: Int = 0): String {
        val delta = JSONObject()
        if (!text.isNullOrEmpty()) delta.put("content", text)
        if (!toolArgs.isNullOrEmpty()) {
            delta.put("tool_calls", JSONArray().put(JSONObject().put("index", index).put("type", "function").put("function", JSONObject().put("arguments", toolArgs))))
        }
        val chunk = JSONObject().apply {
            put("id", "chatcmpl_jh_gateway"); put("object", "chat.completion.chunk"); put("model", "auto")
            put("choices", JSONArray().put(JSONObject().put("index", 0).put("delta", delta).put("finish_reason", JSONObject.NULL)))
        }
        return "data: $chunk\n\n"
    }

    /** Extracts plain text from OpenAI string-or-array message content. */
    private fun jsonText(value: Any?): String = when (value) {
        is String -> value
        is JSONArray -> buildString {
            for (i in 0 until value.length()) append(value.optJSONObject(i)?.optString("text").orEmpty())
        }
        else -> value?.toString().orEmpty()
    }

    /** OpenAI-style multimodal blocks -> Anthropic blocks. Checked by GatewayModels.validate first. */
    private fun translateContentBlock(block: JSONObject): JSONObject = when (block.optString("type")) {
        "image_url" -> {
            val url = block.optJSONObject("image_url")?.optString("url").orEmpty().trim()
            anthropicImageBlock(url)
        }
        else -> block
    }

    private fun anthropicImageBlock(url: String): JSONObject {
        if (url.startsWith("data:", ignoreCase = true)) {
            val header = url.substringBefore(',', "")
            val data = url.substringAfter(',', "")
            val mediaType = header.removePrefix("data:").substringBefore(";").ifBlank { "image/png" }
            return JSONObject().put("type", "image").put("source", JSONObject().put("type", "base64").put("media_type", mediaType).put("data", data))
        }
        return JSONObject().put("type", "image").put("source", JSONObject().put("type", "url").put("url", url))
    }

    /** OpenAI tool definitions ({type:function, function:{name,description,parameters}}) -> Anthropic ({name, description, input_schema}). Anthropic-shaped tools pass through. */
    private fun openAiToolsToAnthropic(tools: JSONArray): JSONArray {
        val out = JSONArray()
        for (i in 0 until tools.length()) {
            val tool = tools.optJSONObject(i) ?: continue
            val fn = tool.optJSONObject("function")
            if (fn != null) {
                out.put(
                    JSONObject().put("name", fn.optString("name"))
                        .put("description", fn.optString("description", ""))
                        .put("input_schema", fn.optJSONObject("parameters") ?: JSONObject().put("type", "object"))
                )
            } else {
                out.put(tool)
            }
        }
        return out
    }

    /** OpenAI tool_choice -> Anthropic tool_choice. {"type":"auto"|"any"|"tool"|"none"} passes through. */
    private fun anthropicToolChoice(choice: Any?): Any? = when (choice) {
        null -> null
        is String -> when (choice.lowercase()) {
            "required" -> JSONObject().put("type", "any")
            "none" -> JSONObject().put("type", "none")
            else -> JSONObject().put("type", "auto")
        }
        is JSONObject -> when (choice.optString("type")) {
            "function" -> {
                val name = choice.optJSONObject("function")?.optString("name").orEmpty()
                if (name.isNotBlank()) JSONObject().put("type", "tool").put("name", name) else JSONObject().put("type", "auto")
            }
            else -> choice
        }
        else -> choice
    }

    private fun canonicalToAnthropic(response: CanonicalResponse): String {
        val content = JSONArray()
        if (response.content.isNotBlank()) content.put(JSONObject().put("type", "text").put("text", response.content))
        for (call in response.toolCalls) {
            content.put(
                JSONObject().put("type", "tool_use").put("id", call.id).put("name", call.name)
                    .put("input", runCatching { JSONObject(call.arguments) }.getOrDefault(JSONObject()))
            )
        }
        return JSONObject().apply {
            put("id", response.id); put("type", "message"); put("role", "assistant"); put("model", response.model)
            put("content", content); put("stop_reason", response.stopReason)
            put("usage", GatewayModels.usageToAnthropic(response.usage))
        }.toString()
    }

    private fun canonicalToResponses(response: CanonicalResponse): String {
        val output = JSONArray()
        output.put(
            JSONObject().put("type", "message").put("role", "assistant")
                .put("content", JSONArray().put(JSONObject().put("type", "output_text").put("text", response.content)))
        )
        for (call in response.toolCalls) {
            output.put(
                JSONObject().put("type", "function_call")
                    .put("call_id", call.id)
                    .put("name", call.name)
                    .put("arguments", call.arguments)
            )
        }
        return JSONObject().apply {
            put("id", response.id); put("object", "response"); put("model", response.model); put("output", output)
            put("status", if (response.stopReason == "max_tokens") "incomplete" else "completed")
            put("usage", JSONObject().put("input_tokens", response.usage.promptTokens).put("output_tokens", response.usage.completionTokens).put("total_tokens", response.usage.totalTokens))
        }.toString()
    }
}
