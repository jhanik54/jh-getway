package com.jh.gateway

import android.security.keystore.KeyGenParameterSpec
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Constructor
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
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation

/**
 * Robolectric shadow for KeyGenParameterSpec.Builder.
 * Robolectric does not ship android.security.keystore, so the app's
 * KeyGenParameterSpec.Builder(...) chain cannot run against android-all
 * stubs (every method throws "not mocked"). This shadow lets the software
 * KeyGeneratorSpi read the keystoreAlias from the spec so providerStore and
 * gatewaySecurity can each persist their own master key alias.
 */
@Implements(android.security.keystore.KeyGenParameterSpec.Builder::class)
class ShadowKeyGenParameterSpecBuilder {
    private var alias: String = ""

    @Implementation
    fun __constructor__(keystoreAlias: String, purposes: Int) {
        alias = keystoreAlias
    }

    @Implementation
    fun setBlockModes(vararg blockModes: String): android.security.keystore.KeyGenParameterSpec.Builder {
        @Suppress("UNCHECKED_CAST")
        return this as android.security.keystore.KeyGenParameterSpec.Builder
    }

    @Implementation
    fun setEncryptionPaddings(vararg paddings: String): android.security.keystore.KeyGenParameterSpec.Builder {
        @Suppress("UNCHECKED_CAST")
        return this as android.security.keystore.KeyGenParameterSpec.Builder
    }

    @Implementation
    fun build(): android.security.keystore.KeyGenParameterSpec {
        try {
            val ctor = android.security.keystore.KeyGenParameterSpec::class.java
                .getDeclaredConstructor(String::class.java, Int::class.java)
            ctor.isAccessible = true
            return ctor.newInstance(alias, 1)
        } catch (e: Exception) {
            throw RuntimeException("Failed to instantiate KeyGenParameterSpec via reflection", e)
        }
    }
}

/**
 * Robolectric shadow for KeyGenParameterSpec, exposing getKeystoreAlias()
 * so that SoftwareKeyGeneratorSpi.engineGenerateKey can read the alias.
 */
@Implements(android.security.keystore.KeyGenParameterSpec::class)
class ShadowKeyGenParameterSpec {
    private var alias: String = ""

    @Implementation
    fun __constructor__(keyAlias: String, purposes: Int) {
        alias = keyAlias
    }

    @Implementation
    fun getKeystoreAlias(): String = alias
}

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
        Security.insertProviderAt(object : Provider(PROVIDER_NAME, 1.0, "In-memory stand-in for tests") {
            init {
                put("KeyStore.AndroidKeyStore", SoftwareKeyStoreSpi::class.java.name)
                put("KEYSTORE.ANDROIDKEYSTORE", SoftwareKeyStoreSpi::class.java.name)
                put("KeyGenerator.AES", SoftwareKeyGeneratorSpi::class.java.name)
                put("KEYGENERATOR.AES", SoftwareKeyGeneratorSpi::class.java.name)
            }
        }, 1)
        // Self-check: surface exactly why the JCA lookup fails under Robolectric instead of a bare NoSuchAlgorithmException.
        runCatching { KeyStore.getInstance(PROVIDER_NAME) }.onFailure { error ->
            val provider = Security.getProvider(PROVIDER_NAME)
            val services = provider?.services?.joinToString(", ") { "${it.type}/${it.algorithm}" } ?: "provider missing"
            val service = provider?.getService("KeyStore", PROVIDER_NAME)
            throw IllegalStateException("TestAndroidKeyStore self-check failed (${error.javaClass.simpleName}: ${error.message}). Services of own provider: [$services]; getService(KeyStore, AndroidKeyStore)=$service", error)
        }
    }

    class SoftwareKeyStoreSpi : KeyStoreSpi() {
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

    class SoftwareKeyGeneratorSpi : KeyGeneratorSpi() {
        private var spec: AlgorithmParameterSpec? = null

        override fun engineInit(random: SecureRandom?) = Unit
        override fun engineInit(keySize: Int, random: SecureRandom?) = Unit
        override fun engineInit(params: AlgorithmParameterSpec?, random: SecureRandom?) {
            spec = params
        }

        override fun engineGenerateKey(): SecretKey {
            val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            val alias = (spec as? KeyGenParameterSpec)?.getKeystoreAlias() ?: "jh_gateway_master_key"
            entries[alias] = KeyStore.SecretKeyEntry(key)
            return key
        }
    }
}