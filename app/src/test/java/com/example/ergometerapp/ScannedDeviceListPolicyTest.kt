package com.example.ergometerapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannedDeviceListPolicyTest {

    @Test
    fun upsertAddsNewDeviceWhenMacDoesNotExist() {
        val devices = mutableListOf<ScannedBleDevice>()

        val changed = ScannedDeviceListPolicy.upsert(
            devices = devices,
            incoming = ScannedBleDevice(
                macAddress = "AA:BB:CC:DD:EE:01",
                displayName = "Trainer",
                rssi = -48,
            ),
        )

        assertTrue(changed)
        assertEquals(1, devices.size)
        assertEquals("AA:BB:CC:DD:EE:01", devices.first().macAddress)
    }

    @Test
    fun upsertKeepsExistingDeviceWhenIncomingIsWeakerAndNameNotBetter() {
        val devices = mutableListOf(
            ScannedBleDevice(
                macAddress = "AA:BB:CC:DD:EE:02",
                displayName = "HR Strap",
                rssi = -55,
            ),
        )

        val changed = ScannedDeviceListPolicy.upsert(
            devices = devices,
            incoming = ScannedBleDevice(
                macAddress = "AA:BB:CC:DD:EE:02",
                displayName = "HR Strap",
                rssi = -70,
            ),
        )

        assertFalse(changed)
        assertEquals(-55, devices.first().rssi)
    }

    @Test
    fun upsertReplacesExistingWhenNameWasMissing() {
        val devices = mutableListOf(
            ScannedBleDevice(
                macAddress = "AA:BB:CC:DD:EE:03",
                displayName = null,
                rssi = -50,
            ),
        )

        val changed = ScannedDeviceListPolicy.upsert(
            devices = devices,
            incoming = ScannedBleDevice(
                macAddress = "AA:BB:CC:DD:EE:03",
                displayName = "Polar H10",
                rssi = -65,
            ),
        )

        assertTrue(changed)
        assertEquals("Polar H10", devices.first().displayName)
        assertEquals(-65, devices.first().rssi)
    }

    @Test
    fun sortedBySignalOrdersDescendingByRssi() {
        val sorted = ScannedDeviceListPolicy.sortedBySignal(
            listOf(
                ScannedBleDevice(macAddress = "A", displayName = "A", rssi = -80),
                ScannedBleDevice(macAddress = "B", displayName = "B", rssi = -50),
                ScannedBleDevice(macAddress = "C", displayName = "C", rssi = -65),
            ),
        )

        assertEquals(listOf("B", "C", "A"), sorted.map { it.macAddress })
    }
}
