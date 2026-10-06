package com.example.chrono

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.Locale

/** Capacités en octets (mode binaire) des versions 5..25, niveaux L, M, Q, H. */
private val CAP = arrayOf(
    intArrayOf(106, 84, 60, 44), intArrayOf(134, 106, 74, 58), intArrayOf(154, 122, 86, 64),
    intArrayOf(192, 152, 108, 84), intArrayOf(230, 180, 130, 98), intArrayOf(271, 213, 151, 119),
    intArrayOf(321, 251, 177, 137), intArrayOf(367, 287, 203, 155), intArrayOf(425, 331, 241, 177),
    intArrayOf(458, 362, 258, 194), intArrayOf(520, 412, 292, 220), intArrayOf(586, 450, 322, 250),
    intArrayOf(644, 504, 364, 280), intArrayOf(718, 560, 394, 310), intArrayOf(792, 624, 442, 338),
    intArrayOf(858, 666, 482, 382), intArrayOf(929, 711, 509, 403), intArrayOf(1003, 779, 565, 439),
    intArrayOf(1091, 857, 611, 461), intArrayOf(1171, 911, 661, 511), intArrayOf(1273, 997, 715, 535)
)

val QR_LEVELS = listOf("L", "M", "Q", "H")
val QR_RECOVERY = listOf("7 %", "15 %", "25 %", "30 %")

fun qrCapacity(version: Int, ec: Int): Int = CAP[(version - 5).coerceIn(0, 20)][ec.coerceIn(0, 3)]

/** Nombre approximatif de tours par code (tours de moins de 100 s : « 73.12, » = 7 caractères). */
fun qrLapsApprox(version: Int, ec: Int): Int = qrCapacity(version, ec) / 7

/** Un temps de tour en secondes, au centième : 73120 ms -> « 73.12 ». */
fun lapSeconds(ms: Long): String = String.format(Locale.ROOT, "%.2f", ms / 1000.0)

/** Découpe en blocs : chaque bloc est une liste Python valide « [73.12, 44.23, ...] » de taille <= cap. */
fun qrBlocks(items: List<String>, cap: Int): List<String> {
    val out = ArrayList<String>()
    val cur = StringBuilder("[")
    var count = 0
    for (s in items) {
        val add = (if (count > 0) ", " else "") + s
        if (count > 0 && cur.length + add.length + 1 > cap) {
            out.add(cur.append("]").toString())
            cur.setLength(0)
            cur.append("[").append(s)
            count = 1
        } else {
            cur.append(add)
            count++
        }
    }
    out.add(cur.append("]").toString())
    return out
}

fun qrMatrix(text: String, version: Int, ec: Int): BitMatrix? = try {
    val level = when (ec) {
        0 -> ErrorCorrectionLevel.L
        1 -> ErrorCorrectionLevel.M
        2 -> ErrorCorrectionLevel.Q
        else -> ErrorCorrectionLevel.H
    }
    val hints = mapOf<EncodeHintType, Any>(
        EncodeHintType.ERROR_CORRECTION to level,
        EncodeHintType.QR_VERSION to version,
        EncodeHintType.MARGIN to 0
    )
    QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
} catch (e: Exception) {
    null
}
