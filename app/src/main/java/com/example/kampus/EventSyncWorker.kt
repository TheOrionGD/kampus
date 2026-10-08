package com.example.kampus

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.kampus.models.AppDataManager
import com.example.kampus.models.CollegeEvent
import com.example.kampus.models.NotificationHelper
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background WorkManager worker for recovery and synchronization of events.
 * Executes periodically with CONNECTED network constraint to recover any events
 * missed while device was offline or in deep doze.
 */
class EventSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "KampusFCM"
        const val WORK_NAME = "kampus_periodic_event_sync"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "SYNC_STARTED: Periodic event synchronization triggered")
        try {
            val dataManager = AppDataManager(applicationContext)
            val cachedEvents = dataManager.getEvents()
            val cachedIds = cachedEvents.map { it.id }.toSet()

            // Fetch latest events from MongoDB Atlas database
            val col = MongoDBHelper.getDb()?.getCollection("events")
            if (col == null) {
                Log.w(TAG, "SYNC_FAILED: Database connection not ready during sync")
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
                    Log.w(TAG, "SYNC_WARNING: Could not parse event document: ${e.message}")
                }
            }

            if (remoteEvents.isNotEmpty()) {
                // Check for new/unprocessed events
                val newEvents = remoteEvents.filter { it.id !in cachedIds }
                var newlyNotifiedCount = 0

                for (event in newEvents) {
                    val alreadyProcessed = NotificationHelper.isEventAlreadyProcessed(applicationContext, event.id)
                    if (!alreadyProcessed) {
                        NotificationHelper.markEventAsProcessed(applicationContext, event.id)
                        NotificationHelper.cacheEventLocally(applicationContext, event)

                        // If the event registration deadline is still active, notify the user
                        if (!NotificationHelper.isDeadlinePassed(event.deadline) && !event.isExpired()) {
                            NotificationHelper.notifyEventPublished(applicationContext, event)
                            newlyNotifiedCount++
                        }
                    }
                }

                // Update local storage with complete remote dataset
                dataManager.saveEvents(remoteEvents)
                Log.d(TAG, "SYNC_COMPLETED: Fetched ${remoteEvents.size} events, ${newEvents.size} new, $newlyNotifiedCount notified")
            } else {
                Log.d(TAG, "SYNC_COMPLETED: No events found on remote")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "SYNC_FAILED: Error during event sync: ${e.message}", e)
            Result.retry()
        }
    }
}
