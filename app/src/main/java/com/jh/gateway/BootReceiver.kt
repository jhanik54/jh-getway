package com.jh.gateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val enabled = context.getSharedPreferences("gateway_settings", Context.MODE_PRIVATE).getBoolean("start_on_boot", false)
        if (enabled) ContextCompat.startForegroundService(context, Intent(context, GatewayService::class.java))
    }
}
