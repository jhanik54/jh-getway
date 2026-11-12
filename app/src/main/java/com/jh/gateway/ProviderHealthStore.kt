package com.jh.gateway

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import org.json.JSONObject

data class ProviderHealth(val providerId: String, val provider: String, val status: String, val lastError: String, val lastLatencyMs: Long, val updatedAt: Long)

class ProviderHealthStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("provider_health", Context.MODE_PRIVATE)

    @Synchronized
    fun recordSuccess(provider: StoredProvider, latencyMs: Long) {
        save(provider, "healthy", "", latencyMs)
    }

    @Synchronized
    fun recordFailure(provider: StoredProvider, failure: ProviderFailure, status: Int) {
        val message = "${failure.name.lowercase().replace('_', ' ')} (HTTP $status)"
        val preservedLatency = get(provider.id)?.lastLatencyMs ?: 0L
        save(provider, "unhealthy", message, preservedLatency)
        notifyOnce(provider.id, provider.name, message)
    }

    fun get(providerId: String): ProviderHealth? = runCatching { JSONObject(prefs.getString("health_$providerId", null) ?: return null).let { ProviderHealth(providerId, it.optString("provider"), it.optString("status"), it.optString("error"), it.optLong("latency"), it.optLong("updatedAt")) } }.getOrNull()

    private fun save(provider: StoredProvider, status: String, error: String, latency: Long) {
        prefs.edit().putString("health_${provider.id}", JSONObject().apply { put("provider", provider.name); put("status", status); put("error", error); put("latency", latency); put("updatedAt", System.currentTimeMillis()) }.toString()).apply()
    }

    private fun notifyOnce(providerId: String, providerName: String, message: String) {
        val now = System.currentTimeMillis()
        // Throttle per provider, not globally: a failure on one provider must not
        // silence the warning for a different provider.
        if (now - prefs.getLong("last_notification_$providerId", 0L) < 5 * 60_000L) return
        prefs.edit().putLong("last_notification_$providerId", now).apply()
        // Best-effort: posting a notification must never break recording health.
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Provider health", NotificationManager.IMPORTANCE_DEFAULT))
            manager.notify(NOTIFICATION_ID, NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_error).setContentTitle("JH GetWay provider warning").setContentText("$providerName: $message").setAutoCancel(true).build())
        }
    }

    companion object { private const val CHANNEL = "provider_health"; private const val NOTIFICATION_ID = 2002 }
}
