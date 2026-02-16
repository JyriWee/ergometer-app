package com.example.ergometerapp.ble

import android.os.Handler
import kotlin.math.min

/**
 * Coordinates bounded reconnect attempts for the HR BLE link.
 *
 * Invariants:
 * - reconnect attempts are scheduled only for an active requested MAC
 * - explicit close suppresses all pending/future reconnect attempts
 * - backoff resets after a successful connection
 */
internal class HrReconnectCoordinator(
    private val handler: Handler,
    private val onReconnectRequested: (String) -> Unit,
    private val onDisconnected: () -> Unit,
    private val maxReconnectAttempts: Int = DEFAULT_MAX_RECONNECT_ATTEMPTS,
    private val reconnectBaseDelayMs: Long = DEFAULT_RECONNECT_BASE_DELAY_MS,
    private val reconnectMaxDelayMs: Long = DEFAULT_RECONNECT_MAX_DELAY_MS,
) {
    private var requestedMac: String? = null
    private var explicitCloseRequested = false
    private var reconnectAttempt = 0
    private var reconnectRunnable: Runnable? = null

    fun onConnectRequested(mac: String) {
        requestedMac = mac
        explicitCloseRequested = false
        reconnectAttempt = 0
        cancelPendingReconnect()
    }

    fun onConnected() {
        reconnectAttempt = 0
        cancelPendingReconnect()
    }

    fun onGattDisconnected() {
        onDisconnected()
        scheduleReconnectIfEligible()
    }

    fun onConnectAttemptFailed() {
        scheduleReconnectIfEligible()
    }

    fun onCloseRequested() {
        explicitCloseRequested = true
        requestedMac = null
        reconnectAttempt = 0
        cancelPendingReconnect()
    }

    private fun scheduleReconnectIfEligible() {
        if (explicitCloseRequested) return
        if (reconnectRunnable != null) return

        val mac = requestedMac ?: return
        if (reconnectAttempt >= maxReconnectAttempts) return

        reconnectAttempt += 1
        val delayMs = reconnectDelayForAttempt(reconnectAttempt)
        reconnectRunnable = Runnable {
            reconnectRunnable = null
            if (explicitCloseRequested) return@Runnable
            onReconnectRequested(mac)
        }
        handler.postDelayed(reconnectRunnable!!, delayMs)
    }

    private fun cancelPendingReconnect() {
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        reconnectRunnable = null
    }

    private fun reconnectDelayForAttempt(attempt: Int): Long {
        val safeAttempt = attempt.coerceAtLeast(1)
        val candidate = reconnectBaseDelayMs * (1L shl (safeAttempt - 1))
        return min(candidate, reconnectMaxDelayMs)
    }

    companion object {
        private const val DEFAULT_MAX_RECONNECT_ATTEMPTS = 4
        private const val DEFAULT_RECONNECT_BASE_DELAY_MS = 1000L
        private const val DEFAULT_RECONNECT_MAX_DELAY_MS = 8000L
    }
}
