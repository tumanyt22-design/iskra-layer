package com.iskra.layer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.provider.Settings

class LayerService : Service() {
    companion object {
        const val ACTION_STOP = "com.iskra.layer.STOP"
        private const val CHANNEL = "layer"
    }
    private var overlay: LayerOverlay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            startAsForeground()
            if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
            val o = LayerOverlay(this)
            o.show()
            overlay = o
        } catch (e: Throwable) { e.printStackTrace(); stopSelf() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            LayerPrefs.setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        overlay?.hide()
        overlay = null
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Слой", NotificationManager.IMPORTANCE_LOW))
        val stopIntent = Intent(this, LayerService::class.java)
        stopIntent.action = ACTION_STOP
        val stopPi = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val action = Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_delete), "Остановить", stopPi).build()
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("Слой работает").setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true).addAction(action).build()

        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }
}
