package com.protectmydevice.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.NotificationCompat
import com.protectmydevice.R
import com.protectmydevice.ui.screens.LostModeScreen
import com.protectmydevice.ui.theme.ProtectTheme

/** Full-screen "this phone is lost" message with the owner's contact text, shown over the lock screen. */
class LostModeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The screen is yellow in every theme, so the system bar icons must always be dark.
        val bars = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        val msg = intent.getStringExtra(EXTRA_MESSAGE).orEmpty().ifBlank { "This phone is lost. Please contact the owner." }
        setContent { ProtectTheme(darkTheme = false) { LostModeScreen(msg) } }
    }

    companion object {
        const val EXTRA_MESSAGE = "message"
        private const val CHANNEL = "lost"
        private const val NOTIFICATION_ID = 3

        /**
         * Puts the lost screen up. Android 10+ blocks activity starts from the background (which is
         * where a push command or a reboot arrives), so this also posts a full-screen notification:
         * on a locked phone the system opens the screen from that.
         */
        fun show(context: Context, message: String) {
            val intent = Intent(context, LostModeActivity::class.java)
                .putExtra(EXTRA_MESSAGE, message)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Lost mode", NotificationManager.IMPORTANCE_HIGH))
            val open = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            runCatching {
                nm.notify(
                    NOTIFICATION_ID,
                    NotificationCompat.Builder(context, CHANNEL)
                        .setSmallIcon(R.drawable.ic_shield_alert)
                        .setContentTitle("Lost phone")
                        .setContentText(message.ifBlank { "This phone is lost. Please contact the owner." })
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setOngoing(true)
                        .setContentIntent(open)
                        .setFullScreenIntent(open, true)
                        .build(),
                )
            }
        }

        fun dismiss(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        }
    }
}
