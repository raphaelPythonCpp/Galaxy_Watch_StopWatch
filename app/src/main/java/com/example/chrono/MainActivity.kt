package com.example.chrono

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material.*
import kotlinx.coroutines.delay
import kotlin.math.abs

private val Green = Color(0xFF34D399)
private val Blue = Color(0xFF3B82F6)
private val Red = Color(0xFFF87171)
private val RedBtn = Color(0xFFB91C1C)
private val Dim = Color(0xFF9CA3AF)
private val Soft = Color(0xFFD1D5DB)
private val RingTrack = Color(0xFF0E1218)
private val SurfaceBtn = Color(0xFF2B2F36)
private val RowBg = Color(0xFF1B1F26)
private val OffTrack = Color(0xFF3A3F47)
private val Tnum = TextStyle(fontFeatureSettings = "tnum")

/** Dégradé vert -> bleu -> rouge, t entre 0 (min) et 1 (max). */
fun gradient(t: Float): Color {
    val x = t.coerceIn(0f, 1f)
    return if (x < 0.5f) lerp(Green, Blue, x * 2f) else lerp(Blue, Red, (x - 0.5f) * 2f)
}

object Ui {
    var showSettings by mutableStateOf(false)
    var ambient by mutableStateOf(false)
}

class MainActivity : ComponentActivity() {
    private var lastWake = 0L

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            lastWake = SystemClock.uptimeMillis()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Stopwatch.load(this)
        Settings.load(this)
        Ui.ambient = false
        registerReceiver(screenOn, IntentFilter(Intent.ACTION_SCREEN_ON))

        if (Settings.aodActive) {
            lifecycle.addObserver(
                AmbientLifecycleObserver(this, object : AmbientLifecycleObserver.AmbientLifecycleCallback {
                    override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                        Ui.ambient = true
                    }
                    override fun onExitAmbient() {
                        Ui.ambient = false
                        lastWake = SystemClock.uptimeMillis()
                    }
                })
            )
        }
        setContent { App() }
    }

    override fun onStart() {
        super.onStart()
        lastWake = SystemClock.uptimeMillis()
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenOn) } catch (e: Exception) { }
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event?.repeatCount == 0) handleButton()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
        if (keyCode == KeyEvent.KEYCODE_BACK) true else super.onKeyUp(keyCode, event)

    private fun handleButton() {
        if (Ui.showSettings) { Ui.showSettings = false; return }
        val pm = getSystemService(PowerManager::class.java)
        val justWoke = Ui.ambient ||
            pm?.isInteractive == false ||
            SystemClock.uptimeMillis() - lastWake < 600
        if (!justWoke) Stopwatch.primary(this)
    }
}

@Composable
fun App() {
    var now by remember { mutableLongStateOf(Stopwatch.elapsed()) }
    val running = Stopwatch.running
    val acc = Stopwatch.accumulated
    val ambient = Ui.ambient
    val eco = Settings.eco

    LaunchedEffect(running, acc, ambient, eco) {
        now = Stopwatch.elapsed()
        if (running) {
            if (ambient) {
                while (true) { delay(1000); now = Stopwatch.elapsed() }
            } else if (eco) {
                // Éco : ~10 mises à jour/s, et aucune quand l'écran n'est pas visible
                while (true) { withFrameMillis { now = Stopwatch.elapsed() }; delay(100) }
            } else {
                var last = 0L
                while (true) {
                    withFrameMillis { t ->
                        if (t - last >= 33) { last = t; now = Stopwatch.elapsed() }
                    }
                }
            }
        }
    }

    val laps = Stopwatch.laps
    val vMin = laps.minOfOrNull { it.lapTime } ?: 0L
    val vMax = laps.maxOfOrNull { it.lapTime } ?: 0L
    fun tOf(v: Long): Float = if (vMax > vMin) (v - vMin).toFloat() / (vMax - vMin) else 0f
    fun colorOf(v: Long): Color = if (eco) Color.White else gradient(tOf(v))
    val idle = !running && acc == 0L

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when {
                ambient -> AmbientScreen(now)
                Ui.showSettings -> SettingsScreen()
                else -> {
                    if (Settings.ringActive) Ring { now }
                    ScalingLazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item { Header(now, running, eco) }
                        if (laps.size >= 2) {
                            item {
                                val avg = laps.sumOf { it.lapTime } / laps.size
                                Text(
                                    "Moy. " + fmtFull(avg),
                                    fontSize = 12.sp, color = colorOf(avg), style = Tnum,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                        itemsIndexed(laps) { i, lap ->
                            LapRow(lap, laps.getOrNull(i + 1)?.lapTime, colorOf(lap.lapTime))
                        }
                    }
                    if (idle) {
                        Box(
                            Modifier.fillMaxSize().padding(top = 16.dp),
                            contentAlignment = Alignment.TopCenter
                        ) { MenuButton { Ui.showSettings = true } }
                    }
                }
            }
        }
    }
}

@Composable
fun Ring(now: () -> Long) {
    Canvas(Modifier.fillMaxSize().padding(3.dp)) {
        val stroke = 6.dp.toPx()
        val topLeft = Offset(stroke / 2, stroke / 2)
        val sz = Size(size.width - stroke, size.height - stroke)
        drawArc(RingTrack, 0f, 360f, false, topLeft, sz, style = Stroke(stroke))
        val head = -90f + (now() % 60000L) / 60000f * 360f
        rotate(degrees = head - 45f, pivot = center) {
            drawArc(
                brush = Brush.sweepGradient(
                    0f to RingTrack,
                    0.125f to Color.White,
                    0.9f to Color.White,
                    1f to RingTrack,
                    center = center
                ),
                startAngle = 0f,
                sweepAngle = 45f,
                useCenter = false,
                topLeft = topLeft,
                size = sz,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun AmbientScreen(ms: Long) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Text(fmtMain(ms), fontSize = 44.sp, color = Color(0xFFB0B0B0), style = Tnum)
    }
}

@Composable
fun MenuButton(onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(SurfaceBtn).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(14.dp)) {
            val w = 1.8.dp.toPx()
            for (i in 0..2) {
                val y = size.height * (0.15f + 0.35f * i)
                drawLine(Color.White, Offset(0f, y), Offset(size.width, y), strokeWidth = w, cap = StrokeCap.Round)
            }
        }
    }
}

/** Interrupteur à glissière (pastille). */
@Composable
fun Pill(checked: Boolean, accent: Color) {
    Box(
        Modifier.width(38.dp).height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (checked) accent else OffTrack)
    ) {
        Box(
            Modifier.align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(3.dp).size(16.dp).clip(CircleShape)
                .background(if (checked) Color.Black else Color.White)
        )
    }
}

/** [texte] [espace] [slider on/off] */
@Composable
fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(0.92f)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(20.dp))
            .background(RowBg)
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
        Pill(checked, accent)
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val eco = Settings.eco
    val accent = if (eco) Color.White else Green
    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text("Réglages", fontSize = 14.sp, color = Dim) }
        item {
            ToggleRow("Cercle", Settings.ringActive, !eco, accent) { Settings.setRing(ctx, it) }
        }
        item {
            ToggleRow("Always-on display", Settings.aodActive, !eco, accent) {
                Settings.setAod(ctx, it)
                (ctx as? Activity)?.recreate()
            }
        }
        item {
            ToggleRow("Mode éco", eco, true, accent) {
                Settings.setEco(ctx, it)
                (ctx as? Activity)?.recreate()
            }
        }
        item {
            Chip(
                onClick = { Ui.showSettings = false },
                label = { Text("Retour") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(0.92f)
            )
        }
    }
}

@Composable
fun Header(ms: Long, running: Boolean, eco: Boolean) {
    val ctx = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                fmtMain(ms),
                fontSize = if (ms >= 3_600_000L) 30.sp else 38.sp,
                fontWeight = FontWeight.Medium, color = Color.White, style = Tnum
            )
            Text(
                if (eco) fmtTenth(ms) else fmtCs(ms),
                fontSize = 18.sp, color = Dim, style = Tnum,
                modifier = Modifier.padding(bottom = 5.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundButton(if (running) "Tour" else "Reset", SurfaceBtn, Color.White) { Stopwatch.left(ctx) }
            if (running) {
                RoundButton("Stop", if (eco) OffTrack else RedBtn, Color.White) { Stopwatch.toggle(ctx) }
            } else {
                RoundButton("Start", if (eco) Color.White else Green, Color.Black) { Stopwatch.toggle(ctx) }
            }
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

/** [n° tour] [temps total] [dt depuis le dernier tour] [écart de dt vs tour précédent] */
@Composable
fun LapRow(lap: Lap, prevLapTime: Long?, color: Color) {
    val delta = if (prevLapTime == null) "" else {
        val d = lap.lapTime - prevLapTime
        val a = abs(d)
        (if (d >= 0) "+" else "−") + (a / 1000) + "." + "%02d".format((a / 10) % 100)
    }
    Row(
        Modifier.fillMaxWidth(0.92f).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("%02d".format(lap.index), fontSize = 11.sp, color = Dim, style = Tnum, modifier = Modifier.width(20.dp))
        Text(fmtFull(lap.total), fontSize = 11.sp, color = Soft, style = Tnum, modifier = Modifier.weight(1f))
        Text(fmtFull(lap.lapTime), fontSize = 12.sp, color = color, style = Tnum, modifier = Modifier.weight(1f))
        Text(delta, fontSize = 10.sp, color = Dim, style = Tnum, textAlign = TextAlign.End, modifier = Modifier.width(40.dp))
    }
}
