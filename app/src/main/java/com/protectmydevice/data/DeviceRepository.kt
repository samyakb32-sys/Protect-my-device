package com.protectmydevice.data

import android.content.Context
import android.location.Location
import android.os.Build
import android.provider.Settings
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.protectmydevice.battery.DayReport
import kotlinx.coroutines.tasks.await

/**
 * Firestore layout (all under the owner's uid):
 *   devices/{deviceId}                 owner, name, fcmToken, lastLocation, lastSeen, battery, stolen, offline
 *   devices/{deviceId}/locations/{id}  lat, lng, accuracy, time
 *   devices/{deviceId}/events/{id}     type (SIM_CHANGED, WRONG_PASSWORD, ...), photoPath, time
 *   devices/{deviceId}/commands/{id}   cmd, message  (a Cloud Function forwards it as FCM)
 *   devices/{deviceId}/battery/{day}   this app's own battery use for one finished day (see battery/)
 */
class DeviceRepository(private val context: Context) {
    private val db = Firebase.firestore
    private val uid get() = Firebase.auth.currentUser?.uid

    /** Stable per-install id (ANDROID_ID is not a SIM/IMEI identifier and needs no permission). */
    val deviceId: String
        get() = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    suspend fun register() {
        val owner = uid ?: return
        val token = FirebaseMessaging.getInstance().token.await()
        db.collection("devices").document(deviceId).set(
            mapOf(
                "owner" to owner,
                "name" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "fcmToken" to token,
                "registeredAt" to FieldValue.serverTimestamp(),
            ),
            com.google.firebase.firestore.SetOptions.merge(),
        ).await()
    }

    suspend fun updateToken(token: String) {
        if (uid == null) return
        db.collection("devices").document(deviceId).update("fcmToken", token).await()
    }

    suspend fun pushLocation(loc: Location, batteryPct: Int) {
        if (uid == null) return
        val data = mapOf(
            "lat" to loc.latitude,
            "lng" to loc.longitude,
            "accuracy" to loc.accuracy,
            "time" to FieldValue.serverTimestamp(),
        )
        val ref = db.collection("devices").document(deviceId)
        ref.collection("locations").add(data).await()
        // The server marks a silent phone offline; coming back is reported from here, where it's known first.
        val wasOffline = runCatching { ref.get().await().getBoolean("offline") == true }.getOrDefault(false)
        ref.update(
            mapOf("lastLocation" to data, "lastSeen" to FieldValue.serverTimestamp(), "battery" to batteryPct, "offline" to false),
        ).await()
        if (wasOffline) logEvent("BACK_ONLINE")
    }

    /** Mirrors stolen mode onto the device doc so the owner's app and the web dashboard can show it. */
    suspend fun setStolen(on: Boolean) {
        if (uid == null) return
        db.collection("devices").document(deviceId).update("stolen", on).await()
    }

    suspend fun logEvent(type: String, extra: Map<String, Any> = emptyMap()) {
        if (uid == null) return
        db.collection("devices").document(deviceId).collection("events")
            .add(extra + mapOf("type" to type, "time" to FieldValue.serverTimestamp())).await()
    }

    /**
     * Queues one finished day of this app's battery use. Not awaited: Firestore keeps the write on
     * disk until the phone is online. Returns false when nobody is signed in (nothing was queued).
     */
    fun uploadBatteryDay(report: DayReport): Boolean {
        if (uid == null) return false
        db.collection("devices").document(deviceId).collection("battery").document(report.date.toString())
            .set(report.toUpload() + ("time" to FieldValue.serverTimestamp()))
        return true
    }

    // ---- Owner side ----

    suspend fun myDevices(): List<DeviceInfo> {
        val owner = uid ?: return emptyList()
        return db.collection("devices").whereEqualTo("owner", owner).get().await().documents.map { d ->
            @Suppress("UNCHECKED_CAST")
            val last = d.get("lastLocation") as? Map<String, Any>
            DeviceInfo(
                id = d.id,
                name = d.getString("name") ?: d.id,
                lat = (last?.get("lat") as? Number)?.toDouble(),
                lng = (last?.get("lng") as? Number)?.toDouble(),
                battery = (d.get("battery") as? Number)?.toInt(),
                stolen = d.getBoolean("stolen") == true,
                lastSeenMs = d.getTimestamp("lastSeen")?.toDate()?.time,
            )
        }
    }

    suspend fun sendCommand(targetDeviceId: String, cmd: Command, message: String = "") {
        db.collection("devices").document(targetDeviceId).collection("commands")
            .add(mapOf("cmd" to cmd.name, "message" to message, "time" to FieldValue.serverTimestamp())).await()
    }
}

data class DeviceInfo(
    val id: String,
    val name: String,
    val lat: Double?,
    val lng: Double?,
    val battery: Int?,
    val stolen: Boolean = false,
    val lastSeenMs: Long? = null,
) {
    /** One missed check-in plus 15 minutes. Must match offlineAfterMs in functions/logic.js and web/app.js. */
    fun isOffline(nowMs: Long = System.currentTimeMillis()): Boolean =
        lastSeenMs != null && nowMs - lastSeenMs > ((if (stolen) 10 else 150) + 15) * 60_000L
}
