package app.cosmox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import org.json.JSONObject

class CosmoVpnService : VpnService() {
    companion object {
        const val ACTION_START = "app.cosmox.START"
        const val ACTION_STOP = "app.cosmox.STOP"
        const val PREFS = "cosmox"
        const val KEY_CONFIG = "runtime_config"
        private const val CHANNEL = "cosmox_vpn"
        private const val NOTIFICATION_ID = 42
    }

    @Volatile private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Thread { stopCore(); stopSelf() }.start()
            return START_NOT_STICKY
        }
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification())
        Thread {
            try {
                val config = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_CONFIG, null)
                    ?: error("Сначала импортируй сервер")
                XrayBridge.registerProtector(this)
                tun = Builder()
                    // Keep CosmoX control-plane traffic outside its own VPN to prevent a routing loop.
                    .addDisallowedApplication(packageName)
                    .setSession("CosmoX")
                    .setMtu(1500)
                    .addAddress("10.111.0.1", 30)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("1.1.1.1")
                    // IPv4-only diagnostic mode: avoid blackholing traffic on devices/networks
                    // where the IPv6 route or resolver is not carried correctly through the tunnel.
                    .establish() ?: error("Android не разрешил создать VPN-интерфейс")
                XrayBridge.setDns(this, "1.1.1.1:53")
                val root = JSONObject(config)
                // libXray expects env.xray as a JSON-encoded string, not a nested object.
                val xrayEnv = JSONObject().put("tun", JSONObject().put("fd", tun!!.fd))
                root.put("env", JSONObject().put("xray", xrayEnv.toString()))
                XrayBridge.invoke("runXray", JSONObject().put("xrayJson", root.toString()))
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("connected", true).apply()
                sendBroadcast(Intent("app.cosmox.STATUS").setPackage(packageName).putExtra("connected", true))
            } catch (e: Exception) {
                sendBroadcast(Intent("app.cosmox.STATUS").setPackage(packageName).putExtra("error", e.message ?: "Не удалось запустить VPN"))
                stopCore()
                stopSelf()
            }
        }.start()
        return START_STICKY
    }

    private fun stopCore() {
        runCatching { XrayBridge.invoke("stopXray") }
        XrayBridge.resetDns()
        runCatching { tun?.close() }
        tun = null
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("connected", false).apply()
        sendBroadcast(Intent("app.cosmox.STATUS").setPackage(packageName).putExtra("connected", false))
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
    }

    override fun onDestroy() {
        stopCore()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "CosmoX VPN", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return builder.setContentTitle("CosmoX")
            .setContentText("VPN-служба активна")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setOngoing(true)
            .build()
    }
}
