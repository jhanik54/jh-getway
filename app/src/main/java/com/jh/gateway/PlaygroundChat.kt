package com.jh.gateway

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends a chat completion from the Playground.
 *
 * Two routes are supported:
 *  - through the running gateway (the gateway key is used), which exercises the whole
 *    local stack including routing and failover;
 *  - straight to the provider (the provider key is used), which is what the user needs
 *    while picking a model that is still free.
 */
class PlaygroundChat(private val context: Context) {

    data class Message(val role: String, val content: String)

    sealed class Outcome {
        data class Success(val reply: String, val model: String) : Outcome()
        data class Failure(val message: String) : Outcome()
    }

    /** Sends the conversation and reports the assistant reply. */
    fun send(
        messages: List<Message>,
        model: String,
        target: Target,
        callback: (Outcome) -> Unit
    ) {
        Thread {
            val outcome = runCatching {
                when (target) {
                    is Target.Gateway -> sendThroughGateway(messages, model, target.baseUrl, target.key)
                    is Target.Provider -> sendDirect(messages, model, target.baseUrl, target.key, target.protocol)
                }
            }.getOrElse { Outcome.Failure(it.message ?: "The request failed") }
            callback(outcome)
        }.start()
    }

    sealed class Target {
        /** Route through the local gateway; [key] is the gateway key. */
        data class Gateway(val baseUrl: String, val key: String) : Target()
        /** Call the provider directly; [key] is the provider API key. */
        data class Provider(val baseUrl: String, val key: String, val protocol: ProviderProtocol) : Target()
    }

    private fun sendThroughGateway(messages: List<Message>, model: String, baseUrl: String, key: String): Outcome {
        val endpoint = baseUrl.trimEnd('/') + "/chat/completions"
        val payload = JSONObject().apply {
            put("model", model.ifBlank { "auto" })
            put("messages", JSONArray().apply { messages.forEach { message -> put(JSONObject().put("role", message.role).put("content", message.content)) } })
            put("stream", false)
        }.toString()
        val connection = openConnection(endpoint, key, key)
        return finish(connection, payload, model)
    }

    private fun sendDirect(messages: List<Message>, model: String, baseUrl: String, key: String, protocol: ProviderProtocol): Outcome {
        val trimmed = baseUrl.trimEnd('/')
        val endpoint = when {
            protocol == ProviderProtocol.ANTHROPIC -> if (trimmed.endsWith("/v1")) "$trimmed/messages" else "$trimmed/v1/messages"
            trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
            else -> "$trimmed/v1/chat/completions"
        }
        val payload = if (protocol == ProviderProtocol.ANTHROPIC) {
            JSONObject().apply {
                put("model", model.ifBlank { "auto" })
                put("max_tokens", 1024)
                put("messages", JSONArray().apply {
                    messages.filter { it.role != "system" }.forEach { message -> put(JSONObject().put("role", message.role).put("content", message.content)) }
                })
            }.toString()
        } else {
            JSONObject().apply {
                put("model", model.ifBlank { "auto" })
                put("messages", JSONArray().apply { messages.forEach { message -> put(JSONObject().put("role", message.role).put("content", message.content)) } })
                put("stream", false)
            }.toString()
        }
        val connection = openConnection(endpoint, key, key, protocol)
        return finish(connection, payload, model, protocol)
    }

    private fun openConnection(endpoint: String, bearer: String, apiKey: String, protocol: ProviderProtocol = ProviderProtocol.OPENAI): HttpURLConnection {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (protocol == ProviderProtocol.ANTHROPIC) {
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
            } else {
                setRequestProperty("Authorization", "Bearer $bearer")
            }
        }
        return connection
    }

    private fun finish(connection: HttpURLConnection, payload: String, model: String, protocol: ProviderProtocol = ProviderProtocol.OPENAI): Outcome {
        try {
            connection.outputStream.use { it.write(payload.toByteArray()) }
            val status = connection.responseCode
            val body = (if (status >= 400) connection.errorStream else connection.inputStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status >= 400) return Outcome.Failure(errorMessage(body, status))
            val reply = extractReply(body, protocol)
                ?: return Outcome.Failure("The provider replied without any text.")
            return Outcome.Success(reply, model)
        } finally {
            connection.disconnect()
        }
    }

    private fun errorMessage(body: String, status: Int): String {
        val parsed = runCatching { JSONObject(body) }.getOrNull()
        val message = parsed?.optJSONObject("error")?.optString("message")
            ?: parsed?.optString("message")
            ?: body.take(300)
        return if (message.isBlank()) "The provider returned HTTP $status." else "HTTP $status: $message"
    }

    /** Pulls the assistant text out of an OpenAI or Anthropic style response. */
    private fun extractReply(body: String, protocol: ProviderProtocol): String? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (protocol == ProviderProtocol.ANTHROPIC) {
            val content = json.optJSONArray("content") ?: return null
            val text = (0 until content.length()).mapNotNull { content.optJSONObject(it)?.optString("text") }.joinToString("\n")
            return text.ifBlank { null }
        }
        val choices = json.optJSONArray("choices") ?: return null
        val first = choices.optJSONObject(0) ?: return null
        val message = first.optJSONObject("message")
        val content = message?.optString("content")
        if (!content.isNullOrBlank()) return content
        // Some providers answer with a reasoning/text field instead.
        val text = first.optString("text")
        return text.ifBlank { null }
    }

    companion object {
        /** The local gateway address for the playground. */
        fun gatewayTarget(context: Context, port: Int, gatewayKey: String, useLanAddress: Boolean, lanAddress: String?): Target.Gateway {
            val host = if (useLanAddress && !lanAddress.isNullOrBlank()) lanAddress else "127.0.0.1"
            return Target.Gateway("http://$host:$port/v1", gatewayKey)
        }
    }
}
