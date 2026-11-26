package com.example.chrono

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Durée lisible à la milliseconde : « 250 ms », « 1,5 s », « 1 min 30 s », « 2 h 05 min » (virgule en français, point sinon). */
fun fmtTime(ms: Long): String {
    val v = ms.coerceAtLeast(0L)
    if (v < 1000L) return "$v ms"
    val h = v / 3_600_000L
    val m = (v % 3_600_000L) / 60_000L
    val rest = v % 60_000L                      // millisecondes dans la minute
    val sec = rest / 1000L
    val frac = rest % 1000L
    val tail = if (frac == 0L) "" else
        (if (Settings.lang == Lang.FR) "," else ".") + String.format(Locale.ROOT, "%03d", frac).trimEnd('0')
    val padded = String.format(Locale.ROOT, "%02d", sec) + tail + " s"
    return when {
        v < 60_000L -> "$sec$tail s"
        v < 3_600_000L -> if (rest == 0L) "$m min" else "$m min $padded"
        rest != 0L -> "$h h " + String.format(Locale.ROOT, "%02d", m) + " min $padded"
        m != 0L -> "$h h " + String.format(Locale.ROOT, "%02d", m) + " min"
        else -> "$h h"
    }
}

/** Distance jolie : « 400 m », « 2,5 m », « 1,5 km », « 12 km » (virgule en français, point sinon). */
fun fmtDist(m: Double): String {
    fun trim(x: Double, decs: Int): String {
        var s = String.format(Locale.ROOT, "%.${decs}f", x)
        if (s.contains('.')) s = s.trimEnd('0').trimEnd('.')
        return if (Settings.lang == Lang.FR) s.replace('.', ',') else s
    }
    return if (m < 1000.0) trim(m, 2) + " m" else trim(m / 1000.0, 3) + " km"
}

/** Nombre de décimales utiles pour un pas donné : 0 (pas >= 1), 1 (pas >= 0,1) ou 2. */
private fun decsFor(step: Double): Int = if (step >= 0.9999) 0 else if (step >= 0.09999) 1 else 2

/** Arrondit v au nombre de décimales imposé par le pas (la valeur réelle correspond ainsi toujours à ce qui est affiché). */
private fun quant(v: Double, step: Double): Double {
    val m = when (decsFor(step)) { 0 -> 1.0; 1 -> 10.0; else -> 100.0 }
    return (v * m).roundToLong() / m
}

/**
 * LINEAR : curseur ordinaire (couleurs, tailles, répétitions...). TIME : temps en MILLISECONDES, échelle log (réglable) ou linéaire.
 * TIME_LIST / DIST_LIST : liste de valeurs rondes (temps en ms / distance en m), multiples de la précision.
 */
enum class SliderType { LINEAR, TIME, TIME_LIST, DIST_LIST }

private const val EPS = 1e-4

/**
 * Un curseur = une liste croissante de CRANS (ticks()) entre lo et hi (plage de travail, réglable par appui long).
 * Glisser : fraction -> index ; boutons − / + : cran précédent / suivant ; même logique pour les bornes min / max (boundTicks()).
 * min0..max0 = bornes d'origine. Les valeurs sont des Double (les ms jusqu'à 24 h restent exactes) ; les temps sont persistés en Long
 * (clés « ms_<key> », « _lo », « _hi », « _log »), les autres curseurs en Float (clés « sl_<key> »).
 * stepProvider : pas imposé (répétitions, volume... ; précision pour les listes).
 * isInt : curseur ENTIER (valeur, min et max du sous-menu).
 */
class SliderState(
    val key: String, val label: S, val min0: Double, private val max0Fixed: Double, val def: Double,
    val type: SliderType = SliderType.LINEAR,
    val persistValue: Boolean = true,
    val stepProvider: (() -> Double)? = null,
    val isInt: Boolean = false,
    /** Pas de sous-menu min/max (appui long = explication seule). */
    val noRange: Boolean = false,
    /** La valeur 0 signifie « désactivé » (affichée ainsi). */
    val offZero: Boolean = false,
    /** Borne d'origine haute calculée (ex. durée de la plus longue piste de coup de feu). */
    private val maxProvider: (() -> Double)? = null,
    /** Migration v14 : ancienne clé « sl_<oldKey> » et facteur vers les ms. */
    private val oldKey: String? = null,
    private val oldFactor: Double = 1.0
) {
    val max0: Double get() = maxProvider?.invoke() ?: max0Fixed

    /** Contrainte appliquée après chaque réglage au curseur (ex. min <= max du départ random). Affectée après la création. */
    var clampTo: ((Double) -> Double)? = null
    /** Appelée après un changement de plage ou une valeur imposée (pour pousser un curseur lié). */
    var afterRange: (() -> Unit)? = null

    var value by mutableDoubleStateOf(def)
    var lo by mutableDoubleStateOf(min0)
    var hi by mutableDoubleStateOf(max0Fixed)
    /** Échelle log (curseurs TIME uniquement), activée par défaut. */
    var log by mutableStateOf(true)

    val hasRange: Boolean get() = !noRange
    private val isTime: Boolean get() = type == SliderType.TIME || type == SliderType.TIME_LIST
    private val isList: Boolean get() = type == SliderType.TIME_LIST || type == SliderType.DIST_LIST

    // ------------------------------------------------------------ crans

    private val memo = HashMap<String, List<Double>>()

    /** Crans entre a et b (jamais vide, croissants). */
    private fun ticksFor(a: Double, b: Double): List<Double> {
        val step = stepProvider?.invoke() ?: 0.0
        val logNow = log
        val k = "$a|$b|$step|$logNow"
        val known = memo[k]
        if (known != null) return known
        val out: List<Double> = when (type) {
            SliderType.LINEAR -> linearTicks(a, b, step)
            SliderType.TIME -> Ticks.time(a.roundToLong(), b.roundToLong(), logNow).map { it.toDouble() }
            SliderType.TIME_LIST -> Ticks.thin(fullList(a, b), Ticks.TIME_CORE)
            SliderType.DIST_LIST -> Ticks.thin(fullList(a, b), Ticks.DIST_CORE)
        }
        if (memo.size > 12) memo.clear()
        memo[k] = out
        return out
    }

    /** Toutes les valeurs rondes de la plage (multiples de la précision), sans allègement. */
    private fun fullList(a: Double, b: Double): List<Double> {
        val step = stepProvider?.invoke() ?: 0.0
        val master = if (type == SliderType.DIST_LIST) Ticks.DIST_LIST else Ticks.TIME_LIST
        val f = Ticks.filtered(master, a, b, step)
        return if (f.size >= 2) f else Ticks.multiples(a, b, step)
    }

    private fun linearTicks(a: Double, b: Double, step: Double): List<Double> {
        if (b <= a) return listOf(a)
        val out = ArrayList<Double>()
        if (step > 0.0) {                       // pas imposé : tous les multiples du pas
            out.add(a)
            var k = ceil(a / step - 1e-6).toLong()
            while (k * step <= b + 1e-6) { out.add((k * step).coerceIn(a, b)); k++ }
            out.add(b)
        } else {                                // 100 pas, arrondis (entier ou décimales du pas)
            val s = if (isInt) maxOf(1.0, ((b - a) / 100.0).roundToLong().toDouble()) else (b - a) / 100.0
            for (n in 0..100) {
                val raw = a + n / 100.0 * (b - a)
                val v = if (isInt) raw.roundToLong().toDouble() else quant(raw, s)
                out.add(v.coerceIn(a, b))
            }
        }
        return out.sorted().distinct()
    }

    fun ticks(): List<Double> = ticksFor(lo, hi)

    private fun nearestIdx(l: List<Double>, v: Double): Int {
        var best = 0
        for (i in l.indices) if (abs(l[i] - v) < abs(l[best] - v)) best = i
        return best
    }

    private fun neighbour(l: List<Double>, v: Double, dir: Int): Double? =
        if (dir > 0) l.firstOrNull { it > v + EPS } else l.lastOrNull { it < v - EPS }

    private fun fractionIn(l: List<Double>, v: Double): Float =
        if (l.size > 1) nearestIdx(l, v) / (l.size - 1f) else 0f

    fun valueAt(l: List<Double>, f: Float): Double = l[(f.coerceIn(0f, 1f) * (l.size - 1)).roundToInt().coerceIn(0, l.size - 1)]

    fun fraction(): Float = fractionIn(ticks(), value)

    private fun choose(v: Double) {
        val n = clampTo?.invoke(v) ?: v
        if (n != value) value = n
    }

    fun setFromFraction(f: Float) { choose(valueAt(ticks(), f)) }

    /** Bouton − (dir < 0) ou + (dir > 0) : exactement un cran. */
    fun step(dir: Int) {
        val n = neighbour(ticks(), value, dir)
        if (n != null) choose(n)
    }

    // ------------------------------------------------------------ plage min / max

    /** Plus petit écart entre lo et hi d'un curseur LINEAR : 1 % de la plage d'origine (au moins 1 pour un curseur entier). */
    val gap0: Double get() {
        val g = (max0 - min0) / 100.0
        return if (isInt) maxOf(1.0, g.roundToLong().toDouble()) else g
    }

    /** Pas effectif d'un curseur LINEAR (décimales affichées). */
    val effStep: Double get() {
        val s = stepProvider?.invoke()?.coerceAtLeast(1e-4) ?: ((hi - lo) / 100.0)
        return if (isInt) maxOf(1.0, s.roundToLong().toDouble()) else s
    }

    private fun fixRange(v: Double): Double = when {
        isList -> v
        type == SliderType.TIME || isInt -> v.roundToLong().toDouble()
        else -> quant(v, gap0)
    }

    /**
     * Crans des curseurs min / max. Temps et listes : crans de toute la plage d'origine PLUS crans de la plage de travail actuelle ;
     * en resserrant min / max on zoome ainsi jusqu'à chaque milliseconde.
     */
    fun boundTicks(): List<Double> = if (type == SliderType.LINEAR) {
        (0..100).map { fixRange(min0 + it / 100.0 * (max0 - min0)) }.distinct()
    } else {
        (ticksFor(min0, max0) + ticksFor(lo, hi)).sorted().distinct()
    }

    fun boundFraction(l: List<Double>, isMin: Boolean): Float = fractionIn(l, if (isMin) lo else hi)

    /** Nouvelle borne min ou max (min < max garanti). */
    fun setBound(isMin: Boolean, v: Double, l: List<Double>) {
        if (isMin) {
            val limit: Double = when (type) {
                SliderType.LINEAR -> hi - gap0
                SliderType.TIME -> hi - 1.0
                SliderType.TIME_LIST, SliderType.DIST_LIST -> l.lastOrNull { it < hi - EPS } ?: lo
            }
            setRange(minOf(v, limit).coerceAtLeast(min0), hi)
        } else {
            val limit: Double = when (type) {
                SliderType.LINEAR -> lo + gap0
                SliderType.TIME -> lo + 1.0
                SliderType.TIME_LIST, SliderType.DIST_LIST -> l.firstOrNull { it > lo + EPS } ?: hi
            }
            setRange(lo, maxOf(v, limit).coerceAtMost(max0))
        }
    }

    fun stepBound(isMin: Boolean, dir: Int) {
        val l = boundTicks()
        val n = neighbour(l, if (isMin) lo else hi, dir)
        if (n != null) setBound(isMin, n, l)
    }

    fun setRange(newLo: Double, newHi: Double) {
        val nl = fixRange(newLo)
        val nh = fixRange(newHi)
        if (nh <= nl) return
        lo = nl; hi = nh
        value = value.coerceIn(nl, nh)
        snap()
        afterRange?.invoke()
    }

    fun resetRange() { setRange(min0, max0) }
    fun resetValue() { value = def.coerceIn(lo, hi); snap() }

    /** Impose une valeur (ex. calibrage) : les bornes lo / hi sont élargies si nécessaire. Penser ensuite à Settings.persistSliders / persistQuiet. */
    fun forceValue(v: Double) {
        val n = v.coerceIn(min0, max0)
        if (n < lo) lo = n
        if (n > hi) hi = n
        value = n
        afterRange?.invoke()
    }

    /** Listes seulement : recale la valeur (après un changement de précision, de plage ou une relecture). */
    fun snap() {
        if (!isList) return
        val l = fullList(lo, hi)
        val n = l[nearestIdx(l, value)]
        if (n != value) value = n
    }

    // ------------------------------------------------------------ affichage

    /** Texte d'une valeur d'un curseur LINEAR, avec autant de décimales que le pas le demande. */
    fun text(v: Double = value, step: Double = effStep): String =
        String.format(Locale.ROOT, "%.${if (isInt) 0 else decsFor(step)}f", v)

    fun display(v: Double = value, step: Double = effStep): String = when {
        offZero && v < 0.5 -> S.OFF.t()
        isTime -> fmtTime(v.roundToLong())
        type == SliderType.DIST_LIST -> fmtDist(v)
        else -> text(v, step)
    }

    // ------------------------------------------------------------ persistance

    fun load(sp: SharedPreferences) {
        val top = max0
        var a = min0
        var b = top
        if (isTime) {
            log = sp.getBoolean("ms_${key}_log", true)
            if (hasRange) {
                if (sp.contains("ms_${key}_lo")) a = sp.getLong("ms_${key}_lo", 0L).toDouble()
                if (sp.contains("ms_${key}_hi")) b = sp.getLong("ms_${key}_hi", 0L).toDouble()
            }
        } else if (hasRange) {
            a = fixRange(sp.getFloat("sl_${key}_lo", min0.toFloat()).toDouble())
            b = fixRange(sp.getFloat("sl_${key}_hi", top.toFloat()).toDouble())
            if (type == SliderType.DIST_LIST && a < 1.0) a = min0
            if (type == SliderType.DIST_LIST && b < 1.0) b = top               // ancienne borne haute sous 1 m
        }
        a = a.coerceIn(min0, top)
        b = b.coerceIn(min0, top)
        if (b <= a) { a = min0; b = top }
        lo = a; hi = b

        var v = def
        if (persistValue) {
            if (isTime) {
                val old = oldKey
                if (sp.contains("ms_$key")) {
                    v = sp.getLong("ms_$key", 0L).toDouble()
                } else if (old != null && sp.contains("sl_$old")) {
                    // anciennes valeurs v14 (s, min ou ms selon le curseur) ; les anciennes bornes ne sont pas reprises
                    v = (sp.getFloat("sl_$old", 0f).toDouble() * oldFactor).roundToLong().toDouble()
                }
            } else {
                v = sp.getFloat("sl_$key", def.toFloat()).toDouble()
                if (type == SliderType.DIST_LIST && v > 0.0 && v < 1.0) v = 1.0     // 0,1 à 0,75 m (v14) n'existent plus
            }
        }
        if (isInt) v = v.roundToLong().toDouble()
        value = v.coerceIn(a, b)
        snap()
    }

    fun save(ed: SharedPreferences.Editor) {
        if (isTime) {
            if (persistValue) ed.putLong("ms_$key", value.roundToLong())
            if (type == SliderType.TIME) ed.putBoolean("ms_${key}_log", log)
            if (hasRange) {
                // borne non modifiée = non écrite : elle suit la borne d'origine (qui peut être calculée)
                if (lo > min0) ed.putLong("ms_${key}_lo", lo.roundToLong()) else ed.remove("ms_${key}_lo")
                if (hi < max0) ed.putLong("ms_${key}_hi", hi.roundToLong()) else ed.remove("ms_${key}_hi")
            }
        } else {
            if (persistValue) ed.putFloat("sl_$key", value.toFloat())
            if (hasRange) {
                ed.putFloat("sl_${key}_lo", lo.toFloat())
                ed.putFloat("sl_${key}_hi", hi.toFloat())
            }
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

    // ---- Curseurs (valeur + plage lo..hi ; voir SliderState). Les temps sont en MILLISECONDES (v15).
    private const val DAY_MS = 86_400_000.0

    val ecoBright = SliderState("ecoBright", S.ECO_BRIGHT, 0.0, 100.0, 0.0, persistValue = false, isInt = true)
    val autoUnlock = SliderState(
        "autoUnlock", S.AUTO_UNLOCK, 0.0, DAY_MS, 0.0, SliderType.TIME, offZero = true,
        oldKey = "autoUnlock", oldFactor = 60_000.0          // v14 : minutes
    )
    val chrono = SliderState("chrono", S.S_CHRONO, 10.0, 90.0, 38.0, isInt = true)
    val btn = SliderState("btn", S.S_BTN, 20.0, 140.0, 58.0, isInt = true)
    val lap = SliderState("lap", S.S_LAPS, 5.0, 40.0, 12.0, isInt = true)
    val gapTimeBtn = SliderState("gapTimeBtn", S.GAP_TIME_BTN, -10.0, 100.0, 3.0)
    val btnGap = SliderState("btnGap", S.GAP_BTN, 0.0, 100.0, 4.0)
    val gapBtnLap = SliderState("gapBtnLap", S.GAP_BTN_LAP, 0.0, 100.0, 3.0)
    val lineGap = SliderState("lineGap", S.GAP_LINES, 0.0, 100.0, 3.0)
    val topPctS = SliderState("topPct", S.TOP_PCT, 0.0, 100.0, 60.0)
    val lapWidth = SliderState("lapWidth", S.LAP_WIDTH, 40.0, 100.0, 90.0)
    // Durée d'appui long : 100 ms (minimum : 0 impossible) à 24 h
    val hold = SliderState("hold", S.HOLD, 100.0, DAY_MS, 2000.0, SliderType.TIME, oldKey = "hold", oldFactor = 1000.0)
    val vibe = SliderState("vibe", S.VIBE, 0.0, 100.0, 100.0, isInt = true)
    // Arrêt auto : 0 (OFF) à 24 h
    val autoStop = SliderState(
        "autoStopS", S.AUTO_STOP, 0.0, DAY_MS, 0.0, SliderType.TIME, offZero = true, oldKey = "autoStopS", oldFactor = 1000.0
    )
    val snake = SliderState("snake", S.SNAKE, 1.0, 100.0, 12.5)
    // Délai avant veille : 1 s à 24 h
    val aodDelay = SliderState(
        "aodDelay", S.AOD_DELAY, 1000.0, DAY_MS, 10_000.0, SliderType.TIME, oldKey = "aodDelay", oldFactor = 1000.0
    )
    val colR = SliderState("colR", S.RED, 0.0, 255.0, 52.0, isInt = true)
    val colG = SliderState("colG", S.GREEN, 0.0, 255.0, 211.0, isInt = true)
    val colB = SliderState("colB", S.BLUE, 0.0, 255.0, 153.0, isInt = true)
    // QR
    val qrVer = SliderState("qrVer", S.QR_VERSION, 5.0, 25.0, 15.0, stepProvider = { 1.0 }, isInt = true)
    val qrEc = SliderState("qrEc", S.QR_EC, 0.0, 3.0, 1.0, stepProvider = { 1.0 }, isInt = true)
    // Suivi avancé
    // Deux précisions en haut du suivi avancé : l'une règle le pas de tous les curseurs de distance, l'autre de tous ceux de temps
    // Listes de valeurs rondes. Précision distance : 1 m à 100 km ; précision temps : 1 ms à 24 h (jamais 0).
    val precisionDist: SliderState = SliderState(
        "precisionDist", S.TRACK_PRECISION, 1.0, 100000.0, 1.0, SliderType.DIST_LIST, noRange = true
    )
    val precisionTime: SliderState = SliderState(
        "precisionTime", S.TRACK_PRECISION_TIME, 1.0, DAY_MS, 1000.0, SliderType.TIME_LIST, noRange = true,
        oldKey = "precisionTime", oldFactor = 1000.0
    )
    val distance: SliderState = SliderState(
        "distance", S.TRACK_DIST, 0.0, 100000.0, 0.0, SliderType.DIST_LIST, stepProvider = { precisionDist.value }, offZero = true
    )
    val exTime: SliderState = SliderState(
        "exTime", S.EX_TIME, 0.0, DAY_MS, 0.0, SliderType.TIME_LIST, stepProvider = { precisionTime.value }, offZero = true,
        oldKey = "exTime", oldFactor = 1000.0
    )
    val nbRep = SliderState("nbRep", S.EX_REPS, 1.0, 100.0, 1.0, stepProvider = { 1.0 }, isInt = true)
    val restTime: SliderState = SliderState(
        "restTime", S.REST_TIME, 0.0, DAY_MS, 0.0, SliderType.TIME_LIST, stepProvider = { precisionTime.value }, offZero = true,
        oldKey = "restTime", oldFactor = 1000.0
    )

    // Départ random : attente min / max (0 à 1 min, min <= max garanti), volume 0..100 %.
    val randMin: SliderState = SliderState(
        "randMin", S.RAND_MIN, 0.0, 60_000.0, 2000.0, SliderType.TIME, oldKey = "randMin", oldFactor = 1000.0
    )
    val randMax: SliderState = SliderState(
        "randMax", S.RAND_MAX, 0.0, 60_000.0, 5000.0, SliderType.TIME, oldKey = "randMax", oldFactor = 1000.0
    )
    init {
        randMin.clampTo = { v: Double -> minOf(v, randMax.value) }
        randMax.clampTo = { v: Double -> maxOf(v, randMin.value) }
        // Après un changement de plage : le curseur modifié pousse l'autre
        randMin.afterRange = { if (randMax.value < randMin.value) randMax.forceValue(randMin.value) }
        randMax.afterRange = { if (randMin.value > randMax.value) randMin.forceValue(randMax.value) }
    }
    val randVol = SliderState("randVol", S.RAND_VOL, 0.0, 100.0, 100.0, stepProvider = { 1.0 }, isInt = true, noRange = true)

    // RÈGLE : le maximum de la compensation audio ET du temps de réaction de référence du calibrage = la DURÉE DE LA PLUS LONGUE PISTE
    // AUDIO DE COUP DE FEU, mesurée sur les ressources (Sounds.maxShotMs ; repli 1500 ms). Rien à retoucher quand on ajoute une piste.
    /** Compensation de la latence du haut-parleur : le chrono ET la vibration du départ suivent le start() du son de ce délai. */
    val randLat = SliderState(
        "randLat", S.RAND_LAT, 0.0, Sounds.FALLBACK_SHOT_MS.toDouble(), 0.0, SliderType.TIME,
        maxProvider = { Sounds.shotMaxMs.toDouble() }, oldKey = "randLat"
    )
    /** Temps de réaction de référence du calibrage de la compensation audio (mesuré par l'utilisateur sur ordinateur). */
    val calibRef = SliderState(
        "calibRef", S.CALIB_REF, 0.0, Sounds.FALLBACK_SHOT_MS.toDouble(), 200.0, SliderType.TIME,
        maxProvider = { Sounds.shotMaxMs.toDouble() }
    )
    /** Nombre de coups de feu d'un calibrage (1 à 20). */
    val calibN = SliderState("calibN", S.CALIB_N, 1.0, 20.0, 5.0, stepProvider = { 1.0 }, isInt = true, noRange = true)
    /** Calibrage : true = médiane des réactions (défaut), false = moyenne. Clé « calibMedian ». */
    var calibMedian by mutableStateOf(true)
        private set

    val sliders: List<SliderState> = listOf(
        ecoBright, autoUnlock, chrono, btn, lap, gapTimeBtn, btnGap, gapBtnLap, lineGap,
        topPctS, lapWidth, hold, vibe, autoStop, snake, aodDelay, colR, colG, colB,
        qrVer, qrEc, precisionDist, precisionTime, distance, exTime, nbRep, restTime,
        randMin, randMax, randVol, randLat, calibRef, calibN
    )
    val byKey: Map<String, SliderState> = sliders.associateBy { it.key }

    // ---- Accès pratiques (lisent l'état : toujours dynamiques)
    val chronoSp: Float get() = chrono.value.toFloat()
    val btnDp: Float get() = btn.value.toFloat()
    val textSp: Float get() = lap.value.toFloat()
    val gapTimeBtnPct: Float get() = gapTimeBtn.value.toFloat()
    val btnGapPct: Float get() = btnGap.value.toFloat()
    val gapBtnLapPct: Float get() = gapBtnLap.value.toFloat()
    val lineGapPct: Float get() = lineGap.value.toFloat()
    val topPct: Float get() = topPctS.value.toFloat()
    val lapWidthPct: Float get() = lapWidth.value.toFloat()
    val vibePct: Float get() = vibe.value.toFloat()
    val snakePct: Float get() = snake.value.toFloat()
    val ecoBrightness: Float get() = ecoBright.value.toFloat()
    val holdMs: Long get() = hold.value.roundToLong().coerceAtLeast(100L)
    /** Déverrouillage automatique en ms (0 = jamais). */
    val autoUnlockMs: Long get() = autoUnlock.value.roundToLong().coerceIn(0L, 86_400_000L)
    /** Arrêt auto en ms (0 = désactivé). */
    val autoStopMs: Long get() = autoStop.value.roundToLong().coerceIn(0L, 86_400_000L)
    val aodDelayMs: Long get() = aodDelay.value.roundToLong().coerceAtLeast(1000L)
    val exTimeMs: Long get() = exTime.value.roundToLong().coerceAtLeast(0L)
    val restTimeMs: Long get() = restTime.value.roundToLong().coerceAtLeast(0L)
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
    val distanceM: Float get() = distance.value.toFloat()
    /** Attente du départ random en ms (min <= max garanti, même si les valeurs persistées étaient incohérentes). */
    val randMinMs: Long get() = minOf(randMin.value, randMax.value).roundToLong().coerceIn(0L, 60_000L)
    val randMaxMs: Long get() = maxOf(randMin.value, randMax.value).roundToLong().coerceIn(0L, 60_000L)
    val randVolPct: Int get() = randVol.value.roundToInt().coerceIn(0, 100)
    val randLatMs: Long get() = randLat.value.roundToLong().coerceIn(0L, Sounds.shotMaxMs)
    val calibRefMs: Long get() = calibRef.value.roundToLong().coerceIn(0L, Sounds.shotMaxMs)
    val rgb: Int
        get() = ((colR.value.roundToInt() and 255) shl 16) or
            ((colG.value.roundToInt() and 255) shl 8) or
            (colB.value.roundToInt() and 255)

    val ringActive: Boolean get() = ring && !eco
    val aodActive: Boolean get() = aod && !eco
    /** La colonne Allure n'existe que si le suivi avancé est actif et qu'une distance est réglée. */
    val paceActive: Boolean get() = colPace && track && distance.value > 0.0

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
        randShotSel = s.getInt("randShot", 1).coerceIn(1, Sounds.shotCount)
        calibMedian = s.getBoolean("calibMedian", true)
        lang= when (s.getString("lang", "fr")) { "en" -> Lang.EN; "zh" -> Lang.ZH; else -> Lang.FR }
        logo = s.getInt("logo", 0).coerceIn(0, Logos.count - 1)
        colNum = s.getBoolean("col0", true)
        colTotal = s.getBoolean("col1", true)
        colLap = s.getBoolean("col2", true)
        colDelta = s.getBoolean("col3", true)
        colPace = s.getBoolean("col4", false)
        showAvg = s.getBoolean("showAvg", true)
        try { Sounds.maxShotMs(c) } catch (e: Exception) { }     // borne haute des curseurs audio : AVANT leur lecture
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
    /** Choix d'un son du départ : kind 0 = « À vos marques » (1..3), 1 = « Prêts » (1..3), 2 = coup de feu (1..nombre de pistes). */
    fun updateRandSound(c: Context, kind: Int, n: Int) {
        when (kind) {
            0 -> { randMarksSel = n.coerceIn(1, 3); p(c).edit().putInt("randMarks", randMarksSel).apply() }
            1 -> { randReadySel = n.coerceIn(1, 3); p(c).edit().putInt("randReady", randReadySel).apply() }
            else -> { randShotSel = n.coerceIn(1, Sounds.shotCount); p(c).edit().putInt("randShot", randShotSel).apply() }
        }
    }
    fun setCalibMedian(c: Context, v: Boolean) { calibMedian = v; p(c).edit().putBoolean("calibMedian", v).apply() }
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
        sliders.forEach { it.snap() }       // une précision modifiée recale distance / temps d'exercice / repos
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
