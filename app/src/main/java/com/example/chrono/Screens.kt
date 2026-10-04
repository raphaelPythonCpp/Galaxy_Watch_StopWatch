package com.example.chrono

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val act = ctx as? Activity
    val eco = Settings.eco
    val pal = remember(Settings.rgb) { makePalette(Settings.rgb) }
    val accent = if (eco) Color.White else pal.accent
    val r = (Settings.rgb shr 16) and 0xFF
    val g = (Settings.rgb shr 8) and 0xFF
    val b = Settings.rgb and 0xFF

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
                ) { f -> Settings.setEcoBrightness((f * 100).roundToInt()) }
            }
        }
        item { ToggleRow("Verrou tactile", Settings.lock, true, accent) { Settings.setLock(ctx, it) } }
        item { ToggleRow("Mode gaucher", Settings.lefty, true, accent) { Settings.setLefty(ctx, it) } }
        item { ToggleRow("Bouton annuler tour", Settings.undoBtn, true, accent) { Settings.setUndoBtn(ctx, it) } }
        item { ToggleRow("Temps en secondes", Settings.secMode, true, accent) { Settings.setSecMode(ctx, it) } }
        item {
            SliderRow(
                "Taille du texte", "${Settings.textLevel}/10",
                (Settings.textLevel - 1) / 9f, accent
            ) { f -> Settings.setTextLevel(ctx, (f * 9).roundToInt() + 1) }
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
    var confirm by remember { mutableStateOf(false) }
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
            Chip(
                onClick = { if (confirm) { History.clear(ctx); confirm = false } else confirm = true },
                label = { Text(if (confirm) "Confirmer ?" else "Tout effacer") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(0.92f)
            )
        }
    }
}

@Composable
fun SessionScreen() {
    val s = History.sessions.getOrNull(Ui.sessionIndex)
    val eco = Settings.eco
    val pal = remember(Settings.rgb) { makePalette(Settings.rgb) }
    // Calculs hors de ScalingLazyColumn : « remember » n'est pas autorisé dans son contenu
    val rows = remember(s) {
        if (s == null) emptyList<Lap>() else {
            var t = 0L
            s.laps.mapIndexed { i, v -> t += v; Lap(i + 1, v, t) }
        }
    }
    val vMin = rows.minOfOrNull { it.lapTime } ?: 0L
    val vMax = rows.maxOfOrNull { it.lapTime } ?: 0L

    ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (s == null) {
            item { Text("Séance introuvable", fontSize = 12.sp, color = Dim) }
        } else {
            item { Text(fmtDate(s.ts), fontSize = 13.sp, color = Dim) }
            item { Text(fmtFull(s.total), fontSize = 20.sp, color = Color.White, style = Tnum) }
            if (rows.isEmpty()) {
                item { Text("Aucun tour", fontSize = 12.sp, color = Dim) }
            }
            itemsIndexed(rows) { i, lap ->
                LapRow(
                    lap, rows.getOrNull(i - 1)?.lapTime,
                    lapColor(lap.lapTime, vMin, vMax, eco, pal),
                    lapMarker(lap.lapTime, vMin, vMax, rows.size),
                    Settings.textSp
                )
            }
        }
    }
}
