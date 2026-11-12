package com.jh.gateway

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        context.getSharedPreferences("gateway_providers", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun storeWithProvider(key: String = "backup-test-key"): ProviderStore {
        val store = ProviderStore(context)
        store.add("BackedUp", "https://backup.example.com", "auto", key)
        return store
    }

    @Test
    fun exportRequiresAtLeastTenCharacterPassword() {
        assertThrows(IllegalArgumentException::class.java) { BackupManager().export(storeWithProvider(), charArrayOf('a', 'b', 'c')) }
    }

    @Test
    fun roundTripRestoresProvidersAndKeys() {
        val store = storeWithProvider("roundtrip-secret-key")
        val payload = BackupManager().export(store, "password-123".toCharArray())
        val id = store.list().single().id
        store.remove(id)
        assertTrue(store.list().isEmpty())
        BackupManager().import(store, payload, "password-123".toCharArray())
        val restored = store.list().single()
        assertEquals("BackedUp", restored.name)
        assertEquals("roundtrip-secret-key", store.apiKey(restored))
    }

    @Test
    fun wrongPasswordFailsDecryption() {
        val store = storeWithProvider()
        val payload = BackupManager().export(store, "right-password".toCharArray())
        assertThrows(Exception::class.java) { BackupManager().import(store, payload, "wrong-password".toCharArray()) }
    }

    @Test
    fun corruptMagicHeaderIsRejected() {
        val store = storeWithProvider()
        val payload = "XXXX1".toByteArray() + ByteArray(64)
        assertThrows(IllegalArgumentException::class.java) { BackupManager().import(store, payload, "password-123".toCharArray()) }
    }

    @Test
    fun exportedFormatStartsWithMagicHeader() {
        val payload = BackupManager().export(storeWithProvider(), "password-123".toCharArray())
        assertEquals("JHGW1", String(payload.copyOfRange(0, 5)))
    }
}
