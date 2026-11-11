package com.protectmydevice.fcm

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.protectmydevice.admin.AdminReceiver
import com.protectmydevice.admin.AlarmPlayer
import com.protectmydevice.data.Command
import com.protectmydevice.data.DeviceRepository
import com.protectmydevice.location.TrackingService
import com.protectmydevice.ui.LostModeActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Receives high-priority FCM data messages ({cmd, message}) sent by the owner's Cloud Function. */
class CommandService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch { runCatching { DeviceRepository(applicationContext).updateToken(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val cmd = Command.parse(message.data["cmd"]) ?: return
        when (cmd) {
            Command.LOCATE -> TrackingService.start(this, urgent = true)
            Command.LOCK -> AdminReceiver.lock(this)
            Command.ALARM -> AlarmPlayer.start(this)
            Command.STOP_ALARM -> AlarmPlayer.stop()
            Command.LOST_MODE -> {
                TrackingService.start(this, urgent = true)
                startActivity(
                    Intent(this, LostModeActivity::class.java)
                        .putExtra(LostModeActivity.EXTRA_MESSAGE, message.data["message"].orEmpty())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            Command.WIPE -> AdminReceiver.wipe(this)
        }
    }
}
