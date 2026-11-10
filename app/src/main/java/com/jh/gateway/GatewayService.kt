package com.jh.gateway

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class GatewayService : Service() {
    private var gatewayServer: GatewayServer? = null
    private lateinit var providerStore: ProviderStore
    private lateinit var gatewaySecurity: GatewaySecurity

    override fun onCreate() {
        super.onCreate()
        providerStore = ProviderStore(this)
        gatewaySecurity = GatewaySecurity(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Gateway", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            gatewayServer?.stop()
            gatewayServer = null
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification())
        if (gatewayServer == null) gatewayServer = GatewayServer(providerStore, gatewaySecurity).also { it.start() }
        return START_STICKY
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentTitle("JH GetWay চলছে")
        .setContentText("Local gateway প্রস্তুত হচ্ছে")
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        gatewayServer?.stop()
        gatewayServer = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.jh.gateway.STOP"
        private const val CHANNEL_ID = "gateway_status"
        private const val NOTIFICATION_ID = 1001
    }
}
