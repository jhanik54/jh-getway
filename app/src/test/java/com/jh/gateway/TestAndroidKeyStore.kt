package com.jh.gateway

import android.security.keystore.KeyGenParameterSpec
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Collections
import java.util.Date
import java.util.Enumeration
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGenerator
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey

/**
 * Software-backed stand-in for the AndroidKeyStore provider, used only by JVM (Robolectric) tests.
 * Robolectric does not ship the real AndroidKeyStore, so the app's AES/GCM encrypt/decrypt path
 * cannot run without this. Keys live in an in-memory map; nothing is hardware-backed or persisted.
 * Never used by the application itself.
 */
object TestAndroidKeyStore {
    private const val PROVIDER_NAME = "AndroidKeyStore"
    private val entries = ConcurrentHashMap<String, KeyStore.SecretKeyEntry>()

    fun install() {
        Security.removeProvider(PROVIDER_NAME)
        Security.addProvider(object : Provider(PROVIDER_NAME, "1.0", "In-memory stand-in for tests") {
            init {
                put("KeyStore.AndroidKeyStore", SoftwareKeyStoreSpi::class.java.name)
                put("KeyGenerator.AES", SoftwareKeyGeneratorSpi::class.java.name)
            }
        })
    }

    private class SoftwareKeyStoreSpi : KeyStoreSpi() {
        override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit
        override fun engineStore(stream: OutputStream?, password: CharArray?) = Unit
        override fun engineGetKey(alias: String, password: CharArray?): Key? = entries[alias]?.secretKey
        override fun engineGetCertificateChain(alias: String): Array<Certificate>? = null
        override fun engineGetCertificate(alias: String): Certificate? = null
        override fun engineGetCertificateAlias(certificate: Certificate): String? = null
        override fun engineGetCreationDate(alias: String): Date? = if (entries.containsKey(alias)) Date() else null
        override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<Certificate>) {
            entries[alias] = KeyStore.SecretKeyEntry(key as SecretKey)
        }
        override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<Certificate>?) {
            throw UnsupportedOperationException("Software test keystore only accepts SecretKey entries")
        }
        override fun engineSetCertificateEntry(alias: String, cert: Certificate) {
            throw UnsupportedOperationException("Software test keystore does not support certificates")
        }
        override fun engineDeleteEntry(alias: String) { entries.remove(alias) }
        override fun engineAliases(): Enumeration<String> = Collections.enumeration(entries.keys)
        override fun engineContainsAlias(alias: String): Boolean = entries.containsKey(alias)
        override fun engineSize(): Int = entries.size
        override fun engineIsKeyEntry(alias: String): Boolean = entries.containsKey(alias)
        override fun engineIsCertificateEntry(alias: String): Boolean = false
        override fun engineGetEntry(alias: String, protParam: KeyStore.ProtectionParameter?): KeyStore.Entry? = entries[alias]
        override fun engineSetEntry(alias: String, entry: KeyStore.Entry, protParam: KeyStore.ProtectionParameter?) {
            entries[alias] = entry as KeyStore.SecretKeyEntry
        }
    }

    private class SoftwareKeyGeneratorSpi : KeyGeneratorSpi() {
        private var spec: AlgorithmParameterSpec? = null

        override fun engineInit(random: SecureRandom?) = Unit
        override fun engineInit(keySize: Int, random: SecureRandom?) = Unit
        override fun engineInit(params: AlgorithmParameterSpec?, random: SecureRandom?) {
            spec = params
        }

        override fun engineGenerateKey(): SecretKey {
            val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            val alias = (spec as? KeyGenParameterSpec)?.keystoreAlias ?: "jh_gateway_master_key"
            entries[alias] = KeyStore.SecretKeyEntry(key)
            return key
        }
    }
}
