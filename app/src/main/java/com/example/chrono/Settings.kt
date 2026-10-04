package com.example.chrono

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Settings {
    var ring by mutableStateOf(true)
        private set
    var aod by mutableStateOf(false)
        private set
    var eco by mutableStateOf(false)
        private set

    private var loaded = false
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val s = p(c)
        ring = s.getBoolean("ring", true)
        aod = s.getBoolean("aod", false)
        eco = s.getBoolean("eco", false)
    }

    fun setRing(c: Context, v: Boolean) { ring = v; p(c).edit().putBoolean("ring", v).apply() }
    fun setAod(c: Context, v: Boolean) { aod = v; p(c).edit().putBoolean("aod", v).apply() }
    fun setEco(c: Context, v: Boolean) { eco = v; p(c).edit().putBoolean("eco", v).apply() }

    /** Valeurs réellement appliquées : le mode éco force tout ce qui consomme à OFF. */
    val ringActive: Boolean get() = ring && !eco
    val aodActive: Boolean get() = aod && !eco
}
