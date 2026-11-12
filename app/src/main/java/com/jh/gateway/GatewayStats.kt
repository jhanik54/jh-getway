package com.jh.gateway

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class RequestLog(val time: Long, val endpoint: String, val status: Int, val provider: String?, val latencyMs: Long, val model: String = "")
data class UsageEntry(val time: Long, val provider: String, val model: String, val promptTokens: Int, val completionTokens: Int)

class GatewayStats(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gateway_stats", Context.MODE_PRIVATE)

    @Synchronized
    fun record(endpoint: String, status: Int, provider: String?, latencyMs: Long, model: String = "") {
        val current = JSONArray(prefs.getString(LOGS, "[]"))
        val next = JSONArray().apply {
            put(JSONObject().apply { put("time", System.currentTimeMillis()); put("endpoint", endpoint); put("status", status); put("provider", provider ?: ""); put("latencyMs", latencyMs); put("model", model) })
            for (index in 0 until minOf(current.length(), 99)) put(current.getJSONObject(index))
        }
        prefs.edit().putString(LOGS, next.toString()).putInt(REQUESTS, prefs.getInt(REQUESTS, 0) + 1).apply()
    }

    @Synchronized
    fun recent(): List<RequestLog> = runCatching {
        val logs = JSONArray(prefs.getString(LOGS, "[]"))
        (0 until logs.length()).map { val item = logs.getJSONObject(it); RequestLog(item.getLong("time"), item.getString("endpoint"), item.getInt("status"), item.optString("provider").ifBlank { null }, item.getLong("latencyMs"), item.optString("model")) }
    }.getOrDefault(emptyList())

    fun requestCount(): Int = prefs.getInt(REQUESTS, 0)

    @Synchronized
    fun recordUsage(body: String, provider: String? = null, model: String = "") {
        runCatching {
            val usage = JSONObject(body).optJSONObject("usage") ?: return
            val prompt = usage.optInt("prompt_tokens", usage.optInt("input_tokens", 0))
            val completion = usage.optInt("completion_tokens", usage.optInt("output_tokens", 0))
            val resolvedModel = model.ifBlank { JSONObject(body).optString("model", "") }
            val current = JSONArray(prefs.getString(USAGE_LOG, "[]"))
            val next = JSONArray().apply {
                put(JSONObject().apply { put("time", System.currentTimeMillis()); put("provider", provider ?: ""); put("model", resolvedModel); put("promptTokens", prompt); put("completionTokens", completion) })
                for (index in 0 until minOf(current.length(), 199)) put(current.getJSONObject(index))
            }
            prefs.edit().putInt(PROMPT_TOKENS, prefs.getInt(PROMPT_TOKENS, 0) + prompt).putInt(COMPLETION_TOKENS, prefs.getInt(COMPLETION_TOKENS, 0) + completion).putString(USAGE_LOG, next.toString()).apply()
        }
    }

    fun promptTokens(): Int = prefs.getInt(PROMPT_TOKENS, 0)
    fun completionTokens(): Int = prefs.getInt(COMPLETION_TOKENS, 0)

    @Synchronized
    fun usageEntries(): List<UsageEntry> = runCatching {
        val entries = JSONArray(prefs.getString(USAGE_LOG, "[]"))
        (0 until entries.length()).map {
            val item = entries.getJSONObject(it)
            UsageEntry(item.getLong("time"), item.optString("provider"), item.optString("model"), item.optInt("promptTokens"), item.optInt("completionTokens"))
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun clear() { prefs.edit().remove(LOGS).remove(REQUESTS).remove(PROMPT_TOKENS).remove(COMPLETION_TOKENS).remove(USAGE_LOG).apply() }

    companion object { private const val LOGS = "logs"; private const val REQUESTS = "requests"; private const val PROMPT_TOKENS = "prompt_tokens"; private const val COMPLETION_TOKENS = "completion_tokens"; private const val USAGE_LOG = "usage_log" }
}
