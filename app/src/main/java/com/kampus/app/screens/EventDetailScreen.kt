package com.kampus.app.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.kampus.app.MongoDBHelper
import com.kampus.app.models.CollegeEvent
import com.kampus.app.models.EventParticipant
import com.kampus.app.models.ImageLoaderHelper
import com.kampus.app.models.NotificationHelper
import com.kampus.app.models.StudentUser
import java.io.File

@Composable
fun EventDetailScreen(
    event: CollegeEvent,
    currentUser: StudentUser,
    participantsList: List<EventParticipant>,
    onRegisterParticipant: (EventParticipant) -> Unit,
    onUnregisterParticipant: (EventParticipant) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showFullPosterDialog by remember { mutableStateOf(false) }

    val registeredParticipant = participantsList.find {
        it.eventId == event.id && it.studentEmail.equals(currentUser.email, ignoreCase = true)
    }
    val isConfirmedRegistered = registeredParticipant != null

    if (showFullPosterDialog) {
        Dialog(
            onDismissRequest = { showFullPosterDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable { showFullPosterDialog = false },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageLoaderHelper.getSafeImageModel(event.posterUrl),
                    contentDescription = "Full Poster View",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )

                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .clickable { showFullPosterDialog = false }
                ) {
                    Text("✕", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Surface(modifier = Modifier.fillMaxWidth(), color = Color.White, shadowElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { onBack() },
                        color = Color(0xFFF1F5F9)
                    ) {
                        Text("← Back", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }

                    Text(event.category, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))

                    Text(
                        "🚩 Flag",
                        color = Color(0xFFDC2626),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { Toast.makeText(context, "Report submitted to Super Admin", Toast.LENGTH_SHORT).show() }
                            .padding(6.dp)
                    )
                }
            }
        },
        bottomBar = {
            Surface(modifier = Modifier.fillMaxWidth(), color = Color.White, shadowElevation = 10.dp) {
                Column(modifier = Modifier.navigationBarsPadding().padding(16.dp)) {
                    // STEP 1: OPEN EXTERNAL GOOGLE FORM LINK
                    Button(
                        onClick = {
                            if (event.externalRegLink.isNotBlank()) {
                                val url = if (!event.externalRegLink.startsWith("http://") && !event.externalRegLink.startsWith("https://")) {
                                    "https://${event.externalRegLink}"
                                } else {
                                    event.externalRegLink
                                }
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } else {
                                Toast.makeText(context, "No registration link provided.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("🔗 1. Open Official Registration Form ➔", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // STEP 2: USER EXPLICITLY CONFIRMS REGISTRATION COMPLETION
                    if (!isConfirmedRegistered) {
                        OutlinedButton(
                            onClick = {
                                val newParticipant = EventParticipant(
                                    eventId = event.id,
                                    studentName = currentUser.name,
                                    studentEmail = currentUser.email,
                                    collegeName = currentUser.college,
                                    department = currentUser.department,
                                    year = currentUser.year,
                                    skills = currentUser.skills
                                )
                                MongoDBHelper.saveParticipant(newParticipant) { success ->
                                    if (success) {
                                        Toast.makeText(context, "Synced to MongoDB Team Finder!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to sync with MongoDB", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                onRegisterParticipant(newParticipant)
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF16A34A)),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF16A34A))
                        ) {
                            Text("✔ 2. Confirm I Completed Form", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color(0xFFDCFCE7),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("✔ Joined in Team Finder", color = Color(0xFF15803D), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    registeredParticipant?.let { participant ->
                                        MongoDBHelper.removeParticipant(participant.eventId, participant.studentEmail) { success ->
                                            if (success) {
                                                Toast.makeText(context, "Removed from MongoDB!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        onUnregisterParticipant(participant)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(44.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDC2626))
                            ) {
                                Text("Cancel", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showFullPosterDialog = true }
            ) {
                AsyncImage(
                    model = ImageLoaderHelper.getSafeImageModel(event.posterUrl),
                    contentDescription = event.title,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                )

                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
                ) {
                    Text("🔍 Tap to Zoom", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(event.coordinatorName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                            Text("${event.coordinatorRole} • ${event.college}", fontSize = 12.sp, color = Color.Gray)
                        }
                        Surface(color = Color(0xFFEFF6FF), shape = RoundedCornerShape(6.dp)) {
                            Text(event.category, color = Color(0xFF1D4ED8), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(event.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))

                    Spacer(modifier = Modifier.height(10.dp))

                    // 🔔 Enable Deadline Reminder Notification Button
                    OutlinedButton(
                        onClick = {
                            NotificationHelper.scheduleBackgroundDeadlineAlarm(context, event)
                            Toast.makeText(context, "🔔 Deadline Reminder Scheduled! You will be alerted on ${event.deadline}.", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2563EB)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2563EB))
                    ) {
                        Text("🔔 Enable Deadline Reminder Notification", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (event.customField1Value.isNotBlank() || event.customField2Value.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                if (event.customField1Value.isNotBlank()) {
                                    Text("🔹 ${event.customField1Label}: ${event.customField1Value}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                                }
                                if (event.customField2Value.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("🔹 ${event.customField2Label}: ${event.customField2Value}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text("📢 Notice: ${event.announcementNote}", fontSize = 12.sp, color = Color(0xFF1E40AF), lineHeight = 17.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("📋 Eligibility: ${event.eligibility}", fontSize = 13.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("📅 Event Date: ${event.eventDate} | Mode: ${event.mode}", fontSize = 13.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("⏰ Reg. Deadline: ${event.deadline}", fontSize = 13.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("🏆 Perks / Prize: ${event.prizePool}", fontSize = 13.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("💳 Registration Fee: ${event.fee}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("About Event:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(event.fullDescription, fontSize = 13.sp, color = Color(0xFF334155), lineHeight = 18.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}