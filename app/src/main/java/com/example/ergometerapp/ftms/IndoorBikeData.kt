package com.example.ergometerapp.ftms

data class IndoorBikeData(
    val valid: Boolean,
    val instantaneousSpeedKmh: Double?,
    val averageSpeedKmh: Double?,
    val instantaneousCadenceRpm: Double?,
    val averageCadenceRpm: Double?,
    val totalDistanceMeters: Int?,
    val resistanceLevel: Int?,
    val instantaneousPowerW: Int?,
    val averagePowerW: Int?,
    val totalEnergyKcal: Int?,
    val energyPerHourKcal: Int?,
    val energyPerMinuteKcal: Int?,
    val heartRateBpm: Int?,
    val metabolicEquivalent: Double?,
    val elapsedTimeSeconds: Int?,
    val remainingTimeSeconds: Int?
)