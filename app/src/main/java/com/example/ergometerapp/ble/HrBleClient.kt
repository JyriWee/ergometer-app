package com.example.ergometerapp.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
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
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(HR_SERVICE_UUID) ?: return
            val ch = service.getCharacteristic(HR_MEASUREMENT_UUID) ?: return

            gatt.setCharacteristicNotification(ch, true)
            val ccc = ch.getDescriptor(CCC_UUID) ?: return
            ccc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(ccc)
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
        require(BluetoothAdapter.checkBluetoothAddress(mac)) {
            "Invalid Bluetooth MAC address: $mac"
        }
        val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(mac)
        gatt = device.connectGatt(context, false, gattCallback)
    }

    fun close() {
        gatt?.close()
        gatt = null
    }

    // HR Measurement (0x2A37) – V0: vain bpm
    private fun parseHeartRate(bytes: ByteArray): Int {
        val flags = bytes[0].toInt()
        val hr16bit = flags and 0x01 != 0
        return if (hr16bit) {
            (bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)
        } else {
            bytes[1].toInt() and 0xFF
        }
    }
}