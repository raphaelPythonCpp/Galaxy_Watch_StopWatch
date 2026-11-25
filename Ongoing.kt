package com.example.chrono

import android.app.Notification
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
    /** Id de la notification ; partagé avec GoalService (service de premier plan) pour ne jamais afficher deux pastilles. */
    const val ID = 4242

    private fun ensureChannel(app: Context) {
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH, S.NOTIF_CHANNEL.t(), NotificationManager.IMPORTANCE_LOW)
        )
    }

    /** Construit la notification + OngoingActivity (sans la publier) ; null en cas d'erreur. */
    fun build(c: Context, startRefMs: Long, text: String? = null): Notification? {
        return try {
            val app = c.applicationContext
            val ico = Logos.glyph[Settings.logo]
            ensureChannel(app)
            val pi = PendingIntent.getActivity(
                app, 0,
                Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE
            )
            val stopPi = PendingIntent.getBroadcast(
                app, 1, Intent(app, StopResetReceiver::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            val nb = NotificationCompat.Builder(app, CH)
                .addAction(ico, S.NOTIF_STOP_RESET.t(), stopPi)
                .setSmallIcon(ico)
                .setContentTitle(S.NOTIF_TITLE.t())
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
            // text != null : attente figée d'un départ random (pas de chrono qui avance sur le cadran)
            val status = if (text != null) {
                Status.Builder().addTemplate("#t#").addPart("t", Status.TextPart(text)).build()
            } else {
                Status.Builder().addTemplate("#time#").addPart("time", Status.StopwatchPart(startRefMs)).build()
            }
            val oa = OngoingActivity.Builder(app, ID, nb)
                .setStaticIcon(ico)
                .setTouchIntent(pi)
                .setStatus(status)
                .build()
            oa.apply(app)
            nb.build()
        } catch (e: Exception) {
            null
        }
    }

    /** Notification minimale de secours (le service de premier plan en exige toujours une). */
    fun fallback(c: Context): Notification {
        val app = c.applicationContext
        try { ensureChannel(app) } catch (e: Exception) { }
        return NotificationCompat.Builder(app, CH)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(S.NOTIF_TITLE.t())
            .setOngoing(true)
            .build()
    }

    fun show(c: Context, startRefMs: Long, text: String? = null) {
        try {
            val app = c.applicationContext
            val nm = app.getSystemService(NotificationManager::class.java) ?: return
            val n = build(c, startRefMs, text) ?: return
            nm.notify(ID, n)
        } catch (e: Exception) { }
    }

    fun hide(c: Context) {
        try { c.getSystemService(NotificationManager::class.java)?.cancel(ID) } catch (e: Exception) { }
    }
}
