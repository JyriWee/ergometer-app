package com.example.ergometerapp.ui.debug

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ergometerapp.ble.debug.FtmsDebugBuffer
import com.example.ergometerapp.ble.debug.FtmsDebugEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun FtmsDebugTimelineScreen() {
    var events by remember { mutableStateOf<List<FtmsDebugEvent>>(emptyList()) }
    val formatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        while (true) {
            events = FtmsDebugBuffer.snapshot()
            delay(1000)
        }
    }

    val orderedEvents = remember(events) { events.sortedBy { eventTimestampMs(it) } }

    LazyColumn(Modifier.padding(16.dp)) {
        items(orderedEvents) { event ->
            Text(formatEventLine(event, formatter))
        }
    }
}

private fun formatEventLine(event: FtmsDebugEvent, formatter: SimpleDateFormat): String {
    val time = formatter.format(Date(eventTimestampMs(event)))
    return when (event) {
        is FtmsDebugEvent.TargetPowerIssued ->
            "$time TargetPowerIssued targetPowerWatts=${event.targetPowerWatts}"
        is FtmsDebugEvent.PowerSample ->
            "$time PowerSample powerWatts=${event.powerWatts}"
        is FtmsDebugEvent.ObservationEnded ->
            "$time ObservationEnded"
    }
}

private fun eventTimestampMs(event: FtmsDebugEvent): Long {
    return when (event) {
        is FtmsDebugEvent.TargetPowerIssued -> event.timestampMs
        is FtmsDebugEvent.PowerSample -> event.timestampMs
        is FtmsDebugEvent.ObservationEnded -> event.timestampMs
    }
}
