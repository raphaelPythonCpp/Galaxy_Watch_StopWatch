package com.example.chrono

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountUpTimeReference
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import java.time.Instant

class StopwatchComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? = when (type.also { Settings.load(this) }) {
        ComplicationType.SHORT_TEXT -> shortText(754_000L, false)
        ComplicationType.MONOCHROMATIC_IMAGE -> iconData()
        else -> null
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        Stopwatch.load(this)
        Settings.load(this)
        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> shortText(Stopwatch.elapsed(), Stopwatch.running)
            ComplicationType.MONOCHROMATIC_IMAGE -> iconData()
            else -> null
        }
    }

    private fun icon(): MonochromaticImage {
        Settings.load(this)
        return MonochromaticImage.Builder(Icon.createWithResource(this, Logos.glyph[Settings.logo])).build()
    }

    private fun tap(): PendingIntent =
        PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    private fun desc() = PlainComplicationText.Builder(S.COMP_DESC.t()).build()

    private fun iconData(): ComplicationData =
        MonochromaticImageComplicationData.Builder(icon(), desc()).setTapAction(tap()).build()

    private fun shortText(elapsed: Long, running: Boolean): ComplicationData {
        // En marche, c'est le cadran qui affiche le temps en direct ; à l'arrêt, valeur figée.
        val text: ComplicationText = if (running) {
            TimeDifferenceComplicationText.Builder(
                TimeDifferenceStyle.STOPWATCH,
                CountUpTimeReference(Instant.ofEpochMilli(System.currentTimeMillis() - elapsed))
            ).build()
        } else {
            PlainComplicationText.Builder(fmtMain(elapsed)).build()
        }
        return ShortTextComplicationData.Builder(text, desc())
            .setMonochromaticImage(icon())
            .setTapAction(tap())
            .build()
    }
}
