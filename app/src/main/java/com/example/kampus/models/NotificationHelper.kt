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
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.kampus.DeadlineReminderWorker
import com.example.kampus.EventSyncWorker
import com.example.kampus.FirebaseHelper
import com.example.kampus.MainActivity
import com.example.kampus.models.AppNotification
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * BroadcastReceiver triggered by AlarmManager on event registration deadline days
 * even if the application is closed or in background.
 */
class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra("eventId") ?: ""
        val title = intent.getStringExtra("title")?.ifBlank { "College Event" } ?: "College Event"
        val college = intent.getStringExtra("college")?.ifBlank { "Campus" } ?: "Campus"
        val deadline = intent.getStringExtra("deadline")?.ifBlank { "Today" } ?: "Today"
        val category = intent.getStringExtra("category")?.ifBlank { "Event" } ?: "Event"
        val notifId = intent.getIntExtra("notifId", (eventId.hashCode().takeIf { it != 0 } ?: (System.currentTimeMillis() % 10000).toInt()))

        // Ensure notification channels exist before posting
        NotificationHelper.ensureNotificationChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (eventId.isNotBlank()) {
                putExtra("eventId", eventId)
                putExtra("fromNotification", true)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, NotificationHelper.DEADLINES_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("⏰ Last Chance to Register: $title ($college)")
            .setContentText("Registration deadline is $deadline!")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Urgent Reminder: Registration for '$title' ($category) at $college closes on $deadline. Tap to apply & join teams.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w("KampusFCM", "NOTIFICATION_PERMISSION_DENIED: Cannot post deadline reminder")
                return
            }
        }

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(notifId, builder.build())
                Log.d("KampusFCM", "NOTIFICATION_POSTED: Deadline reminder posted for $eventId")
            }
        } catch (e: Exception) {
            Log.e("KampusFCM", "Error posting deadline notification: ${e.message}")
        }
    }
}

/**
 * Centralized NotificationHelper for Kampus application.
 * Manages versioned notification channels, idempotent event deduplication,
 * local caching, fast-path notification rendering, and periodic WorkManager sync.
 */
object NotificationHelper {
    private const val TAG = "KampusFCM"
    private const val PREFS_REGISTRY = "kampus_notification_registry"
    private const val KEY_PROCESSED_EVENTS = "processed_event_ids"
    private const val MAX_REGISTRY_SIZE = 200

    // Versioned Notification Channel IDs
    const val EVENTS_CHANNEL_ID = "kampus_events_v2"
    const val EVENTS_CHANNEL_NAME = "Events & Announcements"

    const val DEADLINES_CHANNEL_ID = "kampus_deadlines_v2"
    const val DEADLINES_CHANNEL_NAME = "Event Deadlines"

    const val CHAT_CHANNEL_ID = "kampus_chat_v2"
    const val CHAT_CHANNEL_NAME = "Direct Messages"

    // Legacy aliases for backward compatibility
    const val CHANNEL_ID = EVENTS_CHANNEL_ID
    const val CHANNEL_NAME = EVENTS_CHANNEL_NAME

    /**
     * Creates or verifies all notification channels on API >= 26.
     * Does NOT overwrite or delete user customizations once created.
     */
    fun ensureNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            // 1. Events Channel
            if (notificationManager.getNotificationChannel(EVENTS_CHANNEL_ID) == null) {
                val eventChannel = NotificationChannel(
                    EVENTS_CHANNEL_ID,
                    EVENTS_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Real-time alerts for newly published college hackathons, symposiums and events"
                    enableLights(true)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 200, 300)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setSound(soundUri, null)
                }
                notificationManager.createNotificationChannel(eventChannel)
                Log.d(TAG, "CHANNEL_READY: Created $EVENTS_CHANNEL_ID")
            }

            // 2. Deadlines Channel
            if (notificationManager.getNotificationChannel(DEADLINES_CHANNEL_ID) == null) {
                val deadlineChannel = NotificationChannel(
                    DEADLINES_CHANNEL_ID,
                    DEADLINES_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Urgent reminder alerts before event registrations close"
                    enableLights(true)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setSound(soundUri, null)
                }
                notificationManager.createNotificationChannel(deadlineChannel)
                Log.d(TAG, "CHANNEL_READY: Created $DEADLINES_CHANNEL_ID")
            }

            // 3. Chat Channel
            if (notificationManager.getNotificationChannel(CHAT_CHANNEL_ID) == null) {
                val chatChannel = NotificationChannel(
                    CHAT_CHANNEL_ID,
                    CHAT_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Direct messages and team coordination notifications"
                    enableLights(true)
                    enableVibration(true)
                    setSound(soundUri, null)
                }
                notificationManager.createNotificationChannel(chatChannel)
                Log.d(TAG, "CHANNEL_READY: Created $CHAT_CHANNEL_ID")
            }
        }
    }

    /**
     * Backward-compatible helper method.
     */
    fun createNotificationChannel(context: Context) {
        ensureNotificationChannels(context)
    }

    /**
     * Ensure a specific channel exists before posting a notification.
     */
    fun ensureChannelExists(context: Context, channelId: String) {
        ensureNotificationChannels(context)
    }

    // --- 🔁 IDEMPOTENT EVENT DEDUPLICATION REGISTRY ---

    /**
     * Checks whether an event notification has already been processed and displayed.
     */
    fun isEventAlreadyProcessed(context: Context, eventId: String): Boolean {
        if (eventId.isBlank()) return false
        val prefs = context.getSharedPreferences(PREFS_REGISTRY, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PROCESSED_EVENTS, null) ?: return false
        return try {
            val type = object : TypeToken<Set<String>>() {}.type
            val set: Set<String> = Gson().fromJson(json, type) ?: emptySet()
            set.contains(eventId)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Marks an event ID as processed in the local deduplication registry.
     */
    fun markEventAsProcessed(context: Context, eventId: String) {
        if (eventId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_REGISTRY, Context.MODE_PRIVATE)
        try {
            val json = prefs.getString(KEY_PROCESSED_EVENTS, null)
            val type = object : TypeToken<MutableSet<String>>() {}.type
            val set: MutableSet<String> = if (!json.isNullOrBlank()) {
                Gson().fromJson(json, type) ?: mutableSetOf()
            } else {
                mutableSetOf()
            }

            set.add(eventId)

            // Keep registry bounded to prevent unbounded growth
            if (set.size > MAX_REGISTRY_SIZE) {
                val toRemove = set.take(set.size - MAX_REGISTRY_SIZE)
                set.removeAll(toRemove.toSet())
            }

            prefs.edit().putString(KEY_PROCESSED_EVENTS, Gson().toJson(set)).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error recording processed eventId: ${e.message}")
        }
    }

    // --- 💾 LOCAL EVENT CACHING ---

    /**
     * Immediately caches the event payload into local storage so it is available
     * to the student upon opening the app without needing a network round-trip.
     */
    fun cacheEventLocally(context: Context, event: CollegeEvent) {
        if (event.id.isBlank()) return
        try {
            val dataManager = AppDataManager(context)
            val currentEvents = dataManager.getEvents()
            val existingIndex = currentEvents.indexOfFirst { it.id == event.id }

            if (existingIndex != -1) {
                currentEvents[existingIndex] = event
            } else {
                currentEvents.add(0, event)
            }
            dataManager.saveEvents(currentEvents)
            Log.d(TAG, "EVENT_CACHED: Successfully cached event ${event.id} locally (${event.title})")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching event locally: ${e.message}")
        }
    }

    // --- ⚙️ WORKMANAGER PERIODIC SYNC ENQUEUE ---

    /**
     * Enqueues periodic background event synchronization via WorkManager
     * to recover any missed pushes when network/Doze allows.
     */
    fun schedulePeriodicEventSync(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<EventSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                EventSyncWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
            Log.d(TAG, "WORKMANAGER: Scheduled periodic event sync worker")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling WorkManager sync: ${e.message}")
        }
    }

    /**
     * Schedules periodic deadline reminder checks via WorkManager
     * to send reminders even when app is closed.
     */
    fun scheduleDeadlineReminderSync(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<DeadlineReminderWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                DeadlineReminderWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
            Log.d(TAG, "WORKMANAGER: Scheduled deadline reminder sync worker")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling deadline reminder WorkManager: ${e.message}")
        }
    }

    /**
     * Robust date parser supporting standard, abbreviated, and textual dates.
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
     * Returns true if the deadline date has already passed.
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

    // --- 🔔 1. INSTANT EVENT NOTIFICATION ---

    /**
     * Builds and displays a rich high-priority notification for a newly published event.
     * Guaranteed non-blocking and safe across all API levels (24 to 35+).
     */
    fun notifyEventPublished(
        context: Context,
        event: CollegeEvent
    ) {
        ensureNotificationChannels(context)

        val safeTitle = event.title.trim().ifBlank { "New Event Published" }
        val safeCategory = event.category.trim().ifBlank { "General" }
        val safeDeadline = event.deadline.trim().ifBlank { "Soon" }
        val safeEventDate = event.eventDate.trim().ifBlank { "TBA" }
        val safeStartTime = event.startTime.trim().ifBlank { "10:00 AM" }

        val notificationId = (event.id.hashCode().takeIf { it != 0 } ?: (System.currentTimeMillis() % 10000).toInt()) + 100

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

        val summaryText = "$safeCategory • Registration deadline: $safeDeadline\n$safeEventDate • $safeStartTime"
        val bigBodyText = "$safeTitle\n\nCategory: $safeCategory\nRegistration deadline: $safeDeadline\nEvent date: $safeEventDate\nTime: $safeStartTime"

        val builder = NotificationCompat.Builder(context, EVENTS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(safeTitle)
            .setContentText("$safeCategory • Deadline: $safeDeadline")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigBodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        sendNotification(context, notificationId, builder, event.id)

        // Schedule deadline reminder alarm
        scheduleBackgroundDeadlineAlarm(context, event)
    }

    fun notifyMatchedEvent(context: Context, student: StudentUser, event: CollegeEvent) {
        notifyEventPublished(context, event)
    }

    // --- ⏰ 2. DIRECT DEADLINE REMINDER NOTIFICATION ---

    fun notifyDeadlineReminder(context: Context, student: StudentUser, event: CollegeEvent) {
        if (isDeadlinePassed(event.deadline)) {
            return
        }

        ensureNotificationChannels(context)

        val safeTitle = event.title.trim().ifBlank { "College Event" }
        val safeCollege = event.college.trim().ifBlank { "Campus" }
        val safeCategory = event.category.trim().ifBlank { "Event" }
        val safeDeadline = event.deadline.trim().ifBlank { "Today" }

        val notificationId = (event.id.hashCode().takeIf { it != 0 } ?: (System.currentTimeMillis() % 10000).toInt()) + 200

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

        val builder = NotificationCompat.Builder(context, DEADLINES_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("⏰ Last Chance to Register: $safeTitle ($safeCollege)")
            .setContentText("Registration closing date: $safeDeadline")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Reminder: Registration for $safeTitle ($safeCategory) at $safeCollege closes on $safeDeadline. Tap to apply & join teams.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        sendNotification(context, notificationId, builder, "deadline_${event.id}")
    }

    // --- ⏰ 3. SCHEDULE BACKGROUND ALARM ---

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
                    putExtra("notifId", (event.id.hashCode() % 10000) + 200)
                }

                val requestCode = (event.id.hashCode() % 10000) + 200
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
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
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling deadline alarm: ${e.message}")
        }
    }

    // --- 💬 4. DIRECT CHAT PUSH NOTIFICATION ---

    fun notifyNewChatMessage(context: Context, senderName: String, messageText: String) {
        ensureNotificationChannels(context)

        val safeSender = senderName.trim().ifBlank { "Teammate" }
        val safeMessage = messageText.trim().ifBlank { "New message received" }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("fromNotification", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % 10000).toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, CHAT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("💬 New Message from $safeSender")
            .setContentText(safeMessage)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$safeSender: $safeMessage")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        sendNotification(context, (System.currentTimeMillis() % 10000).toInt(), builder, "chat_$safeSender")
    }

    private fun sendNotification(context: Context, id: Int, builder: NotificationCompat.Builder, trackingKey: String = "") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "NOTIFICATION_PERMISSION_DENIED: Cannot post notification for $trackingKey")
                return
            }
        }
        try {
            with(NotificationManagerCompat.from(context)) {
                notify(id, builder.build())
                Log.d(TAG, "NOTIFICATION_POSTED: Successfully displayed notification id=$id key=$trackingKey")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error posting notification $trackingKey: ${e.message}", e)
        }
    }

    // --- 🔔 DATABASE-DRIVEN IN-APP NOTIFICATIONS ---
    fun createAndSendNotification(
        context: Context,
        userId: String,
        userRole: String,
        title: String,
        message: String,
        type: String,
        eventId: String = ""
    ) {
        val notification = AppNotification(
            id = System.currentTimeMillis().toString() + "_" + userId.hashCode(),
            userId = userId,
            userRole = userRole,
            title = title,
            message = message,
            type = type,
            eventId = eventId,
            isRead = false,
            createdAt = System.currentTimeMillis()
        )

        // Save to database
        FirebaseHelper.saveNotification(notification) { success ->
            if (success) {
                Log.d(TAG, "IN_APP_NOTIFICATION_SAVED: $title for $userId")
            }
        }

        // Also send FCM push notification if it's an event publication
        if (type == "EVENT_PUBLISHED" && eventId.isNotBlank()) {
            // The FCM notification is already handled via the event listener in MainActivity
            // and MyFirebaseMessagingService
        }
    }

    fun createBroadcastNotification(
        context: Context,
        title: String,
        message: String,
        type: String,
        eventId: String = ""
    ) {
        // Send to all students
        createAndSendNotification(context, "ALL", "STUDENT", title, message, type, eventId)
        // Send to all faculty
        createAndSendNotification(context, "ALL", "FACULTY", title, message, type, eventId)
    }
}