package com.jh.gateway

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class RequestLog(val time: Long, val endpoint: String, val status: Int, val provider: String?, val latencyMs: Long)

class GatewayStats(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gateway_stats", Context.MODE_PRIVATE)

    @Synchronized
    fun record(endpoint: String, status: Int, provider: String?, latencyMs: Long) {
        val current = JSONArray(prefs.getString(LOGS, "[]"))
        val next = JSONArray().apply {
            put(JSONObject().apply { put("time", System.currentTimeMillis()); put("endpoint", endpoint); put("status", status); put("provider", provider ?: ""); put("latencyMs", latencyMs) })
            for (index in 0 until minOf(current.length(), 99)) put(current.getJSONObject(index))
        }
        prefs.edit().putString(LOGS, next.toString()).putInt(REQUESTS, prefs.getInt(REQUESTS, 0) + 1).apply()
    }

    @Synchronized
    fun recent(): List<RequestLog> = runCatching {
        val logs = JSONArray(prefs.getString(LOGS, "[]"))
        (0 until logs.length()).map { val item = logs.getJSONObject(it); RequestLog(item.getLong("time"), item.getString("endpoint"), item.getInt("status"), item.optString("provider").ifBlank { null }, item.getLong("latencyMs")) }
    }.getOrDefault(emptyList())

    fun requestCount(): Int = prefs.getInt(REQUESTS, 0)

    companion object { private const val LOGS = "logs"; private const val REQUESTS = "requests" }
}
