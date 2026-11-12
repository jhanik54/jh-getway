package com.jh.gateway

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GatewayStatsTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("gateway_stats", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
    }

    @Test
    fun recordAddsRequestLogsNewestFirstAndCounts() {
        val stats = GatewayStats(context)
        stats.record("/v1/chat/completions", 200, "provider-a", 120, "model-a")
        stats.record("/v1/messages", 500, null, 800)
        assertEquals(2, stats.requestCount())
        val recent = stats.recent()
        assertEquals(2, recent.size)
        assertEquals("model-a", recent[0].model)
        assertEquals("provider-a", recent[0].provider)
        assertEquals("/v1/messages", recent[1].endpoint)
        assertNull(recent[1].provider)
    }

    @Test
    fun usageLogAccumulatesTokensAndKeepsEntries() {
        val stats = GatewayStats(context)
        stats.recordUsage("""{"usage":{"prompt_tokens":10,"completion_tokens":25}}""", "provider-a", "model-x")
        stats.recordUsage("""{"usage":{"input_tokens":3,"output_tokens":7}}""", "provider-b", "")
        assertEquals(13, stats.promptTokens())
        assertEquals(32, stats.completionTokens())
        val entries = stats.usageEntries()
        assertEquals(2, entries.size)
        assertEquals("model-x", entries[0].model)
        assertEquals("provider-b", entries[1].provider)
        assertEquals(3, entries[1].promptTokens)
        assertEquals(7, entries[1].completionTokens)
    }

    @Test
    fun usageLogResolvesModelFromBodyWhenNotPassed() {
        val stats = GatewayStats(context)
        stats.recordUsage("""{"model":"body-model","usage":{"prompt_tokens":1,"completion_tokens":1}}""", "provider-a", "")
        assertEquals("body-model", stats.usageEntries().single().model)
    }

    @Test
    fun recordUsageWithoutUsageBlockIsNoOp() {
        val stats = GatewayStats(context)
        stats.recordUsage("""{"model":"m"}""", "provider-a", "m")
        assertEquals(0, stats.promptTokens())
        assertTrue(stats.usageEntries().isEmpty())
    }

    @Test
    fun usageLogCapsAt200Entries() {
        val stats = GatewayStats(context)
        repeat(205) { stats.recordUsage("""{"usage":{"prompt_tokens":1,"completion_tokens":1}}""", "p", "m") }
        assertEquals(200, stats.usageEntries().size)
    }

    @Test
    fun clearResetsLogsCountersAndUsage() {
        val stats = GatewayStats(context)
        stats.record("/v1/chat/completions", 200, "p", 1, "m")
        stats.recordUsage("""{"usage":{"prompt_tokens":5,"completion_tokens":5}}""", "p", "m")
        stats.clear()
        assertEquals(0, stats.requestCount())
        assertEquals(0, stats.promptTokens())
        assertEquals(0, stats.completionTokens())
        assertTrue(stats.recent().isEmpty())
        assertTrue(stats.usageEntries().isEmpty())
    }
}
