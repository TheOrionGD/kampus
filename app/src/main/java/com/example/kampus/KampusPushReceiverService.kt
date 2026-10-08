package com.example.kampus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.kampus.models.CollegeEvent
import com.example.kampus.models.NotificationHelper
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Firebase-Free Background Push Notification Receiver Service for Kampus.
 * Establishes a light SSE stream with Kampus notification-server and MongoDB backend.
 */
class KampusPushReceiverService : Service() {

    companion object {
        private const val TAG = "KampusPushReceiver"
        private const val DEFAULT_SERVER_URL = "http://10.0.2.2:3000"
        const val ACTION_START_PUSH_SERVICE = "com.example.kampus.START_PUSH_SERVICE"

        fun startService(context: Context, serverUrl: String = DEFAULT_SERVER_URL) {
            try {
                val intent = Intent(context, KampusPushReceiverService::class.java).apply {
                    action = ACTION_START_PUSH_SERVICE
                    putExtra("serverUrl", serverUrl)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start push service: ${e.message}")
            }
        }

        fun getDeviceId(context: Context): String {
            return try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                    ?: UUID.randomUUID().toString()
            } catch (_: Exception) {
                UUID.randomUUID().toString()
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var streamJob: Job? = null
    private val gson = Gson()

    override fun onCreate() {
        super.onCreate()
        createForegroundNotificationChannel()
        startForeground(9991, createForegroundNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val serverUrl = intent?.getStringExtra("serverUrl") ?: DEFAULT_SERVER_URL
        val deviceId = getDeviceId(applicationContext)

        // Register device ID with MongoDB Atlas
        MongoDBHelper.registerDevicePushToken(deviceId)

        // Start or restart SSE listener
        startSseStreamListener(serverUrl, deviceId)

        return START_STICKY
    }

    private fun startSseStreamListener(serverUrl: String, deviceId: String) {
        streamJob?.cancel()
        streamJob = serviceScope.launch {
            while (isActive) {
                var connection: HttpURLConnection? = null
                try {
                    val streamEndpoint = "$serverUrl/api/push/stream?deviceId=$deviceId"
                    val url = URL(streamEndpoint)
                    connection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        setRequestProperty("Accept", "text/event-stream")
                        connectTimeout = 15000
                        readTimeout = 0 // Keep-alive stream
                    }

                    Log.d(TAG, "Connecting to SSE Push Stream: $streamEndpoint")
                    val inputStream = connection.inputStream
                    val reader = BufferedReader(InputStreamReader(inputStream))

                    while (isActive) {
                        val currentLine = reader.readLine() ?: break
                        if (currentLine.startsWith("data:")) {
                            val jsonStr = currentLine.removePrefix("data:").trim()
                            if (jsonStr.isNotBlank()) {
                                handlePushPayload(jsonStr)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "SSE Stream disconnected or unreachable (${e.message}). Retrying in 10s...")
                } finally {
                    try {
                        connection?.disconnect()
                    } catch (_: Exception) {}
                }
                delay(10000) // Retry connection after 10s if server dropped
            }
        }
    }

    private fun handlePushPayload(jsonStr: String) {
        try {
            val jsonObject = gson.fromJson(jsonStr, JsonObject::class.java) ?: return
            val type = jsonObject.get("type")?.asString ?: ""

            if (type == "CONNECTED") {
                Log.d(TAG, "SSE Push Gateway stream connected successfully")
                return
            }

            val title = jsonObject.get("title")?.asString ?: "Campus Announcement"
            val body = jsonObject.get("body")?.asString ?: "New event available"
            val dataObj = jsonObject.getAsJsonObject("data")

            val eventId = dataObj?.get("eventId")?.asString ?: ""
            val category = dataObj?.get("category")?.asString ?: "General"
            val college = dataObj?.get("collegeId")?.asString ?: "Kampus"

            if (eventId.isNotBlank()) {
                if (NotificationHelper.isEventAlreadyProcessed(applicationContext, eventId)) {
                    Log.d(TAG, "Event $eventId already processed, ignoring duplicate SSE push")
                    return
                }
                NotificationHelper.markEventAsProcessed(applicationContext, eventId)
            }

            val event = CollegeEvent(
                id = eventId.ifBlank { System.currentTimeMillis().toString() },
                title = title,
                college = college,
                category = category,
                deadline = dataObj?.get("deadline")?.asString ?: "Upcoming",
                eventDate = dataObj?.get("eventDate")?.asString ?: "Soon",
                startTime = "10:00 AM",
                endTime = "12:00 PM",
                mode = "Offline",
                eligibility = "All Students",
                fee = "Free",
                coordinatorName = "Campus Admin",
                coordinatorRole = "Faculty",
                postedTime = "Just now",
                announcementNote = "",
                fullDescription = body,
                prizePool = "",
                targetDept = "All Departments",
                posterUrl = ""
            )

            NotificationHelper.cacheEventLocally(applicationContext, event)
            NotificationHelper.notifyEventPublished(applicationContext, event)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling push payload: ${e.message}")
        }
    }

    private fun createForegroundNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "push_daemon_channel",
                "Kampus Push Synchronization",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps event push notifications active"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, "push_daemon_channel")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Kampus Event Sync Active")
            .setContentText("Listening for real-time college notifications")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
