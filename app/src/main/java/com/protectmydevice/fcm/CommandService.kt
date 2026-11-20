package com.protectmydevice.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.protectmydevice.admin.AdminReceiver
import com.protectmydevice.admin.AlarmPlayer
import com.protectmydevice.data.Command
import com.protectmydevice.data.DeviceRepository
import com.protectmydevice.data.StolenState
import com.protectmydevice.location.TrackingService
import com.protectmydevice.security.ThiefPhotoService
import com.protectmydevice.ui.LostModeActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Receives high-priority FCM data messages ({cmd, message}) sent by the owner's Cloud Function. */
class CommandService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch { runCatching { DeviceRepository(applicationContext).updateToken(token) } }
    }

    private fun report(block: suspend DeviceRepository.() -> Unit) {
        val repo = DeviceRepository(applicationContext)
        CoroutineScope(Dispatchers.IO).launch { runCatching { repo.block() } }
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
                LostModeActivity.show(this, message.data["message"].orEmpty())
            }
            Command.WIPE -> AdminReceiver.wipe(this)
            Command.STOLEN_MODE -> {
                val text = message.data["message"].orEmpty()
                StolenState.set(this, true, text)
                TrackingService.start(this, urgent = true)
                AdminReceiver.lock(this)
                LostModeActivity.show(this, text)
                ThiefPhotoService.capture(this, reason = "STOLEN_MODE_PHOTO")
                report { setStolen(true); logEvent("STOLEN_MODE_ON") }
            }
            Command.SAFE -> {
                StolenState.set(this, false)
                AlarmPlayer.stop()
                LostModeActivity.dismiss(this)
                TrackingService.start(this)
                report { setStolen(false); logEvent("MARKED_SAFE") }
            }
            Command.PHOTO -> ThiefPhotoService.capture(this, reason = "REMOTE_PHOTO")
        }
    }
}
