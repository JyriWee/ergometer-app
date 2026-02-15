package com.example.ergometerapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothMacAddressTest {

    @Test
    fun normalizeOrNullReturnsCanonicalAddressWhenInputIsValid() {
        val normalized = BluetoothMacAddress.normalizeOrNull("aa:bb:cc:dd:ee:ff")

        assertEquals("AA:BB:CC:DD:EE:FF", normalized)
    }

    @Test
    fun normalizeOrNullReturnsNullWhenFormatIsInvalid() {
        val normalized = BluetoothMacAddress.normalizeOrNull("AABBCCDDEEFF")

        assertNull(normalized)
    }

    @Test
    fun sanitizeUserInputDropsUnsupportedCharacters() {
        val sanitized = BluetoothMacAddress.sanitizeUserInput("aa:bb-cc?dd.ee ff")

        assertEquals("AA:BBCCDDEEFF", sanitized)
    }
}
