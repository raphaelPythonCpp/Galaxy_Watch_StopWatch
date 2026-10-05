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

private fun mapF(f: Float, min: Int, max: Int): Int = (min + f * (max - min)).roundToInt()
private fun fracOf(v: Int, min: Int, max: Int): Float = (v - min).toFloat() / (max - min)
private fun pct(v: Int) = "$v %"

/** Minutes 0..1440 avec une échelle non linéaire (plus fine pour les petites valeurs). */
private fun minFromF(f: Float): Int {
    val m = (1440f * f * f).roundToInt()
    return if (m > 60) ((m / 5f).roundToInt() * 5).coerceAtMost(1440) else m
}
private fun fOfMin(m: Int): Float = kotlin.math.sqrt(m / 1440f)
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
    val accent = if (eco) Color.White else pal.accent
    val onAcc = if (eco) Color.Black else pal.onAccent
    val r = (Settings.rgb shr 16) and 0xFF
    val g = (Settings.rgb shr 8) and 0xFF
    val b = Settings.rgb and 0xFF
    val fin = { Settings.persistLayout(ctx) }
    // Au moins une colonne doit rester affichée
    val colToggle: (Int, Boolean) -> Unit = { idx, v ->
        val count = listOf(Settings.colNum, Settings.colTotal, Settings.colLap, Settings.colDelta).count { it }
        if (v || count > 1) Settings.setCol(ctx, idx, v)
    }

    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text("Réglages", fontSize = 14.sp, color = Dim) }
        item { ToggleRow("Cercle", Settings.ringActive, !eco, accent) { Settings.setRing(ctx, it) } }
        item {
            ToggleRow("Always-on display", Settings.aodActive, !eco, accent) {
                Settings.setAod(ctx, it)
                act?.recreate()
            }
        }
        item {
            ToggleRow("Mode éco", eco, true, accent) {
                Settings.setEco(ctx, it)
                act?.recreate()
            }
        }
        if (eco) {
            item {
                SliderRow(
                    "Luminosité éco", "${Settings.ecoBrightness}%",
                    Settings.ecoBrightness / 100f, accent
                ) { f -> Settings.updateEcoBrightness((f * 100).roundToInt()) }
            }
        }
        item { ToggleRow("Verrou tactile", Settings.lock, true, accent) { Settings.setLock(ctx, it) } }
        if (Settings.lock) {
            item {
                SliderRow(
                    "Déverrouillage auto", fmtMinutes(Settings.autoUnlockMin, "Jamais"),
                    fOfMin(Settings.autoUnlockMin), accent, true, fin
                ) { f -> Settings.updateAutoUnlockMin(minFromF(f)) }
            }
        }
        item { ToggleRow("Mode gaucher", Settings.lefty, true, accent) { Settings.setLefty(ctx, it) } }
        item { ToggleRow("Bouton annuler tour", Settings.undoBtn, true, accent) { Settings.setUndoBtn(ctx, it) } }
        item { ToggleRow("Temps en secondes", Settings.secMode, true, accent) { Settings.setSecMode(ctx, it) } }
        item { ToggleRow("Fondu des tours", Settings.fade, true, accent) { Settings.setFade(ctx, it) } }

        item { Text("Colonnes des tours", fontSize = 13.sp, color = Dim) }
        item {
            Row(Modifier.fillMaxWidth(0.92f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HalfToggle("N°", Settings.colNum, accent, onAcc, Modifier.weight(1f)) { colToggle(0, it) }
                HalfToggle("Total", Settings.colTotal, accent, onAcc, Modifier.weight(1f)) { colToggle(1, it) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(0.92f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HalfToggle("Tour", Settings.colLap, accent, onAcc, Modifier.weight(1f)) { colToggle(2, it) }
                HalfToggle("Écart", Settings.colDelta, accent, onAcc, Modifier.weight(1f)) { colToggle(3, it) }
            }
        }

        item { Text("Tailles", fontSize = 13.sp, color = Dim) }
        item {
            SliderRow("Chrono", "${Settings.chronoSp} sp", fracOf(Settings.chronoSp, 10, 90), accent, true, fin) { f ->
                Settings.updateChronoSp(mapF(f, 10, 90))
            }
        }
        item {
            SliderRow("Cercles", "${Settings.btnDp} dp", fracOf(Settings.btnDp, 20, 140), accent, true, fin) { f ->
                Settings.updateBtnDp(mapF(f, 20, 140))
            }
        }
        item {
            SliderRow("Tours", "${Settings.lapSp} sp", fracOf(Settings.lapSp, 5, 40), accent, true, fin) { f ->
                Settings.updateLapSp(mapF(f, 5, 40))
            }
        }

        item { Text("Espacements (% de la hauteur)", fontSize = 13.sp, color = Dim) }
        item {
            SliderRow("Chrono → cercles", pct(Settings.gapTimeBtnPct), Settings.gapTimeBtnPct / 100f, accent, true, fin) { f ->
                Settings.updateGapTimeBtnPct((f * 100).roundToInt())
            }
        }
        item {
            SliderRow("Entre les cercles", pct(Settings.btnGapPct), Settings.btnGapPct / 100f, accent, true, fin) { f ->
                Settings.updateBtnGapPct((f * 100).roundToInt())
            }
        }
        item {
            SliderRow("Cercles → tours", pct(Settings.gapBtnLapPct), Settings.gapBtnLapPct / 100f, accent, true, fin) { f ->
                Settings.updateGapBtnLapPct((f * 100).roundToInt())
            }
        }
        item {
            SliderRow("Entre les tours", pct(Settings.lineGapPct), Settings.lineGapPct / 100f, accent, true, fin) { f ->
                Settings.updateLineGapPct((f * 100).roundToInt())
            }
        }

        item { Text("Disposition", fontSize = 13.sp, color = Dim) }
        item {
            SliderRow("Haut de l'écran", pct(Settings.topPct), Settings.topPct / 100f, accent, true, fin) { f ->
                Settings.updateTopPct((f * 100).roundToInt())
            }
        }
        item {
            SliderRow("Largeur des tours", pct(Settings.lapWidthPct), fracOf(Settings.lapWidthPct, 40, 100), accent, true, fin) { f ->
                Settings.updateLapWidthPct(mapF(f, 40, 100))
            }
        }
        item {
            Box(
                Modifier.fillMaxWidth(0.92f).height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceBtn)
                    .holdToConfirm(Settings.holdMs.toLong()) { Settings.resetLayout(ctx) },
                contentAlignment = Alignment.Center
            ) { Text("Mise en page par défaut (maintenir)", fontSize = 10.sp, color = Color.White) }
        }

        item { Text("Comportement", fontSize = 13.sp, color = Dim) }
        item {
            SliderRow(
                "Temps de maintien",
                String.format(Locale.ROOT, "%.1f s", Settings.holdMs / 1000f),
                fracOf(Settings.holdMs, 100, 10000), accent, true, fin
            ) { f -> Settings.updateHoldMs(((100 + f * 9900) / 100f).roundToInt() * 100) }
        }
        item {
            SliderRow(
                "Vibrations", if (Settings.vibePct == 0) "Aucune" else pct(Settings.vibePct),
                Settings.vibePct / 100f, accent, true, fin
            ) { f -> Settings.updateVibePct((f * 100).roundToInt()) }
        }
        item {
            SliderRow(
                "Arrêt auto du chrono", fmtMinutes(Settings.autoStopMin, "Désactivé"),
                fOfMin(Settings.autoStopMin), accent, true, fin
            ) { f -> Settings.updateAutoStopMin(minFromF(f)) }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Couleur", fontSize = 13.sp, color = Dim)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(16.dp).clip(CircleShape).background(if (eco) Color.Gray else pal.accent))
            }
        }
        item {
            SliderRow("Rouge", "$r", r / 255f, Color(0xFFEF4444), !eco, { Settings.persistRgb(ctx) }) { f ->
                Settings.setRgbChannel(0, (f * 255).roundToInt())
            }
        }
        item {
            SliderRow("Vert", "$g", g / 255f, Color(0xFF22C55E), !eco, { Settings.persistRgb(ctx) }) { f ->
                Settings.setRgbChannel(1, (f * 255).roundToInt())
            }
        }
        item {
            SliderRow("Bleu", "$b", b / 255f, Color(0xFF3B82F6), !eco, { Settings.persistRgb(ctx) }) { f ->
                Settings.setRgbChannel(2, (f * 255).roundToInt())
            }
        }
    }
}

@Composable
fun HistoryScreen() {
    val ctx = LocalContext.current
    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Text("Historique", fontSize = 14.sp, color = Dim) }
        if (History.sessions.isEmpty()) {
            item { Text("Aucune séance", fontSize = 12.sp, color = Dim) }
        }
        itemsIndexed(History.sessions) { i, s ->
            Chip(
                onClick = { Ui.sessionIndex = i; Ui.screen = Screen.SESSION },
                label = { Text(fmtDate(s.ts), fontSize = 12.sp) },
                secondaryLabel = {
                    Text(
                        fmtFull(s.total) + " · " + s.laps.size + (if (s.laps.size > 1) " tours" else " tour"),
                        fontSize = 11.sp
                    )
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(0.92f)
            )
        }
        item {
            // Même mécanique que le Reset : maintenir 2 s, le snake de la couleur de référence montre la progression
            Box(
                Modifier.fillMaxWidth(0.92f).height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceBtn)
                    .holdToConfirm(Settings.holdMs.toLong()) { History.clear(ctx) },
                contentAlignment = Alignment.Center
            ) { Text("Tout effacer (maintenir)", fontSize = 11.sp, color = Color.White) }
        }
    }
}

@Composable
fun SessionScreen() {
    val s = History.sessions.getOrNull(Ui.sessionIndex)
    val eco = Settings.eco
    val pal = remember(Settings.rgb) { makePalette(Settings.rgb) }
    val hDp = LocalConfiguration.current.screenHeightDp.toFloat()
    val rows = remember(s) {
        if (s == null) emptyList<Lap>() else {
            var t = 0L
            s.laps.mapIndexed { i, v -> t += v; Lap(i + 1, v, t) }
        }
    }
    val vMin = rows.minOfOrNull { it.lapTime } ?: 0L
    val vMax = rows.maxOfOrNull { it.lapTime } ?: 0L

    LazyColumn(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 26.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy((Settings.lineGapPct / 100f * hDp).dp)
    ) {
        if (s == null) {
            item { Text("Séance introuvable", fontSize = 12.sp, color = Dim) }
        } else {
            item { Text(fmtDate(s.ts), fontSize = 13.sp, color = Dim) }
            item { Text(fmtFull(s.total), fontSize = 20.sp, color = Color.White, style = Tnum) }
            if (rows.isEmpty()) {
                item { Text("Aucun tour", fontSize = 12.sp, color = Dim) }
            }
            lazyItemsIndexed(rows) { i, lap ->
                LapRow(
                    lap, rows.getOrNull(i - 1)?.lapTime,
                    lapColor(lap.lapTime, vMin, vMax, eco, pal),
                    lapMarker(lap.lapTime, vMin, vMax, rows.size),
                    Settings.textSp, Settings.lapWidthPct / 100f
                )
            }
        }
    }
}
