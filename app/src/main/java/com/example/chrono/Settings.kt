package com.example.chrono

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Un curseur à 100 pas entre lo et hi (réglables par appui long : sous-menu min/max).
 * min0..max0 = bornes d'origine, larges, qui limitent les curseurs du sous-menu.
 * choices : valeurs discrètes (pas de sous-menu). stepProvider : pas imposé (ex. précision de la distance).
 */
class SliderState(
    val key: String, val label: S, val min0: Float, val max0: Float, val def: Float,
    val curve: Int = 1, val persistValue: Boolean = true,
    val choices: List<Float>? = null, val stepProvider: (() -> Float)? = null
) {
    var value by mutableFloatStateOf(def)
    var lo by mutableFloatStateOf(min0)
    var hi by mutableFloatStateOf(max0)

    private fun shape(t: Float) = if (curve == 2) t * t else t
    private fun unshape(t: Float) = if (curve == 2) sqrt(t) else t

    /** Plus petit écart autorisé entre lo et hi : 1 % de la plage d'origine. */
    val gap0: Float get() = (max0 - min0) / 100f

    /** Pas effectif : imposé, ou (hi - lo) / 100. */
    val effStep: Float get() = stepProvider?.invoke()?.coerceAtLeast(1e-4f) ?: ((hi - lo) / 100f)

    fun fraction(): Float {
        val ch = choices
        if (ch != null) {
            val i = ch.indices.minByOrNull { abs(ch[it] - value) } ?: 0
            return if (ch.size > 1) i / (ch.size - 1f) else 0f
        }
        return if (hi <= lo) 0f else unshape(((value - lo) / (hi - lo)).coerceIn(0f, 1f))
    }

    fun setFromFraction(f: Float) {
        val ch = choices
        if (ch != null) {
            value = ch[(f.coerceIn(0f, 1f) * (ch.size - 1)).roundToInt()]
            return
        }
        if (stepProvider != null) {
            val step = effStep
            val raw = lo + f.coerceIn(0f, 1f) * (hi - lo)
            val n = ((raw - lo) / step).roundToInt()
            value = (lo + n * step).coerceIn(lo, hi)
            return
        }
        val n = (f.coerceIn(0f, 1f) * 100f).roundToInt()
        value = lo + shape(n / 100f) * (hi - lo)
    }

    fun setRange(newLo: Float, newHi: Float) {
        lo = newLo; hi = newHi
        value = value.coerceIn(lo, hi)
    }

    fun resetRange() = setRange(min0, max0)
    fun resetValue() { value = def.coerceIn(lo, hi) }

    /** Texte d'une valeur, avec autant de décimales que le pas le demande. */
    fun text(v: Float = value, step: Float = effStep): String {
        if (choices != null) {
            return if (v % 1f == 0f) String.format(Locale.ROOT, "%.0f", v) else String.format(Locale.ROOT, "%.1f", v)
        }
        val d = if (step >= 1f) 0 else if (step >= 0.1f) 1 else 2
        return String.format(Locale.ROOT, "%.${d}f", v)
    }

    fun load(sp: SharedPreferences) {
        if (choices == null) {
            lo = sp.getFloat("sl_${key}_lo", min0)
            hi = sp.getFloat("sl_${key}_hi", max0)
            if (hi <= lo) { lo = min0; hi = max0 }
        }
        val v = if (persistValue) sp.getFloat("sl_$key", def) else def
        value = if (choices != null) (choices.minByOrNull { abs(it - v) } ?: def) else v.coerceIn(lo, hi)
    }

    fun save(ed: SharedPreferences.Editor) {
        if (persistValue) ed.putFloat("sl_$key", value)
        if (choices == null) {
            ed.putFloat("sl_${key}_lo", lo)
            ed.putFloat("sl_${key}_hi", hi)
        }
    }
}

object Settings {
    // ---- Interrupteurs
    var ring by mutableStateOf(true)
        private set
    var aod by mutableStateOf(false)
        private set
    var eco by mutableStateOf(false)
        private set
    var lock by mutableStateOf(false)
        private set
    var ringInLock by mutableStateOf(true)
        private set
    var lefty by mutableStateOf(false)
        private set
    var undoBtn by mutableStateOf(false)
        private set
    var secMode by mutableStateOf(false)
        private set
    var fade by mutableStateOf(false)
        private set
    var custom by mutableStateOf(false)
        private set
    var light by mutableStateOf(false)
        private set
    var touchRing by mutableStateOf(false)
        private set
    var autoScroll by mutableStateOf(true)
        private set
    var runIcons by mutableStateOf(true)
        private set
    var help by mutableStateOf(false)
        private set
    var track by mutableStateOf(false)
        private set
    var lang by mutableStateOf(Lang.FR)
        private set
    var logo by mutableIntStateOf(0)
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
    var colPace by mutableStateOf(false)
        private set
    var showAvg by mutableStateOf(true)
        private set

    // ---- Curseurs (valeur + plage lo..hi, 100 pas)
    val ecoBright = SliderState("ecoBright", S.ECO_BRIGHT, 0f, 100f, 0f, persistValue = false)
    val autoUnlock = SliderState("autoUnlock", S.AUTO_UNLOCK, 0f, 1440f, 0f, curve = 2)
    val chrono = SliderState("chrono", S.S_CHRONO, 10f, 90f, 38f)
    val btn = SliderState("btn", S.S_BTN, 20f, 140f, 58f)
    val lap = SliderState("lap", S.S_LAPS, 5f, 40f, 12f)
    val gapTimeBtn = SliderState("gapTimeBtn", S.GAP_TIME_BTN, 0f, 100f, 3f)
    val btnGap = SliderState("btnGap", S.GAP_BTN, 0f, 100f, 4f)
    val gapBtnLap = SliderState("gapBtnLap", S.GAP_BTN_LAP, 0f, 100f, 3f)
    val lineGap = SliderState("lineGap", S.GAP_LINES, 0f, 100f, 3f)
    val topPctS = SliderState("topPct", S.TOP_PCT, 0f, 100f, 60f)
    val lapWidth = SliderState("lapWidth", S.LAP_WIDTH, 40f, 100f, 90f)
    val hold = SliderState("hold", S.HOLD, 0.1f, 10f, 2f)
    val vibe = SliderState("vibe", S.VIBE, 0f, 100f, 100f)
    val autoStop = SliderState("autoStop", S.AUTO_STOP, 0f, 1440f, 0f, curve = 2)
    val snake = SliderState("snake", S.SNAKE, 1f, 100f, 12.5f)
    val ringEv = SliderState("ringEv", S.RING_EV, 1f, 20f, 1f, stepProvider = { 1f })
    val colR = SliderState("colR", S.RED, 0f, 255f, 52f)
    val colG = SliderState("colG", S.GREEN, 0f, 255f, 211f)
    val colB = SliderState("colB", S.BLUE, 0f, 255f, 153f)
    // QR
    val qrVer = SliderState("qrVer", S.QR_VERSION, 5f, 25f, 15f, stepProvider = { 1f })
    val qrEc = SliderState("qrEc", S.QR_EC, 0f, 3f, 1f, stepProvider = { 1f })
    // Suivi avancé
    val precision = SliderState(
        "precision", S.TRACK_PRECISION, 0.1f, 1000f, 1f,
        choices = listOf(0.1f, 0.2f, 0.5f, 1f, 2f, 5f, 10f, 20f, 50f, 100f, 200f, 500f, 1000f)
    )
    val distance = SliderState("distance", S.TRACK_DIST, 0f, 10000f, 0f, stepProvider = { precision.value })
    val exTime = SliderState("exTime", S.EX_TIME, 0f, 3600f, 0f, stepProvider = { 1f })
    val nbRep = SliderState("nbRep", S.EX_REPS, 1f, 100f, 1f, stepProvider = { 1f })
    val restTime = SliderState("restTime", S.REST_TIME, 0f, 3600f, 0f, stepProvider = { 1f })

    val sliders: List<SliderState> = listOf(
        ecoBright, autoUnlock, chrono, btn, lap, gapTimeBtn, btnGap, gapBtnLap, lineGap,
        topPctS, lapWidth, hold, vibe, autoStop, snake, ringEv, colR, colG, colB,
        qrVer, qrEc, precision, distance, exTime, nbRep, restTime
    )
    val byKey: Map<String, SliderState> = sliders.associateBy { it.key }

    // ---- Accès pratiques (lisent l'état : toujours dynamiques)
    val chronoSp: Float get() = chrono.value
    val btnDp: Float get() = btn.value
    val textSp: Float get() = lap.value
    val gapTimeBtnPct: Float get() = gapTimeBtn.value
    val btnGapPct: Float get() = btnGap.value
    val gapBtnLapPct: Float get() = gapBtnLap.value
    val lineGapPct: Float get() = lineGap.value
    val topPct: Float get() = topPctS.value
    val lapWidthPct: Float get() = lapWidth.value
    val vibePct: Float get() = vibe.value
    val snakePct: Float get() = snake.value
    val ecoBrightness: Float get() = ecoBright.value
    val holdMs: Long get() = (hold.value * 1000f).roundToInt().toLong()
    val autoUnlockMin: Int get() = autoUnlock.value.roundToInt()
    val autoStopMin: Int get() = autoStop.value.roundToInt()
    val ringEventsPerLine: Int get() = ringEv.value.roundToInt().coerceAtLeast(1)
    val qrVersion: Int get() = qrVer.value.roundToInt().coerceIn(5, 25)
    val qrLevel: Int get() = qrEc.value.roundToInt().coerceIn(0, 3)
    val distanceM: Float get() = distance.value
    val rgb: Int
        get() = ((colR.value.roundToInt() and 255) shl 16) or
            ((colG.value.roundToInt() and 255) shl 8) or
            (colB.value.roundToInt() and 255)

    val ringActive: Boolean get() = ring && !eco
    val aodActive: Boolean get() = aod && !eco
    /** La colonne Allure n'existe que si le suivi avancé est actif et qu'une distance est réglée. */
    val paceActive: Boolean get() = colPace && track && distance.value > 0f

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
        ringInLock = s.getBoolean("ringInLock", true)
        lefty = s.getBoolean("lefty", false)
        undoBtn = s.getBoolean("undo", false)
        secMode = s.getBoolean("sec", false)
        fade = s.getBoolean("fade", false)
        custom = s.getBoolean("custom", false)
        light = s.getBoolean("light", false)
        touchRing = s.getBoolean("touchRing", false)
        autoScroll = s.getBoolean("autoScroll", true)
        runIcons = s.getBoolean("runIcons", true)
        help = s.getBoolean("help", false)
        track = s.getBoolean("track", false)
        lang = when (s.getString("lang", "fr")) { "en" -> Lang.EN; "zh" -> Lang.ZH; else -> Lang.FR }
        logo = s.getInt("logo", 0).coerceIn(0, 11)
        colNum = s.getBoolean("col0", true)
        colTotal = s.getBoolean("col1", true)
        colLap = s.getBoolean("col2", true)
        colDelta = s.getBoolean("col3", true)
        colPace = s.getBoolean("col4", false)
        showAvg = s.getBoolean("showAvg", true)
        sliders.forEach { it.load(s) }
    }

    fun setRing(c: Context, v: Boolean) { ring = v; p(c).edit().putBoolean("ring", v).apply() }
    fun setAod(c: Context, v: Boolean) { aod = v; p(c).edit().putBoolean("aod", v).apply() }
    fun setEco(c: Context, v: Boolean) {
        eco = v
        if (v) ecoBright.resetValue()      // la luminosité éco repart de 0 % à chaque activation
        p(c).edit().putBoolean("eco", v).apply()
    }
    fun setLock(c: Context, v: Boolean) { lock = v; p(c).edit().putBoolean("lock", v).apply() }
    fun setRingInLock(c: Context, v: Boolean) { ringInLock = v; p(c).edit().putBoolean("ringInLock", v).apply() }
    fun setLefty(c: Context, v: Boolean) { lefty = v; p(c).edit().putBoolean("lefty", v).apply() }
    fun setUndoBtn(c: Context, v: Boolean) { undoBtn = v; p(c).edit().putBoolean("undo", v).apply() }
    fun setSecMode(c: Context, v: Boolean) { secMode = v; p(c).edit().putBoolean("sec", v).apply() }
    fun setFade(c: Context, v: Boolean) { fade = v; p(c).edit().putBoolean("fade", v).apply() }
    fun setCustom(c: Context, v: Boolean) { custom = v; p(c).edit().putBoolean("custom", v).apply() }
    fun setLight(c: Context, v: Boolean) { light = v; p(c).edit().putBoolean("light", v).apply() }
    fun setTouchRing(c: Context, v: Boolean) { touchRing = v; p(c).edit().putBoolean("touchRing", v).apply() }
    fun setAutoScroll(c: Context, v: Boolean) { autoScroll = v; p(c).edit().putBoolean("autoScroll", v).apply() }
    fun setRunIcons(c: Context, v: Boolean) { runIcons = v; p(c).edit().putBoolean("runIcons", v).apply() }
    fun setHelp(c: Context, v: Boolean) { help = v; p(c).edit().putBoolean("help", v).apply() }
    fun setTrack(c: Context, v: Boolean) {
        track = v
        p(c).edit().putBoolean("track", v).apply()
        if (v) Segments.schedule(c) else Segments.cancel(c)
    }
    fun setLang(c: Context, l: Lang) {
        lang = l
        p(c).edit().putString("lang", when (l) { Lang.EN -> "en"; Lang.ZH -> "zh"; else -> "fr" }).apply()
    }
    /** Change le logo : icône de l'appli (alias de lancement), tile, complication et notification. */
    fun setLogo(c: Context, i: Int) {
        logo = i.coerceIn(0, 11)
        p(c).edit().putInt("logo", logo).apply()
        Launcher.apply(c, logo)
        Launcher.refresh(c)
    }
    fun setCol(c: Context, idx: Int, v: Boolean) {
        when (idx) {
            0 -> colNum = v
            1 -> colTotal = v
            2 -> colLap = v
            3 -> colDelta = v
            else -> colPace = v
        }
        p(c).edit().putBoolean("col$idx", v).apply()
    }
    fun setShowAvg(c: Context, v: Boolean) { showAvg = v; p(c).edit().putBoolean("showAvg", v).apply() }

    /** Écrit toutes les valeurs et plages des curseurs (appelé au relâchement d'un curseur). */
    fun persistSliders(c: Context) {
        val ed = p(c).edit()
        sliders.forEach { it.save(ed) }
        ed.apply()
    }

    /** Remet la mise en page (tailles, espacements, disposition, colonnes, fondu) aux valeurs d'origine. */
    fun resetLayout(c: Context) {
        listOf(chrono, btn, lap, gapTimeBtn, btnGap, gapBtnLap, lineGap, topPctS, lapWidth).forEach {
            it.resetRange(); it.resetValue()
        }
        colNum = true; colTotal = true; colLap = true; colDelta = true; colPace = false; showAvg = true
        fade = false
        p(c).edit()
            .putBoolean("col0", true).putBoolean("col1", true)
            .putBoolean("col2", true).putBoolean("col3", true)
            .putBoolean("col4", false).putBoolean("showAvg", true)
            .putBoolean("fade", false)
            .apply()
        persistSliders(c)
    }
}
