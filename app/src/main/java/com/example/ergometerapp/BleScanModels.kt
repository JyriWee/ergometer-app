package com.example.ergometerapp

/**
 * Device category used by the in-app BLE picker.
 */
enum class DeviceSelectionKind {
    FTMS,
    HEART_RATE,
}

/**
 * UI-level BLE scan result item.
 */
data class ScannedBleDevice(
    val macAddress: String,
    val displayName: String?,
    val rssi: Int,
)
