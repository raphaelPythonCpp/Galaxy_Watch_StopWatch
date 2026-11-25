package com.example.chrono

import android.content.Context
import androidx.compose.runtime.mutableStateListOf

/** laps = durée de chaque tour, dans l'ordre chronologique. */
data class Session(val ts: Long, val total: Long, val laps: List<Long>)

object History {
    val sessions = mutableStateListOf<Session>()   // la plus récente en premier
    private var loaded = false
    private const val MAX = 50
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val raw = p(c).getString("data", "") ?: ""
        raw.split('\n').filter { it.isNotBlank() }.forEach { line ->
            try {
                val f = line.split(';')
                val laps = if (f.size > 2 && f[2].isNotEmpty()) f[2].split(',').map { v -> v.toLong() } else emptyList()
                sessions.add(Session(f[0].toLong(), f[1].toLong(), laps))
            } catch (e: Exception) { }
        }
    }

    fun add(c: Context, s: Session) {
        load(c)
        sessions.add(0, s)
        while (sessions.size > MAX) sessions.removeAt(sessions.size - 1)
        persist(c)
    }

    fun delete(c: Context, index: Int) {
        load(c)
        if (index in sessions.indices) {
            sessions.removeAt(index)
            persist(c)
        }
    }

    fun clear(c: Context) {
        load(c)
        sessions.clear()
        persist(c)
    }

    private fun persist(c: Context) {
        p(c).edit().putString(
            "data",
            sessions.joinToString("\n") { s -> "${s.ts};${s.total};${s.laps.joinToString(",")}" }
        ).apply()
    }
}
