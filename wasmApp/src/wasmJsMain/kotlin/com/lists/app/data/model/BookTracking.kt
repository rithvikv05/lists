package com.lists.app.data.model

import com.lists.app.currentTimeMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

@Serializable
data class ReadingSessionRecord(
    val id: String = generateUuid(),
    val timestamp: Long = currentTimeMillis(),
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

    private val jsonHelper = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parseReadingSessions(json: String?): List<ReadingSessionRecord> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            jsonHelper.decodeFromString<List<ReadingSessionRecord>>(json)
                .sortedByDescending { it.timestamp }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun serializeReadingSessions(sessions: List<ReadingSessionRecord>): String {
        return try {
            jsonHelper.encodeToString(sessions)
        } catch (_: Throwable) {
            "[]"
        }
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
            ((sum / valid.size) * 10f).roundToInt() / 10f
        } else {
            // Chronological EWMA (alpha = 0.35, favors recent reading habits)
            val chronological = valid.reversed()
            val alpha = 0.35f
            var ewma = chronological[0].second
            for (i in 1 until chronological.size) {
                ewma = alpha * chronological[i].second + (1f - alpha) * ewma
            }
            (ewma * 10f).roundToInt() / 10f
        }

        val minPerPage = if (avgPph > 0f) ((60f / avgPph) * 10f).roundToInt() / 10f else 0f
        val etaMinutes = if (avgPph > 0f && remainingPages > 0) {
            ((remainingPages.toFloat() / avgPph) * 60f).roundToInt()
        } else {
            0
        }

        val formattedEta = if (valid.isEmpty() || avgPph <= 0f) {
            "--"
        } else if (remainingPages <= 0) {
            "Done"
        } else if (etaMinutes < 60) {
            "${etaMinutes}m"
        } else {
            val hours = etaMinutes / 60
            val mins = etaMinutes % 60
            if (mins == 0) "${hours}h" else "${hours}h ${mins}m"
        }

        return ReadingSpeedResult(
            averagePph = avgPph,
            minutesPerPage = minPerPage,
            remainingPages = remainingPages,
            etaMinutes = etaMinutes,
            formattedEta = formattedEta,
            sessionCount = sessions.size
        )
    }
}

