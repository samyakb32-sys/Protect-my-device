package com.protectmydevice.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.protectmydevice.data.StolenState
import com.protectmydevice.security.SimMonitor
import com.protectmydevice.ui.LostModeActivity

/** Restarts protection after a reboot, and puts the lost screen back up if the phone is in stolen mode. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                TrackingService.start(context)
                SimMonitor.check(context)
                if (StolenState.isOn(context)) LostModeActivity.show(context, StolenState.message(context))
            }
        }
    }
}
