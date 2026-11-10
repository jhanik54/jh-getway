package com.jh.gateway

import android.content.Context

enum class GatewayRunState { STOPPED, STARTING, RUNNING, ERROR }

class GatewayStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("gateway_runtime", Context.MODE_PRIVATE)
    var state: GatewayRunState
        get() = runCatching { GatewayRunState.valueOf(prefs.getString("state", GatewayRunState.STOPPED.name)!!) }.getOrDefault(GatewayRunState.STOPPED)
        set(value) { prefs.edit().putString("state", value.name).apply() }
    var error: String
        get() = prefs.getString("error", "") ?: ""
        set(value) { prefs.edit().putString("error", value).apply() }
}
