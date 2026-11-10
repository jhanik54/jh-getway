package com.jh.gateway

import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStream
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class ForwardResult(val status: Int, val body: String, val provider: String? = null, val retryAfterMillis: Long? = null)

class ProviderRouter(private val store: ProviderStore, private val chainStore: ChainStore, private val settings: GatewaySettings) {
    private val roundRobin = ConcurrentHashMap<String, AtomicInteger>()
    private val sticky = ConcurrentHashMap<String, String>()
    fun forward(body: String): ForwardResult {
        val requestedModel = runCatching { JSONObject(body).optString("model", "auto") }.getOrDefault("auto")
        val candidates = eligible(requestedModel, body)
        if (candidates.isEmpty()) return ForwardResult(503, "{\"error\":{\"message\":\"All providers are disabled or cooling down\",\"type\":\"gateway_unavailable\"}}")
        var last = ForwardResult(502, "{\"error\":{\"message\":\"No provider response\",\"type\":\"provider_error\"}}")
        for (provider in candidates) {
            val key = store.apiKey(provider) ?: continue
            last = runCatching { request(provider, key, body) }.getOrElse { ForwardResult(502, "{\"error\":{\"message\":\"${escape(it.message ?: "Network error")}\",\"type\":\"network_error\"}}", provider.name) }
            when {
                last.status in 200..299 -> {
                    sessionKey(body)?.let { sticky[it] = provider.id }
                    return last.copy(provider = provider.name)
                }
                last.status == 401 || last.status == 403 -> store.disable(provider)
                last.status == 429 -> store.markCooldown(provider, last.retryAfterMillis ?: 60_000)
                last.status >= 500 -> store.markCooldown(provider, last.retryAfterMillis ?: 30_000)
            }
        }
        return last
    }

    /** Transparent SSE pass-through. Fallback is still possible until the first upstream byte. */
    fun stream(body: String, output: OutputStream, clientProtocol: ClientProtocol = ClientProtocol.OPENAI): ForwardResult {
        val requestedModel = runCatching { JSONObject(body).optString("model", "auto") }.getOrDefault("auto")
        val candidates = eligible(requestedModel, body)
        if (candidates.isEmpty()) return ForwardResult(503, "{\"error\":{\"message\":\"All providers are unavailable\",\"type\":\"gateway_unavailable\"}}")
        for (provider in candidates) {
            val key = store.apiKey(provider) ?: continue
            val connectionResult = runCatching { open(provider, key, ProtocolAdapter.toProvider(body, provider)) }
            if (connectionResult.isFailure) { store.markCooldown(provider, 30_000); continue }
            val connection = connectionResult.getOrThrow()
            val status = connection.responseCode
            if (status in 200..299) {
                val event = StringBuilder()
                connection.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                    if (line.startsWith("event:")) event.setLength(0).also { event.append(line.substringAfter(':').trim()) }
                    else if (line.startsWith("data:")) {
                        val translated = ProtocolAdapter.translateStreamData(line.substringAfter(':').trim(), event.toString().ifBlank { null }, clientProtocol, provider.protocol)
                        if (translated != null) { output.write(translated.toByteArray()); output.flush() }
                    }
                } }
                connection.disconnect()
                sessionKey(body)?.let { sticky[it] = provider.id }
                return ForwardResult(status, "", provider.name)
            }
            if (status == 401 || status == 403) store.disable(provider)
            if (status == 429) store.markCooldown(provider, retryAfter(connection) ?: 60_000)
            if (status >= 500) store.markCooldown(provider, retryAfter(connection) ?: 30_000)
            connection.disconnect()
        }
        return ForwardResult(502, "{\"error\":{\"message\":\"All providers failed before streaming\",\"type\":\"provider_error\"}}")
    }

    private fun eligible(requestedModel: String, body: String): List<StoredProvider> {
        val providers = store.list().filter { provider ->
        provider.enabled && !provider.isCoolingDown && (requestedModel.isBlank() || requestedModel == "auto" || provider.model == "auto" || provider.model == requestedModel || requestedModel in provider.aliases)
        }
        val chain = chainStore.load(store.list()).filter { it.enabled }.withIndex().associate { it.value.id to (it.index to it.value.tier.ordinal) }
        val sorted = providers.sortedWith(compareBy({ chain[it.id]?.second ?: 0 }, { chain[it.id]?.first ?: Int.MAX_VALUE }, { it.priority }))
        sessionKey(body)?.let { key -> sticky[key]?.let { id -> sorted.firstOrNull { it.id == id }?.let { selected -> return listOf(selected) + sorted.filterNot { it.id == selected.id } } } }
        if (sorted.size < 2) return sorted
        val bestTier = chain[sorted.first().id]?.second ?: 0
        val sameTier = sorted.filter { (chain[it.id]?.second ?: 0) == bestTier }
        if (sameTier.size < 2) return sorted
        val offset = (roundRobin.getOrPut(requestedModel.ifBlank { "auto" }) { AtomicInteger(0) }.getAndIncrement() and Int.MAX_VALUE) % sameTier.size
        val rotated = sameTier.drop(offset) + sameTier.take(offset)
        return rotated + sorted.filterNot { it.id in sameTier.map { entry -> entry.id }.toSet() }
    }

    private fun sessionKey(body: String): String? = runCatching {
        val input = JSONObject(body)
        input.optString("user").takeIf { it.isNotBlank() }
            ?: input.optJSONObject("metadata")?.optString("conversation_id")?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun request(provider: StoredProvider, key: String, body: String): ForwardResult {
        val connection = open(provider, key, ProtocolAdapter.toProvider(body, provider))
        val status = connection.responseCode
        val retryAfter = retryAfter(connection)
        val stream = if (status >= 400) connection.errorStream else connection.inputStream
        val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
        connection.disconnect()
        return ForwardResult(status, ProtocolAdapter.fromProvider(response, provider), retryAfterMillis = retryAfter)
    }

    private fun open(provider: StoredProvider, key: String, body: String): HttpURLConnection {
        val connection = (URL(endpoint(provider.baseUrl, provider.protocol)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = settings.connectTimeoutMs
            readTimeout = settings.readTimeoutMs
            doOutput = true
            if (provider.protocol == ProviderProtocol.ANTHROPIC) { setRequestProperty("x-api-key", key); setRequestProperty("anthropic-version", "2023-06-01") } else setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        connection.outputStream.use { it.write(body.toByteArray()) }
        return connection
    }

    private fun endpoint(base: String, protocol: ProviderProtocol): String = if (protocol == ProviderProtocol.ANTHROPIC) { if (base.endsWith("/v1")) "$base/messages" else "$base/v1/messages" } else if (base.endsWith("/v1")) "$base/chat/completions" else "$base/v1/chat/completions"
    private fun retryAfter(connection: HttpURLConnection): Long? = connection.getHeaderField("Retry-After")?.trim()?.let { value -> value.toLongOrNull()?.times(1_000L) ?: runCatching { java.time.ZonedDateTime.parse(value).toInstant().toEpochMilli() - System.currentTimeMillis() }.getOrNull()?.coerceAtLeast(1_000L) }
    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
}
