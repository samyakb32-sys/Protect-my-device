package com.protectmydevice.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.protectmydevice.R
import com.protectmydevice.data.DeviceRepository
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

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val battery = (getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            scope.launch { runCatching { repo.pushLocation(loc, battery) } }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = DeviceRepository(this)
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        val urgent = intent?.getBooleanExtra(EXTRA_URGENT, false) == true
        val interval = if (urgent) URGENT_MS else NORMAL_MS
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, interval)
            .setMinUpdateIntervalMillis(interval / 2)
            .build()
        client.removeLocationUpdates(callback)
        if (hasLocationPermission()) {
            client.requestLocationUpdates(request, callback, mainLooper)
            _running.value = true
        } else stopSelf()
        return START_STICKY
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
        client.removeLocationUpdates(callback)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "protection"
        const val EXTRA_URGENT = "urgent"
        private const val NORMAL_MS = 5 * 60_000L
        private const val URGENT_MS = 15_000L

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
