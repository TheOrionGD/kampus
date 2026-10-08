package com.example.kampus

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.kampus.models.AppNotification
import com.example.kampus.models.CollegeEvent
import com.example.kampus.models.NotificationHelper
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background WorkManager worker for checking and sending deadline reminders.
 * Runs periodically to catch events whose deadlines are approaching,
 * even when the app is completely closed.
 */
class DeadlineReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "KampusDeadlineWorker"
        const val WORK_NAME = "kampus_deadline_reminder_sync"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "DEADLINE_SYNC_STARTED: Checking for upcoming deadlines")
        try {
            val col = MongoDBHelper.getDb()?.getCollection("events")
            if (col == null) {
                Log.w(TAG, "DEADLINE_SYNC_FAILED: Database connection not ready")
                return@withContext Result.retry()
            }

            val remoteEvents = mutableListOf<CollegeEvent>()
            val gson = Gson()

            col.find().forEach { doc ->
                try {
                    doc.remove("_id")
                    val event = gson.fromJson(doc.toJson(), CollegeEvent::class.java)
                    if (event != null && event.id.isNotBlank()) {
                        remoteEvents.add(event)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "DEADLINE_SYNC_WARNING: Could not parse event: ${e.message}")
                }
            }

            if (remoteEvents.isNotEmpty()) {
                var remindersSent = 0

                for (event in remoteEvents) {
                    // Check if deadline is within 3 days (including today)
                    val deadlinePassed = NotificationHelper.isDeadlinePassed(event.deadline)
                    val deadlineDate = NotificationHelper.parseDeadlineDate(event.deadline)

                    if (!deadlinePassed && deadlineDate != null) {
                        val now = System.currentTimeMillis()
                        val timeDiff = deadlineDate.time - now
                        val daysDiff = timeDiff / (1000 * 60 * 60 * 24)

                        // Send reminder if deadline is within 3 days
                        if (daysDiff >= 0 && daysDiff <= 3) {
                            val alreadyNotified = NotificationHelper.isEventAlreadyProcessed(applicationContext, "deadline_reminder_${event.id}_${daysDiff}d")
                            if (!alreadyNotified) {
                                NotificationHelper.markEventAsProcessed(applicationContext, "deadline_reminder_${event.id}_${daysDiff}d")

                                val daysText = when (daysDiff) {
                                    0L -> "TODAY"
                                    1L -> "TOMORROW"
                                    else -> "in $daysDiff days"
                                }

                                // Create in-app notification for all students
                                NotificationHelper.createBroadcastNotification(
                                    applicationContext,
                                    "⏰ Deadline Approaching: ${event.title}",
                                    "Registration for ${event.title} at ${event.college} closes $daysText (${event.deadline})",
                                    "EVENT_DEADLINE",
                                    event.id
                                )

                                // Also send local push notification
                                NotificationHelper.notifyDeadlineReminder(applicationContext, com.example.kampus.models.StudentUser(), event)

                                remindersSent++
                            }
                        }
                    }
                }

                Log.d(TAG, "DEADLINE_SYNC_COMPLETED: Checked ${remoteEvents.size} events, sent $remindersSent reminders")
            } else {
                Log.d(TAG, "DEADLINE_SYNC_COMPLETED: No events found")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "DEADLINE_SYNC_FAILED: Error during deadline sync: ${e.message}", e)
            Result.retry()
        }
    }
}