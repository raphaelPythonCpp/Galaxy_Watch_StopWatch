package com.example.chrono

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Le nom de l'icône dans la liste des applis suit la langue choisie, via trois alias de lancement (un seul actif). */
object Launcher {
    fun apply(c: Context, lang: Lang) {
        try {
            val pm = c.packageManager
            val all = listOf(Lang.FR to "LaunchFr", Lang.EN to "LaunchEn", Lang.ZH to "LaunchZh")
            // on active d'abord le nouveau, puis on désactive les autres
            all.filter { it.first == lang }.forEach { set(pm, it.second, true) }
            all.filter { it.first != lang }.forEach { set(pm, it.second, false) }
        } catch (e: Exception) { }
    }

    private fun set(pm: PackageManager, name: String, on: Boolean) {
        pm.setComponentEnabledSetting(
            ComponentName("com.example.chrono", "com.example.chrono.$name"),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
