package com.example.kampus

import com.example.kampus.models.CollegeEvent
import com.example.kampus.models.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val notif = remoteMessage.notification

        val type = data["type"] ?: ""

        if (type == "CHAT_MESSAGE" || (data.containsKey("senderName") && !data.containsKey("eventId"))) {
            val senderName = data["senderName"] ?: notif?.title ?: "Teammate"
            val messageText = data["messageText"] ?: notif?.body ?: "New message received"
            NotificationHelper.notifyNewChatMessage(applicationContext, senderName, messageText)
            return
        }

        // Default or EVENT_PUBLISHED notification
        val eventId = data["eventId"] ?: ""
        val title = data["title"] ?: notif?.title ?: "New Event Published"
        val category = data["category"] ?: "General"
        val deadline = data["deadline"] ?: "Soon"
        val eventDate = data["eventDate"] ?: ""
        val startTime = data["startTime"] ?: data["time"] ?: "10:00 AM"
        val college = data["college"] ?: "Campus"

        val event = CollegeEvent(
            id = eventId.ifBlank { System.currentTimeMillis().toString() },
            title = title,
            college = college,
            category = category,
            deadline = deadline,
            eventDate = eventDate,
            startTime = startTime,
            endTime = data["endTime"] ?: "12:00 PM",
            mode = data["mode"] ?: "Online",
            eligibility = data["eligibility"] ?: "All Students",
            fee = data["fee"] ?: "Free",
            coordinatorName = data["coordinatorName"] ?: "Faculty In-Charge",
            coordinatorRole = data["coordinatorRole"] ?: "Faculty",
            postedTime = "Just now",
            announcementNote = "",
            fullDescription = data["description"] ?: "",
            prizePool = data["prizePool"] ?: "",
            targetDept = data["targetDept"] ?: "All Departments",
            posterUrl = data["posterUrl"] ?: ""
        )

        NotificationHelper.notifyEventPublished(applicationContext, event)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        MongoDBHelper.registerDeviceFcmToken(token)
    }
}