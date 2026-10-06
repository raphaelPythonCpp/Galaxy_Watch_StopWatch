package com.example.chrono

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlin.math.roundToInt

/**
 * Cycle d'exercice : [exercice] x nbRep, puis [repos], puis on recommence.
 * Temps d'exercice = 0 -> pas d'objectif (aucune vibration). Les segments suivent le temps du chrono.
 * Une alarme exacte « réveil » est posée à chaque frontière : la vibration marche écran éteint, sans service permanent.
 */
object Segments {
    private fun exMs() = Settings.exTime.value.roundToInt() * 1000L
    private fun restMs() = Settings.restTime.value.roundToInt() * 1000L
    private fun reps() = Settings.nbRep.value.roundToInt().coerceAtLeast(1)

    fun enabled(): Boolean = Settings.track && exMs() > 0L
    private fun cycleMs() = reps() * exMs() + restMs()

    /** (temps écoulé dans le segment en cours, durée de ce segment) pour un temps de chrono e. */
    fun segment(e: Long): Pair<Long, Long> {
        val ex = exMs()
        val cyc = cycleMs()
        val p = e % cyc
        val exAll = reps() * ex
        return if (p < exAll) (p % ex) to ex else (p - exAll) to restMs()
    }

    /** Avancement 0..1 du segment en cours (pour la bague). */
    fun ringFrac(e: Long): Float {
        val (a, len) = segment(e)
        return if (len <= 0L) 0f else (a.toFloat() / len).coerceIn(0f, 1f)
    }

    /** Prochaine frontière strictement après e : (temps de chrono, type) ; type 0 = fin d'exercice, 1 = début de repos, 2 = fin de repos. */
    fun next(e: Long): Pair<Long, Int> {
        val ex = exMs()
        val rest = restMs()
        val n = reps()
        val cyc = cycleMs()
        val base = e - e % cyc
        val p = e % cyc
        for (k in 1..n) {
            val b = k * ex
            if (b > p) return (base + b) to (if (k == n && rest > 0L) 1 else 0)
        }
        return (base + cyc) to (if (rest > 0L) 2 else 0)
    }

    private fun pi(c: Context, type: Int): PendingIntent {
        val app = c.applicationContext
        return PendingIntent.getBroadcast(
            app, 3, Intent(app, SegmentReceiver::class.java).putExtra("type", type),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun cancel(c: Context) {
        try { c.applicationContext.getSystemService(AlarmManager::class.java)?.cancel(pi(c, 0)) }
        catch (e: Exception) { }
    }

    /** (Re)pose l'alarme de la prochaine frontière, si le chrono tourne et qu'un objectif est réglé. */
    fun schedule(c: Context) {
        cancel(c)
        if (!enabled() || !Stopwatch.running) return
        try {
            val app = c.applicationContext
            val e = Stopwatch.elapsed() + 50L          // marge : jamais deux fois la même frontière
            val (b, type) = next(e)
            val at = System.currentTimeMillis() + (b - e)
            val show = PendingIntent.getActivity(
                app, 4, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            app.getSystemService(AlarmManager::class.java)
                ?.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi(app, type))
        } catch (e: Exception) { }
    }
}
