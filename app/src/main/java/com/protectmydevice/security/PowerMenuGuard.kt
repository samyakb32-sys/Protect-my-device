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
 * accessibility service to close the ways in while the phone is locked:
 *  - the power menu (holding the power button), and
 *  - the Quick Settings panel pulled down over the lock screen, which on most phones has a power
 *    button and the airplane-mode tile.
 *
 * Limits: it recognises those panels by SystemUI's internal class and view names, which vary by
 * manufacturer and Android version; it cannot stop a forced restart (holding the hardware keys)
 * or a flat battery; and the owner has to switch it on by hand under Settings > Accessibility.
 * While it is on, the owner also can't pull down Quick Settings until they unlock.
 */
class PowerMenuGuard : AccessibilityService() {
    private var lastReport = 0L
    private var lastScan = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName?.toString() != "com.android.systemui") return
        if (!getSystemService(KeyguardManager::class.java).isKeyguardLocked) return

        val blocked = when {
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                isPowerMenu(event.className?.toString()) -> "POWER_OFF_BLOCKED"
            quickSettingsOpen(event) -> "QUICK_SETTINGS_BLOCKED"
            else -> return
        }

        performGlobalAction(GLOBAL_ACTION_BACK)
        if (Build.VERSION.SDK_INT >= 31) performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)

        val now = System.currentTimeMillis()
        if (now - lastReport > 60_000) {
            lastReport = now
            val repo = DeviceRepository(applicationContext)
            CoroutineScope(Dispatchers.IO).launch { runCatching { repo.logEvent(blocked) } }
        }
    }

    /** Looks through SystemUI's on-screen views for a visible Quick Settings panel. Rate-limited: content events are frequent. */
    private fun quickSettingsOpen(event: AccessibilityEvent): Boolean {
        val now = System.currentTimeMillis()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && now - lastScan < 250) return false
        lastScan = now
        val root = rootInActiveWindow ?: return false
        return QUICK_SETTINGS_IDS.any { id ->
            root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/$id").any { it.isVisibleToUser }
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        /** View ids SystemUI gives the Quick Settings panel and its power button on AOSP-based phones (Pixel and most others). */
        val QUICK_SETTINGS_IDS = listOf("quick_qs_panel", "quick_settings_panel", "qs_panel", "qs_footer_actions", "pm_lite")

        /** The power menu ("global actions") window, by its SystemUI class name. */
        fun isPowerMenu(className: String?) = className.orEmpty().contains("globalactions", ignoreCase = true)

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, PowerMenuGuard::class.java).flattenToString()
            return Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')?.any { it.equals(me, ignoreCase = true) } == true
        }
    }
}
