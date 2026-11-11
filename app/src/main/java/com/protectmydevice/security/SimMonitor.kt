package com.protectmydevice.security

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.protectmydevice.data.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Detects a SIM swap by comparing a fingerprint of the active subscriptions with the one
 * saved earlier. (ICCID is not readable by normal apps on Android 10+, so we use the
 * subscription ids + slot indexes, which change when a different SIM is inserted.)
 * Called at boot and from the app; a swapped SIM triggers an urgent location + event + photo.
 */
object SimMonitor {
    private const val PREFS = "sim"
    private const val KEY = "fingerprint"

    @SuppressLint("MissingPermission")
    fun fingerprint(context: Context): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) return null
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return null
        return sm.activeSubscriptionInfoList.orEmpty()
            .sortedBy { it.simSlotIndex }
            .joinToString("|") { "${it.simSlotIndex}:${it.subscriptionId}:${it.carrierName}" }
    }

    /** Stores the current SIM state as the trusted baseline. */
    fun saveBaseline(context: Context) {
        fingerprint(context)?.let {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, it).apply()
        }
    }

    fun check(context: Context) {
        val current = fingerprint(context) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY, null)
        if (saved == null) { prefs.edit().putString(KEY, current).apply(); return }
        if (saved != current) {
            val app = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { DeviceRepository(app).logEvent("SIM_CHANGED") }
            }
            com.protectmydevice.location.TrackingService.start(app, urgent = true)
            ThiefPhotoService.capture(app, reason = "SIM_CHANGED")
            // Do NOT overwrite the baseline: keep alerting until the owner re-confirms in the app.
        }
    }
}
