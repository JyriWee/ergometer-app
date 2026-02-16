package com.example.ergometerapp.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtmsControllerTimeoutTest {
    @Test
    fun requestControlTimeoutReportsRequestOpcode() {
        var timedOutOpcode: Int? = null
        val controller = FtmsController(
            writeControlPoint = { true },
            onCommandTimeout = { opcode -> timedOutOpcode = opcode },
        )

        controller.setTransportReady(true)
        controller.requestControl()

        assertEquals(0x00, timedOutOpcode)
    }

    @Test
    fun writeStartFailureDoesNotTriggerTimeoutCallback() {
        var timedOutOpcode: Int? = null
        val controller = FtmsController(
            writeControlPoint = { false },
            onCommandTimeout = { opcode -> timedOutOpcode = opcode },
        )

        controller.setTransportReady(true)
        controller.requestControl()

        assertNull(timedOutOpcode)
    }
}
