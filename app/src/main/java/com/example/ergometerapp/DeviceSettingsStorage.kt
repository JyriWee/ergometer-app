package com.example.ergometerapp

import android.content.Context

/**
 * Persists user-selected BLE device addresses for FTMS and optional HR sensors.
 */
object DeviceSettingsStorage {
    private const val PREFERENCES_NAME = "ergometer_app_settings"
    private const val KEY_FTMS_DEVICE_MAC = "ftms_device_mac"
    private const val KEY_HR_DEVICE_MAC = "hr_device_mac"

    /**
     * Loads a normalized FTMS device address or null when no valid value exists.
     */
    fun loadFtmsDeviceMac(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_FTMS_DEVICE_MAC, null) ?: return null
        return BluetoothMacAddress.normalizeOrNull(stored)
    }

    /**
     * Saves FTMS device address when valid, otherwise clears the stored value.
     */
    fun saveFtmsDeviceMac(context: Context, macAddress: String?) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val normalized = macAddress?.let { BluetoothMacAddress.normalizeOrNull(it) }
        if (normalized == null) {
            prefs.edit().remove(KEY_FTMS_DEVICE_MAC).apply()
            return
        }
        prefs.edit().putString(KEY_FTMS_DEVICE_MAC, normalized).apply()
    }

    /**
     * Loads a normalized HR device address or null when no valid value exists.
     */
    fun loadHrDeviceMac(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_HR_DEVICE_MAC, null) ?: return null
        return BluetoothMacAddress.normalizeOrNull(stored)
    }

    /**
     * Saves optional HR device address when valid, otherwise clears the stored value.
     */
    fun saveHrDeviceMac(context: Context, macAddress: String?) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val normalized = macAddress?.let { BluetoothMacAddress.normalizeOrNull(it) }
        if (normalized == null) {
            prefs.edit().remove(KEY_HR_DEVICE_MAC).apply()
            return
        }
        prefs.edit().putString(KEY_HR_DEVICE_MAC, normalized).apply()
    }
}
