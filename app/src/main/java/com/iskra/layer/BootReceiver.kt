package com.iskra.layer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!LayerPrefs.isEnabled(context)) return
        if (!Settings.canDrawOverlays(context)) return
        try {
            context.startForegroundService(Intent(context, LayerService::class.java))
        } catch (_: Exception) {}
    }
}
