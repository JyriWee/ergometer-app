package com.example.ergometerapp.ble

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.ergometerapp.ble.debug.FtmsDebugBuffer
import com.example.ergometerapp.ble.debug.FtmsDebugEvent

/**
 * Serializes FTMS Control Point commands and matches them to response opcodes.
 *
 * BLE Control Point procedures are defined as request/response; devices may drop or
 * reject concurrent commands. This controller enforces a single in-flight command,
 * applies a "last wins" policy for target power updates, and releases the BUSY
 * state on either a response or a timeout to avoid permanent deadlock.
 */
class FtmsController(
    private val writeControlPoint: (ByteArray) -> Unit
) {

    private var hasStopped = false

    private var pendingReset = false

    private var commandState = FtmsCommandState.IDLE
    private var pendingTargetPowerWatts: Int? = null

    private val handler = Handler(Looper.getMainLooper())

    private var lastSentTargetPower: Int? = null

    private val commandTimeoutMs = 2500L

    private var timeoutRunnable: Runnable? = null

    @Suppress("unused")
    /**
     * Indicates whether the controller is ready to send commands.
     *
     * TODO: Wire this to actual readiness (e.g., Control Point CCCD enabled).
     */
    fun isReady(): Boolean = true

    private fun sendCommand(payload: ByteArray, label: String) {
        if (commandState == FtmsCommandState.BUSY) {
            Log.w("FTMS", "Command ignored (BUSY): $label payload=${payload.joinToString()}")
            return
        }

        commandState = FtmsCommandState.BUSY
        startTimeoutTimer()

        Log.d("FTMS", "Sending: $label payload=${payload.joinToString()}")
        writeControlPoint(payload)
        // BUSY is released only after a Control Point response or timeout.
    }

    /**
     * Requests exclusive control of the trainer.
     *
     * Some FTMS devices require this before accepting other Control Point commands.
     */
    fun requestControl() {
        if (hasStopped) {
            Log.d("FTMS", "requestControl() ignored (already stopped)")
            return
        }

        val payload = byteArrayOf(0x00.toByte())
        sendCommand(payload, "requestControl")
    }

    @Suppress("unused")
    /**
     * Sets a device-specific resistance level.
     *
     * This is optional in FTMS and may be ignored by devices that only support
     * power-based targets.
     */
    fun setResistanceLevel(level: Int) {
        val payload = byteArrayOf(0x04.toByte(), level.toByte())
        sendCommand(payload, "setResistanceLevel($level)")
    }

    /**
     * Sets target power in watts (clamped to a safe range for FTMS encoding).
     *
     * "Last wins" ensures rapid UI updates do not queue stale targets while the
     * device is processing the previous command.
     */
    fun setTargetPower(watts: Int) {
        val w = watts.coerceIn(0, 2000)

        if (commandState == FtmsCommandState.BUSY) return
        if (lastSentTargetPower == w) return

        lastSentTargetPower = w

        FtmsDebugBuffer.record(
            FtmsDebugEvent.TargetPowerIssued(System.currentTimeMillis(), w)
        )
        handler.postDelayed(
            {
                FtmsDebugBuffer.record(
                    FtmsDebugEvent.ObservationEnded(System.currentTimeMillis())
                )
            },
            1000L
        )

        // "Last wins" avoids a backlog that FTMS devices are unlikely to process.
        if (commandState == FtmsCommandState.BUSY) {
            pendingTargetPowerWatts = w
            Log.d("FTMS", "Queued target power (last wins): $w W")
            return
        }

        val lo = (w and 0xFF).toByte()
        val hi = ((w shr 8) and 0xFF).toByte()
        val payload = byteArrayOf(0x05.toByte(), lo, hi)

        sendCommand(payload, "setTargetPower($w)")
    }

    /**
     * Stops the current workout session if the device supports it.
     */
    fun stop() {
        if (hasStopped) {
            Log.d("FTMS", "stop() ignored (already stopped)")
            return
        }
        hasStopped = true
        val payload = byteArrayOf(0x08.toByte(), 0x01.toByte())
        sendCommand(payload, "stop")
    }

    @Suppress("unused")
    /**
     * Pauses the current workout session if the device supports it.
     */
    fun pause() {
        if (hasStopped) {
            Log.d("FTMS", "pause() ignored (already stopped)")
            return
        }
        if (commandState == FtmsCommandState.BUSY) {
            Log.d("FTMS", "pause() ignored (BUSY)")
            return
        }
        val payload = byteArrayOf(0x08.toByte(), 0x02.toByte())
        sendCommand(payload, "pause")
    }


    /**
     * Resets the device state.
     *
     * Reset is queued while BUSY to preserve the invariant of a single in-flight
     * Control Point command.
     */
    fun reset() {

        hasStopped = false
        lastSentTargetPower = null

        if (commandState == FtmsCommandState.BUSY) {
            pendingReset = true
            Log.d("FTMS", "Queued reset (pending)")
            return
        }
        val payload = byteArrayOf(0x01.toByte())
        sendCommand(payload, "reset")
    }

    /**
     * Call this from FtmsBleClient when you receive a Control Point response packet (0x80 ...).
     * This releases the BUSY state deterministically.
     */
    fun onControlPointResponse(requestOpcode: Int, resultCode: Int) {
        cancelTimeoutTimer()

        val ok = (resultCode == 0x01)
        commandState = if (ok) FtmsCommandState.SUCCESS else FtmsCommandState.ERROR
        Log.d("FTMS", "CP response opcode=$requestOpcode result=$resultCode state=$commandState")

        // Release for the next command after a definitive response.
        commandState = FtmsCommandState.IDLE

        if (pendingReset) {
            pendingReset = false
            Log.d("FTMS", "Sending queued reset")
            reset()
            return
        }

        // Apply the latest target if it was updated while BUSY.
        val pending = pendingTargetPowerWatts
        if (pending != null) {
            pendingTargetPowerWatts = null
            Log.d("FTMS", "Sending queued target power: $pending W")
            setTargetPower(pending) // this will go through now because commandState is IDLE
        }

    }

    private fun startTimeoutTimer() {
        cancelTimeoutTimer()

        timeoutRunnable = Runnable {
            cancelTimeoutTimer()
            // If the device never responds, release BUSY to avoid a permanent lock.
            if (commandState == FtmsCommandState.BUSY) {
                Log.w("FTMS", "Control Point command timeout -> forcing IDLE")
                commandState = FtmsCommandState.IDLE

                if (pendingReset) {
                    pendingReset = false
                    Log.d("FTMS", "Sending queued reset after timeout")
                    reset()
                } else {
                    val pending = pendingTargetPowerWatts
                    if (pending != null) {
                        pendingTargetPowerWatts = null
                        Log.d("FTMS", "Sending queued target power after timeout: $pending W")
                        setTargetPower(pending)
                    }
                }
            }
        }

        handler.postDelayed(timeoutRunnable!!, commandTimeoutMs)
    }

    private fun cancelTimeoutTimer() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null
    }

}
