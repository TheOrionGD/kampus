package com.example.kampus.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.kampus.MongoDBHelper
import com.example.kampus.models.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamFinderScreen(
    currentUserEmail: String = "",
    events: List<CollegeEvent>,
    participantsList: List<EventParticipant>,
    allStudents: List<StudentUser> = emptyList(),
    allPosts: List<StudentPost> = emptyList()
) {
    var selectedEventId by remember { mutableStateOf("") }
    val context = LocalContext.current

    var selectedParticipantProfileEmail by remember { mutableStateOf<String?>(null) }
    var loadedPublicProfile by remember { mutableStateOf<PublicStudentProfile?>(null) }
    var isProfileLoading by remember { mutableStateOf(false) }

    // Real-time Chat State
    var activeChatParticipant by remember { mutableStateOf<EventParticipant?>(null) }
    val chatMessagesList = remember { mutableStateListOf<ChatMessage>() }
    var messageInput by remember { mutableStateOf("") }

    // Track last notified message ID to avoid duplicate notifications
    var lastNotifiedMsgId by remember { mutableStateOf("") }

    // Listen to real-time chat messages when a chat window is open
    LaunchedEffect(activeChatParticipant) {
        activeChatParticipant?.let { peer ->
            val myEmail = currentUserEmail.trim().lowercase()
            val peerEmail = peer.studentEmail.trim().lowercase()
            MongoDBHelper.listenToDirectMessages(myEmail, peerEmail) { remoteMsgs ->
                val lastMsg = remoteMsgs.lastOrNull()
                if (lastMsg != null && !lastMsg.senderEmail.equals(myEmail, ignoreCase = true)) {
                    if (lastMsg.id != lastNotifiedMsgId) {
                        lastNotifiedMsgId = lastMsg.id
                        NotificationHelper.notifyNewChatMessage(context, peer.studentName, lastMsg.messageText)
                    }
                }

                chatMessagesList.clear()
                chatMessagesList.addAll(remoteMsgs)
            }
        }
    }

    // Load full public student profile when requested
    LaunchedEffect(selectedParticipantProfileEmail) {
        val targetEmail = selectedParticipantProfileEmail
        if (!targetEmail.isNullOrBlank()) {
            isProfileLoading = true
            // Instant local fallback first
            val localStudent = allStudents.find { it.email.equals(targetEmail, ignoreCase = true) }
            val localRegisteredEventIds = participantsList
                .filter { it.studentEmail.equals(targetEmail, ignoreCase = true) }
                .map { it.eventId }
            val localParticipatedEvents = events.filter { it.id in localRegisteredEventIds }
            val localCerts = allPosts.filter { it.studentEmail.equals(targetEmail, ignoreCase = true) }

            loadedPublicProfile = localStudent?.toPublicProfile(
                participatedEvents = localParticipatedEvents,
                certifications = localCerts
            ) ?: PublicStudentProfile(
                id = targetEmail,
                name = targetEmail.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() },
                email = targetEmail,
                college = "Campus",
                department = "Student",
                year = "Final Year",
                headline = "Student & Enthusiast",
                about = "Passionate student focused on software development and team collaboration.",
                profilePhotoUri = "",
                coverPhotoUri = "",
                githubLink = "https://github.com",
                isOpenToWork = false,
                phoneNumber = "",
                skills = emptyList(),
                participatedEvents = localParticipatedEvents,
                certifications = localCerts
            )

            // Fetch live data from MongoDB Atlas
            MongoDBHelper.fetchPublicStudentProfile(targetEmail) { liveProfile ->
                if (liveProfile != null) {
                    loadedPublicProfile = liveProfile
                }
                isProfileLoading = false
            }
        } else {
            loadedPublicProfile = null
        }
    }

    LaunchedEffect(events) {
        if (selectedEventId.isEmpty() && events.isNotEmpty()) {
            selectedEventId = events[0].id
        }
    }

    // 💬 REAL-TIME CHAT DIALOG
    activeChatParticipant?.let { peer ->
        AlertDialog(
            onDismissRequest = { activeChatParticipant = null },
            confirmButton = {
                TextButton(onClick = { activeChatParticipant = null }) {
                    Text("Close Chat", fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2563EB),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(peer.studentName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(peer.studentName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("🟢 Live Chat • ${peer.department}", fontSize = 11.sp, color = Color(0xFF16A34A))
                        }
                    }

                    Surface(
                        color = Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable {
                            selectedParticipantProfileEmail = peer.studentEmail
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("👤", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "View Full Profile",
                                color = Color(0xFF2563EB),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                ) {
                    // Message Box History
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        if (chatMessagesList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No messages yet. Start the conversation! 👋", color = Color.Gray, fontSize = 12.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(chatMessagesList) { msg ->
                                    val isMe = msg.senderEmail.equals(currentUserEmail, ignoreCase = true)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                                    ) {
                                        Surface(
                                            color = if (isMe) Color(0xFF2563EB) else Color.White,
                                            shape = RoundedCornerShape(12.dp),
                                            shadowElevation = 1.dp,
                                            modifier = Modifier.widthIn(max = 240.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = msg.messageText,
                                                    color = if (isMe) Color.White else Color(0xFF0F172A),
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Input & Send
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = messageInput,
                            onValueChange = { messageInput = it },
                            placeholder = { Text("Type a message...", fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                if (messageInput.isNotBlank()) {
                                    val newMsg = ChatMessage(
                                        id = "${System.currentTimeMillis()}_${(0..999).random()}",
                                        senderEmail = currentUserEmail.trim().lowercase(),
                                        receiverEmail = peer.studentEmail.trim().lowercase(),
                                        messageText = messageInput.trim(),
                                        timestamp = System.currentTimeMillis()
                                    )
                                    MongoDBHelper.sendDirectMessage(newMsg) { _ -> }
                                    messageInput = ""
                                }
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("➤", color = Color.White, fontSize = 16.sp)
                        }
                    }
                }
            }
        )
    }

    // 👤 FULL STUDENT PUBLIC PROFILE MODAL DIALOG
    if (selectedParticipantProfileEmail != null && loadedPublicProfile != null) {
        val targetStudent = loadedPublicProfile!!

        AlertDialog(
            onDismissRequest = { selectedParticipantProfileEmail = null },
            confirmButton = {
                Button(
                    onClick = { selectedParticipantProfileEmail = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Done")
                }
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Student Public Profile", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0F172A))
                    if (isProfileLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header Avatar & Identity
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier
                                .size(68.dp)
                                .border(2.dp, Color(0xFF2563EB), CircleShape)
                        ) {
                            if (targetStudent.profilePhotoUri.isNotBlank()) {
                                AsyncImage(
                                    model = ImageLoaderHelper.getSafeImageModel(targetStudent.profilePhotoUri),
                                    contentDescription = "Profile Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        targetStudent.name.take(1).uppercase(),
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    targetStudent.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (targetStudent.isOpenToWork) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(color = Color(0xFFDCFCE7), shape = RoundedCornerShape(4.dp)) {
                                        Text(
                                            "#OPENTOWORK",
                                            color = Color(0xFF15803D),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                "${targetStudent.department} • ${targetStudent.year}",
                                fontSize = 12.sp,
                                color = Color(0xFF2563EB),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                "🏛️ ${targetStudent.college}",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Contact & Verified Links
                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "📧 Email: ${targetStudent.email}",
                                fontSize = 12.sp,
                                color = Color(0xFF334155),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (targetStudent.phoneNumber.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "📱 Mobile: ${targetStudent.phoneNumber}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF334155),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            if (targetStudent.githubLink.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "🐙 GitHub: ${targetStudent.githubLink}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF2563EB),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // About / Bio Section
                    Text("About & Professional Summary:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (targetStudent.about.isNotBlank()) targetStudent.about else targetStudent.headline,
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        lineHeight = 17.sp
                    )

                    // Skills Section
                    if (targetStudent.skills.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Technical Skills:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                        Spacer(modifier = Modifier.height(6.dp))
                        OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            targetStudent.skills.forEach { skill ->
                                Surface(
                                    color = Color(0xFFEFF6FF),
                                    shape = RoundedCornerShape(14.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
                                ) {
                                    Text(
                                        text = skill.trim(),
                                        color = Color(0xFF1D4ED8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // 🏆 PARTICIPATED EVENTS SECTION
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🏆 Participated Events (${targetStudent.participatedEvents.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF0F172A)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (targetStudent.participatedEvents.isEmpty()) {
                        Surface(
                            color = Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "No registered event history found yet.",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        targetStudent.participatedEvents.forEach { ev ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            ev.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF166534)
                                        )
                                        Text(
                                            "🏛️ ${ev.college} • ${ev.category} • Date: ${ev.eventDate}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF15803D)
                                        )
                                    }
                                    Surface(
                                        color = Color(0xFFDCFCE7),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            "Participated",
                                            color = Color(0xFF166534),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 📜 CERTIFICATIONS & ACHIEVEMENTS SECTION
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "📜 Certifications & Achievements (${targetStudent.certifications.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (targetStudent.certifications.isEmpty()) {
                        Surface(
                            color = Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "No certifications or achievement posts shared yet.",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        targetStudent.certifications.forEach { cert ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                elevation = CardDefaults.cardElevation(1.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    if (cert.content.isNotBlank()) {
                                        Text(
                                            cert.content,
                                            fontSize = 12.sp,
                                            color = Color(0xFF1E293B),
                                            lineHeight = 16.sp
                                        )
                                    }
                                    if (cert.imageUri.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        AsyncImage(
                                            model = ImageLoaderHelper.getSafeImageModel(cert.imageUri),
                                            contentDescription = "Certificate / Post Image",
                                            contentScale = ContentScale.FillWidth,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(140.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                        )
                                    }
                                    if (cert.postedTime.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Issued/Posted: ${cert.postedTime}",
                                            fontSize = 10.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp)
    ) {
        Text("👥 Team Finder Directory", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        Text("Find teammates and connect for registered events", fontSize = 12.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(14.dp))

        if (events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No live events available for team building.", color = Color.Gray)
            }
        } else {
            Text("SELECT EVENT:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
            Spacer(modifier = Modifier.height(6.dp))

            SecondaryScrollableTabRow(
                selectedTabIndex = events.indexOfFirst { it.id == selectedEventId }.coerceAtLeast(0),
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                events.forEach { event ->
                    val isSelected = selectedEventId == event.id
                    Tab(
                        selected = isSelected,
                        onClick = { selectedEventId = event.id },
                        text = {
                            Text(
                                text = event.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF2563EB) else Color(0xFF64748B)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val currentEventParticipants = participantsList.filter { it.eventId == selectedEventId }

            if (currentEventParticipants.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "No participants registered for this event yet. Once students open the registration form, they will appear here to form teams!",
                        modifier = Modifier.padding(16.dp),
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(currentEventParticipants) { participant ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
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
                                        text = participant.studentName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                    Surface(
                                        color = Color(0xFFDCFCE7),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = participant.year,
                                            color = Color(0xFF15803D),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "🏛️ ${participant.collegeName} • ${participant.department}",
                                    fontSize = 13.sp,
                                    color = Color(0xFF475569)
                                )

                                if (participant.skills.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "⚡ Skills: ${participant.skills.joinToString(", ")}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF2563EB),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        activeChatParticipant = participant
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(42.dp)
                                ) {
                                    Text("💬 Direct Chat / Connect", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // LINK TO VIEW FULL PUBLIC PROFILE
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedParticipantProfileEmail = participant.studentEmail
                                        },
                                    color = Color(0xFFF1F5F9)
                                ) {
                                    Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "👤 View ${participant.studentName}'s Full Profile ➔",
                                            color = Color(0xFF1E40AF),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}