package com.example.kampus.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.kampus.models.CollegeEvent
import com.example.kampus.models.FacultyKYC
import com.example.kampus.models.FacultyRole
import com.example.kampus.models.ImageLoaderHelper
import com.example.kampus.models.StudentUser
import java.io.File
import kotlin.math.*

data class EventCategoryItem(
    val name: String,
    val icon: String,
    val subtitle: String
)

fun openGoogleMapsNavigation(context: android.content.Context, targetCollege: String, city: String) {
    try {
        val gmmIntentUri = Uri.parse("google.navigation:q=${Uri.encode("$targetCollege $city")}")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        context.startActivity(mapIntent)
    } catch (_: Exception) {
        val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode("$targetCollege $city")}")
        context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
    }
}

fun calculateApproximateDistance(studentCollege: String, targetCollege: String): Pair<String, Double> {
    val s = studentCollege.trim().uppercase()
    val t = targetCollege.trim().uppercase()

    if (s == t) return Pair("Same Campus", 0.0)

    val locationMap = mapOf(
        "KRCT" to Pair("Trichy", Pair(10.8938, 78.7188)),
        "KRCE" to Pair("Trichy", Pair(10.8950, 78.7195)),
        "MKCE" to Pair("Karur", Pair(10.9575, 78.0766)),
        "NIT" to Pair("Trichy", Pair(10.7589, 78.8132)),
        "SASTRA" to Pair("Thanjavur", Pair(10.7280, 79.0158)),
        "CIT" to Pair("Coimbatore", Pair(11.0283, 77.0274)),
        "PSG" to Pair("Coimbatore", Pair(11.0247, 77.0028))
    )

    val sLoc = locationMap.entries.find { s.contains(it.key) }
    val tLoc = locationMap.entries.find { t.contains(it.key) }

    val targetCity = tLoc?.value?.first ?: "Tamil Nadu"

    if (sLoc != null && tLoc != null) {
        val lat1 = Math.toRadians(sLoc.value.second.first)
        val lon1 = Math.toRadians(sLoc.value.second.second)
        val lat2 = Math.toRadians(tLoc.value.second.first)
        val lon2 = Math.toRadians(tLoc.value.second.second)

        val dlat = lat2 - lat1
        val dlon = lon2 - lon1
        val a = sin(dlat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dlon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val distanceKm = 6371.0 * c
        return Pair(targetCity, ((distanceKm * 10).roundToInt() / 10.0))
    }

    val simulatedDist = if (s.take(2) == t.take(2)) 4.2 else 68.5
    return Pair(targetCity, simulatedDist)
}

@Composable
fun CollegeDirectoryScreen(
    currentUser: StudentUser,
    allCollegesList: List<FacultyKYC>,
    allEvents: List<CollegeEvent>,
    onSelectEvent: (CollegeEvent) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCollege by remember { mutableStateOf<FacultyKYC?>(null) }

    // 🌟 Separate Next Page View State for Category Events inside College Details
    var selectedCategoryDetailView by remember { mutableStateOf<String?>(null) }

    var fullScreenLayoutUrl by remember { mutableStateOf<String?>(null) }

    fullScreenLayoutUrl?.let { layoutUrl ->
        Dialog(
            onDismissRequest = { fullScreenLayoutUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable { fullScreenLayoutUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageLoaderHelper.getSafeImageModel(layoutUrl),
                    contentDescription = "Full Campus Layout",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )

                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .clickable { fullScreenLayoutUrl = null }
                ) {
                    Text(
                        "✕",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    val verifiedColleges = remember(allCollegesList) {
        allCollegesList.filter { it.role == FacultyRole.COLLEGE_ADMIN && it.isVerifiedBySuperAdmin }
    }

    val eventCategoryCards = listOf(
        EventCategoryItem("Hackathon", "💻", "Code & Build Projects"),
        EventCategoryItem("Workshop", "🛠️", "Hands-on Masterclasses"),
        EventCategoryItem("Symposium", "📜", "Paper Presentation & Quiz"),
        EventCategoryItem("Placement Drive", "💼", "Hiring & Internship"),
        EventCategoryItem("College Fest", "🎉", "Cultural & Pro-Shows"),
        EventCategoryItem("Internships & Projects", "🚀", "Paid Internships & R&D"),
        EventCategoryItem("Sports & Tournaments", "🏆", "Athletics & Zonal Cups"),
        EventCategoryItem("Courses & Webinars", "🎓", "VAC & Guest Lectures"),
        EventCategoryItem("Paper & Project Expos", "🔬", "Innovation Demonstrations"),
        EventCategoryItem("Clubs & NSS / NCC", "🤝", "Social Service & Leadership")
    )

    if (selectedCollege == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🏛️ Campus Directory",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Auto-sorted by proximity to ${currentUser.college}",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
                Surface(
                    color = Color(0xFFEFF6FF),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        "📍 Nearby Live",
                        color = Color(0xFF2563EB),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search college, city (e.g. MKCE, KRCT, Trichy)...", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                leadingIcon = { Text("🔍", fontSize = 16.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2563EB),
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            val sortedColleges = remember(verifiedColleges, currentUser.college, searchQuery) {
                verifiedColleges
                    .map { college ->
                        val (city, distKm) = calculateApproximateDistance(currentUser.college, college.collegeName)
                        Triple(college, city, distKm)
                    }
                    .filter { (college, city, _) ->
                        college.collegeName.contains(searchQuery, ignoreCase = true) ||
                                city.contains(searchQuery, ignoreCase = true) ||
                                (college.accreditation ?: "").contains(searchQuery, ignoreCase = true)
                    }
                    .sortedBy { it.third }
            }

            if (sortedColleges.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No colleges found matching '$searchQuery'", color = Color.Gray, fontSize = 13.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(sortedColleges) { (college, city, distKm) ->
                        val isMyCampus = distKm == 0.0
                        val totalEventsCount = allEvents.count { it.college.equals(college.collegeName, ignoreCase = true) }
                        val safePhotoUri = college.collegePhotoUri ?: ""

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCollege = college
                                    selectedCategoryDetailView = null
                                },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(3.dp)
                        ) {
                            Column {
                                val defaultCampusBanner = "https://images.unsplash.com/photo-1562774053-701939374585?q=80&w=1000"
                                val displayPhotoModel = ImageLoaderHelper.getSafeImageModel(safePhotoUri) ?: defaultCampusBanner

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(155.dp)
                                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF334155))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Text("🏛️", fontSize = 34.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = college.collegeName.uppercase(),
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }

                                    AsyncImage(
                                        model = displayPhotoModel,
                                        contentDescription = college.collegeName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = college.collegeName.uppercase(),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )

                                        Surface(
                                            color = if (isMyCampus) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = if (isMyCampus) "📍 Home Campus" else "📍 $city",
                                                color = if (isMyCampus) Color(0xFF15803D) else Color(0xFF92400E),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Surface(
                                            color = Color(0xFFEFF6FF),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.clickable {
                                                openGoogleMapsNavigation(context, college.collegeName, city)
                                            }
                                        ) {
                                            Text(
                                                "🗺️ Open in Google Maps ➔",
                                                fontSize = 11.sp,
                                                color = Color(0xFF1D4ED8),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    val safeAccreditation = college.accreditation ?: ""
                                    if (safeAccreditation.isNotBlank()) {
                                        Text(
                                            text = "Accreditation: $safeAccreditation",
                                            fontSize = 12.sp,
                                            color = Color(0xFF059669),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    val safeWebsite = college.collegeWebsite ?: ""
                                    if (safeWebsite.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "🔗 $safeWebsite",
                                            fontSize = 12.sp,
                                            color = Color(0xFF2563EB),
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.clickable {
                                                try {
                                                    val url = if (!safeWebsite.startsWith("http")) "https://$safeWebsite" else safeWebsite
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                } catch (_: Exception) {}
                                            }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🎉 $totalEventsCount Live Events",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF16A34A)
                                        )
                                        Text(
                                            text = "Explore Categories ➔",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2563EB)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        val college = selectedCollege!!
        val collegeEvents = allEvents.filter { it.college.equals(college.collegeName, ignoreCase = true) }
        val (city, distKm) = calculateApproximateDistance(currentUser.college, college.collegeName)

        val safeLayoutUri = college.campusLayoutUri ?: ""
        val safeWebsite = college.collegeWebsite ?: ""

        if (selectedCategoryDetailView != null) {
            val currentCatName = selectedCategoryDetailView!!
            val catEvents = collegeEvents.filter { it.category.equals(currentCatName, ignoreCase = true) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8FAFC))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { selectedCategoryDetailView = null },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("← Back to ${college.collegeName}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2563EB))
                    }
                    Surface(
                        color = Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${catEvents.size} Events",
                            color = Color(0xFF1D4ED8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "🎯 $currentCatName at ${college.collegeName}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "Exclusive verified opportunities under this category",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (catEvents.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Text(
                                text = "No active $currentCatName events available at ${college.collegeName} right now.",
                                modifier = Modifier.padding(24.dp),
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(catEvents) { ev ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectEvent(ev) },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = ev.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Color(0xFF0F172A),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Surface(
                                            color = Color(0xFFEFF6FF),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = ev.category,
                                                color = Color(0xFF1D4ED8),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (ev.coordinatorRole.isNotBlank()) {
                                        Text("🎯 In-Charge: ${ev.coordinatorRole}", fontSize = 12.sp, color = Color(0xFF059669), fontWeight = FontWeight.SemiBold)
                                    } else {
                                        Text("🎯 Eligibility: ${ev.eligibility}", fontSize = 12.sp, color = Color(0xFF475569))
                                    }
                                    Text("📅 Date: ${ev.eventDate} | ⏰ Reg. Deadline: ${ev.deadline}", fontSize = 12.sp, color = Color(0xFF2563EB))

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Tap to View Details & Register ➔",
                                        color = Color(0xFF16A34A),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8FAFC))
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                TextButton(
                    onClick = {
                        selectedCollege = null
                        selectedCategoryDetailView = null
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("← Back to Campus Directory", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF2563EB))
                }

                Spacer(modifier = Modifier.height(4.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column {
                        val defaultBanner = "https://images.unsplash.com/photo-1562774053-701939374585?q=80&w=1000"
                        val detailCampusBanner = ImageLoaderHelper.getSafeImageModel(college.collegePhotoUri) ?: defaultBanner

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF334155))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text("🏛️", fontSize = 38.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = college.collegeName.uppercase(),
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }

                            AsyncImage(
                                model = detailCampusBanner,
                                contentDescription = "Campus Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = college.collegeName.uppercase(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF1E3A8A),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)
                            )
                            Surface(
                                color = Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (distKm == 0.0) "Your College" else "📍 $city",
                                    color = Color(0xFF92400E),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = "Head / Admin: ${college.name} (${college.designation})",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "📍 Navigate to Campus on Google Maps ↗",
                            color = Color(0xFF15803D),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable {
                                openGoogleMapsNavigation(context, college.collegeName, city)
                            }
                        )

                        if (safeWebsite.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "🌐 Tap to Open Website: $safeWebsite ↗",
                                fontSize = 12.sp,
                                color = Color(0xFF2563EB),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    try {
                                        val url = if (!safeWebsite.startsWith("http")) "https://$safeWebsite" else safeWebsite
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (_: Exception) {}
                                }
                            )
                        }
                    }
                }

                if (safeLayoutUri.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "🗺️ Campus Layout & Blueprint Map",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    "🔍 Tap for Full HD View",
                                    fontSize = 11.sp,
                                    color = Color(0xFF2563EB),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                    .clickable { fullScreenLayoutUrl = safeLayoutUri }
                            ) {
                                AsyncImage(
                                    model = ImageLoaderHelper.getSafeImageModel(safeLayoutUri),
                                    contentDescription = "Campus Map Layout",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight()
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "⚡ SELECT EVENT CATEGORY",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF475569)
                )
                Text(
                    text = "Tap a category box to view and expand its events",
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (i in eventCategoryCards.indices step 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val item1 = eventCategoryCards[i]
                            CategoryBoxCard(
                                item = item1,
                                isSelected = false,
                                count = collegeEvents.count { it.category.equals(item1.name, ignoreCase = true) },
                                modifier = Modifier.weight(1f),
                                onClick = { selectedCategoryDetailView = item1.name }
                            )

                            if (i + 1 < eventCategoryCards.size) {
                                val item2 = eventCategoryCards[i + 1]
                                CategoryBoxCard(
                                    item = item2,
                                    isSelected = false,
                                    count = collegeEvents.count { it.category.equals(item2.name, ignoreCase = true) },
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedCategoryDetailView = item2.name }
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

@Composable
fun CategoryBoxCard(
    item: EventCategoryItem,
    isSelected: Boolean,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(95.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = item.icon, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.name,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color(0xFF0F172A),
                maxLines = 1
            )
            Text(
                text = "► $count Events (Tap to view)",
                fontSize = 11.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Normal
            )
        }
    }
}