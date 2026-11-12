package com.jh.gateway

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.concurrent.ConcurrentHashMap

/** Gateway credential and lightweight IP abuse protection. API keys never leave this class in logs. */
class GatewaySecurity(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gateway_security", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val requestWindows = ConcurrentHashMap<String, RateWindow>()
    private data class RateWindow(var startedAt: Long, var count: Int)

    init {
        if (!prefs.contains(GATEWAY_KEY)) prefs.edit().putString(GATEWAY_KEY, encrypt(createGatewayKey())).apply()
        // If the stored key can no longer be decrypted (e.g. the AndroidKeyStore key was
        // invalidated by a screen-lock change), regenerate it instead of leaving the
        // gateway permanently unauthenticated with an empty key.
        if (decrypt(prefs.getString(GATEWAY_KEY, null).orEmpty()).isNullOrBlank()) {
            prefs.edit().putString(GATEWAY_KEY, encrypt(createGatewayKey())).apply()
        }
    }

    fun gatewayKey(): String = decrypt(prefs.getString(GATEWAY_KEY, null).orEmpty()) ?: ""

    fun maskedKey(): String {
        val key = gatewayKey()
        return if (key.length > 12) "${key.take(7)}••••••${key.takeLast(4)}" else "gw-••••••••"
    }

    fun isAuthorized(candidate: String?, remoteIp: String): Boolean {
        if (isBlocked(remoteIp)) return false
        val supplied = candidate?.removePrefix("Bearer ")?.trim().orEmpty()
        val valid = supplied.isNotEmpty() && MessageDigest.isEqual(supplied.toByteArray(), gatewayKey().toByteArray())
        if (!valid) recordFailure(remoteIp)
        return valid
    }

    fun isIpAllowed(remoteIp: String, allowlist: String): Boolean {
        val entries = allowlist.split(',').map(String::trim).filter(String::isNotBlank)
        return entries.isEmpty() || remoteIp in entries || remoteIp == "127.0.0.1" || remoteIp == "0:0:0:0:0:0:0:1"
    }

    @Synchronized
    fun allowRequest(remoteIp: String, maxPerMinute: Int = 120): Boolean {
        cleanupBlockedEvents()
        val now = System.currentTimeMillis()
        val current = requestWindows[remoteIp]
        if (current == null || now - current.startedAt >= 60_000L) { requestWindows[remoteIp] = RateWindow(now, 1); return true }
        if (current.count >= maxPerMinute) return false
        current.count++
        return true
    }

    @Synchronized
    fun regenerate(): String {
        val fresh = createGatewayKey()
        prefs.edit().putString(GATEWAY_KEY, encrypt(fresh)).apply()
        return fresh
    }

    /** Removes expired security blocks so the blocked-key map does not grow unbounded. */
    @Synchronized
    fun cleanupBlockedEvents() {
        val now = System.currentTimeMillis()
        val blockedKeys = prefs.all.keys.filter { it.startsWith("blocked_") && (prefs.all[it] as? Long ?: 0L) < now }
        if (blockedKeys.isNotEmpty()) blockedKeys.forEach { prefs.edit().remove(it).apply() }
    }

    private fun isBlocked(ip: String): Boolean = prefs.getLong("blocked_$ip", 0L) > System.currentTimeMillis()

    private fun recordFailure(ip: String) {
        val now = System.currentTimeMillis()
        val windowStart = prefs.getLong("window_$ip", 0L)
        val count = if (now - windowStart < WINDOW_MS) prefs.getInt("failures_$ip", 0) + 1 else 1
        prefs.edit().putLong("window_$ip", if (count == 1) now else windowStart).putInt("failures_$ip", count).apply()
        if (count >= MAX_FAILURES) prefs.edit().putLong("blocked_$ip", now + BLOCK_MS).apply()
    }

    private fun createGatewayKey(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return "gw-" + Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
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
    private fun encrypt(value: String): String { ensureMasterKey(); val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, secretKey()); return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP) }
    private fun decrypt(value: String): String? = runCatching { ensureMasterKey(); val all = Base64.decode(value, Base64.NO_WRAP); val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, all.copyOfRange(0, 12))); String(cipher.doFinal(all.copyOfRange(12, all.size)), StandardCharsets.UTF_8) }.getOrNull()

    companion object { private const val GATEWAY_KEY = "gateway_key"; private const val KEY_ALIAS = "jh_gateway_auth_key"; private const val MAX_FAILURES = 5; private const val WINDOW_MS = 10 * 60 * 1000L; private const val BLOCK_MS = 15 * 60 * 1000L }
}
