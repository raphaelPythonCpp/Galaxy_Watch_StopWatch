package com.example.chrono

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountUpTimeReference
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import java.time.Instant

class StopwatchComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.SHORT_TEXT) build(754_000L, false) else null

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null
        Stopwatch.load(this)
        return build(Stopwatch.elapsed(), Stopwatch.running)
    }

    private fun build(elapsed: Long, running: Boolean): ComplicationData {
        // En marche, c'est le cadran qui affiche le temps en direct ; à l'arrêt, valeur figée.
        val text: ComplicationText = if (running) {
            TimeDifferenceComplicationText.Builder(
                TimeDifferenceStyle.STOPWATCH,
                CountUpTimeReference(Instant.ofEpochMilli(System.currentTimeMillis() - elapsed))
            ).build()
        } else {
            PlainComplicationText.Builder(fmtMain(elapsed)).build()
        }
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return ShortTextComplicationData.Builder(
            text, PlainComplicationText.Builder("Chronomètre").build()
        ).setTapAction(tap).build()
    }
}
