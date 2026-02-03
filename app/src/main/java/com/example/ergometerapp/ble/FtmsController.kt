package com.example.ergometerapp.ble

import android.os.Handler
import android.os.Looper
import android.util.Log

class FtmsController(
    private val writeControlPoint: (ByteArray) -> Unit
) {
    private var pendingReset = false

    private var commandState = FtmsCommandState.IDLE
    private var pendingTargetPowerWatts: Int? = null

    private val handler = Handler(Looper.getMainLooper())

    private val commandTimeoutMs = 2500L

    private var timeoutRunnable: Runnable? = null

    @Suppress("unused")

    // V0: no real ready logic yet
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
        // NOTE: BUSY is released later in the Control Point response callback
    }

    fun requestControl() {
        val payload = byteArrayOf(0x00.toByte())
        sendCommand(payload, "requestControl")
    }

    @Suppress("unused")

    fun setResistanceLevel(level: Int) {
        val payload = byteArrayOf(0x04.toByte(), level.toByte())
        sendCommand(payload, "setResistanceLevel($level)")
    }

    fun setTargetPower(watts: Int) {
        val w = watts.coerceIn(0, 2000)

        // LAST WINS: if BUSY, keep only the latest request and return
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

    fun stop() {
        val payload = byteArrayOf(0x08.toByte(), 0x01.toByte())
        sendCommand(payload, "stop")
    }

    @Suppress("unused")
    fun pause() {
        val payload = byteArrayOf(0x08.toByte(), 0x02.toByte())
        sendCommand(payload, "pause")
    }

    fun reset() {
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

        // release for the next command
        commandState = FtmsCommandState.IDLE

        if (pendingReset) {
            pendingReset = false
            Log.d("FTMS", "Sending queued reset")
            reset()
            return
        }

        // LAST WINS: if the user requested a new target power while BUSY, send it now
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
            // If the command got stuck, release BUSY
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
