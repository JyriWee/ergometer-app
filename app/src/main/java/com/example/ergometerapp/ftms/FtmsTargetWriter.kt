package com.example.ergometerapp.ftms

/**
 * Narrow output interface for ERG target control.
 *
 * - watts != null → write ERG target
 * - watts == null → clear ERG / release control
 */
fun interface FtmsTargetWriter {
    fun setTargetWatts(watts: Int?)
}
