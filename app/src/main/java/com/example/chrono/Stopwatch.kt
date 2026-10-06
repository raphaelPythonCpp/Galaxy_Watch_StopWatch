package com.example.chrono

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.ApplicationExitInfo
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.VibrationEffect
import android.os.Vibrator
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
    private var startedAt = 0L
    private var loaded = false
    private var quiet = false
    /** true : tours stockés dans un fichier en ajout seul (mode éco) ; false : dans les préférences. */
    private var lapsInFile = false

    private fun now() = System.currentTimeMillis()
    private fun prefs(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("stopwatch", Context.MODE_PRIVATE)
    private fun lapFile(c: Context) = File(c.applicationContext.filesDir, "laps.log")
    private fun limitMs(): Long = Settings.autoStopMin * 60_000L
    private fun lapsString() = laps.joinToString(";") { "${it.index},${it.lapTime},${it.total}" }

    /** Temps écoulé ; plafonné à la limite d'arrêt automatique si elle est active. */
    fun elapsed(): Long {
        val raw = (accumulated + if (running) now() - startedAt else 0L).coerceAtLeast(0L)
        val lim = limitMs()
        return if (running && lim > 0L) minOf(raw, lim) else raw
    }

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        Settings.load(c)
        try {
            val p = prefs(c)
            running = p.getBoolean("run", false)
            accumulated = p.getLong("acc", 0L)
            startedAt = p.getLong("start", 0L)
            nonce = p.getInt("nonce", 0)
            lapsInFile = p.getBoolean("lapsFile", false)
            laps.clear()
            if (lapsInFile) replayFile(c)
            else (p.getString("laps", "") ?: "").split(';').filter { it.isNotEmpty() }.forEach {
                val f = it.split(',')
                laps.add(Lap(f[0].toInt(), f[1].toLong(), f[2].toLong()))
            }
        } catch (e: Exception) {
            running = false; accumulated = 0L; startedAt = 0L; laps.clear(); lapsInFile = false
        }
        checkForcedStop(c)
        enforceAutoStop(c)
        if (running) Segments.schedule(c)
    }

    // ------------------------------------------------------------ arrêt forcé / arrêt automatique

    /**
     * « Forcer l'arrêt » (Paramètres > Applications) tue le processus sans laisser l'appli réagir.
     * Au lancement suivant, on lit le motif de fin du dernier processus : si c'était une demande de l'utilisateur
     * alors que le chrono tournait, on fait « stop puis reset » à l'instant de l'arrêt.
     */
    private fun checkForcedStop(c: Context) {
        try {
            val am = c.getSystemService(ActivityManager::class.java) ?: return
            val infos = am.getHistoricalProcessExitReasons(c.packageName, 0, 5)
            val newest = infos.maxByOrNull { it.timestamp } ?: return
            val p = prefs(c)
            if (newest.timestamp <= p.getLong("exitSeen", 0L)) return
            p.edit().putLong("exitSeen", newest.timestamp).apply()
            if (newest.reason == ApplicationExitInfo.REASON_USER_REQUESTED &&
                running && newest.timestamp > startedAt
            ) {
                stopAndReset(c, newest.timestamp, true)
            }
        } catch (e: Exception) { }
    }

    private fun alarmPi(c: Context): PendingIntent {
        val app = c.applicationContext
        return PendingIntent.getBroadcast(
            app, 2, Intent(app, AutoStopReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun scheduleAutoStop(c: Context) {
        val lim = limitMs()
        if (lim <= 0L || !running) return
        try {
            val at = now() + (lim - elapsed()).coerceAtLeast(0L)
            c.applicationContext.getSystemService(AlarmManager::class.java)
                ?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmPi(c))
        } catch (e: Exception) { }
    }

    private fun cancelAutoStop(c: Context) {
        try { c.applicationContext.getSystemService(AlarmManager::class.java)?.cancel(alarmPi(c)) }
        catch (e: Exception) { }
    }

    /** Arrête le chrono s'il a atteint la limite d'arrêt automatique (temps figé exactement à la limite). */
    fun enforceAutoStop(c: Context) {
        val lim = limitMs()
        if (!running || lim <= 0L) return
        val raw = accumulated + (now() - startedAt)
        if (raw >= lim) {
            accumulated = lim; running = false; startedAt = 0L
            save(c, true)
            Ongoing.hide(c)
            cancelAutoStop(c)
            Segments.cancel(c)
            buzz(c, true)
        }
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
            .putLong("start", startedAt)
            .putInt("nonce", nonce)
            .putBoolean("lapsFile", lapsInFile)
        if (!lapsInFile) e.putString("laps", lapsString())
        e.apply()
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

    fun start(c: Context) {
        if (running) return
        startedAt = now(); running = true
        buzz(c, true); save(c, true)
        Ongoing.show(c, startedAt - accumulated)
        scheduleAutoStop(c)
        Segments.schedule(c)
    }

    fun stop(c: Context) {
        if (!running) return
        accumulated += now() - startedAt; running = false
        buzz(c, true); save(c, true)
        Ongoing.hide(c)
        cancelAutoStop(c)
        Segments.cancel(c)
    }

    fun lap(c: Context) {
        if (!running) return
        syncLapStore(c)
        val t = elapsed()
        val last = laps.firstOrNull()?.total ?: 0L
        val lap = Lap(laps.size + 1, t - last, t)
        laps.add(0, lap)
        writeLapLine(c, "L,${lap.index},${lap.lapTime},${lap.total}")
        buzz(c, false)
    }

    /** Retire le dernier tour enregistré (le chrono continue). */
    fun undoLap(c: Context) {
        if (laps.isEmpty()) return
        syncLapStore(c)
        laps.removeAt(0)
        writeLapLine(c, "U")
        buzz(c, false)
    }

    /** Remise à zéro (chrono arrêté) : la séance est ajoutée à l'historique. */
    fun reset(c: Context, vibrate: Boolean = true) = finishReset(c, vibrate, now())

    /** Arrête puis remet à zéro, comme un Reset après un Stop. `at` = instant de l'arrêt. */
    fun stopAndReset(c: Context, at: Long = now(), silent: Boolean = false) {
        if (running) {
            accumulated += (at - startedAt).coerceAtLeast(0L)
            running = false
        }
        finishReset(c, !silent, at)
    }

    private fun finishReset(c: Context, vibrate: Boolean, ts: Long) {
        if (!running && accumulated > 0L) {
            History.add(c, Session(ts, accumulated, laps.reversed().map { it.lapTime }))
        }
        running = false; accumulated = 0L; startedAt = 0L; laps.clear()
        try { lapFile(c).delete() } catch (e: Exception) { }
        lapsInFile = false
        if (vibrate) buzz(c, true)
        save(c, true)
        Ongoing.hide(c)
        cancelAutoStop(c)
        Segments.cancel(c)
    }

    fun toggle(c: Context) { if (running) stop(c) else start(c) }
    /** Bouton physique : tour si en marche, sinon start. */
    fun primary(c: Context) { if (running) lap(c) else start(c) }

    /** Frontière de segment : 0 = fin d'exercice (2 impulsions), 1 = début de repos (1 longue), 2 = fin de repos (3 courtes). */
    fun buzzSegment(c: Context, type: Int) {
        try {
            Settings.load(c)
            val pct = Settings.vibePct
            if (pct <= 0.05f) return
            val v = c.getSystemService(Vibrator::class.java) ?: return
            fun amp(base: Int) = (base * pct / 100f).roundToInt().coerceIn(1, 255)
            val effect = if (Settings.eco) {
                VibrationEffect.createOneShot(if (type == 1) 300L else 100L, amp(200))
            } else when (type) {
                1 -> VibrationEffect.createOneShot(450L, amp(255))
                2 -> VibrationEffect.createWaveform(
                    longArrayOf(0, 90, 60, 90, 60, 90), intArrayOf(0, amp(255), 0, amp(255), 0, amp(255)), -1
                )
                else -> VibrationEffect.createWaveform(
                    longArrayOf(0, 120, 80, 120), intArrayOf(0, amp(255), 0, amp(255)), -1
                )
            }
            v.vibrate(effect)
        } catch (e: Exception) { }
    }

    /** strong = start/stop/reset ; sinon tour. Intensité globale réglable (0 = aucune vibration). */
    fun buzz(c: Context, strong: Boolean) {
        try {
            Settings.load(c)
            val pct = Settings.vibePct
            if (pct <= 0.05f) return
            val v = c.getSystemService(Vibrator::class.java) ?: return
            fun amp(base: Int) = (base * pct / 100f).roundToInt().coerceIn(1, 255)
            val effect = if (Settings.eco) {
                VibrationEffect.createOneShot(if (strong) 80 else 40, amp(200))
            } else if (strong) {
                VibrationEffect.createWaveform(longArrayOf(0, 140, 70, 140), intArrayOf(0, amp(255), 0, amp(255)), -1)
            } else {
                VibrationEffect.createOneShot(55, amp(190))
            }
            v.vibrate(effect)
        } catch (e: Exception) { }
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
