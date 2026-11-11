package com.protectmydevice.data

/** Remote commands the owner can send to a protected device. */
enum class Command {
    LOCATE, LOCK, ALARM, STOP_ALARM, LOST_MODE, WIPE;

    companion object {
        /** Parses the "cmd" field of an FCM data message. Unknown values return null. */
        fun parse(raw: String?): Command? =
            raw?.trim()?.uppercase()?.let { v -> values().firstOrNull { it.name == v } }
    }
}
