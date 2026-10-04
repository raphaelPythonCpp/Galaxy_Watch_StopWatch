package com.example.chrono

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material.*
import kotlin.math.abs

private val Green = Color(0xFF34D399)
private val Red = Color(0xFFF87171)
private val RedBtn = Color(0xFFB91C1C)
private val Dim = Color(0xFF9CA3AF)
private val Track = Color(0xFF1F2937)
private val SurfaceBtn = Color(0xFF2B2F36)
private val Tnum = TextStyle(fontFeatureSettings = "tnum")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Stopwatch.load(this)
        setContent { App() }
    }

    // Bouton physique du bas (Retour) : tour si en marche, sinon start.
    // L'écran suit le délai d'extinction système ; un appui sur le bouton le rallume.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event?.repeatCount == 0) Stopwatch.primary(this)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
        if (keyCode == KeyEvent.KEYCODE_BACK) true else super.onKeyUp(keyCode, event)
}

@Composable
fun App() {
    var now by remember { mutableLongStateOf(Stopwatch.elapsed()) }
    val running = Stopwatch.running
    val acc = Stopwatch.accumulated

    // Rafraîchissement synchronisé avec l'écran (~30 i/s), suspendu quand l'appli n'est pas visible
    LaunchedEffect(running, acc) {
        now = Stopwatch.elapsed()
        if (running) {
            var last = 0L
            while (true) {
                withFrameMillis { t ->
                    if (t - last >= 33) { last = t; now = Stopwatch.elapsed() }
                }
            }
        }
    }

    val laps = Stopwatch.laps
    val stats = laps.size >= 2
    val best = if (stats) laps.minOf { it.lapTime } else -1L
    val worst = if (stats) laps.maxOf { it.lapTime } else -1L

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Ring { now }
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item { Header(now, running) }
                if (stats) {
                    item {
                        Text(
                            "Moy. " + fmtFull(laps.sumOf { it.lapTime } / laps.size),
                            fontSize = 12.sp, color = Dim, style = Tnum,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                itemsIndexed(laps) { i, lap ->
                    val color = when {
                        stats && lap.lapTime == best -> Green
                        stats && lap.lapTime == worst -> Red
                        else -> Color.White
                    }
                    LapRow(lap, laps.getOrNull(i + 1)?.lapTime, color)
                }
            }
        }
    }
}

@Composable
fun Ring(now: () -> Long) {
    Canvas(Modifier.fillMaxSize().padding(3.dp)) {
        val stroke = 5.dp.toPx()
        val topLeft = Offset(stroke / 2, stroke / 2)
        val sz = Size(size.width - stroke, size.height - stroke)
        drawArc(Track, 0f, 360f, false, topLeft, sz, style = Stroke(stroke))
        val sweep = (now() % 60000L) / 60000f * 360f
        if (sweep > 0f) {
            drawArc(Green, -90f, sweep, false, topLeft, sz, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

@Composable
fun Header(ms: Long, running: Boolean) {
    val ctx = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                fmtMain(ms),
                fontSize = if (ms >= 3_600_000L) 30.sp else 38.sp,
                fontWeight = FontWeight.Medium, color = Color.White, style = Tnum
            )
            Text(
                fmtCs(ms), fontSize = 18.sp, color = Dim, style = Tnum,
                modifier = Modifier.padding(bottom = 5.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundButton(if (running) "Tour" else "RàZ", SurfaceBtn, Color.White) { Stopwatch.left(ctx) }
            RoundButton(
                if (running) "Stop" else "Start",
                if (running) RedBtn else Green,
                if (running) Color.White else Color.Black
            ) { Stopwatch.toggle(ctx) }
        }
    }
}

@Composable
fun RoundButton(label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(58.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = bg, contentColor = fg)
    ) { Text(label, fontSize = 12.sp, color = fg) }
}

@Composable
fun LapRow(lap: Lap, prev: Long?, color: Color) {
    val delta = if (prev == null) "" else {
        val d = lap.lapTime - prev
        val a = abs(d)
        (if (d >= 0) "+" else "−") + (a / 1000) + "." + "%02d".format((a / 10) % 100)
    }
    Row(
        Modifier.fillMaxWidth(0.82f).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("%02d".format(lap.index), fontSize = 12.sp, color = Dim, style = Tnum, modifier = Modifier.width(26.dp))
        Text(fmtFull(lap.lapTime), fontSize = 15.sp, color = color, style = Tnum, modifier = Modifier.weight(1f))
        Text(delta, fontSize = 11.sp, color = Dim, style = Tnum)
    }
}
