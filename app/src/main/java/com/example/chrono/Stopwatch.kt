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

/** État unique partagé par l'appli, le tile et la complication. Sauvegardé à chaque action. */
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

    private fun save(c: Context) {
        val app = c.applicationContext
        prefs(app).edit()
            .putBoolean("run", running)
            .putLong("acc", accumulated)
            .putLong("start", startedAt)
            .putInt("nonce", nonce)
            .putString("laps", laps.joinToString(";") { "${it.index},${it.lapTime},${it.total}" })
            .apply()
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

    /** Pendant une requête de tile, on évite de redemander une mise à jour du tile. */
    fun quietly(block: () -> Unit) {
        quiet = true
        try { block() } finally { quiet = false }
    }

    fun bumpNonce(c: Context) { nonce++; save(c) }

    fun start(c: Context) {
        if (running) return
        startedAt = now(); running = true
        buzz(c, true); save(c)
    }

    fun stop(c: Context) {
        if (!running) return
        accumulated += now() - startedAt; running = false
        buzz(c, true); save(c)
    }

    fun lap(c: Context) {
        if (!running) return
        val t = elapsed()
        val last = laps.firstOrNull()?.total ?: 0L
        laps.add(0, Lap(laps.size + 1, t - last, t))
        buzz(c, false); save(c)
    }

    fun reset(c: Context) {
        running = false; accumulated = 0L; startedAt = 0L; laps.clear()
        buzz(c, false); save(c)
    }

    fun toggle(c: Context) { if (running) stop(c) else start(c) }
    /** Bouton physique : tour si en marche, sinon start. */
    fun primary(c: Context) { if (running) lap(c) else start(c) }
    /** Bouton de gauche : tour si en marche, sinon remise à zéro. */
    fun left(c: Context) { if (running) lap(c) else reset(c) }

    private fun buzz(c: Context, heavy: Boolean) {
        try {
            c.getSystemService(Vibrator::class.java)?.vibrate(
                VibrationEffect.createPredefined(
                    if (heavy) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_CLICK
                )
            )
        } catch (e: Exception) { }
    }
}

fun fmtMain(ms: Long): String {
    val s = (ms / 1000) % 60
    val m = (ms / 60000) % 60
    val h = ms / 3600000
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%02d:%02d", m, s)
}

fun fmtCs(ms: Long): String = String.format(Locale.ROOT, ".%02d", (ms / 10) % 100)
fun fmtFull(ms: Long): String = fmtMain(ms) + fmtCs(ms)
