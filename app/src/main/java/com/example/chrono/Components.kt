package com.example.chrono

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

// ---------------------------------------------------------------- thème (mode clair = 255 - rgb sur les gris)

private fun g(c: Color): Color =
    if (Settings.light) Color(1f - c.red, 1f - c.green, 1f - c.blue, c.alpha) else c

val Fg: Color get() = g(Color.White)
val Bg: Color get() = g(Color.Black)
val Dim: Color get() = g(Color(0xFF9CA3AF))
val Soft: Color get() = g(Color(0xFFD1D5DB))
val SurfaceBtn: Color get() = g(Color(0xFF2B2F36))
val RowBg: Color get() = g(Color(0xFF1B1F26))
val OffTrack: Color get() = g(Color(0xFF3A3F47))
val RingTrack: Color get() = g(Color(0xFF0E1218))
val Tnum = TextStyle(fontFeatureSettings = "tnum")

enum class Screen { MAIN, SETTINGS, RANGE, HISTORY, SESSION }

object Ui {
    var screen by mutableStateOf(Screen.MAIN)
    var sessionIndex by mutableIntStateOf(0)
    var rangeKey by mutableStateOf("")
    var ambient by mutableStateOf(false)
    var locked by mutableStateOf(false)
    var lockedAt = 0L
    var hold by mutableFloatStateOf(0f)
}

// ---------------------------------------------------------------- bague

/** Cercle presque noir + « snake » (longueur = % de la circonférence) : couleur forte (tête) -> couleur du cercle (queue). */
@Composable
fun Ring(snakeFrac: Float, frac: () -> Float) {
    Canvas(Modifier.fillMaxSize().padding(3.dp)) {
        val stroke = 6.dp.toPx()
        val topLeft = Offset(stroke / 2, stroke / 2)
        val sz = Size(size.width - stroke, size.height - stroke)
        drawArc(RingTrack, 0f, 360f, false, topLeft, sz, style = Stroke(stroke))
        val s = snakeFrac.coerceIn(0.01f, 0.998f)
        val sweep = 360f * s
        val plateau = maxOf(s, 0.9f).coerceAtMost(0.999f)
        val head = -90f + frac() * 360f
        rotate(degrees = head - sweep, pivot = center) {
            drawArc(
                brush = Brush.sweepGradient(
                    0f to RingTrack,
                    s to Fg,
                    plateau to Fg,
                    1f to RingTrack,
                    center = center
                ),
                startAngle = 0f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = sz,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
    }
}

/** Progression d'un appui long : arc plein de la couleur de référence, sans dégradé, qui s'allonge depuis midi. */
@Composable
fun HoldRing(color: Color) {
    Canvas(Modifier.fillMaxSize().padding(3.dp)) {
        val p = Ui.hold
        if (p > 0f) {
            val stroke = 6.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val sz = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color, -90f, p.coerceAtMost(1f) * 360f, false, topLeft, sz,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
    }
}

// ---------------------------------------------------------------- texte

/** Chiffres à chasse fixe, interligne 0,8 em et marges haut/bas supprimées : à 0 % d'espacement, les objets se touchent. */
fun tight(sizeSp: Float): TextStyle = TextStyle(
    fontFeatureSettings = "tnum",
    lineHeight = (sizeSp * 0.8f).sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
)

/** Fondu progressif en haut et en bas d'une liste (option). */
fun Modifier.edgeFade(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = size.height
        val f = minOf(22.dp.toPx(), h / 3f)
        if (f > 0f) {
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = f),
                size = Size(size.width, f),
                blendMode = BlendMode.DstIn
            )
            drawRect(
                Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = h - f, endY = h),
                topLeft = Offset(0f, h - f),
                size = Size(size.width, f),
                blendMode = BlendMode.DstIn
            )
        }
    }

@Composable
fun TimeText(now: MutableLongState, eco: Boolean) {
    val ms = now.longValue
    val base = Settings.chronoSp
    val big = if (!Settings.secMode && ms >= 3_600_000L) base * 0.79f else base
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            fmtMain(ms),
            fontSize = big.sp,
            fontWeight = FontWeight.Medium, color = Fg, style = tight(big)
        )
        if (!eco) {
            Text(fmtTenth(ms), fontSize = (big * 0.47f).sp, color = Dim, style = tight(big * 0.47f))
        }
    }
}

/** Always-on display : noir et gris fixes (un fond clair abîmerait l'écran AMOLED). */
@Composable
fun AmbientScreen(now: MutableLongState) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Text(fmtMain(now.longValue), fontSize = 44.sp, color = Color(0xFFB0B0B0), style = Tnum)
    }
}

// ---------------------------------------------------------------- icônes

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
                drawLine(Fg, Offset(0f, y), Offset(size.width, y), strokeWidth = w, cap = StrokeCap.Round)
            }
        }
    }
}

/** Icône d'historique : horloge. */
@Composable
fun HistoryButton(onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(SurfaceBtn).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(16.dp)) {
            val w = 1.6.dp.toPx()
            val c = center
            val r = size.minDimension / 2 - w / 2
            drawCircle(Fg, r, c, style = Stroke(w))
            drawLine(Fg, c, Offset(c.x, c.y - r * 0.55f), w, StrokeCap.Round)
            drawLine(Fg, c, Offset(c.x + r * 0.45f, c.y), w, StrokeCap.Round)
        }
    }
}

@Composable
fun LockIcon() {
    Canvas(Modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            Fg,
            topLeft = Offset(0f, h * 0.45f),
            size = Size(w, h * 0.55f),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
        drawArc(
            Fg, 180f, 180f, false,
            topLeft = Offset(w * 0.2f, 0f),
            size = Size(w * 0.6f, h * 0.9f),
            style = Stroke(1.6.dp.toPx())
        )
    }
}

/** Triangle ▲ (meilleur) ou ▼ (pire), dessiné (aucune police nécessaire). */
@Composable
fun Marker(dir: Int, color: Color, dim: Dp) {
    Canvas(Modifier.size(dim)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path()
        if (dir > 0) {
            path.moveTo(w / 2, 0f); path.lineTo(w, h); path.lineTo(0f, h)
        } else {
            path.moveTo(0f, 0f); path.lineTo(w, 0f); path.lineTo(w / 2, h)
        }
        path.close()
        drawPath(path, color)
    }
}

// ---------------------------------------------------------------- boutons et appuis longs

@Composable
fun RoundButton(
    label: String, dim: Dp, bg: Color, fg: Color, fs: TextUnit,
    enabled: Boolean = true, onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(dim).alpha(if (enabled) 1f else 0.4f),
        colors = ButtonDefaults.buttonColors(backgroundColor = bg, contentColor = fg)
    ) { Text(label, fontSize = fs, color = fg) }
}

/**
 * Appui long : durée lue à CHAQUE image dans Settings.holdMs (donc dynamique), snake de progression
 * (Ui.hold), et vibration à chaque réussite. Annulé si on relâche avant.
 */
@Composable
fun Modifier.holdToConfirm(onDone: () -> Unit): Modifier {
    val ctx = LocalContext.current
    val done by rememberUpdatedState(onDone)
    return this.pointerInput(Unit) {
        detectTapGestures(onPress = {
            coroutineScope {
                val job = launch {
                    val t0 = SystemClock.uptimeMillis()
                    while (true) {
                        delay(16)
                        val ms = Settings.holdMs.coerceAtLeast(50L)
                        val p = (SystemClock.uptimeMillis() - t0).toFloat() / ms
                        Ui.hold = p.coerceAtMost(1f)
                        if (p >= 1f) {
                            Ui.hold = 0f
                            Stopwatch.buzz(ctx, true)
                            done()
                            break
                        }
                    }
                }
                tryAwaitRelease()
                job.cancel()
                Ui.hold = 0f
            }
        })
    }
}

@Composable
fun HoldButton(label: String, dim: Dp, bg: Color, fg: Color, fs: TextUnit, onDone: () -> Unit) {
    Box(
        Modifier.size(dim).clip(CircleShape).background(bg).holdToConfirm(onDone),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = fs, color = fg) }
}

// ---------------------------------------------------------------- bague tactile de secours

/**
 * Défilement circulaire au doigt dans la bande extérieure de l'écran : ~15° = 1 ligne.
 * Un simple appui n'est jamais intercepté ; seul un mouvement circulaire (> 8°) l'est.
 */
@Composable
fun Modifier.circularScroll(enabled: () -> Boolean, onLines: (Int) -> Unit): Modifier {
    val en by rememberUpdatedState(enabled)
    val cb by rememberUpdatedState(onLines)
    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = minOf(cx, cy)
            val d0 = hypot(down.position.x - cx, down.position.y - cy)
            if (!en() || d0 < r * 0.78f) return@awaitEachGesture
            var last = atan2(down.position.y - cy, down.position.x - cx)
            var acc = 0f
            var active = false
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                val a = atan2(ch.position.y - cy, ch.position.x - cx)
                var da = Math.toDegrees((a - last).toDouble()).toFloat()
                if (da > 180f) da -= 360f
                if (da < -180f) da += 360f
                last = a
                acc += da
                if (!active && abs(acc) > 8f) active = true
                if (active) {
                    ev.changes.forEach { it.consume() }
                    val lines = (acc / 15f).toInt()
                    if (lines != 0) {
                        cb(lines)
                        acc -= lines * 15f
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- réglages

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
                .background(if (checked) Bg else Fg)
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
        Text(label, fontSize = 13.sp, color = Fg, modifier = Modifier.weight(1f))
        Pill(checked, accent)
    }
}

/** Case à bascule en demi-ligne (deux ou trois par ligne). */
@Composable
fun HalfToggle(
    label: String, checked: Boolean, accent: Color, onAccent: Color, modifier: Modifier,
    fontSp: Float = 12f, onChange: (Boolean) -> Unit
) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(if (checked) accent else RowBg)
            .clickable { onChange(!checked) }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = fontSp.sp, color = if (checked) onAccent else Dim) }
}

private fun snap(f: Float): Float = if (f < 0.03f) 0f else if (f > 0.97f) 1f else f

/**
 * Barre de curseur générique : fraction 0..1, onChange pendant le glissement, onFinish au relâchement.
 * onLong (optionnel) : appui long (temps de maintien) sans bouger -> sous-menu min/max.
 */
@Composable
fun SliderBar(
    label: String, valueText: String, fraction: Float, fill: Color, enabled: Boolean,
    onFinish: () -> Unit, onLong: (() -> Unit)?, onChange: (Float) -> Unit
) {
    val base = Modifier.fillMaxWidth(0.92f)
        .alpha(if (enabled) 1f else 0.4f)
        .clip(RoundedCornerShape(20.dp))
        .background(RowBg)
    val holdable = if (onLong != null && enabled) base.holdToConfirm { onLong() } else base
    Column(holdable.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = Fg, modifier = Modifier.weight(1f))
            Text(valueText, fontSize = 12.sp, color = Dim, style = Tnum)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(OffTrack)
                .pointerInput(enabled) {
                    if (enabled) {
                        detectTapGestures { off ->
                            onChange(snap((off.x / size.width).coerceIn(0f, 1f)))
                            onFinish()
                        }
                    }
                }
                .pointerInput(enabled) {
                    if (enabled) {
                        detectHorizontalDragGestures(onDragEnd = { onFinish() }) { change, _ ->
                            change.consume()
                            onChange(snap((change.position.x / size.width).coerceIn(0f, 1f)))
                        }
                    }
                }
        ) {
            // Rien du tout à 0 : plus de bande résiduelle
            if (fraction > 0.001f) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceAtMost(1f)).background(fill))
            }
        }
    }
}

/** Curseur lié à un SliderState (100 pas entre lo et hi). Appui long -> sous-menu min/max. */
@Composable
fun SliderRow(
    spec: SliderState, accent: Color, enabled: Boolean = true, unit: String = "",
    fmt: ((Float) -> String)? = null, onRelease: () -> Unit = {}
) {
    val ctx = LocalContext.current
    SliderBar(
        label = spec.label.t(),
        valueText = fmt?.invoke(spec.value) ?: (spec.text() + unit),
        fraction = spec.fraction(),
        fill = accent,
        enabled = enabled,
        onFinish = { Settings.persistSliders(ctx); onRelease() },
        onLong = { Ui.rangeKey = spec.key; Ui.screen = Screen.RANGE },
        onChange = { f -> spec.setFromFraction(f) }
    )
}

// ---------------------------------------------------------------- tours

/** Largeurs des colonnes, mesurées sur les valeurs réelles : chaque colonne ne prend que la place nécessaire. */
class LapCols(val num: Dp, val total: Dp, val lap: Dp, val delta: Dp) {
    fun sum(): Dp {
        var s = 0.dp
        if (Settings.colNum) s += num
        if (Settings.colTotal) s += total
        if (Settings.colLap) s += lap
        if (Settings.colDelta) s += delta
        return s
    }
}

@Composable
fun rememberLapCols(laps: List<Lap>, prevIsNext: Boolean, sz: Float): LapCols {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val lastKey = laps.firstOrNull()?.total ?: 0L
    val secMode = Settings.secMode
    return remember(laps.size, lastKey, sz, secMode) {
        val st = tight(sz).copy(fontSize = sz.sp)
        fun w(s: String): Dp = with(density) { measurer.measure(s, st).size.width.toDp() }
        val maxTotal = laps.maxOfOrNull { it.total } ?: 0L
        val maxLap = laps.maxOfOrNull { it.lapTime } ?: 0L
        var maxAbs = 0L
        var hasDelta = false
        laps.forEachIndexed { i, l ->
            val p = if (prevIsNext) laps.getOrNull(i + 1) else laps.getOrNull(i - 1)
            if (p != null) {
                hasDelta = true
                maxAbs = maxOf(maxAbs, abs(l.lapTime - p.lapTime))
            }
        }
        LapCols(
            w(if (laps.size >= 100) "000" else "00"),
            w(fmtFull(maxTotal)),
            w(fmtFull(maxLap)) + (sz * 0.6f).dp + 2.dp,
            if (hasDelta) w("−" + (maxAbs / 1000) + "." + ((maxAbs / 100) % 10)) else 0.dp
        )
    }
}

/**
 * [n° tour] [temps total] [dt depuis le dernier tour] [écart de dt vs tour précédent].
 * Colonnes alignées, de largeur mesurée ; l'espace libre est réparti entre elles, aucun texte n'est rogné.
 */
@Composable
fun LapRow(lap: Lap, prevLapTime: Long?, color: Color, marker: Int, sz: Float, cols: LapCols, availW: Dp) {
    val delta = if (prevLapTime == null) "" else {
        val d = lap.lapTime - prevLapTime
        val a = abs(d)
        (if (d >= 0) "+" else "−") + (a / 1000) + "." + ((a / 100) % 10)
    }
    val fs = sz.sp
    val st = tight(sz)
    val need = cols.sum()
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.requiredWidth(if (need > availW) need else availW),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (Settings.colNum) {
                Text("%02d".format(lap.index), fontSize = fs, color = Dim, style = st, maxLines = 1,
                    softWrap = false, overflow = TextOverflow.Visible, modifier = Modifier.width(cols.num))
            }
            if (Settings.colTotal) {
                Text(fmtFull(lap.total), fontSize = fs, color = Soft, style = st, maxLines = 1,
                    softWrap = false, overflow = TextOverflow.Visible, modifier = Modifier.width(cols.total))
            }
            if (Settings.colLap) {
                Row(Modifier.width(cols.lap), verticalAlignment = Alignment.CenterVertically) {
                    if (marker != 0) {
                        Marker(marker, color, (sz * 0.6f).dp)
                        Spacer(Modifier.width(2.dp))
                    }
                    Text(fmtFull(lap.lapTime), fontSize = fs, color = color, style = st, maxLines = 1,
                        softWrap = false, overflow = TextOverflow.Visible)
                }
            }
            if (Settings.colDelta) {
                Text(delta, fontSize = fs, color = Dim, style = st, textAlign = TextAlign.End, maxLines = 1,
                    softWrap = false, overflow = TextOverflow.Visible, modifier = Modifier.width(cols.delta))
            }
        }
    }
}
