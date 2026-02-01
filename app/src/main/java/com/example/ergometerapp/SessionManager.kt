package com.example.ergometerapp.session

import com.example.ergometerapp.SessionState
import com.example.ergometerapp.ftms.IndoorBikeData

enum class SessionPhase {
    IDLE,
    RUNNING,
    STOPPED
}
/**
 * SessionManager
 *
 * Vastaa yhden harjoitussession elinkaaresta:
 * - START / RUNNING / STOPPED
 * - kerää FTMS- ja HR-datan
 * - tuottaa session summaryn
 *
 * Ei sisällä UI-logiikkaa.
 * Ei tiedä BLE-yksityiskohdista.
 */
class SessionManager(
    private val context: android.content.Context,
    private val onStateUpdated: (SessionState) -> Unit
) {
    var lastSummary: SessionSummary? = null
        private set
    private val powerSamples = mutableListOf<Int>()
    private val cadenceSamples = mutableListOf<Int>()
    private val heartRateSamples = mutableListOf<Int>()
    private var latestBikeData: IndoorBikeData? = null
    private var latestHeartRate: Int? = null
    private var sessionStartMillis: Long? = null

    private var durationAtStopSec: Int? = null

    private var lastDistanceMeters: Int? = null

    private var sessionPhase: SessionPhase = SessionPhase.IDLE

    // UI käyttää tätä tilan näyttämiseen ja nappien enable/disable-logiikkaan
    fun getPhase(): SessionPhase = sessionPhase

    fun updateBikeData(bikeData: IndoorBikeData) {
        latestBikeData = bikeData

        if (sessionPhase == SessionPhase.RUNNING) {

            // 1) Kerää power (vain järkevät arvot)
            latestBikeData?.instantaneousPowerW
                ?.takeIf { it > 0 }
                ?.let { powerSamples.add(it) }

            // 2) Päivitä viimeisin matka
            latestBikeData?.totalDistanceMeters
                ?.let { lastDistanceMeters = it }

            //// 3)  HR-prioriteetti:
            //// - Erillinen HR-vyö, jos saatavilla
            //// -  Ergometrin sisäinen HR (kahva-anturi)
            //// -  null, jos kumpikaan ei ole käytettävissä
            if (latestHeartRate == null) {
                latestBikeData?.heartRateBpm
                    ?.takeIf { it in 30..220 }
                    ?.let { heartRateSamples.add(it) }
            }
            latestBikeData?.instantaneousCadenceRpm?.let { cadenceSamples.add(it.toInt()) }
        }

        emitState()
    }

    fun updateHeartRate(hr: Int?) {
        latestHeartRate = hr

        if (sessionPhase == SessionPhase.RUNNING) {
            hr?.let { heartRateSamples.add(it) }
        }

        emitState()
    }


    private fun emitState() {

        val durationSec =
            when (sessionPhase) {
                SessionPhase.RUNNING -> {
                    val start = sessionStartMillis
                    if (start != null)
                        ((System.currentTimeMillis() - start) / 1000).toInt()
                    else 0
                }
                SessionPhase.STOPPED -> durationAtStopSec ?: 0
                else -> 0
            }

        val effectiveHr =
            latestHeartRate
                ?: latestBikeData?.heartRateBpm
                    ?.takeIf { it in 30..220 }

        val state = SessionState(
            bike = latestBikeData,
            heartRateBpm = latestHeartRate,
            effectiveHeartRateBpm = effectiveHr,
            timestampMillis = System.currentTimeMillis(),
            durationSeconds = durationSec
        )

        onStateUpdated(state)
    }

    fun startSession() {
        sessionPhase = SessionPhase.RUNNING
        sessionStartMillis = System.currentTimeMillis()

        powerSamples.clear()
        cadenceSamples.clear()
        heartRateSamples.clear()
        lastDistanceMeters = null
        lastSummary = null
        latestBikeData = null
        latestHeartRate = null
        lastDistanceMeters = null
        durationAtStopSec = null
        emitState()
    }

    fun stopSession() {
        if (sessionPhase != SessionPhase.RUNNING) return

        val start = sessionStartMillis ?: return

        val durationSec = ((System.currentTimeMillis() - start) / 1000).toInt()

        durationAtStopSec = durationSec

        val avgPower =
            powerSamples.takeIf { it.isNotEmpty() }?.average()?.toInt()

        val maxPower =
            powerSamples.maxOrNull()

        val avgCadence =
            cadenceSamples.takeIf { it.isNotEmpty() }?.average()?.toInt()

        val maxCadence =
            cadenceSamples.maxOrNull()

        val avgHeartRate =
            heartRateSamples.takeIf { it.isNotEmpty() }?.average()?.toInt()

        val maxHeartRate =
            heartRateSamples.maxOrNull()

        val summary = SessionSummary(
            durationSeconds = durationSec,
            avgPower = avgPower,
            maxPower = maxPower,
            avgCadence = avgCadence,
            maxCadence = maxCadence,
            avgHeartRate = avgHeartRate,
            maxHeartRate = maxHeartRate,
            distanceMeters = lastDistanceMeters
        )

        lastSummary = summary
        sessionPhase = SessionPhase.STOPPED

        lastSummary?.let {
            SessionStorage.save(context, it)
        }
    }
}


