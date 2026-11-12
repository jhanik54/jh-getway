package com.jh.gateway

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GatewaySettingsTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("gateway_settings", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
    }

    @Test
    fun portDefaultsTo8787AndIsClamped() {
        val settings = GatewaySettings(context)
        assertEquals(8787, settings.port)
        settings.port = 80
        assertEquals(1024, settings.port)
        settings.port = 70_000
        assertEquals(65_535, settings.port)
        settings.port = 9000
        assertEquals(9000, settings.port)
    }

    @Test
    fun connectAndReadTimeoutsAreClamped() {
        val settings = GatewaySettings(context)
        settings.connectTimeoutMs = 100
        assertEquals(3_000, settings.connectTimeoutMs)
        settings.connectTimeoutMs = 999_999
        assertEquals(60_000, settings.connectTimeoutMs)
        settings.readTimeoutMs = 1_000
        assertEquals(10_000, settings.readTimeoutMs)
        settings.readTimeoutMs = 9_999_999
        assertEquals(600_000, settings.readTimeoutMs)
    }

    @Test
    fun stickyTtlIsClamped() {
        val settings = GatewaySettings(context)
        settings.stickyTtlSeconds = 1
        assertEquals(60, settings.stickyTtlSeconds)
        settings.stickyTtlSeconds = 999_999
        assertEquals(86_400, settings.stickyTtlSeconds)
    }

    @Test
    fun maxConcurrentIsClamped() {
        val settings = GatewaySettings(context)
        assertEquals(4, settings.maxConcurrent)
        settings.maxConcurrent = 0
        assertEquals(1, settings.maxConcurrent)
        settings.maxConcurrent = 100
        assertEquals(32, settings.maxConcurrent)
    }

    @Test
    fun routingStrategyThemeAndStickyDefaults() {
        val settings = GatewaySettings(context)
        assertEquals("PRIORITY", settings.routingStrategy)
        assertEquals("LIGHT", settings.theme)
        assertTrue(settings.stickySession)
        assertEquals(1_800, settings.stickyTtlSeconds)
    }
}
