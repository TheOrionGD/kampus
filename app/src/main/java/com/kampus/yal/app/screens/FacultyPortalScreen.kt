package com.kampus.yal.app.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.kampus.yal.app.MongoDBHelper
import com.kampus.yal.app.models.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

data class EventCategoryBanner(
    val name: String,
    val subtitle: String,
    val imageUrl: String,
    val icon: String,
    val defaultRole: String = ""
)

data class DeptBannerItem(
    val deptCode: String,
    val deptName: String,
    val imageUrl: String,
    val icon: String
)



@Composable
fun FacultyEventPublishScreen(
    faculty: FacultyKYC,
    allFaculties: MutableList<FacultyKYC>,
    allEvents: MutableList<CollegeEvent>,
    onAddEvent: (CollegeEvent) -> Unit,
    onFacultyApproved: (FacultyKYC) -> Unit,
    onFacultyDeleted: (FacultyKYC) -> Unit,
    onDeleteEvent: (CollegeEvent) -> Unit,
    onUpdateFacultyLayout: (FacultyKYC) -> Unit = {},
    onUpdateFaculty: (FacultyKYC) -> Unit = {},
    onLogout: () -> Unit
) {
    var adminTab by remember { mutableIntStateOf(0) }
    var showProfilePage by remember { mutableStateOf(false) }
    val isCollegeAdmin = faculty.role == FacultyRole.COLLEGE_ADMIN

    var currentCampusBannerUri by remember { mutableStateOf(faculty.collegePhotoUri ?: "") }
    var currentLayoutUri by remember { mutableStateOf(faculty.campusLayoutUri ?: "") }

    val context = LocalContext.current

    // Fetch Section 1 (Campus Entrance/Banner) and Section 2 (Blueprint Layout) images directly from MongoDB database
    LaunchedEffect(faculty.collegeEmail, adminTab) {
        if (isCollegeAdmin) {
            MongoDBHelper.fetchCollegeMedia(faculty.collegeEmail, faculty.collegeName) { bannerUri, layoutUri ->
                if (bannerUri.isNotBlank()) currentCampusBannerUri = bannerUri
                if (layoutUri.isNotBlank()) currentLayoutUri = layoutUri
            }
        }
    }

    // 🗑️ AUTOMATIC EXPIRY CHECK & DELETE: Automatically remove events when their end time passes
    LaunchedEffect(allEvents) {
        val targetEvents = if (isCollegeAdmin) {
            allEvents.filter { it.college.equals(faculty.collegeName, ignoreCase = true) }
        } else {
            allEvents.filter { it.coordinatorName.equals(faculty.name, ignoreCase = true) }
        }

        for (ev in targetEvents) {
            if (ev.isExpired()) {
                onDeleteEvent(ev)
            }
        }
    }

    val allCategoryBanners = listOf(
        EventCategoryBanner("Hackathon", "Build Real-world Tech Solutions & Win Cash Prizes", "https://images.unsplash.com/photo-1504384308090-c894fdcc538d?w=800", "💻", "Event Convener / Faculty In-Charge"),
        EventCategoryBanner("Workshop", "Hands-on Masterclasses, Tools & Certificates", "https://images.unsplash.com/photo-1517245386807-bb43f82c33c4?w=800", "🛠️", "Technical Workshop Coordinator"),
        EventCategoryBanner("Symposium", "National Conferences, PPT Presentations & Expos", "https://images.unsplash.com/photo-1475721027785-f74eccf877e2?w=800", "📜", "Symposium Staff Coordinator"),
        EventCategoryBanner("Placement Drive", "Exclusive Campus & Off-Campus Hiring Drives", "https://images.unsplash.com/photo-1521737711867-e3b97375f902?w=800", "💼", "Placement & Training Officer"),
        EventCategoryBanner("College Fest", "Inter-College Culturals, Pro-Shows & Competitions", "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=800", "🎉", "Cultural Committee Convener"),
        EventCategoryBanner("Internships & Projects", "Industry Internships & Live Research Work", "https://images.unsplash.com/photo-1522071820081-009f0129c71c?w=800", "🚀", "Placement & Internship Officer (Training & Placement Cell)"),
        EventCategoryBanner("Sports & Tournaments", "Zonal Matches, Athletics & Tournaments", "https://images.unsplash.com/photo-1461896836934-ffe607ba8211?w=800", "🏆", "Physical Director (PD / Sports In-Charge)"),
        EventCategoryBanner("Courses & Webinars", "Value Added Courses & Online Masterclasses", "https://images.unsplash.com/photo-1434030216411-0b793f4b4173?w=800", "🎓", "Department Faculty / Course Coordinator"),
        EventCategoryBanner("Paper & Project Expos", "Prototype Demonstrations & Innovations", "https://images.unsplash.com/photo-1581091226825-a6a2a5aee158?w=800", "🔬", "R&D Coordinator / Innovation In-Charge"),
        EventCategoryBanner("Clubs & NSS / NCC", "Social Service, Camps & Leadership Clubs", "https://images.unsplash.com/photo-1559027615-cd4628902d4a?w=800", "🤝", "NSS / NCC Officer & Student Club Coordinator")
    )

    val categoryBanners = remember(faculty.department, isCollegeAdmin) {
        if (isCollegeAdmin) {
            allCategoryBanners
        } else {
            val deptLower = faculty.department.lowercase()
            when {
                deptLower.contains("sport") || deptLower.contains("physical") -> {
                    allCategoryBanners.filter { it.name == "Sports & Tournaments" }
                }
                deptLower.contains("placement") || deptLower.contains("internship") -> {
                    allCategoryBanners.filter { it.name == "Placement Drive" || it.name == "Internships & Projects" }
                }
                deptLower.contains("r&d") || deptLower.contains("research") || deptLower.contains("expo") -> {
                    allCategoryBanners.filter { it.name == "Paper & Project Expos" }
                }
                deptLower.contains("nss") || deptLower.contains("ncc") || deptLower.contains("club") -> {
                    allCategoryBanners.filter { it.name == "Clubs & NSS / NCC" }
                }
                else -> {
                    allCategoryBanners.filter {
                        it.name == "Hackathon" ||
                                it.name == "Workshop" ||
                                it.name == "Symposium" ||
                                it.name == "Courses & Webinars" ||
                                it.name == "Paper & Project Expos"
                    }
                }
            }
        }
    }

    val defaultDeptBanners = listOf(
        DeptBannerItem("CSE", "Computer Science & Engineering", "https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=800", "💻"),
        DeptBannerItem("ECE", "Electronics & Communication", "https://images.unsplash.com/photo-1581092160607-ee22621dd758?w=800", "📡"),
        DeptBannerItem("MECH", "Mechanical Engineering", "https://images.unsplash.com/photo-1537462715879-360eeb61a0ad?w=800", "⚙️"),
        DeptBannerItem("IT", "Information Technology", "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=800", "🌐"),
        DeptBannerItem("EEE", "Electrical & Electronics", "https://images.unsplash.com/photo-1473341304170-971dccb5ac1e?w=800", "⚡"),
        DeptBannerItem("AI & DS", "Artificial Intelligence & Data Science", "https://images.unsplash.com/photo-1677442136019-21780efad99a?w=800", "🤖"),
        DeptBannerItem("Sports", "Physical Education & Sports Wing", "https://images.unsplash.com/photo-1461896836934-ffe607ba8211?w=800", "🏆"),
        DeptBannerItem("Placement", "Training & Placement Cell / Internships", "https://images.unsplash.com/photo-1521737711867-e3b97375f902?w=800", "💼"),
        DeptBannerItem("R&D", "Research, Innovation & Project Expos", "https://images.unsplash.com/photo-1581091226825-a6a2a5aee158?w=800", "🔬"),
        DeptBannerItem("Clubs", "NSS / NCC & Student Clubs Wing", "https://images.unsplash.com/photo-1559027615-cd4628902d4a?w=800", "🤝")
    )

    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedDeptApprovals by remember { mutableStateOf<String?>(null) }

    var title by remember { mutableStateOf("") }
    var eventDate by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("10:00 AM") }
    var endTime by remember { mutableStateOf("12:00 PM") }
    var deadline by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("Offline (Campus)") }
    var fee by remember { mutableStateOf("Free") }
    var eligibility by remember { mutableStateOf(if (isCollegeAdmin) "All College Students" else "${faculty.department} Students") }
    var registrationLink by remember { mutableStateOf("") }
    var fullDesc by remember { mutableStateOf("") }
    var coordinatorRoleTitle by remember { mutableStateOf("") }

    var showModeDropdown by remember { mutableStateOf(false) }
    var showEligibilityDropdown by remember { mutableStateOf(false) }

    val modeOptions = listOf(
        "Offline (Campus)",
        "Online (Virtual)",
        "Hybrid (Online + Offline)"
    )

    val eligibilityOptions = listOf(
        if (isCollegeAdmin) "All College Students" else "${faculty.department} Students",
        "All College Students",
        "1st Year Students",
        "2nd Year Students",
        "3rd Year Students",
        "Final Year Students",
        "1st & 2nd Year Students",
        "3rd & Final Year Students",
        "Postgraduate (PG) & Research Scholars"
    ).distinct()

    val currentCal = Calendar.getInstance()
    val eventDatePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                eventDate = String.format(Locale.ENGLISH, "%02d/%02d/%d", day, month + 1, year)
            },
            currentCal.get(Calendar.YEAR),
            currentCal.get(Calendar.MONTH),
            currentCal.get(Calendar.DAY_OF_MONTH)
        )
    }

    val deadlineDatePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                deadline = String.format(Locale.ENGLISH, "%02d/%02d/%d", day, month + 1, year)
            },
            currentCal.get(Calendar.YEAR),
            currentCal.get(Calendar.MONTH),
            currentCal.get(Calendar.DAY_OF_MONTH)
        )
    }

    val startTimePickerDialog = remember {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val amPm = if (hourOfDay < 12) "AM" else "PM"
                val hour12 = if (hourOfDay == 0) 12 else if (hourOfDay > 12) hourOfDay - 12 else hourOfDay
                startTime = String.format(Locale.ENGLISH, "%02d:%02d %s", hour12, minute, amPm)
            },
            10, 0, false
        )
    }

    val endTimePickerDialog = remember {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val amPm = if (hourOfDay < 12) "AM" else "PM"
                val hour12 = if (hourOfDay == 0) 12 else if (hourOfDay > 12) hourOfDay - 12 else hourOfDay
                endTime = String.format(Locale.ENGLISH, "%02d:%02d %s", hour12, minute, amPm)
            },
            12, 0, false
        )
    }

    var hackathonTeamSize by remember { mutableStateOf("2 to 4 Members") }
    var hackathonPrizePool by remember { mutableStateOf("₹50,000 Cash Prize") }
    var hackathonTracks by remember { mutableStateOf("Web3, AI, App Dev") }

    var workshopSpeaker by remember { mutableStateOf("") }
    var workshopPrerequisites by remember { mutableStateOf("Laptop Required") }
    var workshopTakeaways by remember { mutableStateOf("Certificate + Learning Material") }

    var symposiumPaperThemes by remember { mutableStateOf("AI, IoT, Cloud, Cybersecurity") }
    var symposiumEventsList by remember { mutableStateOf("Paper Presentation, Tech Quiz, Debugging") }

    var driveCompanyName by remember { mutableStateOf("") }
    var drivePackageCTC by remember { mutableStateOf("8.5 LPA") }
    var driveEligibleCriteria by remember { mutableStateOf("CGPA > 7.0 (No Active Backlogs)") }

    var festCelebrity by remember { mutableStateOf("") }
    var festFlagshipEvents by remember { mutableStateOf("Battle of Bands, Choreo Night") }

    var customField1Value by remember { mutableStateOf("") }
    var customField2Value by remember { mutableStateOf("") }

    var savedPosterPath by remember { mutableStateOf("") }
    var fullScreenImageUri by remember { mutableStateOf<String?>(null) }

    val posterPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            MongoDBHelper.processPickedImageUri(context, it, "event_posters") { safeUrl ->
                savedPosterPath = safeUrl
            }
        }
    }

    val campusBannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            MongoDBHelper.processPickedImageUri(context, it, "campus_banners") { safeUrl ->
                currentCampusBannerUri = safeUrl
                val updatedFaculty = faculty.copy(collegePhotoUri = safeUrl)
                val idx = allFaculties.indexOfFirst { f -> f.collegeEmail == faculty.collegeEmail }
                if (idx != -1) allFaculties[idx] = updatedFaculty
                onUpdateFacultyLayout(updatedFaculty)
                Toast.makeText(context, "Campus Entrance Photo Updated!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val blueprintPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            MongoDBHelper.processPickedImageUri(context, it, "campus_layouts") { safeUrl ->
                currentLayoutUri = safeUrl
                val updatedFaculty = faculty.copy(campusLayoutUri = safeUrl)
                val idx = allFaculties.indexOfFirst { f -> f.collegeEmail == faculty.collegeEmail }
                if (idx != -1) allFaculties[idx] = updatedFaculty
                onUpdateFacultyLayout(updatedFaculty)
                Toast.makeText(context, "Campus Blueprint Layout Saved!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fullScreenImageUri?.let { imgUri ->
        Dialog(
            onDismissRequest = { fullScreenImageUri = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable { fullScreenImageUri = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageLoaderHelper.getSafeImageModel(imgUri),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )

                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .clickable { fullScreenImageUri = null }
                ) {
                    Text("✕", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = if (isCollegeAdmin) "🏛️ College Admin Portal" else "🎓 Dept Faculty Portal",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = "${faculty.name} • ${faculty.collegeName} (${faculty.department})",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "🚪 Logout",
                    color = Color(0xFFDC2626),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = adminTab == 0,
                onClick = { adminTab = 0 },
                label = { Text("Post Event", fontSize = 11.sp) },
                modifier = Modifier.weight(1f)
            )

            if (isCollegeAdmin) {
                val pendingCount = allFaculties.count {
                    it.collegeName.trim().equals(faculty.collegeName.trim(), ignoreCase = true) &&
                            it.role == FacultyRole.DEPT_FACULTY &&
                            !it.isVerifiedByCollegeAdmin
                }
                FilterChip(
                    selected = adminTab == 1,
                    onClick = {
                        adminTab = 1
                        selectedDeptApprovals = null
                    },
                    label = { Text("Approvals ($pendingCount)", fontSize = 11.sp) },
                    modifier = Modifier.weight(1.1f)
                )
                FilterChip(
                    selected = adminTab == 2,
                    onClick = { adminTab = 2 },
                    label = { Text("Events", fontSize = 11.sp) },
                    modifier = Modifier.weight(0.9f)
                )
                FilterChip(
                    selected = adminTab == 3,
                    onClick = { adminTab = 3 },
                    label = { Text("Campus Media", fontSize = 11.sp) },
                    modifier = Modifier.weight(1.2f)
                )
            } else {
                val myEventsCount = allEvents.count { it.coordinatorName.equals(faculty.name, ignoreCase = true) }
                FilterChip(
                    selected = adminTab == 2,
                    onClick = { adminTab = 2 },
                    label = { Text("My Events ($myEventsCount)") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (adminTab == 0) {
            if (selectedCategory == null) {
                Text(
                    text = if (isCollegeAdmin) "SELECT EVENT CATEGORY TO PUBLISH (ALL CATEGORIES):" else "SELECT CATEGORY FOR ${faculty.department.uppercase()}:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF475569)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    categoryBanners.forEach { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(115.dp)
                                .clickable {
                                    selectedCategory = item.name
                                    coordinatorRoleTitle = item.defaultRole
                                },
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(3.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = item.imageUrl,
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(Color(0xF00F172A), Color(0x990F172A), Color.Transparent)
                                            )
                                        )
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(item.icon, fontSize = 20.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(item.subtitle, fontSize = 11.sp, color = Color(0xFFCBD5E1), lineHeight = 15.sp, maxLines = 1)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Staff Role: ${item.defaultRole}", fontSize = 10.sp, color = Color(0xFFFDE047), fontWeight = FontWeight.SemiBold)
                                    }
                                    Text("➔", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { selectedCategory = null }) {
                        Text("← Change Category", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Surface(color = Color(0xFFDBEAFE), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = "Publishing: ${selectedCategory!!}",
                            color = Color(0xFF1E40AF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("🖼️ Event Poster Banner", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (savedPosterPath.isNotBlank()) {
                            AsyncImage(
                                model = ImageLoaderHelper.getSafeImageModel(savedPosterPath),
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth().wrapContentHeight().clip(RoundedCornerShape(10.dp))
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(onClick = { posterPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (savedPosterPath.isNotBlank()) "✔ Change Poster" else "📁 Upload Poster Image from Device")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "📌 Core Event Information & Timing",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF1E3A8A)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = registrationLink,
                            onValueChange = { registrationLink = it },
                            label = { Text("Official Registration Form Link *", color = Color(0xFF334155)) },
                            placeholder = { Text("https://forms.google.com/...", color = Color(0xFF94A3B8)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF0F172A),
                                unfocusedTextColor = Color(0xFF0F172A),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Event Title *", color = Color(0xFF334155)) },
                            placeholder = { Text("e.g. National Level Technical Symposium", color = Color(0xFF94A3B8)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF0F172A),
                                unfocusedTextColor = Color(0xFF0F172A),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = coordinatorRoleTitle,
                            onValueChange = { coordinatorRoleTitle = it },
                            label = { Text("In-Charge Staff Role / Title *", color = Color(0xFF334155)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF0F172A),
                                unfocusedTextColor = Color(0xFF0F172A),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // 📅 Event Date Picker (Clickable Calendar Dialog)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = eventDate,
                                onValueChange = {},
                                readOnly = true,
                                enabled = true,
                                label = { Text("📅 Event Date (Tap to Pick Calendar Date) *", color = Color(0xFF334155), fontWeight = FontWeight.SemiBold) },
                                placeholder = { Text("Tap to select date from calendar", color = Color(0xFF94A3B8)) },
                                trailingIcon = {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFEFF6FF),
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Text("📅", fontSize = 18.sp, modifier = Modifier.padding(6.dp))
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color(0xFF0F172A),
                                    unfocusedTextColor = Color(0xFF0F172A),
                                    focusedContainerColor = Color(0xFFF8FAFC),
                                    unfocusedContainerColor = Color(0xFFF8FAFC)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { eventDatePickerDialog.show() }
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        // ⏰ Start Time & End Time Pickers (Clickable Clock Dialogs)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = startTime,
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = true,
                                    label = { Text("⏰ Start Time *", color = Color(0xFF334155)) },
                                    trailingIcon = {
                                        Text("🕒", fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFF0F172A),
                                        unfocusedTextColor = Color(0xFF0F172A),
                                        focusedContainerColor = Color(0xFFF8FAFC),
                                        unfocusedContainerColor = Color(0xFFF8FAFC)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable { startTimePickerDialog.show() }
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = endTime,
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = true,
                                    label = { Text("⏰ End Time (Expiry) *", color = Color(0xFF334155)) },
                                    trailingIcon = {
                                        Text("🕒", fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFF0F172A),
                                        unfocusedTextColor = Color(0xFF0F172A),
                                        focusedContainerColor = Color(0xFFF8FAFC),
                                        unfocusedContainerColor = Color(0xFFF8FAFC)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable { endTimePickerDialog.show() }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 📅 Registration Deadline Picker & Fee
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = deadline,
                                    onValueChange = {},
                                    readOnly = true,
                                    enabled = true,
                                    label = { Text("⏰ Reg. Deadline *", color = Color(0xFF334155), fontWeight = FontWeight.SemiBold) },
                                    placeholder = { Text("Pick deadline", color = Color(0xFF94A3B8)) },
                                    trailingIcon = {
                                        Text("📅", fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFF0F172A),
                                        unfocusedTextColor = Color(0xFF0F172A),
                                        focusedContainerColor = Color(0xFFF8FAFC),
                                        unfocusedContainerColor = Color(0xFFF8FAFC)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable { deadlineDatePickerDialog.show() }
                                )
                            }

                            OutlinedTextField(
                                value = fee,
                                onValueChange = { fee = it },
                                label = { Text("Fee (Free / ₹)", color = Color(0xFF334155)) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color(0xFF0F172A),
                                    unfocusedTextColor = Color(0xFF0F172A),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 🌐 Mode Dropdown (Offline / Online / Hybrid)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = mode,
                                onValueChange = {},
                                readOnly = true,
                                enabled = true,
                                label = { Text("🌐 Mode of Event (Tap to Choose)", color = Color(0xFF334155), fontWeight = FontWeight.SemiBold) },
                                trailingIcon = {
                                    Text("▼", fontSize = 12.sp, color = Color(0xFF475569), modifier = Modifier.padding(end = 12.dp))
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color(0xFF0F172A),
                                    unfocusedTextColor = Color(0xFF0F172A),
                                    focusedContainerColor = Color(0xFFF8FAFC),
                                    unfocusedContainerColor = Color(0xFFF8FAFC)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { showModeDropdown = true }
                            )

                            DropdownMenu(
                                expanded = showModeDropdown,
                                onDismissRequest = { showModeDropdown = false },
                                modifier = Modifier.background(Color.White)
                            ) {
                                modeOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option, fontWeight = if (mode == option) FontWeight.Bold else FontWeight.Normal, color = Color(0xFF0F172A)) },
                                        onClick = {
                                            mode = option
                                            showModeDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 👨‍🎓 Target Eligibility Dropdown
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = eligibility,
                                onValueChange = {},
                                readOnly = true,
                                enabled = true,
                                label = { Text("👨‍🎓 Target Eligibility (Tap to Choose)", color = Color(0xFF334155), fontWeight = FontWeight.SemiBold) },
                                trailingIcon = {
                                    Text("▼", fontSize = 12.sp, color = Color(0xFF475569), modifier = Modifier.padding(end = 12.dp))
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color(0xFF0F172A),
                                    unfocusedTextColor = Color(0xFF0F172A),
                                    focusedContainerColor = Color(0xFFF8FAFC),
                                    unfocusedContainerColor = Color(0xFFF8FAFC)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { showEligibilityDropdown = true }
                            )

                            DropdownMenu(
                                expanded = showEligibilityDropdown,
                                onDismissRequest = { showEligibilityDropdown = false },
                                modifier = Modifier.background(Color.White)
                            ) {
                                eligibilityOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option, fontWeight = if (eligibility == option) FontWeight.Bold else FontWeight.Normal, color = Color(0xFF0F172A)) },
                                        onClick = {
                                            eligibility = option
                                            showEligibilityDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("⚡ Specific Configurations for $selectedCategory", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF166534))
                        Spacer(modifier = Modifier.height(8.dp))

                        when (selectedCategory) {
                            "Hackathon" -> {
                                OutlinedTextField(value = hackathonTeamSize, onValueChange = { hackathonTeamSize = it }, label = { Text("Team Size Range") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(value = hackathonPrizePool, onValueChange = { hackathonPrizePool = it }, label = { Text("Prize Pool / Rewards") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(value = hackathonTracks, onValueChange = { hackathonTracks = it }, label = { Text("Tracks / Problem Areas") }, modifier = Modifier.fillMaxWidth())
                            }
                            "Workshop" -> {
                                OutlinedTextField(value = workshopSpeaker, onValueChange = { workshopSpeaker = it }, label = { Text("Keynote Speaker & Designation") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(value = workshopTakeaways, onValueChange = { workshopTakeaways = it }, label = { Text("Certificates & Key Takeaways") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(value = workshopPrerequisites, onValueChange = { workshopPrerequisites = it }, label = { Text("Prerequisites") }, modifier = Modifier.fillMaxWidth())
                            }
                            "Symposium" -> {
                                OutlinedTextField(value = symposiumPaperThemes, onValueChange = { symposiumPaperThemes = it }, label = { Text("Call for Papers / Tracks") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(value = symposiumEventsList, onValueChange = { symposiumEventsList = it }, label = { Text("Sub-Events List") }, modifier = Modifier.fillMaxWidth())
                            }
                            "Placement Drive" -> {
                                OutlinedTextField(value = driveCompanyName, onValueChange = { driveCompanyName = it }, label = { Text("Hiring Organization") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(value = drivePackageCTC, onValueChange = { drivePackageCTC = it }, label = { Text("Package (CTC Offered)") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(value = driveEligibleCriteria, onValueChange = { driveEligibleCriteria = it }, label = { Text("Eligibility Cutoff (CGPA)") }, modifier = Modifier.fillMaxWidth())
                            }
                            "College Fest" -> {
                                OutlinedTextField(value = festCelebrity, onValueChange = { festCelebrity = it }, label = { Text("Celebrity Guests / Pro-Shows") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(value = festFlagshipEvents, onValueChange = { festFlagshipEvents = it }, label = { Text("Flagship Cultural Competitions") }, modifier = Modifier.fillMaxWidth())
                            }
                            "Internships & Projects" -> {
                                OutlinedTextField(value = customField1Value, onValueChange = { customField1Value = it }, label = { Text("Company / Organization / Research Lab") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(value = customField2Value, onValueChange = { customField2Value = it }, label = { Text("Stipend & Duration (e.g. ₹15,000/pm • 3 Months)") }, modifier = Modifier.fillMaxWidth())
                            }
                            "Sports & Tournaments" -> {
                                OutlinedTextField(value = customField1Value, onValueChange = { customField1Value = it }, label = { Text("Sports Event (Cricket, Football, Athletics)") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(value = customField2Value, onValueChange = { customField2Value = it }, label = { Text("Trophies, Cash Prizes & Medals") }, modifier = Modifier.fillMaxWidth())
                            }
                            "Courses & Webinars" -> {
                                OutlinedTextField(value = customField1Value, onValueChange = { customField1Value = it }, label = { Text("Course Provider / Platform (NPTEL, VAC, Speaker)") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(value = customField2Value, onValueChange = { customField2Value = it }, label = { Text("Credits, Certificates & Key Takeaways") }, modifier = Modifier.fillMaxWidth())
                            }
                            "Paper & Project Expos" -> {
                                OutlinedTextField(value = customField1Value, onValueChange = { customField1Value = it }, label = { Text("Expo Domains (AI, IoT, Robotics, Patents)") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(value = customField2Value, onValueChange = { customField2Value = it }, label = { Text("Prototype Grant / Cash Awards") }, modifier = Modifier.fillMaxWidth())
                            }
                            "Clubs & NSS / NCC" -> {
                                OutlinedTextField(value = customField1Value, onValueChange = { customField1Value = it }, label = { Text("Club Unit (NSS, NCC, Rotaract, YRC)") }, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(value = customField2Value, onValueChange = { customField2Value = it }, label = { Text("Camp / Activity Type & Certificate") }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = fullDesc,
                    onValueChange = { fullDesc = it },
                    label = { Text("Full Description & Guidelines") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (title.isBlank() || eventDate.isBlank() || deadline.isBlank() || registrationLink.isBlank() || endTime.isBlank()) {
                            Toast.makeText(context, "Fill in Title, Date, End Time and Form Link!", Toast.LENGTH_SHORT).show()
                        } else {
                            val (c1L, c1V, c2L, c2V, perks) = when (selectedCategory) {
                                "Hackathon" -> listOf("Team Size", hackathonTeamSize, "Tracks", hackathonTracks, hackathonPrizePool)
                                "Workshop" -> listOf("Speaker", workshopSpeaker, "Prerequisites", workshopPrerequisites, workshopTakeaways)
                                "Symposium" -> listOf("Tracks", symposiumPaperThemes, "Sub Events", symposiumEventsList, "Certificates & Trophies")
                                "Placement Drive" -> listOf("Company", driveCompanyName, "Package", drivePackageCTC, drivePackageCTC)
                                "College Fest" -> listOf("Headliner", festCelebrity, "Flagship Acts", festFlagshipEvents, "Cash Prizes & Mementos")
                                "Internships & Projects" -> listOf("Company", customField1Value, "Stipend", customField2Value, customField2Value)
                                "Sports & Tournaments" -> listOf("Sport", customField1Value, "Awards", customField2Value, customField2Value)
                                "Courses & Webinars" -> listOf("Platform", customField1Value, "Perks", customField2Value, customField2Value)
                                "Paper & Project Expos" -> listOf("Domain", customField1Value, "Grant", customField2Value, customField2Value)
                                "Clubs & NSS / NCC" -> listOf("Club", customField1Value, "Perks", customField2Value, customField2Value)
                                else -> listOf("", "", "", "", "")
                            }

                            onAddEvent(
                                CollegeEvent(
                                    id = System.currentTimeMillis().toString(),
                                    title = title.trim(),
                                    college = faculty.collegeName,
                                    category = selectedCategory!!,
                                    deadline = deadline.trim(),
                                    eventDate = eventDate.trim(),
                                    startTime = startTime.trim(),
                                    endTime = endTime.trim(),
                                    mode = mode.trim(),
                                    eligibility = eligibility.trim(),
                                    fee = fee.trim(),
                                    coordinatorName = faculty.name,
                                    coordinatorRole = coordinatorRoleTitle.ifBlank { "${faculty.designation} (${faculty.department})" },
                                    postedTime = "Just now",
                                    announcementNote = "Official verified event post.",
                                    fullDescription = fullDesc.trim().ifEmpty { "$selectedCategory conducted by ${faculty.department} at ${faculty.collegeName}." },
                                    prizePool = perks,
                                    targetDept = faculty.department,
                                    posterUrl = savedPosterPath,
                                    externalRegLink = registrationLink.trim(),
                                    customField1Label = c1L,
                                    customField1Value = c1V,
                                    customField2Label = c2L,
                                    customField2Value = c2V
                                )
                            )

                            Toast.makeText(context, "🎉 $selectedCategory Published Live & Synced!", Toast.LENGTH_SHORT).show()
                            title = ""
                            fullDesc = ""
                            registrationLink = ""
                            savedPosterPath = ""
                            customField1Value = ""
                            customField2Value = ""
                            selectedCategory = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("🚀 Publish $selectedCategory Live", fontWeight = FontWeight.Bold)
                }
            }

        } else if (adminTab == 1 && isCollegeAdmin) {
            val collegeDeptFaculties = allFaculties.filter {
                it.collegeName.trim().equals(faculty.collegeName.trim(), ignoreCase = true) &&
                        it.role == FacultyRole.DEPT_FACULTY
            }
            val pendingFacultiesAll = collegeDeptFaculties.filter { !it.isVerifiedByCollegeAdmin }

            if (selectedDeptApprovals == null) {
                if (pendingFacultiesAll.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDeptApprovals = "ALL_PENDING" },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = Color(0xFF2563EB),
                                    shape = CircleShape,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("${pendingFacultiesAll.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Pending Faculty Verifications",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1E3A8A)
                                    )
                                    Text(
                                        "Waiting for your college admin review",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            Surface(
                                color = Color(0xFF2563EB),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.clickable { selectedDeptApprovals = "ALL_PENDING" }
                            ) {
                                Text(
                                    "Review ➔",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                Text(
                    "SELECT WING / DEPARTMENT TO REVIEW:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    defaultDeptBanners.forEach { dept ->
                        val pendingInDept = collegeDeptFaculties.count {
                            it.department.contains(dept.deptCode, ignoreCase = true) && !it.isVerifiedByCollegeAdmin
                        }
                        val totalInDept = collegeDeptFaculties.count {
                            it.department.contains(dept.deptCode, ignoreCase = true)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clickable { selectedDeptApprovals = dept.deptCode },
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = dept.imageUrl,
                                    contentDescription = dept.deptName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(Color(0xF00F172A), Color(0x990F172A), Color.Transparent)
                                            )
                                        )
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(dept.icon, fontSize = 18.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("${dept.deptCode} Wing", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(dept.deptName, fontSize = 11.sp, color = Color(0xFFCBD5E1), maxLines = 1)
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = if (pendingInDept > 0) "⚠️ $pendingInDept Pending Approval ($totalInDept Total)" else "✔ $totalInDept Active Staff",
                                            fontSize = 10.sp,
                                            color = if (pendingInDept > 0) Color(0xFFFDE047) else Color(0xFF86EFAC),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text("➔", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                val targetDept = selectedDeptApprovals!!
                val facultiesInDept = if (targetDept == "ALL_PENDING") {
                    pendingFacultiesAll
                } else {
                    collegeDeptFaculties.filter { it.department.contains(targetDept, ignoreCase = true) }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { selectedDeptApprovals = null }) {
                        Text("← Back to Departments", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Surface(color = Color(0xFFDBEAFE), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = if (targetDept == "ALL_PENDING") "All Pending Staff (${facultiesInDept.size})" else "$targetDept Wing",
                            color = Color(0xFF1E40AF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (facultiesInDept.isEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Text("No faculty registrations found under $targetDept.", modifier = Modifier.padding(16.dp), color = Color.Gray, fontSize = 13.sp)
                    }
                } else {
                    for (deptFac in facultiesInDept) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(deptFac.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                                    Surface(
                                        color = if (deptFac.isVerifiedByCollegeAdmin) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            if (deptFac.isVerifiedByCollegeAdmin) "✔ APPROVED" else "PENDING",
                                            color = if (deptFac.isVerifiedByCollegeAdmin) Color(0xFF15803D) else Color(0xFF92400E),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text("Designation: ${deptFac.designation} (${deptFac.department})", fontSize = 13.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold)
                                Text("✉️ Email: ${deptFac.collegeEmail} | 📞 Phone: ${deptFac.contactNumber}", fontSize = 12.sp, color = Color.Gray)

                                val safeIdProof = deptFac.idProofUri ?: ""
                                if (safeIdProof.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Institutional ID Proof Document (Tap to zoom):", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    AsyncImage(
                                        model = ImageLoaderHelper.getSafeImageModel(safeIdProof),
                                        contentDescription = "ID Proof",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .wrapContentHeight()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { fullScreenImageUri = safeIdProof }
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                if (!deptFac.isVerifiedByCollegeAdmin) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { onFacultyApproved(deptFac) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Approve for ${deptFac.department}")
                                        }
                                        OutlinedButton(
                                            onClick = { onFacultyDeleted(deptFac) },
                                            modifier = Modifier.weight(0.6f)
                                        ) {
                                            Text("Reject", color = Color.Red)
                                        }
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { onFacultyDeleted(deptFac) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Revoke Access", color = Color.Red)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (adminTab == 2) {
            val targetCollegeEvents = if (isCollegeAdmin) {
                allEvents.filter { it.college.equals(faculty.collegeName, ignoreCase = true) }
            } else {
                allEvents.filter { it.coordinatorName.equals(faculty.name, ignoreCase = true) }
            }

            Text(
                text = if (isCollegeAdmin) "ALL EVENTS IN ${faculty.collegeName.uppercase()} (${targetCollegeEvents.size})" else "MY EVENTS (${targetCollegeEvents.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (targetCollegeEvents.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Text("No events posted yet.", modifier = Modifier.padding(16.dp), color = Color.Gray, fontSize = 13.sp)
                }
            } else {
                for (ev in targetCollegeEvents) {
                    val isExpired = ev.isExpired()
                    val safePosterUrl = ev.posterUrl ?: ""
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column {
                            if (safePosterUrl.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight()
                                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                                        .clickable { fullScreenImageUri = safePosterUrl }
                                ) {
                                    AsyncImage(
                                        model = ImageLoaderHelper.getSafeImageModel(safePosterUrl),
                                        contentDescription = ev.title,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxWidth().wrapContentHeight()
                                    )

                                    if (isExpired) {
                                        Surface(
                                            color = Color.Red.copy(alpha = 0.85f),
                                            shape = RoundedCornerShape(bottomStart = 8.dp),
                                            modifier = Modifier.align(Alignment.TopEnd)
                                        ) {
                                            Text(
                                                "EXPIRED / ENDED",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(ev.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF0F172A))
                                        Text("Category: ${ev.category} • Role: ${ev.coordinatorRole}", fontSize = 12.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold)
                                        Text("📅 ${ev.eventDate} • ⏰ ${ev.startTime} to ${ev.endTime}", fontSize = 12.sp, color = Color(0xFF475569))
                                    }

                                    Button(
                                        onClick = {
                                            onDeleteEvent(ev)
                                            Toast.makeText(context, "Event deleted from feed!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("🗑️ Delete", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (adminTab == 3 && isCollegeAdmin) {
            val safeBannerUri = currentCampusBannerUri ?: ""
            val safeLayout = currentLayoutUri ?: ""

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🏛️️ Section 1: Campus Entrance / Banner Photo", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("This photo appears as the front card in Campus Directory and Governance Portal.", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(10.dp))

                        if (safeBannerUri.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(10.dp))
                                    .clickable { fullScreenImageUri = safeBannerUri }
                            ) {
                                AsyncImage(
                                    model = ImageLoaderHelper.getSafeImageModel(safeBannerUri),
                                    contentDescription = "Campus Entrance",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Button(
                            onClick = { campusBannerPicker.launch("image/*") },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text(if (safeBannerUri.isNotBlank()) "📷 Change Campus Entrance Image" else "📁 Upload Campus Entrance Image", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🗺️ Section 2: Campus Architectural Layout / Blueprint Map", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Students will see this architectural map inside college details in full uncut view.", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(10.dp))

                        if (safeLayout.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(10.dp))
                                    .clickable { fullScreenImageUri = safeLayout }
                            ) {
                                AsyncImage(
                                    model = ImageLoaderHelper.getSafeImageModel(safeLayout),
                                    contentDescription = "Blueprint Map",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Button(
                            onClick = { blueprintPicker.launch("image/*") },
                            modifier = Modifier.fillMaxWidth().height(46.5.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                        ) {
                            Text(if (safeLayout.isNotBlank()) "🗺️️ Change Blueprint Layout Map" else "📁 Upload Blueprint Layout Map", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}