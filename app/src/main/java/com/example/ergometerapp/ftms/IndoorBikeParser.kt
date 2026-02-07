package com.example.ergometerapp.ftms

import com.example.ergometerapp.ble.debug.FtmsDebugBuffer
import com.example.ergometerapp.ble.debug.FtmsDebugEvent

/**
 * Parser for FTMS Indoor Bike Data (UUID 0x2AD2).
 *
 * Field presence is driven by the flags bitmask. Some devices deviate from the
 * spec and still include certain fields even when the corresponding flag is
 * cleared; this parser tolerates such behavior to keep telemetry flowing.
 */
/**
 * Parses an FTMS Indoor Bike Data packet.
 *
 * Returns [IndoorBikeData.valid] false when the payload is malformed or too
 * short, rather than throwing and tearing down the BLE callback.
 */
fun parseIndoorBikeData(bytes: ByteArray): IndoorBikeData {

    var offset = 0

    fun u8(): Int =
        bytes[offset++].toInt() and 0xFF

    fun u16(): Int {
        val v = (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8)
        offset += 2
        return v
    }

    fun u24(): Int {
        val v = (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16)
        offset += 3
        return v
    }

    return try {
        // Flags are little-endian per FTMS and gate optional fields.
        val flags = u16()
        fun flag(bit: Int) = (flags and (1 shl bit)) != 0

        // Instantaneous Speed is always present per spec.
        val instantSpeed = u16() / 100.0

        // Some devices (e.g., Tunturi E80) send Average Speed even with flag(0) cleared.
        val avgSpeed =
            if (flag(0)) u16() / 100.0 else u16() / 100.0

        // Cadence fields are optional; missing fields are preserved as null.
        val instantCadence =
            if (flag(1)) u16() / 2.0 else null

        val avgCadence =
            if (flag(2)) u16() / 2.0 else null

        val distance =
            if (flag(3)) u24() else null

        val resistance =
            if (flag(4)) u16() else null

        val instantPower =
            if (flag(5)) u16() else null
        instantPower?.let { FtmsDebugBuffer.record(FtmsDebugEvent.PowerSample(System.currentTimeMillis(), it)) }

        val avgPower =
            if (flag(6)) u16() else null

        val totalEnergy =
            if (flag(7)) u16() else null

        val energyPerHour =
            if (flag(8)) u16() else null

        val energyPerMinute =
            if (flag(9)) u8() else null

        val heartRate =
            if (flag(10)) u8() else null

        val met =
            if (flag(11)) u8() / 10.0 else null

        val elapsed =
            if (flag(12)) u16() else null

        val remaining =
            if (flag(13)) u16() else null

        IndoorBikeData(
            valid = true,
            instantaneousSpeedKmh = instantSpeed,
            averageSpeedKmh = avgSpeed,
            instantaneousCadenceRpm = instantCadence,
            averageCadenceRpm = avgCadence,
            totalDistanceMeters = distance,
            resistanceLevel = resistance,
            instantaneousPowerW = instantPower,
            averagePowerW = avgPower,
            totalEnergyKcal = totalEnergy,
            energyPerHourKcal = energyPerHour,
            energyPerMinuteKcal = energyPerMinute,
            heartRateBpm = heartRate,
            metabolicEquivalent = met,
            elapsedTimeSeconds = elapsed,
            remainingTimeSeconds = remaining
        )

    } catch (e: Exception) {
        // TODO: Consider returning partial data when only trailing fields are missing.
        IndoorBikeData(
            valid = false,
            instantaneousSpeedKmh = null,
            averageSpeedKmh = null,
            instantaneousCadenceRpm = null,
            averageCadenceRpm = null,
            totalDistanceMeters = null,
            resistanceLevel = null,
            instantaneousPowerW = null,
            averagePowerW = null,
            totalEnergyKcal = null,
            energyPerHourKcal = null,
            energyPerMinuteKcal = null,
            heartRateBpm = null,
            metabolicEquivalent = null,
            elapsedTimeSeconds = null,
            remainingTimeSeconds = null
        )
    }
}
