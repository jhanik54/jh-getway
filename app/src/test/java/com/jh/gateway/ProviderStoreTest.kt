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
class ProviderStoreTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    private fun clearPrefs() {
        context.getSharedPreferences("gateway_providers", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("provider_health", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("gateway_stats", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Before
    fun setUp() = clearPrefs()

    @Test
    fun addRejectsNonHttpSchemes() {
        val store = ProviderStore(context)
        for (url in listOf("ftp://files.example.com", "file:///etc/passwd", "javascript:alert(1)", "ssh://host")) {
            assertThrows(IllegalArgumentException::class.java) { store.add("Bad", url, "auto", "key-x") }
        }
    }

    @Test
    fun addRejectsHostlessOrBlankUrls() {
        val store = ProviderStore(context)
        for (url in listOf("", "   ", "https://", "http://", "notaurl", "example.com")) {
            assertThrows(IllegalArgumentException::class.java) { store.add("Bad", url, "auto", "key-x") }
        }
    }

    @Test
    fun addRejectsBlankNameBlankKeyAndBlankModelFallsBack() {
        val store = ProviderStore(context)
        assertThrows(IllegalArgumentException::class.java) { store.add("  ", "https://api.example.com", "auto", "key-x") }
        assertThrows(IllegalArgumentException::class.java) { store.add("Name", "https://api.example.com", "auto", "   ") }
        store.add("Name", "https://api.example.com", "", "key-x")
        assertEquals("auto", store.list().single().model)
    }

    @Test
    fun addTrimsTrailingSlashFromBaseUrl() {
        val store = ProviderStore(context)
        store.add("OpenAI", "https://api.example.com/", "auto", "key-trailing")
        assertEquals("https://api.example.com", store.list().single().baseUrl)
    }

    @Test
    fun apiKeyRoundTripsThroughEncryption() {
        val store = ProviderStore(context)
        store.add("OpenAI", "https://api.example.com", "auto", "super-secret-key-123")
        val provider = store.list().single()
        assertEquals("super-secret-key-123", store.apiKey(provider))
    }

    @Test
    fun duplicateApiKeyIsRejected() {
        val store = ProviderStore(context)
        store.add("A", "https://a.example.com", "auto", "same-key")
        assertThrows(IllegalArgumentException::class.java) { store.add("B", "https://b.example.com", "auto", "same-key") }
        assertEquals(1, store.list().size)
    }

    @Test
    fun updateValidatesUrlAndKeepsListConsistent() {
        val store = ProviderStore(context)
        store.add("A", "https://a.example.com", "auto", "key-a")
        val id = store.list().single().id
        assertThrows(IllegalArgumentException::class.java) { store.update(id, "A", "ftp://nope", "auto", "key-a", ProviderProtocol.OPENAI) }
        store.update(id, "Renamed", "https://a.example.com/v1", "new-model", "key-a", ProviderProtocol.OPENAI)
        val updated = store.list().single()
        assertEquals("Renamed", updated.name)
        assertEquals("new-model", updated.model)
        assertEquals("https://a.example.com/v1", updated.baseUrl)
    }

    @Test
    fun removeDeletesProviderFromRegistry() {
        val store = ProviderStore(context)
        store.add("A", "https://a.example.com", "auto", "key-remove")
        val id = store.list().single().id
        store.remove(id)
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun exportImportRoundTripsProviderAndKey() {
        val store = ProviderStore(context)
        store.add("BackedUp", "https://backup.example.com", "auto", "key-roundtrip")
        val payload = store.exportPayload()
        val id = store.list().single().id
        store.remove(id)
        assertTrue(store.list().isEmpty())
        store.importPayload(payload)
        val restored = store.list().single()
        assertEquals("BackedUp", restored.name)
        assertEquals("key-roundtrip", store.apiKey(restored))
    }

    @Test
    fun importSkipsDuplicateApiKeys() {
        val store = ProviderStore(context)
        store.add("Mine", "https://a.example.com", "auto", "key-dup")
        store.importPayload("""[{"name":"Duplicate","baseUrl":"https://b.example.com","model":"auto","priority":2,"protocol":"OPENAI","aliases":"","apiKey":"key-dup"}]""")
        assertEquals(1, store.list().size)
    }
}
