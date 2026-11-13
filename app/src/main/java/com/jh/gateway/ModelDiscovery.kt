package com.jh.gateway

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Discovers the model list of a provider and classifies the models as free or paid.
 *
 * Used by the Playground: the user pastes an API key, the app talks to the provider's
 * model endpoint directly, and the answer is cached so the free/paid counts are shown
 * immediately on the next launch.
 */
class ModelDiscovery(private val context: Context) {
    private val prefs = context.getSharedPreferences("model_discovery", Context.MODE_PRIVATE)

    /** Result of a discovery run: the catalog plus any error message. */
    data class Result(val catalog: ModelCatalog, val error: String? = null) {
        val ok: Boolean get() = error == null
    }

    /**
     * Fetches the model list of [baseUrl] with [apiKey] and classifies every model.
     * Runs on a background thread; the callback is invoked on the caller thread.
     */
    fun discover(baseUrl: String, apiKey: String, providerName: String, callback: (Result) -> Unit) {
        Thread {
            val result = runCatching {
                val endpoint = modelsEndpoint(baseUrl)
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("x-api-key", apiKey)
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "JH-GetWay")
                }
                val status = connection.responseCode
                val body = (if (status >= 400) connection.errorStream else connection.inputStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                if (status == 401 || status == 403) error("The API key was rejected (HTTP $status).")
                if (status >= 400) error("Provider returned HTTP $status.")
                if (body.isBlank()) error("The provider returned an empty model list.")
                val catalog = ModelCatalogParser.parse(body, providerName)
                if (catalog.models.isEmpty()) error("The provider returned no models.")
                cache(providerName, catalog)
                Result(catalog)
            }.getOrElse { error ->
                Result(cached(providerName), error.message ?: "Could not reach the provider")
            }
            callback(result)
        }.start()
    }

    /** The last catalog discovered for [providerName], if any. */
    fun cached(providerName: String): ModelCatalog {
        val stored = prefs.getString(key(providerName), null) ?: return ModelCatalog(emptyList(), "none")
        return ModelCatalogParser.fromJson(stored, providerName)
    }

    fun cache(providerName: String, catalog: ModelCatalog) {
        prefs.edit().putString(key(providerName), ModelCatalogParser.toJson(catalog)).apply()
    }

    fun forget(providerName: String) {
        prefs.edit().remove(key(providerName)).apply()
    }

    private fun key(providerName: String) = "catalog_$providerName"

    companion object {
        /** The model listing endpoint of a provider, derived from its base URL. */
        fun modelsEndpoint(baseUrl: String): String {
            val trimmed = baseUrl.trim().trimEnd('/')
            return if (trimmed.endsWith("/v1")) "$trimmed/models" else "$trimmed/v1/models"
        }

        /** Base URL of a known provider preset. */
        fun baseUrlFor(preset: String): String = when (preset) {
            "OpenCode Zen" -> "https://opencode.ai/zen/v1"
            "OpenRouter" -> "https://openrouter.ai/api/v1"
            "Groq" -> "https://api.groq.com/openai/v1"
            "DeepSeek" -> "https://api.deepseek.com"
            "Mistral" -> "https://api.mistral.ai/v1"
            "OpenAI" -> "https://api.openai.com/v1"
            "Anthropic" -> "https://api.anthropic.com/v1"
            "Gemini" -> "https://generativelanguage.googleapis.com/v1beta/openai"
            else -> "https://"
        }

        /** Human-readable summary of a catalog, used in the UI. */
        fun summarize(catalog: ModelCatalog): String = JSONObject().apply {
            put("total", catalog.total)
            put("free", catalog.free.size)
            put("paid", catalog.paid.size)
            put("unknown", catalog.unknown.size)
        }.toString()
    }
}
