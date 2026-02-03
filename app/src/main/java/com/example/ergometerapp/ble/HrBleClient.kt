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
import android.os.Build
import android.util.Log
import java.util.UUID

class HrBleClient(
    private val context: Context,
    private val onHeartRate: (Int) -> Unit
) {
    private var gatt: BluetoothGatt? = null

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
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (!hasBluetoothConnectPermission()) {
                    Log.w("HR", "Missing BLUETOOTH_CONNECT permission; cannot discover services")
                    return
                }
                try {
                    gatt.discoverServices()
                } catch (e: SecurityException) {
                    Log.w("HR", "discoverServices failed: ${e.message}")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!hasBluetoothConnectPermission()) {
                Log.w("HR", "Missing BLUETOOTH_CONNECT permission; cannot configure HR notifications")
                return
            }
            val service = gatt.getService(HR_SERVICE_UUID) ?: return
            val ch = service.getCharacteristic(HR_MEASUREMENT_UUID) ?: return

            try {
                gatt.setCharacteristicNotification(ch, true)
                val ccc = ch.getDescriptor(CCC_UUID) ?: return
                ccc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(ccc)
            } catch (e: SecurityException) {
                Log.w("HR", "Configuring notifications failed: ${e.message}")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == HR_MEASUREMENT_UUID) {
                val bpm = parseHeartRate(characteristic.value)
                onHeartRate(bpm)

            }
        }
    }

    fun connect(mac: String) {
        if (!hasBluetoothConnectPermission()) {
            Log.w("HR", "Missing BLUETOOTH_CONNECT permission; cannot connect")
            return
        }
        require(BluetoothAdapter.checkBluetoothAddress(mac)) {
            "Invalid Bluetooth MAC address: $mac"
        }
        try {
            val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(mac)
            gatt = device.connectGatt(context, false, gattCallback)
        } catch (e: SecurityException) {
            Log.w("HR", "connectGatt failed: ${e.message}")
        }
    }

    fun close() {
        try {
            gatt?.close()
        } catch (e: SecurityException) {
            Log.w("HR", "close failed: ${e.message}")
        }
        gatt = null
    }

    // HR Measurement (0x2A37) – V0: bpm only
    private fun parseHeartRate(bytes: ByteArray): Int {
        val flags = bytes[0].toInt()
        val hr16bit = flags and 0x01 != 0
        return if (hr16bit) {
            (bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)
        } else {
            bytes[1].toInt() and 0xFF
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }
}
