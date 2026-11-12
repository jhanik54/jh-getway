package com.jh.gateway

import org.json.JSONArray
import org.json.JSONObject

/** Thrown when a client or provider sends a field the canonical model cannot represent without silent loss. */
class UnsupportedFieldException(val field: String) : Exception("Unsupported field: $field")

/** Thrown when a request body does not structurally match its declared protocol schema. */
class CanonicalFormatException(message: String) : Exception(message)

/**
 * Canonical internal representation for the gateway. Wire protocols (OpenAI chat, Anthropic
 * messages, OpenAI Responses) are translated through this layer so unsupported fields and
 * mappings are explicit instead of silently dropped.
 */
data class CanonicalRequest(
    val model: String,
    val messages: List<Message>,
    val maxTokens: Int,
    val temperature: Double? = null,
    val stream: Boolean = false,
    val tools: List<Tool> = emptyList(),
    val toolChoice: String = "auto",
    val parallelToolCalls: Boolean = true,
    val responseFormat: String? = null
) {
    data class Message(
        val role: String,
        val content: String,
        val name: String? = null,
        val toolCallId: String? = null,
        val toolCalls: List<ToolCall> = emptyList()
    )

    data class ToolCall(val id: String, val name: String, val arguments: String)

    data class Tool(val name: String, val description: String, val parametersJson: String)
}

/** Canonical response produced either by a client request or by an upstream provider. */
data class CanonicalResponse(
    val id: String,
    val model: String,
    val content: String,
    val toolCalls: List<CanonicalRequest.ToolCall> = emptyList(),
    val stopReason: String = "end_turn",
    val usage: Usage = Usage()
) {
    data class Usage(val promptTokens: Int = 0, val completionTokens: Int = 0, val totalTokens: Int = 0)
}

object GatewayModels {

    // Anthropic Messages API has no frequency/presence penalties, seed, logprobs, n, stream_options,
    // response_format or parallel_tool_calls. Sending them would be silently ignored upstream.
    private val unsupportedAnthropic = setOf(
        "frequency_penalty", "presence_penalty", "seed", "logprobs", "top_logprobs", "n", "stream_options",
        "response_format", "parallel_tool_calls"
    )
    private val unsupportedResponses = setOf("logprobs", "top_logprobs", "n", "response_format")
    private val toolCallIdPrefix = "toolu_"

    fun parse(body: String, protocol: ClientProtocol): CanonicalRequest {
        val input = JSONObject(body)
        val messages = mutableListOf<CanonicalRequest.Message>()
        when (protocol) {
            ClientProtocol.OPENAI -> readOpenAiMessages(input.optJSONArray("messages"), messages)
            ClientProtocol.ANTHROPIC -> readAnthropicMessages(input, messages)
            ClientProtocol.RESPONSES -> readResponsesInput(input, messages)
        }
        return CanonicalRequest(
            model = input.optString("model", "auto"),
            messages = messages,
            maxTokens = input.optInt("max_tokens", input.optInt("max_completion_tokens", 4096)),
            temperature = if (input.has("temperature")) input.optDouble("temperature") else null,
            stream = input.optBoolean("stream", false),
            tools = readTools(input.optJSONArray("tools")),
            toolChoice = input.opt("tool_choice")?.toString() ?: "auto",
            parallelToolCalls = input.optBoolean("parallel_tool_calls", true),
            responseFormat = input.optJSONObject("response_format")?.optString("type")
        )
    }

    /** Reject unsupported fields for the given provider instead of letting them vanish. */
    fun validate(body: String, provider: ProviderProtocol, protocol: ClientProtocol) {
        val input = runCatching { JSONObject(body) }.getOrElse { throw CanonicalFormatException("body is not valid JSON") }
        val candidates = when (protocol) {
            ClientProtocol.ANTHROPIC -> unsupportedAnthropic
            ClientProtocol.RESPONSES -> unsupportedResponses
            ClientProtocol.OPENAI -> if (provider == ProviderProtocol.ANTHROPIC) unsupportedAnthropic else emptySet()
        }
        for (key in input.keys()) {
            if (key in candidates) throw UnsupportedFieldException(key)
        }
        input.optInt("n", 1).let { if (it != 1) throw UnsupportedFieldException("n") }
        if (provider == ProviderProtocol.ANTHROPIC) checkImageSources(input)
        // Structural validation through the canonical parser.
        runCatching { parse(body, protocol) }.getOrElse { error ->
            if (error is UnsupportedFieldException || error is CanonicalFormatException) throw error
            throw CanonicalFormatException("request body does not match the ${protocol.name.lowercase()} schema: ${error.message}")
        }
    }

    /** Anthropic image policy: http(s) and base64 data URIs translate; anything else is rejected. */
    private fun checkImageSources(input: JSONObject) {
        val messages = input.optJSONArray("messages") ?: return
        for (i in 0 until messages.length()) {
            val content = messages.optJSONObject(i)?.opt("content")
            if (content is JSONArray) {
                for (j in 0 until content.length()) {
                    val block = content.optJSONObject(j) ?: continue
                    if (block.optString("type") != "image_url") continue
                    val url = block.optJSONObject("image_url")?.optString("url").orEmpty().trim()
                    val translatable = url.startsWith("http://", ignoreCase = true) ||
                        url.startsWith("https://", ignoreCase = true) ||
                        (url.startsWith("data:", ignoreCase = true) && url.substringAfter(",", "").isNotBlank() && url.contains("base64,", ignoreCase = true))
                    if (!translatable) throw UnsupportedFieldException("messages[].content[].image_url (unsupported image source)")
                }
            }
        }
    }

    // ---------- Request reading helpers ----------

    private fun readOpenAiMessages(array: JSONArray?, out: MutableList<CanonicalRequest.Message>) {
        if (array == null) return
        for (i in 0 until array.length()) out += canonicalFromOpenAi(array.getJSONObject(i))
    }

    private fun readAnthropicMessages(input: JSONObject, out: MutableList<CanonicalRequest.Message>) {
        input.optString("system").takeIf { it.isNotBlank() }?.let { out += CanonicalRequest.Message("system", it) }
        val messages = input.optJSONArray("messages") ?: return
        for (i in 0 until messages.length()) {
            val item = messages.getJSONObject(i)
            val text = when (val raw = item.opt("content")) {
                is JSONArray -> buildString { for (j in 0 until raw.length()) append(raw.optJSONObject(j)?.optString("text").orEmpty()) }
                is String -> raw
                else -> raw?.toString().orEmpty()
            }
            out += CanonicalRequest.Message(
                role = item.optString("role", "user"),
                content = text,
                toolCalls = anthropicToolBlocks(item.optJSONArray("content"))
            )
        }
    }

    private fun readResponsesInput(input: JSONObject, out: MutableList<CanonicalRequest.Message>) {
        when (val raw = input.opt("input")) {
            is String -> out += CanonicalRequest.Message("user", raw)
            is JSONArray -> for (i in 0 until raw.length()) {
                val item = raw.optJSONObject(i) ?: continue
                if (item.optString("role").isBlank()) continue
                out += canonicalFromOpenAi(item)
            }
        }
    }

    private fun canonicalFromOpenAi(item: JSONObject): CanonicalRequest.Message {
        val content = when (val raw = item.opt("content")) {
            is JSONArray -> buildString { for (j in 0 until raw.length()) append(raw.optJSONObject(j)?.optString("text").orEmpty()) }
            is String -> raw
            null, JSONObject.NULL -> ""
            else -> raw.toString()
        }
        return CanonicalRequest.Message(
            role = item.optString("role", "user"),
            content = content,
            name = item.optString("name").takeIf { it.isNotBlank() },
            toolCallId = item.optString("tool_call_id").takeIf { it.isNotBlank() },
            toolCalls = readToolCalls(item.optJSONArray("tool_calls"))
        )
    }

    fun readToolCalls(array: JSONArray?): List<CanonicalRequest.ToolCall> {
        if (array == null) return emptyList()
        val out = mutableListOf<CanonicalRequest.ToolCall>()
        for (i in 0 until array.length()) {
            val call = array.optJSONObject(i) ?: continue
            val fn = call.optJSONObject("function") ?: JSONObject()
            out += CanonicalRequest.ToolCall(
                id = call.optString("id", "${toolCallIdPrefix}$i"),
                name = fn.optString("name", ""),
                arguments = fn.optString("arguments", "{}")
            )
        }
        return out
    }

    private fun anthropicToolBlocks(array: JSONArray?): List<CanonicalRequest.ToolCall> {
        if (array == null) return emptyList()
        val out = mutableListOf<CanonicalRequest.ToolCall>()
        for (i in 0 until array.length()) {
            val block = array.optJSONObject(i) ?: continue
            if (block.optString("type") == "tool_use") {
                out += CanonicalRequest.ToolCall(
                    id = block.optString("id", "${toolCallIdPrefix}$i"),
                    name = block.optString("name", ""),
                    arguments = block.optJSONObject("input")?.toString() ?: "{}"
                )
            }
        }
        return out
    }

    private fun readTools(array: JSONArray?): List<CanonicalRequest.Tool> = buildList {
        if (array == null) return@buildList
        for (i in 0 until array.length()) {
            val tool = array.optJSONObject(i) ?: continue
            val fn = tool.optJSONObject("function") ?: tool
            add(
                CanonicalRequest.Tool(
                    name = fn.optString("name", ""),
                    description = fn.optString("description", ""),
                    parametersJson = (tool.optJSONObject("input_schema") ?: fn.optJSONObject("parameters") ?: JSONObject()).toString()
                )
            )
        }
    }

    // ---------- Stop reason mapping (Anthropic <-> OpenAI) ----------

    fun stopReasonToOpenAi(stopReason: String): String = when (stopReason) {
        "end_turn", "stop_sequence" -> "stop"
        "max_tokens" -> "length"
        "tool_use" -> "tool_calls"
        else -> stopReason
    }

    fun stopReasonFromOpenAi(finishReason: String): String = when (finishReason) {
        "stop" -> "end_turn"
        "length" -> "max_tokens"
        "tool_calls" -> "tool_use"
        "content_filter" -> "stop_sequence"
        else -> finishReason
    }

    // ---------- Usage mapping (Anthropic <-> OpenAI) ----------

    fun usageToOpenAi(input: JSONObject?): JSONObject {
        val inTokens = input?.optInt("input_tokens", 0) ?: 0
        val outTokens = input?.optInt("output_tokens", 0) ?: 0
        return JSONObject().apply {
            put("prompt_tokens", inTokens)
            put("completion_tokens", outTokens)
            put("total_tokens", inTokens + outTokens)
        }
    }

    fun usageFromOpenAi(input: JSONObject?): JSONObject = JSONObject().apply {
        put("input_tokens", input?.optInt("prompt_tokens", 0) ?: 0)
        put("output_tokens", input?.optInt("completion_tokens", 0) ?: 0)
    }

    fun usageToAnthropic(usage: CanonicalResponse.Usage): JSONObject = JSONObject().apply {
        put("input_tokens", usage.promptTokens)
        put("output_tokens", usage.completionTokens)
    }

    fun usageFromOpenAiResponse(input: JSONObject?): CanonicalResponse.Usage {
        val prompt = input?.optInt("prompt_tokens", 0) ?: 0
        val completion = input?.optInt("completion_tokens", 0) ?: 0
        return CanonicalResponse.Usage(prompt, completion, prompt + completion)
    }
}
