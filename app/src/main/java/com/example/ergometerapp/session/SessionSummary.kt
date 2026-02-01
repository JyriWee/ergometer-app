package com.example.ergometerapp.session

data class SessionSummary(
    val durationSeconds: Int,
    val avgPower: Int?,
    val maxPower: Int?,
    val avgCadence: Int?,
    val maxCadence: Int?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val distanceMeters: Int?
)
