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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var lastWake = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Stopwatch.load(this)       // charge aussi les réglages ; détecte un « Forcer l'arrêt » précédent
        Settings.load(this)
        History.load(this)
        Ui.ambient = false
        Ui.soft = false
        Ui.lastInput = SystemClock.uptimeMillis()
        Ui.locked = false          // jamais verrouillé au (re)démarrage de l'appli
        Ui.hold = 0f
        Ui.warn = -1

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
        Ui.wake()
        checkAutoUnlock()
        Stopwatch.enforceAutoStop(this)
    }

    override fun onStop() {
        Ui.hold = 0f
        Stopwatch.markSeen(this)
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
        val wasSoft = Ui.soft
        Ui.wake()                                   // note l'interaction ; sort de la veille douce
        if (wasSoft) return                         // veille douce : le premier appui réveille seulement
        if (Ui.warn >= 0) { Ui.warn = -1; return }
        if (Ui.screen != Screen.MAIN) {
            // retour : codes QR -> séance, séance -> historique, sous-menus -> leur écran d'origine, le reste -> principal
            Ui.screen = when (Ui.screen) {
                Screen.QR -> Screen.SESSION
                Screen.SESSION -> Screen.HISTORY
                Screen.RANGE -> Ui.rangeFrom
                Screen.INFO -> Ui.infoFrom
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
    val accent = if (eco) Fg else pal.accent

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

    // Affichage permanent (logiciel) : l'écran reste allumé tant que le chrono tourne ou affiche un temps. Après le délai
    // réglé sans interaction, veille douce : noir, chiffres gris, luminosité minimale (un toucher ou le bouton réveille).
    val keepOn = Settings.aodActive && (running || acc > 0L)
    LaunchedEffect(keepOn) {
        val w = (ctx as? Activity)?.window
        if (keepOn) w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (keepOn) Ui.lastInput = SystemClock.uptimeMillis()
        else if (Ui.soft) { Ui.soft = false; Ui.ambient = false }
        while (keepOn) {
            delay(1000)
            if (!Ui.ambient && SystemClock.uptimeMillis() - Ui.lastInput >= Settings.aodDelayMs) {
                Ui.soft = true
                Ui.ambient = true
            }
        }
    }

    // Luminosité : minimale en veille douce ; réglée en mode éco ; sinon celle du système
    val dimmed = Ui.ambient && Ui.soft
    LaunchedEffect(eco, Settings.ecoBrightness, dimmed) {
        (ctx as? Activity)?.window?.let { w ->
            val lp = w.attributes
            lp.screenBrightness =
                if (dimmed) 0.01f
                else if (eco) (Settings.ecoBrightness / 100f).coerceAtLeast(0.01f)
                else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            w.attributes = lp
        }
    }

    // Retour à l'écran principal : les positions de défilement mémorisées des réglages sont oubliées
    LaunchedEffect(Ui.screen) { if (Ui.screen == Screen.MAIN) Ui.savedPos.clear() }

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
    // Bague : 1 tour = 1' au départ, puis = meilleur tour ; avec un objectif d'exercice, = le segment en cours
    val ringRef = if (laps.isEmpty()) 60_000L else stats.min.coerceAtLeast(1L)
    val idle = !running && acc == 0L
    val topF = Settings.topPct / 100f
    val showTop = topF > 0.04f
    val showBottom = topF < 0.96f
    val gapBtnLap = (Settings.gapBtnLapPct / 100f * hDp).dp
    val lineGap = (Settings.lineGapPct / 100f * hDp).dp
    val showAvg = Settings.showAvg && laps.size >= 2

    // Liste des tours : démarre en haut ; si on est en haut, un nouveau tour reste visible en haut (option « défilement
    // auto ») ; sinon la vue ne bouge pas (on compense l'élément ajouté en tête).
    val listState = rememberLazyListState()
    val itemCount = laps.size + (if (showAvg) 1 else 0)
    var prevCount by remember { mutableIntStateOf(itemCount) }
    LaunchedEffect(itemCount) {
        val d = itemCount - prevCount
        prevCount = itemCount
        if (itemCount == 0) {
            listState.scrollToItem(0)
        } else if (d > 0) {
            val idx = listState.firstVisibleItemIndex
            val off = listState.firstVisibleItemScrollOffset
            if (idx > 0 || off > 0 || !Settings.autoScroll) listState.scrollToItem(idx + d, off)
        }
    }

    // Bague rotative native (tactile Samsung) : exactement 1 ligne par cran
    val focus = remember { FocusRequester() }
    val step = Settings.seriesLen
    val lineStepPx = with(LocalDensity.current) { (Settings.textSp * 0.8f).sp.toPx() + lineGap.toPx() }
    LaunchedEffect(Ui.screen, ambient) {
        try { focus.requestFocus() } catch (e: Exception) { }
    }

    MaterialTheme(colors = if (Settings.light) LightColors else Colors()) {
        Box(
            Modifier.fillMaxSize().background(Bg)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            Ui.lastInput = SystemClock.uptimeMillis()
                        }
                    }
                }
        ) {
            when {
                ambient -> AmbientScreen(now)
                Ui.screen == Screen.SETTINGS -> SettingsScreen()
                Ui.screen == Screen.RANGE -> RangeScreen()
                Ui.screen == Screen.INFO -> InfoScreen()
                Ui.screen == Screen.TRACK -> TrackScreen()
                Ui.screen == Screen.HELP -> HelpScreen()
                Ui.screen == Screen.HISTORY -> HistoryScreen()
                Ui.screen == Screen.SESSION -> SessionScreen()
                Ui.screen == Screen.QR -> QrScreen()
                else -> {
                    if (Settings.ringActive) {
                        Ring(Settings.snakePct / 100f) {
                            val e = now.longValue
                            if (Segments.enabled()) Segments.ringFrac(e)
                            else (((e - lastTotal).coerceAtLeast(0L)) % ringRef).toFloat() / ringRef
                        }
                    }
                    Column(
                        Modifier.fillMaxSize()
                            .onRotaryScrollEvent { e ->
                                if (!Ui.locked || Settings.ringInLock) {
                                    Ui.lastInput = SystemClock.uptimeMillis()
                                    val d = e.verticalScrollPixels
                                    if (d != 0f) listState.dispatchRawDelta((if (d > 0f) 1f else -1f) * lineStepPx)   // 1 ligne, pas plus
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
                                    if (showAvg) {
                                        item {
                                            Text(
                                                S.AVG.t() + " " + fmtFull(stats.avg),
                                                fontSize = Settings.textSp.sp,
                                                color = lapColor(stats.avg, stats.min, stats.max, eco, pal),
                                                style = tight(Settings.textSp)
                                            )
                                        }
                                    }
                                    itemsIndexed(laps) { i, lap ->
                                        // ▲▼ : comparaison avec le tour précédent (ou le tour de même rang de la série précédente)
                                        val marker = if (Settings.showMarkers) relMarker(lap.lapTime, laps.getOrNull(i + step)?.lapTime) else 0
                                        LapRow(
                                            lap, laps.getOrNull(i + 1)?.lapTime,
                                            lapColor(lap.lapTime, stats.min, stats.max, eco, pal),
                                            marker, Settings.textSp, cols, availW
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (Ui.locked) {
                        // Bloque tout le tactile (les icônes du haut, dessinées ensuite, restent actives par appui long)
                        Box(
                            Modifier.fillMaxSize().pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent().changes.forEach { it.consume() }
                                    }
                                }
                            }
                        )
                    }
                    // Icônes du haut, centrées ensemble.
                    // Repos : réglages, historique, [aide], [suivi] (appui simple).
                    // Activité : [réglages], [suivi] (appui long) et cadenas (appui long = déverrouille).
                    Box(
                        Modifier.fillMaxSize().padding(top = 14.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (idle) {
                                TopIcon(false, { Ui.screen = Screen.SETTINGS }) { MenuGlyph() }
                                TopIcon(false, { Ui.screen = Screen.HISTORY }) { HistoryGlyph() }
                                if (Settings.help) TopIcon(false, { Ui.screen = Screen.HELP }) { HelpGlyph() }
                                if (Settings.track) TopIcon(false, { Ui.screen = Screen.TRACK }) { TrackGlyph() }
                            } else {
                                if (Settings.runIcons) TopIcon(true, { Ui.screen = Screen.SETTINGS }) { MenuGlyph() }
                                if (Settings.track) TopIcon(true, { Ui.screen = Screen.TRACK }) { TrackGlyph() }
                                if (Ui.locked) TopIcon(true, { Ui.locked = false }) { LockIcon() }
                            }
                        }
                    }
                }
            }
            // Progression des appuis longs (Reset, effacement, cadenas, sous-menus, mise en page par défaut)
            HoldRing(accent)

            // Avertissement avant d'activer le mode clair (0) ou l'always-on display (1), ou de désactiver « Réglages en activité » (2)
            if (Ui.warn >= 0) {
                WarnDialog(
                    Ui.warn, accent,
                    onOk = {
                        when (Ui.warn) {
                            0 -> Settings.setLight(ctx, true)
                            1 -> {
                                Settings.setAod(ctx, true)
                                (ctx as? Activity)?.recreate()
                            }
                            else -> Settings.setRunIcons(ctx, false)
                        }
                        Ui.warn = -1
                    },
                    onCancel = { Ui.warn = -1 }
                )
            }
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

    // Espace sous la ligne de base des chiffres (rapporté à la taille de police), mesuré une fois :
    // 0 % d'écart = le bas des chiffres touche le haut des cercles ; une valeur négative les fait se chevaucher.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val padRatio = remember(density) {
        val r = measurer.measure("0", tight(100f).copy(fontSize = 100.sp, fontWeight = FontWeight.Medium))
        ((r.size.height - r.lastBaseline) / with(density) { 100.sp.toPx() }).coerceAtLeast(0f)
    }
    val over1h by remember(now) { derivedStateOf { now.longValue >= 3_600_000L } }
    val gapPct = Settings.gapTimeBtnPct

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
        Row(
            Modifier.layout { m, c ->
                val p = m.measure(c)
                val big = if (!Settings.secMode && over1h) Settings.chronoSp * 0.79f else Settings.chronoSp
                val g = (gapPct / 100f * hDp).dp.roundToPx() - (padRatio * big.sp.toPx()).roundToInt()
                layout(p.width, maxOf(0, p.height + g)) { p.place(0, g) }
            },
            horizontalArrangement = Arrangement.spacedBy((Settings.btnGapPct / 100f * hDp).dp)
        ) {
            for (b in shown) b()
        }
    }
}
