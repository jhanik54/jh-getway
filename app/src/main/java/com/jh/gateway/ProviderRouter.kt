package com.jh.gateway

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class ForwardResult(val status: Int, val body: String, val provider: String? = null)

class ProviderRouter(private val store: ProviderStore) {
    fun forward(body: String): ForwardResult {
        val candidates = store.list().filter { it.enabled && !it.isCoolingDown }
        if (candidates.isEmpty()) return ForwardResult(503, "{\"error\":{\"message\":\"All providers are disabled or cooling down\",\"type\":\"gateway_unavailable\"}}")
        var last = ForwardResult(502, "{\"error\":{\"message\":\"No provider response\",\"type\":\"provider_error\"}}")
        for (provider in candidates) {
            val key = store.apiKey(provider) ?: continue
            last = runCatching { request(provider, key, body) }.getOrElse { ForwardResult(502, "{\"error\":{\"message\":\"${escape(it.message ?: "Network error")}\",\"type\":\"network_error\"}}") }
            when {
                last.status in 200..299 -> return last.copy(provider = provider.name)
                last.status == 401 || last.status == 403 -> store.disable(provider)
                last.status == 429 -> store.markCooldown(provider, 60_000)
            }
        }
        return last
    }

    private fun request(provider: StoredProvider, key: String, body: String): ForwardResult {
        val connection = (URL(endpoint(provider.baseUrl)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        connection.outputStream.use { it.write(body.toByteArray()) }
        val status = connection.responseCode
        val stream = if (status >= 400) connection.errorStream else connection.inputStream
        val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
        connection.disconnect()
        return ForwardResult(status, response)
    }

    private fun endpoint(base: String): String = if (base.endsWith("/v1")) "$base/chat/completions" else "$base/v1/chat/completions"
    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}
