package com.example.ergometerapp.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ergometerapp.workout.Step
import com.example.ergometerapp.workout.WorkoutFile
import kotlin.math.roundToInt

private const val CHART_HEIGHT_DP = 160
private const val MAX_RELATIVE_POWER_DATA = 2.0
private const val DEFAULT_RENDER_MAX_RELATIVE_POWER = 1.5
private const val HIGH_INTENSITY_RENDER_MAX_RELATIVE_POWER = 2.0
private val BASE_GUIDE_RELATIVE_POWERS = listOf(0.5, 0.75, 1.0, 1.25, 1.5)

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
    currentTargetWatts: Int? = null,
    chartHeight: Dp = CHART_HEIGHT_DP.dp,
) {
    val segments = remember(workout) { buildWorkoutProfileSegments(workout) }
    val guideRelativePowers = remember(segments) { guideRelativePowersForSegments(segments) }
    val renderMaxRelativePower = guideRelativePowers.lastOrNull() ?: DEFAULT_RENDER_MAX_RELATIVE_POWER
    val semanticsDescription =
        "Workout profile chart, ${segments.size} segments, ftp ${ftpWatts.coerceAtLeast(1)} watts"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(chartHeight)
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
        val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        val targetLabelTextColor = MaterialTheme.colorScheme.onSurface
        val targetLabelBackgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalDurationSec = segments.sumOf { it.durationSec }.coerceAtLeast(1)
            val leftAxisWidth = 42.dp.toPx()
            val rightAxisWidth = 58.dp.toPx()
            val topPadding = 8.dp.toPx()
            val bottomPadding = 8.dp.toPx()
            val plotLeft = leftAxisWidth
            val plotRight = (size.width - rightAxisWidth).coerceAtLeast(plotLeft + 1f)
            val plotTop = topPadding
            val plotBottom = (size.height - bottomPadding).coerceAtLeast(plotTop + 1f)
            val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)

            drawGuides(
                color = guideColor,
                guideRelativePowers = guideRelativePowers,
                renderMaxRelativePower = renderMaxRelativePower,
                plotLeft = plotLeft,
                plotRight = plotRight,
                plotTop = plotTop,
                plotBottom = plotBottom,
            )

            segments.forEach { segment ->
                val startX = plotLeft +
                    (segment.startSec.toFloat() / totalDurationSec.toFloat()) * plotWidth
                val endX = plotLeft +
                    ((segment.startSec + segment.durationSec).toFloat() / totalDurationSec.toFloat()) * plotWidth
                if (endX <= startX) return@forEach

                when (segment.kind) {
                    SegmentKind.FREERIDE -> {
                        val topY = yForPower(
                            relativePower = 0.12,
                            renderMaxRelativePower = renderMaxRelativePower,
                            chartTop = plotTop,
                            chartBottom = plotBottom,
                        )
                        drawRect(
                            color = freeRideColor,
                            topLeft = androidx.compose.ui.geometry.Offset(startX, topY),
                            size = androidx.compose.ui.geometry.Size(endX - startX, plotBottom - topY),
                        )
                    }

                    SegmentKind.RAMP,
                    SegmentKind.STEADY -> {
                        val startRel = segment.startPowerRelFtp ?: return@forEach
                        val endRel = segment.endPowerRelFtp ?: return@forEach
                        val startY = yForPower(
                            relativePower = startRel,
                            renderMaxRelativePower = renderMaxRelativePower,
                            chartTop = plotTop,
                            chartBottom = plotBottom,
                        )
                        val endY = yForPower(
                            relativePower = endRel,
                            renderMaxRelativePower = renderMaxRelativePower,
                            chartTop = plotTop,
                            chartBottom = plotBottom,
                        )
                        val color = zoneColor((startRel + endRel) / 2.0)

                        val path = Path().apply {
                            moveTo(startX, plotBottom)
                            lineTo(startX, startY)
                            lineTo(endX, endY)
                            lineTo(endX, plotBottom)
                            close()
                        }
                        drawPath(path = path, color = color)
                    }
                }
            }

            if (elapsedSec != null && elapsedSec >= 0) {
                val cursorX = plotLeft + (elapsedSec.toFloat() / totalDurationSec.toFloat())
                    .coerceIn(0f, 1f) * plotWidth
                drawLine(
                    color = cursorColor,
                    start = androidx.compose.ui.geometry.Offset(cursorX, plotTop),
                    end = androidx.compose.ui.geometry.Offset(cursorX, plotBottom),
                    strokeWidth = 3.dp.toPx(),
                )

                if (currentTargetWatts != null && currentTargetWatts > 0) {
                    drawCurrentTargetLabel(
                        targetWatts = currentTargetWatts,
                        textColor = targetLabelTextColor,
                        backgroundColor = targetLabelBackgroundColor,
                        cursorX = cursorX,
                        plotLeft = plotLeft,
                        chartRight = size.width,
                        plotTop = plotTop,
                        plotBottom = plotBottom,
                    )
                }
            }

            drawGuideAxisLabels(
                ftpWatts = ftpWatts,
                guideRelativePowers = guideRelativePowers,
                renderMaxRelativePower = renderMaxRelativePower,
                textColor = axisLabelColor,
                leftX = plotLeft - 6.dp.toPx(),
                rightX = plotRight + 6.dp.toPx(),
                plotTop = plotTop,
                plotBottom = plotBottom,
            )
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
                val start = maxOf(low, high)
                val end = minOf(low, high)
                appendSegment(duration, start, end, SegmentKind.RAMP)
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
    return value.coerceIn(0.0, MAX_RELATIVE_POWER_DATA)
}

private fun guideRelativePowersForSegments(segments: List<WorkoutProfileSegment>): List<Double> {
    val maxRelativePower = segments.maxOfOrNull { segment ->
        maxOf(
            segment.startPowerRelFtp ?: 0.0,
            segment.endPowerRelFtp ?: 0.0,
        )
    } ?: 0.0
    return if (maxRelativePower > DEFAULT_RENDER_MAX_RELATIVE_POWER) {
        BASE_GUIDE_RELATIVE_POWERS + HIGH_INTENSITY_RENDER_MAX_RELATIVE_POWER
    } else {
        BASE_GUIDE_RELATIVE_POWERS
    }
}

private fun DrawScope.drawGuides(
    color: Color,
    guideRelativePowers: List<Double>,
    renderMaxRelativePower: Double,
    plotLeft: Float,
    plotRight: Float,
    plotTop: Float,
    plotBottom: Float,
) {
    guideRelativePowers.forEach { guideRel ->
        val y = yForPower(
            relativePower = guideRel,
            renderMaxRelativePower = renderMaxRelativePower,
            chartTop = plotTop,
            chartBottom = plotBottom,
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(plotLeft, y),
            end = androidx.compose.ui.geometry.Offset(plotRight, y),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

private fun DrawScope.drawGuideAxisLabels(
    ftpWatts: Int,
    guideRelativePowers: List<Double>,
    renderMaxRelativePower: Double,
    textColor: Color,
    leftX: Float,
    rightX: Float,
    plotTop: Float,
    plotBottom: Float,
) {
    val safeFtpWatts = ftpWatts.coerceAtLeast(1)
    val leftPaint = Paint().apply {
        isAntiAlias = true
        color = textColor.toArgb()
        textSize = 12.dp.toPx()
        textAlign = Paint.Align.RIGHT
    }
    val rightPaint = Paint().apply {
        isAntiAlias = true
        color = textColor.toArgb()
        textSize = 12.dp.toPx()
        textAlign = Paint.Align.LEFT
    }
    val leftBaselineOffset = -(leftPaint.fontMetrics.ascent + leftPaint.fontMetrics.descent) / 2f
    val rightBaselineOffset = -(rightPaint.fontMetrics.ascent + rightPaint.fontMetrics.descent) / 2f

    guideRelativePowers.forEach { relativePower ->
        val y = yForPower(
            relativePower = relativePower,
            renderMaxRelativePower = renderMaxRelativePower,
            chartTop = plotTop,
            chartBottom = plotBottom,
        )
        drawContext.canvas.nativeCanvas.drawText(
            "${(relativePower * 100).roundToInt()}%",
            leftX,
            y + leftBaselineOffset,
            leftPaint,
        )
        drawContext.canvas.nativeCanvas.drawText(
            "${(safeFtpWatts * relativePower).roundToInt()} W",
            rightX,
            y + rightBaselineOffset,
            rightPaint,
        )
    }
}

private fun DrawScope.drawCurrentTargetLabel(
    targetWatts: Int,
    textColor: Color,
    backgroundColor: Color,
    cursorX: Float,
    plotLeft: Float,
    chartRight: Float,
    plotTop: Float,
    plotBottom: Float,
) {
    val label = "$targetWatts W"
    val labelPaint = Paint().apply {
        isAntiAlias = true
        color = textColor.toArgb()
        textSize = 12.dp.toPx()
        textAlign = Paint.Align.LEFT
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    val labelWidth = labelPaint.measureText(label)
    val fontMetrics = labelPaint.fontMetrics
    val labelHeight = fontMetrics.descent - fontMetrics.ascent
    val horizontalPadding = 6.dp.toPx()
    val verticalPadding = 3.dp.toPx()
    val margin = 6.dp.toPx()
    val topInset = 4.dp.toPx()
    val corner = 6.dp.toPx()
    val boxHeight = labelHeight + verticalPadding * 2
    val boxWidth = labelWidth + horizontalPadding * 2

    val desiredX = cursorX + margin
    val maxX = chartRight - boxWidth
    val boxLeft = desiredX.coerceIn(plotLeft, maxX.coerceAtLeast(plotLeft))
    val maxTop = plotBottom - boxHeight
    val boxTop = (plotTop + topInset).coerceIn(plotTop, maxTop.coerceAtLeast(plotTop))

    drawRoundRect(
        color = backgroundColor,
        topLeft = androidx.compose.ui.geometry.Offset(boxLeft, boxTop),
        size = androidx.compose.ui.geometry.Size(
            width = boxWidth,
            height = boxHeight,
        ),
        cornerRadius = CornerRadius(corner, corner),
    )

    val textBaseline = boxTop + verticalPadding - fontMetrics.ascent
    drawContext.canvas.nativeCanvas.drawText(
        label,
        boxLeft + horizontalPadding,
        textBaseline,
        labelPaint,
    )
}

private fun yForPower(
    relativePower: Double,
    renderMaxRelativePower: Double,
    chartTop: Float,
    chartBottom: Float,
): Float {
    val normalized =
        (relativePower.coerceIn(0.0, renderMaxRelativePower) / renderMaxRelativePower).toFloat()
    return chartTop + (chartBottom - chartTop) * (1f - normalized)
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
