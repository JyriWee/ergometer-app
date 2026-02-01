package com.example.ergometerapp.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import java.util.UUID

class FtmsBleClient(
    private val context: Context,
    private val onIndoorBikeData: (ByteArray) -> Unit,
    private val onReady: () -> Unit,
    private val onControlPointResponse: (Int, Int) -> Unit,
    private val onDisconnected: () -> Unit
) {

    private var gatt: BluetoothGatt? = null
    private var controlPointCharacteristic: BluetoothGattCharacteristic? = null

    private var indoorBikeDataCharacteristic: BluetoothGattCharacteristic? = null

    private enum class SetupStep { NONE, CP_CCCD, BIKE_CCCD }
    private var setupStep: SetupStep = SetupStep.NONE

    private val FTMS_SERVICE_UUID =
        UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    private val INDOOR_BIKE_DATA_UUID =
        UUID.fromString("00002ad2-0000-1000-8000-00805f9b34fb")

    private val FTMS_CONTROL_POINT_UUID =
        UUID.fromString("00002ad9-0000-1000-8000-00805f9b34fb")

    private val CCCD_UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            this@FtmsBleClient.gatt = gatt

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.w("FTMS", "GATT disconnected (status=$status)")
            controlPointCharacteristic = null
            this@FtmsBleClient.gatt = null
            onDisconnected()
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Log.d("FTMS", "onDescriptorWrite step=$setupStep status=$status")

            when (setupStep) {
                SetupStep.CP_CCCD -> {
                    writeBikeCccd(gatt)
                }
                SetupStep.BIKE_CCCD -> {
                    setupStep = SetupStep.NONE
                    Log.d("FTMS", "FTMS notifications/indications setup done")
                    onReady()
                }
                else -> {
                    // ignore
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(FTMS_SERVICE_UUID) ?: run {
                Log.w("FTMS", "FTMS service not found")
                return
            }

            indoorBikeDataCharacteristic =
                service.getCharacteristic(INDOOR_BIKE_DATA_UUID)

            controlPointCharacteristic =
                service.getCharacteristic(FTMS_CONTROL_POINT_UUID)

            if (controlPointCharacteristic == null) {
                Log.w("FTMS", "Control Point characteristic not found")
            } else {
                Log.d("FTMS", "Control Point ready")
            }

            if (indoorBikeDataCharacteristic == null) {
                Log.w("FTMS", "Indoor Bike Data characteristic not found")
                return
            }

            // Enable notifications locally (doesn't write CCCD yet)
            gatt.setCharacteristicNotification(indoorBikeDataCharacteristic, true)

            // Enable indications locally (doesn't write CCCD yet)
            controlPointCharacteristic?.let { cp ->
                gatt.setCharacteristicNotification(cp, true)
            }

            // Start CCCD writes in a controlled order:
            // 1) Control Point indications
            val cp = controlPointCharacteristic ?: run {
                // If no CP, we can still at least enable bike data CCCD directly:
                writeBikeCccd(gatt)
                return
            }

            writeControlPointCccd(gatt, cp)
        }

        private fun handleCharacteristicChanged(
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            when (characteristic.uuid) {
                INDOOR_BIKE_DATA_UUID -> onIndoorBikeData(value)
                FTMS_CONTROL_POINT_UUID -> {
                    Log.d("FTMS", "Control Point response: ${value.joinToString()}")

                    // Response Code = 0x80, request opcode = value[1], result = value[2]
                    if (value.size >= 3 && value[0] == 0x80.toByte()) {
                        val requestOpcode = value[1].toInt() and 0xFF
                        val resultCode = value[2].toInt() and 0xFF
                        onControlPointResponse(requestOpcode, resultCode)
                    }
                }

            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = characteristic.value ?: return
            handleCharacteristicChanged(characteristic, value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicChanged(characteristic, value)
        }

    }

    fun connect(mac: String) {
        val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(mac)
        gatt = device.connectGatt(context, false, gattCallback)
    }

    fun close() {
        gatt?.close()
        gatt = null
    }
    private fun writeControlPointCccd(gatt: BluetoothGatt, cp: BluetoothGattCharacteristic) {
        val cccd = cp.getDescriptor(CCCD_UUID)
        if (cccd == null) {
            Log.w("FTMS", "CCCD not found for Control Point")
            // fall back to bike CCCD
            writeBikeCccd(gatt)
            return
        }

        setupStep = SetupStep.CP_CCCD
        cccd.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        val ok = gatt.writeDescriptor(cccd)
        Log.d("FTMS", "Writing CP CCCD (indication) -> $ok")
    }

    private fun writeBikeCccd(gatt: BluetoothGatt) {
        val ch = indoorBikeDataCharacteristic ?: run {
            Log.w("FTMS", "Bike characteristic missing when writing CCCD")
            return
        }

        val cccd = ch.getDescriptor(CCCD_UUID)
        if (cccd == null) {
            Log.w("FTMS", "CCCD not found for Indoor Bike Data")
            return
        }

        setupStep = SetupStep.BIKE_CCCD
        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val ok = gatt.writeDescriptor(cccd)
        Log.d("FTMS", "Writing BIKE CCCD (notification) -> $ok")
    }

    fun writeControlPoint(payload: ByteArray) {
        val currentGatt = gatt
        val characteristic = controlPointCharacteristic

        if (currentGatt == null || characteristic == null) {
            Log.w("FTMS", "writeControlPoint called but not ready")
            return
        }

        characteristic.value = payload
        characteristic.writeType =
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        val ok = currentGatt.writeCharacteristic(characteristic)
        Log.d("FTMS", "writeControlPoint ${payload.joinToString()} -> $ok")
    }

}