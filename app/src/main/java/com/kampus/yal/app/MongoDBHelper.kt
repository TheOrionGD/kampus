package com.kampus.yal.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.kampus.yal.app.models.AdminUser
import com.kampus.yal.app.models.AppNotification
import com.kampus.yal.app.models.ChatMessage
import com.kampus.yal.app.models.CollegeEvent
import com.kampus.yal.app.models.DeviceFcmToken
import com.kampus.yal.app.models.EventParticipant
import com.kampus.yal.app.models.FacultyKYC
import com.kampus.yal.app.models.FacultyRole
import com.kampus.yal.app.models.PublicStudentProfile
import com.kampus.yal.app.models.StudentPost
import com.kampus.yal.app.models.StudentUser
import com.google.gson.Gson
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.UpdateOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.bson.Document
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import com.mongodb.ReadPreference
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

object MongoDBHelper {
    private val DB_NAME: String = BuildConfig.MONGODB_DATABASE.ifBlank { "Kampus" }
    private val CONNECTION_URI: String = BuildConfig.MONGODB_URI.ifBlank {
        BuildConfig.MONGODB_FALLBACK_URI
    }

    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    @Volatile
    private var mongoClient: MongoClient? = null
    @Volatile
    private var database: MongoDatabase? = null

    init {
        scope.launch(Dispatchers.IO) {
            getDb()
        }
    }

    private fun buildClientSettings(uriStr: String): MongoClientSettings {
        val sslContext = try {
            SSLContext.getDefault()
        } catch (_: Throwable) {
            null
        }

        return MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(uriStr))
            .readPreference(ReadPreference.primaryPreferred())
            .applyToClusterSettings { builder ->
                builder.serverSelectionTimeout(12, TimeUnit.SECONDS)
            }
            .applyToSocketSettings { builder ->
                builder.connectTimeout(12, TimeUnit.SECONDS)
                builder.readTimeout(15, TimeUnit.SECONDS)
            }
            .applyToConnectionPoolSettings { builder ->
                builder.minSize(1)
                builder.maxSize(15)
                builder.maxWaitTime(12, TimeUnit.SECONDS)
                builder.maxConnectionIdleTime(60, TimeUnit.SECONDS)
                builder.maxConnectionLifeTime(10, TimeUnit.MINUTES)
            }
            .applyToServerSettings { builder ->
                builder.heartbeatFrequency(10, TimeUnit.SECONDS)
                builder.minHeartbeatFrequency(500, TimeUnit.MILLISECONDS)
            }
            .applyToSslSettings { builder ->
                builder.enabled(true)
                if (sslContext != null) {
                    builder.context(sslContext)
                }
                builder.invalidHostNameAllowed(true)
            }
            .build()
    }

    @Synchronized
    fun getDb(): MongoDatabase? {
        if (database != null) return database

        return try {
            val settings = buildClientSettings(CONNECTION_URI)
            val client = MongoClients.create(settings)
            val db = client.getDatabase(DB_NAME)
            mongoClient = client
            database = db
            db
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Resets the MongoDB client and reconnects (useful when device switches between Wi-Fi and Mobile Data).
     */
    @Synchronized
    fun resetConnection() {
        try {
            mongoClient?.close()
        } catch (_: Throwable) {}
        mongoClient = null
        database = null
        scope.launch(Dispatchers.IO) {
            getDb()
        }
    }

    fun prewarmConnection() {
        scope.launch(Dispatchers.IO) {
            try {
                getDb()?.runCommand(Document("ping", 1))
            } catch (_: Throwable) {}
        }
    }

    private fun getCollection(name: String): MongoCollection<Document>? {
        return getDb()?.getCollection(name)
    }

    // 🔔 FIREBASE-FREE DEVICE PUSH TOKEN REGISTRATION & MANAGEMENT (MongoDB Atlas)
    fun registerDevicePushToken(token: String, userEmail: String = "", role: String = "STUDENT", collegeId: String = "col_abc") {
        if (token.isBlank()) return
        scope.launch {
            try {
                val col = getCollection("deviceTokens")
                val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
                val safeEmail = userEmail.trim().lowercase()
                val doc = Document()
                    .append("_id", token)
                    .append("deviceId", token)
                    .append("pushToken", token)
                    .append("userId", if (safeEmail.isBlank()) "ALL" else safeEmail)
                    .append("collegeId", collegeId)
                    .append("platform", "ANDROID")
                    .append("role", role)
                    .append("isActive", true)
                    .append("status", "ACTIVE")
                    .append("deviceModel", deviceModel)
                    .append("lastSeenAt", java.util.Date())
                    .append("lastUpdated", System.currentTimeMillis())
                    .append("updatedAt", java.util.Date())

                col?.replaceOne(Filters.eq("_id", token), doc, ReplaceOptions().upsert(true))

                // Also maintain fallback in fcm_tokens for legacy compatibility
                val fcmCol = getCollection("fcm_tokens")
                val tokenObj = DeviceFcmToken(
                    userId = safeEmail,
                    token = token,
                    role = role,
                    status = "ACTIVE",
                    lastUpdated = System.currentTimeMillis(),
                    deviceModel = deviceModel
                )
                // Also send FCM token to Render Express backend (/api/device-tokens)
                registerFcmTokenWithRenderBackend(token, safeEmail, role, collegeId)
            } catch (_: Exception) {}
        }
    }

    fun registerDeviceFcmToken(token: String, userEmail: String = "", role: String = "STUDENT") {
        registerDevicePushToken(token, userEmail, role)
    }

    fun registerFcmTokenWithRenderBackend(
        fcmToken: String,
        userId: String = "",
        role: String = "STUDENT",
        collegeId: String = "col_abc",
        authToken: String = ""
    ) {
        scope.launch(Dispatchers.IO) {
            val backendEndpoints = listOf(
                "https://kampus-notification-server.onrender.com/api/device-tokens",
                "http://10.0.2.2:3000/api/device-tokens",
                "http://localhost:3000/api/device-tokens"
            )
            val safeUser = if (userId.isBlank()) "ALL" else userId.trim().lowercase()
            val payload = gson.toJson(mapOf(
                "token" to fcmToken,
                "userId" to safeUser,
                "role" to role,
                "collegeId" to collegeId,
                "platform" to "android"
            ))
            for (endpoint in backendEndpoints) {
                try {
                    val connection = java.net.URL(endpoint).openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "POST"
                    if (authToken.isNotBlank()) {
                        connection.setRequestProperty("Authorization", "Bearer $authToken")
                    }
                    connection.setRequestProperty("X-User-Id", safeUser)
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.connectTimeout = 4000
                    connection.readTimeout = 4000
                    connection.doOutput = true
                    connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    Log.d("MongoDBHelper", "Registered FCM token with backend ($endpoint), code: $code")
                    if (code in 200..299) break
                } catch (e: Exception) {
                    Log.w("MongoDBHelper", "FCM backend token registration attempt error ($endpoint): ${e.message}")
                }
            }
        }
    }

    // 🖼️ UNIVERSAL IMAGE CLOUD STORAGE HELPER (Cloudinary CDN upload with Base64 fallback)
    /**
     * Reads image URI synchronously from open InputStream on the calling thread (before URI permissions expire),
     * compresses & scales it, uploads directly to Cloudinary CDN, and posts the secure CDN URL back to caller
     * so MongoDB Atlas persists the remote HTTPS Cloudinary URL accessible by all devices.
     */
    fun processPickedImageUri(context: Context, uri: Uri, folderName: String, onResult: (String) -> Unit) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) {
                onResult("")
                return
            }

            // Scale down to max 1200px
            val maxDim = 1200
            var width = originalBitmap.width
            var height = originalBitmap.height
            if (width > maxDim || height > maxDim) {
                val ratio = width.toFloat() / height.toFloat()
                if (ratio > 1) {
                    width = maxDim
                    height = (maxDim / ratio).toInt()
                } else {
                    height = maxDim
                    width = (maxDim * ratio).toInt()
                }
            }
            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true)

            scope.launch(Dispatchers.IO) {
                try {
                    val cdnUrl = CloudinaryHelper.uploadBitmapToCloudinary(scaledBitmap, folderName)
                    if (!cdnUrl.isNullOrBlank()) {
                        mainHandler.post { onResult(cdnUrl) }
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.w("MongoDBHelper", "Cloudinary background upload warning: ${e.message}")
                }

                // Fallback: Base64 Data URL if Cloudinary upload fails
                val outputStream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
                val byteArray = outputStream.toByteArray()
                val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                val dataUrl = "data:image/jpeg;base64,$base64"

                mainHandler.post { onResult(dataUrl) }
            }
        } catch (e: Exception) {
            Log.e("MongoDBHelper", "Error processing image URI: ${e.message}")
            onResult("")
        }
    }

    fun uploadImageToStorage(context: Context?, uriStr: String, folderName: String, onComplete: (String) -> Unit) {
        if (uriStr.isBlank()) {
            onComplete("")
            return
        }
        if (uriStr.startsWith("https://res.cloudinary.com/")) {
            onComplete(uriStr)
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                var bitmap: Bitmap? = null
                if (uriStr.startsWith("data:image/")) {
                    try {
                        val base64Data = uriStr.substringAfter("base64,")
                        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                        if (bytes.isNotEmpty()) {
                            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }
                    } catch (_: Exception) {}
                } else if (uriStr.startsWith("/")) {
                    val file = File(uriStr)
                    if (file.exists()) {
                        bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    }
                } else if (context != null && (uriStr.startsWith("content://") || uriStr.startsWith("file://"))) {
                    try {
                        val parsedUri = Uri.parse(uriStr)
                        val inputStream = context.contentResolver.openInputStream(parsedUri)
                        bitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream?.close()
                    } catch (_: Exception) {}
                }

                if (bitmap != null) {
                    val maxDim = 1200
                    var width = bitmap.width
                    var height = bitmap.height
                    if (width > maxDim || height > maxDim) {
                        val ratio = width.toFloat() / height.toFloat()
                        if (ratio > 1) {
                            width = maxDim
                            height = (maxDim / ratio).toInt()
                        } else {
                            height = maxDim
                            width = (maxDim * ratio).toInt()
                        }
                        bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
                    }

                    // 1. Primary: Upload to Cloudinary CDN
                    val cdnUrl = CloudinaryHelper.uploadBitmapToCloudinary(bitmap, folderName)
                    if (!cdnUrl.isNullOrBlank()) {
                        mainHandler.post { onComplete(cdnUrl) }
                        return@launch
                    }

                    // 2. Base64 fallback if Cloudinary upload fails
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
                    val byteArray = outputStream.toByteArray()
                    val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                    val dataUrl = "data:image/jpeg;base64,$base64"

                    mainHandler.post { onComplete(dataUrl) }
                    return@launch
                }
            } catch (e: Exception) {
                Log.e("MongoDBHelper", "Error in uploadImageToStorage: ${e.message}")
            }

            val safeFallback = if (uriStr.startsWith("http://") || uriStr.startsWith("https://") || uriStr.startsWith("data:image/")) {
                uriStr
            } else {
                ""
            }
            mainHandler.post { onComplete(safeFallback) }
        }
    }

    // --- 📅 EVENTS MONGODB OPERATIONS ---
    fun saveEvent(event: CollegeEvent, onComplete: (Boolean) -> Unit) {
        scope.launch {
            try {
                val col = getCollection("events")
                val json = gson.toJson(event)
                val doc = Document.parse(json).append("_id", event.id)
                col?.replaceOne(Filters.eq("_id", event.id), doc, ReplaceOptions().upsert(true))
                mainHandler.post { onComplete(true) }
            } catch (e: Exception) {
                mainHandler.post { onComplete(false) }
            }
        }
    }

    fun deleteEvent(eventId: String, onComplete: (Boolean) -> Unit) {
        scope.launch {
            try {
                getCollection("events")?.deleteOne(Filters.eq("_id", eventId))
                mainHandler.post { onComplete(true) }
            } catch (_: Exception) {
                mainHandler.post { onComplete(false) }
            }
        }
    }

    fun listenToEvents(onDataChanged: (List<CollegeEvent>) -> Unit) {
        scope.launch {
            while (isActive) {
                try {
                    val col = getCollection("events")
                    val list = mutableListOf<CollegeEvent>()
                    col?.find()?.forEach { doc ->
                        doc.remove("_id")
                        val event = gson.fromJson(doc.toJson(), CollegeEvent::class.java)
                        if (event != null) list.add(event)
                    }
                    mainHandler.post { onDataChanged(list) }
                } catch (_: Exception) {}
                delay(5000)
            }
        }
    }

    // --- 👨‍🎓 STUDENTS MONGODB OPERATIONS ---
    fun saveStudent(student: StudentUser, onComplete: (Boolean) -> Unit) {
        scope.launch {
            try {
                val col = getCollection("students")
                val safeEmail = student.email.trim().lowercase()
                val json = gson.toJson(student)
                val doc = Document.parse(json).append("_id", safeEmail)
                col?.replaceOne(Filters.eq("_id", safeEmail), doc, ReplaceOptions().upsert(true))
                mainHandler.post { onComplete(true) }
            } catch (_: Exception) {
                mainHandler.post { onComplete(false) }
            }
        }
    }

    fun listenToStudents(onDataChanged: (List<StudentUser>) -> Unit) {
        scope.launch {
            while (isActive) {
                try {
                    val col = getCollection("students")
                    val list = mutableListOf<StudentUser>()
                    col?.find()?.forEach { doc ->
                        doc.remove("_id")
                        val student = gson.fromJson(doc.toJson(), StudentUser::class.java)
                        if (student != null) list.add(student)
                    }
                    mainHandler.post { onDataChanged(list) }
                } catch (_: Exception) {}
                delay(5000)
            }
        }
    }

    // 👤 FETCH PUBLIC STUDENT PROFILE WITH PARTICIPATED EVENTS & CERTIFICATES
    fun fetchPublicStudentProfile(studentEmail: String, onComplete: (PublicStudentProfile?) -> Unit) {
        val safeEmail = studentEmail.trim().lowercase()
        scope.launch {
            try {
                val studentDoc = getCollection("students")?.find(Filters.eq("_id", safeEmail))?.firstOrNull()
                val studentUser = if (studentDoc != null) {
                    studentDoc.remove("_id")
                    gson.fromJson(studentDoc.toJson(), StudentUser::class.java)
                } else null

                // Fetch registered events for this student
                val registeredEventIds = mutableListOf<String>()
                getCollection("participants")?.find(Filters.regex("_id", "_$safeEmail$"))?.forEach { doc ->
                    val eid = doc.getString("eventId")
                    if (!eid.isNullOrBlank()) registeredEventIds.add(eid)
                }

                // Fetch event details
                val participatedEvents = mutableListOf<CollegeEvent>()
                if (registeredEventIds.isNotEmpty()) {
                    getCollection("events")?.find(Filters.`in`("_id", registeredEventIds))?.forEach { doc ->
                        doc.remove("_id")
                        val ev = gson.fromJson(doc.toJson(), CollegeEvent::class.java)
                        if (ev != null) participatedEvents.add(ev)
                    }
                }

                // Fetch student posts & certificates
                val certPosts = mutableListOf<StudentPost>()
                getCollection("student_posts")?.find(Filters.eq("studentEmail", safeEmail))?.forEach { doc ->
                    doc.remove("_id")
                    val post = gson.fromJson(doc.toJson(), StudentPost::class.java)
                    if (post != null) certPosts.add(post)
                }

                val publicProfile = studentUser?.toPublicProfile(
                    participatedEvents = participatedEvents,
                    certifications = certPosts
                ) ?: PublicStudentProfile(
                    id = safeEmail,
                    name = safeEmail.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() },
                    email = safeEmail,
                    college = "Campus",
                    department = "Student",
                    year = "Final Year",
                    headline = "Student & Enthusiast",
                    about = "Passionate student focused on learning and building real-world projects.",
                    profilePhotoUri = "",
                    coverPhotoUri = "",
                    githubLink = "",
                    isOpenToWork = false,
                    phoneNumber = "",
                    skills = emptyList(),
                    participatedEvents = participatedEvents,
                    certifications = certPosts
                )

                mainHandler.post { onComplete(publicProfile) }
            } catch (_: Exception) {
                mainHandler.post { onComplete(null) }
            }
        }
    }

    // --- 🏛️ COLLEGES MONGODB OPERATIONS ---
    fun saveCollege(collegeId: String, collegeMap: Map<String, Any>, onComplete: (Boolean) -> Unit) {
        scope.launch {
            try {
                val col = getCollection("colleges")
                val doc = Document(collegeMap).append("_id", collegeId)
                col?.replaceOne(Filters.eq("_id", collegeId), doc, ReplaceOptions().upsert(true))
                mainHandler.post { onComplete(true) }
            } catch (_: Exception) {
                mainHandler.post { onComplete(false) }
            }
        }
    }

    fun listenToColleges(onDataChanged: (List<Map<String, Any>>) -> Unit) {
        scope.launch {
            while (isActive) {
                try {
                    val col = getCollection("colleges")
                    val list = mutableListOf<Map<String, Any>>()
                    col?.find()?.forEach { doc ->
                        val map = HashMap<String, Any>()
                        doc.forEach { (k, v) -> if (k != "_id" && v != null) map[k] = v }
                        list.add(map)
                    }
                    mainHandler.post { onDataChanged(list) }
                } catch (_: Exception) {}
                delay(8000)
            }
        }
    }

    // --- 👨‍🏫 FACULTIES MONGODB OPERATIONS ---
    fun saveFaculty(facultyMap: Map<String, Any>, onComplete: (Boolean) -> Unit) {
        scope.launch {
            try {
                val col = getCollection("college_faculties")
                val email = (facultyMap["collegeEmail"] as? String ?: "").trim().lowercase()
                val doc = Document(facultyMap).append("_id", email)
                col?.replaceOne(Filters.eq("_id", email), doc, ReplaceOptions().upsert(true))
                mainHandler.post { onComplete(true) }
            } catch (_: Exception) {
                mainHandler.post { onComplete(false) }
            }
        }
    }

    fun listenToFaculties(onDataChanged: (List<Map<String, Any>>) -> Unit) {
        scope.launch {
            while (isActive) {
                try {
                    val col = getCollection("college_faculties")
                    val list = mutableListOf<Map<String, Any>>()
                    col?.find()?.forEach { doc ->
                        val map = HashMap<String, Any>()
                        doc.forEach { (k, v) -> if (k != "_id" && v != null) map[k] = v }
                        list.add(map)
                    }
                    mainHandler.post { onDataChanged(list) }
                } catch (_: Exception) {}
                delay(8000)
            }
        }
    }

    fun deleteFaculty(email: String, onComplete: (Boolean) -> Unit = {}) {
        val safeEmail = email.trim().lowercase()
        scope.launch {
            try {
                if (safeEmail.isNotBlank()) {
                    val filter = Filters.or(
                        Filters.eq("_id", safeEmail),
                        Filters.eq("collegeEmail", safeEmail),
                        Filters.eq("email", safeEmail)
                    )
                    getCollection("college_faculties")?.deleteMany(filter)
                    getCollection("colleges")?.deleteMany(filter)
                    getCollection("users")?.deleteMany(filter)
                }
                mainHandler.post { onComplete(true) }
            } catch (_: Exception) {
                mainHandler.post { onComplete(false) }
            }
        }
    }

    fun deleteCollegeAndAssociatedFaculties(collegeEmail: String, collegeName: String, onComplete: (Boolean) -> Unit = {}) {
        val safeEmail = collegeEmail.trim().lowercase()
        val safeCollegeName = collegeName.trim()
        scope.launch {
            try {
                // Delete from 'colleges' collection
                val collegeFilters = mutableListOf<org.bson.conversions.Bson>()
                if (safeEmail.isNotBlank()) {
                    collegeFilters.add(Filters.eq("_id", safeEmail))
                    collegeFilters.add(Filters.eq("collegeEmail", safeEmail))
                }
                if (safeCollegeName.isNotBlank()) {
                    val quoted = java.util.regex.Pattern.quote(safeCollegeName)
                    collegeFilters.add(Filters.regex("collegeName", "^\\s*" + quoted + "\\s*$", "i"))
                    collegeFilters.add(Filters.regex("name", "^\\s*" + quoted + "\\s*$", "i"))
                    collegeFilters.add(Filters.regex("college", "^\\s*" + quoted + "\\s*$", "i"))
                }
                if (collegeFilters.isNotEmpty()) {
                    getCollection("colleges")?.deleteMany(Filters.or(collegeFilters))
                }

                // Delete all associated faculties from 'college_faculties' collection
                val facultyFilters = mutableListOf<org.bson.conversions.Bson>()
                if (safeEmail.isNotBlank()) {
                    facultyFilters.add(Filters.eq("_id", safeEmail))
                    facultyFilters.add(Filters.eq("collegeEmail", safeEmail))
                }
                if (safeCollegeName.isNotBlank()) {
                    val quoted = java.util.regex.Pattern.quote(safeCollegeName)
                    facultyFilters.add(Filters.regex("collegeName", "^\\s*" + quoted + "\\s*$", "i"))
                    facultyFilters.add(Filters.regex("college", "^\\s*" + quoted + "\\s*$", "i"))
                }
                if (facultyFilters.isNotEmpty()) {
                    getCollection("college_faculties")?.deleteMany(Filters.or(facultyFilters))
                }

                // Delete associated events from 'events' collection
                if (safeCollegeName.isNotBlank()) {
                    val quoted = java.util.regex.Pattern.quote(safeCollegeName)
                    getCollection("events")?.deleteMany(
                        Filters.or(
                            Filters.regex("college", "^\\s*" + quoted + "\\s*$", "i"),
                            Filters.regex("collegeName", "^\\s*" + quoted + "\\s*$", "i")
                        )
                    )
                }

                // Delete from 'users' collection if present
                if (safeEmail.isNotBlank() || safeCollegeName.isNotBlank()) {
                    val userFilters = mutableListOf<org.bson.conversions.Bson>()
                    if (safeEmail.isNotBlank()) {
                        userFilters.add(Filters.eq("_id", safeEmail))
                        userFilters.add(Filters.eq("email", safeEmail))
                        userFilters.add(Filters.eq("collegeEmail", safeEmail))
                    }
                    if (safeCollegeName.isNotBlank()) {
                        val quoted = java.util.regex.Pattern.quote(safeCollegeName)
                        userFilters.add(Filters.regex("collegeName", "^\\s*" + quoted + "\\s*$", "i"))
                        userFilters.add(Filters.regex("college", "^\\s*" + quoted + "\\s*$", "i"))
                    }
                    if (userFilters.isNotEmpty()) {
                        getCollection("users")?.deleteMany(Filters.or(userFilters))
                    }
                }

                mainHandler.post { onComplete(true) }
            } catch (_: Exception) {
                mainHandler.post { onComplete(false) }
            }
        }
    }

    // --- 🤝 PARTICIPANTS MONGODB OPERATIONS ---
    fun saveParticipant(participant: EventParticipant, onComplete: (Boolean) -> Unit) {
        scope.launch {
            try {
                val col = getCollection("participants")
                val key = "${participant.eventId}_${participant.studentEmail.trim().lowercase()}"
                val json = gson.toJson(participant)
                val doc = Document.parse(json).append("_id", key)
                col?.replaceOne(Filters.eq("_id", key), doc, ReplaceOptions().upsert(true))
                mainHandler.post { onComplete(true) }
            } catch (_: Exception) {
                mainHandler.post { onComplete(false) }
            }
        }
    }

    fun removeParticipant(eventId: String, studentEmail: String, onComplete: (Boolean) -> Unit) {
        scope.launch {
            try {
                val key = "${eventId}_${studentEmail.trim().lowercase()}"
                getCollection("participants")?.deleteOne(Filters.eq("_id", key))
                mainHandler.post { onComplete(true) }
            } catch (_: Exception) {
                mainHandler.post { onComplete(false) }
            }
        }
    }

    fun listenToParticipants(onDataChanged: (List<EventParticipant>) -> Unit) {
        scope.launch {
            while (isActive) {
                try {
                    val col = getCollection("participants")
                    val list = mutableListOf<EventParticipant>()
                    col?.find()?.forEach { doc ->
                        doc.remove("_id")
                        val p = gson.fromJson(doc.toJson(), EventParticipant::class.java)
                        if (p != null) list.add(p)
                    }
                    mainHandler.post { onDataChanged(list) }
                } catch (_: Exception) {}
                delay(5000)
            }
        }
    }

    // --- 📝 STUDENT POSTS & CERTIFICATES MONGODB OPERATIONS ---
    fun saveStudentPost(post: StudentPost, onComplete: (Boolean) -> Unit) {
        scope.launch {
            try {
                val col = getCollection("student_posts")
                val json = gson.toJson(post)
                val doc = Document.parse(json).append("_id", post.id)
                col?.replaceOne(Filters.eq("_id", post.id), doc, ReplaceOptions().upsert(true))
                mainHandler.post { onComplete(true) }
            } catch (_: Exception) {
                mainHandler.post { onComplete(false) }
            }
        }
    }

    fun listenToStudentPosts(onDataChanged: (List<StudentPost>) -> Unit) {
        scope.launch {
            while (isActive) {
                try {
                    val col = getCollection("student_posts")
                    val list = mutableListOf<StudentPost>()
                    col?.find()?.forEach { doc ->
                        doc.remove("_id")
                        val post = gson.fromJson(doc.toJson(), StudentPost::class.java)
                        if (post != null) list.add(post)
                    }
                    mainHandler.post { onDataChanged(list) }
                } catch (_: Exception) {}
                delay(5000)
            }
        }
    }

    // --- 🔔 IN-APP NOTIFICATIONS MONGODB OPERATIONS ---
    fun saveNotification(notification: AppNotification, onComplete: (Boolean) -> Unit) {
        scope.launch {
            try {
                val col = getCollection("notifications")
                val json = gson.toJson(notification)
                val doc = Document.parse(json).append("_id", notification.id)
                col?.replaceOne(Filters.eq("_id", notification.id), doc, ReplaceOptions().upsert(true))
                mainHandler.post { onComplete(true) }
            } catch (_: Exception) {
                mainHandler.post { onComplete(false) }
            }
        }
    }

    fun listenToNotifications(userId: String, userRole: String, onDataChanged: (List<AppNotification>) -> Unit) {
        scope.launch {
            while (isActive) {
                try {
                    val col = getCollection("notifications")
                    val list = mutableListOf<AppNotification>()
                    val filter = Filters.or(
                        Filters.eq("userId", userId.trim().lowercase()),
                        Filters.eq("userId", "ALL"),
                        Filters.and(
                            Filters.eq("userRole", userRole),
                            Filters.ne("userId", userId.trim().lowercase())
                        )
                    )
                    col?.find(filter)?.forEach { doc ->
                        doc.remove("_id")
                        val notification = gson.fromJson(doc.toJson(), AppNotification::class.java)
                        if (notification != null) list.add(notification)
                    }
                    val sorted = list.sortedByDescending { it.createdAt }
                    mainHandler.post { onDataChanged(sorted) }
                } catch (_: Exception) {}
                delay(3000)
            }
        }
    }

    fun markNotificationAsRead(notificationId: String, onComplete: (Boolean) -> Unit) {
        scope.launch {
            try {
                val col = getCollection("notifications")
                col?.updateOne(Filters.eq("_id", notificationId), Document("\$set", Document("isRead", true)))
                mainHandler.post { onComplete(true) }
            } catch (_: Exception) {
                mainHandler.post { onComplete(false) }
            }
        }
    }

    fun markAllNotificationsAsRead(userId: String, onComplete: (Boolean) -> Unit) {
        scope.launch {
            try {
                val col = getCollection("notifications")
                col?.updateMany(Filters.eq("userId", userId.trim().lowercase()), Document("\$set", Document("isRead", true)))
                mainHandler.post { onComplete(true) }
            } catch (_: Exception) {
                mainHandler.post { onComplete(false) }
            }
        }
    }

    // --- 💬 DIRECT CHATS MONGODB OPERATIONS ---
    fun sendDirectMessage(message: ChatMessage, onComplete: (Boolean) -> Unit) {
        scope.launch {
            try {
                val col = getCollection("direct_chats")
                val u1 = message.senderEmail.trim().lowercase()
                val u2 = message.receiverEmail.trim().lowercase()
                val chatId = if (u1 < u2) "${u1}_$u2" else "${u2}_$u1"
                val json = gson.toJson(message)
                val doc = Document.parse(json)
                    .append("_id", message.id)
                    .append("chatId", chatId)
                col?.replaceOne(Filters.eq("_id", message.id), doc, ReplaceOptions().upsert(true))
                mainHandler.post { onComplete(true) }
            } catch (_: Exception) {
                mainHandler.post { onComplete(false) }
            }
        }
    }

    fun listenToDirectMessages(user1: String, user2: String, onUpdate: (List<ChatMessage>) -> Unit) {
        scope.launch {
            val u1 = user1.trim().lowercase()
            val u2 = user2.trim().lowercase()
            val chatId = if (u1 < u2) "${u1}_$u2" else "${u2}_$u1"

            while (isActive) {
                try {
                    val col = getCollection("direct_chats")
                    val list = mutableListOf<ChatMessage>()
                    col?.find(Filters.eq("chatId", chatId))?.forEach { doc ->
                        doc.remove("_id")
                        doc.remove("chatId")
                        val msg = gson.fromJson(doc.toJson(), ChatMessage::class.java)
                        if (msg != null) list.add(msg)
                    }
                    val sorted = list.sortedBy { it.timestamp }
                    mainHandler.post { onUpdate(sorted) }
                } catch (_: Exception) {}
                delay(2000)
            }
        }
    }

    fun listenToAllIncomingMessages(userEmail: String, onNewMessage: (String, String) -> Unit) {
        scope.launch {
            val safeEmail = userEmail.trim().lowercase()
            var lastSeenTimestamp = System.currentTimeMillis()

            while (isActive) {
                try {
                    val col = getCollection("direct_chats")
                    col?.find(Filters.and(
                        Filters.eq("receiverEmail", safeEmail),
                        Filters.gt("timestamp", lastSeenTimestamp)
                    ))?.forEach { doc ->
                        val sender = doc.getString("senderEmail") ?: ""
                        val text = doc.getString("messageText") ?: ""
                        val ts = doc.getLong("timestamp") ?: 0L
                        if (ts > lastSeenTimestamp) {
                            lastSeenTimestamp = ts
                            mainHandler.post { onNewMessage(sender, text) }
                        }
                    }
                } catch (_: Exception) {}
                delay(2000)
            }
        }
    }

    // --- 🔐 DYNAMIC MONGODB AUTHENTICATION & ROLE-BASED ACCESS CONTROL (RBAC) ---

    /**
     * Authenticates Super Admin credentials dynamically against the MongoDB 'admins' collection.
     */
    fun authenticateSuperAdmin(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        val safeEmail = email.trim().lowercase()
        scope.launch {
            try {
                withTimeoutOrNull(4000L) {
                    val col = getCollection("admins")
                    if (col == null) {
                        mainHandler.post { onResult(false, "Database connection unavailable. Please check network/Atlas access.") }
                        return@withTimeoutOrNull
                    }
                    val adminDoc = col.find(
                        Filters.or(
                            Filters.eq("_id", safeEmail),
                            Filters.eq("email", safeEmail)
                        )
                    ).firstOrNull()

                    if (adminDoc != null) {
                        val dbPassword = adminDoc.getString("password") ?: ""
                        val dbRole = adminDoc.getString("role") ?: "SUPER_ADMIN"
                        if (dbPassword == password && (dbRole.equals("SUPER_ADMIN", ignoreCase = true) || dbRole.equals("ADMIN", ignoreCase = true))) {
                            mainHandler.post { onResult(true, null) }
                            return@withTimeoutOrNull
                        } else {
                            mainHandler.post { onResult(false, "Invalid Super Admin password") }
                            return@withTimeoutOrNull
                        }
                    }
                    mainHandler.post { onResult(false, "Super Admin account not found in database") }
                } ?: run {
                    mainHandler.post { onResult(false, "Database request timed out (Atlas cluster unreachable).") }
                }
            } catch (e: Throwable) {
                mainHandler.post { onResult(false, e.message ?: "Authentication failed (Timeout)") }
            }
        }
    }

    /**
     * Persists Super Admin profile (name, avatar photo, headline, about) into the
     * MongoDB 'admins' collection. Uses $set so existing credentials are preserved.
     */
    fun saveSuperAdminProfile(
        email: String,
        name: String,
        profilePhotoUri: String,
        headline: String,
        about: String,
        onComplete: (Boolean) -> Unit
    ) {
        val safeEmail = email.trim().lowercase()
        scope.launch {
            try {
                val col = getCollection("admins")
                val update = Document("\$set", Document("email", safeEmail)
                    .append("name", name)
                    .append("profilePhotoUri", profilePhotoUri)
                    .append("headline", headline)
                    .append("about", about)
                    .append("lastUpdated", System.currentTimeMillis()))
                col?.updateOne(
                    Filters.or(Filters.eq("_id", safeEmail), Filters.eq("email", safeEmail)),
                    update,
                    UpdateOptions().upsert(true)
                )
                mainHandler.post { onComplete(true) }
            } catch (_: Exception) {
                mainHandler.post { onComplete(false) }
            }
        }
    }

    /**
     * Fetches the Super Admin profile document from the MongoDB 'admins' collection.
     */
    fun fetchSuperAdminProfile(email: String, onComplete: (AdminUser?) -> Unit) {
        val safeEmail = email.trim().lowercase()
        scope.launch {
            try {
                val doc = getCollection("admins")?.find(
                    Filters.or(Filters.eq("_id", safeEmail), Filters.eq("email", safeEmail))
                )?.firstOrNull()

                if (doc != null) {
                    val profile = AdminUser(
                        email = doc.getString("email") ?: safeEmail,
                        name = doc.getString("name") ?: "Super Administrator",
                        role = doc.getString("role") ?: "SUPER_ADMIN",
                        profilePhotoUri = doc.getString("profilePhotoUri") ?: "",
                        headline = doc.getString("headline") ?: "Kampus Super Administrator",
                        about = doc.getString("about") ?: ""
                    )
                    mainHandler.post { onComplete(profile) }
                } else {
                    mainHandler.post { onComplete(null) }
                }
            } catch (_: Exception) {
                mainHandler.post { onComplete(null) }
            }
        }
    }

    /**
     * Fetches Section 1 (Campus Entrance/Banner) and Section 2 (Architectural Blueprint Layout)
     * media images for College Admin from MongoDB Atlas 'colleges' and 'college_faculties' collections.
     */
    fun fetchCollegeMedia(email: String, collegeName: String, onResult: (String, String) -> Unit) {
        val safeEmail = email.trim().lowercase()
        val safeCollege = collegeName.trim()
        scope.launch {
            try {
                var bannerUrl = ""
                var layoutUrl = ""

                // 1. Query 'colleges' collection
                val collegesCol = getCollection("colleges")
                val collegeDoc = collegesCol?.find(
                    Filters.or(
                        Filters.eq("_id", safeEmail),
                        Filters.eq("collegeEmail", safeEmail),
                        Filters.eq("collegeName", safeCollege)
                    )
                )?.firstOrNull()

                if (collegeDoc != null) {
                    bannerUrl = collegeDoc.getString("collegePhotoUri") ?: ""
                    layoutUrl = collegeDoc.getString("campusLayoutUri") ?: ""
                }

                // 2. Fallback to 'college_faculties' collection if blank
                if (bannerUrl.isBlank() || layoutUrl.isBlank()) {
                    val facultiesCol = getCollection("college_faculties")
                    val facultyDoc = facultiesCol?.find(
                        Filters.or(
                            Filters.eq("_id", safeEmail),
                            Filters.eq("collegeEmail", safeEmail)
                        )
                    )?.firstOrNull()

                    if (facultyDoc != null) {
                        if (bannerUrl.isBlank()) bannerUrl = facultyDoc.getString("collegePhotoUri") ?: ""
                        if (layoutUrl.isBlank()) layoutUrl = facultyDoc.getString("campusLayoutUri") ?: ""
                    }
                }

                mainHandler.post { onResult(bannerUrl, layoutUrl) }
            } catch (_: Exception) {
                mainHandler.post { onResult("", "") }
            }
        }
    }

    /**
     * Authenticates Faculty credentials dynamically against the MongoDB 'college_faculties' collection.
     */
    fun authenticateFaculty(email: String, password: String, onResult: (FacultyKYC?, String?) -> Unit) {
        val safeEmail = email.trim().lowercase()
        scope.launch {
            try {
                withTimeoutOrNull(4000L) {
                    val col = getCollection("college_faculties")
                    if (col == null) {
                        mainHandler.post { onResult(null, "Database connection unavailable. Please check network/Atlas access.") }
                        return@withTimeoutOrNull
                    }
                    val doc = col.find(
                        Filters.or(
                            Filters.eq("_id", safeEmail),
                            Filters.eq("collegeEmail", safeEmail)
                        )
                    ).firstOrNull()

                    if (doc != null) {
                        val dbPassword = doc.getString("password") ?: ""
                        if (dbPassword == password) {
                            doc.remove("_id")
                            val faculty = gson.fromJson(doc.toJson(), FacultyKYC::class.java)
                            mainHandler.post { onResult(faculty, null) }
                            return@withTimeoutOrNull
                        } else {
                            mainHandler.post { onResult(null, "Incorrect faculty password") }
                            return@withTimeoutOrNull
                        }
                    }
                    mainHandler.post { onResult(null, "Faculty account not found in database") }
                } ?: run {
                    mainHandler.post { onResult(null, "Database request timed out (Atlas cluster unreachable).") }
                }
            } catch (e: Throwable) {
                mainHandler.post { onResult(null, e.message ?: "Authentication failed (Timeout)") }
            }
        }
    }

    /**
     * Authenticates Student credentials dynamically against the MongoDB 'students' collection.
     */
    fun authenticateStudent(email: String, password: String, onResult: (StudentUser?, String?) -> Unit) {
        val safeEmail = email.trim().lowercase()
        scope.launch {
            try {
                withTimeoutOrNull(4000L) {
                    val col = getCollection("students")
                    if (col == null) {
                        mainHandler.post { onResult(null, "Database connection unavailable. Please check network/Atlas access.") }
                        return@withTimeoutOrNull
                    }
                    val doc = col.find(
                        Filters.or(
                            Filters.eq("_id", safeEmail),
                            Filters.eq("email", safeEmail)
                        )
                    ).firstOrNull()

                    if (doc != null) {
                        val dbPassword = doc.getString("password") ?: ""
                        if (dbPassword == password) {
                            doc.remove("_id")
                            val student = gson.fromJson(doc.toJson(), StudentUser::class.java)
                            mainHandler.post { onResult(student, null) }
                            return@withTimeoutOrNull
                        } else {
                            mainHandler.post { onResult(null, "Incorrect student password") }
                            return@withTimeoutOrNull
                        }
                    }
                    mainHandler.post { onResult(null, "Student account not found in database") }
                } ?: run {
                    mainHandler.post { onResult(null, "Database request timed out (Atlas cluster unreachable).") }
                }
            } catch (e: Throwable) {
                mainHandler.post { onResult(null, e.message ?: "Authentication failed (Timeout)") }
            }
        }
    }

    fun triggerServerEventNotification(eventId: String) {
        scope.launch(Dispatchers.IO) {
            val endpoints = listOf(
                "https://kampus-notification-server.onrender.com/api/notifications/notify-event",
                "http://10.0.2.2:3000/api/notifications/notify-event",
                "http://localhost:3000/api/notifications/notify-event"
            )
            for (ep in endpoints) {
                try {
                    val url = java.net.URL(ep)
                    val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        connectTimeout = 4000
                        readTimeout = 4000
                    }
                    val payload = gson.toJson(mapOf("eventId" to eventId, "timestamp" to System.currentTimeMillis()))
                    conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    Log.d("MongoDBHelper", "Triggered server event notification API ($ep), code: $code")
                    if (code == 200) break
                } catch (_: Exception) {}
            }
        }
    }
}
