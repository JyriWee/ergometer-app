package com.example.ergometerapp.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.example.ergometerapp.ScannedBleDevice
import java.util.UUID

/**
 * Performs short BLE scans filtered by service UUID for trainer/HR selection.
 *
 * The scanner owns a single in-flight scan to avoid overlapping callbacks and
 * duplicated device lists in UI.
 */
class BleDeviceScanner(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var callback: ScanCallback? = null
    private var stopRunnable: Runnable? = null

    /**
     * Starts a fresh scan for [serviceUuid] and emits discovered devices.
     *
     * Returns false when scanning cannot start.
     */
    fun start(
        serviceUuid: UUID,
        durationMs: Long = 10000L,
        onDeviceFound: (ScannedBleDevice) -> Unit,
        onFinished: (String?) -> Unit,
    ): Boolean {
        stop()

        if (!hasBluetoothScanPermission()) {
            onFinished("Missing BLUETOOTH_SCAN permission.")
            return false
        }

        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            onFinished("Bluetooth is unavailable or disabled.")
            return false
        }

        val leScanner = adapter.bluetoothLeScanner
        if (leScanner == null) {
            onFinished("BLE scanner is unavailable on this device.")
            return false
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(serviceUuid))
                .build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                emitDevice(result, onDeviceFound)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { emitDevice(it, onDeviceFound) }
            }

            override fun onScanFailed(errorCode: Int) {
                stop()
                mainHandler.post { onFinished("Scan failed (code=$errorCode).") }
            }
        }

        try {
            leScanner.startScan(filters, settings, scanCallback)
        } catch (e: SecurityException) {
            onFinished("Scan failed: ${e.message ?: "permission denied"}")
            return false
        } catch (e: Exception) {
            onFinished("Scan failed: ${e.message ?: "unknown error"}")
            return false
        }

        scanner = leScanner
        callback = scanCallback
        stopRunnable = Runnable {
            stop()
            onFinished(null)
        }
        mainHandler.postDelayed(stopRunnable!!, durationMs)
        return true
    }

    /**
     * Stops current scan, if any.
     */
    fun stop() {
        stopRunnable?.let { mainHandler.removeCallbacks(it) }
        stopRunnable = null

        val currentScanner = scanner
        val currentCallback = callback
        scanner = null
        callback = null

        if (currentScanner != null && currentCallback != null) {
            try {
                currentScanner.stopScan(currentCallback)
            } catch (e: SecurityException) {
                Log.w("BLE_SCAN", "stopScan failed: ${e.message}")
            } catch (_: IllegalStateException) {
                // Scanner may already be torn down.
            }
        }
    }

    private fun emitDevice(result: ScanResult, onDeviceFound: (ScannedBleDevice) -> Unit) {
        val device = result.device ?: return
        val address = device.address ?: return
        if (!BluetoothAdapter.checkBluetoothAddress(address)) return
        val nameFromRecord = result.scanRecord?.deviceName
        val name = nameFromRecord ?: safeDeviceName(device)
        mainHandler.post {
            onDeviceFound(
                ScannedBleDevice(
                    macAddress = address,
                    displayName = name,
                    rssi = result.rssi,
                ),
            )
        }
    }

    private fun safeDeviceName(device: BluetoothDevice): String? {
        return try {
            device.name
        } catch (_: SecurityException) {
            null
        }
    }

    private fun hasBluetoothScanPermission(): Boolean {
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED
    }
}
