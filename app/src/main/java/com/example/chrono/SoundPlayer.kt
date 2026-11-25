package com.example.chrono

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import kotlin.math.roundToInt

/** Ressources audio du départ random (res/raw). Références directes R.raw.* : le shrink de ressources ne les supprime donc pas. */
object Sounds {
    private val marks = arrayOf(
        intArrayOf(R.raw.marks_fr_1, R.raw.marks_fr_2, R.raw.marks_fr_3),
        intArrayOf(R.raw.marks_en_1, R.raw.marks_en_2, R.raw.marks_en_3),
        intArrayOf(R.raw.marks_zh_1, R.raw.marks_zh_2, R.raw.marks_zh_3)
    )
    private val ready = arrayOf(
        intArrayOf(R.raw.ready_fr_1, R.raw.ready_fr_2, R.raw.ready_fr_3),
        intArrayOf(R.raw.ready_en_1, R.raw.ready_en_2, R.raw.ready_en_3),
        intArrayOf(R.raw.ready_zh_1, R.raw.ready_zh_2, R.raw.ready_zh_3)
    )
    private val shots = intArrayOf(
        R.raw.shot_synth_1, R.raw.shot_synth_2, R.raw.shot_synth_3, R.raw.shot_user_1, R.raw.shot_user_2
    )

    /** kind 0 = « À vos marques » (selon la langue de l'appli), 1 = « Prêts » (idem), 2 = coup de feu ; n = 1..3 / 1..3 / 1..5. */
    fun res(kind: Int, n: Int): Int {
        val lang = Settings.lang.ordinal.coerceIn(0, 2)     // FR, EN, ZH
        return when (kind) {
            0 -> marks[lang][(n - 1).coerceIn(0, 2)]
            1 -> ready[lang][(n - 1).coerceIn(0, 2)]
            else -> shots[(n - 1).coerceIn(0, 4)]
        }
    }
}

/**
 * Volume du flux ALARME, partagé par tous les lecteurs. Compteur de détenteurs : le volume d'origine n'est mémorisé qu'au PREMIER détenteur
 * et n'est restauré qu'au DERNIER (jamais deux fois). La restauration est ignorée si l'utilisateur a modifié le volume entre-temps
 * (valeur courante différente de celle que nous avions réglée). Le volume d'origine est aussi persisté : si le processus est tué pendant
 * un son, restorePending() le rétablit au lancement suivant.
 */
object AlarmVolume {
    private var holders = 0
    private var saved = -1
    private var applied = -1

    private fun am(c: Context): AudioManager? =
        try { c.applicationContext.getSystemService(AudioManager::class.java) } catch (e: Exception) { null }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("sound", Context.MODE_PRIVATE)

    /** Règle le flux ALARME à pct % du maximum. Renvoie true si un détenteur a été enregistré (à relâcher exactement une fois avec release). */
    fun acquire(c: Context, pct: Int): Boolean {
        val m = am(c) ?: return false
        try {
            val max = m.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val min = m.getStreamMinVolume(AudioManager.STREAM_ALARM)
            val target = (max * pct.coerceIn(0, 100) / 100f).roundToInt().coerceIn(min, max)
            val orig = m.getStreamVolume(AudioManager.STREAM_ALARM)
            m.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
            val now = m.getStreamVolume(AudioManager.STREAM_ALARM)
            if (holders == 0) saved = orig
            applied = now
            holders++
            try { prefs(c).edit().putInt("saved", saved).putInt("applied", applied).commit() } catch (e: Exception) { }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /** Relâche un détenteur ; le dernier restaure le volume d'origine. */
    fun release(c: Context) {
        if (holders <= 0) return
        holders--
        if (holders == 0) restore(c, saved, applied)
    }

    /** Restauration forcée (annulation, destruction) : idempotente. */
    fun restoreAll(c: Context) {
        holders = 0
        if (saved >= 0) restore(c, saved, applied)
    }

    /** Au lancement : un volume resté modifié par un processus tué est rétabli (si personne ne l'a changé depuis). */
    fun restorePending(c: Context) {
        if (holders > 0 || saved >= 0) return
        try {
            val p = prefs(c)
            val s = p.getInt("saved", -1)
            val a = p.getInt("applied", -1)
            if (s >= 0) restore(c, s, a)
        } catch (e: Exception) { }
    }

    private fun restore(c: Context, s: Int, a: Int) {
        saved = -1; applied = -1
        try { prefs(c).edit().putInt("saved", -1).putInt("applied", -1).commit() } catch (e: Exception) { }
        if (s < 0) return
        try {
            val m = am(c) ?: return
            if (m.getStreamVolume(AudioManager.STREAM_ALARM) == a) m.setStreamVolume(AudioManager.STREAM_ALARM, s, 0)
        } catch (e: Exception) { }
    }
}

/**
 * Lecteur d'un son à la fois (MediaPlayer, AudioAttributes USAGE_ALARM + CONTENT_TYPE_SONIFICATION : haut-parleur de la montre, passe aussi
 * en arrière-plan). Tout se passe sur le thread principal. play() règle le volume du flux ALARME (AlarmVolume) puis le restaure à la fin du son,
 * à stop() ou en cas d'erreur. Le rappel onDone(ok) n'est PAS appelé si stop() / un nouveau play() a annulé ce son entre-temps.
 */
class SoundPlayer(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var mp: MediaPlayer? = null
    private var held = false
    private var gen = 0
    private var timeout: Runnable? = null

    private val attrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    val isPlaying: Boolean get() = mp != null

    /** Joue la ressource resId à volPct % du volume maximum du flux alarme. Volume 0 : silence (onDone(true) aussitôt, rien n'est joué). */
    fun play(resId: Int, volPct: Int, onDone: (Boolean) -> Unit) {
        stop()
        val g = gen
        if (volPct <= 0) {
            main.post { if (g == gen) onDone(true) }
            return
        }
        held = AlarmVolume.acquire(app, volPct)
        var player: MediaPlayer? = null
        try {
            player = MediaPlayer.create(app, resId, attrs, AudioManager.AUDIO_SESSION_ID_GENERATE)
        } catch (e: Exception) { }
        if (player == null) {
            end(g, false, onDone)
            return
        }
        mp = player
        try {
            player.setOnCompletionListener { end(g, true, onDone) }
            player.setOnErrorListener { _, _, _ -> end(g, false, onDone); true }
            player.start()
        } catch (e: Exception) {
            end(g, false, onDone)
            return
        }
        val dur = try { player.duration } catch (e: Exception) { 0 }
        val r = Runnable { end(g, false, onDone) }     // filet : le son ne se termine jamais (rappel perdu)
        timeout = r
        main.postDelayed(r, dur.coerceAtLeast(0) + 2500L)
    }

    /** Fin d'un son (normale ou erreur) : libère, restaure le volume, puis appelle onDone (via le thread principal, jamais en ré-entrance). */
    private fun end(g: Int, ok: Boolean, onDone: (Boolean) -> Unit) {
        if (g != gen) return
        gen++
        val g2 = gen
        cleanup()
        main.post { if (g2 == gen) onDone(ok) }
    }

    /** Arrête le son en cours (sans rappel) et restaure le volume. */
    fun stop() {
        gen++
        cleanup()
    }

    private fun cleanup() {
        timeout?.let { main.removeCallbacks(it) }
        timeout = null
        val p = mp
        mp = null
        if (p != null) {
            try { p.setOnCompletionListener(null); p.setOnErrorListener(null) } catch (e: Exception) { }
            try { p.stop() } catch (e: Exception) { }
            try { p.release() } catch (e: Exception) { }
        }
        if (held) {
            held = false
            AlarmVolume.release(app)
        }
    }
}
