package com.example.chrono

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/** accent = couleur de référence ; mid = +90° de teinte ; inverse = teinte opposée (+180°). */
class Palette(val accent: Color, val mid: Color, val inverse: Color, val inverseDark: Color) {
    val onAccent: Color get() = if (accent.luminance() > 0.5f) Color.Black else Color.White

    fun gradient(t: Float): Color {
        val x = t.coerceIn(0f, 1f)
        return if (x < 0.5f) lerp(accent, mid, x * 2f) else lerp(mid, inverse, (x - 0.5f) * 2f)
    }
}

private fun hueShift(c: Color, deg: Float, vMul: Float = 1f): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toArgb(), hsv)
    hsv[0] = (hsv[0] + deg) % 360f
    hsv[2] = (hsv[2] * vMul).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

fun makePalette(rgb: Int): Palette {
    val base = Color(0xFF000000.toInt() or (rgb and 0xFFFFFF))
    return Palette(base, hueShift(base, 90f), hueShift(base, 180f), hueShift(base, 180f, 0.7f))
}

fun Color.argbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

fun lapColor(v: Long, vMin: Long, vMax: Long, eco: Boolean, pal: Palette): Color =
    if (eco) Fg
    else pal.gradient(if (vMax > vMin) (v - vMin).toFloat() / (vMax - vMin) else 0f)

/**
 * Triangle d'un tour par rapport à sa référence : 1 = plus rapide (▲), -1 = plus lent (▼),
 * 0 = identique ou pas de référence (premier tour, première série).
 */
fun relMarker(v: Long, ref: Long?): Int =
    if (ref == null || v == ref) 0 else if (v < ref) 1 else -1
