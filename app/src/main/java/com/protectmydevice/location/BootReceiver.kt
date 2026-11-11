package com.protectmydevice.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.protectmydevice.security.SimMonitor

/** Restarts protection after reboot; pushes an urgent "last location" when the battery gets low. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                TrackingService.start(context)
                SimMonitor.check(context)
            }
            Intent.ACTION_BATTERY_LOW -> TrackingService.start(context, urgent = true)
        }
    }
}
