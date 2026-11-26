package com.example.chrono

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToLong

/**
 * CRANS DES CURSEURS (v15). Un curseur n'est qu'une liste croissante de valeurs (« crans ») : glisser = fraction -> index,
 * − / + = index − 1 / index + 1. Ce fichier ne contient que le calcul de ces listes (aucun état, aucune dépendance Android).
 *
 * TEMPS (millisecondes entières, échelle log ou linéaire) : time(lo, hi, log).
 *   - plage étroite (moins de MAX valeurs) : CHAQUE milliseconde ;
 *   - log : environ 40 valeurs jolies espacées en log ; lo = 0 : le 1er cran vaut 0 (forcé), puis le log commence à 1 ms ;
 *   - linéaire : PAS CONSTANT « joli » (le plus petit qui donne au plus MAX crans), multiples du pas entre lo et hi ;
 *   - lo et hi sont toujours des crans ; la liste n'est jamais vide.
 * LISTES (précisions, distance, exercice, repos) : valeurs rondes TIME_LIST / DIST_LIST, allégées à MAX crans si trop longues.
 */
object Ticks {
    const val MAX = 50
    private const val LOG_COUNT = 40

    /** Pas « jolis » en ms (rapport entre deux voisins <= 2,5 : un pas constant donne ainsi toujours au moins 20 crans). */
    private val STEPS = longArrayOf(
        1L, 2L, 5L, 10L, 20L, 25L, 50L, 100L, 200L, 250L, 500L, 1_000L, 2_000L, 5_000L, 10_000L, 15_000L, 30_000L, 60_000L,
        120_000L, 300_000L, 600_000L, 900_000L, 1_800_000L, 3_600_000L, 7_200_000L, 10_800_000L, 21_600_000L, 43_200_000L
    )

    /** Valeurs jolies en ms, préférées par l'échelle log quand elles sont assez proches du point visé. */
    private val NICE: LongArray = run {
        val out = ArrayList<Long>()
        listOf(1L, 2L, 3L, 4L, 5L, 6L, 8L, 10L, 12L, 15L, 20L, 25L, 30L, 40L, 50L, 60L, 80L, 100L, 120L, 150L, 200L, 250L, 300L,
            400L, 500L, 600L, 800L).forEach { out.add(it) }
        listOf(1.0, 1.2, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0, 12.0, 15.0, 20.0, 25.0, 30.0, 40.0, 45.0, 50.0)
            .forEach { out.add((it * 1_000.0).roundToLong()) }
        listOf(1.0, 1.25, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0, 12.0, 15.0, 20.0, 25.0, 30.0, 40.0, 45.0, 50.0)
            .forEach { out.add((it * 60_000.0).roundToLong()) }
        listOf(1.0, 1.25, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0, 12.0, 16.0, 20.0, 24.0)
            .forEach { out.add((it * 3_600_000.0).roundToLong()) }
        out.toLongArray()
    }

    /** Crans d'un curseur de temps entre lo et hi (ms). */
    fun time(lo: Long, hi: Long, log: Boolean): List<Long> {
        if (hi <= lo) return listOf(lo)
        if (hi - lo < MAX) return (lo..hi).toList()
        return if (log) logTime(lo, hi) else linearTime(lo, hi)
    }

    private fun linearTime(lo: Long, hi: Long): List<Long> {
        for (s in STEPS) {
            val first = (lo / s + 1L) * s
            val last = ((hi - 1L) / s) * s
            val inner = if (last >= first) (last - first) / s + 1L else 0L
            if (inner + 2L <= MAX) {
                val out = ArrayList<Long>()
                out.add(lo)
                var v = first
                while (v <= last) { out.add(v); v += s }
                out.add(hi)
                return out
            }
        }
        return listOf(lo, hi)
    }

    private fun logTime(lo: Long, hi: Long): List<Long> {
        val a = maxOf(lo, 1L).toDouble()
        val r = (hi / a).pow(1.0 / (LOG_COUNT - 1))
        val out = sortedSetOf(lo, hi)
        for (i in 0 until LOG_COUNT) {
            val t = a * r.pow(i)
            val gap = t * (r - 1.0)              // écart voulu jusqu'au cran suivant
            var v = nearestNice(t)
            if (abs(v - t) > gap * 0.5) {        // plage resserrée : arrondi au pas joli inférieur à l'écart
                var s = 1L
                for (c in STEPS) if (c <= gap) s = c
                v = (t / s).roundToLong() * s
            }
            if (v > lo && v < hi) out.add(v)
        }
        return out.toList()
    }

    private fun nearestNice(t: Double): Long {
        var best = NICE[0]
        for (n in NICE) if (abs(n - t) < abs(best - t)) best = n
        return best
    }

    // ------------------------------------------------------------ listes rondes (précisions, distance, exercice, repos)

    /** Temps en ms. */
    val TIME_LIST: List<Double> =
        listOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0, 12.0, 15.0, 20.0, 25.0, 30.0, 40.0, 50.0, 60.0, 75.0, 80.0,
            100.0, 150.0, 200.0, 250.0, 300.0, 400.0, 500.0, 600.0, 750.0, 800.0) +
        listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0, 12.0, 15.0, 20.0, 25.0, 30.0, 40.0, 45.0, 50.0,
            60.0, 75.0, 90.0, 120.0, 150.0, 180.0, 240.0, 300.0, 360.0, 420.0, 480.0, 540.0, 600.0, 720.0, 900.0,
            1200.0, 1500.0, 1800.0, 2400.0, 2700.0, 3000.0, 3600.0, 4500.0, 5400.0, 7200.0, 9000.0, 10800.0,
            14400.0, 18000.0, 21600.0, 28800.0, 36000.0, 43200.0, 57600.0, 72000.0, 86400.0).map { it * 1000.0 }

    /** Distances en mètres. */
    val DIST_LIST: List<Double> = listOf(
        0.0, 1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 7.5, 8.0, 10.0, 12.0, 15.0, 20.0, 25.0, 30.0, 40.0, 50.0, 60.0, 75.0, 80.0,
        100.0, 110.0, 120.0, 150.0, 200.0, 250.0, 300.0, 400.0, 500.0, 600.0, 750.0, 800.0, 1000.0, 1200.0, 1500.0, 2000.0,
        2500.0, 3000.0, 4000.0, 5000.0, 6000.0, 7500.0, 8000.0, 10000.0, 12000.0, 15000.0, 20000.0, 25000.0, 30000.0, 40000.0,
        50000.0, 60000.0, 75000.0, 80000.0, 100000.0
    )

    /** Valeurs gardées en priorité quand une liste doit être allégée. */
    val TIME_CORE: Set<Double> =
        setOf(0.0, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0) +
        listOf(1.0, 2.0, 5.0, 10.0, 15.0, 20.0, 30.0, 45.0, 60.0, 90.0, 120.0, 180.0, 300.0, 600.0, 900.0, 1200.0, 1800.0,
            2700.0, 3600.0, 5400.0, 7200.0, 10800.0, 21600.0, 43200.0, 86400.0).map { it * 1000.0 }

    val DIST_CORE: Set<Double> = setOf(
        0.0, 1.0, 2.0, 5.0, 10.0, 20.0, 25.0, 50.0, 100.0, 200.0, 300.0, 400.0, 500.0, 800.0, 1000.0, 1500.0, 2000.0, 3000.0,
        5000.0, 10000.0, 15000.0, 20000.0, 50000.0, 100000.0
    )

    /** true si v est un multiple (à la tolérance près) du pas ; un pas <= 0 n'impose rien. */
    fun isMultiple(v: Double, step: Double): Boolean {
        if (step <= 0.0) return true
        val r = v / step
        return abs(r - round(r)) < 1e-6
    }

    /** Valeurs de la liste dans [lo, hi] et multiples du pas (0 toujours accepté). Peut être vide. */
    fun filtered(list: List<Double>, lo: Double, hi: Double, step: Double): List<Double> =
        list.filter { v -> v >= lo - 1e-4 && v <= hi + 1e-4 && (v == 0.0 || isMultiple(v, step)) }

    /** Repli quand aucune (ou une seule) valeur ronde ne tient dans la plage : multiples du pas, au plus MAX. Jamais vide. */
    fun multiples(lo: Double, hi: Double, step: Double): List<Double> {
        val s = if (step > 0.0) step else 1.0
        val first = ceil(lo / s - 1e-6).toLong()
        val last = floor(hi / s + 1e-6).toLong()
        if (last < first) return listOf(lo)
        val stride = (last - first) / (MAX - 1) + 1L
        val out = ArrayList<Double>()
        var k = first
        while (k <= last) { out.add(k * s); k += stride }
        if (k - stride < last) out.add(last * s)        // le dernier multiple reste toujours atteignable
        return out
    }

    /** Allège une liste croissante à MAX valeurs : extrémités et valeurs « core », puis celles qui comblent les plus grands trous (log). */
    fun thin(list: List<Double>, core: Set<Double>): List<Double> {
        if (list.size <= MAX) return list
        val keep = ArrayList<Double>()
        val rest = ArrayList<Double>()
        list.forEachIndexed { i, v -> if (i == 0 || i == list.size - 1 || v in core) keep.add(v) else rest.add(v) }
        while (keep.size < MAX && rest.isNotEmpty()) {
            var best = 0
            var bestScore = -1.0
            for (i in rest.indices) {
                val c = rest[i]
                val at = -(keep.binarySearch(c)) - 1          // c n'est pas dans keep : point d'insertion
                val before = ln(maxOf(keep[at - 1], 0.5))
                val after = ln(keep[at])
                val score = minOf(ln(c) - before, after - ln(c))
                if (score > bestScore + 1e-9) { bestScore = score; best = i }
            }
            val v = rest.removeAt(best)
            keep.add(-(keep.binarySearch(v)) - 1, v)
        }
        return keep
    }
}
