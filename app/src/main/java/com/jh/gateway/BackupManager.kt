package com.jh.gateway

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password protected backup format: JHGW1 + salt + IV + AES-GCM(payload). */
class BackupManager {
    fun export(store: ProviderStore, password: CharArray): ByteArray {
        require(password.size >= 10) { "Backup password must be at least 10 characters" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = derive(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        val encrypted = cipher.doFinal(store.exportPayload().toByteArray(StandardCharsets.UTF_8))
        return ByteBuffer.allocate(5 + salt.size + iv.size + encrypted.size).put("JHGW1".toByteArray()).put(salt).put(iv).put(encrypted).array()
    }

    fun import(store: ProviderStore, backup: ByteArray, password: CharArray, replace: Boolean = false) {
        require(String(backup.copyOfRange(0, 5), StandardCharsets.UTF_8) == "JHGW1") { "Invalid backup file" }
        val salt = backup.copyOfRange(5, 21); val iv = backup.copyOfRange(21, 33); val encrypted = backup.copyOfRange(33, backup.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, derive(password, salt), GCMParameterSpec(128, iv)) }
        store.importPayload(String(cipher.doFinal(encrypted), StandardCharsets.UTF_8), replace)
    }

    private fun derive(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, 120_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }
}
