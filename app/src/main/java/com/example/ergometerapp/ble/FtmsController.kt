package com.example.ergometerapp.ble

import android.util.Log
import android.os.Handler
import android.os.Looper

class FtmsController(
    private val writeControlPoint: (ByteArray) -> Unit
) {
    private var pendingReset = false

    private var commandState = FtmsCommandState.IDLE
    private var pendingTargetPowerWatts: Int? = null

    private val handler = Handler(Looper.getMainLooper())

    private val commandTimeoutMs = 2500L

    private var timeoutRunnable: Runnable? = null

    // V0: ei vielä oikeaa ready-logiikkaa
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
        // HUOM: BUSY vapautetaan myöhemmin Control Point response -callbackista
    }

    fun requestControl() {
        val payload = byteArrayOf(0x00.toByte())
        sendCommand(payload, "requestControl")
    }

    fun setResistanceLevel(level: Int) {
        val payload = byteArrayOf(0x04.toByte(), level.toByte())
        sendCommand(payload, "setResistanceLevel($level)")
    }

    fun setTargetPower(watts: Int) {
        val w = watts.coerceIn(0, 2000)

        // LAST WINS: jos BUSY, muistetaan vain viimeisin pyyntö ja poistutaan
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
     * Kutsu tätä FtmsBleClientistä, kun saat Control Point response -paketin (0x80 ...).
     * Tämä vapauttaa BUSY-tilan deterministisesti.
     */
    fun onControlPointResponse(requestOpcode: Int, resultCode: Int) {
        cancelTimeoutTimer()

        val ok = (resultCode == 0x01)
        commandState = if (ok) FtmsCommandState.SUCCESS else FtmsCommandState.ERROR
        Log.d("FTMS", "CP response opcode=$requestOpcode result=$resultCode state=$commandState")

        // vapauta seuraavaa komentoa varten
        commandState = FtmsCommandState.IDLE

        if (pendingReset) {
            pendingReset = false
            Log.d("FTMS", "Sending queued reset")
            reset()
            return
        }

        // LAST WINS: jos käyttäjä pyysi uutta target poweria BUSY aikana, lähetä se nyt
        val pending = pendingTargetPowerWatts
        if (pending != null) {
            pendingTargetPowerWatts = null
            Log.d("FTMS", "Sending queued target power: $pending W")
            setTargetPower(pending) // tämä menee nyt läpi, koska commandState on IDLE
        }

    }

    private fun startTimeoutTimer() {
        cancelTimeoutTimer()

        timeoutRunnable = Runnable {
            cancelTimeoutTimer()
            // Jos komento jäi roikkumaan, vapauta BUSY
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
