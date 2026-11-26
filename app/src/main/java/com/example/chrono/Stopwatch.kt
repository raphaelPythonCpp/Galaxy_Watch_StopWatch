package com.example.chrono

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.ApplicationExitInfo
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.roundToInt

data class Lap(val index: Int, val lapTime: Long, val total: Long)

/** État unique partagé par l'appli, le tile, la complication et les récepteurs. */
object Stopwatch {
    var running by mutableStateOf(false)
        private set
    var accumulated by mutableLongStateOf(0L)
        private set
    val laps = mutableStateListOf<Lap>()   // le plus récent en premier

    var nonce = 0
        private set
    /** Heure murale au Start (sert aux comparaisons avec les horodatages du système et à la notification). */
    private var startedAt = 0L
    /**
     * Horloge monotone (SystemClock.elapsedRealtime) au Start : le temps mesuré n'est ainsi pas faussé quand la montre
     * recale son heure (synchronisation avec le téléphone, réseau, changement manuel). Valable tant que la montre n'a
     * pas redémarré (compteur de démarrages identique) ; sinon on retombe sur l'heure murale (le chrono survit au redémarrage).
     */
    private var startedEl = 0L
    private var startedBoot = -1
    private var sameBoot = false
    private var loaded = false
    private var quiet = false
    /** true : tours stockés dans un fichier en ajout seul (mode éco) ; false : dans les préférences. */
    private var lapsInFile = false

    private fun now() = System.currentTimeMillis()
    private fun prefs(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("stopwatch", Context.MODE_PRIVATE)
    private fun lapFile(c: Context) = File(c.applicationContext.filesDir, "laps.log")
    private fun limitMs(): Long = Settings.autoStopMs
    private fun lapsString() = laps.joinToString(";") { "${it.index},${it.lapTime},${it.total}" }

    /** Durée écoulée depuis le dernier Start : horloge monotone si possible, sinon heure murale. */
    private fun runDelta(): Long {
        if (sameBoot && startedEl > 0L) {
            val d = SystemClock.elapsedRealtime() - startedEl
            if (d >= 0L) return d
        }
        return (now() - startedAt).coerceAtLeast(0L)
    }

    /**
     * Dernière interaction, en TEMPS DE CHRONO (ms) : lancement / reprise, tour manuel, annulation de tour (et, plus tard, coup de feu
     * du départ random). L'arrêt automatique survient quand (temps - lastAct) > limite ; le temps est alors figé à lastAct + limite.
     * Persisté (clé « lastAct », nouvelle en v14) ; remis à 0 par la remise à zéro.
     */
    private var lastAct = 0L

    /** Temps de chrono brut (non plafonné). */
    private fun rawElapsed(): Long = (accumulated + if (running) runDelta() else 0L).coerceAtLeast(0L)

    /** Temps de chrono auquel l'arrêt automatique se déclenche (lastAct + limite), ou -1 s'il est désactivé. */
    private fun autoStopAt(): Long {
        val lim = limitMs()
        return if (lim > 0L) lastAct + lim else -1L
    }

    /** Temps écoulé ; plafonné au point d'arrêt automatique si l'arrêt est actif (jamais d'affichage au-delà). */
    fun elapsed(): Long {
        val raw = rawElapsed()
        val stopAt = autoStopAt()
        return if (running && stopAt > 0L) minOf(raw, stopAt) else raw
    }

    /** Enregistre une interaction (au temps de chrono t) et persiste. */
    private fun touch(c: Context, t: Long) {
        lastAct = t
        try { prefs(c).edit().putLong("lastAct", t).apply() } catch (e: Exception) { }
    }

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        Settings.load(c)
        try {
            val p = prefs(c)
            running = p.getBoolean("run", false)
            accumulated = p.getLong("acc", 0L)
            lastAct = p.getLong("lastAct", accumulated).coerceAtLeast(0L)   // état v13 : repart de la reprise
            startedAt = p.getLong("start", 0L)
            startedEl = p.getLong("startEl", 0L)
            startedBoot = p.getInt("startBoot", -1)
            sameBoot = running && startedEl > 0L && startedBoot != -1 &&
                startedBoot == bootCount(c) && SystemClock.elapsedRealtime() >= startedEl
            nonce = p.getInt("nonce", 0)
            lapsInFile = p.getBoolean("lapsFile", false)
            laps.clear()
            if (lapsInFile) replayFile(c)
            else (p.getString("laps", "") ?: "").split(';').filter { it.isNotEmpty() }.forEach {
                val f = it.split(',')
                laps.add(Lap(f[0].toInt(), f[1].toLong(), f[2].toLong()))
            }
        } catch (e: Exception) {
            running = false; accumulated = 0L; lastAct = 0L; startedAt = 0L; startedEl = 0L; sameBoot = false; laps.clear(); lapsInFile = false
        }
        checkForcedStop(c)
        RandomStart.load(c)      // branche l'écouteur de frontières ; une attente de départ random interrompue par un redémarrage est annulée
        enforceAutoStop(c)
        if (running) {
            scheduleAutoStop(c)      // réarme l'arrêt auto (réglage changé / alarme perdue)
            Segments.schedule(c)     // réarme l'alarme de la prochaine frontière d'objectif + couche « premier plan »
        }
    }

    // ------------------------------------------------------------ arrêt forcé / fermeture de l'appli

    /**
     * Quand l'appli est FERMÉE (retirée des applis récentes) ou FORCÉE À L'ARRÊT (Paramètres > Applications), le processus
     * est tué sans que l'appli puisse réagir. Au lancement suivant (appli, tile, complication…), deux détections
     * indépendantes décident s'il faut faire « stop puis reset » :
     *  1. Sentinelle : tant que le chrono tourne, une alarme lointaine tient un PendingIntent. « Forcer l'arrêt » annule
     *     alarmes et PendingIntents de l'appli ; s'il a disparu alors qu'il avait été posé, l'appli a été arrêtée de force
     *     (sauf redémarrage de la montre, qui efface aussi les alarmes mais où le chrono continue comme avant).
     *  2. Journal de sortie du système (ApplicationExitInfo) : fermeture demandée par l'utilisateur (applis récentes ou arrêt forcé).
     * Un processus tué par manque de mémoire ne déclenche rien : le chrono continue, calé sur l'horloge.
     */
    private fun checkForcedStop(c: Context) {
        val p = prefs(c)
        var exitTs = 0L
        var userKill = false
        try {
            val am = c.getSystemService(ActivityManager::class.java)
            val newest = am?.getHistoricalProcessExitReasons(c.packageName, 0, 8)?.maxByOrNull { it.timestamp }
            if (newest != null && newest.timestamp > p.getLong("exitSeen", 0L)) {
                p.edit().putLong("exitSeen", newest.timestamp).apply()
                exitTs = newest.timestamp
                userKill = isUserKill(newest)
            }
        } catch (e: Exception) { }
        if (!running) return
        val savedBoot = p.getInt("boot", -2)
        val rebooted = savedBoot != -2 && savedBoot != bootCount(c)
        val sentinelLost = p.getBoolean("sentinel", false) && !rebooted && sentinelMissing(c)
        val closedByUser = userKill && exitTs > startedAt
        if (sentinelLost || closedByUser) {
            val seen = p.getLong("seen", 0L)
            val at = when {
                exitTs > startedAt -> exitTs        // instant de la fermeture, donné par le système
                seen > startedAt -> seen            // sinon, dernier passage dans l'appli
                else -> now()
            }
            stopAndReset(c, at, true)
        } else {
            armSentinel(c)                          // (re)pose la sentinelle pour la suite
        }
    }

    /** Fermeture voulue par l'utilisateur : applis récentes (« remove task ») ou Forcer l'arrêt (« stop <paquet> »). */
    private fun isUserKill(i: ApplicationExitInfo): Boolean {
        if (i.reason == ApplicationExitInfo.REASON_USER_REQUESTED || i.reason == ApplicationExitInfo.REASON_USER_STOPPED) return true
        val d = (i.description ?: "").lowercase(Locale.ROOT)
        return d.contains("remove task") || d.startsWith("stop ") || d.contains("force stop") || d.contains("forcestop")
    }

    private fun bootCount(c: Context): Int =
        try { android.provider.Settings.Global.getInt(c.contentResolver, android.provider.Settings.Global.BOOT_COUNT) }
        catch (e: Exception) { -1 }

    private fun sentinelIntent(app: Context) =
        Intent(app, SentinelReceiver::class.java).setAction("com.example.chrono.SENTINEL")

    private fun sentinelPi(c: Context, create: Boolean): PendingIntent? {
        val app = c.applicationContext
        return PendingIntent.getBroadcast(
            app, 7, sentinelIntent(app),
            PendingIntent.FLAG_IMMUTABLE or (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
        )
    }

    private fun sentinelMissing(c: Context): Boolean =
        try { sentinelPi(c, false) == null } catch (e: Exception) { false }

    /** Pose la sentinelle : alarme non réveillante dans ~300 jours (aucun coût de batterie), qui retient le PendingIntent. */
    fun armSentinel(c: Context) {
        try {
            val pi = sentinelPi(c, true) ?: return
            c.applicationContext.getSystemService(AlarmManager::class.java)
                ?.set(AlarmManager.RTC, now() + 300L * 86_400_000L, pi)
            prefs(c).edit().putBoolean("sentinel", true).putInt("boot", bootCount(c)).apply()
        } catch (e: Exception) { }
    }

    private fun disarmSentinel(c: Context) {
        try {
            val pi = sentinelPi(c, false)
            if (pi != null) {
                c.applicationContext.getSystemService(AlarmManager::class.java)?.cancel(pi)
                pi.cancel()
            }
        } catch (e: Exception) { }
        try { prefs(c).edit().putBoolean("sentinel", false).apply() } catch (e: Exception) { }
    }

    /** Dernier passage dans l'appli (sert d'estimation de l'instant d'arrêt si le système n'en donne pas). */
    fun markSeen(c: Context) {
        try { prefs(c).edit().putLong("seen", now()).apply() } catch (e: Exception) { }
    }

    // ------------------------------------------------------------ arrêt automatique

    private fun alarmPi(c: Context): PendingIntent {
        val app = c.applicationContext
        return PendingIntent.getBroadcast(
            app, 2, Intent(app, AutoStopReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * (Re)pose l'alarme d'arrêt auto à l'instant lastAct + limite (+ 5 ms pour que « temps - lastAct > limite » soit vrai à la réception).
     * À appeler à chaque interaction (start / reprise, tour, annulation de tour), au chargement et quand le réglage change.
     * Même PendingIntent => remplace l'alarme précédente. Réglage à 0 ou chrono arrêté => alarme annulée.
     */
    private fun scheduleAutoStop(c: Context) {
        val stopAt = autoStopAt()
        if (!running || stopAt < 0L) { cancelAutoStop(c); return }
        try {
            val at = now() + (stopAt - rawElapsed()).coerceAtLeast(0L) + 5L
            Segments.armExact(c, at, alarmPi(c), 5)
        } catch (e: Exception) { }
    }

    /** Point d'entrée public (appelé au relâchement d'un curseur) : applique tout de suite un nouveau réglage d'arrêt auto. */
    fun rescheduleAutoStop(c: Context) {
        if (!running) return
        enforceAutoStop(c)
        if (running) scheduleAutoStop(c)
    }

    private fun cancelAutoStop(c: Context) {
        try { c.applicationContext.getSystemService(AlarmManager::class.java)?.cancel(alarmPi(c)) }
        catch (e: Exception) { }
    }

    /** Dernier instant (horloge monotone) où la vibration d'arrêt auto a été donnée : garde anti-rafale. */
    private var lastAutoBuzzEl = -1_000_000L

    /** UNE seule vibration d'arrêt auto : jamais deux à moins de 1,5 s, quel que soit le nombre d'appelants. */
    private fun buzzAutoStop(c: Context) {
        val t = SystemClock.elapsedRealtime()
        if (t - lastAutoBuzzEl < 1500L) return
        lastAutoBuzzEl = t
        buzz(c, true, alarm = !Segments.uiVisible)
    }

    /**
     * Arrête le chrono quand (temps - dernière interaction) > limite ; le temps est figé EXACTEMENT à lastAct + limite
     * (même si l'on s'en aperçoit plus tard : alarme retardée, écran éteint, rechargement). Idempotent : ne fait rien si
     * le chrono est déjà arrêté (l'état est écrit en commit() avant la vibration), donc une seule vibration.
     * Appelée par la boucle d'affichage, onStart, AutoStopReceiver, load(), lap(), et au relâchement du curseur.
     * La séance enregistrée dans l'historique (Reset / Terminer) utilise ce temps figé.
     */
    fun enforceAutoStop(c: Context) {
        if (!running) return
        val stopAt = autoStopAt()
        if (stopAt < 0L) return
        if (rawElapsed() <= stopAt) return
        accumulated = stopAt; running = false; startedAt = 0L; startedEl = 0L; sameBoot = false
        save(c, true)
        Ongoing.hide(c)
        cancelAutoStop(c)
        RandomStart.cancel(c, false)           // chrono arrêté pendant une attente non figée : plus de coup de feu
        Segments.cancel(c)
        disarmSentinel(c)
        buzzAutoStop(c)
    }

    // ------------------------------------------------------------ stockage des tours

    private fun replayFile(c: Context) {
        val f = lapFile(c)
        if (!f.exists()) return
        f.forEachLine { line ->
            try {
                if (line == "U") {
                    if (laps.isNotEmpty()) laps.removeAt(0)
                } else if (line.startsWith("L,")) {
                    val p = line.split(',')
                    laps.add(0, Lap(p[1].toInt(), p[2].toLong(), p[3].toLong()))
                }
            } catch (e: Exception) { }
        }
    }

    /** À appeler AVANT de modifier la liste : bascule le stockage selon le mode éco (migration unique). */
    private fun syncLapStore(c: Context) {
        val wantFile = Settings.eco
        if (wantFile && !lapsInFile) {
            lapFile(c).writeText(laps.reversed().joinToString("") { "L,${it.index},${it.lapTime},${it.total}\n" })
            lapsInFile = true
            prefs(c).edit().putBoolean("lapsFile", true).putString("laps", "").apply()
        } else if (!wantFile && lapsInFile) {
            lapFile(c).delete()
            lapsInFile = false
            prefs(c).edit().putBoolean("lapsFile", false).putString("laps", lapsString()).apply()
        }
    }

    /** Mode éco : on AJOUTE une ligne au fichier (incrémental). Sinon : on réécrit la liste dans les préférences. */
    private fun writeLapLine(c: Context, line: String) {
        if (lapsInFile) {
            FileOutputStream(lapFile(c), true).use { it.write((line + "\n").toByteArray()) }
        } else {
            prefs(c).edit().putString("laps", lapsString()).apply()
        }
    }

    // ------------------------------------------------------------ sauvegarde

    /** notify = true seulement quand l'état start/stop/reset change (pas à chaque tour). */
    private fun save(c: Context, notify: Boolean) {
        val app = c.applicationContext
        val e = prefs(app).edit()
            .putBoolean("run", running)
            .putLong("acc", accumulated)
            .putLong("lastAct", lastAct)
            .putLong("start", startedAt)
            .putLong("startEl", startedEl)
            .putInt("startBoot", startedBoot)
            .putInt("nonce", nonce)
            .putBoolean("lapsFile", lapsInFile)
        if (!lapsInFile) e.putString("laps", lapsString())
        // Start / Stop / Reset : écriture immédiate (commit), pour ne jamais perdre un changement d'état si le processus est tué
        if (notify) e.commit() else e.apply()
        if (!notify) return
        try {
            ComplicationDataSourceUpdateRequester
                .create(app, ComponentName(app, StopwatchComplicationService::class.java))
                .requestUpdateAll()
        } catch (e2: Exception) { }
        if (!quiet) {
            try {
                TileService.getUpdater(app).requestUpdate(StopwatchTileService::class.java)
            } catch (e2: Exception) { }
        }
    }

    fun quietly(block: () -> Unit) {
        quiet = true
        try { block() } finally { quiet = false }
    }

    fun bumpNonce(c: Context) { nonce++; save(c, true) }

    // ------------------------------------------------------------ actions

    /** Start. Départ random activé et séance neuve : le chrono ne démarre pas ici mais au coup de feu (RandomStart.tryStart). */
    fun start(c: Context) {
        if (running || RandomStart.waiting) return
        if (RandomStart.tryStart(c)) return
        startImmediately(c)
    }

    /** Démarrage réel du chrono (Start normal, ou départ random non figé : le chrono tourne pendant l'attente). */
    fun startImmediately(c: Context) {
        if (running) return
        val fresh = accumulated == 0L
        startedAt = now(); startedEl = SystemClock.elapsedRealtime(); startedBoot = bootCount(c)
        sameBoot = true; running = true
        lastAct = accumulated                  // lancement / reprise = interaction (arrêt auto décompté depuis ici)
        if (fresh) Segments.resetProgress(c)   // nouvelle séance : aucune frontière d'objectif déjà vibrée
        buzz(c, true); save(c, true)
        Ongoing.show(c, now() - elapsed())
        scheduleAutoStop(c)
        Segments.schedule(c)
        armSentinel(c)
        markSeen(c)
    }

    fun stop(c: Context) {
        if (!running) return
        enforceAutoStop(c)                     // limite déjà dépassée : le temps est figé à la limite, pas à « maintenant »
        if (!running) return
        accumulated += runDelta(); running = false; startedEl = 0L; sameBoot = false
        buzz(c, true); save(c, true)
        Ongoing.hide(c)
        cancelAutoStop(c)
        RandomStart.cancel(c, false)
        Segments.cancel(c)
        disarmSentinel(c)
    }

    fun lap(c: Context) {
        if (!running || RandomStart.waiting) return     // pas de tour manuel pendant l'attente d'un départ random
        enforceAutoStop(c)                     // si la limite est déjà dépassée, le chrono s'arrête et aucun tour n'est ajouté
        if (!running) return
        syncLapStore(c)
        val t = elapsed()
        val last = laps.firstOrNull()?.total ?: 0L
        val lap = Lap(laps.size + 1, t - last, t)
        laps.add(0, lap)
        writeLapLine(c, "L,${lap.index},${lap.lapTime},${lap.total}")
        touch(c, t)                            // interaction : l'arrêt auto repart de ce tour
        buzz(c, false)
        scheduleAutoStop(c)
        if (Settings.trackRecal) Segments.schedule(c)      // le tour décale les objectifs suivants
    }

    /** Retire le dernier tour enregistré (le chrono continue). */
    fun undoLap(c: Context) {
        if (laps.isEmpty() || RandomStart.waiting) return
        syncLapStore(c)
        laps.removeAt(0)
        writeLapLine(c, "U")
        if (running) {                          // annuler un tour compte comme une interaction (maintenant)
            enforceAutoStop(c)
            if (running) { touch(c, elapsed()); scheduleAutoStop(c) }
        }
        buzz(c, false)
        if (Settings.trackRecal) Segments.schedule(c)      // le décalage de ce tour est annulé
    }

    /** Remise à zéro (chrono arrêté) : la séance est ajoutée à l'historique. */
    fun reset(c: Context, vibrate: Boolean = true) = finishReset(c, vibrate, now())

    /** Arrête puis remet à zéro, comme un Reset après un Stop. `at` = instant (heure murale) de l'arrêt, ou maintenant si absent. */
    fun stopAndReset(c: Context, at: Long? = null, silent: Boolean = false) {
        var ts = now()
        if (running) {
            if (at == null) accumulated += runDelta()
            else { accumulated += (at - startedAt).coerceAtLeast(0L); ts = at }
            val cap = autoStopAt()                  // l'arrêt auto avait peut-être déjà dû se produire : temps figé à lastAct + limite
            if (cap >= 0L && accumulated > cap) accumulated = cap
            running = false
        } else if (at != null) ts = at
        finishReset(c, !silent, ts)
    }

    /**
     * Supprime l'activité : remise à zéro SANS enregistrement dans l'historique (chrono arrêté, appui long sur « Suppr. »).
     * La vibration est déjà donnée par l'appui long.
     */
    fun discard(c: Context) {
        running = false
        finishReset(c, false, now(), saveSession = false)
    }

    private fun finishReset(c: Context, vibrate: Boolean, ts: Long, saveSession: Boolean = true) {
        RandomStart.cancel(c, false)            // séquence de départ random éventuelle : sons stoppés, volume restauré (l'attente n'entre jamais dans l'historique)
        if (saveSession && !running && accumulated > 0L) {
            History.add(c, Session(ts, accumulated, laps.reversed().map { it.lapTime }))
        }
        running = false; accumulated = 0L; lastAct = 0L; startedAt = 0L; startedEl = 0L; sameBoot = false; laps.clear()
        try { lapFile(c).delete() } catch (e: Exception) { }
        lapsInFile = false
        Segments.resetProgress(c)               // remise à zéro : plus aucune frontière vibrée
        if (vibrate) buzz(c, true)
        save(c, true)
        Ongoing.hide(c)
        cancelAutoStop(c)
        Segments.cancel(c)
        disarmSentinel(c)
    }

    /** Pendant l'attente d'un départ random, tout appui sur Start / Stop / bouton physique ANNULE l'attente (aucun coup de feu). */
    fun toggle(c: Context) {
        if (RandomStart.waiting) { RandomStart.cancel(c, true); return }
        if (running) stop(c) else start(c)
    }
    /** Bouton physique : tour si en marche, sinon start ; annule l'attente d'un départ random. */
    fun primary(c: Context) {
        if (RandomStart.waiting) { RandomStart.cancel(c, true); return }
        if (running) lap(c) else start(c)
    }

    // ------------------------------------------------------------ départ random (appelé par RandomStart)

    /** Republie l'état (tile, complication, préférences) sans le modifier. */
    fun publish(c: Context) { save(c, true) }

    /** Fige le chrono EXACTEMENT au temps t (frontière) pendant l'attente : « running » interne = faux, plus aucun arrêt auto, plus d'alarme. */
    fun freezeAt(c: Context, t: Long) {
        if (!running) return
        accumulated = t.coerceAtLeast(0L); running = false; startedAt = 0L; startedEl = 0L; sameBoot = false
        save(c, true)
        cancelAutoStop(c)
        Ongoing.show(c, 0L, RandomStart.ongoingText())
    }

    /**
     * Coup de feu. frozen = le chrono était figé : reprise à accumulated. Sinon il tournait : on vérifie qu'il tourne encore.
     * Ajoute un tour automatique (sauf à 0:00), compte comme interaction (arrêt auto), vibre (départ). Renvoie le temps de chrono du tir,
     * ou -1 si le chrono ne peut pas (re)démarrer (déjà arrêté : aucun tir).
     */
    fun randomShot(c: Context, frozen: Boolean): Long {
        val t0 = SystemClock.elapsedRealtime()       // instant du start() du son (appelé juste après)
        // Compensation de la latence du haut-parleur : SON, VIBRATION et DÉBUT DU CHRONO doivent être perçus ensemble. Le son se fait entendre
        // ~comp ms après son start() ; le chrono (runDelta reste à 0 d'ici là), la vibration et le tour automatique suivent donc de comp ms.
        val comp = Settings.randLatMs
        if (frozen) {
            if (running) return -1L
            startedAt = now() + comp; startedEl = t0 + comp; startedBoot = bootCount(c)
            sameBoot = true; running = true
            lastAct = accumulated
        } else {
            if (!running) return -1L
            enforceAutoStop(c)
            if (!running) return -1L
        }
        // Chrono figé : le tir a lieu EXACTEMENT au temps figé (sinon 1 ms écoulé depuis la reprise créerait un tour parasite à 0:00.001).
        // Chrono non figé (il tourne déjà) : le tir est entendu comp ms plus tard, donc à elapsed() + comp.
        val t = if (frozen) accumulated else elapsed() + comp
        val wait = (comp - (SystemClock.elapsedRealtime() - t0)).coerceAtLeast(0L)
        val appCtx = c.applicationContext
        if (frozen) {
            shotBookkeeping(appCtx, t, true)
        } else if (wait <= 0L) {
            shotBookkeeping(appCtx, t, false)
        } else {
            // tour automatique et interaction à l'instant où le coup de feu est entendu
            shotHandler.postDelayed({ if (running) shotBookkeeping(appCtx, t, false) }, wait)
        }
        if (wait <= 0L) {
            buzz(appCtx, true, alarm = !Segments.uiVisible)
        } else {
            // vibration de départ : en même temps que le son entendu et le début du chrono (annulée si le chrono est arrêté entre-temps)
            shotHandler.postDelayed({ if (running) buzz(appCtx, true, alarm = !Segments.uiVisible) }, wait)
        }
        return t
    }

    private val shotHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Tour automatique du tir (sauf à 0:00), interaction pour l'arrêt auto, sauvegarde, pastille, alarme d'arrêt auto. */
    private fun shotBookkeeping(c: Context, t: Long, frozen: Boolean) {
        if (t > 0L) {
            syncLapStore(c)
            val last = laps.firstOrNull()?.total ?: 0L
            val lap = Lap(laps.size + 1, t - last, t)
            laps.add(0, lap)
            try { writeLapLine(c, "L,${lap.index},${lap.lapTime},${lap.total}") } catch (e: Exception) { }
        }
        touch(c, t)
        save(c, frozen)
        if (frozen) {
            Ongoing.show(c, now() - elapsed())
            armSentinel(c)
            markSeen(c)
        }
        scheduleAutoStop(c)
    }

    /** Annulation de l'attente par l'utilisateur : le chrono est (ou reste) arrêté, rien n'est enregistré de l'attente. */
    fun haltForCancel(c: Context, vibrate: Boolean = true) {
        if (running) { stop(c); return }       // chrono non figé : arrêt normal
        save(c, true)
        Ongoing.hide(c)
        cancelAutoStop(c)
        Segments.cancel(c)                      // alarme de frontière restée posée pendant l'attente figée (+ service s'il n'est plus utile)
        disarmSentinel(c)
        if (vibrate) buzz(c, false)
    }

    /** Usage « alarme » : sans lui, le système ignore les vibrations d'une appli qui n'est pas au premier plan (écran éteint, alarme d'objectif). */
    private val alarmAttrs: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    /**
     * strong = start/stop/reset ; sinon tour. Intensité globale réglable (0 = aucune vibration).
     * alarm = true pour les vibrations déclenchées hors de l'appli (objectifs du suivi avancé, arrêt automatique) :
     * Android 10+ n'autorise une vibration d'arrière-plan que pour les usages alarme / sonnerie / notification.
     */
    fun buzz(c: Context, strong: Boolean, alarm: Boolean = false) {
        buzzLogged(c, strong, alarm)
    }

    /** Vibreur du système : VibratorManager (API 31+) puis service Vibrator classique ; null si introuvable. */
    private fun vibrator(c: Context): Vibrator? {
        val app = c.applicationContext
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val d = vm?.defaultVibrator
                if (d != null) return d
            }
        } catch (e: Exception) { }
        return try { app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator } catch (e: Exception) { null }
    }

    /**
     * Vibration ROBUSTE ; renvoie le chemin réellement utilisé (pour le journal de diagnostic) : "off" (intensité 0), "novib" (pas de vibreur),
     * "alarm+plain" / "alarm" (attributs d'usage alarme ; si alarm = true on appelle AUSSI la vibration simple juste après : le système
     * ignore la seconde si la première joue déjà, car l'usage alarme est prioritaire, et elle sauve le coup si l'usage alarme est
     * refusé par le réglage / l'OEM), "plain" (vibration simple), "legacy" (durée seule), "fail". Jamais d'exception.
     */
    @Suppress("DEPRECATION")
    fun buzzLogged(c: Context, strong: Boolean, alarm: Boolean = false): String {
        try { Settings.load(c) } catch (e: Exception) { }
        val pct = try { Settings.vibePct } catch (e: Exception) { 100f }
        if (pct <= 0.05f) return "off"
        val v = vibrator(c) ?: return "novib"
        fun amp(base: Int) = (base * pct / 100f).roundToInt().coerceIn(1, 255)
        val effect: VibrationEffect? = try {
            if (Settings.eco) {
                VibrationEffect.createOneShot(if (strong) 80L else 40L, amp(200))
            } else if (strong) {
                VibrationEffect.createWaveform(longArrayOf(0, 140, 70, 140), intArrayOf(0, amp(255), 0, amp(255)), -1)
            } else {
                VibrationEffect.createOneShot(55L, amp(190))
            }
        } catch (e: Exception) { null }
        if (effect != null) {
            var alarmOk = false
            if (alarm) {
                try { v.vibrate(effect, alarmAttrs); alarmOk = true } catch (e: Exception) { }
            }
            try {
                v.vibrate(effect)
                return if (alarm) (if (alarmOk) "alarm+plain" else "plain(repli)") else "plain"
            } catch (e: Exception) { }
            if (alarmOk) return "alarm"
        }
        try { v.vibrate(if (strong) 200L else 60L); return "legacy" } catch (e: Exception) { }
        return "fail"
    }
}

/** Partie principale : mm:ss (ou h:mm:ss), ou « secondes seules » si l'option est activée. */
fun fmtMain(ms: Long): String {
    if (Settings.secMode) return (ms / 1000).toString()
    val s = (ms / 1000) % 60
    val m = (ms / 60000) % 60
    val h = ms / 3600000
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%02d:%02d", m, s)
}

/** Jamais plus que les dixièmes. */
fun fmtTenth(ms: Long): String = ".${(ms / 100) % 10}"
fun fmtFull(ms: Long): String = fmtMain(ms) + fmtTenth(ms)
