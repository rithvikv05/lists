package com.minimal.lists.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.minimal.lists.data.api.EpisodeItem
import com.minimal.lists.data.db.ListItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object EpisodeReminderManager {
    private const val PREFS_NAME = "episode_reminders_prefs"
    private const val SETTINGS_PREFS = "lists_settings_prefs"

    fun isReminderSet(context: Context, itemId: Long, episodeKey: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("${itemId}_$episodeKey", false)
    }

    fun toggleReminder(
        context: Context,
        item: ListItem,
        episode: EpisodeItem
    ): Boolean {
        val currentlySet = isReminderSet(context, item.id, episode.key)
        return if (currentlySet) {
            cancelReminder(context, item.id, episode.key)
            false
        } else {
            val scheduled = scheduleReminder(context, item, episode)
            scheduled
        }
    }

    fun scheduleReminder(
        context: Context,
        item: ListItem,
        episode: EpisodeItem
    ): Boolean {
        val triggerTimeMillis = calculateTriggerMillis(context, episode) ?: return false
        if (triggerTimeMillis <= System.currentTimeMillis()) return false

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false

        val intent = Intent(context, EpisodeReminderReceiver::class.java).apply {
            putExtra(EpisodeReminderReceiver.EXTRA_ITEM_ID, item.id)
            putExtra(EpisodeReminderReceiver.EXTRA_SERIES_TITLE, item.title)
            putExtra(EpisodeReminderReceiver.EXTRA_EPISODE_KEY, episode.key)
            putExtra(EpisodeReminderReceiver.EXTRA_EPISODE_NUMBER, episode.number)
            putExtra(EpisodeReminderReceiver.EXTRA_EPISODE_NAME, episode.name)
            putExtra(EpisodeReminderReceiver.EXTRA_COVER_URL, item.coverUrl)
        }

        val requestCode = (item.id xor episode.key.hashCode().toLong()).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
                }
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent)
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean("${item.id}_${episode.key}", true).apply()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun cancelReminder(context: Context, itemId: Long, episodeKey: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(context, EpisodeReminderReceiver::class.java)
        val requestCode = (itemId xor episodeKey.hashCode().toLong()).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager?.cancel(pendingIntent)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("${itemId}_$episodeKey").apply()
    }

    private fun calculateTriggerMillis(context: Context, episode: EpisodeItem): Long? {
        if (episode.airstamp.isNotBlank()) {
            try {
                val instant = Instant.parse(episode.airstamp)
                return instant.toEpochMilli()
            } catch (_: Exception) {}
        }

        val cleanAirdate = episode.airdate.trim()
        if (cleanAirdate.isBlank()) return null

        val localDate = parseAirdate(cleanAirdate) ?: return null

        val settingsPrefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val fallbackHour = settingsPrefs.getInt("fallback_reminder_hour", 9)
        val fallbackMinute = settingsPrefs.getInt("fallback_reminder_minute", 0)

        val localDateTime = localDate.atTime(fallbackHour, fallbackMinute)
        val zonedDateTime = localDateTime.atZone(ZoneId.systemDefault())
        return zonedDateTime.toInstant().toEpochMilli()
    }

    private fun parseAirdate(cleanDate: String): LocalDate? {
        try {
            if (cleanDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                return LocalDate.parse(cleanDate)
            }
        } catch (_: Exception) {}
        try {
            val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
            return LocalDate.parse(cleanDate, formatter)
        } catch (_: Exception) {}
        try {
            val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)
            return LocalDate.parse(cleanDate, formatter)
        } catch (_: Exception) {}
        return null
    }
}

