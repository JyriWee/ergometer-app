package com.example.ergometerapp.ftms

/**
 * FTMS Indoor Bike Data (UUID 0x2AD2)
 * Parseri perustuu Bluetooth FTMS -spesifikaatioon ja
 * käyttäjän nRF Connect -lokissa vahvistettuun datamuotoon.
 */

/*
data class IndoorBikeData(
    val valid: Boolean,

    val instantaneousSpeedKmh: Double?,   // km/h
    val averageSpeedKmh: Double?,

    val instantaneousCadenceRpm: Double?, // rpm
    val averageCadenceRpm: Double?,

    val totalDistanceMeters: Int?,

    val resistanceLevel: Int?,

    val instantaneousPowerW: Int?,
    val averagePowerW: Int?,

    val totalEnergyKcal: Int?,
    val energyPerHourKcal: Int?,
    val energyPerMinuteKcal: Int?,

    val heartRateBpm: Int?,

    val metabolicEquivalent: Double?,     // MET

    val elapsedTimeSeconds: Int?,
    val remainingTimeSeconds: Int?
)
*/
/**
 * Parsii FTMS Indoor Bike Data -paketin.
 * Ei heitä poikkeuksia normaalivirheissä → valid=false.
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
        // Flags (2 bytes, little-endian)
        val flags = u16()
        fun flag(bit: Int) = (flags and (1 shl bit)) != 0

        // 0) Instantaneous Speed (always present), unit: 0.01 km/h
        val instantSpeed = u16() / 100.0

        // Tunturi E80: Average Speed appears always, even if flag(0) == false
        val avgSpeed =
            if (flag(0)) u16() / 100.0 else u16() / 100.0

        // 1) Instantaneous Cadence, unit: 0.5 rpm
        val instantCadence =
            if (flag(1)) u16() / 2.0 else null

        // 2) Average Cadence, unit: 0.5 rpm
        val avgCadence =
            if (flag(2)) u16() / 2.0 else null

        // 3) Total Distance, unit: meter
        val distance =
            if (flag(3)) u24() else null

        // 4) Resistance Level
        val resistance =
            if (flag(4)) u16() else null

        // 5) Instantaneous Power, unit: watt
        val instantPower =
            if (flag(5)) u16() else null

        // 6) Average Power, unit: watt
        val avgPower =
            if (flag(6)) u16() else null

        // 7) Total Energy, unit: kcal
        val totalEnergy =
            if (flag(7)) u16() else null

        // 8) Energy Per Hour, unit: kcal/hour
        val energyPerHour =
            if (flag(8)) u16() else null

        // 9) Energy Per Minute, unit: kcal/min
        val energyPerMinute =
            if (flag(9)) u8() else null

        // 10) Heart Rate, unit: bpm
        val heartRate =
            if (flag(10)) u8() else null

        // 11) Metabolic Equivalent (MET), unit: 0.1
        val met =
            if (flag(11)) u8() / 10.0 else null

        // 12) Elapsed Time, unit: seconds
        val elapsed =
            if (flag(12)) u16() else null

        // 13) Remaining Time, unit: seconds
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
