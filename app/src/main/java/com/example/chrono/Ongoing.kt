package com.example.chrono

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status

/** Pastille « chrono en cours » sur le cadran : le système affiche et fait avancer le temps, sans réveiller l'appli. */
object Ongoing {
    private const val CH = "chrono_running"
    private const val ID = 4242

    fun show(c: Context, startRefMs: Long) {
        try {
            val app = c.applicationContext
            val nm = app.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CH, "Chrono en cours", NotificationManager.IMPORTANCE_LOW)
            )
            val pi = PendingIntent.getActivity(
                app, 0,
                Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE
            )
            val stopPi = PendingIntent.getBroadcast(
                app, 1, Intent(app, StopResetReceiver::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            val nb = NotificationCompat.Builder(app, CH)
                .addAction(R.drawable.ic_stopwatch, "Stop + Reset", stopPi)
                .setSmallIcon(R.drawable.ic_stopwatch)
                .setContentTitle("Chronomètre")
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
            val status = Status.Builder()
                .addTemplate("#time#")
                .addPart("time", Status.StopwatchPart(startRefMs))
                .build()
            val oa = OngoingActivity.Builder(app, ID, nb)
                .setStaticIcon(R.drawable.ic_stopwatch)
                .setTouchIntent(pi)
                .setStatus(status)
                .build()
            oa.apply(app)
            nm.notify(ID, nb.build())
        } catch (e: Exception) { }
    }

    fun hide(c: Context) {
        try { c.getSystemService(NotificationManager::class.java)?.cancel(ID) } catch (e: Exception) { }
    }
}
