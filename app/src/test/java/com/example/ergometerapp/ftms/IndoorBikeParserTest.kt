package com.example.ergometerapp.ftms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndoorBikeParserTest {
    @Test
    fun parsesTotalDistanceAsMetersFromDecimeterPayload() {
        val payload = byteArrayOf(
            0x10, 0x00, // flags: bit 4 (Total Distance present)
            0x00, 0x00, // instantaneous speed (required field)
            0x10, 0x27, 0x00, // total distance raw = 10000 decimeters => 1000 meters
        )

        val parsed = parseIndoorBikeData(payload)

        assertTrue(parsed.valid)
        assertEquals(1000, parsed.totalDistanceMeters)
    }

    @Test
    fun leavesTotalDistanceNullWhenFieldIsNotPresent() {
        val payload = byteArrayOf(
            0x00, 0x00, // flags: no optional fields
            0x00, 0x00, // instantaneous speed (required field)
        )

        val parsed = parseIndoorBikeData(payload)

        assertTrue(parsed.valid)
        assertNull(parsed.totalDistanceMeters)
    }
}
