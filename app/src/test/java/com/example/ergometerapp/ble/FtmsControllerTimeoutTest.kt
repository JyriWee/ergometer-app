package com.example.ergometerapp.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class FtmsControllerTimeoutTest {
    @Test
    fun requestControlTimeoutReportsRequestOpcode() {
        val timedOutOpcodes = mutableListOf<Int?>()
        val handler = ManualHandler()
        val controller = FtmsController(
            writeControlPoint = { true },
            onCommandTimeout = { opcode -> timedOutOpcodes += opcode },
            handler = handler,
        )

        controller.setTransportReady(true)
        controller.requestControl()
        assertTrue(timedOutOpcodes.isEmpty())
        handler.advanceBy(2500L)

        assertEquals(listOf(0x00), timedOutOpcodes)
    }

    @Test
    fun writeStartFailureDoesNotTriggerTimeoutCallback() {
        val timedOutOpcodes = mutableListOf<Int?>()
        val handler = ManualHandler()
        val controller = FtmsController(
            writeControlPoint = { false },
            onCommandTimeout = { opcode -> timedOutOpcodes += opcode },
            handler = handler,
        )

        controller.setTransportReady(true)
        controller.requestControl()
        handler.advanceBy(3000L)

        assertTrue(timedOutOpcodes.isEmpty())
    }

    @Test
    fun mismatchedResponseDoesNotReleaseBusyAndTimesOutExpectedCommand() {
        val writes = mutableListOf<ByteArray>()
        val timedOutOpcodes = mutableListOf<Int?>()
        val unexpectedResponses = mutableListOf<UnexpectedResponse>()
        val handler = ManualHandler()
        val controller = FtmsController(
            writeControlPoint = { payload ->
                writes += payload.copyOf()
                true
            },
            onCommandTimeout = { opcode -> timedOutOpcodes += opcode },
            onUnexpectedControlPointResponse = { expectedOpcode, receivedOpcode, resultCode, reason ->
                unexpectedResponses += UnexpectedResponse(
                    expectedOpcode = expectedOpcode,
                    receivedOpcode = receivedOpcode,
                    resultCode = resultCode,
                    reason = reason,
                )
            },
            handler = handler,
        )

        controller.setTransportReady(true)
        controller.requestControl()
        assertEquals(1, writes.size)
        assertEquals(0x00, writes.first().first().toInt() and 0xFF)

        controller.onControlPointResponse(requestOpcode = 0x05, resultCode = 0x01)
        assertEquals(1, unexpectedResponses.size)
        assertEquals(
            FtmsUnexpectedControlPointResponseReason.OPCODE_MISMATCH,
            unexpectedResponses.first().reason,
        )
        assertEquals(0x00, unexpectedResponses.first().expectedOpcode)

        controller.setTargetPower(250)
        assertEquals(1, writes.size)

        handler.advanceBy(2500L)
        assertEquals(listOf(0x00), timedOutOpcodes)
        assertEquals(2, writes.size)
        assertEquals(0x05, writes[1].first().toInt() and 0xFF)
    }

    @Test
    fun responseWithoutInFlightSignalsUnexpected() {
        val unexpectedResponses = mutableListOf<UnexpectedResponse>()
        val controller = FtmsController(
            writeControlPoint = { true },
            onUnexpectedControlPointResponse = { expectedOpcode, receivedOpcode, resultCode, reason ->
                unexpectedResponses += UnexpectedResponse(
                    expectedOpcode = expectedOpcode,
                    receivedOpcode = receivedOpcode,
                    resultCode = resultCode,
                    reason = reason,
                )
            },
        )

        controller.setTransportReady(true)
        controller.onControlPointResponse(requestOpcode = 0x00, resultCode = 0x01)

        assertEquals(1, unexpectedResponses.size)
        assertEquals(
            FtmsUnexpectedControlPointResponseReason.NO_COMMAND_IN_FLIGHT,
            unexpectedResponses.first().reason,
        )
        assertNull(unexpectedResponses.first().expectedOpcode)
    }

    private data class UnexpectedResponse(
        val expectedOpcode: Int?,
        val receivedOpcode: Int,
        val resultCode: Int,
        val reason: FtmsUnexpectedControlPointResponseReason,
    )

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
