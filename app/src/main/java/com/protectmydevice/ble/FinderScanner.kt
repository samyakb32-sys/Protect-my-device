package com.protectmydevice.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid

/**
 * Offline finding, part 2: scans for [BeaconAdvertiser] beacons. [onSighting] receives the raw
 * beacon id and RSSI; the caller uploads (beaconId, own location) so the owner's backend can match
 * it against the lost device's secret. Coverage depends on how many people run the app nearby.
 */
class FinderScanner(private val context: Context, private val onSighting: (ByteArray, Int) -> Unit) {
    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val id = result.scanRecord?.getServiceData(ParcelUuid(BeaconAdvertiser.SERVICE_UUID)) ?: return
            onSighting(id, result.rssi)
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        val scanner = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter?.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(BeaconAdvertiser.SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
        scanner.startScan(listOf(filter), settings, callback)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter?.bluetoothLeScanner?.stopScan(callback)
    }
}
