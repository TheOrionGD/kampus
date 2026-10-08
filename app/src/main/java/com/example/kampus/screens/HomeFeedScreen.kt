package com.example.kampus.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import coil.compose.AsyncImage
import com.example.kampus.models.CollegeEvent
import com.example.kampus.models.StudentUser

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.kampus.MongoDBHelper

data class FeedCategoryBanner(
    val name: String,
    val subtitle: String,
    val imageUrl: String,
    val icon: String,
    val inchargeRole: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
    user: StudentUser,
    events: List<CollegeEvent>,
    onEventClick: (CollegeEvent) -> Unit,
    onRefresh: (() -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var categoryDetailView by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    val handleRefresh: () -> Unit = {
        isRefreshing = true
        coroutineScope.launch {
            MongoDBHelper.resetConnection()
            MongoDBHelper.prewarmConnection()
            onRefresh?.invoke()
            delay(1200)
            isRefreshing = false
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = handleRefresh,
        modifier = Modifier.fillMaxSize()
    ) {

    val allCategoryBanners = listOf(
        FeedCategoryBanner("Hackathon", "Build Tech Solutions & Win Cash Prizes", "https://images.unsplash.com/photo-1504384308090-c894fdcc538d?w=800", "💻", "Event Convener / Faculty"),
        FeedCategoryBanner("Workshop", "Hands-on Masterclasses & Certifications", "https://images.unsplash.com/photo-1517245386807-bb43f82c33c4?w=800", "🛠️", "Technical Workshop Head"),
        FeedCategoryBanner("Symposium", "National Conferences & Paper Presentations", "https://images.unsplash.com/photo-1475721027785-f74eccf877e2?w=800", "📜", "Symposium Staff Coordinator"),
        FeedCategoryBanner("Placement Drive", "Exclusive Campus & Off-Campus Hiring", "https://images.unsplash.com/photo-1521737711867-e3b97375f902?w=800", "💼", "Training & Placement Officer"),
        FeedCategoryBanner("College Fest", "Inter-College Culturals & Pro-Shows", "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=800", "🎉", "Cultural Committee Head"),
        FeedCategoryBanner("Internships & Projects", "Industry Internships & Live R&D Work", "https://images.unsplash.com/photo-1522071820081-009f0129c71c?w=800", "🚀", "Placement & Internship Officer"),
        FeedCategoryBanner("Sports & Tournaments", "Zonal Matches, Athletics & Tournaments", "https://images.unsplash.com/photo-1461896836934-ffe607ba8211?w=800", "🏆", "Physical Director (PD)"),
        FeedCategoryBanner("Courses & Webinars", "Value Added Courses & Online Masterclasses", "https://images.unsplash.com/photo-1434030216411-0b793f4b4173?w=800", "🎓", "Department Course Coordinator"),
        FeedCategoryBanner("Paper & Project Expos", "Prototype Demonstrations & Innovations", "https://images.unsplash.com/photo-1581091226825-a6a2a5aee158?w=800", "🔬", "R&D Coordinator / Innovation In-Charge"),
        FeedCategoryBanner("Clubs & NSS / NCC", "Social Service, Camps & Leadership Clubs", "https://images.unsplash.com/photo-1559027615-cd4628902d4a?w=800", "🤝", "NSS / NCC Officer & Club Head")
    )

    val filteredCategoryBanners = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            allCategoryBanners
        } else {
            allCategoryBanners.filter {
                it.name.contains(searchQuery.trim(), ignoreCase = true) ||
                        it.subtitle.contains(searchQuery.trim(), ignoreCase = true) ||
                        it.inchargeRole.contains(searchQuery.trim(), ignoreCase = true)
            }
        }
    }

    val displayedEvents = remember(events, selectedCategoryFilter, searchQuery) {
        events.filter { ev ->
            val matchesCategory = selectedCategoryFilter == null || ev.category.equals(selectedCategoryFilter, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    ev.title.contains(searchQuery.trim(), ignoreCase = true) ||
                    ev.category.contains(searchQuery.trim(), ignoreCase = true) ||
                    ev.college.contains(searchQuery.trim(), ignoreCase = true) ||
                    ev.targetDept.contains(searchQuery.trim(), ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    if (categoryDetailView != null) {
        val currentCategoryName = categoryDetailView!!
        val categoryEvents = events.filter { it.category.equals(currentCategoryName, ignoreCase = true) }

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
                TextButton(onClick = { categoryDetailView = null }) {
                    Text("← Back to Feed", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2563EB))
                }
                Surface(
                    color = Color(0xFFEFF6FF),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${categoryEvents.size} Events",
                        color = Color(0xFF1D4ED8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "🎯 $currentCategoryName Opportunities",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Text(
                text = "Exclusive verified events under this category",
                fontSize = 12.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (categoryEvents.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Text(
                            text = "No active events available under $currentCategoryName yet.",
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
                    items(categoryEvents) { ev ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEventClick(ev) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // 🏛️ Top Row: College Name & Website / Registration Link Symbol
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("🏛️", fontSize = 15.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = ev.college,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF2563EB)
                                        )
                                    }

                                    // External Registration Website Link Symbol
                                    Surface(
                                        color = Color(0xFFEFF6FF),
                                        shape = CircleShape,
                                        modifier = Modifier.clickable {
                                            if (ev.externalRegLink.isNotBlank()) {
                                                val url = if (!ev.externalRegLink.startsWith("http://") && !ev.externalRegLink.startsWith("https://")) {
                                                    "https://${ev.externalRegLink}"
                                                } else {
                                                    ev.externalRegLink
                                                }
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                context.startActivity(intent)
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = "🌐",
                                            fontSize = 16.sp,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(8.dp))

                                // Event Title & Category Tag
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ev.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = Color(0xFF0F172A),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Surface(
                                        color = Color(0xFFF1F5F9),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = ev.category,
                                            color = Color(0xFF475569),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Target Dept: ${ev.targetDept}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "📅 ${ev.eventDate} | ⏰ Deadline: ${ev.deadline}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )

                                Spacer(modifier = Modifier.height(10.dp))
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Student Event Hub ✨",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Welcome back, ${user.name} (${user.department})",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Surface(
                            color = Color(0xFFEFF6FF),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "🔔",
                                fontSize = 20.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search Hackathon, Sports, Internship, Expos...", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    leadingIcon = { Text("🔍", fontSize = 16.sp) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            Text(
                                text = "✕",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                modifier = Modifier
                                    .clickable { searchQuery = "" }
                                    .padding(8.dp)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2563EB),
                        unfocusedContainerColor = Color.White,
                        focusedContainerColor = Color.White
                    )
                )
            }

            if (selectedCategoryFilter != null || searchQuery.isNotBlank()) {
                item {
                    Surface(
                        color = Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = buildString {
                                    append("Showing ")
                                    if (selectedCategoryFilter != null) append("[$selectedCategoryFilter] ")
                                    if (searchQuery.isNotBlank()) append("for \"$searchQuery\" ")
                                    append("(${displayedEvents.size})")
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF1D4ED8)
                            )
                            Text(
                                text = "✕ Clear Filter",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626),
                                modifier = Modifier.clickable {
                                    selectedCategoryFilter = null
                                    searchQuery = ""
                                }
                            )
                        }
                    }
                }
            }

            if (filteredCategoryBanners.isNotEmpty()) {
                item {
                    Text(
                        text = "🎯 EXPLORE OPPORTUNITIES (${filteredCategoryBanners.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF475569)
                    )
                }

                items(filteredCategoryBanners) { item ->
                    val activeCount = events.count { it.category.equals(item.name, ignoreCase = true) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(115.dp)
                            .clickable {
                                categoryDetailView = item.name
                            },
                        shape = RoundedCornerShape(18.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
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
                                            listOf(
                                                Color(0xEB0F172A),
                                                Color(0x990F172A),
                                                Color.Transparent
                                            )
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
                                        Text(item.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(item.subtitle, fontSize = 11.sp, color = Color(0xFFCBD5E1), maxLines = 1)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "In-Charge: ${item.inchargeRole} • $activeCount Active",
                                        fontSize = 10.sp,
                                        color = Color(0xFFFDE047),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text("➔", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⚡ FEATURED & LIVE EVENTS (${displayedEvents.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF475569)
                )
            }

            if (displayedEvents.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank())
                                "No events found matching '$searchQuery'."
                            else
                                "No active events in this category yet.",
                            modifier = Modifier.padding(18.dp),
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(displayedEvents) { ev ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEventClick(ev) },
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
                            Text(
                                text = "🏛️ ${ev.college} • Dept: ${ev.targetDept}",
                                fontSize = 12.sp,
                                color = Color(0xFF2563EB),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "📅 ${ev.eventDate} | ⏰ Deadline: ${ev.deadline}",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )

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

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
}