package com.example.chrono

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

val Dim = Color(0xFF9CA3AF)
val Soft = Color(0xFFD1D5DB)
val SurfaceBtn = Color(0xFF2B2F36)
val RowBg = Color(0xFF1B1F26)
val OffTrack = Color(0xFF3A3F47)
private val RingTrack = Color(0xFF0E1218)
val Tnum = TextStyle(fontFeatureSettings = "tnum")

enum class Screen { MAIN, SETTINGS, HISTORY, SESSION }

object Ui {
    var screen by mutableStateOf(Screen.MAIN)
    var sessionIndex by mutableIntStateOf(0)
    var ambient by mutableStateOf(false)
    var locked by mutableStateOf(false)
    var lockedAt = 0L
    var hold by mutableFloatStateOf(0f)
}

// ---------------------------------------------------------------- bague

/** Cercle presque noir + « snake » de 1/8 de tour (blanc -> couleur du cercle). frac = avancement 0..1. */
@Composable
fun Ring(frac: () -> Float) {
    Canvas(Modifier.fillMaxSize().padding(3.dp)) {
        val stroke = 6.dp.toPx()
        val topLeft = Offset(stroke / 2, stroke / 2)
        val sz = Size(size.width - stroke, size.height - stroke)
        drawArc(RingTrack, 0f, 360f, false, topLeft, sz, style = Stroke(stroke))
        val head = -90f + frac() * 360f
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

// ---------------------------------------------------------------- temps

/** Chiffres à chasse fixe, interligne 0,8 em et marges haut/bas supprimées : à 0 % d'espacement, les objets se touchent. */
fun tight(sizeSp: Float): TextStyle = TextStyle(
    fontFeatureSettings = "tnum",
    lineHeight = (sizeSp * 0.8f).sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
)

/** Fondu progressif en haut et en bas d'une liste (option désactivée par défaut). */
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
    val base = Settings.chronoSp.toFloat()
    val big = if (!Settings.secMode && ms >= 3_600_000L) base * 0.79f else base
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            fmtMain(ms),
            fontSize = big.sp,
            fontWeight = FontWeight.Medium, color = Color.White, style = tight(big)
        )
        if (!eco) {
            Text(fmtTenth(ms), fontSize = (big * 0.47f).sp, color = Dim, style = tight(big * 0.47f))
        }
    }
}

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
                drawLine(Color.White, Offset(0f, y), Offset(size.width, y), strokeWidth = w, cap = StrokeCap.Round)
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
            drawCircle(Color.White, r, c, style = Stroke(w))
            drawLine(Color.White, c, Offset(c.x, c.y - r * 0.55f), w, StrokeCap.Round)
            drawLine(Color.White, c, Offset(c.x + r * 0.45f, c.y), w, StrokeCap.Round)
        }
    }
}

@Composable
fun LockIcon() {
    Canvas(Modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            Color.White,
            topLeft = Offset(0f, h * 0.45f),
            size = Size(w, h * 0.55f),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
        drawArc(
            Color.White, 180f, 180f, false,
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

// ---------------------------------------------------------------- boutons

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

/** Appui long de holdMs ms : la progression s'affiche via Ui.hold (HoldRing). Annulé si on relâche avant. */
@Composable
fun Modifier.holdToConfirm(holdMs: Long, onDone: () -> Unit): Modifier {
    val done by rememberUpdatedState(onDone)
    return this.pointerInput(Unit) {
        detectTapGestures(onPress = {
            coroutineScope {
                val job = launch {
                    val t0 = SystemClock.uptimeMillis()
                    while (true) {
                        delay(16)
                        val p = (SystemClock.uptimeMillis() - t0).toFloat() / holdMs
                        Ui.hold = p.coerceAtMost(1f)
                        if (p >= 1f) {
                            done()
                            Ui.hold = 0f
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
fun HoldButton(label: String, dim: Dp, bg: Color, fg: Color, fs: TextUnit, holdMs: Long, onDone: () -> Unit) {
    Box(
        Modifier.size(dim).clip(CircleShape).background(bg).holdToConfirm(holdMs, onDone),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = fs, color = fg) }
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

private fun snap(f: Float): Float = if (f < 0.03f) 0f else if (f > 0.97f) 1f else f

/** Curseur horizontal : fraction 0..1, onChange pendant le glissement, onFinish au relâchement. */
@Composable
fun SliderRow(
    label: String, valueText: String, fraction: Float, fill: Color,
    enabled: Boolean = true, onFinish: () -> Unit = {}, onChange: (Float) -> Unit
) {
    Column(
        Modifier.fillMaxWidth(0.92f)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(20.dp))
            .background(RowBg)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
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

// ---------------------------------------------------------------- tours

/** [n° tour] [temps total] [dt depuis le dernier tour] [écart de dt vs tour précédent] — colonnes au choix, même taille partout. */
@Composable
fun LapRow(lap: Lap, prevLapTime: Long?, color: Color, marker: Int, sz: Float, widthFrac: Float) {
    val delta = if (prevLapTime == null) "" else {
        val d = lap.lapTime - prevLapTime
        val a = abs(d)
        (if (d >= 0) "+" else "−") + (a / 1000) + "." + ((a / 100) % 10)
    }
    val fs = sz.sp
    val u = sz.dp
    val st = tight(sz)
    Row(
        Modifier.fillMaxWidth(widthFrac),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (Settings.colNum) {
            Text("%02d".format(lap.index), fontSize = fs, color = Dim, style = st, maxLines = 1, softWrap = false,
                modifier = Modifier.width(u * 2.2f))
        }
        if (Settings.colTotal) {
            Text(fmtFull(lap.total), fontSize = fs, color = Soft, style = st, maxLines = 1, softWrap = false,
                modifier = Modifier.weight(1f))
        }
        if (Settings.colLap) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (marker != 0) {
                    Marker(marker, color, u * 0.6f)
                    Spacer(Modifier.width(2.dp))
                }
                Text(fmtFull(lap.lapTime), fontSize = fs, color = color, style = st, maxLines = 1, softWrap = false)
            }
        }
        if (Settings.colDelta) {
            Text(delta, fontSize = fs, color = Dim, style = st, textAlign = TextAlign.End, maxLines = 1, softWrap = false,
                modifier = Modifier.width(u * 3.6f).padding(end = 6.dp))
        }
    }
}

/** Case à bascule en demi-ligne (deux par ligne). */
@Composable
fun HalfToggle(label: String, checked: Boolean, accent: Color, onAccent: Color, modifier: Modifier, onChange: (Boolean) -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(if (checked) accent else RowBg)
            .clickable { onChange(!checked) }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 12.sp, color = if (checked) onAccent else Dim) }
}
