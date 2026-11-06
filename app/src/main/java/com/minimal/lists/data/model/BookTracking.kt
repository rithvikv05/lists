package com.minimal.lists.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ReadingSessionRecord(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val startPage: Int,
    val endPage: Int,
    val pagesRead: Int,
    val durationMinutes: Int
)

data class ReadingSpeedResult(
    val averagePph: Float,
    val minutesPerPage: Float,
    val remainingPages: Int,
    val etaMinutes: Int,
    val formattedEta: String,
    val sessionCount: Int
)

object BookTrackingUtils {

    fun parseReadingSessions(json: String?): List<ReadingSessionRecord> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<ReadingSessionRecord>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ReadingSessionRecord(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        startPage = obj.optInt("startPage", 0),
                        endPage = obj.optInt("endPage", 0),
                        pagesRead = obj.optInt("pagesRead", 0),
                        durationMinutes = obj.optInt("durationMinutes", 0)
                    )
                )
            }
        } catch (_: Exception) {}
        return list.sortedByDescending { it.timestamp }
    }

    fun serializeReadingSessions(sessions: List<ReadingSessionRecord>): String {
        val arr = JSONArray()
        for (s in sessions) {
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("timestamp", s.timestamp)
            obj.put("startPage", s.startPage)
            obj.put("endPage", s.endPage)
            obj.put("pagesRead", s.pagesRead)
            obj.put("durationMinutes", s.durationMinutes)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun calculateReadingSpeed(
        sessions: List<ReadingSessionRecord>,
        totalPages: Int,
        currentPage: Int
    ): ReadingSpeedResult {
        val remainingPages = if (totalPages > 0) (totalPages - currentPage).coerceAtLeast(0) else 0

        // Filter valid sessions (at least 1 min, > 0 pages, within sensible 5-180 pph)
        val valid = sessions.filter { it.durationMinutes >= 1 && it.pagesRead > 0 }
            .map { s ->
                val pph = (s.pagesRead.toFloat() / s.durationMinutes.toFloat()) * 60f
                Pair(s, pph)
            }
            .filter { it.second in 5f..180f }

        val avgPph: Float = if (valid.isEmpty()) {
            0f // Keep 0 until at least 1 reading log has been recorded
        } else if (valid.size < 3) {
            val sum = valid.sumOf { it.second.toDouble() }.toFloat()
            Math.round((sum / valid.size) * 10f) / 10f
        } else {
            // Chronological EWMA (alpha = 0.35, favors recent reading habits)
            val chronological = valid.reversed()
            val alpha = 0.35f
            var ewma = chronological[0].second
            for (i in 1 until chronological.size) {
                ewma = alpha * chronological[i].second + (1f - alpha) * ewma
            }
            Math.round(ewma * 10f) / 10f
        }

        val minPerPage = if (avgPph > 0f) Math.round((60f / avgPph) * 10f) / 10f else 0f
        val etaMinutes = if (avgPph > 0f && remainingPages > 0) {
            Math.round((remainingPages.toFloat() / avgPph) * 60f)
        } else {
            0
        }

        val hours = etaMinutes / 60
        val mins = etaMinutes % 60
        val formattedEta = when {
            avgPph <= 0f -> "--"
            remainingPages == 0 && totalPages > 0 -> "Finished"
            hours > 0 -> "${hours}h ${mins}m left"
            mins > 0 -> "${mins}m left"
            else -> "--"
        }

        return ReadingSpeedResult(
            averagePph = avgPph,
            minutesPerPage = minPerPage,
            remainingPages = remainingPages,
            etaMinutes = etaMinutes,
            formattedEta = formattedEta,
            sessionCount = valid.size
        )
    }
}

