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
    var fade by mutableStateOf(false)
        private set
    var rgb by mutableIntStateOf(0x34D399)
        private set

    // Colonnes des tours
    var colNum by mutableStateOf(true)
        private set
    var colTotal by mutableStateOf(true)
        private set
    var colLap by mutableStateOf(true)
        private set
    var colDelta by mutableStateOf(true)
        private set

    // ---- Tailles
    var chronoSp by mutableIntStateOf(38)      // 10..90 sp
        private set
    var btnDp by mutableIntStateOf(58)         // 20..140 dp
        private set
    var lapSp by mutableIntStateOf(12)         // 5..40 sp
        private set

    // ---- Espacements, en % de la hauteur de l'écran (0 = les objets se touchent)
    var gapTimeBtnPct by mutableIntStateOf(3)  // chrono -> cercles
        private set
    var btnGapPct by mutableIntStateOf(4)      // entre les cercles
        private set
    var gapBtnLapPct by mutableIntStateOf(3)   // cercles -> tours
        private set
    var lineGapPct by mutableIntStateOf(3)     // entre les lignes de tours
        private set
    var lapWidthPct by mutableIntStateOf(90)   // largeur du bloc des tours, 40..100 % de la largeur
        private set
    var topPct by mutableIntStateOf(60)        // frontière haut / bas, 0..100 % de la hauteur
        private set

    // ---- Comportement
    var holdMs by mutableIntStateOf(2000)      // 100..10000 ms : Reset, effacement, cadenas
        private set
    var vibePct by mutableIntStateOf(100)      // 0..100 %
        private set
    var autoUnlockMin by mutableIntStateOf(0)  // 0 = jamais ; 0..1440 min
        private set
    var autoStopMin by mutableIntStateOf(0)    // 0 = désactivé ; 0..1440 min
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
        fade = s.getBoolean("fade", false)
        rgb = s.getInt("rgb", 0x34D399)
        colNum = s.getBoolean("col0", true)
        colTotal = s.getBoolean("col1", true)
        colLap = s.getBoolean("col2", true)
        colDelta = s.getBoolean("col3", true)
        chronoSp = s.getInt("chronoSp", 38)
        btnDp = s.getInt("btnDp", 58)
        lapSp = s.getInt("lapSp", 12)
        gapTimeBtnPct = s.getInt("gapTimeBtnPct", 3)
        btnGapPct = s.getInt("btnGapPct", 4)
        gapBtnLapPct = s.getInt("gapBtnLapPct", 3)
        lineGapPct = s.getInt("lineGapPct", 3)
        lapWidthPct = s.getInt("lapWidthPct", 90)
        topPct = s.getInt("topPct", 60)
        holdMs = s.getInt("holdMs", 2000)
        vibePct = s.getInt("vibePct", 100)
        autoUnlockMin = s.getInt("autoUnlockMin", 0)
        autoStopMin = s.getInt("autoStopMin", 0)
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
    fun setFade(c: Context, v: Boolean) { fade = v; p(c).edit().putBoolean("fade", v).apply() }
    fun setCol(c: Context, idx: Int, v: Boolean) {
        when (idx) {
            0 -> colNum = v
            1 -> colTotal = v
            2 -> colLap = v
            else -> colDelta = v
        }
        p(c).edit().putBoolean("col$idx", v).apply()
    }

    // Sliders : mise à jour immédiate en mémoire, écriture sur disque au relâchement (persistLayout)
    fun updateChronoSp(v: Int) { chronoSp = v.coerceIn(10, 90) }
    fun updateBtnDp(v: Int) { btnDp = v.coerceIn(20, 140) }
    fun updateLapSp(v: Int) { lapSp = v.coerceIn(5, 40) }
    fun updateGapTimeBtnPct(v: Int) { gapTimeBtnPct = v.coerceIn(0, 100) }
    fun updateBtnGapPct(v: Int) { btnGapPct = v.coerceIn(0, 100) }
    fun updateGapBtnLapPct(v: Int) { gapBtnLapPct = v.coerceIn(0, 100) }
    fun updateLineGapPct(v: Int) { lineGapPct = v.coerceIn(0, 100) }
    fun updateLapWidthPct(v: Int) { lapWidthPct = v.coerceIn(40, 100) }
    fun updateTopPct(v: Int) { topPct = v.coerceIn(0, 100) }
    fun updateHoldMs(v: Int) { holdMs = v.coerceIn(100, 10000) }
    fun updateVibePct(v: Int) { vibePct = v.coerceIn(0, 100) }
    fun updateAutoUnlockMin(v: Int) { autoUnlockMin = v.coerceIn(0, 1440) }
    fun updateAutoStopMin(v: Int) { autoStopMin = v.coerceIn(0, 1440) }
    fun updateEcoBrightness(v: Int) { ecoBrightness = v.coerceIn(0, 100) }

    fun persistLayout(c: Context) {
        p(c).edit()
            .putInt("chronoSp", chronoSp)
            .putInt("btnDp", btnDp)
            .putInt("lapSp", lapSp)
            .putInt("gapTimeBtnPct", gapTimeBtnPct)
            .putInt("btnGapPct", btnGapPct)
            .putInt("gapBtnLapPct", gapBtnLapPct)
            .putInt("lineGapPct", lineGapPct)
            .putInt("lapWidthPct", lapWidthPct)
            .putInt("topPct", topPct)
            .putInt("holdMs", holdMs)
            .putInt("vibePct", vibePct)
            .putInt("autoUnlockMin", autoUnlockMin)
            .putInt("autoStopMin", autoStopMin)
            .apply()
    }

    /** Remet la mise en page (tailles, espacements, colonnes, fondu) aux valeurs d'origine. */
    fun resetLayout(c: Context) {
        chronoSp = 38; btnDp = 58; lapSp = 12
        gapTimeBtnPct = 3; btnGapPct = 4; gapBtnLapPct = 3; lineGapPct = 3
        lapWidthPct = 90; topPct = 60
        colNum = true; colTotal = true; colLap = true; colDelta = true
        fade = false
        p(c).edit()
            .putBoolean("col0", true).putBoolean("col1", true)
            .putBoolean("col2", true).putBoolean("col3", true)
            .putBoolean("fade", false)
            .apply()
        persistLayout(c)
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
    val textSp: Float get() = lapSp.toFloat()
}
