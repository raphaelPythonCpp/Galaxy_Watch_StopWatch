package com.example.chrono

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Text
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Résultat d'un calibrage : statistique des réactions, référence, compensation appliquée (ms). */
data class CalibResult(val stat: Long, val ref: Long, val comp: Long, val median: Boolean)

/**
 * CALIBRAGE DE LA COMPENSATION AUDIO (v15). N coups de feu ; à chacun : « Prêt… », attente aléatoire [attente min, attente max] du départ
 * random, coup de feu (son et volume du départ, lecteur préparé d'avance), puis réaction (bouton physique du bas ou toucher). AUCUNE vibration.
 * réaction = instant de l'appui - instant du start() du son (horloge SystemClock.elapsedRealtime).
 * compensation = (médiane ou moyenne des réactions) - réaction de référence, bornée à [0, durée de la plus longue piste de tir], appliquée à randLat.
 *
 * ÉTATS : IDLE -> WAIT (son préparé, minuteur) -> LISTEN (coup de feu parti) -> PAUSE (réaction affichée) -> WAIT ... -> DONE.
 * Appui pendant WAIT = trop tôt : le même test recommence. Rien n'est modifié tant que le dernier test n'est pas terminé.
 * abort() (quitter, retour système, activité en pause, ambiant, écran éteint) : minuteurs retirés, son stoppé, volume restauré.
 * Tout se passe sur le thread principal.
 */
object Calibration {
    const val IDLE = 0
    const val WAIT = 1
    const val LISTEN = 2
    const val PAUSE = 3
    const val DONE = 4

    private const val PAUSE_MS = 1500L            // entre deux tests : la réaction (ou « trop tôt ») reste affichée
    private const val TIMEOUT_MS = 10_000L        // aucune réaction : on arrête (l'écran ne reste pas allumé indéfiniment)
    private const val DONE_GUARD_MS = 800L        // un double appui sur le dernier test ne doit pas fermer les résultats

    var state by mutableIntStateOf(IDLE)
        private set
    /** Nombre de tests de la série en cours. */
    var total by mutableIntStateOf(0)
        private set
    var tooEarly by mutableStateOf(false)
        private set
    /** Message à afficher sur l'écran d'accueil du calibrage (annulation, erreur). */
    var message by mutableStateOf<S?>(null)
        private set
    var result by mutableStateOf<CalibResult?>(null)
        private set
    val reactions = mutableStateListOf<Long>()

    /** Calibrage en cours : le bouton physique et le toucher servent de réaction. */
    val busy: Boolean get() = state == WAIT || state == LISTEN || state == PAUSE

    private var shotAt = 0L           // SystemClock.elapsedRealtime du start() du son
    private var doneAt = 0L
    private var armedAt = 0L          // SystemClock.uptimeMillis du début du test en cours
    private var appCtx: Context? = null
    private var player: SoundPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val fireR = Runnable { fire() }
    private val nextR = Runnable { arm() }
    private val timeoutR = Runnable { fail(S.CALIB_TIMEOUT) }

    /** Raison qui interdit de lancer un calibrage, ou null. */
    fun blocker(): S? = when {
        RandomStart.active -> S.CALIB_BUSY_RAND
        Stopwatch.running -> S.CALIB_BUSY_RUN
        Settings.randVolPct <= 0 -> S.CALIB_VOL0
        else -> null
    }

    /** Ouvre l'écran de calibrage (depuis le Suivi avancé), sans résultat précédent. */
    fun open() {
        abort()
        state = IDLE
        reactions.clear()
        result = null
        message = null
        Ui.screen = Screen.CALIB
    }

    /** Lance (ou relance) une série de tests. */
    fun start(c: Context) {
        if (busy || blocker() != null) return
        appCtx = c.applicationContext
        clearTimers()
        RandomStart.stopPreview()
        reactions.clear()
        result = null
        message = null
        tooEarly = false
        total = Settings.calibN.value.roundToInt().coerceIn(1, 20)
        arm()
    }

    /** Prépare le coup de feu et arme le minuteur aléatoire. */
    private fun arm() {
        val app = appCtx ?: return
        clearTimers()
        stopSound()                     // coup de feu précédent encore en cours : coupé (volume restauré avant d'être repris)
        if (RandomStart.active) { fail(S.CALIB_BUSY_RAND); return }
        if (Stopwatch.running) { fail(S.CALIB_BUSY_RUN); return }
        tooEarly = false
        state = WAIT
        armedAt = SystemClock.uptimeMillis()
        val p = player ?: SoundPlayer(app).also { player = it }
        val ready = try { p.prepare(Sounds.res(2, Settings.randShotSel), Settings.randVolPct) } catch (e: Exception) { false }
        if (!ready) { fail(S.CALIB_SOUND_ERR); return }
        val mn = Settings.randMinMs
        val mx = Settings.randMaxMs
        val delay = if (mx > mn) mn + kotlin.random.Random.nextLong(mx - mn + 1L) else mn
        handler.postDelayed(fireR, delay)
    }

    private fun fire() {
        if (state != WAIT) return
        val app = appCtx ?: return
        val pm = app.getSystemService(PowerManager::class.java)
        if (pm?.isInteractive == false) { abort(); return }
        val p = player ?: return
        val started = try { p.startPrepared { } } catch (e: Exception) { false }
        shotAt = SystemClock.elapsedRealtime()      // comme le départ random : horodatage juste après start()
        if (!started) { fail(S.CALIB_SOUND_ERR); return }
        state = LISTEN                              // rien ne change à l'écran : on réagit au son seulement
        handler.postDelayed(timeoutR, TIMEOUT_MS)
    }

    /** Appui de réaction. eventUptime = horodatage de l'événement (base SystemClock.uptimeMillis) : l'instant réel de l'appui. */
    fun press(eventUptime: Long) {
        if (eventUptime < armedAt) return           // doigt posé avant ce test (pendant la pause), relâché après : ignoré
        val age = SystemClock.uptimeMillis() - eventUptime
        val at = SystemClock.elapsedRealtime() - (if (age in 0L..2000L) age else 0L)
        if (state == WAIT) {
            early()
        } else if (state == LISTEN) {
            val r = at - shotAt
            if (r < 0L) early() else record(r)      // r < 0 : appui antérieur au tir, traité après lui
        }
    }

    /** Appui avant le tir : le même test recommence après une courte pause. */
    private fun early() {
        clearTimers()
        stopSound()
        tooEarly = true
        state = PAUSE
        handler.postDelayed(nextR, PAUSE_MS)
    }

    private fun record(ms: Long) {
        clearTimers()
        tooEarly = false
        reactions.add(ms)
        if (reactions.size >= total) {
            complete()                              // le son se termine seul, puis le volume est restauré
        } else {
            state = PAUSE
            handler.postDelayed(nextR, PAUSE_MS)
        }
    }

    /** Dernier test fait : calcule la compensation et l'applique à randLat. */
    private fun complete() {
        val app = appCtx ?: return
        val sorted = reactions.sorted()
        val n = sorted.size
        if (n == 0) return
        val median = Settings.calibMedian
        val stat: Long = if (median) {
            if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2] + 1L) / 2L
        } else {
            (sorted.sum().toDouble() / n).roundToLong()
        }
        val ref = Settings.calibRefMs
        val comp = (stat - ref).coerceIn(0L, Sounds.shotMaxMs)
        Settings.randLat.forceValue(comp.toDouble())
        Settings.persistQuiet(app)
        result = CalibResult(stat, ref, comp, median)
        doneAt = SystemClock.uptimeMillis()
        state = DONE
    }

    /** Résultats tout juste affichés : le bouton physique est ignoré un instant. */
    fun justFinished(): Boolean = state == DONE && SystemClock.uptimeMillis() - doneAt < DONE_GUARD_MS

    private fun fail(msg: S) {
        clearTimers()
        stopSound()
        reactions.clear()
        tooEarly = false
        state = IDLE
        message = msg
    }

    /** Annulation propre (idempotente). Une série en cours est abandonnée sans rien modifier ; des résultats affichés sont conservés. */
    fun abort() {
        clearTimers()
        stopSound()
        if (busy) {
            reactions.clear()
            tooEarly = false
            state = IDLE
            message = S.CALIB_CANCELLED
        }
    }

    private fun clearTimers() {
        handler.removeCallbacks(fireR)
        handler.removeCallbacks(nextR)
        handler.removeCallbacks(timeoutR)
    }

    /** Stoppe le son (joué ou seulement préparé) et restaure le volume. */
    private fun stopSound() {
        try { player?.stop() } catch (e: Exception) { }
    }
}

@Composable
private fun CalibButton(label: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 12.sp, color = fg, textAlign = TextAlign.Center) }
}

@Composable
private fun CalibText(text: String, size: Float, color: Color) {
    Text(text, fontSize = size.sp, color = color, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.86f))
}

/** Écran dédié du calibrage : consignes, tests, résultats. */
@Composable
fun CalibScreen() {
    val ctx = LocalContext.current
    val view = LocalView.current
    val eco = Settings.eco
    val pal = remember(Settings.rgb) { makePalette(Settings.rgb) }
    val accent = if (eco) Fg else pal.accent
    val onAcc = if (eco) Bg else pal.onAccent
    val busy = Calibration.busy
    val state = Calibration.state
    val res = Calibration.result
    val list = Calibration.reactions.toList()
    val block = Calibration.blocker()
    val quit: () -> Unit = {
        Calibration.abort()
        Ui.screen = Screen.TRACK
    }

    // Écran gardé allumé pendant les tests (indépendant du drapeau de fenêtre de l'affichage permanent)
    DisposableEffect(busy) {
        view.keepScreenOn = busy
        onDispose { view.keepScreenOn = false }
    }
    // Écran quitté (de quelque façon que ce soit) : annulation propre
    DisposableEffect(Unit) {
        onDispose { Calibration.abort() }
    }
    // Geste « retour » du système (le bouton physique, lui, passe par MainActivity.onKeyDown et ne vient pas ici)
    BackHandler { quit() }

    if (busy) {
        val pausedAfterReaction = state == Calibration.PAUSE && !Calibration.tooEarly
        val num = if (pausedAfterReaction) list.size else list.size + 1
        Box(Modifier.fillMaxSize()) {
            // Toute la surface = réaction. Instant retenu = celui où le doigt se pose, mais l'appui n'est compté qu'au relâchement et
            // seulement si le doigt n'a pas bougé : un balayage « retour » ne compte jamais. Le bouton Quitter, au-dessus, n'en fait pas partie.
            Column(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var tap = false
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break                    // geste repris ou annulé par le système
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                            change.consume()
                            if (!change.pressed) { tap = true; break }
                        }
                        if (tap) Calibration.press(down.uptimeMillis)
                    }
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CalibText(S.CALIB_TEST.t() + " " + num + " / " + Calibration.total, 12f, Dim)
                Spacer(Modifier.height(6.dp))
                if (state == Calibration.PAUSE) {
                    if (Calibration.tooEarly) CalibText(S.CALIB_EARLY.t(), 14f, accent)
                    else CalibText(fmtTime(list.lastOrNull() ?: 0L), 24f, accent)
                } else {
                    // Même affichage avant et après le tir : aucun indice visuel
                    Text(S.CALIB_READY.t(), fontSize = 24.sp, color = Fg, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(6.dp))
                CalibText(S.CALIB_HINT.t(), 10f, Dim)
            }
            CalibButton(
                S.CALIB_QUIT.t(), SurfaceBtn, Fg,
                Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).width(90.dp), quit
            )
        }
    } else if (state == Calibration.DONE && res != null) {
        ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item { CalibText(S.RAND_LAT.t(), 12f, Dim) }
            item { Text(fmtTime(res.comp), fontSize = 24.sp, color = accent, fontWeight = FontWeight.Bold) }
            item { CalibText(S.CALIB_APPLIED.t(), 11f, Fg) }
            item {
                CalibText((if (res.median) S.MEDIAN.t() else S.MEAN.t()) + " : " + fmtTime(res.stat), 12f, Fg)
            }
            item { CalibText(S.CALIB_REF.t() + " : " + fmtTime(res.ref), 12f, Fg) }
            item {
                CalibText(fmtTime(res.stat) + " − " + fmtTime(res.ref) + " → " + fmtTime(res.comp), 11f, Dim)
            }
            if (res.stat < res.ref) {
                item { CalibText(S.CALIB_CLAMP_LOW.t(), 11f, accent) }
            } else if (res.stat - res.ref > res.comp) {
                item { CalibText(S.CALIB_CLAMP_HIGH.t(), 11f, accent) }
            }
            item { CalibText(S.CALIB_REACTIONS.t(), 12f, Dim) }
            list.forEachIndexed { i, r ->
                item { Text((i + 1).toString() + " : " + fmtTime(r), fontSize = 12.sp, color = Fg, style = Tnum) }
            }
            if (block != null) {
                item { CalibText(block.t(), 11f, accent) }
            }
            item {
                CalibButton(S.CALIB_AGAIN.t(), if (block == null) accent else SurfaceBtn, if (block == null) onAcc else Dim,
                    Modifier.fillMaxWidth(0.8f)) { Calibration.start(ctx) }
            }
            item { CalibButton(S.CALIB_QUIT.t(), SurfaceBtn, Fg, Modifier.fillMaxWidth(0.8f), quit) }
        }
    } else {
        val msg = Calibration.message
        ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Text(S.CALIB_SECTION.t(), fontSize = 14.sp, color = accent, fontWeight = FontWeight.Bold) }
            if (msg != null) {
                item { CalibText(msg.t(), 11f, accent) }
            }
            if (block != null) {
                item { CalibText(block.t(), 12f, accent) }
            } else {
                item { CalibButton(S.CALIB_GO.t(), accent, onAcc, Modifier.fillMaxWidth(0.8f)) { Calibration.start(ctx) } }
            }
            item { CalibText(S.CALIB_INTRO.t(), 11f, Fg) }
            item {
                CalibText(
                    S.CALIB_N.t() + " : " + Settings.calibN.value.roundToInt() + " · " +
                        (if (Settings.calibMedian) S.MEDIAN.t() else S.MEAN.t()),
                    11f, Dim
                )
            }
            item { CalibText(S.CALIB_REF.t() + " : " + fmtTime(Settings.calibRefMs), 11f, Dim) }
            item {
                CalibText(
                    S.RAND_MIN.t() + " / " + S.RAND_MAX.t() + " : " + fmtTime(Settings.randMinMs) + " – " + fmtTime(Settings.randMaxMs),
                    11f, Dim
                )
            }
            item { CalibButton(S.CALIB_QUIT.t(), SurfaceBtn, Fg, Modifier.fillMaxWidth(0.8f), quit) }
        }
    }
}
