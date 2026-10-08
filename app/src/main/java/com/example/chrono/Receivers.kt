package com.example.chrono

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

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
        Stopwatch.enforceAutoStop(context)
    }
}

/** Frontière d'un segment (exercice / repos) : vibration comme pour un tour (si activée), puis alarme suivante. */
class SegmentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Stopwatch.load(context)
        if (!Stopwatch.running || !Segments.enabled()) return
        if (Settings.trackVibe) Stopwatch.buzz(context, false)   // vibration identique à celle d'un tour
        Segments.schedule(context)
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
