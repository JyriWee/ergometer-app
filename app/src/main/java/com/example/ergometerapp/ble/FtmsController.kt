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
    private val writeControlPoint: (ByteArray) -> Boolean,
    private val onStopAcknowledged: () -> Unit = {},
    private val onCommandTimeout: (requestOpcode: Int?) -> Unit = {}
) {

    private var transportReady = false

    private var hasStopped = false

    private var pendingReset = false

    private var commandState = FtmsCommandState.IDLE
    private var pendingTargetPowerWatts: Int? = null
    private var pendingStopWorkout = false

    private val handler = Handler(Looper.getMainLooper())

    private var lastSentTargetPower: Int? = null

    private val commandTimeoutMs = 2500L

    private var timeoutRunnable: Runnable? = null
    private var inFlightRequestOpcode: Int? = null

    private fun dumpControllerState(event: String) {
        Log.d(
            "FTMS",
            "CTRL_DUMP event=$event state=$commandState transportReady=$transportReady " +
                "hasStopped=$hasStopped " +
                "pendingTarget=$pendingTargetPowerWatts pendingReset=$pendingReset " +
                "pendingStop=$pendingStopWorkout lastSentTarget=$lastSentTargetPower " +
                "inFlightOpcode=$inFlightRequestOpcode " +
                "timeoutArmed=${timeoutRunnable != null}"
        )
    }

    /**
     * Indicates whether the controller is ready to send commands.
     */
    fun isReady(): Boolean = transportReady

    /**
     * Updates command transport readiness from BLE setup state.
     *
     * Callers must set this true only after Control Point indication setup is
     * complete, and false on disconnect/teardown.
     */
    fun setTransportReady(ready: Boolean) {
        transportReady = ready
        dumpControllerState("setTransportReady(ready=$ready)")
    }

    private fun sendCommand(payload: ByteArray, label: String) {
        if (!transportReady) {
            Log.w("FTMS", "Command ignored (not ready): $label payload=${payload.joinToString()}")
            dumpControllerState("sendCommandIgnoredNotReady(label=$label)")
            return
        }

        if (commandState == FtmsCommandState.BUSY) {
            Log.w("FTMS", "Command ignored (BUSY): $label payload=${payload.joinToString()}")
            dumpControllerState("sendCommandIgnoredBusy(label=$label)")
            return
        }

        Log.d("FTMS", "Sending: $label payload=${payload.joinToString()}")
        val writeStarted = writeControlPoint(payload)
        if (!writeStarted) {
            Log.w(
                "FTMS",
                "Command send failed (write not started): $label payload=${payload.joinToString()}",
            )
            dumpControllerState("sendCommandWriteFailed(label=$label)")
            return
        }

        commandState = FtmsCommandState.BUSY
        inFlightRequestOpcode = payload.firstOrNull()?.toInt()?.and(0xFF)
        startTimeoutTimer()
        dumpControllerState("sendCommandSent(label=$label,writeStarted=true)")
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
            dumpControllerState("requestControlIgnoredAlreadyStopped")
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

        if (commandState == FtmsCommandState.BUSY) {
            pendingTargetPowerWatts = w
            Log.d("FTMS", "Queued target power (last wins): $w W")
            dumpControllerState("setTargetPowerQueuedBusy(watts=$w)")
            return
        }

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
            dumpControllerState("setTargetPowerQueuedBusyPostObservation(watts=$w)")
            return
        }

        val lo = (w and 0xFF).toByte()
        val hi = ((w shr 8) and 0xFF).toByte()
        val payload = byteArrayOf(0x05.toByte(), lo, hi)

        sendCommand(payload, "setTargetPower($w)")
    }

    /**
     * Stops the current workout session if the device supports it.
     *
     * STOP is serialized through the same Control Point pipeline as other
     * commands. If another command is in flight, STOP is queued and sent as
     * soon as BUSY clears.
     */
    fun stopWorkout() {
        if (hasStopped) {
            Log.d("FTMS", "stopWorkout() ignored (already stopped)")
            dumpControllerState("stopWorkoutIgnoredAlreadyStopped")
            return
        }
        hasStopped = true

        // STOP is terminal for queued work in this lifecycle.
        pendingTargetPowerWatts = null
        pendingReset = false
        pendingStopWorkout = true

        if (commandState == FtmsCommandState.BUSY) {
            Log.d("FTMS", "Queued stopWorkout (pending)")
            dumpControllerState("stopWorkoutQueuedBusy")
            return
        }

        sendStopWorkout()
    }

    private fun sendStopWorkout() {
        pendingStopWorkout = false
        dumpControllerState("sendStopWorkout")
        val payload = byteArrayOf(0x08.toByte(), 0x01.toByte())
        sendCommand(payload, "stopWorkout")
    }


    /**
     * Clears the active ERG target without marking the session as hard-stopped.
     *
     * FTMS ERG release on this trainer is done by setting target power to zero
     * (0x05 0x00 0x00), which keeps telemetry/session running.
     */
    fun clearTargetPower() {
        setTargetPower(0)
    }

    /**
     * Compatibility alias for soft release paths.
     *
     * This method must remain STOP-free; explicit workout stop is handled only by
     * [stopWorkout].
     */
    fun releaseControl() {
        clearTargetPower()
    }

    @Suppress("unused")
    /**
     * Pauses the current workout session if the device supports it.
     */
    fun pause() {
        if (hasStopped) {
            Log.d("FTMS", "pause() ignored (already stopped)")
            dumpControllerState("pauseIgnoredAlreadyStopped")
            return
        }
        if (commandState == FtmsCommandState.BUSY) {
            Log.d("FTMS", "pause() ignored (BUSY)")
            dumpControllerState("pauseIgnoredBusy")
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
        pendingStopWorkout = false

        if (commandState == FtmsCommandState.BUSY) {
            pendingReset = true
            Log.d("FTMS", "Queued reset (pending)")
            dumpControllerState("resetQueuedBusy")
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
        inFlightRequestOpcode = null

        val ok = (resultCode == 0x01)
        commandState = if (ok) FtmsCommandState.SUCCESS else FtmsCommandState.ERROR
        Log.d("FTMS", "CP response opcode=$requestOpcode result=$resultCode state=$commandState")
        dumpControllerState("onControlPointResponse(op=$requestOpcode,result=$resultCode,ok=$ok)")
        if (requestOpcode == 0x08 && ok) {

            onStopAcknowledged()
        }

        // Release for the next command after a definitive response.
        commandState = FtmsCommandState.IDLE

        if (pendingStopWorkout) {
            Log.d("FTMS", "Sending queued stopWorkout")
            dumpControllerState("onControlPointResponseSendingQueuedStop")
            sendStopWorkout()
            return
        }

        if (pendingReset) {
            pendingReset = false
            Log.d("FTMS", "Sending queued reset")
            dumpControllerState("onControlPointResponseSendingQueuedReset")
            reset()
            return
        }

        // Apply the latest target if it was updated while BUSY.
        val pending = pendingTargetPowerWatts
        if (pending != null) {
            pendingTargetPowerWatts = null
            Log.d("FTMS", "Sending queued target power: $pending W")
            dumpControllerState("onControlPointResponseSendingQueuedTarget(watts=$pending)")
            setTargetPower(pending) // this will go through now because commandState is IDLE
        }

        dumpControllerState("onControlPointResponseDone")
    }

    /**
     * Clears command pipeline state after a GATT disconnect.
     *
     * If disconnect happens while STOP is pending/in flight, timeout recovery
     * is bypassed and BUSY is cleared immediately.
     */
    fun onDisconnected() {
        transportReady = false
        val stopWasPendingOrInFlight =
            hasStopped && (pendingStopWorkout || commandState == FtmsCommandState.BUSY)
        cancelTimeoutTimer()

        pendingTargetPowerWatts = null
        pendingReset = false
        pendingStopWorkout = false
        inFlightRequestOpcode = null

        if (stopWasPendingOrInFlight) {
            Log.d("FTMS", "Disconnected during STOP; cleared BUSY without timeout recovery")
        }
        commandState = FtmsCommandState.IDLE
        dumpControllerState("onDisconnected")
    }

    private fun startTimeoutTimer() {
        cancelTimeoutTimer()

        timeoutRunnable = Runnable {
            cancelTimeoutTimer()
            // If the device never responds, release BUSY to avoid a permanent lock.
            if (commandState == FtmsCommandState.BUSY) {
                Log.w("FTMS", "Control Point command timeout -> forcing IDLE")
                val timedOutOpcode = inFlightRequestOpcode
                commandState = FtmsCommandState.IDLE
                inFlightRequestOpcode = null
                dumpControllerState("commandTimeoutForcedIdle")
                onCommandTimeout(timedOutOpcode)

                if (pendingStopWorkout) {
                    Log.d("FTMS", "Sending queued stopWorkout after timeout")
                    dumpControllerState("commandTimeoutSendingQueuedStop")
                    sendStopWorkout()
                } else if (pendingReset) {
                    pendingReset = false
                    Log.d("FTMS", "Sending queued reset after timeout")
                    dumpControllerState("commandTimeoutSendingQueuedReset")
                    reset()
                } else {
                    val pending = pendingTargetPowerWatts
                    if (pending != null) {
                        pendingTargetPowerWatts = null
                        Log.d("FTMS", "Sending queued target power after timeout: $pending W")
                        dumpControllerState("commandTimeoutSendingQueuedTarget(watts=$pending)")
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
