package com.example.chrono

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

private fun fmtDate(ts: Long): String =
    SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(Date(ts))

/**
 * État de défilement mémorisé par écran : en revenant d'un sous-menu (plage min/max, explication),
 * la liste retrouve exactement sa place. La mémoire est vidée au retour à l'écran principal.
 */
@Composable
private fun rememberSavedListState(screen: Screen): ScalingLazyListState {
    val saved = Ui.savedPos[screen]
    val st = rememberScalingLazyListState(
        initialCenterItemIndex = saved?.first ?: 1,
        initialCenterItemScrollOffset = saved?.second ?: 0
    )
    DisposableEffect(Unit) {
        onDispose {
            try { Ui.savedPos[screen] = st.centerItemIndex to st.centerItemScrollOffset } catch (e: Exception) { }
        }
    }
    return st
}

@Composable
fun LogoBox(i: Int, selected: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Image(
        painter = painterResource(Logos.launcher[i]),
        contentDescription = null,
        modifier = modifier.aspectRatio(1f).clip(CircleShape)
            .border(if (selected) 2.dp else 0.dp, if (selected) accent else Color.Transparent, CircleShape)
            .tapOrHold(onTap = onClick, onHold = { Ui.openInfo("logo") })
    )
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val act = ctx as? Activity
    val eco = Settings.eco
    val pal = remember(Settings.rgb) { makePalette(Settings.rgb) }
    val accent = if (eco) Fg else pal.accent
    val onAcc = if (eco) Bg else pal.onAccent
    val listState = rememberSavedListState(Screen.SETTINGS)
    // Au moins une colonne doit rester affichée (Allure et Moyenne sont des plus)
    val colToggle: (Int, Boolean) -> Unit = { idx, v ->
        val count = listOf(Settings.colNum, Settings.colTotal, Settings.colLap, Settings.colDelta).count { it }
        if (idx == 4 || v || count > 1) Settings.setCol(ctx, idx, v)
    }

    ScalingLazyColumn(Modifier.fillMaxSize(), state = listState, horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text(S.SETTINGS.t(), fontSize = 14.sp, color = Dim) }

        // ---------------- Essentiels : toujours affichés
        item {
            ToggleRow(S.ECO.t(), eco, true, accent, "eco") {
                Settings.setEco(ctx, it)
                act?.recreate()
            }
        }
        if (eco) {
            item { SliderRow(Settings.ecoBright, accent, unit = " %") }
        }
        item { SliderRow(Settings.hold, accent) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(S.COLOR.t(), fontSize = 13.sp, color = Dim)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(16.dp).clip(CircleShape).background(if (eco) Dim else pal.accent))
            }
        }
        item { SliderRow(Settings.colR, Color(0xFFEF4444), !eco) }
        item { SliderRow(Settings.colG, Color(0xFF22C55E), !eco) }
        item { SliderRow(Settings.colB, Color(0xFF3B82F6), !eco) }
        item { ToggleRow(S.LOCK.t(), Settings.lock, true, accent, "lock") { Settings.setLock(ctx, it) } }
        if (Settings.lock) {
            item {
                SliderRow(Settings.autoUnlock, accent,
                    fmt = { v -> if (v < 0.5f) S.NEVER.t() else fmtDur((v * 60f).roundToInt()) })
            }
            item {
                ToggleRow(S.RING_IN_LOCK.t(), Settings.ringInLock, true, accent, "ringInLock") { Settings.setRingInLock(ctx, it) }
            }
        }
        item {
            ToggleRow(S.AOD.t(), Settings.aodActive, !eco, accent, "aod") { v ->
                if (v) Ui.warn = 1                       // avertissement avant activation
                else { Settings.setAod(ctx, false); act?.recreate() }
            }
        }
        if (Settings.aodActive) {
            item { SliderRow(Settings.aodDelay, accent) }
        }
        item {
            ToggleRow(S.RUN_ICONS.t(), Settings.runIcons, true, accent, "runIcons") { v ->
                if (v) Settings.setRunIcons(ctx, true)
                else Ui.warn = 2                         // avertissement avant désactivation
            }
        }
        item { ToggleRow(S.HELP_TOGGLE.t(), Settings.help, true, accent, "help") { Settings.setHelp(ctx, it) } }
        item { ToggleRow(S.TRACK.t(), Settings.track, true, accent, "track") { Settings.setTrack(ctx, it) } }
        item { ToggleRow(S.CUSTOM.t(), Settings.custom, true, accent, "custom") { Settings.setCustom(ctx, it) } }

        // ---------------- Personnalisation : tout le reste
        if (Settings.custom) {
            item { Text(S.LANGUAGE.t(), fontSize = 13.sp, color = Dim) }
            item {
                Row(Modifier.fillMaxWidth(0.92f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HalfToggle("Français", Settings.lang == Lang.FR, accent, onAcc, Modifier.weight(1f), 11f, "lang") {
                        Settings.setLang(ctx, Lang.FR)
                    }
                    HalfToggle("English", Settings.lang == Lang.EN, accent, onAcc, Modifier.weight(1f), 11f, "lang") {
                        Settings.setLang(ctx, Lang.EN)
                    }
                    HalfToggle("中文", Settings.lang == Lang.ZH, accent, onAcc, Modifier.weight(1f), 11f, "lang") {
                        Settings.setLang(ctx, Lang.ZH)
                    }
                }
            }

            item { Text(S.LOGO.t(), fontSize = 13.sp, color = Dim) }
            for (row in 0 until 2) {
                item {
                    Row(Modifier.fillMaxWidth(0.92f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (col in 0 until 5) {
                            val i = row * 5 + col
                            LogoBox(i, Settings.logo == i, accent, Modifier.weight(1f)) { Settings.setLogo(ctx, i) }
                        }
                    }
                }
            }

            item {
                ToggleRow(S.LIGHT.t(), Settings.light, true, accent, "light") { v ->
                    if (v) Ui.warn = 0                   // avertissement avant activation
                    else Settings.setLight(ctx, false)
                }
            }
            item { ToggleRow(S.RING.t(), Settings.ringActive, !eco, accent, "ring") { Settings.setRing(ctx, it) } }
            item { SliderRow(Settings.snake, accent, !eco, " %") }
            item { ToggleRow(S.AUTO_SCROLL.t(), Settings.autoScroll, true, accent, "autoScroll") { Settings.setAutoScroll(ctx, it) } }
            item { ToggleRow(S.LEFTY.t(), Settings.lefty, true, accent, "lefty") { Settings.setLefty(ctx, it) } }
            item { ToggleRow(S.UNDO_BTN.t(), Settings.undoBtn, true, accent, "undo") { Settings.setUndoBtn(ctx, it) } }
            item { ToggleRow(S.SECONDS.t(), Settings.secMode, true, accent, "sec") { Settings.setSecMode(ctx, it) } }
            item { ToggleRow(S.FADE.t(), Settings.fade, true, accent, "fade") { Settings.setFade(ctx, it) } }
            item { ToggleRow(S.MARKERS.t(), Settings.showMarkers, true, accent, "markers") { Settings.setShowMarkers(ctx, it) } }

            // Colonnes : 2 - 2 - 2 (la moyenne est le dernier choix)
            item { Text(S.COLS.t(), fontSize = 13.sp, color = Dim) }
            item {
                Row(Modifier.fillMaxWidth(0.92f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HalfToggle(S.COL_NUM.t(), Settings.colNum, accent, onAcc, Modifier.weight(1f), info = "colNum") { colToggle(0, it) }
                    HalfToggle(S.COL_TOTAL.t(), Settings.colTotal, accent, onAcc, Modifier.weight(1f), info = "colTotal") { colToggle(1, it) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(0.92f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HalfToggle(S.COL_LAP.t(), Settings.colLap, accent, onAcc, Modifier.weight(1f), info = "colLap") { colToggle(2, it) }
                    HalfToggle(S.COL_DELTA.t(), Settings.colDelta, accent, onAcc, Modifier.weight(1f), info = "colDelta") { colToggle(3, it) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(0.92f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HalfToggle(S.COL_PACE.t(), Settings.colPace, accent, onAcc, Modifier.weight(1f), info = "colPace") { colToggle(4, it) }
                    HalfToggle(S.AVG.t(), Settings.showAvg, accent, onAcc, Modifier.weight(1f), info = "showAvg") { Settings.setShowAvg(ctx, it) }
                }
            }

            item { Text(S.QR_SECTION.t(), fontSize = 13.sp, color = Dim) }
            item {
                SliderRow(
                    Settings.qrVer, accent,
                    fmt = { v ->
                        val ver = v.roundToInt()
                        "v$ver · ≈" + qrLapsApprox(ver, Settings.qrLevel) + " " + S.QR_LAPS.t()
                    }
                )
            }
            item {
                SliderRow(
                    Settings.qrEc, accent,
                    fmt = { v ->
                        val l = v.roundToInt().coerceIn(0, 3)
                        QR_LEVELS[l] + " (" + QR_RECOVERY[l] + ") · ≈" +
                            qrLapsApprox(Settings.qrVersion, l) + " " + S.QR_LAPS.t()
                    }
                )
            }

            item { Text(S.SIZES.t(), fontSize = 13.sp, color = Dim) }
            item { SliderRow(Settings.chrono, accent, unit = " sp") }
            item { SliderRow(Settings.btn, accent, unit = " dp") }
            item { SliderRow(Settings.lap, accent, unit = " sp") }

            item { Text(S.SPACING.t(), fontSize = 13.sp, color = Dim) }
            item { SliderRow(Settings.gapTimeBtn, accent, unit = " %") }
            item { SliderRow(Settings.btnGap, accent, unit = " %") }
            item { SliderRow(Settings.gapBtnLap, accent, unit = " %") }
            item { SliderRow(Settings.lineGap, accent, unit = " %") }

            item { Text(S.LAYOUT.t(), fontSize = 13.sp, color = Dim) }
            item { SliderRow(Settings.topPctS, accent, unit = " %") }
            item { SliderRow(Settings.lapWidth, accent, unit = " %") }
            item {
                Box(
                    Modifier.fillMaxWidth(0.92f).height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceBtn)
                        .holdToConfirm { Settings.resetLayout(ctx) },
                    contentAlignment = Alignment.Center
                ) { Text(S.LAYOUT_RESET.t(), fontSize = 10.sp, color = Fg) }
            }

            item { Text(S.BEHAVIOR.t(), fontSize = 13.sp, color = Dim) }
            item {
                SliderRow(
                    Settings.vibe, accent,
                    fmt = { v -> if (v < 0.05f) S.NONE.t() else Settings.vibe.text(v) + " %" },
                    onRelease = { Stopwatch.buzz(ctx, true) }   // aperçu de l'intensité réglée
                )
            }
            item {
                SliderRow(Settings.autoStop, accent,
                    fmt = { v -> if (v < 0.5f) S.OFF.t() else fmtDur((v * 60f).roundToInt()) })
            }
        }

        // ---------------- À propos
        item {
            Column(Modifier.padding(top = 10.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Super StopWatch  v11", fontSize = 11.sp, color = Dim, textAlign = TextAlign.Center)
                Text(S.ABOUT_BY.t(), fontSize = 9.sp, color = Dim, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Sous-menu d'un curseur : explication, puis plage min / max (bornes d'origine larges, 100 pas, min < max). */
@Composable
fun RangeScreen() {
    val ctx = LocalContext.current
    val eco = Settings.eco
    val pal = remember(Settings.rgb) { makePalette(Settings.rgb) }
    val accent = if (eco) Fg else pal.accent
    val spec = Settings.byKey[Ui.rangeKey]
    val info = Info.byKey[Ui.rangeKey]

    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (spec == null) {
            item { Text(S.NOT_FOUND.t(), fontSize = 12.sp, color = Dim) }
        } else {
            val step0 = spec.gap0
            val span = spec.max0 - spec.min0
            item {
                Text(spec.label.t(), fontSize = 14.sp, color = accent, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
            if (info != null) {
                info.body().split("\n").filter { it.isNotBlank() }.forEach { line ->
                    item { Text(line, fontSize = 11.sp, color = Fg, modifier = Modifier.fillMaxWidth(0.88f)) }
                }
            }
            item {
                Text(
                    S.RANGE.t() + " : " + spec.display(spec.lo, step0) + " – " + spec.display(spec.hi, step0),
                    fontSize = 11.sp, color = Dim, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            item {
                SliderBar(
                    S.MIN.t(), spec.display(spec.lo, step0), (spec.lo - spec.min0) / span, accent, true,
                    { Settings.persistSliders(ctx) }, null
                ) { f ->
                    val n = (f * 100f).roundToInt()
                    val v = spec.min0 + n / 100f * span
                    spec.setRange(minOf(v, spec.hi - step0).coerceAtLeast(spec.min0), spec.hi)
                }
            }
            item {
                SliderBar(
                    S.MAX.t(), spec.display(spec.hi, step0), (spec.hi - spec.min0) / span, accent, true,
                    { Settings.persistSliders(ctx) }, null
                ) { f ->
                    val n = (f * 100f).roundToInt()
                    val v = spec.min0 + n / 100f * span
                    spec.setRange(spec.lo, maxOf(v, spec.lo + step0).coerceAtMost(spec.max0))
                }
            }
            item {
                Box(
                    Modifier.fillMaxWidth(0.92f).height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceBtn)
                        .holdToConfirm { spec.resetRange(); Settings.persistSliders(ctx) },
                    contentAlignment = Alignment.Center
                ) { Text(S.RANGE_RESET.t(), fontSize = 10.sp, color = Fg) }
            }
        }
    }
}

/** Explication d'un réglage (appui long sur un interrupteur, une case ou un curseur à valeurs discrètes). */
@Composable
fun InfoScreen() {
    val eco = Settings.eco
    val pal = remember(Settings.rgb) { makePalette(Settings.rgb) }
    val accent = if (eco) Fg else pal.accent
    val info = Info.byKey[Ui.infoKey]

    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (info == null) {
            item { Text(S.NOT_FOUND.t(), fontSize = 12.sp, color = Dim) }
        } else {
            item {
                Text(info.title.t(), fontSize = 14.sp, color = accent, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
            info.body().split("\n").filter { it.isNotBlank() }.forEach { line ->
                item { Text(line, fontSize = 11.sp, color = Fg, modifier = Modifier.fillMaxWidth(0.88f)) }
            }
        }
    }
}

/** Suivi avancé : distance par tour (allure) + objectif d'exercice / repos. Accessible pendant l'activité. */
@Composable
fun TrackScreen() {
    val ctx = LocalContext.current
    val eco = Settings.eco
    val pal = remember(Settings.rgb) { makePalette(Settings.rgb) }
    val accent = if (eco) Fg else pal.accent
    val resched: () -> Unit = { Segments.schedule(ctx) }
    val listState = rememberSavedListState(Screen.TRACK)

    ScalingLazyColumn(Modifier.fillMaxSize(), state = listState, horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text(S.TRACK.t(), fontSize = 14.sp, color = Dim) }
        item { SliderRow(Settings.precision, accent, unit = " m") }
        item { SliderRow(Settings.distance, accent, unit = " m") }
        item {
            SliderRow(
                Settings.exTime, accent,
                fmt = { v -> if (v < 0.5f) S.OFF.t() else fmtDur(v.roundToInt()) },
                onRelease = resched
            )
        }
        item { SliderRow(Settings.nbRep, accent, unit = " ×", onRelease = resched) }
        item { SliderRow(Settings.restTime, accent, onRelease = resched) }
        item { ToggleRow(S.TRACK_VIBE.t(), Settings.trackVibe, true, accent, "trackVibe") { Settings.setTrackVibe(ctx, it) } }
    }
}

/** Aide complète : sections à titres en gras (couleur de référence), texte découpé en courtes lignes. */
@Composable
fun HelpScreen() {
    val eco = Settings.eco
    val pal = remember(Settings.rgb) { makePalette(Settings.rgb) }
    val accent = if (eco) Fg else pal.accent

    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text(S.HELP_TITLE.t(), fontSize = 16.sp, color = accent, fontWeight = FontWeight.Bold) }
        HELP_SECTIONS.forEach { (title, body) ->
            item { Spacer(Modifier.height(6.dp)) }
            item {
                Text(
                    title.t(), fontSize = 13.sp, color = accent, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.9f)
                )
            }
            body.t().split("\n").filter { it.isNotBlank() }.forEach { line ->
                item { Text(line, fontSize = 11.sp, color = Fg, modifier = Modifier.fillMaxWidth(0.88f)) }
            }
        }
    }
}

@Composable
fun HistoryScreen() {
    val ctx = LocalContext.current
    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text(S.HISTORY.t(), fontSize = 14.sp, color = Dim) }
        if (History.sessions.isEmpty()) {
            item { Text(S.NO_SESSION.t(), fontSize = 12.sp, color = Dim) }
        }
        itemsIndexed(History.sessions) { i, s ->
            // Glisser vers la droite : ouvre la séance ; maintenir : supprime cette séance (nom = date et heure)
            var dx by remember { mutableFloatStateOf(0f) }
            Row(
                Modifier.fillMaxWidth(0.92f)
                    .swipeOrHold(
                        onSwipe = { Ui.sessionIndex = i; Ui.screen = Screen.SESSION },
                        onHold = { History.delete(ctx, i) },
                        onDrag = { dx = it }
                    )
                    .graphicsLayer { translationX = dx }
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceBtn)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(fmtDate(s.ts), fontSize = 12.sp, color = Fg)
                    Text(
                        fmtFull(s.total) + " · " + s.laps.size + " " +
                            (if (s.laps.size > 1) S.LAP_MANY.t() else S.LAP_ONE.t()),
                        fontSize = 11.sp, color = Dim
                    )
                }
                Spacer(Modifier.width(6.dp))
                ChevronGlyph()
            }
        }
        item {
            // Même mécanique que le Reset : maintenir (temps réglable), snake de progression, vibration
            Box(
                Modifier.fillMaxWidth(0.92f).height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceBtn)
                    .holdToConfirm { History.clear(ctx) },
                contentAlignment = Alignment.Center
            ) { Text(S.CLEAR_ALL.t(), fontSize = 11.sp, color = Fg) }
        }
    }
}

@Composable
private fun Stat(label: S, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.t(), fontSize = 9.sp, color = Dim)
        Text(value, fontSize = 12.sp, color = Fg, style = Tnum)
    }
}

/** Statistiques minimalistes : meilleur, pire, moyenne, médiane, écart-type, régularité, barres. */
@Composable
private fun StatsBlock(laps: List<Long>, eco: Boolean, pal: Palette) {
    if (laps.isEmpty()) return
    val n = laps.size
    val sorted = laps.sorted()
    val mn = sorted.first()
    val mx = sorted.last()
    val avg = laps.sum() / n
    val med = if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2
    var acc = 0.0
    for (v in laps) { val d = (v - avg).toDouble(); acc += d * d }
    val sd = sqrt(acc / n)
    val reg = (100.0 * (1.0 - sd / avg.coerceAtLeast(1L))).coerceIn(0.0, 100.0).roundToInt()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat(S.BEST, fmtFull(mn)); Stat(S.WORST, fmtFull(mx))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat(S.AVG, fmtFull(avg)); Stat(S.MEDIAN, fmtFull(med))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat(S.STDEV, "±" + fmtFull(sd.toLong())); Stat(S.REGULARITY, "$reg %")
        }
        Canvas(Modifier.fillMaxWidth(0.8f).height(26.dp)) {
            val w = size.width / n
            laps.forEachIndexed { i, v ->
                val h = size.height * (v.toFloat() / mx.toFloat()).coerceIn(0.05f, 1f)
                drawRect(
                    lapColor(v, mn, mx, eco, pal),
                    Offset(i * w, size.height - h),
                    Size(maxOf(1f, w - (if (w > 4f) 1.5f else 0f)), h)
                )
            }
        }
    }
}

@Composable
fun SessionScreen() {
    val ctx = LocalContext.current
    val s = History.sessions.getOrNull(Ui.sessionIndex)
    val eco = Settings.eco
    val pal = remember(Settings.rgb) { makePalette(Settings.rgb) }
    val cfg = LocalConfiguration.current
    val hDp = cfg.screenHeightDp.toFloat()
    val availW = (cfg.screenWidthDp * Settings.lapWidthPct / 100f).dp
    val rows = remember(s) {
        if (s == null) emptyList<Lap>() else {
            var t = 0L
            s.laps.mapIndexed { i, v -> t += v; Lap(i + 1, v, t) }
        }
    }
    val cols = rememberLapCols(rows, false, Settings.textSp)
    val vMin = rows.minOfOrNull { it.lapTime } ?: 0L
    val vMax = rows.maxOfOrNull { it.lapTime } ?: 0L
    val step = Settings.seriesLen

    LazyColumn(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 26.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy((Settings.lineGapPct / 100f * hDp).dp)
    ) {
        if (s == null) {
            item { Text(S.NOT_FOUND.t(), fontSize = 12.sp, color = Dim) }
        } else {
            item { Text(fmtDate(s.ts), fontSize = 13.sp, color = Dim) }
            item { Text(fmtFull(s.total), fontSize = 20.sp, color = Fg, style = Tnum) }
            item { StatsBlock(s.laps, eco, pal) }
            if (rows.isEmpty()) {
                item { Text(S.NO_LAP.t(), fontSize = 12.sp, color = Dim) }
            }
            lazyItemsIndexed(rows) { i, lap ->
                // ▲▼ : comparaison avec le tour précédent (ou le tour de même rang de la série précédente)
                val marker = if (Settings.showMarkers) relMarker(lap.lapTime, rows.getOrNull(i - step)?.lapTime) else 0
                LapRow(
                    lap, rows.getOrNull(i - 1)?.lapTime,
                    lapColor(lap.lapTime, vMin, vMax, eco, pal),
                    marker, Settings.textSp, cols, availW
                )
            }
            item {
                // Bouton QR : génère les codes de cette séance (liste Python des temps de tour en secondes)
                Box(
                    Modifier.padding(top = 6.dp).size(40.dp).clip(CircleShape).background(SurfaceBtn)
                        .clickable { Ui.screen = Screen.QR },
                    contentAlignment = Alignment.Center
                ) { QrGlyph(Fg) }
            }
            item {
                Box(
                    Modifier.fillMaxWidth(0.92f).height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(SurfaceBtn)
                        .holdToConfirm {
                            History.delete(ctx, Ui.sessionIndex)
                            Ui.screen = Screen.HISTORY
                        },
                    contentAlignment = Alignment.Center
                ) { Text(S.DELETE_ONE.t(), fontSize = 10.sp, color = Fg) }
            }
        }
    }
}

/** Codes QR de la séance : fond blanc imposé, luminosité maximale, défilement gauche/droite entre les blocs. */
@Composable
fun QrScreen() {
    val s = History.sessions.getOrNull(Ui.sessionIndex)
    val ver = Settings.qrVersion
    val ec = Settings.qrLevel
    val cfg = LocalConfiguration.current
    val side = (minOf(cfg.screenWidthDp, cfg.screenHeightDp) * 0.66f).dp
    val items = remember(s) {
        if (s == null) emptyList() else (if (s.laps.isEmpty()) listOf(s.total) else s.laps).map { lapSeconds(it) }
    }
    val blocks = remember(items, ver, ec) {
        if (items.isEmpty()) emptyList() else qrBlocks(items, qrCapacity(ver, ec))
    }
    val mats = remember(blocks, ver, ec) { blocks.map { qrMatrix(it, ver, ec) } }

    val act = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        val w = act?.window
        val old = w?.attributes?.screenBrightness ?: -1f
        if (w != null) {
            val lp = w.attributes
            lp.screenBrightness = 1f
            w.attributes = lp
        }
        onDispose {
            if (w != null) {
                val lp = w.attributes
                lp.screenBrightness = old
                w.attributes = lp
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        if (mats.isEmpty()) {
            Text("—", color = Color.Black)
        } else {
            val pager = rememberPagerState(pageCount = { mats.size })
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val m = mats[page]
                        if (m != null) QrCanvas(m, side) else Text("QR ✗", color = Color.Black)
                        Spacer(Modifier.height(4.dp))
                        Text("${page + 1}/${mats.size}", fontSize = 12.sp, color = Color.Black)
                    }
                }
            }
        }
    }
}
