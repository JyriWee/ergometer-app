package com.example.ergometerapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ergometerapp.workout.Step
import com.example.ergometerapp.workout.WorkoutFile

private const val CHART_HEIGHT_DP = 160
private const val MAX_RELATIVE_POWER = 2.0

internal enum class SegmentKind {
    RAMP,
    STEADY,
    FREERIDE,
}

internal data class WorkoutProfileSegment(
    val startSec: Int,
    val durationSec: Int,
    val startPowerRelFtp: Double?,
    val endPowerRelFtp: Double?,
    val kind: SegmentKind,
)

/**
 * Renders a static workout profile chart from parsed workout steps.
 *
 * The chart uses relative FTP values from the source workout (`0.75 == 75% FTP`)
 * so the shape is deterministic across devices.
 */
@Composable
internal fun WorkoutProfileChart(
    workout: WorkoutFile,
    ftpWatts: Int,
    modifier: Modifier = Modifier,
    elapsedSec: Int? = null,
) {
    val segments = remember(workout) { buildWorkoutProfileSegments(workout) }
    val semanticsDescription =
        "Workout profile chart, ${segments.size} segments, ftp ${ftpWatts.coerceAtLeast(1)} watts"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT_DP.dp)
            .semantics { contentDescription = semanticsDescription }
    ) {
        if (segments.isEmpty()) {
            Text(
                text = "No chart data",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            return@Box
        }

        val guideColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        val freeRideColor = MaterialTheme.colorScheme.surfaceVariant
        val cursorColor = MaterialTheme.colorScheme.primary

        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalDurationSec = segments.sumOf { it.durationSec }.coerceAtLeast(1)
            drawGuides(guideColor)

            segments.forEach { segment ->
                val startX = (segment.startSec.toFloat() / totalDurationSec.toFloat()) * size.width
                val endX =
                    ((segment.startSec + segment.durationSec).toFloat() / totalDurationSec.toFloat()) * size.width
                if (endX <= startX) return@forEach

                when (segment.kind) {
                    SegmentKind.FREERIDE -> {
                        val topY = yForPower(0.12, size.height)
                        drawRect(
                            color = freeRideColor,
                            topLeft = androidx.compose.ui.geometry.Offset(startX, topY),
                            size = androidx.compose.ui.geometry.Size(endX - startX, size.height - topY),
                        )
                    }

                    SegmentKind.RAMP,
                    SegmentKind.STEADY -> {
                        val startRel = segment.startPowerRelFtp ?: return@forEach
                        val endRel = segment.endPowerRelFtp ?: return@forEach
                        val startY = yForPower(startRel, size.height)
                        val endY = yForPower(endRel, size.height)
                        val color = zoneColor((startRel + endRel) / 2.0)

                        val path = Path().apply {
                            moveTo(startX, size.height)
                            lineTo(startX, startY)
                            lineTo(endX, endY)
                            lineTo(endX, size.height)
                            close()
                        }
                        drawPath(path = path, color = color)
                    }
                }
            }

            if (elapsedSec != null && elapsedSec >= 0) {
                val cursorX = (elapsedSec.toFloat() / totalDurationSec.toFloat())
                    .coerceIn(0f, 1f) * size.width
                drawLine(
                    color = cursorColor,
                    start = androidx.compose.ui.geometry.Offset(cursorX, 0f),
                    end = androidx.compose.ui.geometry.Offset(cursorX, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }
    }
}

internal fun buildWorkoutProfileSegments(workout: WorkoutFile): List<WorkoutProfileSegment> {
    val segments = mutableListOf<WorkoutProfileSegment>()
    var startSec = 0

    fun appendSegment(
        durationSec: Int,
        startPowerRelFtp: Double?,
        endPowerRelFtp: Double?,
        kind: SegmentKind,
    ) {
        if (durationSec <= 0) return
        segments += WorkoutProfileSegment(
            startSec = startSec,
            durationSec = durationSec,
            startPowerRelFtp = startPowerRelFtp,
            endPowerRelFtp = endPowerRelFtp,
            kind = kind,
        )
        startSec += durationSec
    }

    workout.steps.forEach { step ->
        when (step) {
            is Step.Warmup -> {
                val duration = validDuration(step.durationSec) ?: return@forEach
                val low = validPower(step.powerLow) ?: return@forEach
                val high = validPower(step.powerHigh) ?: return@forEach
                appendSegment(duration, low, high, SegmentKind.RAMP)
            }

            is Step.Cooldown -> {
                val duration = validDuration(step.durationSec) ?: return@forEach
                val low = validPower(step.powerLow) ?: return@forEach
                val high = validPower(step.powerHigh) ?: return@forEach
                appendSegment(duration, low, high, SegmentKind.RAMP)
            }

            is Step.Ramp -> {
                val duration = validDuration(step.durationSec) ?: return@forEach
                val low = validPower(step.powerLow) ?: return@forEach
                val high = validPower(step.powerHigh) ?: return@forEach
                appendSegment(duration, low, high, SegmentKind.RAMP)
            }

            is Step.SteadyState -> {
                val duration = validDuration(step.durationSec) ?: return@forEach
                val power = validPower(step.power) ?: return@forEach
                appendSegment(duration, power, power, SegmentKind.STEADY)
            }

            is Step.IntervalsT -> {
                val onDuration = validDuration(step.onDurationSec) ?: return@forEach
                val offDuration = validDuration(step.offDurationSec) ?: return@forEach
                val onPower = validPower(step.onPower) ?: return@forEach
                val offPower = validPower(step.offPower) ?: return@forEach
                val repeatCount = step.repeat?.takeIf { it > 0 } ?: return@forEach

                // Expand explicitly so rendering stays time-true without hidden loop state.
                repeat(repeatCount) {
                    appendSegment(onDuration, onPower, onPower, SegmentKind.STEADY)
                    appendSegment(offDuration, offPower, offPower, SegmentKind.STEADY)
                }
            }

            is Step.FreeRide -> {
                val duration = validDuration(step.durationSec) ?: return@forEach
                appendSegment(duration, null, null, SegmentKind.FREERIDE)
            }

            is Step.Unknown -> {
                // Unknown tags are intentionally ignored in MVP chart rendering.
            }
        }
    }

    return segments
}

private fun validDuration(durationSec: Int?): Int? {
    return durationSec?.takeIf { it > 0 }
}

private fun validPower(powerRelFtp: Double?): Double? {
    val value = powerRelFtp ?: return null
    if (!value.isFinite() || value < 0.0) return null
    return value.coerceIn(0.0, MAX_RELATIVE_POWER)
}

private fun DrawScope.drawGuides(color: Color) {
    listOf(0.5, 1.0, 1.5, 2.0).forEach { guideRel ->
        val y = yForPower(guideRel, size.height)
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(0f, y),
            end = androidx.compose.ui.geometry.Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

private fun yForPower(relativePower: Double, chartHeight: Float): Float {
    val normalized = (relativePower.coerceIn(0.0, MAX_RELATIVE_POWER) / MAX_RELATIVE_POWER).toFloat()
    return chartHeight * (1f - normalized)
}

private fun zoneColor(relativePower: Double): Color {
    return when {
        relativePower <= 0.55 -> Color(0xFF9AA6B2)
        relativePower <= 0.75 -> Color(0xFF23A6D5)
        relativePower <= 0.90 -> Color(0xFF2FBF71)
        relativePower <= 1.05 -> Color(0xFFF3B400)
        relativePower <= 1.20 -> Color(0xFFFF7A3D)
        else -> Color(0xFFE64A19)
    }
}
