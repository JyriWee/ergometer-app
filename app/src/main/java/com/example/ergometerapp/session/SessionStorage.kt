package com.example.ergometerapp.session

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SessionStorage {

    fun save(context: Context, summary: SessionSummary) {
        val formatter =
            SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)

        val filename =
            "session_${formatter.format(Date())}.txt"

        val content = buildString {
            appendLine("Duration: ${formatTime(summary.durationSeconds)}")
            appendLine("Avg power: ${summary.avgPower ?: "--"} W")
            appendLine("Max HR: ${summary.maxHeartRate ?: "--"} bpm")
            appendLine("Distance: ${summary.distanceMeters ?: "--"} m")
        }

        context.openFileOutput(filename, Context.MODE_PRIVATE)
            .use { it.write(content.toByteArray()) }
    }

    private fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%d:%02d".format(m, s)
    }
}