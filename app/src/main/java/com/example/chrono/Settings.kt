package com.example.chrono

import android.content.Context

object Settings {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun aod(c: Context): Boolean = p(c).getBoolean("aod", false)
    fun setAod(c: Context, v: Boolean) { p(c).edit().putBoolean("aod", v).apply() }
}
