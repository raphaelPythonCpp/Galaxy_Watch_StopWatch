package com.example.chrono

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var lastWake = 0L
    private var longHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Stopwatch.load(this)
        Settings.load(this)
        History.load(this)
        Ui.ambient = false

        // Permission de notification (pastille « chrono en cours » sur le cadran), demandée une seule fois
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val sp = getSharedPreferences("settings", MODE_PRIVATE)
            if (!sp.getBoolean("askedNotif", false)) {
                sp.edit().putBoolean("askedNotif", true).apply()
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

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

    // Détection du réveil sans récepteur de diffusion (moins de travail en arrière-plan)
    override fun onStart() {
        super.onStart()
        lastWake = SystemClock.uptimeMillis()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) lastWake = SystemClock.uptimeMillis()
    }

    // Bouton physique du bas (Retour)
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val first = event?.repeatCount == 0
            if (first) longHandled = false
            if (Ui.screen == Screen.MAIN && Ui.locked) {
                // Verrou tactile : appui court = action (au relâchement), appui long = déverrouiller
                if (first) event?.startTracking()
                return true
            }
            if (first) handleButton()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && Ui.locked) {
            longHandled = true
            Ui.locked = false
            Stopwatch.buzz(this, true)
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (Ui.locked && Ui.screen == Screen.MAIN && !longHandled) handleButton()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun handleButton() {
        if (Ui.screen != Screen.MAIN) {
            Ui.screen = if (Ui.screen == Screen.SESSION) Screen.HISTORY else Screen.MAIN
            return
        }
        val pm = getSystemService(PowerManager::class.java)
        val justWoke = Ui.ambient ||
            pm?.isInteractive == false ||
            SystemClock.uptimeMillis() - lastWake < 600
        // Écran éteint / ambiant / à peine rallumé : on réveille seulement, pas de tour
        if (!justWoke) Stopwatch.primary(this)
    }
}

private class LapStats(val min: Long, val max: Long, val avg: Long)

@Composable
fun App() {
    val ctx = LocalContext.current
    val now = remember { mutableLongStateOf(Stopwatch.elapsed()) }
    val running = Stopwatch.running
    val acc = Stopwatch.accumulated
    val ambient = Ui.ambient
    val eco = Settings.eco
    val pal = remember(Settings.rgb) { makePalette(Settings.rgb) }

    // Mise à jour : 0,1 s (normal) ou 1 s (éco / ambiant), calée sur le changement de chiffre.
    // withFrameMillis ne reprend que si l'écran est visible : aucun réveil quand l'écran est éteint.
    LaunchedEffect(running, acc, ambient, eco) {
        now.longValue = Stopwatch.elapsed()
        if (running) {
            val step = if (ambient || eco) 1000L else 100L
            while (true) {
                withFrameMillis { now.longValue = Stopwatch.elapsed() }
                delay(step - Stopwatch.elapsed() % step)
            }
        }
    }

    // Verrou tactile : actif tant que le chrono tourne (si l'option est activée)
    LaunchedEffect(running, Settings.lock) { Ui.locked = running && Settings.lock }

    // Luminosité du mode éco
    LaunchedEffect(eco, Settings.ecoBrightness) {
        (ctx as? Activity)?.window?.let { w ->
            val lp = w.attributes
            lp.screenBrightness =
                if (eco) (Settings.ecoBrightness / 100f).coerceAtLeast(0.01f)
                else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            w.attributes = lp
        }
    }

    // Statistiques recalculées seulement quand la liste des tours change
    val laps = Stopwatch.laps
    val lastTotal = laps.firstOrNull()?.total ?: 0L
    val stats = remember(laps.size, lastTotal) {
        LapStats(
            laps.minOfOrNull { it.lapTime } ?: 0L,
            laps.maxOfOrNull { it.lapTime } ?: 0L,
            if (laps.isEmpty()) 0L else laps.sumOf { it.lapTime } / laps.size
        )
    }
    // Bague : 1 tour = 1' au départ, puis = meilleur tour
    val ringRef = if (laps.isEmpty()) 60_000L else stats.min.coerceAtLeast(3000L)
    val idle = !running && acc == 0L

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when {
                ambient -> AmbientScreen(now)
                Ui.screen == Screen.SETTINGS -> SettingsScreen()
                Ui.screen == Screen.HISTORY -> HistoryScreen()
                Ui.screen == Screen.SESSION -> SessionScreen()
                else -> {
                    if (Settings.ringActive) {
                        Ring { (((now.longValue - lastTotal).coerceAtLeast(0L)) % ringRef).toFloat() / ringRef }
                    }
                    HoldRing()
                    ScalingLazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item { Header(now, running, eco, pal, laps.isNotEmpty()) }
                        if (laps.size >= 2) {
                            item {
                                Text(
                                    "Moy. " + fmtFull(stats.avg),
                                    fontSize = Settings.textSp.sp,
                                    color = lapColor(stats.avg, stats.min, stats.max, eco, pal),
                                    style = Tnum,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                        itemsIndexed(laps) { i, lap ->
                            LapRow(
                                lap, laps.getOrNull(i + 1)?.lapTime,
                                lapColor(lap.lapTime, stats.min, stats.max, eco, pal),
                                lapMarker(lap.lapTime, stats.min, stats.max, laps.size),
                                Settings.textSp
                            )
                        }
                    }
                    if (idle) {
                        Box(
                            Modifier.fillMaxSize().padding(top = 16.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MenuButton { Ui.screen = Screen.SETTINGS }
                                HistoryButton { Ui.screen = Screen.HISTORY }
                            }
                        }
                    }
                    if (Ui.locked) {
                        Box(
                            Modifier.fillMaxSize().pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent().changes.forEach { it.consume() }
                                    }
                                }
                            },
                            contentAlignment = Alignment.TopCenter
                        ) { Box(Modifier.padding(top = 18.dp)) { LockIcon() } }
                    }
                }
            }
        }
    }
}

@Composable
fun Header(now: MutableLongState, running: Boolean, eco: Boolean, pal: Palette, hasLaps: Boolean) {
    val ctx = LocalContext.current
    val undoOn = Settings.undoBtn
    val bs = if (undoOn) 48.dp else 58.dp
    val gap = if (undoOn) 8.dp else 10.dp
    val fs = if (undoOn) 11.sp else 12.sp
    val startBg = if (eco) Color.White else pal.accent
    val startFg = if (eco) Color.Black else pal.onAccent
    val stopBg = if (eco) OffTrack else pal.inverseDark

    val undo: @Composable () -> Unit = {
        RoundButton("Annuler", bs, SurfaceBtn, Color.White, 10.sp, hasLaps) { Stopwatch.undoLap(ctx) }
    }
    val left: @Composable () -> Unit = {
        if (running) {
            RoundButton("Tour", bs, SurfaceBtn, Color.White, fs) { Stopwatch.lap(ctx) }
        } else {
            // Reset protégé : maintenir 3 s (snake blanc = progression)
            HoldButton("Reset", bs, SurfaceBtn, Color.White, fs, 3000L) { Stopwatch.reset(ctx) }
        }
    }
    val right: @Composable () -> Unit = {
        if (running) {
            RoundButton("Stop", bs, stopBg, Color.White, fs) { Stopwatch.toggle(ctx) }
        } else {
            RoundButton("Start", bs, startBg, startFg, fs) { Stopwatch.toggle(ctx) }
        }
    }
    val order = if (undoOn) listOf(undo, left, right) else listOf(left, right)
    val shown = if (Settings.lefty) order.reversed() else order

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TimeText(now, eco)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            for (b in shown) b()
        }
    }
}
