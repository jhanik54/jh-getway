package com.jh.gateway

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ChainStoreTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("gateway_chain", Context.MODE_PRIVATE)

    private fun provider(id: String, name: String = id, enabled: Boolean = true, priority: Int = 1) =
        StoredProvider(id, name, "https://api.$id.example.com", "auto", priority, enabled = enabled)

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
    }

    @Test
    fun loadWithNoProvidersReturnsDisabledEmptySentinel() {
        val chain = ChainStore(context).load(emptyList())
        assertEquals(1, chain.size)
        assertEquals("empty", chain[0].id)
        assertFalse(chain[0].enabled)
    }

    @Test
    fun loadCreatesEntriesForRegisteredProviders() {
        val chain = ChainStore(context).load(listOf(provider("p1", priority = 1), provider("p2", priority = 2)))
        assertEquals(listOf("p1", "p2"), chain.map { it.id })
        assertTrue(chain.all { it.enabled })
    }

    @Test
    fun disabledChainEntryIsPersistedAsDisabled() {
        val store = ChainStore(context)
        store.save(listOf(ChainEntry("p1", "p1", "auto", ChainTier.BEST, false)))
        val chain = store.load(listOf(provider("p1")))
        assertFalse(chain.first { it.id == "p1" }.enabled)
    }

    @Test
    fun providerDisabledAtRegistryLevelLoadsDisabledChainEntry() {
        val chain = ChainStore(context).load(listOf(provider("p1", enabled = false)))
        assertFalse(chain.first { it.id == "p1" }.enabled)
    }

    @Test
    fun orphanEntriesAreDropped() {
        val store = ChainStore(context)
        store.save(listOf(ChainEntry("ghost", "ghost", "auto", ChainTier.BEST, true)))
        val chain = store.load(listOf(provider("p1")))
        assertEquals(listOf("p1"), chain.map { it.id })
    }

    @Test
    fun savedTierSurvivesRoundTrip() {
        val store = ChainStore(context)
        val providers = listOf(provider("p1", priority = 1), provider("p2", priority = 2))
        store.save(listOf(ChainEntry("p2", "p2", "auto", ChainTier.BASIC, true), ChainEntry("p1", "p1", "auto", ChainTier.GOOD, true)))
        val chain = store.load(providers)
        assertEquals(ChainTier.GOOD, chain.first { it.id == "p1" }.tier)
        assertEquals(ChainTier.BASIC, chain.first { it.id == "p2" }.tier)
    }

    @Test
    fun emptySentinelIsNeverPersisted() {
        val store = ChainStore(context)
        store.save(listOf(ChainEntry("empty", "No provider", "none", ChainTier.BASIC, false), ChainEntry("p1", "p1", "auto", ChainTier.GOOD, true)))
        val chain = store.load(listOf(provider("p1")))
        assertEquals("p1", chain.single().id)
    }
}
