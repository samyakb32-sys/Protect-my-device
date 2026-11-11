package com.protectmydevice.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.protectmydevice.R
import com.protectmydevice.data.DeviceRepository
import com.protectmydevice.data.StolenState
import com.protectmydevice.security.ThiefPhotoService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service that reports the device location to Firestore over WiFi or mobile data.
 * Works without a SIM card: FusedLocationProvider uses GPS + WiFi/Bluetooth scans.
 */
class TrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repo: DeviceRepository
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val handler = Handler(Looper.getMainLooper())
    private val settle = Runnable { requestUpdates(urgent = false) }
    private var receiversRegistered = false

    private fun battery() = (getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val battery = battery()
            scope.launch { runCatching { repo.pushLocation(loc, battery) } }
        }
    }

    /**
     * Moments worth a "last known position": the phone being switched off, the battery running
     * out, and airplane mode going on. These broadcasts only reach receivers registered at
     * runtime, so they are tied to this always-running service rather than the manifest.
     * Firestore queues the writes on disk, so anything that can't be sent now goes out the next
     * time the phone is online.
     */
    private val systemEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SHUTDOWN -> lastGasp("POWERING_OFF")
                Intent.ACTION_BATTERY_LOW -> { lastGasp("BATTERY_LOW"); burst() }
                Intent.ACTION_AIRPLANE_MODE_CHANGED -> if (intent.getBooleanExtra("state", false)) {
                    lastGasp("AIRPLANE_MODE_ON")
                    if (StolenState.isOn(context)) ThiefPhotoService.capture(context, reason = "AIRPLANE_MODE_PHOTO")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastGasp(type: String) {
        val battery = battery()
        if (!hasLocationPermission()) {
            scope.launch { runCatching { repo.logEvent(type) } }
            return
        }
        client.lastLocation.addOnCompleteListener { task ->
            val loc = task.result
            scope.launch {
                runCatching {
                    if (loc != null) repo.pushLocation(loc, battery)
                    repo.logEvent(type, if (loc != null) mapOf("lat" to loc.latitude, "lng" to loc.longitude) else emptyMap())
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = DeviceRepository(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (!receiversRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SHUTDOWN)
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            }
            ContextCompat.registerReceiver(this, systemEvents, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiversRegistered = true
        }
        if (intent?.getBooleanExtra(EXTRA_URGENT, false) == true) burst() else requestUpdates(urgent = false)
        return START_STICKY
    }

    /** A fix every 15 seconds for two minutes, then back to the regular pace. */
    private fun burst() {
        handler.removeCallbacks(settle)
        requestUpdates(urgent = true)
        handler.postDelayed(settle, URGENT_WINDOW_MS)
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates(urgent: Boolean) {
        val stolen = StolenState.isOn(this)
        val interval = when {
            urgent -> URGENT_MS
            stolen -> STOLEN_MS
            else -> NORMAL_MS
        }
        // Stolen mode spends battery on GPS-grade fixes; day to day it stays on the cheap setting.
        val priority = if (urgent || stolen) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val request = LocationRequest.Builder(priority, interval)
            .setMinUpdateIntervalMillis(interval / 2)
            .build()
        client.removeLocationUpdates(callback)
        if (hasLocationPermission()) {
            client.requestLocationUpdates(request, callback, mainLooper)
            _running.value = true
        } else stopSelf()
    }

    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(
        this, android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.tracking_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_shield_check)
            .setColor(ContextCompat.getColor(this, R.color.brand_ink))
            .setContentTitle(getString(R.string.tracking_notification))
            .setOngoing(true)
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    override fun onDestroy() {
        _running.value = false
        handler.removeCallbacks(settle)
        if (receiversRegistered) unregisterReceiver(systemEvents)
        client.removeLocationUpdates(callback)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "protection"
        const val EXTRA_URGENT = "urgent"
        private const val NORMAL_MS = 15 * 60_000L
        private const val STOLEN_MS = 10 * 60_000L
        private const val URGENT_MS = 15_000L
        private const val URGENT_WINDOW_MS = 2 * 60_000L

        private val _running = MutableStateFlow(false)
        /** True while location updates are being reported; the home screen shows this as "tracking on". */
        val running: StateFlow<Boolean> = _running

        fun start(context: Context, urgent: Boolean = false) {
            ContextCompat.startForegroundService(
                context, Intent(context, TrackingService::class.java).putExtra(EXTRA_URGENT, urgent)
            )
        }
    }
}
