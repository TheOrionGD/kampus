package com.example.kampus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kampus.models.*
import com.example.kampus.screens.*
import com.example.kampus.ui.theme.KampusTheme
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {
    private var pendingDeepLinkEventId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create notification channels for rich alerts
        NotificationHelper.createNotificationChannel(applicationContext)

        // Universal topic subscription for campus-wide alerts
        try {
            FirebaseMessaging.getInstance().subscribeToTopic("college_events")
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (!token.isNullOrBlank()) {
                    MongoDBHelper.registerDeviceFcmToken(token)
                }
            }
        } catch (_: Exception) {}

        // Handle initial deep-linking event ID from notification click
        pendingDeepLinkEventId = intent?.getStringExtra("eventId")

        setContent {
            KampusTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KampusApp(initialDeepLinkEventId = pendingDeepLinkEventId)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val eventId = intent.getStringExtra("eventId")
        if (!eventId.isNullOrBlank()) {
            pendingDeepLinkEventId = eventId
        }
    }
}

@Composable
fun KampusApp(initialDeepLinkEventId: String? = null) {
    val context = LocalContext.current
    val dataManager = remember { AppDataManager(context) }

    // Request Notification permission for Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        MongoDBHelper.prewarmConnection()
    }

    var route by remember { mutableStateOf(ScreenRoute.SPLASH) }

    val registeredStudents = remember { mutableStateListOf<StudentUser>().apply { addAll(dataManager.getStudents()) } }
    val allFaculties = remember { mutableStateListOf<FacultyKYC>().apply { addAll(dataManager.getFaculties()) } }
    val allEvents = remember { mutableStateListOf<CollegeEvent>().apply { addAll(dataManager.getEvents()) } }
    val allParticipants = remember { mutableStateListOf<EventParticipant>().apply { addAll(dataManager.getParticipants()) } }
    val allPosts = remember { mutableStateListOf<StudentPost>().apply { addAll(dataManager.getStudentPosts()) } }

    var currentStudent by remember { mutableStateOf<StudentUser?>(null) }
    var currentFaculty by remember { mutableStateOf<FacultyKYC?>(null) }
    var selectedEvent by remember { mutableStateOf<CollegeEvent?>(null) }

    var targetDeepLinkEventId by remember { mutableStateOf(initialDeepLinkEventId) }

    // Check and trigger deep linking if an eventId was provided via notification
    LaunchedEffect(targetDeepLinkEventId, allEvents.size, currentStudent) {
        val eid = targetDeepLinkEventId
        if (!eid.isNullOrBlank() && allEvents.isNotEmpty()) {
            val matchingEvent = allEvents.find { it.id == eid }
            if (matchingEvent != null) {
                selectedEvent = matchingEvent
                if (currentStudent != null) {
                    route = ScreenRoute.MAIN_FEED
                }
                targetDeepLinkEventId = null
            }
        }
    }

    // Register FCM Token with current student identity upon login
    LaunchedEffect(currentStudent?.email) {
        currentStudent?.let { user ->
            try {
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) {
                        MongoDBHelper.registerDeviceFcmToken(token, user.email, "STUDENT")
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Global Background Chat Listener for live messaging notifications
    currentStudent?.let { loggedInUser ->
        LaunchedEffect(loggedInUser.email) {
            val myEmail = loggedInUser.email.trim().lowercase()
            MongoDBHelper.listenToAllIncomingMessages(myEmail) { senderEmail, messageText ->
                if (messageText.isNotEmpty() && senderEmail != myEmail) {
                    val senderObj = registeredStudents.find { it.email.equals(senderEmail, ignoreCase = true) }
                    val senderDisplayName = senderObj?.name ?: senderEmail.substringBefore("@")
                    NotificationHelper.notifyNewChatMessage(context, senderDisplayName, messageText)
                }
            }
        }
    }

    // MongoDB Atlas Real-time Listeners for cloud data synchronization & instant event alerts
    LaunchedEffect(Unit) {
        MongoDBHelper.listenToParticipants { remoteList ->
            allParticipants.clear()
            allParticipants.addAll(remoteList)
            dataManager.saveParticipants(remoteList)
        }

        val knownEventIds = mutableSetOf<String>()
        var isFirstLoad = true

        MongoDBHelper.listenToEvents { remoteEvents ->
            if (isFirstLoad) {
                isFirstLoad = false
                knownEventIds.addAll(remoteEvents.map { it.id })
            } else {
                val newEvents = remoteEvents.filter { it.id !in knownEventIds }
                for (newEvent in newEvents) {
                    knownEventIds.add(newEvent.id)
                    if (currentStudent != null) {
                        NotificationHelper.notifyEventPublished(context, newEvent)
                    }
                }
            }

            allEvents.clear()
            allEvents.addAll(remoteEvents)
            dataManager.saveEvents(remoteEvents)
        }

        MongoDBHelper.listenToStudents { remoteStudents ->
            registeredStudents.clear()
            registeredStudents.addAll(remoteStudents)
            dataManager.saveStudents(remoteStudents)
        }

        MongoDBHelper.listenToStudentPosts { remotePosts ->
            allPosts.clear()
            allPosts.addAll(remotePosts)
            dataManager.saveStudentPosts(remotePosts)
        }

        MongoDBHelper.listenToColleges { remoteCollegesMaps ->
            val mappedFaculties = remoteCollegesMaps.mapNotNull { map ->
                try {
                    val remoteSuperAdminStatus = map["isVerifiedBySuperAdmin"] as? Boolean ?: false
                    FacultyKYC(
                        name = map["name"] as? String ?: "",
                        collegeName = map["collegeName"] as? String ?: "",
                        designation = map["designation"] as? String ?: "",
                        department = map["department"] as? String ?: "",
                        collegeEmail = map["collegeEmail"] as? String ?: "",
                        contactNumber = map["contactNumber"] as? String ?: "",
                        collegeWebsite = map["collegeWebsite"] as? String ?: "",
                        accreditation = map["accreditation"] as? String ?: "",
                        password = map["password"] as? String ?: "",
                        role = if ((map["role"] as? String) == "COLLEGE_ADMIN") FacultyRole.COLLEGE_ADMIN else FacultyRole.DEPT_FACULTY,
                        isVerifiedBySuperAdmin = remoteSuperAdminStatus,
                        isVerifiedByCollegeAdmin = map["isVerifiedByCollegeAdmin"] as? Boolean ?: false,
                        collegePhotoUri = map["collegePhotoUri"] as? String ?: "",
                        idProofUri = map["idProofUri"] as? String ?: ""
                    )
                } catch (_: Exception) {
                    null
                }
            }
            if (mappedFaculties.isNotEmpty()) {
                for (remoteCol in mappedFaculties) {
                    val idx = allFaculties.indexOfFirst { it.collegeEmail.equals(remoteCol.collegeEmail, ignoreCase = true) }
                    if (idx != -1) {
                        allFaculties[idx] = allFaculties[idx].copy(
                            isVerifiedBySuperAdmin = allFaculties[idx].isVerifiedBySuperAdmin || remoteCol.isVerifiedBySuperAdmin
                        )
                    } else {
                        allFaculties.add(remoteCol)
                    }
                }
                dataManager.saveFaculties(allFaculties.toList())
            }
        }

        MongoDBHelper.listenToFaculties { remoteFacultiesMaps ->
            val mappedDeptFaculties = remoteFacultiesMaps.mapNotNull { map ->
                try {
                    FacultyKYC(
                        name = map["name"] as? String ?: "",
                        collegeEmail = map["collegeEmail"] as? String ?: "",
                        contactNumber = map["contactNumber"] as? String ?: "",
                        password = map["password"] as? String ?: "",
                        collegeName = map["collegeName"] as? String ?: "",
                        department = map["department"] as? String ?: "",
                        designation = map["designation"] as? String ?: "",
                        accreditation = map["accreditation"] as? String ?: "",
                        collegeWebsite = map["collegeWebsite"] as? String ?: "",
                        collegePhotoUri = map["collegePhotoUri"] as? String ?: "",
                        campusLayoutUri = map["campusLayoutUri"] as? String ?: "",
                        idProofUri = map["idProofUri"] as? String ?: "",
                        role = if ((map["role"] as? String) == "COLLEGE_ADMIN") FacultyRole.COLLEGE_ADMIN else FacultyRole.DEPT_FACULTY,
                        isVerifiedBySuperAdmin = map["isVerifiedBySuperAdmin"] as? Boolean ?: false,
                        isVerifiedByCollegeAdmin = map["isVerifiedByCollegeAdmin"] as? Boolean ?: false
                    )
                } catch (_: Exception) {
                    null
                }
            }
            if (mappedDeptFaculties.isNotEmpty()) {
                for (remoteFac in mappedDeptFaculties) {
                    val idx = allFaculties.indexOfFirst { it.collegeEmail.equals(remoteFac.collegeEmail, ignoreCase = true) }
                    if (idx != -1) {
                        allFaculties[idx] = allFaculties[idx].copy(
                            isVerifiedBySuperAdmin = allFaculties[idx].isVerifiedBySuperAdmin || remoteFac.isVerifiedBySuperAdmin,
                            isVerifiedByCollegeAdmin = allFaculties[idx].isVerifiedByCollegeAdmin || remoteFac.isVerifiedByCollegeAdmin
                        )
                    } else {
                        allFaculties.add(remoteFac)
                    }
                }
                dataManager.saveFaculties(allFaculties.toList())
            }
        }
    }

    when (route) {
        ScreenRoute.SPLASH -> {
            SplashScreen(
                onSplashFinished = {
                    route = ScreenRoute.PERMISSIONS
                }
            )
        }

        ScreenRoute.PERMISSIONS -> {
            PermissionsScreen(
                onContinue = {
                    route = ScreenRoute.LOGIN
                }
            )
        }

        ScreenRoute.LOGIN -> {
            LoginScreen(
                onStudentLoginAttempt = { email, password ->
                    val cleanEmail = email.trim().lowercase()
                    val cleanPassword = password.trim()

                    // Fast path: check local cache first
                    val localStudent = registeredStudents.find { it.email.trim().lowercase() == cleanEmail }
                    if (localStudent != null && (localStudent.password.isBlank() || localStudent.password == cleanPassword)) {
                        currentStudent = localStudent
                        route = ScreenRoute.MAIN_FEED
                        Toast.makeText(context, "Welcome back, ${localStudent.name}!", Toast.LENGTH_SHORT).show()
                        return@LoginScreen
                    }

                    // Query MongoDB Atlas students collection
                    MongoDBHelper.authenticateStudent(cleanEmail, cleanPassword) { student, err ->
                        if (student != null) {
                            currentStudent = student
                            val idx = registeredStudents.indexOfFirst { it.email.equals(student.email, ignoreCase = true) }
                            if (idx != -1) registeredStudents[idx] = student else registeredStudents.add(student)
                            dataManager.saveStudents(registeredStudents.toList())
                            route = ScreenRoute.MAIN_FEED
                            Toast.makeText(context, "Welcome back, ${student.name}!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, err ?: "Your student account is not registered. Please create a profile.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onFacultyLoginAttempt = { email, password ->
                    val cleanEmail = email.trim().lowercase()
                    val cleanPassword = password.trim()

                    // Fast path: check local faculty cache
                    val localFaculty = allFaculties.find { it.collegeEmail.trim().lowercase() == cleanEmail }
                    if (localFaculty != null && localFaculty.password == cleanPassword) {
                        if (localFaculty.role == FacultyRole.COLLEGE_ADMIN && !localFaculty.isVerifiedBySuperAdmin) {
                            Toast.makeText(context, "College Admin KYC is pending verification by Super Admin.", Toast.LENGTH_LONG).show()
                        } else if (localFaculty.role == FacultyRole.DEPT_FACULTY && !localFaculty.isVerifiedByCollegeAdmin && !localFaculty.isVerifiedBySuperAdmin) {
                            Toast.makeText(context, "Department Faculty KYC is pending approval by College Admin.", Toast.LENGTH_LONG).show()
                        } else {
                            currentFaculty = localFaculty
                            route = ScreenRoute.FACULTY_PORTAL
                            Toast.makeText(context, "Welcome, ${localFaculty.name}!", Toast.LENGTH_SHORT).show()
                        }
                        return@LoginScreen
                    }

                    // Query MongoDB Atlas college_faculties collection
                    MongoDBHelper.authenticateFaculty(cleanEmail, cleanPassword) { faculty, err ->
                        if (faculty != null) {
                            if (faculty.role == FacultyRole.COLLEGE_ADMIN && !faculty.isVerifiedBySuperAdmin) {
                                Toast.makeText(context, "College Admin KYC is pending verification by Super Admin.", Toast.LENGTH_LONG).show()
                            } else if (faculty.role == FacultyRole.DEPT_FACULTY && !faculty.isVerifiedByCollegeAdmin && !faculty.isVerifiedBySuperAdmin) {
                                Toast.makeText(context, "Department Faculty KYC is pending approval by College Admin.", Toast.LENGTH_LONG).show()
                            } else {
                                currentFaculty = faculty
                                val idx = allFaculties.indexOfFirst { it.collegeEmail.equals(faculty.collegeEmail, ignoreCase = true) }
                                if (idx != -1) allFaculties[idx] = faculty else allFaculties.add(faculty)
                                dataManager.saveFaculties(allFaculties.toList())
                                route = ScreenRoute.FACULTY_PORTAL
                                Toast.makeText(context, "Welcome, ${faculty.name}!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, err ?: "Faculty account not found or incorrect password.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onSuperAdminLoginAttempt = { email, password ->
                    val cleanEmail = email.trim().lowercase()
                    val cleanPassword = password.trim()

                    if (cleanEmail == "admin@kampus.com" && cleanPassword == "admin123") {
                        route = ScreenRoute.SUPER_ADMIN
                        Toast.makeText(context, "Super Admin authenticated", Toast.LENGTH_SHORT).show()
                        return@LoginScreen
                    }

                    MongoDBHelper.authenticateSuperAdmin(cleanEmail, cleanPassword) { success, err ->
                        if (success) {
                            route = ScreenRoute.SUPER_ADMIN
                            Toast.makeText(context, "Super Admin authenticated", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, err ?: "Invalid Super Admin credentials.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onStudentRegister = { route = ScreenRoute.STUDENT_SETUP },
                onFacultyRegister = { route = ScreenRoute.FACULTY_KYC }
            )
        }

        ScreenRoute.STUDENT_SETUP -> {
            StudentProfileSetupScreen(
                onComplete = { newStudent ->
                    val idx = registeredStudents.indexOfFirst { it.email.equals(newStudent.email, ignoreCase = true) }
                    if (idx != -1) {
                        registeredStudents[idx] = newStudent
                    } else {
                        registeredStudents.add(newStudent)
                    }
                    dataManager.saveStudents(registeredStudents.toList())
                    MongoDBHelper.saveStudent(newStudent) { _ -> }
                    currentStudent = newStudent
                    route = ScreenRoute.MAIN_FEED
                },
                onBack = { route = ScreenRoute.LOGIN }
            )
        }

        ScreenRoute.FACULTY_KYC -> {
            FacultyKYCScreen(
                onSubmitKYC = { newFaculty ->
                    MongoDBHelper.uploadImageToStorage(context, newFaculty.idProofUri, "faculty_id_proofs") { idProofUrl ->
                        MongoDBHelper.uploadImageToStorage(context, newFaculty.collegePhotoUri, "campus_banners") { collegePhotoUrl ->
                            MongoDBHelper.uploadImageToStorage(context, newFaculty.campusLayoutUri, "campus_layouts") { layoutUrl ->
                                val processedFaculty = newFaculty.copy(
                                    idProofUri = idProofUrl,
                                    collegePhotoUri = collegePhotoUrl,
                                    campusLayoutUri = layoutUrl
                                )
                                val idx = allFaculties.indexOfFirst { it.collegeEmail.equals(processedFaculty.collegeEmail, ignoreCase = true) }
                                if (idx != -1) {
                                    allFaculties[idx] = processedFaculty
                                } else {
                                    allFaculties.add(processedFaculty)
                                }
                                dataManager.saveFaculties(allFaculties.toList())

                                val facultyMap = mapOf(
                                    "name" to processedFaculty.name,
                                    "collegeEmail" to processedFaculty.collegeEmail,
                                    "contactNumber" to processedFaculty.contactNumber,
                                    "password" to processedFaculty.password,
                                    "collegeName" to processedFaculty.collegeName,
                                    "department" to processedFaculty.department,
                                    "designation" to processedFaculty.designation,
                                    "accreditation" to processedFaculty.accreditation,
                                    "collegeWebsite" to processedFaculty.collegeWebsite,
                                    "collegePhotoUri" to processedFaculty.collegePhotoUri,
                                    "campusLayoutUri" to processedFaculty.campusLayoutUri,
                                    "idProofUri" to processedFaculty.idProofUri,
                                    "role" to processedFaculty.role.name,
                                    "isVerifiedBySuperAdmin" to processedFaculty.isVerifiedBySuperAdmin,
                                    "isVerifiedByCollegeAdmin" to processedFaculty.isVerifiedByCollegeAdmin
                                )
                                MongoDBHelper.saveFaculty(facultyMap) { _ -> }

                                Toast.makeText(context, "Registration submitted for verification.", Toast.LENGTH_LONG).show()
                                route = ScreenRoute.LOGIN
                            }
                        }
                    }
                },
                onBack = { route = ScreenRoute.LOGIN }
            )
        }

        ScreenRoute.SUPER_ADMIN -> {
            val pendingKYCs = remember {
                mutableStateListOf<FacultyKYC>().apply {
                    addAll(allFaculties.filter { !it.isVerifiedBySuperAdmin && it.role == FacultyRole.COLLEGE_ADMIN })
                }
            }

            SuperAdminScreen(
                pendingList = pendingKYCs,
                allFacultiesList = allFaculties,
                onFacultyApproved = { facultyToApprove ->
                    val idx = allFaculties.indexOfFirst { it.collegeEmail == facultyToApprove.collegeEmail }
                    if (idx != -1) {
                        val updated = facultyToApprove.copy(isVerifiedBySuperAdmin = true)
                        allFaculties[idx] = updated
                        pendingKYCs.removeIf { it.collegeEmail == facultyToApprove.collegeEmail }

                        val collegeMap = mapOf(
                            "name" to updated.name,
                            "collegeName" to updated.collegeName,
                            "designation" to updated.designation,
                            "department" to updated.department,
                            "collegeEmail" to updated.collegeEmail,
                            "contactNumber" to updated.contactNumber,
                            "collegeWebsite" to updated.collegeWebsite,
                            "accreditation" to updated.accreditation,
                            "password" to updated.password,
                            "role" to updated.role.name,
                            "isVerifiedBySuperAdmin" to true,
                            "collegePhotoUri" to updated.collegePhotoUri,
                            "idProofUri" to updated.idProofUri
                        )
                        MongoDBHelper.saveCollege(updated.collegeName, collegeMap) { success ->
                            if (success) {
                                Toast.makeText(context, "${updated.collegeName} approved & synced!", Toast.LENGTH_SHORT).show()
                            }
                        }

                        val facultyMap = collegeMap.toMutableMap().apply {
                            put("campusLayoutUri", updated.campusLayoutUri)
                            put("isVerifiedByCollegeAdmin", updated.isVerifiedByCollegeAdmin)
                        }
                        MongoDBHelper.saveFaculty(facultyMap) { _ -> }

                        dataManager.saveFaculties(allFaculties.toList())
                    }
                },
                onFacultyDeleted = { facultyToDelete ->
                    allFaculties.removeIf { it.collegeEmail == facultyToDelete.collegeEmail }
                    pendingKYCs.removeIf { it.collegeEmail == facultyToDelete.collegeEmail }
                    dataManager.saveFaculties(allFaculties.toList())
                },
                onLogout = {
                    dataManager.saveFaculties(allFaculties.toList())
                    route = ScreenRoute.LOGIN
                }
            )
        }

        ScreenRoute.FACULTY_PORTAL -> {
            currentFaculty?.let { faculty ->
                FacultyEventPublishScreen(
                    faculty = faculty,
                    allFaculties = allFaculties,
                    allEvents = allEvents,
                    onAddEvent = { newEvent ->
                        MongoDBHelper.uploadImageToStorage(context, newEvent.posterUrl, "event_posters") { posterUrl ->
                            val processedEvent = newEvent.copy(posterUrl = posterUrl)

                            MongoDBHelper.saveEvent(processedEvent) { success ->
                                if (success) {
                                    Toast.makeText(context, "Event published & synced to MongoDB Atlas!", Toast.LENGTH_SHORT).show()
                                    // Trigger instant rich push notification
                                    NotificationHelper.notifyEventPublished(context, processedEvent)
                                }
                            }
                            allEvents.add(0, processedEvent)
                            dataManager.saveEvents(allEvents.toList())
                        }
                    },
                    onFacultyApproved = { deptFac ->
                        val idx = allFaculties.indexOfFirst { it.collegeEmail == deptFac.collegeEmail }
                        if (idx != -1) {
                            val updatedDeptFac = deptFac.copy(isVerifiedByCollegeAdmin = true)
                            allFaculties[idx] = updatedDeptFac

                            val facultyMap = mapOf(
                                "name" to updatedDeptFac.name,
                                "collegeEmail" to updatedDeptFac.collegeEmail,
                                "contactNumber" to updatedDeptFac.contactNumber,
                                "password" to updatedDeptFac.password,
                                "collegeName" to updatedDeptFac.collegeName,
                                "department" to updatedDeptFac.department,
                                "designation" to updatedDeptFac.designation,
                                "accreditation" to updatedDeptFac.accreditation,
                                "collegeWebsite" to updatedDeptFac.collegeWebsite,
                                "collegePhotoUri" to updatedDeptFac.collegePhotoUri,
                                "campusLayoutUri" to updatedDeptFac.campusLayoutUri,
                                "idProofUri" to updatedDeptFac.idProofUri,
                                "role" to updatedDeptFac.role.name,
                                "isVerifiedBySuperAdmin" to updatedDeptFac.isVerifiedBySuperAdmin,
                                "isVerifiedByCollegeAdmin" to true
                            )
                            MongoDBHelper.saveFaculty(facultyMap) { success ->
                                if (success) {
                                    Toast.makeText(context, "Faculty approval synced!", Toast.LENGTH_SHORT).show()
                                }
                            }

                            dataManager.saveFaculties(allFaculties.toList())
                            Toast.makeText(context, "${deptFac.name} approved!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFacultyDeleted = { deptFac ->
                        allFaculties.removeIf { it.collegeEmail == deptFac.collegeEmail }
                        dataManager.saveFaculties(allFaculties.toList())
                    },
                    onDeleteEvent = { eventToDelete ->
                        MongoDBHelper.deleteEvent(eventToDelete.id) { _ -> }
                        allEvents.removeIf { it.id == eventToDelete.id }
                        allParticipants.removeIf { it.eventId == eventToDelete.id }
                        dataManager.saveEvents(allEvents.toList())
                        dataManager.saveParticipants(allParticipants.toList())
                    },
                    onUpdateFacultyLayout = { updatedFaculty ->
                        MongoDBHelper.uploadImageToStorage(context, updatedFaculty.collegePhotoUri, "campus_banners") { bannerUrl ->
                            MongoDBHelper.uploadImageToStorage(context, updatedFaculty.campusLayoutUri, "campus_layouts") { layoutUrl ->
                                val processedFaculty = updatedFaculty.copy(
                                    collegePhotoUri = bannerUrl,
                                    campusLayoutUri = layoutUrl
                                )
                                val idx = allFaculties.indexOfFirst { it.collegeEmail == processedFaculty.collegeEmail }
                                if (idx != -1) {
                                    allFaculties[idx] = processedFaculty

                                    val facultyMap = mapOf(
                                        "name" to processedFaculty.name,
                                        "collegeEmail" to processedFaculty.collegeEmail,
                                        "contactNumber" to processedFaculty.contactNumber,
                                        "password" to processedFaculty.password,
                                        "collegeName" to processedFaculty.collegeName,
                                        "department" to processedFaculty.department,
                                        "designation" to processedFaculty.designation,
                                        "accreditation" to processedFaculty.accreditation,
                                        "collegeWebsite" to processedFaculty.collegeWebsite,
                                        "collegePhotoUri" to processedFaculty.collegePhotoUri,
                                        "campusLayoutUri" to processedFaculty.campusLayoutUri,
                                        "idProofUri" to processedFaculty.idProofUri,
                                        "role" to processedFaculty.role.name,
                                        "isVerifiedBySuperAdmin" to processedFaculty.isVerifiedBySuperAdmin,
                                        "isVerifiedByCollegeAdmin" to processedFaculty.isVerifiedByCollegeAdmin
                                    )
                                    MongoDBHelper.saveFaculty(facultyMap) { _ -> }

                                    if (processedFaculty.role == FacultyRole.COLLEGE_ADMIN) {
                                        val collegeMap = mapOf(
                                            "name" to processedFaculty.name,
                                            "collegeName" to processedFaculty.collegeName,
                                            "designation" to processedFaculty.designation,
                                            "department" to processedFaculty.department,
                                            "collegeEmail" to processedFaculty.collegeEmail,
                                            "contactNumber" to processedFaculty.contactNumber,
                                            "collegeWebsite" to processedFaculty.collegeWebsite,
                                            "accreditation" to processedFaculty.accreditation,
                                            "password" to processedFaculty.password,
                                            "role" to processedFaculty.role.name,
                                            "isVerifiedBySuperAdmin" to processedFaculty.isVerifiedBySuperAdmin,
                                            "collegePhotoUri" to processedFaculty.collegePhotoUri,
                                            "idProofUri" to processedFaculty.idProofUri
                                        )
                                        MongoDBHelper.saveCollege(processedFaculty.collegeName, collegeMap) { _ -> }
                                    }

                                    dataManager.saveFaculties(allFaculties.toList())
                                    currentFaculty = processedFaculty
                                    Toast.makeText(context, "College Layout & Banner Updated!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    onLogout = {
                        dataManager.saveEvents(allEvents.toList())
                        dataManager.saveFaculties(allFaculties.toList())
                        currentFaculty = null
                        route = ScreenRoute.LOGIN
                    }
                )
            } ?: run { route = ScreenRoute.LOGIN }
        }

        ScreenRoute.MAIN_FEED -> {
            currentStudent?.let { user ->
                val activeEvents = allEvents.filter { !it.isExpired() }

                if (selectedEvent == null) {
                    MainAppScaffold(
                        user = user,
                        events = activeEvents,
                        participantsList = allParticipants,
                        postsList = allPosts,
                        allStudents = registeredStudents.toList(),
                        allFaculties = allFaculties.toList(),
                        onAddNewPost = { newPost ->
                            MongoDBHelper.uploadImageToStorage(context, newPost.imageUri, "student_posts") { postImgUrl ->
                                val processedPost = newPost.copy(imageUri = postImgUrl)
                                allPosts.add(0, processedPost)
                                dataManager.saveStudentPosts(allPosts.toList())
                                MongoDBHelper.saveStudentPost(processedPost) { _ -> }
                            }
                        },
                        onDeletePost = { postToDelete ->
                            allPosts.removeIf { it.id == postToDelete.id }
                            dataManager.saveStudentPosts(allPosts.toList())
                        },
                        onUpdateUser = { updatedUser ->
                            MongoDBHelper.uploadImageToStorage(context, updatedUser.profilePhotoUri, "student_profiles") { profileUrl ->
                                MongoDBHelper.uploadImageToStorage(context, updatedUser.coverPhotoUri, "student_covers") { coverUrl ->
                                    val processedUser = updatedUser.copy(
                                        profilePhotoUri = profileUrl,
                                        coverPhotoUri = coverUrl
                                    )
                                    currentStudent = processedUser
                                    val idx = registeredStudents.indexOfFirst { it.email.equals(processedUser.email, ignoreCase = true) }
                                    if (idx != -1) {
                                        registeredStudents[idx] = processedUser
                                        dataManager.saveStudents(registeredStudents.toList())
                                        MongoDBHelper.saveStudent(processedUser) { _ -> }
                                    }
                                }
                            }
                        },
                        onSelectEvent = { selectedEvent = it },
                        onLogout = {
                            currentStudent = null
                            route = ScreenRoute.LOGIN
                        }
                    )
                } else {
                    EventDetailScreen(
                        event = selectedEvent!!,
                        currentUser = user,
                        participantsList = allParticipants,
                        onRegisterParticipant = { newParticipant ->
                            if (!allParticipants.any { it.eventId == newParticipant.eventId && it.studentEmail.equals(newParticipant.studentEmail, ignoreCase = true) }) {
                                allParticipants.add(newParticipant)
                            }
                            dataManager.saveParticipants(allParticipants.toList())
                        },
                        onUnregisterParticipant = { participantToRemove ->
                            allParticipants.removeIf {
                                it.eventId == participantToRemove.eventId &&
                                        it.studentEmail.equals(participantToRemove.studentEmail, ignoreCase = true)
                            }
                            dataManager.saveParticipants(allParticipants.toList())
                        },
                        onBack = { selectedEvent = null }
                    )
                }
            } ?: run { route = ScreenRoute.LOGIN }
        }
    }
}

@Composable
fun MainAppScaffold(
    user: StudentUser,
    events: List<CollegeEvent>,
    participantsList: List<EventParticipant>,
    postsList: List<StudentPost>,
    allStudents: List<StudentUser>,
    allFaculties: List<FacultyKYC>,
    onAddNewPost: (StudentPost) -> Unit,
    onDeletePost: (StudentPost) -> Unit,
    onUpdateUser: (StudentUser) -> Unit,
    onSelectEvent: (CollegeEvent) -> Unit,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("🏠", fontSize = 18.sp) },
                    label = { Text("Feed") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("🏛", fontSize = 18.sp) },
                    label = { Text("Colleges") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Text("👥", fontSize = 18.sp) },
                    label = { Text("Team") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Text("⏰", fontSize = 18.sp) },
                    label = { Text("Deadlines") }
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Text("👤", fontSize = 18.sp) },
                    label = { Text("Profile") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> HomeFeedScreen(user = user, events = events, onEventClick = onSelectEvent)
                1 -> CollegeDirectoryScreen(
                    currentUser = user,
                    allCollegesList = allFaculties,
                    allEvents = events,
                    onSelectEvent = onSelectEvent
                )
                2 -> TeamFinderScreen(
                    currentUserEmail = user.email,
                    events = events,
                    participantsList = participantsList,
                    allStudents = allStudents,
                    allPosts = postsList
                )
                3 -> DeadlinesScreen(
                    user = user,
                    events = events,
                    registeredIds = participantsList.filter { it.studentEmail.equals(user.email, ignoreCase = true) }.map { it.eventId }
                )
                4 -> ProfileScreen(
                    user = user,
                    postsList = postsList,
                    onAddNewPost = onAddNewPost,
                    onDeletePost = onDeletePost,
                    onUpdateUser = onUpdateUser,
                    onLogout = onLogout
                )
            }
        }
    }
}