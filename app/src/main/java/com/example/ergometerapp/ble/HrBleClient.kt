package com.example.ergometerapp.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID

/**
 * BLE client for the standard Heart Rate Service (0x180D).
 *
 * The HR Measurement characteristic is notify-only; the client must enable the
 * CCCD and then parse notifications according to the flag byte.
 */
class HrBleClient(
    private val context: Context,
    private val onHeartRate: (Int) -> Unit,
    private val onConnected: () -> Unit = {},
    private val onDisconnected: () -> Unit = {},
) {
    private var gatt: BluetoothGatt? = null
    private val mainThreadHandler = Handler(Looper.getMainLooper())
    private val reconnectCoordinator = HrReconnectCoordinator(
        handler = mainThreadHandler,
        onReconnectRequested = { mac -> connectInternal(mac, isReconnect = true) },
        onDisconnected = { mainThreadHandler.post { onDisconnected() } },
    )

    private val HR_SERVICE_UUID =
        UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val HR_MEASUREMENT_UUID =
        UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    private val CCC_UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            if (gatt != this@HrBleClient.gatt) {
                Log.d("HR", "Ignoring stale connection callback state=$newState status=$status")
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                reconnectCoordinator.onConnected()
                mainThreadHandler.post { onConnected() }
                if (!hasBluetoothConnectPermission()) {
                    Log.w("HR", "Missing BLUETOOTH_CONNECT permission; cannot discover services")
                    reconnectCoordinator.onConnectAttemptFailed()
                    return
                }
                try {
                    gatt.discoverServices()
                } catch (e: SecurityException) {
                    Log.w("HR", "discoverServices failed: ${e.message}")
                    reconnectCoordinator.onConnectAttemptFailed()
                }
                return
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                try {
                    gatt.close()
                } catch (e: SecurityException) {
                    Log.w("HR", "close after disconnect failed: ${e.message}")
                }
                if (this@HrBleClient.gatt === gatt) {
                    this@HrBleClient.gatt = null
                }
                reconnectCoordinator.onGattDisconnected()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w("HR", "Service discovery failed (status=$status)")
                try {
                    gatt.disconnect()
                } catch (e: SecurityException) {
                    Log.w("HR", "disconnect failed after discovery error: ${e.message}")
                }
                return
            }
            if (!hasBluetoothConnectPermission()) {
                Log.w("HR", "Missing BLUETOOTH_CONNECT permission; cannot configure HR notifications")
                return
            }
            val service = gatt.getService(HR_SERVICE_UUID) ?: return
            val ch = service.getCharacteristic(HR_MEASUREMENT_UUID) ?: return

            try {
                gatt.setCharacteristicNotification(ch, true)
                val ccc = ch.getDescriptor(CCC_UUID) ?: return
                gatt.writeDescriptor(ccc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } catch (e: SecurityException) {
                Log.w("HR", "Configuring notifications failed: ${e.message}")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == HR_MEASUREMENT_UUID) {
                val bpm = HrMeasurementParser.parseBpm(value) ?: return
                mainThreadHandler.post { onHeartRate(bpm) }
            }
        }

    }

    /**
     * Connects to a Heart Rate peripheral by MAC address.
     *
     * This uses the platform GATT stack; callers must ensure Bluetooth is enabled
     * and the device is bonded if required by the peripheral.
     */
    fun connect(mac: String) {
        require(BluetoothAdapter.checkBluetoothAddress(mac)) {
            "Invalid Bluetooth MAC address: $mac"
        }
        reconnectCoordinator.onConnectRequested(mac)
        connectInternal(mac, isReconnect = false)
    }
    @Suppress("unused")
    /**
     * Releases the GATT connection.
     *
     * Always safe to call; exceptions can occur if permissions were revoked.
     */
    fun close() {
        reconnectCoordinator.onCloseRequested()
        try {
            gatt?.close()
        } catch (e: SecurityException) {
            Log.w("HR", "close failed: ${e.message}")
        }
        gatt = null
    }

    private fun connectInternal(mac: String, isReconnect: Boolean) {
        if (!hasBluetoothConnectPermission()) {
            Log.w("HR", "Missing BLUETOOTH_CONNECT permission; cannot connect")
            if (isReconnect) {
                reconnectCoordinator.onConnectAttemptFailed()
            }
            return
        }

        try {
            gatt?.close()
        } catch (e: SecurityException) {
            Log.w("HR", "close before connect failed: ${e.message}")
        }
        gatt = null

        try {
            val bluetoothManager = context.getSystemService(android.bluetooth.BluetoothManager::class.java)
            val adapter = bluetoothManager.adapter
            val device = adapter.getRemoteDevice(mac)
            val newGatt = device.connectGatt(context, false, gattCallback)
            if (newGatt == null) {
                Log.w("HR", "connectGatt returned null")
                reconnectCoordinator.onConnectAttemptFailed()
                return
            }
            gatt = newGatt
        } catch (e: SecurityException) {
            Log.w("HR", "connectGatt failed: ${e.message}")
            reconnectCoordinator.onConnectAttemptFailed()
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }
}
