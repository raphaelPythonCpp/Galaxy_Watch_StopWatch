package com.example.chrono

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material.*
import kotlinx.coroutines.delay

private const val HOLD_LOCK_MS = 3000L

class MainActivity : ComponentActivity() {
    private var lastWake = 0L

    // Appui long sur le bouton du bas (3 s) : bascule le verrou tactile. Minuterie propre :
    // elle ne dépend pas des callbacks « long press » du système, ni de la réception du relâchement.
    private val handler = Handler(Looper.getMainLooper())
    private var downAt = 0L
    private var tracking = false
    private var holdFired = false
    private val tick = object : Runnable {
        override fun run() {
            val p = (SystemClock.uptimeMillis() - downAt).toFloat() / HOLD_LOCK_MS
            if (p >= 1f) {
                holdFired = true
                Ui.hold = 0f
                toggleLock()
            } else {
                Ui.hold = if (p > 0.15f) p else 0f
                handler.postDelayed(this, 16)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Stopwatch.load(this)
        Settings.load(this)
        History.load(this)
        Ui.ambient = false
        Ui.locked = false     // jamais verrouillé au (re)démarrage de l'appli
        Ui.hold = 0f

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

    override fun onStart() {
        super.onStart()
        lastWake = SystemClock.uptimeMillis()
    }

    override fun onStop() {
        handler.removeCallbacks(tick)
        tracking = false
        Ui.hold = 0f
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) lastWake = SystemClock.uptimeMillis()
    }

    /** Le verrou n'a de sens que : option activée + chrono en marche + écran principal. */
    private fun holdApplies() =
        Settings.lock && Stopwatch.running && Ui.screen == Screen.MAIN && !Ui.ambient

    private fun toggleLock() {
        if (Settings.lock && Stopwatch.running) {
            Ui.locked = !Ui.locked
            Stopwatch.buzz(this, true)
        }
    }

    // Bouton physique du bas (Retour)
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event?.repeatCount == 0) {
                holdFired = false
                if (holdApplies()) {
                    // Appui court = action (au relâchement) ; maintenu 3 s = bascule du verrou tactile
                    tracking = true
                    downAt = SystemClock.uptimeMillis()
                    handler.removeCallbacks(tick)
                    handler.postDelayed(tick, 16)
                } else {
                    handleButton()
                }
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (tracking) {
                tracking = false
                handler.removeCallbacks(tick)
                Ui.hold = 0f
                if (!holdFired) handleButton()
            }
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
    val holdColor = if (eco) Color.White else pal.accent

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

    // Verrou tactile : s'enclenche seulement quand on DÉMARRE le chrono pendant cette session de l'appli
    // (jamais restauré après une fermeture ou un arrêt forcé), et se lève quand le chrono s'arrête.
    var wasRunning by remember { mutableStateOf(running) }
    LaunchedEffect(running) {
        if (running && !wasRunning && Settings.lock) Ui.locked = true
        if (!running) Ui.locked = false
        wasRunning = running
    }

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
    // Bague : 1 tour = 1' au départ, puis = meilleur tour (sans limite basse, seulement >= 1 ms)
    val ringRef = if (laps.isEmpty()) 60_000L else stats.min.coerceAtLeast(1L)
    val idle = !running && acc == 0L
    val topF = Settings.topPct / 100f

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
                    Column(Modifier.fillMaxSize()) {
                        // Partie du haut : temps + boutons (réduite pour tenir si l'espace est petit)
                        if (topF > 0.04f) {
                            Box(
                                Modifier.fillMaxWidth().weight(topF).clipToBounds(),
                                contentAlignment = Alignment.Center
                            ) {
                                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    val need = if (Settings.undoBtn) 100.dp else 112.dp
                                    val s = (maxHeight / need).coerceIn(0.35f, 1f)
                                    Box(
                                        Modifier.wrapContentSize(Alignment.Center, unbounded = true)
                                            .graphicsLayer { scaleX = s; scaleY = s }
                                    ) { Header(now, running, eco, pal, laps.isNotEmpty()) }
                                }
                            }
                        }
                        // Partie du bas : moyenne + tours
                        if (topF < 0.96f) {
                            Box(Modifier.fillMaxWidth().weight(1f - topF)) {
                                ScalingLazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (laps.size >= 2) {
                                        item {
                                            Text(
                                                "Moy. " + fmtFull(stats.avg),
                                                fontSize = Settings.textSp.sp,
                                                color = lapColor(stats.avg, stats.min, stats.max, eco, pal),
                                                style = Tnum
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
                            }
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
                        // Bloque tout le tactile...
                        Box(
                            Modifier.fillMaxSize().pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent().changes.forEach { it.consume() }
                                    }
                                }
                            }
                        )
                        // ... sauf le cadenas : le maintenir 3 s déverrouille (2e sortie, en plus du bouton du bas)
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            Box(
                                Modifier.padding(top = 8.dp).size(44.dp).clip(CircleShape)
                                    .holdToConfirm(3000L) {
                                        Ui.locked = false
                                        Stopwatch.buzz(ctx, true)
                                    },
                                contentAlignment = Alignment.Center
                            ) { LockIcon() }
                        }
                    }
                }
            }
            // Progression des appuis longs (Reset, effacement de l'historique, verrou) : arc plein, couleur de référence
            HoldRing(holdColor)
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
            // Reset protégé : maintenir 2 s (snake = progression)
            HoldButton("Reset", bs, SurfaceBtn, Color.White, fs, 2000L) { Stopwatch.reset(ctx) }
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
