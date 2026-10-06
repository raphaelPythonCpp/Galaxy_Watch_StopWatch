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

/** Frontière d'un segment (exercice / repos) : vibration propre à chaque type, puis alarme suivante. */
class SegmentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Stopwatch.load(context)
        if (!Stopwatch.running || !Segments.enabled()) return
        Stopwatch.buzzSegment(context, intent.getIntExtra("type", 0))
        Segments.schedule(context)
    }
}
