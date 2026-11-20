package com.protectmydevice.data

import android.content.Context

/** Whether this phone has been told it is stolen. Kept on disk so it survives a reboot. */
object StolenState {
    private const val PREFS = "stolen"

    fun isOn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", false)

    fun message(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("message", "").orEmpty()

    fun set(context: Context, on: Boolean, message: String = "") {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("on", on).putString("message", message).apply()
    }
}
