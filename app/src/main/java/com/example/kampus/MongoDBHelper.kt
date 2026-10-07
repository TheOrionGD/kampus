package com.example.kampus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.example.kampus.models.ChatMessage
import com.example.kampus.models.CollegeEvent
import com.example.kampus.models.DeviceFcmToken
import com.example.kampus.models.EventParticipant
import com.example.kampus.models.FacultyKYC
import com.example.kampus.models.FacultyRole
import com.example.kampus.models.PublicStudentProfile
import com.example.kampus.models.StudentPost
import com.example.kampus.models.StudentUser
import com.google.gson.Gson
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bson.Document
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

object MongoDBHelper {
    private const val DB_NAME = "Kampus"
    // Using direct cluster connection string with SSL and replica set for universal Android runtime compatibility
    private const val CONNECTION_URI =
        "mongodb://godfreytrprof_db_user:6JjxTbgSJbzjBkv4@ac-vyntutv-shard-00-00.rbxbuxe.mongodb.net:27017,ac-vyntutv-shard-00-01.rbxbuxe.mongodb.net:27017,ac-vyntutv-shard-00-02.rbxbuxe.mongodb.net:27017/Kampus?ssl=true&replicaSet=atlas-vyntutv-shard-0&authSource=admin&retryWrites=true&w=majority"

    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private var mongoClient: MongoClient? = null
    private var database: MongoDatabase? = null

    init {
        try {
            val settings = MongoClientSettings.builder()
                .applyConnectionString(ConnectionString(CONNECTION_URI))
                .applyToSocketSettings { builder ->
                    builder.connectTimeout(15, TimeUnit.SECONDS)
                    builder.readTimeout(15, TimeUnit.SECONDS)
                }
                .build()
            mongoClient = MongoClients.create(settings)
            database = mongoClient?.getDatabase(DB_NAME)
        } catch (_: Exception) {}
    }

    private fun getDb(): MongoDatabase? {
        if (database == null) {
            try {
                val settings = MongoClientSettings.builder()
                    .applyConnectionString(ConnectionString(CONNECTION_URI))
                    .build()
                mongoClient = MongoClients.create(settings)
                database = mongoClient?.getDatabase(DB_NAME)
            } catch (_: Exception) {}
        }
        return database
    }

    private fun getCollection(name: String): MongoCollection<Document>? {
        return getDb()?.getCollection(name)
    }

    // 🔔 FCM DEVICE TOKEN REGISTRATION & MANAGEMENT
    fun registerDeviceFcmToken(token: String, userEmail: String = "", role: String = "STUDENT") {
        if (token.isBlank()) return
        scope.launch {
            try {
                val col = getCollection("fcm_tokens")
                val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
                val tokenObj = DeviceFcmToken(
                    userId = userEmail.trim().lowercase(),
                    token = token,
                    role = role,
                    status = "ACTIVE",
                    lastUpdated = System.currentTimeMillis(),
                    deviceModel = deviceModel
                )
                val json = gson.toJson(tokenObj)
                val doc = Document.parse(json).append("_id", token)
                col?.replaceOne(Filters.eq("_id", token), doc, ReplaceOptions().upsert(true))
            } catch (_: Exception) {}
        }
    }

    // 🖼️ UNIVERSAL IMAGE CLOUD STORAGE HELPER (Compressed JPEG Base64 data URL for global cross-device rendering)
    fun uploadImageToStorage(context: Context?, uriStr: String, folderName: String, onComplete: (String) -> Unit) {
        if (uriStr.isBlank()) {
            onComplete("")
            return
        }
        if (uriStr.startsWith("http://") || uriStr.startsWith("https://") || uriStr.startsWith("data:image/")) {
            onComplete(uriStr)
            return
        }

        scope.launch {
            try {
                var bitmap: Bitmap? = null
                if (uriStr.startsWith("/")) {
                    val file = File(uriStr)
                    if (file.exists()) {
                        bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    }
                } else if (context != null) {
                    val parsedUri = Uri.parse(uriStr)
                    val inputStream: InputStream? = context.contentResolver.openInputStream(parsedUri)
                    bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                }

                if (bitmap != null) {
                    // Scale down image to optimal size (max width/height 1000px) to keep documents lightweight (~50-100KB)
                    val maxDim = 1000
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

                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    val byteArray = outputStream.toByteArray()
                    val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                    val dataUrl = "data:image/jpeg;base64,$base64"

                    mainHandler.post { onComplete(dataUrl) }
                    return@launch
                }
            } catch (_: Exception) {}

            mainHandler.post { onComplete(uriStr) }
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
                delay(3000)
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
                delay(3000)
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
                delay(3000)
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
                delay(3000)
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
                delay(3000)
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
                delay(3000)
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
}
