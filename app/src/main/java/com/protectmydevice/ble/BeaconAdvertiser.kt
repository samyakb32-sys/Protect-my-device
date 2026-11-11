package com.protectmydevice.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import java.security.MessageDigest
import java.util.UUID

/**
 * Offline finding, part 1: a lost phone with no internet keeps broadcasting a BLE beacon whose id
 * rotates every 15 minutes (derived from a per-device secret), so strangers can't track it but the
 * owner's other devices / other app users' [FinderScanner] can recognise it and report a sighting.
 */
object BeaconAdvertiser {
    val SERVICE_UUID: UUID = UUID.fromString("0000fd6f-0000-1000-8000-00805f9b34fb")
    private const val ROTATION_MS = 15 * 60_000L
    private var callback: AdvertiseCallback? = null

    /** Rotating 8-byte id = first 8 bytes of SHA-256(secret || time-window). */
    fun beaconId(secret: String, nowMs: Long): ByteArray {
        val window = nowMs / ROTATION_MS
        return MessageDigest.getInstance("SHA-256").digest("$secret:$window".toByteArray()).copyOf(8)
    }

    @SuppressLint("MissingPermission")
    fun start(context: Context, secret: String) {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter ?: return
        val advertiser = adapter.bluetoothLeAdvertiser ?: return
        stop(context)
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addServiceData(ParcelUuid(SERVICE_UUID), beaconId(secret, System.currentTimeMillis()))
            .setIncludeDeviceName(false)
            .build()
        callback = object : AdvertiseCallback() {}
        advertiser.startAdvertising(settings, data, callback)
    }

    @SuppressLint("MissingPermission")
    fun stop(context: Context) {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        callback?.let { adapter?.bluetoothLeAdvertiser?.stopAdvertising(it) }
        callback = null
    }
}
