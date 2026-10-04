package com.example.chrono

import android.content.ComponentName
import android.content.Context
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
import java.util.Locale

data class Lap(val index: Int, val lapTime: Long, val total: Long)

/** État unique partagé par l'appli, le tile et la complication. */
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

    private fun now() = System.currentTimeMillis()
    private fun prefs(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("stopwatch", Context.MODE_PRIVATE)

    fun elapsed(): Long =
        (accumulated + if (running) now() - startedAt else 0L).coerceAtLeast(0L)

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        try {
            val p = prefs(c)
            running = p.getBoolean("run", false)
            accumulated = p.getLong("acc", 0L)
            startedAt = p.getLong("start", 0L)
            nonce = p.getInt("nonce", 0)
            laps.clear()
            (p.getString("laps", "") ?: "").split(';').filter { it.isNotEmpty() }.forEach {
                val f = it.split(',')
                laps.add(Lap(f[0].toInt(), f[1].toLong(), f[2].toLong()))
            }
        } catch (e: Exception) {
            running = false; accumulated = 0L; startedAt = 0L; laps.clear()
        }
    }

    /** notify = true seulement quand l'état start/stop/reset change (pas à chaque tour). */
    private fun save(c: Context, notify: Boolean) {
        val app = c.applicationContext
        prefs(app).edit()
            .putBoolean("run", running)
            .putLong("acc", accumulated)
            .putLong("start", startedAt)
            .putInt("nonce", nonce)
            .putString("laps", laps.joinToString(";") { "${it.index},${it.lapTime},${it.total}" })
            .apply()
        if (!notify) return
        try {
            ComplicationDataSourceUpdateRequester
                .create(app, ComponentName(app, StopwatchComplicationService::class.java))
                .requestUpdateAll()
        } catch (e: Exception) { }
        if (!quiet) {
            try {
                TileService.getUpdater(app).requestUpdate(StopwatchTileService::class.java)
            } catch (e: Exception) { }
        }
    }

    fun quietly(block: () -> Unit) {
        quiet = true
        try { block() } finally { quiet = false }
    }

    fun bumpNonce(c: Context) { nonce++; save(c, true) }

    fun start(c: Context) {
        if (running) return
        startedAt = now(); running = true
        buzz(c, true); save(c, true)
        Ongoing.show(c, startedAt - accumulated)
    }

    fun stop(c: Context) {
        if (!running) return
        accumulated += now() - startedAt; running = false
        buzz(c, true); save(c, true)
        Ongoing.hide(c)
    }

    fun lap(c: Context) {
        if (!running) return
        val t = elapsed()
        val last = laps.firstOrNull()?.total ?: 0L
        laps.add(0, Lap(laps.size + 1, t - last, t))
        buzz(c, false); save(c, false)
    }

    /** Retire le dernier tour enregistré (le chrono continue). */
    fun undoLap(c: Context) {
        if (laps.isEmpty()) return
        laps.removeAt(0)
        buzz(c, false); save(c, false)
    }

    fun reset(c: Context) {
        if (!running && accumulated > 0L) {
            History.add(c, Session(now(), accumulated, laps.reversed().map { it.lapTime }))
        }
        running = false; accumulated = 0L; startedAt = 0L; laps.clear()
        buzz(c, true); save(c, true)
        Ongoing.hide(c)
    }

    fun toggle(c: Context) { if (running) stop(c) else start(c) }
    /** Bouton physique : tour si en marche, sinon start. */
    fun primary(c: Context) { if (running) lap(c) else start(c) }

    /** strong = start/stop/reset ; sinon tour. */
    fun buzz(c: Context, strong: Boolean) {
        try {
            val v = c.getSystemService(Vibrator::class.java) ?: return
            Settings.load(c)
            val effect = if (Settings.eco) {
                VibrationEffect.createOneShot(if (strong) 80 else 40, 200)
            } else if (strong) {
                VibrationEffect.createWaveform(longArrayOf(0, 140, 70, 140), intArrayOf(0, 255, 0, 255), -1)
            } else {
                VibrationEffect.createOneShot(55, 190)
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
