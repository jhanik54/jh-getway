package com.jh.gateway

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowKeyGenParameterSpecBuilder::class.java])
class GatewaySecurityTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        TestAndroidKeyStore.install()
        context.getSharedPreferences("gateway_security", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun issuedKeyHasPrefixAndMasksMiddle() {
        val security = GatewaySecurity(context)
        val key = security.gatewayKey()
        assertTrue(key.startsWith("gw-"))
        val masked = security.maskedKey()
        assertTrue(masked.startsWith("gw-"))
        assertTrue(masked.endsWith(key.takeLast(4)))
        assertFalse(masked.contains(key.substring(7, key.length - 4)))
    }

    @Test
    fun validBearerTokenIsAccepted() {
        val security = GatewaySecurity(context)
        assertTrue(security.isAuthorized("Bearer ${security.gatewayKey()}", "10.0.0.1"))
    }

    @Test
    fun wrongOrMissingTokenIsRejected() {
        val security = GatewaySecurity(context)
        assertFalse(security.isAuthorized("Bearer definitely-wrong", "10.0.0.2"))
        assertFalse(security.isAuthorized(null, "10.0.0.3"))
        assertFalse(security.isAuthorized("Bearer ", "10.0.0.4"))
    }

    @Test
    fun allowlistRespectsEntriesAndAlwaysAllowsLocalhost() {
        val security = GatewaySecurity(context)
        assertTrue(security.isIpAllowed("192.168.1.50", ""))
        assertTrue(security.isIpAllowed("1.2.3.4", "1.2.3.4, 5.6.7.8"))
        assertFalse(security.isIpAllowed("9.9.9.9", "1.2.3.4, 5.6.7.8"))
        assertTrue(security.isIpAllowed("127.0.0.1", "1.2.3.4"))
        assertTrue(security.isIpAllowed("0:0:0:0:0:0:0:1", "1.2.3.4"))
    }

    @Test
    fun rateLimitBlocksFloodAfterConfiguredLimit() {
        val security = GatewaySecurity(context)
        val ip = "203.0.113.7"
        assertTrue(security.allowRequest(ip, 3))
        assertTrue(security.allowRequest(ip, 3))
        assertTrue(security.allowRequest(ip, 3))
        assertFalse(security.allowRequest(ip, 3))
    }

    @Test
    fun fiveFailuresFromOneIpBlocksIt() {
        val security = GatewaySecurity(context)
        val ip = "198.51.100.9"
        repeat(5) { security.isAuthorized("Bearer bad-token", ip) }
        // The blocklist short-circuits before the key is even checked.
        assertFalse(security.isAuthorized("Bearer ${security.gatewayKey()}", ip))
    }
}
