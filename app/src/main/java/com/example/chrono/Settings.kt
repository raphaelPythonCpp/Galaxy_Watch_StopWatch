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
    var rgb by mutableIntStateOf(0x34D399)
        private set

    // ---- Mise en page (valeurs par défaut = l'apparence précédente)
    /** Taille du temps réel, en sp (10..90). */
    var chronoSp by mutableIntStateOf(38)
        private set
    /** Diamètre des cercles Start / Stop / Tour / Reset / Annuler, en dp (20..140). */
    var btnDp by mutableIntStateOf(58)
        private set
    /** Taille du texte des tours, en sp (5..40). */
    var lapSp by mutableIntStateOf(12)
        private set
    /** Écart temps réel -> cercles, en dp (0..100). */
    var gapTimeBtn by mutableIntStateOf(6)
        private set
    /** Écart cercles -> texte des tours, en dp (0..100). */
    var gapBtnLap by mutableIntStateOf(0)
        private set
    /** Part de l'écran (0..100 %) occupée par le haut (temps + boutons) ; le bas contient les tours. */
    var topPct by mutableIntStateOf(60)
        private set
    /** Durée d'un appui long (Reset, effacement de l'historique, sortie du verrou tactile), en ms (100..10000). */
    var holdMs by mutableIntStateOf(2000)
        private set
    /** Luminosité du mode éco : jamais sauvegardée, revient à 0 % à chaque activation / relance. */
    var ecoBrightness by mutableIntStateOf(0)
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
        rgb = s.getInt("rgb", 0x34D399)
        chronoSp = s.getInt("chronoSp", 38)
        btnDp = s.getInt("btnDp", 58)
        lapSp = s.getInt("lapSp", 12)
        gapTimeBtn = s.getInt("gapTimeBtn", 6)
        gapBtnLap = s.getInt("gapBtnLap", 0)
        topPct = s.getInt("topPct", 60)
        holdMs = s.getInt("holdMs", 2000)
        ecoBrightness = 0
    }

    fun setRing(c: Context, v: Boolean) { ring = v; p(c).edit().putBoolean("ring", v).apply() }
    fun setAod(c: Context, v: Boolean) { aod = v; p(c).edit().putBoolean("aod", v).apply() }
    fun setEco(c: Context, v: Boolean) {
        eco = v
        if (v) ecoBrightness = 0
        p(c).edit().putBoolean("eco", v).apply()
    }
    fun setLock(c: Context, v: Boolean) { lock = v; p(c).edit().putBoolean("lock", v).apply() }
    fun setLefty(c: Context, v: Boolean) { lefty = v; p(c).edit().putBoolean("lefty", v).apply() }
    fun setUndoBtn(c: Context, v: Boolean) { undoBtn = v; p(c).edit().putBoolean("undo", v).apply() }
    fun setSecMode(c: Context, v: Boolean) { secMode = v; p(c).edit().putBoolean("sec", v).apply() }

    // Sliders : mise à jour immédiate en mémoire, écriture sur disque au relâchement (persistLayout)
    fun updateChronoSp(v: Int) { chronoSp = v.coerceIn(10, 90) }
    fun updateBtnDp(v: Int) { btnDp = v.coerceIn(20, 140) }
    fun updateLapSp(v: Int) { lapSp = v.coerceIn(5, 40) }
    fun updateGapTimeBtn(v: Int) { gapTimeBtn = v.coerceIn(0, 100) }
    fun updateGapBtnLap(v: Int) { gapBtnLap = v.coerceIn(0, 100) }
    fun updateTopPct(v: Int) { topPct = v.coerceIn(0, 100) }
    fun updateHoldMs(v: Int) { holdMs = v.coerceIn(100, 10000) }
    fun updateEcoBrightness(v: Int) { ecoBrightness = v.coerceIn(0, 100) }

    fun persistLayout(c: Context) {
        p(c).edit()
            .putInt("chronoSp", chronoSp)
            .putInt("btnDp", btnDp)
            .putInt("lapSp", lapSp)
            .putInt("gapTimeBtn", gapTimeBtn)
            .putInt("gapBtnLap", gapBtnLap)
            .putInt("topPct", topPct)
            .putInt("holdMs", holdMs)
            .apply()
    }

    /** channel : 0 = R, 1 = G, 2 = B. */
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
    /** Taille (sp) du texte des tours. */
    val textSp: Float get() = lapSp.toFloat()
}
