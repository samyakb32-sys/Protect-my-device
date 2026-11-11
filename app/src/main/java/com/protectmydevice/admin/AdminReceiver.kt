package com.protectmydevice.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.protectmydevice.data.DeviceRepository
import com.protectmydevice.security.ThiefPhotoService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Device-admin hook: enables lock/wipe and reports wrong-password attempts with a photo. */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onPasswordFailed(context: Context, intent: Intent) {
        ThiefPhotoService.capture(context, reason = "WRONG_PASSWORD")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Disabling protection removes remote lock/wipe for this phone."

    companion object {
        fun component(context: Context) = ComponentName(context, AdminReceiver::class.java)

        fun isActive(context: Context): Boolean =
            (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager)
                .isAdminActive(component(context))

        fun lock(context: Context) {
            if (isActive(context))
                (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).lockNow()
        }

        fun wipe(context: Context) {
            if (!isActive(context)) return
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { DeviceRepository(context).logEvent("WIPE_EXECUTED") }
                (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).wipeData(0)
            }
        }
    }
}
