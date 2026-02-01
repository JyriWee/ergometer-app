package com.example.ergometerapp

import com.example.ergometerapp.ftms.IndoorBikeData

data class SessionState(
    val bike: IndoorBikeData?,
    val heartRateBpm: Int?,            // vyö
    val effectiveHeartRateBpm: Int?,   // uusi
    val timestampMillis: Long,
    val durationSeconds: Int
)

