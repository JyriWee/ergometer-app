package com.example.ergometerapp.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HrReconnectCoordinatorTest {

    @Test
    fun unexpectedDisconnectSchedulesBoundedReconnectBackoff() {
        val reconnectRequests = mutableListOf<String>()
        val handler = ManualHandler()
        val coordinator = HrReconnectCoordinator(
            handler = handler,
            onReconnectRequested = { mac -> reconnectRequests += mac },
            onDisconnected = {},
            maxReconnectAttempts = 3,
            reconnectBaseDelayMs = 1000L,
            reconnectMaxDelayMs = 8000L,
        )

        coordinator.onConnectRequested("AA:BB:CC:DD:EE:FF")
        coordinator.onGattDisconnected()

        handler.advanceBy(999L)
        assertTrue(reconnectRequests.isEmpty())
        handler.advanceBy(1L)
        assertEquals(listOf("AA:BB:CC:DD:EE:FF"), reconnectRequests)

        coordinator.onConnectAttemptFailed()
        handler.advanceBy(1999L)
        assertEquals(1, reconnectRequests.size)
        handler.advanceBy(1L)
        assertEquals(2, reconnectRequests.size)

        coordinator.onConnectAttemptFailed()
        handler.advanceBy(3999L)
        assertEquals(2, reconnectRequests.size)
        handler.advanceBy(1L)
        assertEquals(3, reconnectRequests.size)

        coordinator.onConnectAttemptFailed()
        handler.advanceBy(10000L)
        assertEquals(3, reconnectRequests.size)
    }

    @Test
    fun explicitCloseCancelsPendingReconnect() {
        val reconnectRequests = mutableListOf<String>()
        val disconnectedEvents = mutableListOf<Unit>()
        val handler = ManualHandler()
        val coordinator = HrReconnectCoordinator(
            handler = handler,
            onReconnectRequested = { mac -> reconnectRequests += mac },
            onDisconnected = { disconnectedEvents += Unit },
            maxReconnectAttempts = 3,
            reconnectBaseDelayMs = 1000L,
            reconnectMaxDelayMs = 8000L,
        )

        coordinator.onConnectRequested("AA:BB:CC:DD:EE:FF")
        coordinator.onGattDisconnected()
        coordinator.onCloseRequested()
        handler.advanceBy(2000L)

        assertEquals(1, disconnectedEvents.size)
        assertTrue(reconnectRequests.isEmpty())
    }

    @Test
    fun successfulConnectionResetsBackoffToBaseDelay() {
        val reconnectRequests = mutableListOf<String>()
        val handler = ManualHandler()
        val coordinator = HrReconnectCoordinator(
            handler = handler,
            onReconnectRequested = { mac -> reconnectRequests += mac },
            onDisconnected = {},
            maxReconnectAttempts = 4,
            reconnectBaseDelayMs = 1000L,
            reconnectMaxDelayMs = 8000L,
        )

        coordinator.onConnectRequested("AA:BB:CC:DD:EE:FF")
        coordinator.onGattDisconnected()
        handler.advanceBy(1000L)
        assertEquals(1, reconnectRequests.size)

        coordinator.onConnected()
        coordinator.onGattDisconnected()
        handler.advanceBy(999L)
        assertEquals(1, reconnectRequests.size)
        handler.advanceBy(1L)
        assertEquals(2, reconnectRequests.size)
    }

    private class ManualHandler : android.os.Handler(android.os.Looper.getMainLooper()) {
        private data class ScheduledRunnable(
            val runnable: Runnable,
            val runAtMs: Long,
            val order: Long,
        )

        private val queue = mutableListOf<ScheduledRunnable>()
        private var nextOrder = 0L
        private var nowMs = 0L

        override fun post(runnable: Runnable): Boolean {
            queue += ScheduledRunnable(
                runnable = runnable,
                runAtMs = nowMs,
                order = nextOrder++,
            )
            return true
        }

        override fun postDelayed(runnable: Runnable, delayMillis: Long): Boolean {
            queue += ScheduledRunnable(
                runnable = runnable,
                runAtMs = nowMs + delayMillis.coerceAtLeast(0L),
                order = nextOrder++,
            )
            return true
        }

        override fun removeCallbacks(runnable: Runnable) {
            queue.removeAll { it.runnable === runnable }
        }

        fun advanceBy(deltaMs: Long) {
            require(deltaMs >= 0L) { "Delta must be non-negative." }
            val targetMs = nowMs + deltaMs
            while (true) {
                val next = queue
                    .filter { it.runAtMs <= targetMs }
                    .minWithOrNull(compareBy<ScheduledRunnable> { it.runAtMs }.thenBy { it.order })
                    ?: break
                queue.remove(next)
                nowMs = next.runAtMs
                next.runnable.run()
            }
            nowMs = targetMs
        }
    }
}
