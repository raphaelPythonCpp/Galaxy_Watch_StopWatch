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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var lastWake = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Stopwatch.load(this)       // charge aussi les réglages ; détecte un « Forcer l'arrêt » précédent
        Settings.load(this)
        History.load(this)
        Ui.ambient = false
        Ui.locked = false          // jamais verrouillé au (re)démarrage de l'appli
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
        checkAutoUnlock()
        Stopwatch.enforceAutoStop(this)
    }

    override fun onStop() {
        Ui.hold = 0f
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            lastWake = SystemClock.uptimeMillis()
            checkAutoUnlock()
        }
    }

    /** Filet de sécurité : levée du verrou tactile une fois le délai réglé écoulé (même écran éteint entre-temps). */
    private fun checkAutoUnlock() {
        val m = Settings.autoUnlockMin
        if (Ui.locked && m > 0 && SystemClock.elapsedRealtime() - Ui.lockedAt >= m * 60_000L) {
            Ui.locked = false
        }
    }

    // Bouton physique du bas (Retour) : appui simple uniquement (l'appui long appartient à Samsung)
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
        if (Ui.screen != Screen.MAIN) {
            // retour : sous-menu -> réglages, séance -> historique, le reste -> écran principal
            Ui.screen = when (Ui.screen) {
                Screen.SESSION -> Screen.HISTORY
                Screen.RANGE -> Screen.SETTINGS
                else -> Screen.MAIN
            }
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

private val LightColors = Colors(
    background = Color.White,
    onBackground = Color.Black,
    surface = Color(0xFFE6E6E6),
    onSurface = Color.Black,
    onSurfaceVariant = Color(0xFF444444)
)

@Composable
fun App() {
    val ctx = LocalContext.current
    val cfg = LocalConfiguration.current
    val hDp = cfg.screenHeightDp.toFloat()                  // diamètre vertical de la montre
    val availW = (cfg.screenWidthDp * Settings.lapWidthPct / 100f).dp
    val now = remember { mutableLongStateOf(Stopwatch.elapsed()) }
    val running = Stopwatch.running
    val acc = Stopwatch.accumulated
    val ambient = Ui.ambient
    val eco = Settings.eco
    val pal = remember(Settings.rgb) { makePalette(Settings.rgb) }
    val holdColor = if (eco) Fg else pal.accent

    // Mise à jour : 0,1 s (normal) ou 1 s (éco / ambiant), calée sur le changement de chiffre.
    // withFrameMillis ne reprend que si l'écran est visible : aucun réveil quand l'écran est éteint.
    LaunchedEffect(running, acc, ambient, eco) {
        now.longValue = Stopwatch.elapsed()
        if (running) {
            val step = if (ambient || eco) 1000L else 100L
            while (true) {
                withFrameMillis { now.longValue = Stopwatch.elapsed() }
                Stopwatch.enforceAutoStop(ctx)
                delay(step - Stopwatch.elapsed() % step)
            }
        }
    }

    // Verrou tactile : s'enclenche seulement quand on DÉMARRE le chrono pendant cette session de l'appli
    var wasRunning by remember { mutableStateOf(running) }
    LaunchedEffect(running) {
        if (running && !wasRunning && Settings.lock) Ui.locked = true
        if (!running) Ui.locked = false
        wasRunning = running
    }

    // Déverrouillage automatique après N minutes (0 = jamais)
    LaunchedEffect(Ui.locked, Settings.autoUnlockMin) {
        if (Ui.locked) {
            Ui.lockedAt = SystemClock.elapsedRealtime()
            val m = Settings.autoUnlockMin
            if (m > 0) {
                delay(m * 60_000L)
                Ui.locked = false
            }
        }
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
    val cols = rememberLapCols(laps, true, Settings.textSp)
    // Bague : 1 tour = 1' au départ, puis = meilleur tour (sans limite basse, seulement >= 1 ms)
    val ringRef = if (laps.isEmpty()) 60_000L else stats.min.coerceAtLeast(1L)
    val idle = !running && acc == 0L
    val topF = Settings.topPct / 100f
    val showTop = topF > 0.04f
    val showBottom = topF < 0.96f
    val gapBtnLap = (Settings.gapBtnLapPct / 100f * hDp).dp
    val lineGap = (Settings.lineGapPct / 100f * hDp).dp

    // Défilement de la liste des tours : bague rotative native (tactile Samsung) + secours au doigt
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val lineStepPx = with(LocalDensity.current) { (Settings.textSp * 0.8f).sp.toPx() + lineGap.toPx() }
    LaunchedEffect(Ui.screen, ambient) {
        try { focus.requestFocus() } catch (e: Exception) { }
    }

    MaterialTheme(colors = if (Settings.light) LightColors else Colors()) {
        Box(
            Modifier.fillMaxSize().background(Bg)
                .circularScroll(
                    enabled = {
                        Ui.screen == Screen.MAIN && !Ui.ambient && Settings.touchRing &&
                            (!Ui.locked || Settings.ringInLock)
                    },
                    onLines = { n -> listState.dispatchRawDelta(n * lineStepPx) }
                )
        ) {
            when {
                ambient -> AmbientScreen(now)
                Ui.screen == Screen.SETTINGS -> SettingsScreen()
                Ui.screen == Screen.RANGE -> RangeScreen()
                Ui.screen == Screen.HISTORY -> HistoryScreen()
                Ui.screen == Screen.SESSION -> SessionScreen()
                else -> {
                    if (Settings.ringActive) {
                        Ring(Settings.snakePct / 100f) {
                            (((now.longValue - lastTotal).coerceAtLeast(0L)) % ringRef).toFloat() / ringRef
                        }
                    }
                    Column(
                        Modifier.fillMaxSize()
                            .onRotaryScrollEvent { e ->
                                if (!Ui.locked || Settings.ringInLock) {
                                    listState.dispatchRawDelta(e.verticalScrollPixels)
                                    true
                                } else false
                            }
                            .focusRequester(focus)
                            .focusable()
                    ) {
                        // Haut : temps + cercles, collés au bas de leur zone (la frontière = « Haut de l'écran »)
                        if (showTop) {
                            Box(
                                Modifier.fillMaxWidth().weight(topF).clipToBounds(),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(Modifier.wrapContentSize(Alignment.BottomCenter, unbounded = true)) {
                                    Header(now, running, eco, pal, laps.isNotEmpty(), hDp)
                                }
                            }
                        }
                        if (showTop && showBottom && gapBtnLap > 0.dp) {
                            Spacer(Modifier.height(gapBtnLap))
                        }
                        // Bas : liste simple, démarre en haut de sa zone ; marge de fin = n'importe quel tour peut monter tout en haut
                        if (showBottom) {
                            Box(
                                Modifier.fillMaxWidth().weight(1f - topF)
                                    .then(if (Settings.fade) Modifier.edgeFade() else Modifier)
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(lineGap),
                                    contentPadding = PaddingValues(bottom = ((1f - topF) * hDp).dp)
                                ) {
                                    if (laps.size >= 2) {
                                        item(key = "avg") {
                                            Text(
                                                S.AVG.t() + " " + fmtFull(stats.avg),
                                                fontSize = Settings.textSp.sp,
                                                color = lapColor(stats.avg, stats.min, stats.max, eco, pal),
                                                style = tight(Settings.textSp)
                                            )
                                        }
                                    }
                                    itemsIndexed(laps, key = { _, lap -> lap.index }) { i, lap ->
                                        LapRow(
                                            lap, laps.getOrNull(i + 1)?.lapTime,
                                            lapColor(lap.lapTime, stats.min, stats.max, eco, pal),
                                            lapMarker(lap.lapTime, stats.min, stats.max, laps.size),
                                            Settings.textSp, cols, availW
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
                        // ... sauf le cadenas : le maintenir (temps de maintien des réglages) déverrouille
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            Box(
                                Modifier.padding(top = 8.dp).size(44.dp).clip(CircleShape)
                                    .holdToConfirm { Ui.locked = false },
                                contentAlignment = Alignment.Center
                            ) { LockIcon() }
                        }
                    }
                }
            }
            // Progression des appuis longs (Reset, effacement, cadenas, sous-menus, mise en page par défaut)
            HoldRing(holdColor)
        }
    }
}

@Composable
fun Header(now: MutableLongState, running: Boolean, eco: Boolean, pal: Palette, hasLaps: Boolean, hDp: Float) {
    val ctx = LocalContext.current
    val undoOn = Settings.undoBtn
    // Diamètre réglé par le curseur « Cercles » ; réduit de 17 % quand le 3e cercle (Annuler) est présent
    val bs = (Settings.btnDp * (if (undoOn) 0.83f else 1f)).dp
    val fs = (bs.value * 0.205f).sp
    val startBg = if (eco) Fg else pal.accent
    val startFg = if (eco) Bg else pal.onAccent
    val stopBg = if (eco) OffTrack else pal.inverseDark

    val undo: @Composable () -> Unit = {
        RoundButton(S.UNDO.t(), bs, SurfaceBtn, Fg, (bs.value * 0.19f).sp, hasLaps) { Stopwatch.undoLap(ctx) }
    }
    val left: @Composable () -> Unit = {
        if (running) {
            RoundButton(S.LAP.t(), bs, SurfaceBtn, Fg, fs) { Stopwatch.lap(ctx) }
        } else {
            // Reset protégé : maintenir (temps réglable) ; la vibration est déjà donnée par l'appui long
            HoldButton(S.RESET.t(), bs, SurfaceBtn, Fg, fs) { Stopwatch.reset(ctx, false) }
        }
    }
    val right: @Composable () -> Unit = {
        if (running) {
            RoundButton(S.STOP.t(), bs, stopBg, Color.White, fs) { Stopwatch.toggle(ctx) }
        } else {
            RoundButton(S.START.t(), bs, startBg, startFg, fs) { Stopwatch.toggle(ctx) }
        }
    }
    val order = if (undoOn) listOf(undo, left, right) else listOf(left, right)
    val shown = if (Settings.lefty) order.reversed() else order

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TimeText(now, eco)
        Spacer(Modifier.height((Settings.gapTimeBtnPct / 100f * hDp).dp))
        Row(horizontalArrangement = Arrangement.spacedBy((Settings.btnGapPct / 100f * hDp).dp)) {
            for (b in shown) b()
        }
    }
}
