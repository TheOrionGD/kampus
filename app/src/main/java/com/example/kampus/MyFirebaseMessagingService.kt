package com.example.kampus

import android.util.Log
import com.example.kampus.models.CollegeEvent
import com.example.kampus.models.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Dedicated FirebaseMessagingService handling FCM token registration,
 * data payload parsing, foreground message processing, and killed/background push handling.
 * 
 * Supports structured notification data parameters:
 * - event_id / eventId
 * - title
 * - category
 * - registration_deadline / deadline
 * - event_date / eventDate
 * - event_time / startTime / time
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "KampusFCM"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "FCM_RECEIVED: Message received from ${remoteMessage.from}, priority=${remoteMessage.priority}")

        val data = remoteMessage.data
        val notification = remoteMessage.notification

        // Extract structured data parameters (supporting both snake_case and camelCase)
        val eventId = data["event_id"]?.trim() ?: data["eventId"]?.trim() ?: ""
        val title = data["title"]?.trim() ?: notification?.title?.trim() ?: ""
        val category = data["category"]?.trim()?.ifBlank { "General" } ?: "General"
        val deadline = data["registration_deadline"]?.trim() ?: data["deadline"]?.trim() ?: "Soon"
        val eventDate = data["event_date"]?.trim() ?: data["eventDate"]?.trim() ?: ""
        val eventTime = data["event_time"]?.trim() ?: data["startTime"]?.trim() ?: data["time"]?.trim() ?: "10:00 AM"

        val type = data["type"] ?: ""

        // Handle direct chat messages
        if (type == "CHAT_MESSAGE" || (data.containsKey("senderName") && eventId.isBlank())) {
            val senderName = data["senderName"] ?: "Teammate"
            val messageText = data["messageText"] ?: notification?.body ?: "New message received"
            NotificationHelper.notifyNewChatMessage(applicationContext, senderName, messageText)
            return
        }

        // Validate payload presence
        if (eventId.isBlank() && title.isBlank() && notification == null) {
            Log.w(TAG, "FCM_DATA_INVALID: Empty or missing required payload fields")
            return
        }

        val safeTitle = title.ifBlank { notification?.title } ?: "Campus Event Alert"
        val safeBody = data["description"] ?: data["fullDescription"] ?: notification?.body ?: "$category • Deadline: $deadline"

        // Deduplication check: prevent duplicate notifications for same eventId
        if (eventId.isNotBlank() && NotificationHelper.isEventAlreadyProcessed(applicationContext, eventId)) {
            Log.d(TAG, "EVENT_ALREADY_PROCESSED: Event $eventId already processed, ignoring duplicate push")
            return
        }

        if (eventId.isNotBlank()) {
            NotificationHelper.markEventAsProcessed(applicationContext, eventId)
        }

        val college = data["college"]?.trim()?.ifBlank { "Kampus" } ?: "Kampus"
        val posterUrl = data["posterUrl"]?.trim() ?: ""
        val mode = data["mode"]?.trim() ?: "Online"
        val eligibility = data["eligibility"]?.trim() ?: "All Students"
        val fee = data["fee"]?.trim() ?: "Free"
        val coordinatorName = data["coordinatorName"]?.trim() ?: "Faculty In-Charge"
        val coordinatorRole = data["coordinatorRole"]?.trim() ?: "Faculty"
        val prizePool = data["prizePool"]?.trim() ?: ""
        val targetDept = data["targetDept"]?.trim() ?: "All Departments"

        val event = CollegeEvent(
            id = eventId.ifBlank { System.currentTimeMillis().toString() },
            title = safeTitle,
            college = college,
            category = category,
            deadline = deadline,
            eventDate = eventDate,
            startTime = eventTime,
            endTime = data["endTime"]?.trim() ?: "12:00 PM",
            mode = mode,
            eligibility = eligibility,
            fee = fee,
            coordinatorName = coordinatorName,
            coordinatorRole = coordinatorRole,
            postedTime = "Just now",
            announcementNote = "",
            fullDescription = safeBody,
            prizePool = prizePool,
            targetDept = targetDept,
            posterUrl = posterUrl
        )

        // 1. Fast local cache: Persist event locally so it's instantly visible on app open
        NotificationHelper.cacheEventLocally(applicationContext, event)

        // 2. Fast notification: Render Android notification with pending intent attached
        NotificationHelper.notifyEventPublished(applicationContext, event)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "TOKEN_REFRESHED: FCM Token updated -> $token")
        if (token.isNotBlank()) {
            MongoDBHelper.registerDeviceFcmToken(token)
        }
    }
}