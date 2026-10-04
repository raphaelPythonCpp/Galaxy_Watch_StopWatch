package com.example.chrono

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Settings {
    var ring by mutableStateOf(true)
        private set
    var aod by mutableStateOf(false)
        private set
    var eco by mutableStateOf(false)
        private set
    var lock by mutableStateOf(false)
        private set
    var lefty by mutableStateOf(false)
        private set
    var undoBtn by mutableStateOf(false)
        private set
    var secMode by mutableStateOf(false)
        private set
    var textLevel by mutableIntStateOf(5)
        private set
    var rgb by mutableIntStateOf(0x34D399)
        private set
    /** Luminosité du mode éco : jamais sauvegardée, revient à 30 % à chaque activation / relance. */
    var ecoBrightness by mutableIntStateOf(30)
        private set

    private var loaded = false
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val s = p(c)
        ring = s.getBoolean("ring", true)
        aod = s.getBoolean("aod", false)
        eco = s.getBoolean("eco", false)
        lock = s.getBoolean("lock", false)
        lefty = s.getBoolean("lefty", false)
        undoBtn = s.getBoolean("undo", false)
        secMode = s.getBoolean("sec", false)
        textLevel = s.getInt("textLevel", 5)
        rgb = s.getInt("rgb", 0x34D399)
        ecoBrightness = 30
    }

    fun setRing(c: Context, v: Boolean) { ring = v; p(c).edit().putBoolean("ring", v).apply() }
    fun setAod(c: Context, v: Boolean) { aod = v; p(c).edit().putBoolean("aod", v).apply() }
    fun setEco(c: Context, v: Boolean) {
        eco = v
        if (v) ecoBrightness = 30
        p(c).edit().putBoolean("eco", v).apply()
    }
    fun setLock(c: Context, v: Boolean) { lock = v; p(c).edit().putBoolean("lock", v).apply() }
    fun setLefty(c: Context, v: Boolean) { lefty = v; p(c).edit().putBoolean("lefty", v).apply() }
    fun setUndoBtn(c: Context, v: Boolean) { undoBtn = v; p(c).edit().putBoolean("undo", v).apply() }
    fun setSecMode(c: Context, v: Boolean) { secMode = v; p(c).edit().putBoolean("sec", v).apply() }
    fun setTextLevel(c: Context, v: Int) {
        val x = v.coerceIn(1, 10)
        if (x == textLevel) return
        textLevel = x
        p(c).edit().putInt("textLevel", x).apply()
    }
    fun updateEcoBrightness(v: Int) { ecoBrightness = v.coerceIn(0, 100) }

    /** channel : 0 = R, 1 = G, 2 = B. Sauvegarde différée (persistRgb) pour ne pas écrire à chaque glissement. */
    fun setRgbChannel(channel: Int, v: Int) {
        val x = v.coerceIn(0, 255)
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        rgb = when (channel) {
            0 -> (x shl 16) or (g shl 8) or b
            1 -> (r shl 16) or (x shl 8) or b
            else -> (r shl 16) or (g shl 8) or x
        }
    }
    fun persistRgb(c: Context) { p(c).edit().putInt("rgb", rgb).apply() }

    val ringActive: Boolean get() = ring && !eco
    val aodActive: Boolean get() = aod && !eco
    /** Taille (sp) du texte des listes : niveau 1..10 -> 8..17 sp (niveau 5 = 12 sp). */
    val textSp: Float get() = 7f + textLevel
}
