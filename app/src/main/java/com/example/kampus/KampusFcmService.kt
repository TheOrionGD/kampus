package com.example.kampus

import android.util.Log
import com.example.kampus.models.CollegeEvent
import com.example.kampus.models.NotificationHelper
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Standard Firebase Cloud Messaging (FCM) Push Receiver Service for Kampus.
 * Receives remote messages from FCM and displays notifications in system status bar / tray,
 * while MongoDB persists notification history for the in-app notification center.
 */
class KampusFcmService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "KampusFcmService"

        /**
         * Subscribe device to global college events FCM topic.
         */
        fun subscribeToEventsTopic() {
            try {
                FirebaseMessaging.getInstance().subscribeToTopic("college_events")
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "FCM: Successfully subscribed to 'college_events' topic")
                        } else {
                            Log.w(TAG, "FCM: Failed to subscribe to 'college_events' topic", task.exception)
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "FCM: Error subscribing to topic: ${e.message}")
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM: New FCM Device Token received: $token")
        // Register FCM Token with MongoDB Atlas
        MongoDBHelper.registerDeviceFcmToken(token)
        subscribeToEventsTopic()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM: Push Message received from: ${remoteMessage.from}")

        try {
            val data = remoteMessage.data
            val notification = remoteMessage.notification

            val title = data["title"]
                ?: notification?.title
                ?: "Campus Announcement"

            val body = data["body"]
                ?: data["description"]
                ?: notification?.body
                ?: "New college event available"

            val eventId = data["eventId"] ?: data["id"] ?: ""
            val category = data["category"] ?: "General"
            val college = data["college"] ?: data["collegeId"] ?: "Kampus"
            val deadline = data["deadline"] ?: data["registration_deadline"] ?: "Upcoming"
            val eventDate = data["eventDate"] ?: data["event_date"] ?: "Soon"
            val startTime = data["startTime"] ?: data["event_time"] ?: "10:00 AM"

            if (eventId.isNotBlank()) {
                if (NotificationHelper.isEventAlreadyProcessed(applicationContext, eventId)) {
                    Log.d(TAG, "FCM: Event $eventId already processed, skipping duplicate notification")
                    return
                }
                NotificationHelper.markEventAsProcessed(applicationContext, eventId)
            }

            val event = CollegeEvent(
                id = eventId.ifBlank { System.currentTimeMillis().toString() },
                title = title,
                college = college,
                category = category,
                deadline = deadline,
                eventDate = eventDate,
                startTime = startTime,
                endTime = "12:00 PM",
                mode = data["mode"] ?: "Offline",
                eligibility = data["eligibility"] ?: "All Students",
                fee = data["fee"] ?: "Free",
                coordinatorName = data["coordinatorName"] ?: "Campus Admin",
                coordinatorRole = data["coordinatorRole"] ?: "Faculty",
                postedTime = "Just now",
                announcementNote = "",
                fullDescription = body,
                prizePool = data["prizePool"] ?: "",
                targetDept = data["targetDept"] ?: "All Departments",
                posterUrl = data["posterUrl"] ?: ""
            )

            // 1. Cache event locally so it appears instantly in home feed
            NotificationHelper.cacheEventLocally(applicationContext, event)

            // 2. Display push notification in Android system tray / status bar
            NotificationHelper.notifyEventPublished(applicationContext, event)

        } catch (e: Exception) {
            Log.e(TAG, "FCM: Error processing FCM notification payload: ${e.message}", e)
        }
    }
}
