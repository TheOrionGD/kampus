package com.example.kampus.models

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object ImageLoaderHelper {
    /**
     * Safely converts image references (URL, base64 data URL, or local file)
     * into a format compatible with Coil AsyncImage.
     */
    fun getSafeImageModel(uriOrDataUrl: String?): Any? {
        if (uriOrDataUrl.isNullOrBlank()) return null
        val trimmed = uriOrDataUrl.trim()
        if (trimmed.startsWith("data:image/") && trimmed.contains("base64,")) {
            try {
                val base64Data = trimmed.substringAfter("base64,")
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                if (bytes.isNotEmpty()) return bytes
            } catch (_: Exception) {
                return null
            }
        }
        if (trimmed.startsWith("/")) {
            val f = File(trimmed)
            if (f.exists()) return f
            return null
        }
        if (trimmed.startsWith("file://")) {
            try {
                val path = android.net.Uri.parse(trimmed).path
                if (path != null) {
                    val f = File(path)
                    if (f.exists()) return f
                    return null
                }
            } catch (_: Exception) {
                return null
            }
        }
        return trimmed
    }
}

enum class FacultyRole {
    COLLEGE_ADMIN,  // Verified by Super Admin; Approves Department Faculties
    DEPT_FACULTY    // Verified by College Admin; Posts Department Events
}

data class AdminUser(
    val email: String,
    val name: String = "Super Administrator",
    val role: String = "SUPER_ADMIN",
    val password: String = "",
    val profilePhotoUri: String = "",
    val headline: String = "Kampus Super Administrator",
    val about: String = "Platform administrator responsible for verifying colleges and managing the Kampus governance hierarchy."
) {
    constructor() : this("")
}

data class StudentUser(
    val name: String,
    val email: String,
    val password: String,
    val college: String,
    val department: String,
    val year: String,
    val skills: List<String> = emptyList(),
    val headline: String = "Aspiring Software Developer | Computer Science Student",
    val about: String = "Passionate student focused on software development, cloud computing, and real-world project building.",
    val profilePhotoUri: String = "",
    val coverPhotoUri: String = "",
    val githubLink: String = "https://github.com",
    var isOpenToWork: Boolean = false,
    val phoneNumber: String = "" // 📱 Mobile number field
) {
    constructor() : this("", "", "", "", "", "")

    fun toPublicProfile(
        participatedEvents: List<CollegeEvent> = emptyList(),
        certifications: List<StudentPost> = emptyList()
    ): PublicStudentProfile {
        return PublicStudentProfile(
            id = email.trim().lowercase(),
            name = name,
            email = email,
            college = college,
            department = department,
            year = year,
            headline = headline,
            about = about,
            profilePhotoUri = profilePhotoUri,
            coverPhotoUri = coverPhotoUri,
            githubLink = githubLink,
            isOpenToWork = isOpenToWork,
            phoneNumber = phoneNumber,
            skills = skills,
            participatedEvents = participatedEvents,
            certifications = certifications
        )
    }
}

data class PublicStudentProfile(
    val id: String,
    val name: String,
    val email: String,
    val college: String,
    val department: String,
    val year: String,
    val headline: String,
    val about: String,
    val profilePhotoUri: String,
    val coverPhotoUri: String,
    val githubLink: String,
    val isOpenToWork: Boolean,
    val phoneNumber: String,
    val skills: List<String> = emptyList(),
    val participatedEvents: List<CollegeEvent> = emptyList(),
    val certifications: List<StudentPost> = emptyList()
) {
    constructor() : this("", "", "", "", "", "", "", "", "", "", "", false, "")
}

data class DeviceFcmToken(
    val userId: String,
    val token: String,
    val role: String = "STUDENT",
    val status: String = "ACTIVE",
    val lastUpdated: Long = System.currentTimeMillis(),
    val deviceModel: String = ""
) {
    constructor() : this("", "")
}

data class StudentPost(
    val id: String,
    val studentEmail: String,
    val studentName: String,
    val studentHeadline: String,
    val postedTime: String,
    val content: String,
    val imageUri: String = "",
    var likesCount: Int = 0
) {
    constructor() : this("", "", "", "", "", "")
}

data class FacultyKYC(
    val name: String,
    val collegeEmail: String,
    val contactNumber: String,
    val password: String,
    val collegeName: String,
    val department: String,
    val designation: String,
    val accreditation: String = "",
    val collegeWebsite: String = "",
    val collegePhotoUri: String = "",       // 🏛️ For Campus Entrance / Main Banner
    val campusLayoutUri: String = "",       // 🗺️ For Blueprint / Campus Layout Map (Separate!)
    val idProofUri: String = "",
    val role: FacultyRole = FacultyRole.DEPT_FACULTY,
    val isVerifiedBySuperAdmin: Boolean = false,
    val isVerifiedByCollegeAdmin: Boolean = false,
    val profilePhotoUri: String = "",
    val headline: String = "",
    val about: String = ""
) {
    constructor() : this("", "", "", "", "", "", "")
}

data class CollegeEvent(
    val id: String,
    val title: String,
    val college: String,
    val category: String,
    val deadline: String,
    val eventDate: String,      // Format: d/M/yy or dd/MM/yyyy (e.g. 1/9/26)
    val startTime: String = "10:00 AM", // e.g. 10:00 AM
    val endTime: String = "12:00 PM",   // e.g. 12:00 PM (After this time, event expires)
    val mode: String,
    val eligibility: String,
    val fee: String,
    val coordinatorName: String,
    val coordinatorRole: String,
    val postedTime: String,
    val announcementNote: String,
    val fullDescription: String,
    val prizePool: String,
    val targetDept: String,
    val posterUrl: String,
    val externalRegLink: String = "",
    val customField1Label: String = "",
    val customField1Value: String = "",
    val customField2Label: String = "",
    val customField2Value: String = ""
) {
    constructor() : this("", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "")

    // AUTOMATIC EXPIRY CALCULATION FUNCTION (Validates exact date & end-time)
    fun isExpired(): Boolean {
        return try {
            val now = Calendar.getInstance().time
            val fullEndDateTimeStr = "$eventDate $endTime".trim()

            val formats = listOf(
                SimpleDateFormat("d/M/yy hh:mm a", Locale.ENGLISH),
                SimpleDateFormat("d/M/yy HH:mm", Locale.ENGLISH),
                SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.ENGLISH),
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ENGLISH),
                SimpleDateFormat("d/M/yyyy hh:mm a", Locale.ENGLISH),
                SimpleDateFormat("d/M/yyyy HH:mm", Locale.ENGLISH),
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH),
                SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.ENGLISH),
                SimpleDateFormat("d/M/yy", Locale.ENGLISH),
                SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH),
                SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            )

            for (format in formats) {
                try {
                    format.isLenient = true
                    val parsedDate = format.parse(fullEndDateTimeStr)
                    if (parsedDate != null) {
                        return now.after(parsedDate)
                    }
                } catch (_: Exception) {}
            }
            false
        } catch (e: Exception) {
            false
        }
    }
}

data class EventParticipant(
    val eventId: String,
    val studentName: String,
    val studentEmail: String,
    val collegeName: String,
    val department: String,
    val year: String,
    val skills: List<String> = emptyList()
) {
    constructor() : this("", "", "", "", "", "")
}

// 💬 Direct Chat Message Model
data class ChatMessage(
    val id: String = "",
    val senderEmail: String = "",
    val receiverEmail: String = "",
    val messageText: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "", "", 0L)
}

// 🔔 In-App Notification Model (Database-driven push notifications)
data class AppNotification(
    val id: String,
    val userId: String, // student email, faculty email, or "ALL" for broadcast
    val userRole: String, // "STUDENT", "FACULTY", "SUPER_ADMIN", "ALL"
    val title: String,
    val message: String,
    val type: String, // "EVENT_PUBLISHED", "EVENT_DEADLINE", "EVENT_REMINDER", "ANNOUNCEMENT", "CHAT"
    val eventId: String = "",
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "", "", "", "")
}

enum class ScreenRoute {
    SPLASH,
    PERMISSIONS,
    LOGIN,
    STUDENT_SETUP,
    FACULTY_KYC,
    SUPER_ADMIN,
    MAIN_FEED,
    FACULTY_PORTAL
}

class AppDataManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("kampus_app_db_clean", Context.MODE_PRIVATE)
    private val gson = Gson()

    data class AuthSession(
        val role: String, // "STUDENT", "FACULTY", "SUPER_ADMIN"
        val email: String
    )

    fun saveAuthSession(role: String, email: String) {
        prefs.edit()
            .putString("auth_session_role", role)
            .putString("auth_session_email", email.trim().lowercase())
            .apply()
    }

    fun saveCurrentStudent(student: StudentUser) {
        prefs.edit()
            .putString("auth_session_role", "STUDENT")
            .putString("auth_session_email", student.email.trim().lowercase())
            .putString("current_student_json", gson.toJson(student))
            .apply()
    }

    fun getCurrentStudent(): StudentUser? {
        val json = prefs.getString("current_student_json", null) ?: return null
        return try {
            gson.fromJson(json, StudentUser::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun saveCurrentFaculty(faculty: FacultyKYC) {
        prefs.edit()
            .putString("auth_session_role", "FACULTY")
            .putString("auth_session_email", faculty.collegeEmail.trim().lowercase())
            .putString("current_faculty_json", gson.toJson(faculty))
            .apply()
    }

    fun getCurrentFaculty(): FacultyKYC? {
        val json = prefs.getString("current_faculty_json", null) ?: return null
        return try {
            gson.fromJson(json, FacultyKYC::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun getAuthSession(): AuthSession? {
        val role = prefs.getString("auth_session_role", null)
        val email = prefs.getString("auth_session_email", null)
        if (!role.isNullOrBlank() && !email.isNullOrBlank()) {
            return AuthSession(role, email)
        }

        // Fallback 1: check if current_student_json is saved
        val student = getCurrentStudent()
        if (student != null && student.email.isNotBlank()) {
            saveAuthSession("STUDENT", student.email)
            return AuthSession("STUDENT", student.email)
        }

        // Fallback 2: check if current_faculty_json is saved
        val faculty = getCurrentFaculty()
        if (faculty != null && faculty.collegeEmail.isNotBlank()) {
            saveAuthSession("FACULTY", faculty.collegeEmail)
            return AuthSession("FACULTY", faculty.collegeEmail)
        }

        return null
    }

    fun clearAuthSession() {
        prefs.edit()
            .remove("auth_session_role")
            .remove("auth_session_email")
            .remove("current_student_json")
            .remove("current_faculty_json")
            .apply()
    }

    fun saveStudents(list: List<StudentUser>) {
        prefs.edit().putString("students_list", gson.toJson(list)).apply()
    }

    fun getStudents(): MutableList<StudentUser> {
        val json = prefs.getString("students_list", null)
        if (!json.isNullOrBlank()) {
            val type = object : TypeToken<MutableList<StudentUser>>() {}.type
            val list = gson.fromJson<MutableList<StudentUser>>(json, type)
            if (!list.isNullOrEmpty()) return list
        }
        return mutableListOf(
            StudentUser(
                name = "Aravind Swaminathan",
                email = "student1@kampus.edu",
                password = "student123",
                college = "Kampus Institute of Technology",
                department = "Computer Science & Engineering",
                year = "3rd Year",
                skills = listOf("Kotlin", "Android", "UI/UX", "Python"),
                headline = "Android Developer & AI Enthusiast",
                about = "Passionate CS student competing in hackathons and symposiums.",
                phoneNumber = "9876543220",
                isOpenToWork = true
            ),
            StudentUser(
                name = "Priya Natarajan",
                email = "student2@kampus.edu",
                password = "student123",
                college = "Kampus Institute of Technology",
                department = "Information Technology",
                year = "Final Year",
                skills = listOf("React", "Node.js", "Cloud", "Cybersecurity"),
                headline = "Full-stack Developer & Tech Lead",
                about = "Building real-world web and mobile applications.",
                phoneNumber = "9876543221",
                isOpenToWork = false
            )
        )
    }

    fun saveFaculties(allFaculties: List<FacultyKYC>) {
        prefs.edit().putString("all_faculties_list", gson.toJson(allFaculties)).apply()
    }

    fun getFaculties(): MutableList<FacultyKYC> {
        val json = prefs.getString("all_faculties_list", null)
        if (!json.isNullOrBlank()) {
            val type = object : TypeToken<MutableList<FacultyKYC>>() {}.type
            val list = gson.fromJson<MutableList<FacultyKYC>>(json, type)
            if (!list.isNullOrEmpty()) return list
        }
        return mutableListOf(
            FacultyKYC(
                name = "Dr. Rajesh Kumar",
                collegeName = "Kampus Institute of Technology",
                department = "Administration",
                designation = "Dean / College Admin",
                collegeEmail = "collegeadmin@kampus.edu",
                contactNumber = "9876543210",
                collegeWebsite = "https://kampus.edu",
                accreditation = "NAAC A++",
                collegePhotoUri = "https://images.unsplash.com/photo-1562774053-701939374585?q=80&w=1000",
                password = "admin123",
                role = FacultyRole.COLLEGE_ADMIN,
                isVerifiedBySuperAdmin = true,
                isVerifiedByCollegeAdmin = true
            ),
            FacultyKYC(
                name = "Prof. Ananya Sharma",
                collegeName = "Kampus Institute of Technology",
                department = "Computer Science & Engineering",
                designation = "Assistant Professor & Event Convener",
                collegeEmail = "faculty@kampus.edu",
                contactNumber = "9876543211",
                collegeWebsite = "https://kampus.edu",
                accreditation = "NAAC A++",
                password = "faculty123",
                role = FacultyRole.DEPT_FACULTY,
                isVerifiedBySuperAdmin = true,
                isVerifiedByCollegeAdmin = true
            )
        )
    }

    fun saveEvents(list: List<CollegeEvent>) {
        prefs.edit().putString("college_events", gson.toJson(list)).apply()
    }

    fun getEvents(): MutableList<CollegeEvent> {
        val json = prefs.getString("college_events", null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<CollegeEvent>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    fun saveParticipants(list: List<EventParticipant>) {
        prefs.edit().putString("event_participants", gson.toJson(list)).apply()
    }

    fun getParticipants(): MutableList<EventParticipant> {
        val json = prefs.getString("event_participants", null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<EventParticipant>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    fun saveStudentPosts(list: List<StudentPost>) {
        prefs.edit().putString("student_posts", gson.toJson(list)).apply()
    }

    fun getStudentPosts(): MutableList<StudentPost> {
        val json = prefs.getString("student_posts", null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<StudentPost>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }
}