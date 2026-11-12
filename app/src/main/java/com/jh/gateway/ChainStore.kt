package com.jh.gateway

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class ChainTier { BEST, GOOD, BASIC }
data class ChainEntry(val id: String, val provider: String, val model: String, val tier: ChainTier, val enabled: Boolean = true)

class ChainStore(context: Context) {
    private val prefs = context.getSharedPreferences("gateway_chain", Context.MODE_PRIVATE)

    @Synchronized
    fun load(providers: List<StoredProvider>): List<ChainEntry> {
        val saved = runCatching {
            val array = JSONArray(prefs.getString(KEY, "[]"))
            (0 until array.length()).map { item ->
                val value = array.getJSONObject(item)
                ChainEntry(value.getString("id"), value.getString("provider"), value.getString("model"), ChainTier.valueOf(value.optString("tier", "BEST")), value.optBoolean("enabled", true))
            }
        }.getOrDefault(emptyList())
        val known = providers.map { it.id }.toSet()
        val filtered = saved.filter { it.id in known && it.enabled }
        val missing = providers.filter { provider -> filtered.none { it.id == provider.id } }.map { provider ->
            if (provider.enabled) ChainEntry(provider.id, provider.name, provider.model, ChainTier.BEST, provider.enabled)
            else ChainEntry(provider.id, provider.name, provider.model, ChainTier.BEST, false)
        }
        val result = (filtered + missing).filter { it.id != "empty" && it.id in known }
        return if (result.isEmpty()) listOf(ChainEntry("empty", "No provider", "Add a provider first", ChainTier.BASIC, false)) else result
    }

    @Synchronized
    fun save(entries: List<ChainEntry>) {
        val array = JSONArray()
        entries.filter { it.id != "empty" }.forEach { entry -> array.put(JSONObject().apply { put("id", entry.id); put("provider", entry.provider); put("model", entry.model); put("tier", entry.tier.name); put("enabled", entry.enabled) }) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    companion object { private const val KEY = "chain" }
}
