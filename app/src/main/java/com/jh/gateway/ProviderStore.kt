package com.jh.gateway

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.UUID
import java.net.URI

enum class ProviderProtocol { OPENAI, ANTHROPIC }

data class StoredProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val priority: Int,
    val protocol: ProviderProtocol = ProviderProtocol.OPENAI,
    val enabled: Boolean = true,
    val cooldownUntil: Long = 0L,
    val aliases: List<String> = emptyList()
) {
    val isCoolingDown: Boolean get() = cooldownUntil > System.currentTimeMillis()
}

/** Stores provider metadata plainly, but encrypts every API key with Android Keystore. */
class ProviderStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = context.getSharedPreferences("gateway_providers", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val keyCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    init { ensureMasterKey() }

    @Synchronized
    fun list(): List<StoredProvider> = runCatching {
        val array = JSONArray(prefs.getString(PROVIDERS, "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            StoredProvider(item.getString("id"), item.getString("name"), item.getString("baseUrl"), item.getString("model"), item.getInt("priority"), runCatching { ProviderProtocol.valueOf(item.optString("protocol", "OPENAI")) }.getOrDefault(ProviderProtocol.OPENAI), item.optBoolean("enabled", true), item.optLong("cooldownUntil", 0L), (0 until item.optJSONArray("aliases")?.length().orZero()).mapNotNull { item.optJSONArray("aliases")?.optString(it)?.takeIf(String::isNotBlank) })
        }.sortedBy { it.priority }
    }.getOrDefault(emptyList())

    @Synchronized
    fun add(name: String, baseUrl: String, model: String, apiKey: String, protocol: ProviderProtocol = ProviderProtocol.OPENAI, aliases: List<String> = emptyList()) {
        require(name.trim().isNotBlank()) { "Provider name is required" }
        val normalizedUrl = baseUrl.trim().trimEnd('/')
        val parsedUrl = runCatching { URI(normalizedUrl) }.getOrNull()
        require(parsedUrl != null && parsedUrl.scheme in setOf("http", "https") && !parsedUrl.host.isNullOrBlank()) { "Base URL must be a valid http(s) URL" }
        require(apiKey.trim().isNotBlank()) { "API key is required" }
        val normalizedKey = apiKey.trim()
        require(list().none { apiKey(it) == normalizedKey }) { "This API key is already configured" }
        val id = "provider_${UUID.randomUUID()}"
        val provider = StoredProvider(id, name.trim(), normalizedUrl, model.trim().ifBlank { "auto" }, list().size + 1, protocol, aliases = aliases.map(String::trim).filter(String::isNotBlank).distinct())
        save(list() + provider)
        prefs.edit().putString(keyName(id), encrypt(normalizedKey)).apply()
        keyCache[id] = normalizedKey
    }

    /**
     * Decrypted API key for [provider], or null when it is missing or unreadable.
     *
     * The plaintext is cached in memory: decryption touches the AndroidKeyStore on
     * every call, which is far too slow for the request path. The cache is dropped
     * whenever the key for that provider changes or the provider is removed.
     */
    fun apiKey(provider: StoredProvider): String? {
        val cached = keyCache[provider.id]
        if (cached != null) return cached
        val stored = prefs.getString(keyName(provider.id), null) ?: return null
        val decrypted = decrypt(stored) ?: return null
        keyCache[provider.id] = decrypted
        return decrypted
    }

    /**
     * True when a stored API key can no longer be decrypted (the AndroidKeyStore master key
     * was invalidated, e.g. after a screen-lock change). The provider is still listed, but its
     * key must be entered again.
     */
    @Synchronized
    fun hasUnreadableKeys(): Boolean = list().any { provider ->
        val stored = prefs.getString(keyName(provider.id), null) ?: return@any false
        decrypt(stored).isNullOrBlank()
    }

    @Synchronized
    fun markCooldown(provider: StoredProvider, millis: Long) {
        save(list().map { if (it.id == provider.id) it.copy(cooldownUntil = System.currentTimeMillis() + millis) else it })
    }

    @Synchronized
    fun disable(provider: StoredProvider) {
        save(list().map { if (it.id == provider.id) it.copy(enabled = false) else it })
    }

    @Synchronized
    fun setEnabled(id: String, enabled: Boolean) { save(list().map { if (it.id == id) it.copy(enabled = enabled) else it }) }

    @Synchronized
    fun get(id: String): StoredProvider? = list().firstOrNull { it.id == id }

    @Synchronized
    fun update(id: String, name: String, baseUrl: String, model: String, apiKey: String, protocol: ProviderProtocol, aliases: List<String> = emptyList()) {
        require(name.trim().isNotBlank()) { "Provider name is required" }
        val normalizedUrl = baseUrl.trim().trimEnd('/')
        val parsedUrl = runCatching { URI(normalizedUrl) }.getOrNull()
        require(parsedUrl != null && parsedUrl.scheme in setOf("http", "https") && !parsedUrl.host.isNullOrBlank()) { "Base URL must be a valid http(s) URL" }
        val normalizedKey = apiKey.trim()
        require(normalizedKey.isNotBlank()) { "API key is required" }
        require(list().filterNot { it.id == id }.none { apiKey(it) == normalizedKey }) { "This API key is already configured" }
        val current = list().firstOrNull { it.id == id } ?: error("Provider not found")
        save(list().map { if (it.id == id) current.copy(name = name.trim(), baseUrl = normalizedUrl, model = model.trim().ifBlank { "auto" }, protocol = protocol, aliases = aliases.map(String::trim).filter(String::isNotBlank).distinct()) else it })
        prefs.edit().putString(keyName(id), encrypt(normalizedKey)).apply()
        keyCache[id] = normalizedKey
    }

    @Synchronized
    fun remove(id: String) {
        save(list().filterNot { it.id == id })
        prefs.edit().remove(keyName(id)).apply()
        keyCache.remove(id)
        try {
            appContext.getSharedPreferences("provider_health", Context.MODE_PRIVATE).edit().remove("health_$id").apply()
            appContext.getSharedPreferences("gateway_stats", Context.MODE_PRIVATE).edit().remove("health_$id").apply()
        } catch (_: Throwable) {
            // Best effort cleanup; provider is already removed from the registry.
        }
    }

    @Synchronized
    fun exportPayload(): String {
        val array = JSONArray()
        list().forEach { provider -> array.put(JSONObject().apply { put("name", provider.name); put("baseUrl", provider.baseUrl); put("model", provider.model); put("priority", provider.priority); put("protocol", provider.protocol.name); put("aliases", provider.aliases.joinToString(",")); put("apiKey", apiKey(provider).orEmpty()) }) }
        return array.toString()
    }

    @Synchronized
    fun importPayload(payload: String, replace: Boolean = false) {
        val incoming = JSONArray(payload)
        if (replace) {
            // Remove the old metadata AND every encrypted API key, otherwise the keys of
            // replaced providers linger in preferences (and are still exportable).
            val staleKeys = prefs.all.keys.filter { it.startsWith("key_") }
            prefs.edit().remove(PROVIDERS).apply()
            staleKeys.forEach { prefs.edit().remove(it).apply() }
            keyCache.clear()
        }
        for (index in 0 until incoming.length()) {
            val item = incoming.getJSONObject(index)
            val key = item.optString("apiKey")
            if (key.isNotBlank() && list().none { apiKey(it) == key }) add(item.optString("name", "Imported provider"), item.getString("baseUrl"), item.optString("model", "auto"), key, runCatching { ProviderProtocol.valueOf(item.optString("protocol", "OPENAI")) }.getOrDefault(ProviderProtocol.OPENAI), item.optString("aliases").split(',').filter(String::isNotBlank))
        }
    }

    private fun save(providers: List<StoredProvider>) {
        val array = JSONArray()
        providers.forEach { p -> array.put(JSONObject().apply { put("id", p.id); put("name", p.name); put("baseUrl", p.baseUrl); put("model", p.model); put("priority", p.priority); put("protocol", p.protocol.name); put("enabled", p.enabled); put("cooldownUntil", p.cooldownUntil); put("aliases", p.aliases.joinToString(",")) }) }
        prefs.edit().putString(PROVIDERS, array.toString()).apply()
    }

    private fun ensureMasterKey() {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
                generateKey()
            }
        }
    }

    private fun secretKey(): SecretKey = (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    private fun encrypt(value: String): String { val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, secretKey()); return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP) }
    private fun decrypt(value: String): String? = runCatching { val all = Base64.decode(value, Base64.NO_WRAP); val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, all.copyOfRange(0, 12))); String(cipher.doFinal(all.copyOfRange(12, all.size)), StandardCharsets.UTF_8) }.getOrNull()
    private fun keyName(id: String) = "key_$id"

    companion object { private const val PROVIDERS = "providers"; private const val KEY_ALIAS = "jh_gateway_master_key" }
}

private fun Int?.orZero(): Int = this ?: 0
