package com.example.chrono

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/** Bouton « Stop + Reset » de la notification (pastille du cadran) : arrête puis réinitialise, sans ouvrir l'appli. */
class StopResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Stopwatch.load(context)
        Stopwatch.stopAndReset(context)
    }
}

/** Alarme (inexacte) posée au démarrage si l'arrêt automatique est activé. */
class AutoStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Stopwatch.load(context)
        // Si l'alarme arrive un peu trop tôt (horloge murale vs monotone), enforceAutoStop ne fait rien : on la repose au lieu de la perdre
        Stopwatch.rescheduleAutoStop(context)
    }
}

/**
 * Frontière d'un segment (exercice / repos) : Segments.onAlarm détecte la frontière franchie, vibre UNE fois comme pour un tour (dédoublonnage
 * persistant, jamais de tour créé), puis pose l'alarme suivante.
 * POINT D'EXTENSION (départ random) : brancher Segments.boundaryListener (appelé par Segments.check à chaque nouvelle frontière, depuis cette
 * alarme comme depuis la boucle d'affichage) ; ne pas dupliquer la logique ici.
 */
class SegmentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Stopwatch.load(context)
        if (!Stopwatch.running || !Segments.enabled()) return
        Segments.onAlarm(context)
    }
}

/**
 * Service de premier plan LÉGER : ne fait rien d'autre que garder le processus (et donc ses alarmes) à l'abri de la mise en veille / de
 * l'arrêt par le système tant que le chrono tourne ET que les objectifs vibrent (démarré / arrêté par Segments.schedule / Segments.cancel).
 * Il reprend la notification « chrono en cours » (même id que Ongoing) pour ne pas en afficher une seconde.
 */
class GoalService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try { Stopwatch.load(this) } catch (e: Exception) { }
        var ok = false
        try {
            val n = Ongoing.build(this, System.currentTimeMillis() - Stopwatch.elapsed(), RandomStart.ongoingText()) ?: Ongoing.fallback(this)
            enterForeground(n)
            ok = true
        } catch (e: Exception) { }
        if (!ok) {
            try { enterForeground(Ongoing.fallback(this)); ok = true } catch (e: Exception) { }
        }
        if (!ok || !Segments.wantsService()) stopSelf()
        return START_NOT_STICKY
    }

    private fun enterForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) startForeground(Ongoing.ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(Ongoing.ID, n)
    }

    /** Appli fermée depuis les applis récentes alors que le service la gardait en vie : même règle que d'habitude, stop + reset (séance enregistrée). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            Stopwatch.load(this)
            if (Stopwatch.running || RandomStart.active) Stopwatch.stopAndReset(this, null, true)
        } catch (e: Exception) { }
        try { stopSelf() } catch (e: Exception) { }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Service détruit en pleine séquence de départ random (anormal) : séquence annulée, sons stoppés, volume restauré
        try { RandomStart.onServiceDestroyed(this) } catch (e: Exception) { }
        // Chrono toujours en marche (objectifs désactivés en cours de route) : on garde la pastille ; sinon on la retire.
        try {
            stopForeground(if (Stopwatch.running) Service.STOP_FOREGROUND_DETACH else Service.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) { }
        super.onDestroy()
    }
}

/** Alarme du coup de feu du départ random (filet de sécurité du minuteur en processus ; sans effet si la séquence n'est plus en attente). */
class RandomStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Stopwatch.load(context)
        RandomStart.onAlarm(context)
    }
}

/**
 * Sentinelle « arrêt forcé » : l'alarme qui la retient est lointaine et ne sonne en pratique jamais ;
 * si elle sonne, on charge l'état (ce qui vérifie l'arrêt forcé) et on la repose.
 */
class SentinelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Stopwatch.load(context)
        if (Stopwatch.running) Stopwatch.armSentinel(context)
    }
}
