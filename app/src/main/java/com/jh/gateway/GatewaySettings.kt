package com.jh.gateway

import android.content.Context

class GatewaySettings(context: Context) {
    private val prefs = context.getSharedPreferences("gateway_settings", Context.MODE_PRIVATE)
    var port: Int get() = prefs.getInt("port", 8787); set(value) { prefs.edit().putInt("port", value.coerceIn(1024, 65535)).apply() }
    var wifiMode: Boolean get() = prefs.getBoolean("wifi_mode", false); set(value) { prefs.edit().putBoolean("wifi_mode", value).apply() }
    var startOnBoot: Boolean get() = prefs.getBoolean("start_on_boot", false); set(value) { prefs.edit().putBoolean("start_on_boot", value).apply() }
    var appLock: Boolean get() = prefs.getBoolean("app_lock", false); set(value) { prefs.edit().putBoolean("app_lock", value).apply() }
    var screenshotBlock: Boolean get() = prefs.getBoolean("screenshot_block", false); set(value) { prefs.edit().putBoolean("screenshot_block", value).apply() }
    var lastUnlock: Long get() = prefs.getLong("last_unlock", 0L); set(value) { prefs.edit().putLong("last_unlock", value).apply() }
    var theme: String get() = prefs.getString("theme", "LIGHT") ?: "LIGHT"; set(value) { prefs.edit().putString("theme", value).apply() }
    var connectTimeoutMs: Int get() = prefs.getInt("connect_timeout_ms", 15_000); set(value) { prefs.edit().putInt("connect_timeout_ms", value.coerceIn(3_000, 60_000)).apply() }
    var readTimeoutMs: Int get() = prefs.getInt("read_timeout_ms", 120_000); set(value) { prefs.edit().putInt("read_timeout_ms", value.coerceIn(10_000, 600_000)).apply() }
}
