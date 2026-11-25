package com.example.chrono

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * DÉPART RANDOM (v14). Le début de chaque répétition d'exercice (la 1re de la séance incluse, pas les répétitions de repos) se fait au COUP DE FEU :
 * « À vos marques » (voix) -> « Prêts » (voix) -> attente aléatoire -> coup de feu. Instant du tir = fin de « Prêts » + random(min, max).
 *
 * MACHINE D'ÉTATS (persistée dans les préférences « randstart » ; tout se passe sur le thread principal) :
 *   IDLE -> MARKS (son « À vos marques ») -> READY (son « Prêts ») -> WAIT (délai) -> GO (coup de feu joué, chrono déjà (re)parti) -> IDLE.
 *   waiting = MARKS..WAIT : l'écran remplace le chrono par le texte, le bouton ANNULE. active = MARKS..GO : service + wake lock gardés.
 *
 * DÉCLENCHEURS
 *   - 1re répétition : Stopwatch.start() -> tryStart() (séance neuve : temps 0, aucun tour). Une reprise après pause démarre normalement.
 *   - Répétition suivante : Segments.boundaryListener (frontière de fin de segment suivie d'une répétition NON repos, Segments.isExerciseStart).
 *
 * CHRONO PENDANT L'ATTENTE
 *   - randFreeze ON  : le temps est FIGÉ exactement à l'instant de la frontière (Stopwatch.freezeAt : running interne = faux, accumulated = frontière).
 *     Au tir : reprise, lap automatique (sauf à 0:00), interaction d'arrêt auto, vibration de départ, objectifs re-synchronisés (aucun décalage nécessaire).
 *   - randFreeze OFF : le chrono continue ; au tir, lap automatique et décalage explicite des objectifs (Segments.addRandShift = retard réel
 *     entre la frontière et le tir). 1re répétition : le chrono démarre à l'appui, la frontière de référence est 0.
 *
 * GARDE EN VIE (écran éteint) : wake lock PARTIEL <= 110 s (libéré à coup sûr), service de premier plan GoalService pendant toute la séquence
 * (Segments.refreshService), minuteur en processus (Handler) + alarme (AlarmManager via Segments.armExact -> RandomStartReceiver) en filet.
 * Un redémarrage du processus pendant l'attente est détecté à load() : l'attente est annulée proprement (chrono arrêté, volume restauré).
 */
object RandomStart {
    const val IDLE = 0
    const val MARKS = 1
    const val READY = 2
    const val WAIT = 3
    const val GO = 4

    private const val WAKE_MS = 110_000L          // wake lock <= 2 min
    private const val LATE_MS = 5000L             // frontière détectée plus de 5 s après son instant : pas de départ (on rouvre l'appli longtemps après)

    var state by mutableIntStateOf(IDLE)
        private set

    /** Attente en cours (marques / prêts / délai) : le chrono central est remplacé par un texte, le bouton annule. */
    val waiting: Boolean get() = state == MARKS || state == READY || state == WAIT
    /** Séquence en cours (jusqu'à la fin du son du coup de feu) : service de premier plan et wake lock gardés. */
    val active: Boolean get() = state != IDLE

    /** Départ random utilisable : réglage ON et suivi avancé actif. */
    fun enabled(): Boolean = Settings.track && Settings.randStart

    private var frozen = true
    private var first = false
    private var shotAt = 0L           // heure murale du coup de feu
    private var boundT = 0L           // temps de chrono de la frontière de référence
    private var appCtx: Context? = null
    private var wake: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var player: SoundPlayer? = null
    private var previewPlayer: SoundPlayer? = null

    private val fireR = Runnable { appCtx?.let { fire(it) } }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("randstart", Context.MODE_PRIVATE)

    private fun persist(c: Context) {
        try {
            prefs(c).edit().putInt("st", state).putBoolean("frozen", frozen).putBoolean("first", first)
                .putLong("shotAt", shotAt).putLong("bT", boundT).commit()
        } catch (e: Exception) { }
    }

    private fun setState(c: Context, s: Int) {
        state = s
        persist(c)
    }

    private fun sp(c: Context): SoundPlayer = player ?: SoundPlayer(c.applicationContext).also { player = it }

    /** Texte de la pastille / notification pendant une attente FIGÉE ; null sinon (chrono qui avance). */
    fun ongoingText(): String? = if (waiting && frozen) S.RS_NOTIF.t() else null

    // ------------------------------------------------------------ chargement (chaque point d'entrée, via Stopwatch.load)

    /** Branche l'écouteur de frontières, rétablit un volume resté modifié, annule une attente interrompue par la mort du processus. */
    fun load(c: Context) {
        val app = c.applicationContext
        appCtx = app
        Segments.boundaryListener = { ctx, j, vibrated -> onBoundary(ctx, j, vibrated) }
        Segments.loadRandShift(app)
        try { AlarmVolume.restorePending(app) } catch (e: Exception) { }
        val p = prefs(app)
        val st = try { p.getInt("st", IDLE) } catch (e: Exception) { IDLE }
        if (st == IDLE || state != IDLE) return
        // Séquence interrompue (processus tué) : retour à un état sain
        try { p.edit().putInt("st", IDLE).commit() } catch (e: Exception) { }
        cancelAlarm(app)
        releaseWake()
        // GO = le coup de feu était déjà parti (chrono légitimement en marche) : on ne l'arrête pas
        if (st != GO) Stopwatch.haltForCancel(app, false)        // chrono arrêté (s'il tournait pendant l'attente) ; notification retirée ; aucune vibration
        Segments.refreshService(app)
    }

    // ------------------------------------------------------------ déclenchement

    /** Frontière traitée par Segments.check : début d'une répétition d'exercice suivie d'un départ random. */
    private fun onBoundary(c: Context, j: Int, vibrated: Boolean) {
        if (!enabled() || active || !Stopwatch.running) return
        if (!Segments.isExerciseStart(j)) return
        val bT = Segments.boundaryTime(j)
        if (Stopwatch.elapsed() - bT > LATE_MS) return
        begin(c, false, bT)
    }

    /**
     * Appelé par Stopwatch.start : true si le départ random prend le relais (le chrono ne démarre pas à l'appui, ou démarre sans tir
     * s'il n'est pas figé). Seulement pour une séance neuve (temps 0, aucun tour) ; sinon false : démarrage normal.
     */
    fun tryStart(c: Context): Boolean {
        if (!enabled() || active) return false
        if (Stopwatch.accumulated != 0L || Stopwatch.laps.isNotEmpty()) return false
        val app = c.applicationContext
        Segments.resetProgress(app)                // séance neuve : aucune frontière traitée, décalage du départ random remis à 0
        begin(app, true, 0L)
        return true
    }

    private fun begin(c: Context, isFirst: Boolean, bT: Long) {
        val app = c.applicationContext
        appCtx = app
        frozen = Settings.randFreeze
        first = isFirst
        boundT = bT
        shotAt = 0L
        setState(app, MARKS)
        acquireWake(app)
        Segments.refreshService(app)               // GoalService : garde le processus (et donc le son) en vie, écran éteint
        try {
            if (isFirst) {
                if (!frozen) Stopwatch.startImmediately(app) else Stopwatch.publish(app)
            } else if (frozen) {
                Stopwatch.freezeAt(app, bT)
            }
        } catch (e: Exception) { }
        playMarks(app)
    }

    // ------------------------------------------------------------ séquence

    private fun playMarks(c: Context) {
        try {
            sp(c).play(Sounds.res(0, Settings.randMarksSel), Settings.randVolPct) { onMarksDone(c) }
        } catch (e: Exception) { onMarksDone(c) }
    }

    private fun onMarksDone(c: Context) {
        if (state != MARKS) return
        setState(c, READY)
        try {
            sp(c).play(Sounds.res(1, Settings.randReadySel), Settings.randVolPct) { onReadyDone(c) }
        } catch (e: Exception) { onReadyDone(c) }
    }

    private fun onReadyDone(c: Context) {
        if (state != READY) return
        // Le coup de feu est CHARGÉ MAINTENANT (avant de calculer l'instant du tir) : au tir, il ne reste que start()
        try { sp(c).prepare(Sounds.res(2, Settings.randShotSel), Settings.randVolPct) } catch (e: Exception) { }
        val mn = Settings.randMinSec * 1000L
        val mx = Settings.randMaxSec * 1000L
        val delay = if (mx > mn) mn + kotlin.random.Random.nextLong(mx - mn + 1L) else mn
        shotAt = System.currentTimeMillis() + delay
        setState(c, WAIT)
        armAlarm(c, shotAt)
        handler.removeCallbacks(fireR)
        handler.postDelayed(fireR, delay)
    }

    /** Alarme de secours (RandomStartReceiver) : tire si l'attente est bien terminée. */
    fun onAlarm(c: Context) {
        if (state != WAIT) return
        if (System.currentTimeMillis() >= shotAt - 300L) fire(c)
    }

    /** COUP DE FEU. Idempotent (minuteur et alarme peuvent arriver tous les deux). */
    private fun fire(c: Context) {
        if (state != WAIT) return
        val app = c.applicationContext
        // CHEMIN CRITIQUE (le chrono doit démarrer au même instant que le son, comme en athlétisme : début du signal) :
        // 1) état GO en mémoire seulement (rapide) ; 2) start() du son déjà préparé ; 3) horodatage du chrono (1res lignes de randomShot).
        // Tout le reste (écriture disque, alarmes, notification) vient APRÈS, sans retarder ni le son ni le chrono.
        state = GO
        var started = false
        try { started = sp(app).startPrepared { if (state == GO) finish(app) } } catch (e: Exception) { }
        var t = -1L
        try { t = Stopwatch.randomShot(app, frozen) } catch (e: Exception) { }
        handler.removeCallbacks(fireR)
        cancelAlarm(app)
        persist(app)                                  // état GO sur disque
        if (t < 0L) {                                 // chrono arrêté entre-temps : aucun tir
            if (started) { try { sp(app).stop() } catch (e: Exception) { } }
            finish(app)
            return
        }
        try {
            if (!frozen) Segments.addRandShift(app, t - boundT)    // retard réel : les objectifs suivants sont décalés d'autant
            Segments.resync(app)
            Segments.schedule(app)
        } catch (e: Exception) { }
        if (!started) {                               // repli : son non préparé (échec de chargement) -> lecture classique (volume 0 = silence)
            try {
                sp(app).play(Sounds.res(2, Settings.randShotSel), Settings.randVolPct) { if (state == GO) finish(app) }
            } catch (e: Exception) { finish(app) }
        }
    }

    /** Fin normale de la séquence (coup de feu joué). */
    private fun finish(c: Context) {
        if (state == IDLE) return
        setState(c, IDLE)
        releaseWake()
        Segments.refreshService(c)
    }

    /**
     * Annule la séquence : sons stoppés (volume restauré), minuteur / alarme / wake lock libérés. haltChrono = true (bouton, réglage OFF) :
     * si l'on attendait, le chrono est arrêté (ou reste arrêté), aucun tir. false : l'appelant arrête / remet déjà le chrono à zéro.
     */
    fun cancel(c: Context, haltChrono: Boolean) {
        if (state == IDLE) return
        val app = c.applicationContext
        val wasWaiting = waiting
        handler.removeCallbacks(fireR)
        cancelAlarm(app)
        try { player?.stop() } catch (e: Exception) { }
        setState(app, IDLE)
        releaseWake()
        try { AlarmVolume.restoreAll(app) } catch (e: Exception) { }
        if (haltChrono && wasWaiting) {
            try { Stopwatch.haltForCancel(app) } catch (e: Exception) { }
        }
        Segments.refreshService(app)
    }

    /** GoalService détruit en pleine séquence : on annule tout (jamais de son ni de wake lock orphelin). */
    fun onServiceDestroyed(c: Context) {
        if (active) cancel(c, true)
    }

    // ------------------------------------------------------------ aperçu des sons (écran Suivi avancé)

    /** Joue le son choisi (kind 0 marques, 1 prêts, 2 tir) au volume du curseur. Ignoré pendant une séquence. */
    fun preview(c: Context, kind: Int, n: Int) {
        if (active) return
        try {
            val pp = previewPlayer ?: SoundPlayer(c.applicationContext).also { previewPlayer = it }
            pp.play(Sounds.res(kind, n), Settings.randVolPct) { }
        } catch (e: Exception) { }
    }

    /** Arrête l'aperçu (activité arrêtée / détruite) et restaure le volume. */
    fun stopPreview() {
        try { previewPlayer?.stop() } catch (e: Exception) { }
    }

    // ------------------------------------------------------------ wake lock et alarme

    private fun acquireWake(c: Context) {
        releaseWake()
        try {
            val pm = c.getSystemService(PowerManager::class.java) ?: return
            val w = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "chrono:randstart")
            w.setReferenceCounted(false)
            w.acquire(WAKE_MS)                       // se libère seul au bout de 110 s même si tout le reste échoue
            wake = w
        } catch (e: Exception) { }
    }

    private fun releaseWake() {
        try { wake?.let { if (it.isHeld) it.release() } } catch (e: Exception) { }
        wake = null
    }

    private fun alarmPi(c: Context): PendingIntent {
        val app = c.applicationContext
        return PendingIntent.getBroadcast(
            app, 8, Intent(app, RandomStartReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun armAlarm(c: Context, at: Long) {
        try { Segments.armExact(c, at, alarmPi(c), 6) } catch (e: Exception) { }
    }

    private fun cancelAlarm(c: Context) {
        try { c.applicationContext.getSystemService(android.app.AlarmManager::class.java)?.cancel(alarmPi(c)) } catch (e: Exception) { }
    }
}
