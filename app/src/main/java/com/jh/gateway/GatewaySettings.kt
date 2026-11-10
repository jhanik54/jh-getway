package com.jh.gateway

import android.content.Context

class GatewaySettings(context: Context) {
    private val prefs = context.getSharedPreferences("gateway_settings", Context.MODE_PRIVATE)
    var port: Int get() = prefs.getInt("port", 8787); set(value) { prefs.edit().putInt("port", value.coerceIn(1024, 65535)).apply() }
    var wifiMode: Boolean get() = prefs.getBoolean("wifi_mode", false); set(value) { prefs.edit().putBoolean("wifi_mode", value).apply() }
    var startOnBoot: Boolean get() = prefs.getBoolean("start_on_boot", false); set(value) { prefs.edit().putBoolean("start_on_boot", value).apply() }
}
