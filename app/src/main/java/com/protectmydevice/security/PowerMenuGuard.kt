package com.protectmydevice.security

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.protectmydevice.data.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * EXPERIMENTAL. Android has no API to stop someone switching a phone off, so this uses an
 * accessibility service to watch for the system power menu and close it again while the phone is
 * locked. Whoever holds a locked phone then can't reach "Power off" from the menu.
 *
 * Limits: it relies on SystemUI's internal class name ("…globalactions…"), which varies by
 * manufacturer and Android version; it cannot stop a forced shutdown (holding the hardware keys);
 * and the owner has to switch it on by hand under Settings > Accessibility.
 */
class PowerMenuGuard : AccessibilityService() {
    private var lastReport = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() != "com.android.systemui") return
        if (!event.className?.toString().orEmpty().contains("globalactions", ignoreCase = true)) return
        if (!getSystemService(KeyguardManager::class.java).isKeyguardLocked) return

        performGlobalAction(GLOBAL_ACTION_BACK)
        if (Build.VERSION.SDK_INT >= 31) performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)

        val now = System.currentTimeMillis()
        if (now - lastReport > 60_000) {
            lastReport = now
            val repo = DeviceRepository(applicationContext)
            CoroutineScope(Dispatchers.IO).launch { runCatching { repo.logEvent("POWER_OFF_BLOCKED") } }
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, PowerMenuGuard::class.java).flattenToString()
            return Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')?.any { it.equals(me, ignoreCase = true) } == true
        }
    }
}
