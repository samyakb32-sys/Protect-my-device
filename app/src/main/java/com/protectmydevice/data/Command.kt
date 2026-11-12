package com.protectmydevice.data

/** Remote commands the owner can send to a protected device. */
enum class Command {
    LOCATE, LOCK, ALARM, STOP_ALARM, LOST_MODE, WIPE,
    /** Lock + lost screen + photo + a location every 10 minutes, until [SAFE]. Survives reboots. */
    STOLEN_MODE,
    /** Ends [STOLEN_MODE]: back to normal tracking, alarm off, lost screen dismissed. */
    SAFE,
    /** Take one front-camera photo now and upload it. */
    PHOTO;

    companion object {
        /** Parses the "cmd" field of an FCM data message. Unknown values return null. */
        fun parse(raw: String?): Command? =
            raw?.trim()?.uppercase()?.let { v -> values().firstOrNull { it.name == v } }
    }
}
