package com.jh.gateway

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.net.wifi.WifiManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat

class GatewayService : Service() {
    private var gatewayServer: GatewayServer? = null
    private lateinit var providerStore: ProviderStore
    private lateinit var gatewaySecurity: GatewaySecurity
    private lateinit var gatewayStats: GatewayStats
    private lateinit var gatewaySettings: GatewaySettings
    private lateinit var chainStore: ChainStore
    private lateinit var stateStore: GatewayStateStore
    private lateinit var providerHealth: ProviderHealthStore
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        providerStore = ProviderStore(this)
        gatewaySecurity = GatewaySecurity(this)
        gatewayStats = GatewayStats(this)
        gatewaySettings = GatewaySettings(this)
        chainStore = ChainStore(this)
        stateStore = GatewayStateStore(this)
        providerHealth = ProviderHealthStore(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Gateway", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESTART) {
            // Apply changed settings (port, Wi-Fi mode, concurrency) by rebuilding the server.
            releaseLocks()
            gatewayServer?.stop()
            gatewayServer = null
            stateStore.state = GatewayRunState.STARTING
            startForeground(NOTIFICATION_ID, notification("Restarting gateway…"))
            try {
                gatewayServer = GatewayServer(providerStore, gatewaySecurity, gatewayStats, gatewaySettings, chainStore, providerHealth)
                gatewayServer?.start()
                acquireLocks()
                stateStore.state = GatewayRunState.RUNNING
                stateStore.error = ""
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification("Gateway running • ${gatewaySettings.port}"))
            } catch (error: Throwable) {
                releaseLocks()
                gatewayServer = null
                stateStore.state = GatewayRunState.ERROR
                stateStore.error = error.message ?: "Could not restart gateway"
                stopSelf()
            }
            return START_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            releaseLocks()
            gatewayServer?.stop()
            gatewayServer = null
            stateStore.state = GatewayRunState.STOPPED
            stateStore.error = ""
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification("Starting gateway…"))
        stateStore.state = GatewayRunState.STARTING
        try {
            // A sticky restart delivers a null intent: the server must still be (re)created.
            if (gatewayServer == null) {
                gatewayServer = GatewayServer(providerStore, gatewaySecurity, gatewayStats, gatewaySettings, chainStore, providerHealth)
                gatewayServer?.start()
            }
            acquireLocks()
            stateStore.state = GatewayRunState.RUNNING
            stateStore.error = ""
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification("Gateway running • ${gatewaySettings.port}"))
        } catch (error: Throwable) {
            releaseLocks()
            gatewayServer = null
            stateStore.state = GatewayRunState.ERROR
            stateStore.error = error.message ?: "Could not start gateway"
            stopSelf()
        }
        return START_STICKY
    }

    private fun notification(status: String): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentTitle("JH GetWay চলছে")
        .setContentText(status)
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .addAction(NotificationCompat.Action(0, "Stop", PendingIntent.getService(this, 2, Intent(this, GatewayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)))
        .build()

    private fun acquireLocks() {
        if (wakeLock == null) wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JHGetWay:Gateway").apply { setReferenceCounted(false); acquire() }
        if (wifiLock == null) wifiLock = (getSystemService(WIFI_SERVICE) as WifiManager).createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "JHGetWay:Wifi").apply { setReferenceCounted(false); acquire() }
    }

    private fun releaseLocks() { wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null; wifiLock?.let { if (it.isHeld) it.release() }; wifiLock = null }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseLocks()
        gatewayServer?.stop()
        gatewayServer = null
        if (::stateStore.isInitialized && stateStore.state != GatewayRunState.ERROR) stateStore.state = GatewayRunState.STOPPED
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.jh.gateway.STOP"
        const val ACTION_RESTART = "com.jh.gateway.RESTART"
        private const val CHANNEL_ID = "gateway_status"
        private const val NOTIFICATION_ID = 1001
    }
}
