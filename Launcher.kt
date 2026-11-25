package com.example.chrono

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

/** L'icône de l'appli = l'alias de lancement actif (un seul sur 10). Le nom reste « Super StopWatch ». */
object Launcher {
    fun apply(c: Context, index: Int) {
        try {
            val pm = c.packageManager
            for (i in 0 until Logos.count) {
                if (i == index) set(pm, i, true)
            }
            for (i in 0 until Logos.count) {
                if (i != index) set(pm, i, false)
            }
        } catch (e: Exception) { }
    }

    private fun set(pm: PackageManager, i: Int, on: Boolean) {
        pm.setComponentEnabledSetting(
            ComponentName("com.example.chrono", "com.example.chrono.Logo%02d".format(i + 1)),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    /** Demande au tile et à la complication de se redessiner avec le nouveau logo. */
    fun refresh(c: Context) {
        val app = c.applicationContext
        try { TileService.getUpdater(app).requestUpdate(StopwatchTileService::class.java) } catch (e: Exception) { }
        try {
            ComplicationDataSourceUpdateRequester
                .create(app, ComponentName(app, StopwatchComplicationService::class.java))
                .requestUpdateAll()
        } catch (e: Exception) { }
    }
}
