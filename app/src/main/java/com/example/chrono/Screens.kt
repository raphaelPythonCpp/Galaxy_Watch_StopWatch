package com.example.chrono

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private fun fmtDate(ts: Long): String =
    SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(Date(ts))

private fun fmtMinutes(m: Int, zero: String): String = when {
    m <= 0 -> zero
    m < 60 -> "$m min"
    else -> "${m / 60} h" + (if (m % 60 != 0) " %02d".format(m % 60) else "")
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val act = ctx as? Activity
    val eco = Settings.eco
    val pal = remember(Settings.rgb) { makePalette(Settings.rgb) }
    val accent = if (eco) Fg else pal.accent
    val onAcc = if (eco) Bg else pal.onAccent
    // Au moins une colonne doit rester affichée
    val colToggle: (Int, Boolean) -> Unit = { idx, v ->
        val count = listOf(Settings.colNum, Settings.colTotal, Settings.colLap, Settings.colDelta).count { it }
        if (v || count > 1) Settings.setCol(ctx, idx, v)
    }

    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text(S.SETTINGS.t(), fontSize = 14.sp, color = Dim) }

        // ---------------- Essentiels : toujours affichés
        item {
            ToggleRow(S.ECO.t(), eco, true, accent) {
                Settings.setEco(ctx, it)
                act?.recreate()
            }
        }
        if (eco) {
            item { SliderRow(Settings.ecoBright, accent, unit = " %") }
        }
        item { SliderRow(Settings.hold, accent, unit = " s") }
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
        item { ToggleRow(S.RING.t(), Settings.ringActive, !eco, accent) { Settings.setRing(ctx, it) } }
        item { ToggleRow(S.LOCK.t(), Settings.lock, true, accent) { Settings.setLock(ctx, it) } }
        if (Settings.lock) {
            item {
                SliderRow(Settings.autoUnlock, accent,
                    fmt = { v -> fmtMinutes(v.roundToInt(), S.NEVER.t()) })
            }
            item {
                ToggleRow(S.RING_IN_LOCK.t(), Settings.ringInLock, true, accent) { Settings.setRingInLock(ctx, it) }
            }
        }
        item {
            ToggleRow(S.AOD.t(), Settings.aodActive, !eco, accent) {
                Settings.setAod(ctx, it)
                act?.recreate()
            }
        }
        item { ToggleRow(S.CUSTOM.t(), Settings.custom, true, accent) { Settings.setCustom(ctx, it) } }

        // ---------------- Personnalisation : tout le reste
        if (Settings.custom) {
            item { Text(S.LANGUAGE.t(), fontSize = 13.sp, color = Dim) }
            item {
                Row(Modifier.fillMaxWidth(0.92f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HalfToggle("Français", Settings.lang == Lang.FR, accent, onAcc, Modifier.weight(1f), 11f) {
                        Settings.setLang(ctx, Lang.FR)
                    }
                    HalfToggle("English", Settings.lang == Lang.EN, accent, onAcc, Modifier.weight(1f), 11f) {
                        Settings.setLang(ctx, Lang.EN)
                    }
                    HalfToggle("中文", Settings.lang == Lang.ZH, accent, onAcc, Modifier.weight(1f), 11f) {
                        Settings.setLang(ctx, Lang.ZH)
                    }
                }
            }
            item { ToggleRow(S.LIGHT.t(), Settings.light, true, accent) { Settings.setLight(ctx, it) } }
            item { SliderRow(Settings.snake, accent, !eco, " %") }
            item { ToggleRow(S.TOUCH_RING.t(), Settings.touchRing, true, accent) { Settings.setTouchRing(ctx, it) } }
            item { ToggleRow(S.LEFTY.t(), Settings.lefty, true, accent) { Settings.setLefty(ctx, it) } }
            item { ToggleRow(S.UNDO_BTN.t(), Settings.undoBtn, true, accent) { Settings.setUndoBtn(ctx, it) } }
            item { ToggleRow(S.SECONDS.t(), Settings.secMode, true, accent) { Settings.setSecMode(ctx, it) } }
            item { ToggleRow(S.FADE.t(), Settings.fade, true, accent) { Settings.setFade(ctx, it) } }

            item { Text(S.COLS.t(), fontSize = 13.sp, color = Dim) }
            item {
                Row(Modifier.fillMaxWidth(0.92f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HalfToggle(S.COL_NUM.t(), Settings.colNum, accent, onAcc, Modifier.weight(1f)) { colToggle(0, it) }
                    HalfToggle(S.COL_TOTAL.t(), Settings.colTotal, accent, onAcc, Modifier.weight(1f)) { colToggle(1, it) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(0.92f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HalfToggle(S.COL_LAP.t(), Settings.colLap, accent, onAcc, Modifier.weight(1f)) { colToggle(2, it) }
                    HalfToggle(S.COL_DELTA.t(), Settings.colDelta, accent, onAcc, Modifier.weight(1f)) { colToggle(3, it) }
                }
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
                    fmt = { v -> fmtMinutes(v.roundToInt(), S.OFF.t()) })
            }
        }
    }
}

/** Sous-menu d'un curseur : plage min / max (bornes d'origine larges, 100 pas, min < max). */
@Composable
fun RangeScreen() {
    val ctx = LocalContext.current
    val eco = Settings.eco
    val pal = remember(Settings.rgb) { makePalette(Settings.rgb) }
    val accent = if (eco) Fg else pal.accent
    val spec = Settings.byKey[Ui.rangeKey]

    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (spec == null) {
            item { Text(S.NOT_FOUND.t(), fontSize = 12.sp, color = Dim) }
        } else {
            val step0 = spec.gap0
            val span = spec.max0 - spec.min0
            item { Text(spec.label.t(), fontSize = 14.sp, color = Dim) }
            item {
                Text(
                    S.RANGE.t() + " : " + spec.text(spec.lo, step0) + " – " + spec.text(spec.hi, step0),
                    fontSize = 11.sp, color = Dim
                )
            }
            item {
                SliderBar(
                    S.MIN.t(), spec.text(spec.lo, step0), (spec.lo - spec.min0) / span, accent, true,
                    { Settings.persistSliders(ctx) }, null
                ) { f ->
                    val n = (f * 100f).roundToInt()
                    val v = spec.min0 + n / 100f * span
                    spec.setRange(minOf(v, spec.hi - step0).coerceAtLeast(spec.min0), spec.hi)
                }
            }
            item {
                SliderBar(
                    S.MAX.t(), spec.text(spec.hi, step0), (spec.hi - spec.min0) / span, accent, true,
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

@Composable
fun HistoryScreen() {
    val ctx = LocalContext.current
    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text(S.HISTORY.t(), fontSize = 14.sp, color = Dim) }
        if (History.sessions.isEmpty()) {
            item { Text(S.NO_SESSION.t(), fontSize = 12.sp, color = Dim) }
        }
        itemsIndexed(History.sessions) { i, s ->
            Chip(
                onClick = { Ui.sessionIndex = i; Ui.screen = Screen.SESSION },
                label = { Text(fmtDate(s.ts), fontSize = 12.sp) },
                secondaryLabel = {
                    Text(
                        fmtFull(s.total) + " · " + s.laps.size + " " +
                            (if (s.laps.size > 1) S.LAP_MANY.t() else S.LAP_ONE.t()),
                        fontSize = 11.sp
                    )
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(0.92f)
            )
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
fun SessionScreen() {
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
            if (rows.isEmpty()) {
                item { Text(S.NO_LAP.t(), fontSize = 12.sp, color = Dim) }
            }
            lazyItemsIndexed(rows) { i, lap ->
                LapRow(
                    lap, rows.getOrNull(i - 1)?.lapTime,
                    lapColor(lap.lapTime, vMin, vMax, eco, pal),
                    lapMarker(lap.lapTime, vMin, vMax, rows.size),
                    Settings.textSp, cols, availW
                )
            }
        }
    }
}
