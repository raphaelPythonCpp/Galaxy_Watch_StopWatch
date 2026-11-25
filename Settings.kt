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

/** Durée lisible : « 45 s », « 12 min 05 s », « 1 h 05 min » (au-delà d'une heure, les secondes sont omises). */
fun fmtDur(sec: Int, zero: String = "0 s"): String {
    val s = sec.coerceAtLeast(0)
    return when {
        s == 0 -> zero
        s < 60 -> "$s s"
        s < 3600 -> {
            val m = s / 60
            val r = s % 60
            if (r == 0) "$m min" else String.format(Locale.ROOT, "%d min %02d s", m, r)
        }
        else -> {
            val h = s / 3600
            val m = (s % 3600) / 60
            if (m == 0) "$h h" else String.format(Locale.ROOT, "%d h %02d min", h, m)
        }
    }
}

/** Valeurs « jolies » des curseurs en échelle log (le 0 est toujours le 1er cran). Temps en secondes, distances en mètres. */
object NiceValues {
    val time: List<Float> = listOf(
        0f, 1f, 2f, 3f, 4f, 5f, 6f, 8f, 10f, 12f, 15f, 20f, 25f, 30f, 40f, 45f, 50f,
        60f, 75f, 90f, 120f, 150f, 180f, 240f, 300f, 360f, 420f, 480f, 540f, 600f, 720f, 900f,
        1200f, 1500f, 1800f, 2400f, 2700f, 3000f, 3600f, 4500f, 5400f, 7200f, 9000f, 10800f,
        14400f, 18000f, 21600f, 28800f, 36000f, 43200f, 57600f, 72000f, 86400f
    )
    val dist: List<Float> = listOf(
        0f, 0.1f, 0.2f, 0.25f, 0.3f, 0.4f, 0.5f, 0.6f, 0.75f, 0.8f, 1f, 1.5f, 2f, 2.5f, 3f, 4f, 5f, 6f, 7.5f, 8f,
        10f, 12f, 15f, 20f, 25f, 30f, 40f, 50f, 60f, 75f, 80f, 100f, 110f, 120f, 150f, 200f, 250f, 300f, 400f,
        500f, 600f, 750f, 800f, 1000f, 1200f, 1500f, 2000f, 2500f, 3000f, 4000f, 5000f, 6000f, 7500f, 8000f,
        10000f, 12000f, 15000f, 20000f, 25000f, 30000f, 40000f, 50000f, 60000f, 75000f, 80000f, 100000f
    )

    /** true si v est un multiple (à la tolérance près) du pas ; un pas <= 0 n'impose rien. */
    fun isMultiple(v: Float, step: Float): Boolean {
        if (step <= 0f) return true
        val r = v / step
        return abs(r - r.roundToInt()) < 1e-3f
    }
}

/** Distance jolie : « 400 m », « 2,5 m », « 1,5 km », « 12 km » (virgule en français, point sinon). */
/** Millisecondes lisibles : « 250 ms », « 1.250 s ». */
fun fmtMs(ms: Int): String {
    val v = ms.coerceAtLeast(0)
    return if (v < 1000) "$v ms" else "${v / 1000}.${(v % 1000).toString().padStart(3, '0')} s"
}

fun fmtDist(m: Float): String {
    fun trim(x: Float, decs: Int): String {
        var s = String.format(Locale.ROOT, "%.${decs}f", x)
        if (s.contains('.')) s = s.trimEnd('0').trimEnd('.')
        return if (Settings.lang == Lang.FR) s.replace('.', ',') else s
    }
    return if (m < 1000f) trim(m, 2) + " m" else trim(m / 1000f, 3) + " km"
}

/** Nombre de décimales utiles pour un pas donné : 0 (pas >= 1), 1 (pas >= 0,1) ou 2. */
private fun decsFor(step: Float): Int = if (step >= 1f) 0 else if (step >= 0.1f) 1 else 2

/** Arrondit v au nombre de décimales imposé par le pas (la valeur réelle correspond ainsi toujours à ce qui est affiché). */
private fun quant(v: Float, step: Float): Float {
    val m = when (decsFor(step)) { 0 -> 1f; 1 -> 10f; else -> 100f }
    return (v * m).roundToInt() / m
}

/**
 * Un curseur à 100 pas entre lo et hi (réglables par appui long : sous-menu min/max).
 * min0..max0 = bornes d'origine, larges, qui limitent les curseurs du sous-menu.
 * choices : valeurs discrètes (pas de sous-menu). stepProvider : pas imposé (ex. précision de la distance).
 * kind : 0 = valeur brute, 1 = durée en secondes, 2 = durée en minutes (affichées en s / min / h).
 * isInt : curseur ENTIER (valeur, min et max du sous-menu) : répétitions, couleurs, versions QR, durées, tailles...
 * Sans isInt, la valeur est décimale et arrondie aux décimales du pas (distances, espacements, snake, temps de maintien).
 */
class SliderState(
    val key: String, val label: S, val min0: Float, val max0: Float, val def: Float,
    val curve: Int = 1, val persistValue: Boolean = true,
    val choices: List<Float>? = null, val stepProvider: (() -> Float)? = null,
    val kind: Int = 0, val isInt: Boolean = false,
    /** Échelle « liste log » de valeurs rondes : 0 = non, 1 = temps (secondes), 2 = distance (mètres). Le 1er cran est 0. */
    val nice: Int = 0,
    /** Pas de sous-menu min/max (appui long = explication seule). */
    val noRange: Boolean = false
) {
    /** Contrainte appliquée après chaque réglage au curseur (ex. min <= max du départ random) ; null = aucune. Affectée après la création (pas de référence croisée à l'initialisation). */
    var clampTo: ((Float) -> Float)? = null
    var value by mutableFloatStateOf(def)
    var lo by mutableFloatStateOf(min0)
    var hi by mutableFloatStateOf(max0)

    /** Le curseur a un sous-menu min/max. */
    val hasRange: Boolean get() = choices == null && !noRange

    private fun niceFull(): List<Float> = if (nice == 2) NiceValues.dist else NiceValues.time

    private fun nearestIdx(list: List<Float>, v: Float): Int {
        var best = 0
        var bd = Float.MAX_VALUE
        for (i in list.indices) {
            val d = abs(list[i] - v)
            if (d < bd) { bd = d; best = i }
        }
        return best
    }

    /** Valeurs proposées : liste ronde dans [lo, hi], multiples du pas imposé (0 toujours accepté s'il est dans la plage). */
    private fun niceEff(): List<Float> {
        val st = stepProvider?.invoke() ?: 0f
        val out = niceFull().filter { v -> v >= lo - 1e-4f && v <= hi + 1e-4f && (v == 0f || NiceValues.isMultiple(v, st)) }
        return if (out.isEmpty()) listOf(lo) else out
    }

    /** Recale la valeur sur la liste effective (après un changement de précision, de plage ou une relecture). */
    fun snapNice() {
        if (nice == 0) return
        val l = niceEff()
        val n = l[nearestIdx(l, value)]
        if (n != value) value = n
    }

    /** Position 0..1 d'une borne du sous-menu min/max sur la liste complète. */
    fun rangeFraction(v: Float): Float {
        val l = niceFull()
        return nearestIdx(l, v) / (l.size - 1f)
    }

    /** Borne du sous-menu pour une fraction 0..1 : (valeur, true = min / false = max) garantit min < max. */
    fun rangeValue(f: Float): Float {
        val l = niceFull()
        return l[(f.coerceIn(0f, 1f) * (l.size - 1)).roundToInt()]
    }

    fun niceBelow(v: Float): Float = niceFull().lastOrNull { it < v - 1e-6f } ?: v
    fun niceAbove(v: Float): Float = niceFull().firstOrNull { it > v + 1e-6f } ?: v

    private fun shape(t: Float) = if (curve == 2) t * t else t
    private fun unshape(t: Float) = if (curve == 2) sqrt(t) else t

    /** Plus petit écart autorisé entre lo et hi : 1 % de la plage d'origine (au moins 1 pour un curseur entier). */
    val gap0: Float get() {
        val g = (max0 - min0) / 100f
        return if (isInt) maxOf(1f, g.roundToInt().toFloat()) else g
    }

    /** Pas effectif : imposé, ou (hi - lo) / 100 ; au moins 1 pour un curseur entier. */
    val effStep: Float get() {
        val s = stepProvider?.invoke()?.coerceAtLeast(1e-4f) ?: ((hi - lo) / 100f)
        return if (isInt) maxOf(1f, s.roundToInt().toFloat()) else s
    }

    /** Arrondit une borne min / max du sous-menu : entier, ou décimales du plus petit écart. */
    fun fixRange(v: Float): Float = if (nice > 0) v else if (isInt) v.roundToInt().toFloat() else quant(v, gap0)

    fun fraction(): Float {
        if (nice > 0) {
            val l = niceEff()
            return if (l.size > 1) nearestIdx(l, value) / (l.size - 1f) else 0f
        }
        val ch = choices
        if (ch != null) {
            val i = ch.indices.minByOrNull { abs(ch[it] - value) } ?: 0
            return if (ch.size > 1) i / (ch.size - 1f) else 0f
        }
        return if (hi <= lo) 0f else unshape(((value - lo) / (hi - lo)).coerceIn(0f, 1f))
    }

    fun setFromFraction(f: Float) {
        setFromFractionRaw(f)
        val cl = clampTo
        if (cl != null) {
            val v = cl(value)
            if (v != value) value = v
        }
    }

    private fun setFromFractionRaw(f: Float) {
        val ch = choices
        val ff = f.coerceIn(0f, 1f)
        if (nice > 0) {
            val l = niceEff()
            value = l[(ff * (l.size - 1)).roundToInt().coerceIn(0, l.size - 1)]
            return
        }
        if (ch != null) {
            value = ch[(ff * (ch.size - 1)).roundToInt()]
            return
        }
        if (stepProvider != null) {
            // Pas imposé (précision) : la valeur est toujours un multiple du pas, quelle que soit la borne basse
            val step = effStep
            val raw = lo + ff * (hi - lo)
            val v = (raw / step).roundToInt() * step
            value = (if (isInt) v.roundToInt().toFloat() else quant(v, step)).coerceIn(lo, hi)
            return
        }
        val n = (ff * 100f).roundToInt()
        val raw = lo + shape(n / 100f) * (hi - lo)
        value = (if (isInt) raw.roundToInt().toFloat() else quant(raw, effStep)).coerceIn(lo, hi)
    }

    fun setRange(newLo: Float, newHi: Float) {
        val nl = fixRange(newLo)
        val nh = fixRange(newHi)
        if (nh <= nl) return
        lo = nl; hi = nh
        value = value.coerceIn(lo, hi)
        snapNice()
    }

    fun resetRange() = setRange(min0, max0)
    fun resetValue() { value = def.coerceIn(lo, hi); snapNice() }

    /** Texte d'une valeur, avec autant de décimales que le pas le demande. */
    fun text(v: Float = value, step: Float = effStep): String {
        if (isInt) return String.format(Locale.ROOT, "%.0f", v)
        if (choices != null) {
            return if (v % 1f == 0f) String.format(Locale.ROOT, "%.0f", v) else String.format(Locale.ROOT, "%.1f", v)
        }
        return String.format(Locale.ROOT, "%.${decsFor(step)}f", v)
    }

    /** Texte affiché : durées lisibles pour les curseurs de temps, sinon la valeur avec ses décimales. */
    fun display(v: Float = value, step: Float = effStep): String = if (nice == 2) fmtDist(v) else if (nice == 1) fmtDur(v.roundToInt()) else when (kind) {
        1 -> if (step < 1f) text(v, step) + " s" else fmtDur(v.roundToInt())
        2 -> fmtDur((v * 60f).roundToInt(), "0 min")
        3 -> fmtMs(v.roundToInt())
        else -> text(v, step)
    }

    fun load(sp: SharedPreferences) {
        if (hasRange) {
            lo = fixRange(sp.getFloat("sl_${key}_lo", min0))
            hi = fixRange(sp.getFloat("sl_${key}_hi", max0))
            if (nice > 0) {
                val full = niceFull()
                lo = full[nearestIdx(full, lo)]
                hi = full[nearestIdx(full, hi)]
            }
            if (hi <= lo || (nice > 0 && (lo < min0 || hi > max0))) { lo = min0; hi = max0 }
        }
        val v = if (persistValue) sp.getFloat("sl_$key", def) else def
        value = if (choices != null) (choices.minByOrNull { abs(it - v) } ?: def)
        else (if (isInt) v.roundToInt().toFloat() else v).coerceIn(lo, hi)
        snapNice()
    }

    fun save(ed: SharedPreferences.Editor) {
        if (persistValue) ed.putFloat("sl_$key", value)
        if (hasRange) {
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
    var autoScroll by mutableStateOf(true)
        private set
    var runIcons by mutableStateOf(true)
        private set
    var help by mutableStateOf(false)
        private set
    var track by mutableStateOf(false)
        private set
    var trackVibe by mutableStateOf(true)
        private set
    /** Recalage des objectifs sur les tours (suivi avancé) : chaque tour décale les objectifs suivants, sans changer leurs durées. */
    var trackRecal by mutableStateOf(false)
        private set
    var showMarkers by mutableStateOf(true)
        private set
    // Départ random (v14) : clés « randStart », « randFreeze », « randMarks », « randReady », « randShot » (nouvelles)
    var randStart by mutableStateOf(false)
        private set
    /** Chrono figé pendant l'attente (marques / prêts / délai) : oui par défaut. */
    var randFreeze by mutableStateOf(true)
        private set
    var randMarksSel by mutableIntStateOf(1)
        private set
    var randReadySel by mutableIntStateOf(1)
        private set
    var randShotSel by mutableIntStateOf(1)
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
    val ecoBright = SliderState("ecoBright", S.ECO_BRIGHT, 0f, 100f, 0f, persistValue = false, isInt = true)
    val autoUnlock = SliderState("autoUnlock", S.AUTO_UNLOCK, 0f, 1440f, 0f, curve = 2, kind = 2, isInt = true)
    val chrono = SliderState("chrono", S.S_CHRONO, 10f, 90f, 38f, isInt = true)
    val btn = SliderState("btn", S.S_BTN, 20f, 140f, 58f, isInt = true)
    val lap = SliderState("lap", S.S_LAPS, 5f, 40f, 12f, isInt = true)
    val gapTimeBtn = SliderState("gapTimeBtn", S.GAP_TIME_BTN, -10f, 100f, 3f)
    val btnGap = SliderState("btnGap", S.GAP_BTN, 0f, 100f, 4f)
    val gapBtnLap = SliderState("gapBtnLap", S.GAP_BTN_LAP, 0f, 100f, 3f)
    val lineGap = SliderState("lineGap", S.GAP_LINES, 0f, 100f, 3f)
    val topPctS = SliderState("topPct", S.TOP_PCT, 0f, 100f, 60f)
    val lapWidth = SliderState("lapWidth", S.LAP_WIDTH, 40f, 100f, 90f)
    val hold = SliderState("hold", S.HOLD, 0.1f, 10f, 2f, stepProvider = { 0.1f }, kind = 1)
    val vibe = SliderState("vibe", S.VIBE, 0f, 100f, 100f, isInt = true)
    // v14 : arrêt auto en SECONDES, échelle liste log (0 = OFF .. 24 h). Clé NOUVELLE « autoStopS » (l'ancienne « autoStop » était en minutes).
    val autoStop = SliderState("autoStopS", S.AUTO_STOP, 0f, 86400f, 0f, kind = 1, isInt = true, nice = 1)
    val snake = SliderState("snake", S.SNAKE, 1f, 100f, 12.5f)
    // 2 s à 1 h, échelle quadratique (fin pour les petites valeurs) ; entier
    val aodDelay = SliderState("aodDelay", S.AOD_DELAY, 2f, 3600f, 10f, curve = 2, kind = 1, isInt = true)
    val colR = SliderState("colR", S.RED, 0f, 255f, 52f, isInt = true)
    val colG = SliderState("colG", S.GREEN, 0f, 255f, 211f, isInt = true)
    val colB = SliderState("colB", S.BLUE, 0f, 255f, 153f, isInt = true)
    // QR
    val qrVer = SliderState("qrVer", S.QR_VERSION, 5f, 25f, 15f, stepProvider = { 1f }, isInt = true)
    val qrEc = SliderState("qrEc", S.QR_EC, 0f, 3f, 1f, stepProvider = { 1f }, isInt = true)
    // Suivi avancé
    // Deux précisions en haut du suivi avancé : l'une règle le pas de tous les curseurs de distance, l'autre de tous ceux de temps
    // v14 : tous en échelle liste log (valeurs rondes, 0 = 1er cran). Précision 0 = « aucune contrainte » (toutes les valeurs rondes).
    val precisionDist = SliderState(
        "precisionDist", S.TRACK_PRECISION, 0f, 100000f, 1f, nice = 2, noRange = true
    )
    val precisionTime = SliderState(
        "precisionTime", S.TRACK_PRECISION_TIME, 0f, 86400f, 1f, kind = 1, isInt = true, nice = 1, noRange = true
    )
    val distance = SliderState("distance", S.TRACK_DIST, 0f, 100000f, 0f, stepProvider = { precisionDist.value }, nice = 2)
    val exTime = SliderState("exTime", S.EX_TIME, 0f, 86400f, 0f, stepProvider = { precisionTime.value }, kind = 1, isInt = true, nice = 1)
    val nbRep = SliderState("nbRep", S.EX_REPS, 1f, 100f, 1f, stepProvider = { 1f }, isInt = true)
    val restTime = SliderState("restTime", S.REST_TIME, 0f, 86400f, 0f, stepProvider = { precisionTime.value }, kind = 1, isInt = true, nice = 1)

    // Départ random : attente min / max (0..60 s, entiers, min <= max garanti à chaque réglage), volume 0..100 %. Pas de sous-menu min/max.
    val randMin: SliderState = SliderState(
        "randMin", S.RAND_MIN, 0f, 60f, 2f, stepProvider = { 1f }, kind = 1, isInt = true, noRange = true
    )
    val randMax: SliderState = SliderState(
        "randMax", S.RAND_MAX, 0f, 60f, 5f, stepProvider = { 1f }, kind = 1, isInt = true, noRange = true
    )
    init {
        randMin.clampTo = { v: Float -> minOf(v, randMax.value) }
        randMax.clampTo = { v: Float -> maxOf(v, randMin.value) }
    }
    val randVol = SliderState("randVol", S.RAND_VOL, 0f, 100f, 100f, stepProvider = { 1f }, isInt = true, noRange = true)
    /** Compensation de la latence du haut-parleur (ms) : le chrono ET la vibration du départ suivent le start() du son de ce délai (0..5000 ms, pas de 5 ; plage réglable par appui long). */
    val randLat = SliderState("randLat", S.RAND_LAT, 0f, 5000f, 0f, stepProvider = { 5f }, kind = 3, isInt = true)

    val sliders: List<SliderState> = listOf(
        ecoBright, autoUnlock, chrono, btn, lap, gapTimeBtn, btnGap, gapBtnLap, lineGap,
        topPctS, lapWidth, hold, vibe, autoStop, snake, aodDelay, colR, colG, colB,
        qrVer, qrEc, precisionDist, precisionTime, distance, exTime, nbRep, restTime,
        randMin, randMax, randVol, randLat
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
    /** Arrêt auto en SECONDES (0 = désactivé), 0..86400. Slider Settings.autoStop, clé de persistance « sl_autoStopS ». */
    val autoStopSec: Int get() = autoStop.value.roundToInt().coerceIn(0, 86400)
    val aodDelayMs: Long get() = (aodDelay.value * 1000f).roundToInt().toLong()
    /**
     * Longueur d'une série pour les triangles ▲▼ : en suivi avancé (objectif d'exercice réglé), nbRep tours ; avec le recalage,
     * un tour par segment (chaque exercice + le repos) ; sinon 1 (tour précédent).
     */
    val seriesLen: Int
        get() = if (track && Segments.enabled()) {
            if (trackRecal && !randStart) Segments.perCycle() else nbRep.value.roundToInt().coerceAtLeast(1)
        } else 1
    val qrVersion: Int get() = qrVer.value.roundToInt().coerceIn(5, 25)
    val qrLevel: Int get() = qrEc.value.roundToInt().coerceIn(0, 3)
    val distanceM: Float get() = distance.value
    /** Attente du départ random en secondes (min <= max garanti, même si les valeurs persistées étaient incohérentes). */
    val randMinSec: Int get() = minOf(randMin.value.roundToInt(), randMax.value.roundToInt()).coerceIn(0, 60)
    val randMaxSec: Int get() = maxOf(randMin.value.roundToInt(), randMax.value.roundToInt()).coerceIn(0, 60)
    val randVolPct: Int get() = randVol.value.roundToInt().coerceIn(0, 100)
    val randLatMs: Int get() = randLat.value.roundToInt().coerceIn(0, 5000)
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
        autoScroll = s.getBoolean("autoScroll", true)
        runIcons = s.getBoolean("runIcons", true)
        help = s.getBoolean("help", false)
        track = s.getBoolean("track", false)
        trackVibe = s.getBoolean("trackVibe", true)
        trackRecal = s.getBoolean("trackRecal", false)
        showMarkers = s.getBoolean("markers", true)
        randStart = s.getBoolean("randStart", false)
        randFreeze = s.getBoolean("randFreeze", true)
        randMarksSel = s.getInt("randMarks", 1).coerceIn(1, 3)
        randReadySel = s.getInt("randReady", 1).coerceIn(1, 3)
        randShotSel = s.getInt("randShot", 1).coerceIn(1, 5)
        lang = when (s.getString("lang", "fr")) { "en" -> Lang.EN; "zh" -> Lang.ZH; else -> Lang.FR }
        logo = s.getInt("logo", 0).coerceIn(0, Logos.count - 1)
        colNum = s.getBoolean("col0", true)
        colTotal = s.getBoolean("col1", true)
        colLap = s.getBoolean("col2", true)
        colDelta = s.getBoolean("col3", true)
        colPace = s.getBoolean("col4", false)
        showAvg = s.getBoolean("showAvg", true)
        sliders.forEach { it.load(s) }
        if (randMin.value > randMax.value) randMin.value = randMax.value
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
    fun setAutoScroll(c: Context, v: Boolean) { autoScroll = v; p(c).edit().putBoolean("autoScroll", v).apply() }
    fun setRunIcons(c: Context, v: Boolean) { runIcons = v; p(c).edit().putBoolean("runIcons", v).apply() }
    fun setHelp(c: Context, v: Boolean) { help = v; p(c).edit().putBoolean("help", v).apply() }
    fun setTrack(c: Context, v: Boolean) {
        track = v
        p(c).edit().putBoolean("track", v).apply()
        if (v) Segments.schedule(c) else Segments.cancel(c)
    }
    fun setTrackVibe(c: Context, v: Boolean) {
        trackVibe = v
        p(c).edit().putBoolean("trackVibe", v).apply()
        Segments.schedule(c)          // les alarmes de frontière restent utiles au départ random même sans vibrations
    }
    /** Départ random ON / OFF (Segments.schedule : les alarmes de frontière servent aussi au départ random). */
    fun updateRandStart(c: Context, v: Boolean) {
        randStart = v
        p(c).edit().putBoolean("randStart", v).apply()
        if (!v) RandomStart.cancel(c, true)
        Segments.schedule(c)
    }
    fun updateRandFreeze(c: Context, v: Boolean) { randFreeze = v; p(c).edit().putBoolean("randFreeze", v).apply() }
    /** Choix d'un son du départ : kind 0 = « À vos marques » (1..3), 1 = « Prêts » (1..3), 2 = coup de feu (1..5). */
    fun updateRandSound(c: Context, kind: Int, n: Int) {
        when (kind) {
            0 -> { randMarksSel = n.coerceIn(1, 3); p(c).edit().putInt("randMarks", randMarksSel).apply() }
            1 -> { randReadySel = n.coerceIn(1, 3); p(c).edit().putInt("randReady", randReadySel).apply() }
            else -> { randShotSel = n.coerceIn(1, 5); p(c).edit().putInt("randShot", randShotSel).apply() }
        }
    }
    fun setTrackRecal(c: Context, v: Boolean) {
        trackRecal = v
        p(c).edit().putBoolean("trackRecal", v).apply()
        Segments.schedule(c)          // les objectifs à venir sont recalculés tout de suite
    }
    fun setShowMarkers(c: Context, v: Boolean) { showMarkers = v; p(c).edit().putBoolean("markers", v).apply() }
    fun setLang(c: Context, l: Lang) {
        lang = l
        p(c).edit().putString("lang", when (l) { Lang.EN -> "en"; Lang.ZH -> "zh"; else -> "fr" }).apply()
    }
    /** Change le logo : icône de l'appli (alias de lancement), tile, complication et notification. */
    fun setLogo(c: Context, i: Int) {
        logo = i.coerceIn(0, Logos.count - 1)
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
        sliders.forEach { it.snapNice() }       // une précision modifiée recale distance / temps d'exercice / repos
        val ed = p(c).edit()
        sliders.forEach { it.save(ed) }
        ed.apply()
        // Chrono en marche : un réglage d'arrêt auto / d'objectif modifié s'applique tout de suite (alarmes replanifiées)
        try { Stopwatch.rescheduleAutoStop(c) } catch (e: Exception) { }
        try { if (Stopwatch.running) Segments.schedule(c) } catch (e: Exception) { }
    }

    /** Écrit les curseurs SANS replanifier (arrêt auto / objectifs) : pour les réglages qui n'en dépendent pas (départ random). */
    fun persistQuiet(c: Context) {
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
