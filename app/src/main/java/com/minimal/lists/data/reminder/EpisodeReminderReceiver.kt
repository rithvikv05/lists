package com.minimal.lists.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import com.minimal.lists.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

class EpisodeReminderReceiver : BroadcastReceiver() {
    companion object {
        const val CHANNEL_ID = "episode_reminders_channel"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_SERIES_TITLE = "extra_series_title"
        const val EXTRA_EPISODE_KEY = "extra_episode_key"
        const val EXTRA_EPISODE_NUMBER = "extra_episode_number"
        const val EXTRA_EPISODE_NAME = "extra_episode_name"
        const val EXTRA_COVER_URL = "extra_cover_url"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val seriesTitle = intent.getStringExtra(EXTRA_SERIES_TITLE) ?: "Tracked Series"
        val epKey = intent.getStringExtra(EXTRA_EPISODE_KEY) ?: ""
        val epNumber = intent.getIntExtra(EXTRA_EPISODE_NUMBER, 1)
        val epName = intent.getStringExtra(EXTRA_EPISODE_NAME) ?: "New Episode"
        val coverUrl = intent.getStringExtra(EXTRA_COVER_URL) ?: ""
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, 0L)

        // Clear reminder flag from prefs
        val prefs = context.getSharedPreferences("episode_reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("${itemId}_$epKey").apply()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Silent notification channel (IMPORTANCE_LOW)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Episode Releases",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Silent alerts when tracked episodes become available"
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_item_id", itemId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (itemId xor epKey.hashCode().toLong()).toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val epDisplay = if (epKey.isNotBlank()) "$epKey: $epName" else "Ep $epNumber: $epName"

        fun showNotification(coverBitmap: Bitmap? = null) {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Your tracked ep is live")
                .setContentText("$seriesTitle • $epDisplay")
                .setSubText(seriesTitle)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$seriesTitle • $epDisplay"))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSound(null)
                .setVibrate(null)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            if (coverBitmap != null) {
                builder.setLargeIcon(coverBitmap)
            }

            val notificationId = (itemId.hashCode() * 31 + epKey.hashCode())
            notificationManager.notify(notificationId, builder.build())
        }

        if (coverUrl.isNotBlank() && coverUrl.startsWith("http")) {
            CoroutineScope(Dispatchers.IO).launch {
                val bitmap = try {
                    val conn = URL(coverUrl).openConnection() as HttpURLConnection
                    conn.doInput = true
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    conn.connect()
                    BitmapFactory.decodeStream(conn.inputStream)
                } catch (_: Exception) {
                    null
                }
                showNotification(bitmap)
            }
        } else {
            showNotification(null)
        }
    }
}

