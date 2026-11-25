package com.example.chrono

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * Cycle d'exercice : [exercice] x nbRep, puis [repos], puis on recommence.
 * Temps d'exercice = 0 -> pas d'objectif (aucune vibration). Les segments suivent le temps du chrono.
 *
 * FRONTIÈRES. La j-ième frontière (j >= 1) est la fin du j-ième segment du cycle (chaque exercice, puis le repos s'il existe, puis on
 * recommence). Une frontière est identifiée par son INDEX j (stable) et non par son instant : le recalage ne fait que déplacer les instants.
 *   - boundaryTime(j)   : instant de chrono (ms) de la frontière j, recalage compris.
 *   - crossedCount(e)   : nombre de frontières déjà franchies au temps de chrono e.
 *   - nextBoundary(e)   : (index, instant) de la prochaine frontière strictement après e.
 *
 * VIBRATION DES OBJECTIFS : jamais de tour créé, uniquement une vibration (celle d'un tour). Trois couches redondantes, sans doublon :
 *   1. PREMIER PLAN : la boucle d'affichage de MainActivity appelle check(c, "ui") à chaque tick (tant que l'écran est allumé).
 *   2. ALARME : setAlarmClock (repli : exacte, puis inexacte) posée sur la prochaine frontière -> SegmentReceiver -> onAlarm() ; marche
 *      appli en arrière-plan / écran éteint.
 *   3. SERVICE DE PREMIER PLAN léger (GoalService) actif seulement quand le chrono tourne ET que les objectifs vibrent : il empêche le
 *      système (Samsung : « applis en veille ») de tuer l'appli / d'annuler ses alarmes. Il ne fait rien d'autre.
 * DÉDOUBLONNAGE persistant : « segDone » = index de la dernière frontière traitée (SharedPreferences « stopwatch »). check() ne vibre que
 * pour une frontière d'index > segDone, puis l'enregistre AVANT de vibrer. Une frontière franchie depuis plus de FRESH_MS est marquée
 * traitée sans vibrer (on rouvre l'appli longtemps après). Plusieurs frontières franchies d'un coup = une seule vibration.
 * Le journal « segDiag » (12 dernières lignes, Segments.diagText) dit qui a vibré, quand, avec quel chemin de vibration.
 *
 * POINT D'EXTENSION (départ random, etc.) : boundaryListener est appelé par check() à chaque nouvelle frontière traitée.
 *
 * Recalage (Settings.trackRecal) : le tour n° k marque la fin du segment n° k (chaque exercice, puis le repos, puis on recommence).
 * Les objectifs gardent leurs durées mais se décalent de « instant du tour - fin prévue du segment k » (le décalage). Ex. exercice 10 s x 3,
 * repos 20 s : tour à 8 s -> objectif suivant à 18 s ; tour à 20 s -> 30 s ; tour à 31 s -> fin du repos à 51 s.
 * Le décalage est déduit de la liste des tours : annuler le dernier tour annule donc aussi son décalage, et un redémarrage ne le perd pas.
 * Hypothèse : un tour manuel qui avance une frontière (tour à 8 s pour un objectif à 10 s) la fait « franchir » à cet instant : la frontière
 * vibre alors une fois (juste après la vibration du tour). Un tour à +0,1 s d'une frontière : les deux vibrent.
 */
object Segments {
    /** true tant que MainActivity est visible (onStart..onStop) : la vibration simple suffit alors, l'usage « alarme » sert en arrière-plan. */
    @Volatile var uiVisible = false

    /** Appelé (thread principal) quand check() traite une nouvelle frontière : (contexte, index de frontière, true si vibration donnée). */
    @Volatile var boundaryListener: ((Context, Int, Boolean) -> Unit)? = null

    private const val FRESH_MS = 4000L          // au-delà, une frontière franchie est marquée traitée sans vibrer
    private const val ALARM_SLACK_MS = 250L     // tolérance quand l'alarme arrive à la limite de la frontière

    private fun exMs() = Settings.exTime.value.roundToInt() * 1000L
    private fun restMs() = Settings.restTime.value.roundToInt() * 1000L
    private fun reps() = Settings.nbRep.value.roundToInt().coerceAtLeast(1)

    fun enabled(): Boolean = Settings.track && exMs() > 0L
    private fun cycleMs() = reps() * exMs() + restMs()

    /** Nombre de segments d'un cycle : une répétition = un segment, plus le repos s'il y en a un. */
    fun perCycle(): Int = reps() + (if (restMs() > 0L) 1 else 0)

    /** Fin prévue (sans décalage) du segment n° k du chrono (k >= 1), en ms de chrono. */
    private fun nominalEnd(k: Int): Long {
        val n = perCycle()
        val idx = (k - 1).coerceAtLeast(0)
        val pos = idx % n
        val inCycle = if (pos < reps()) (pos + 1) * exMs() else reps() * exMs() + restMs()
        return (idx / n) * cycleMs() + inCycle
    }

    /** Décalage courant (ms, négatif = en avance) : dernier tour - fin prévue du segment qu'il termine. 0 si le recalage est inactif. */
    fun shift(): Long {
        // Départ random actif : les départs au coup de feu re-synchronisent déjà les objectifs ; le recalage par les tours (un tour par segment)
        // n'a plus de sens (pas de tour au début d'un repos) et est donc ignoré.
        if (!Settings.trackRecal || Settings.randStart || !enabled()) return 0L
        val laps = Stopwatch.laps
        val last = laps.firstOrNull() ?: return 0L
        return last.total - nominalEnd(laps.size)
    }

    /**
     * Décalage EXPLICITE du départ random (chrono non figé pendant l'attente) : retard réel cumulé entre la frontière et le coup de feu.
     * Persisté (clé « randShift » des préférences « stopwatch »), remis à 0 par resetProgress (nouvelle séance / remise à zéro).
     */
    @Volatile var randShiftMs = 0L
        private set

    private fun totalShift(): Long = shift() + randShiftMs

    fun loadRandShift(c: Context) {
        randShiftMs = try { prefs(c).getLong("randShift", 0L) } catch (e: Exception) { 0L }
    }

    fun addRandShift(c: Context, d: Long) {
        randShiftMs += d.coerceAtLeast(0L)
        try { prefs(c).edit().putLong("randShift", randShiftMs).commit() } catch (e: Exception) { }
    }

    /** true si le segment qui SUIT la frontière j est une répétition d'exercice (et non le repos) : seul cas d'un départ random. */
    fun isExerciseStart(j: Int): Boolean {
        if (!enabled() || j < 1) return false
        return (j % perCycle()) < reps()
    }

    /** Après un décalage / un départ : la frontière déjà « traitée » est celle du temps de chrono actuel (aucune frontière sautée ni rejouée). */
    fun resync(c: Context) {
        try { setDone(c, crossedCount(Stopwatch.elapsed())) } catch (e: Exception) { }
    }

    /** Instant de chrono (ms) de la frontière j (j >= 1), recalage compris. */
    fun boundaryTime(j: Int): Long = nominalEnd(j) + totalShift()

    /** Nombre de frontières franchies (instant <= e) au temps de chrono e, recalage compris. 0 si aucun objectif. */
    fun crossedCount(e: Long): Int {
        if (!enabled()) return 0
        val en = e - totalShift()               // temps « prévu » (sans décalage)
        if (en < 0L) return 0
        val ex = exMs()
        val cyc = cycleMs()
        val q = en / cyc
        val rem = en % cyc
        val k = minOf(reps().toLong(), rem / ex)   // fins d'exercice déjà passées dans le cycle courant (la fin de repos = début du cycle suivant)
        return (q * perCycle() + k).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** Prochaine frontière strictement après le temps de chrono e : (index j, instant de chrono). */
    fun nextBoundary(e: Long): Pair<Int, Long> {
        val j = crossedCount(e) + 1
        return j to boundaryTime(j)
    }

    /** (temps écoulé dans le segment en cours, durée de ce segment) pour un temps de chrono e. */
    fun segment(e: Long): Pair<Long, Long> {
        val ex = exMs()
        val cyc = cycleMs()
        val p = (e - totalShift()).coerceAtLeast(0L) % cyc
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
        val s = totalShift()
        val en = (e - s).coerceAtLeast(0L)          // temps « prévu » (sans décalage)
        val base = en - en % cyc
        val p = en % cyc
        for (k in 1..n) {
            val b = k * ex
            if (b > p) return (base + b + s) to (if (k == n && rest > 0L) 1 else 0)
        }
        return (base + cyc + s) to (if (rest > 0L) 2 else 0)
    }

    // ------------------------------------------------------------ état persistant de dédoublonnage + journal

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("stopwatch", Context.MODE_PRIVATE)

    private var doneCache = -1                   // -1 = pas encore lu ; sinon index de la dernière frontière traitée

    private fun done(c: Context): Int {
        if (doneCache < 0) doneCache = try { prefs(c).getInt("segDone", 0) } catch (e: Exception) { 0 }
        return doneCache
    }

    private fun setDone(c: Context, j: Int) {
        doneCache = j
        try { prefs(c).edit().putInt("segDone", j).commit() } catch (e: Exception) { }
    }

    /** Nouvelle séance / remise à zéro : plus aucune frontière n'a été traitée. */
    fun resetProgress(c: Context) {
        doneCache = 0
        randShiftMs = 0L
        try { prefs(c).edit().putInt("segDone", 0).putString("segSig", "").putLong("randShift", 0L).apply() } catch (e: Exception) { }
    }

    /** Si les réglages d'objectif ont changé en cours de séance, les index n'ont plus le même sens : on se recale en silence. */
    private fun syncSignature(c: Context) {
        val sig = "${exMs()}|${restMs()}|${reps()}|${Settings.trackRecal}|${Settings.randStart}"
        try {
            val p = prefs(c)
            if (p.getString("segSig", "") != sig) {
                setDone(c, crossedCount(Stopwatch.elapsed()))
                p.edit().putString("segSig", sig).apply()
            }
        } catch (e: Exception) { }
    }

    private fun diag(c: Context, msg: String) {
        try {
            val p = prefs(c)
            val t = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.ROOT).format(java.util.Date())
            val all = ((p.getString("segDiag", "") ?: "").split('\n').filter { it.isNotEmpty() } + "$t $msg").takeLast(12)
            p.edit().putString("segDiag", all.joinToString("\n")).apply()
        } catch (e: Exception) { }
    }

    /** Journal de diagnostic des objectifs (12 dernières lignes : alarmes posées, vibrations données, chemin de vibration utilisé). */
    fun diagText(c: Context): String = try { prefs(c).getString("segDiag", "") ?: "" } catch (e: Exception) { "" }

    // ------------------------------------------------------------ détection + vibration (couches 1 et 2)

    /**
     * Détecte les frontières franchies depuis la dernière traitée et vibre UNE fois (jamais de tour). Idempotent et sans danger à appeler
     * souvent (10 fois par seconde au premier plan). src = origine pour le journal ; slackMs = tolérance (alarme arrivée juste avant).
     */
    @Synchronized
    fun check(c: Context, src: String, slackMs: Long = 0L) {
        try {
            if (!Stopwatch.running || !enabled()) return
            if (RandomStart.waiting) return                       // attente d'un départ random : aucune frontière traitée (resync au tir)
            val e = Stopwatch.elapsed() + slackMs
            val j = crossedCount(e)
            val d = done(c)
            if (j <= d) return
            val late = e - boundaryTime(j)
            setDone(c, j)                                         // enregistré AVANT la vibration : aucun doublon possible
            var vibrated = false
            var path = "silent"
            if (Settings.trackVibe && late <= FRESH_MS) {
                path = Stopwatch.buzzLogged(c, false, alarm = !uiVisible)   // vibration d'un tour
                vibrated = true
            }
            diag(c, "$src j=$j (done=$d) late=${late}ms -> $path")
            try { boundaryListener?.invoke(c, j, vibrated) } catch (ex: Exception) { diag(c, "listener: ${ex.message}") }
        } catch (ex: Exception) {
            diag(c, "check error: ${ex.message}")
        }
    }

    /** Appelé par SegmentReceiver (alarme) : vibre si besoin, puis pose l'alarme suivante. */
    fun onAlarm(c: Context) {
        check(c, "alarm", ALARM_SLACK_MS)
        schedule(c)
    }

    // ------------------------------------------------------------ alarmes (couche 2)

    private fun pi(c: Context, j: Int): PendingIntent {
        val app = c.applicationContext
        return PendingIntent.getBroadcast(
            app, 3, Intent(app, SegmentReceiver::class.java).putExtra("j", j),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * Pose une alarme à l'heure murale `at` : setAlarmClock (aucune permission, passe le Doze), sinon exacte, sinon inexacte.
     * showCode = requestCode du PendingIntent « afficher l'appli » de l'alarme-horloge. Renvoie le mode utilisé ("fail" si tout échoue).
     */
    fun armExact(c: Context, at: Long, op: PendingIntent, showCode: Int): String {
        val app = c.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return "noAM"
        try {
            val show = PendingIntent.getActivity(app, showCode, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), op)
            return "alarmClock"
        } catch (e: Exception) { }
        try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op); return "exact" } catch (e: Exception) { }
        try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op); return "inexact" } catch (e: Exception) { }
        return "fail"
    }

    private fun cancelAlarm(c: Context) {
        try { c.applicationContext.getSystemService(AlarmManager::class.java)?.cancel(pi(c, 0)) }
        catch (e: Exception) { }
    }

    /** Annule l'alarme de frontière ET arrête le service de premier plan (arrêt, remise à zéro, objectifs désactivés). */
    fun cancel(c: Context) {
        cancelAlarm(c)
        if (!RandomStart.active) stopService(c)
    }

    /** Les alarmes de frontière servent aux vibrations des objectifs ET au départ random. */
    private fun goalsActive(): Boolean = Settings.trackVibe || Settings.randStart

    /** Met le service de premier plan en phase avec l'état (appelé par RandomStart au début / à la fin d'une séquence). */
    fun refreshService(c: Context) {
        if (wantsService()) startService(c) else stopService(c)
    }

    /**
     * (Re)pose l'alarme de la prochaine frontière, si le chrono tourne, qu'un objectif est réglé et que les vibrations d'objectifs sont ON.
     * Rattrape d'abord une éventuelle frontière déjà franchie (check) et garde le service de premier plan en phase avec l'état.
     */
    fun schedule(c: Context) {
        try {
            cancelAlarm(c)
            if (!enabled() || !goalsActive() || !Stopwatch.running || RandomStart.waiting) {
                if (!RandomStart.active) stopService(c)          // pendant un départ random, le service garde le processus en vie
                return
            }
            syncSignature(c)
            check(c, "sched")
            if (!Stopwatch.running || RandomStart.waiting) return   // check() a pu lancer un départ random (chrono figé) : pas d'alarme de frontière
            val e = Stopwatch.elapsed()
            val j = maxOf(done(c), crossedCount(e)) + 1     // première frontière ni traitée ni franchie
            val b = boundaryTime(j)
            val at = System.currentTimeMillis() + (b - e).coerceAtLeast(0L)
            val how = armExact(c, at, pi(c, j), 4)
            startService(c)
            diag(c, "arm j=$j in=${b - e}ms $how")
        } catch (ex: Exception) {
            diag(c, "schedule error: ${ex.message}")
        }
    }

    // ------------------------------------------------------------ service de premier plan (couche 3)

    private var serviceUp = false

    /** Le service tourne si le chrono tourne ET que des objectifs vibrent / un départ random est configuré, ou pendant toute une séquence de départ random. */
    fun wantsService(): Boolean = RandomStart.active || (Stopwatch.running && enabled() && goalsActive())

    private fun startService(c: Context) {
        if (serviceUp || !wantsService()) return
        try {
            val app = c.applicationContext
            ContextCompat.startForegroundService(app, Intent(app, GoalService::class.java))
            serviceUp = true
        } catch (e: Exception) {
            diag(c, "service KO: ${e.javaClass.simpleName}")
        }
    }

    private fun stopService(c: Context) {
        serviceUp = false
        try {
            val app = c.applicationContext
            app.stopService(Intent(app, GoalService::class.java))
        } catch (e: Exception) { }
    }
}
