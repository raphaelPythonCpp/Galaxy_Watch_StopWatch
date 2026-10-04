package com.example.chrono

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.*
import kotlinx.coroutines.delay

data class Lap(val index: Int, val lapTime: Long, val total: Long)

/** État global : survit à la recréation de l'activité. */
object Stopwatch {
    var running by mutableStateOf(false)
    var accumulated by mutableLongStateOf(0L)
    private var startedAt = 0L
    val laps = mutableStateListOf<Lap>()

    fun elapsed(): Long =
        accumulated + if (running) SystemClock.elapsedRealtime() - startedAt else 0L

    fun start() {
        if (!running) { startedAt = SystemClock.elapsedRealtime(); running = true }
    }

    fun stop() {
        if (running) { accumulated += SystemClock.elapsedRealtime() - startedAt; running = false }
    }

    fun lap() {
        if (!running) return
        val t = elapsed()
        val last = laps.firstOrNull()?.total ?: 0L
        laps.add(0, Lap(laps.size + 1, t - last, t))
    }

    fun reset() { running = false; accumulated = 0L; laps.clear() }

    /** Bouton physique : tour si en marche, sinon start. */
    fun primaryButton() { if (running) lap() else start() }
}

fun format(ms: Long): String {
    val cs = (ms / 10) % 100
    val s = (ms / 1000) % 60
    val m = (ms / 60000) % 60
    val h = ms / 3600000
    return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, s, cs)
    else "%02d:%02d.%02d".format(m, s, cs)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StopwatchScreen(this) }
    }
}

@Composable
fun StopwatchScreen(activity: ComponentActivity) {
    var display by remember { mutableLongStateOf(Stopwatch.elapsed()) }
    val running = Stopwatch.running

    // Rafraîchissement de l'affichage
    LaunchedEffect(running) {
        display = Stopwatch.elapsed()
        while (running) { display = Stopwatch.elapsed(); delay(30) }
    }

    // Écran allumé pendant que le chrono tourne
    DisposableEffect(running) {
        if (running) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Bouton physique du bas (Retour) : tour si en marche, sinon start
    BackHandler(enabled = true) { Stopwatch.primaryButton() }

    MaterialTheme {
        Scaffold(timeText = {}) {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Text(
                        text = format(display),
                        fontSize = 34.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        color = Color.White
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (running) {
                            Button(
                                onClick = { Stopwatch.lap() },
                                modifier = Modifier.size(56.dp),
                                colors = ButtonDefaults.secondaryButtonColors()
                            ) { Text("Tour", fontSize = 12.sp) }
                            Button(
                                onClick = { Stopwatch.stop() },
                                modifier = Modifier.size(56.dp),
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFE5484D))
                            ) { Text("Stop", fontSize = 12.sp) }
                        } else {
                            Button(
                                onClick = { Stopwatch.reset(); display = 0L },
                                modifier = Modifier.size(56.dp),
                                colors = ButtonDefaults.secondaryButtonColors()
                            ) { Text("RàZ", fontSize = 12.sp) }
                            Button(
                                onClick = { Stopwatch.start() },
                                modifier = Modifier.size(56.dp),
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3DDC84))
                            ) { Text("Start", fontSize = 12.sp, color = Color.Black) }
                        }
                    }
                }
                items(Stopwatch.laps) { lap ->
                    Row(
                        modifier = Modifier.fillMaxWidth(0.8f).padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("%02d".format(lap.index), fontSize = 13.sp, color = Color.Gray)
                        Text(format(lap.lapTime), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
