package com.example.kampus

import android.util.Log
import com.example.kampus.models.CollegeEvent
import com.example.kampus.models.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * High-performance FCM Service handling data-only high-priority event pushes.
 * Executes on background thread with fast path validation, idempotency checks,
 * local caching, and instant notification dispatch without blocking network calls.
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "KampusFCM"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "FCM_RECEIVED: Message received from ${remoteMessage.from}, priority=${remoteMessage.priority}")

        val data = remoteMessage.data
        if (data.isEmpty()) {
            Log.w(TAG, "FCM_DATA_INVALID: Empty data payload received, skipping")
            return
        }

        val type = data["type"] ?: ""

        // Handle direct chat messages
        if (type == "CHAT_MESSAGE" || (data.containsKey("senderName") && !data.containsKey("eventId"))) {
            val senderName = data["senderName"] ?: "Teammate"
            val messageText = data["messageText"] ?: "New message received"
            NotificationHelper.notifyNewChatMessage(applicationContext, senderName, messageText)
            return
        }

        // Handle event notifications (NEW_EVENT or legacy EVENT_PUBLISHED)
        val eventId = data["eventId"]?.trim() ?: ""
        val title = data["title"]?.trim() ?: ""

        // Validation of required payload fields
        if (eventId.isBlank() || title.isBlank()) {
            Log.w(TAG, "FCM_DATA_INVALID: Missing required eventId or title (eventId='$eventId', title='$title')")
            return
        }

        Log.d(TAG, "FCM_DATA_VALID: Valid event payload for eventId=$eventId ('$title')")

        // Idempotency: Check if event has already been processed and notified
        if (NotificationHelper.isEventAlreadyProcessed(applicationContext, eventId)) {
            Log.d(TAG, "EVENT_ALREADY_PROCESSED: Event $eventId already processed, ignoring duplicate push")
            return
        }

        // Mark as processed immediately to prevent duplicate concurrent processing
        NotificationHelper.markEventAsProcessed(applicationContext, eventId)

        val category = data["category"]?.trim()?.ifBlank { "General" } ?: "General"
        val deadline = data["deadline"]?.trim()?.ifBlank { "Soon" } ?: "Soon"
        val eventDate = data["eventDate"]?.trim()?.ifBlank { "" } ?: ""
        val startTime = data["startTime"]?.trim() ?: data["time"]?.trim() ?: "10:00 AM"
        val endTime = data["endTime"]?.trim() ?: "12:00 PM"
        val college = data["college"]?.trim()?.ifBlank { "Kampus" } ?: "Kampus"
        val description = data["description"]?.trim() ?: data["fullDescription"]?.trim() ?: ""
        val posterUrl = data["posterUrl"]?.trim() ?: ""
        val mode = data["mode"]?.trim() ?: "Online"
        val eligibility = data["eligibility"]?.trim() ?: "All Students"
        val fee = data["fee"]?.trim() ?: "Free"
        val coordinatorName = data["coordinatorName"]?.trim() ?: "Faculty In-Charge"
        val coordinatorRole = data["coordinatorRole"]?.trim() ?: "Faculty"
        val prizePool = data["prizePool"]?.trim() ?: ""
        val targetDept = data["targetDept"]?.trim() ?: "All Departments"

        val event = CollegeEvent(
            id = eventId,
            title = title,
            college = college,
            category = category,
            deadline = deadline,
            eventDate = eventDate,
            startTime = startTime,
            endTime = endTime,
            mode = mode,
            eligibility = eligibility,
            fee = fee,
            coordinatorName = coordinatorName,
            coordinatorRole = coordinatorRole,
            postedTime = "Just now",
            announcementNote = "",
            fullDescription = description,
            prizePool = prizePool,
            targetDept = targetDept,
            posterUrl = posterUrl
        )

        // 1. Fast local cache: Persist event locally so it's instantly visible on app open
        NotificationHelper.cacheEventLocally(applicationContext, event)

        // 2. Fast notification: Render Android notification immediately without slow network blocking
        NotificationHelper.notifyEventPublished(applicationContext, event)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "TOKEN_REFRESHED: FCM Token updated")
        if (token.isNotBlank()) {
            MongoDBHelper.registerDeviceFcmToken(token)
        }
    }
}