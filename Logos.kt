package com.example.chrono

/** 10 styles de logo : « logo_XX » = icône colorée (aperçu + lanceur), « glyph_XX » = pictogramme blanc (tile, complication, notification). */
object Logos {
    val launcher = intArrayOf(
        R.drawable.logo_01, R.drawable.logo_02, R.drawable.logo_03, R.drawable.logo_04, R.drawable.logo_05,
        R.drawable.logo_06, R.drawable.logo_07, R.drawable.logo_08, R.drawable.logo_09, R.drawable.logo_10
    )
    val glyph = intArrayOf(
        R.drawable.glyph_01, R.drawable.glyph_02, R.drawable.glyph_03, R.drawable.glyph_04, R.drawable.glyph_05,
        R.drawable.glyph_06, R.drawable.glyph_07, R.drawable.glyph_08, R.drawable.glyph_09, R.drawable.glyph_10
    )
    val count: Int get() = launcher.size
}
