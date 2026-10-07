package com.example.kampus.models

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.kampus.MainActivity
import java.text.SimpleDateFormat
import java.util.*

// BroadcastReceiver triggered by Android OS even if the app is fully CLOSED/KILLED
class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra("eventId") ?: ""
        val title = intent.getStringExtra("title") ?: "College Event"
        val college = intent.getStringExtra("college") ?: "Campus"
        val deadline = intent.getStringExtra("deadline") ?: "Today"
        val category = intent.getStringExtra("category") ?: "Event"
        val notifId = intent.getIntExtra("notifId", (System.currentTimeMillis() % 10000).toInt())

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (eventId.isNotBlank()) {
                putExtra("eventId", eventId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, "kampus_event_channel_v1")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("⏰ Last Chance to Register: $title ($college)")
            .setContentText("Registration deadline is $deadline!")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Urgent Reminder: Registration for '$title' ($category) at $college closes on $deadline. Tap to apply & join teams.")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(notifId, builder.build())
            }
        } catch (_: Exception) {}
    }
}

object NotificationHelper {
    const val CHANNEL_ID = "kampus_event_channel_v1"
    const val CHANNEL_NAME = "Kampus College Events & Deadlines"
    const val CHAT_CHANNEL_ID = "kampus_chat_channel"
    const val CHAT_CHANNEL_NAME = "Chat Notifications"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val eventChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for real-time department & skill matched event alerts"
                enableLights(true)
                enableVibration(true)
                setSound(soundUri, null)
            }
            notificationManager.createNotificationChannel(eventChannel)

            val chatChannel = NotificationChannel(
                CHAT_CHANNEL_ID,
                CHAT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Direct messages from teammates"
                enableLights(true)
                enableVibration(true)
                setSound(soundUri, null)
            }
            notificationManager.createNotificationChannel(chatChannel)
        }
    }

    /**
     * Robust date parser supporting standard, abbreviated, and textual dates.
     * If the year is omitted, automatically resolves to current calendar year.
     */
    fun parseDeadlineDate(deadlineStr: String): Date? {
        if (deadlineStr.isBlank()) return null
        val trimmed = deadlineStr.trim()
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        val fullFormats = listOf(
            SimpleDateFormat("d/M/yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH),
            SimpleDateFormat("d/M/yy", Locale.ENGLISH),
            SimpleDateFormat("dd/MM/yy", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH),
            SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH),
            SimpleDateFormat("d-M-yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH),
            SimpleDateFormat("d-M-yy", Locale.ENGLISH),
            SimpleDateFormat("dd-MM-yy", Locale.ENGLISH),
            SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH),
            SimpleDateFormat("d MMM yyyy", Locale.ENGLISH),
            SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH),
            SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH),
            SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH),
            SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH),
            SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH),
            SimpleDateFormat("MMMM d yyyy", Locale.ENGLISH),
            SimpleDateFormat("MMM d yyyy", Locale.ENGLISH),
            SimpleDateFormat("d MMM yy", Locale.ENGLISH),
            SimpleDateFormat("d MMMM yy", Locale.ENGLISH)
        )

        for (fmt in fullFormats) {
            try {
                fmt.isLenient = false
                val parsed = fmt.parse(trimmed)
                if (parsed != null) return parsed
            } catch (_: Exception) {}
        }

        // Try format without year by appending current year
        val partialFormats = listOf(
            SimpleDateFormat("d MMMM", Locale.ENGLISH),
            SimpleDateFormat("dd MMMM", Locale.ENGLISH),
            SimpleDateFormat("d MMM", Locale.ENGLISH),
            SimpleDateFormat("dd MMM", Locale.ENGLISH),
            SimpleDateFormat("MMMM d", Locale.ENGLISH),
            SimpleDateFormat("MMM d", Locale.ENGLISH),
            SimpleDateFormat("MMMM dd", Locale.ENGLISH),
            SimpleDateFormat("MMM dd", Locale.ENGLISH),
            SimpleDateFormat("d/M", Locale.ENGLISH),
            SimpleDateFormat("dd/MM", Locale.ENGLISH),
            SimpleDateFormat("d-M", Locale.ENGLISH),
            SimpleDateFormat("dd-MM", Locale.ENGLISH)
        )

        for (fmt in partialFormats) {
            try {
                fmt.isLenient = false
                val parsed = fmt.parse(trimmed)
                if (parsed != null) {
                    val cal = Calendar.getInstance().apply {
                        time = parsed
                        set(Calendar.YEAR, currentYear)
                    }
                    return cal.time
                }
            } catch (_: Exception) {}
        }

        // Fallback lenient attempt
        for (fmt in fullFormats) {
            try {
                fmt.isLenient = true
                val parsed = fmt.parse(trimmed)
                if (parsed != null) return parsed
            } catch (_: Exception) {}
        }

        return null
    }

    /**
     * Strictly returns true if the deadline day has already passed.
     * If deadline is a date-only field, it remains active until 23:59:59.999 of that date.
     * At 00:00:00 of the next day, it is strictly passed.
     */
    fun isDeadlinePassed(deadlineStr: String): Boolean {
        if (deadlineStr.isBlank()) return false
        val parsedDate = parseDeadlineDate(deadlineStr) ?: return false
        val cal = Calendar.getInstance().apply {
            time = parsedDate
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return System.currentTimeMillis() > cal.timeInMillis
    }

    // 1. INSTANT UNIVERSAL PUSH NOTIFICATION FOR NEWLY PUBLISHED EVENT
    fun notifyEventPublished(
        context: Context,
        event: CollegeEvent
    ) {
        val notificationId = (event.id.hashCode() % 10000) + 100

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("eventId", event.id)
            putExtra("fromNotification", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val titleText = "New Event Published"
        val bigBodyText = "${event.title}\n" +
                "Category: ${event.category}\n" +
                "Registration Deadline: ${event.deadline}\n" +
                "Event Date: ${event.eventDate}\n" +
                "Time: ${event.startTime}"

        val summaryText = "${event.title} (${event.category}) • Deadline: ${event.deadline}"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titleText)
            .setContentText(summaryText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigBodyText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        sendNotification(context, notificationId, builder)

        // Schedule the alarm so user gets notified on the deadline day even if the app is closed
        scheduleBackgroundDeadlineAlarm(context, event)
    }

    // Backwards compatibility alias for matched event
    fun notifyMatchedEvent(context: Context, student: StudentUser, event: CollegeEvent) {
        notifyEventPublished(context, event)
    }

    // 2. DIRECT DEADLINE REMINDER NOTIFICATION
    fun notifyDeadlineReminder(context: Context, student: StudentUser, event: CollegeEvent) {
        if (isDeadlinePassed(event.deadline)) {
            return
        }

        val notificationId = (event.id.hashCode() % 10000) + 200

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("eventId", event.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("⏰ Last Chance to Register: ${event.title} (${event.college})")
            .setContentText("Registration closing date: ${event.deadline}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Reminder: Registration for ${event.title} (${event.category}) at ${event.college} closes on ${event.deadline}. Tap to apply & join teams.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        sendNotification(context, notificationId, builder)
    }

    // 3. SCHEDULE BACKGROUND ALARM (Fires even when the app is completely closed)
    fun scheduleBackgroundDeadlineAlarm(context: Context, event: CollegeEvent) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val parsedDate = parseDeadlineDate(event.deadline) ?: return

            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply {
                time = parsedDate
                set(Calendar.HOUR_OF_DAY, 9) // 9:00 AM on deadline date
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val deadlineTimeMillis = cal.timeInMillis

            if (deadlineTimeMillis > now) {
                val intent = Intent(context, EventReminderReceiver::class.java).apply {
                    putExtra("eventId", event.id)
                    putExtra("title", event.title)
                    putExtra("college", event.college)
                    putExtra("deadline", event.deadline)
                    putExtra("category", event.category)
                    putExtra("notifId", event.id.hashCode())
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    event.id.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadlineTimeMillis, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadlineTimeMillis, pendingIntent)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadlineTimeMillis, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, deadlineTimeMillis, pendingIntent)
                }
            }
        } catch (_: Exception) {}
    }

    // 4. DIRECT CHAT PUSH NOTIFICATION HELPER
    fun notifyNewChatMessage(context: Context, senderName: String, messageText: String) {
        createNotificationChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHAT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("💬 New Message from $senderName")
            .setContentText(messageText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$senderName: $messageText")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        sendNotification(context, (System.currentTimeMillis() % 10000).toInt(), builder)
    }

    private fun sendNotification(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        try {
            with(NotificationManagerCompat.from(context)) {
                notify(id, builder.build())
            }
        } catch (_: Exception) {}
    }
}