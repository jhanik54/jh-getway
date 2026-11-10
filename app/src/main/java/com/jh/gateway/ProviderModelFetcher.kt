package com.jh.gateway

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ProviderModelFetcher {
    fun fetch(baseUrl: String, apiKey: String, protocol: ProviderProtocol, callback: (Result<List<String>>) -> Unit) {
        Thread {
            callback(runCatching {
                if (protocol == ProviderProtocol.ANTHROPIC) error("Anthropic does not expose a standard /models endpoint; enter a model manually")
                val endpoint = if (baseUrl.trimEnd('/').endsWith("/v1")) "${baseUrl.trimEnd('/')}/models" else "${baseUrl.trimEnd('/')}/v1/models"
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "JH-GetWay")
                }
                val status = connection.responseCode
                val body = (if (status >= 400) connection.errorStream else connection.inputStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                check(status in 200..299) { "Provider returned HTTP $status" }
                val data = JSONObject(body).optJSONArray("data") ?: error("Provider returned no model list")
                (0 until data.length()).mapNotNull { data.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank) }.distinct().sorted()
            })
        }.start()
    }
}
