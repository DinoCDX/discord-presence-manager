package com.dpm.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun BenchmarkCard() {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var service by remember { mutableStateOf<List<Bench.Sample>>(emptyList()) }
    var app by remember { mutableStateOf<List<Bench.Sample>>(emptyList()) }
    var on by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            service = withContext(Dispatchers.IO) { Bench.load(ctx) }
            app = withContext(Dispatchers.IO) { Bench.load(ctx, app = true) }
            on = Bench.recording(ctx)
            delay(2_000L)
        }
    }
    SectionCard("Benchmarks", Modifier.fillMaxWidth()) {
        Text(
            when {
                on && !Live.isAlive() -> "Recording. Presence is off, so the presence service has no samples. The app is sampled while DPM is open."
                on -> "Recording every 2 seconds. The last 5 minutes are shown. The app is sampled while DPM is open."
                else -> "Off. Start recording, then turn presence on."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpTonalButton(onClick = {
                if (on) {
                    Bench.stop(ctx)
                    on = false
                } else {
                    Bench.start(ctx)
                    on = true
                    service = emptyList()
                    app = emptyList()
                }
            }) {
                Text(if (on) "Stop" else "Start")
            }
            ExpTextButton(onClick = {
                Bench.clear(ctx)
                service = emptyList()
                app = emptyList()
            }) { Text("Clear") }
        }
        if (service.isEmpty() && app.isEmpty()) {
            Text(
                "No samples yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        } else {
            BenchSection("Presence service", service, full = true)
            BenchSection("App", app, full = false)
        }
    }
}

@Composable
private fun BenchSection(title: String, list: List<Bench.Sample>, full: Boolean) {
    val cs = MaterialTheme.colorScheme
    Text(
        title,
        style = MaterialTheme.typography.titleMediumEmphasized,
        color = cs.primary,
        modifier = Modifier.padding(top = 16.dp)
    )
    if (list.isEmpty()) {
        Text(
            "No samples yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        return
    }
    val pss = list.map { it.pssMb }
    val heap = list.map { it.heapMb }
    val cpu = list.map { it.cpu }
    val last = list.last()

    Chart("Memory (MB)", listOf(cs.primary to pss, cs.tertiary to heap))
    Text(
        "RAM ${stat(pss)} MB  ·  Heap ${stat(heap)} MB",
        style = MaterialTheme.typography.labelLarge,
        color = cs.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
    Text(
        "Breakdown avg: Java ${f(list.map { it.javaMb }.average())} · " +
            "Native ${f(list.map { it.nativeMb }.average())} · " +
            "Graphics ${f(list.map { it.graphicsMb }.average())} · " +
            "Code ${f(list.map { it.codeMb }.average())} · " +
            "Other ${f(list.map { it.otherMb }.average())} MB",
        style = MaterialTheme.typography.labelLarge,
        color = cs.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )

    Chart("CPU (%)", listOf(cs.primary to cpu))
    Text(
        "CPU ${stat(cpu)} %",
        style = MaterialTheme.typography.labelLarge,
        color = cs.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )

    Text(
        if (full) {
            "Service update: avg ${f(last.tickAvg)} ms, slowest ${f(last.tickMax)} ms\n" +
                "Media lookup avg ${f(last.mediaAvg)} ms  ·  Discord pushes ${f(last.pushesPerMin)} per min"
        } else {
            "Media lookup avg ${f(last.mediaAvg)} ms"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = cs.onSurface,
        modifier = Modifier.padding(top = 12.dp)
    )
}

@Composable
private fun Chart(title: String, series: List<Pair<Color, List<Double>>>) {
    val cs = MaterialTheme.colorScheme
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = cs.onSurface,
        modifier = Modifier.padding(top = 12.dp)
    )
    val all = series.flatMap { it.second }
    if (all.isEmpty()) {
        Text("No data", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        return
    }
    val lo = all.minOrNull() ?: 0.0
    val hi = all.maxOrNull() ?: 0.0
    val span = (hi - lo).takeIf { it > 0.0 } ?: 1.0
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(cs.surfaceContainerHighest, RoundedCornerShape(12.dp))
    ) {
        series.forEach { (color, values) ->
            if (values.size >= 2) {
                val path = Path()
                values.forEachIndexed { i, v ->
                    val x = size.width * i / (values.size - 1)
                    val y = size.height - ((v - lo) / span).toFloat() * size.height
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
            }
        }
    }
}

private fun f(x: Double): String = String.format(Locale.US, "%.1f", x)

private fun stat(v: List<Double>): String {
    if (v.isEmpty()) return "-"
    return "now ${f(v.last())}  ·  avg ${f(v.average())}  ·  peak ${f(v.maxOrNull() ?: 0.0)}"
}
