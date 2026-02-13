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
 * BLE client for the Fitness Machine Service (FTMS, 0x1826).
 *
 * This client enables notifications/indications in a strict order to satisfy
 * GATT's single-operation-at-a-time constraint and FTMS expectations for
 * Control Point acknowledgements.
 */

// TODO(bt-reliability): Centralize reconnect and FTMS control ownership handling.

class FtmsBleClient(
    private val context: Context,
    private val onIndoorBikeData: (ByteArray) -> Unit,
    private val onReady: (Boolean) -> Unit,
    private val onControlPointResponse: (Int, Int) -> Unit,
    private val onDisconnected: () -> Unit
) {

    private var gatt: BluetoothGatt? = null
    private var controlPointCharacteristic: BluetoothGattCharacteristic? = null
    private val mainThreadHandler = Handler(Looper.getMainLooper())

    private var indoorBikeDataCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile
    private var disconnectEventEmitted = false

    private enum class SetupStep { NONE, CP_CCCD, BIKE_CCCD }
    private var setupStep: SetupStep = SetupStep.NONE
    private var controlPointIndicationEnabled = false

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
                disconnectEventEmitted = false
                setupStep = SetupStep.NONE
                controlPointIndicationEnabled = false
                if (!hasBluetoothConnectPermission()) {
                    Log.w("FTMS", "Missing BLUETOOTH_CONNECT permission; cannot discover services")
                    return
                }
                try {
                    gatt.discoverServices()
                } catch (e: SecurityException) {
                    Log.w("FTMS", "discoverServices failed: ${e.message}")
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w("FTMS", "GATT disconnected (status=$status)")
                setupStep = SetupStep.NONE
                controlPointIndicationEnabled = false
                indoorBikeDataCharacteristic = null
                try {
                    gatt.close()
                } catch (e: SecurityException) {
                    Log.w("FTMS", "close failed: ${e.message}")
                }

                controlPointCharacteristic = null
                this@FtmsBleClient.gatt = null

                emitDisconnectedOnce()
            }


        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            Log.d("FTMS", "onDescriptorWrite step=$setupStep status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w("FTMS", "Descriptor write failed at step=$setupStep status=$status")
                setupStep = SetupStep.NONE
                controlPointIndicationEnabled = false
                indoorBikeDataCharacteristic = null
                controlPointCharacteristic = null
                this@FtmsBleClient.gatt = null
                try {
                    gatt.disconnect()
                } catch (e: SecurityException) {
                    Log.w("FTMS", "disconnect failed during setup failure: ${e.message}")
                }
                try {
                    gatt.close()
                } catch (e: SecurityException) {
                    Log.w("FTMS", "close failed during setup failure: ${e.message}")
                }
                emitDisconnectedOnce()
                return
            }

            when (setupStep) {
                SetupStep.CP_CCCD -> {
                    controlPointIndicationEnabled = true
                    writeBikeCccd(gatt)
                }
                SetupStep.BIKE_CCCD -> {
                    setupStep = SetupStep.NONE
                    val controlPointReady =
                        controlPointCharacteristic != null && controlPointIndicationEnabled
                    Log.d(
                        "FTMS",
                        "FTMS notifications/indications setup done (controlPointReady=$controlPointReady)"
                    )
                    mainThreadHandler.post { onReady(controlPointReady) }
                }
                else -> {
                    // ignore
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w("FTMS", "Service discovery failed (status=$status)")
                setupStep = SetupStep.NONE
                controlPointIndicationEnabled = false
                indoorBikeDataCharacteristic = null
                controlPointCharacteristic = null
                try {
                    gatt.disconnect()
                } catch (e: SecurityException) {
                    Log.w("FTMS", "disconnect failed after discovery error: ${e.message}")
                }
                return
            }
            if (!hasBluetoothConnectPermission()) {
                Log.w("FTMS", "Missing BLUETOOTH_CONNECT permission; cannot configure FTMS")
                return
            }
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

            // Local enablement is required before writing CCCD values on some stacks.
            try {
                gatt.setCharacteristicNotification(indoorBikeDataCharacteristic, true)
            } catch (e: SecurityException) {
                Log.w("FTMS", "setCharacteristicNotification failed: ${e.message}")
                return
            }

            // Control Point uses indications for reliable acknowledgements.
            controlPointCharacteristic?.let { cp ->
                try {
                    gatt.setCharacteristicNotification(cp, true)
                } catch (e: SecurityException) {
                    Log.w("FTMS", "setCharacteristicNotification failed: ${e.message}")
                    return
                }
            }

            // Start CCCD writes in a controlled order to avoid concurrent GATT ops.
            val cp = controlPointCharacteristic ?: run {
                // If no CP, we can still enable bike data CCCD directly.
                writeBikeCccd(gatt)
                return
            }

            writeControlPointCccd(gatt, cp)
        }

        private fun handleCharacteristicChanged(
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (this@FtmsBleClient.gatt == null) {
                Log.d("FTMS", "Notification ignored (GATT closed)")
                return
            }
            when (characteristic.uuid) {
                INDOOR_BIKE_DATA_UUID -> mainThreadHandler.post {
                    Log.d("FTMS", "IndoorBikeData notification received")
                    onIndoorBikeData(value) }
                FTMS_CONTROL_POINT_UUID -> {
                    Log.d("FTMS", "Control Point response: ${value.joinToString()}")

                    // FTMS: Response Code (0x80), then request opcode and result code.
                    if (value.size >= 3 && value[0] == 0x80.toByte()) {
                        val requestOpcode = value[1].toInt() and 0xFF
                        val resultCode = value[2].toInt() and 0xFF
                        mainThreadHandler.post { onControlPointResponse(requestOpcode, resultCode) }
                    }
                }

            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicChanged(characteristic, value)
        }

    }

    /**
     * Connects to a Fitness Machine peripheral by MAC address.
     *
     * Callers should wait for [onReady] before issuing Control Point commands.
     */
    fun connect(mac: String) {
        if (!hasBluetoothConnectPermission()) {
            Log.w("FTMS", "Missing BLUETOOTH_CONNECT permission; cannot connect")
            return
        }
        if (!BluetoothAdapter.checkBluetoothAddress(mac)) {
            Log.w("FTMS", "Invalid Bluetooth MAC address: $mac")
            return
        }
        if (gatt != null) {
            Log.d("FTMS", "connect ignored (already connected or connecting)")
            return
        }
        disconnectEventEmitted = false
        try {
            val bluetoothManager = context.getSystemService(android.bluetooth.BluetoothManager::class.java)
            val adapter = bluetoothManager.adapter
            val device = adapter.getRemoteDevice(mac)
            val newGatt = device.connectGatt(context, false, gattCallback)
            if (newGatt == null) {
                Log.w("FTMS", "connectGatt returned null; connection not started")
                return
            }
            gatt = newGatt
            Log.d("FTMS", "connectGatt started for $mac")
        } catch (e: SecurityException) {
            Log.w("FTMS", "connectGatt failed: ${e.message}")
        }
    }

    @Suppress("unused")
    /**
     * Releases the GATT connection.
     *
     * Safe to call multiple times; exceptions can occur if permissions change.
     */
    fun close() {
        val currentGatt = gatt ?: return
        try {
            currentGatt.disconnect()
        } catch (e: SecurityException) {
            Log.w("FTMS", "disconnect failed: ${e.message}")
        }
        // ÄLÄ kutsu close() tässä
    }


    /**
     * Emits a single semantic disconnect event per connection lifecycle.
     *
     * Android can surface multiple low-level teardown signals (explicit close,
     * callback disconnect, setup failure). Callers should observe one stable
     * disconnect transition regardless of source.
     */
    private fun emitDisconnectedOnce() {
        synchronized(this) {
            if (disconnectEventEmitted) return
            disconnectEventEmitted = true
        }
        mainThreadHandler.post { onDisconnected() }
    }

    private fun writeControlPointCccd(gatt: BluetoothGatt, cp: BluetoothGattCharacteristic) {
        if (!hasBluetoothConnectPermission()) {
            Log.w("FTMS", "Missing BLUETOOTH_CONNECT permission; cannot write CP CCCD")
            return
        }
        val cccd = cp.getDescriptor(CCCD_UUID)
        if (cccd == null) {
            Log.w("FTMS", "CCCD not found for Control Point")
            // fall back to bike CCCD
            controlPointIndicationEnabled = false
            writeBikeCccd(gatt)
            return
        }

        setupStep = SetupStep.CP_CCCD
        try {
            val ok = gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
            Log.d("FTMS", "Writing CP CCCD (indication) -> $ok")
        } catch (e: SecurityException) {
            Log.w("FTMS", "writeDescriptor failed: ${e.message}")
        }
    }

    private fun writeBikeCccd(gatt: BluetoothGatt) {
        if (!hasBluetoothConnectPermission()) {
            Log.w("FTMS", "Missing BLUETOOTH_CONNECT permission; cannot write Bike CCCD")
            return
        }
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
        try {
            val ok = gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            Log.d("FTMS", "Writing BIKE CCCD (notification) -> $ok")
        } catch (e: SecurityException) {
            Log.w("FTMS", "writeDescriptor failed: ${e.message}")
        }
    }

    /**
     * Writes a raw FTMS Control Point payload.
     *
     * FTMS devices are allowed to reject commands until they have granted control
     * and Control Point indications are enabled.
     */
    fun writeControlPoint(payload: ByteArray) {
        if (!hasBluetoothConnectPermission()) {
            Log.w("FTMS", "Missing BLUETOOTH_CONNECT permission; cannot write Control Point")
            return
        }
        val currentGatt = gatt
        val characteristic = controlPointCharacteristic

        if (currentGatt == null || characteristic == null) {
            Log.w("FTMS", "writeControlPoint called but not ready")
            return
        }

        try {
            val ok = currentGatt.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
            Log.d("FTMS", "writeControlPoint ${payload.joinToString()} -> $ok")
        } catch (e: SecurityException) {
            Log.w("FTMS", "writeCharacteristic failed: ${e.message}")
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

}
