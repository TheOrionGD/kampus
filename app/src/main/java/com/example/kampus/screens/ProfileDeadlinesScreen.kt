package com.example.kampus.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.kampus.MongoDBHelper
import com.example.kampus.models.CollegeEvent
import com.example.kampus.models.ImageLoaderHelper
import com.example.kampus.models.NotificationHelper
import com.example.kampus.models.StudentPost
import com.example.kampus.models.StudentUser
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: StudentUser,
    postsList: List<StudentPost>,
    onAddNewPost: (StudentPost) -> Unit,
    onDeletePost: (StudentPost) -> Unit,
    onUpdateUser: (StudentUser) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var showCreatePostModal by remember { mutableStateOf(false) }
    var showEditAboutModal by remember { mutableStateOf(false) }
    var fullScreenImageUri by remember { mutableStateOf<String?>(null) }

    var showCoverOptionsSheet by remember { mutableStateOf(false) }
    var showAvatarOptionsSheet by remember { mutableStateOf(false) }

    var currentCoverUri by remember { mutableStateOf(user.coverPhotoUri) }
    var currentProfileUri by remember { mutableStateOf(user.profilePhotoUri) }
    var isOpenToWorkEnabled by remember { mutableStateOf(user.isOpenToWork) }

    LaunchedEffect(user.coverPhotoUri, user.profilePhotoUri, user.isOpenToWork) {
        currentCoverUri = user.coverPhotoUri
        currentProfileUri = user.profilePhotoUri
        isOpenToWorkEnabled = user.isOpenToWork
    }

    val coverPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            MongoDBHelper.uploadImageToStorage(context, it.toString(), "student_covers") { cloudDataUrl ->
                currentCoverUri = cloudDataUrl
                val updated = user.copy(coverPhotoUri = cloudDataUrl)
                onUpdateUser(updated)
                Toast.makeText(context, "Cover Photo Saved to Cloud!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val avatarPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            MongoDBHelper.uploadImageToStorage(context, it.toString(), "student_avatars") { cloudDataUrl ->
                currentProfileUri = cloudDataUrl
                val updated = user.copy(profilePhotoUri = cloudDataUrl)
                onUpdateUser(updated)
                Toast.makeText(context, "Profile Photo Saved to Cloud!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val userPosts = postsList.filter { it.studentEmail.equals(user.email, ignoreCase = true) }

    // FULL SCREEN IMAGE VIEWER
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

    // EDIT ABOUT & SKILLS MODAL DIALOG
    if (showEditAboutModal) {
        var editHeadline by remember { mutableStateOf(user.headline) }
        var editAbout by remember { mutableStateOf(user.about) }
        var editSkills by remember { mutableStateOf(user.skills.joinToString(", ")) }

        AlertDialog(
            onDismissRequest = { showEditAboutModal = false },
            title = { Text("Edit Profile & About", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Text("Professional Headline:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = editHeadline,
                        onValueChange = { editHeadline = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("About Summary:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = editAbout,
                        onValueChange = { editAbout = it },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Top Skills (comma separated):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = editSkills,
                        onValueChange = { editSkills = it },
                        placeholder = { Text("e.g. Flutter, Python, AI/ML") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedSkills = editSkills.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val updated = user.copy(
                            headline = editHeadline.trim(),
                            about = editAbout.trim(),
                            skills = if (parsedSkills.isNotEmpty()) parsedSkills else user.skills
                        )
                        onUpdateUser(updated)
                        showEditAboutModal = false
                        Toast.makeText(context, "Profile details updated!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditAboutModal = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // COVER PHOTO BOTTOM SHEET
    if (showCoverOptionsSheet) {
        ModalBottomSheet(onDismissRequest = { showCoverOptionsSheet = false }, containerColor = Color.White) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Cover Photo Options", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF0F172A))
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        showCoverOptionsSheet = false
                        coverPhotoPicker.launch("image/*")
                    }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📷", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(if (currentCoverUri.isNotBlank()) "Change Cover Photo" else "Upload Cover Photo", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }

                if (currentCoverUri.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            currentCoverUri = ""
                            onUpdateUser(user.copy(coverPhotoUri = ""))
                            showCoverOptionsSheet = false
                            Toast.makeText(context, "Cover photo removed", Toast.LENGTH_SHORT).show()
                        }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🗑️", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Text("Delete Cover Photo", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFFDC2626))
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // PROFILE AVATAR BOTTOM SHEET
    if (showAvatarOptionsSheet) {
        ModalBottomSheet(onDismissRequest = { showAvatarOptionsSheet = false }, containerColor = Color.White) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Profile Photo & Frame", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF0F172A))
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        showAvatarOptionsSheet = false
                        avatarPhotoPicker.launch("image/*")
                    }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🖼️", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(if (currentProfileUri.isNotBlank()) "Change Profile Photo" else "Upload Profile Photo", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        isOpenToWorkEnabled = !isOpenToWorkEnabled
                        val updated = user.copy(isOpenToWork = isOpenToWorkEnabled)
                        onUpdateUser(updated)
                        showAvatarOptionsSheet = false
                        Toast.makeText(context, if (isOpenToWorkEnabled) "#OPENTOWORK Frame Added!" else "Frame Removed!", Toast.LENGTH_SHORT).show()
                    }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🟢", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(if (isOpenToWorkEnabled) "Remove #OPENTOWORK Frame" else "Add #OPENTOWORK Frame", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }

                if (currentProfileUri.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            currentProfileUri = ""
                            onUpdateUser(user.copy(profilePhotoUri = ""))
                            showAvatarOptionsSheet = false
                            Toast.makeText(context, "Profile photo removed", Toast.LENGTH_SHORT).show()
                        }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🗑️", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Text("Delete Profile Photo", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFFDC2626))
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // CREATE POST MODAL DIALOG
    if (showCreatePostModal) {
        var postCaption by remember { mutableStateOf("") }
        var postImagePath by remember { mutableStateOf("") }

        val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                MongoDBHelper.uploadImageToStorage(context, it.toString(), "student_posts") { cloudUrl ->
                    postImagePath = cloudUrl
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showCreatePostModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color(0xFF2563EB), modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(user.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(user.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Post to Network", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = postCaption,
                        onValueChange = { postCaption = it },
                        placeholder = { Text("What skill, project, or achievement do you want to share?") },
                        modifier = Modifier.fillMaxWidth().height(110.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (postImagePath.isNotBlank()) {
                        AsyncImage(
                            model = ImageLoaderHelper.getSafeImageModel(postImagePath),
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    OutlinedButton(
                        onClick = { imagePicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (postImagePath.isNotBlank()) "✔ Change Image" else "📷 Add Certificate / Project Photo")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (postCaption.isBlank() && postImagePath.isBlank()) {
                            Toast.makeText(context, "Please enter caption or select an image!", Toast.LENGTH_SHORT).show()
                        } else {
                            onAddNewPost(
                                StudentPost(
                                    id = System.currentTimeMillis().toString(),
                                    studentEmail = user.email,
                                    studentName = user.name,
                                    studentHeadline = "${user.department} Student at ${user.college}",
                                    postedTime = "Just now",
                                    content = postCaption.trim(),
                                    imageUri = postImagePath
                                )
                            )
                            showCreatePostModal = false
                            Toast.makeText(context, "Post Added to Profile!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Post")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePostModal = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
            .verticalScroll(rememberScrollState())
    ) {
        // LINKEDIN COVER BANNER & PROFILE CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF334155))
                            )
                        )
                ) {
                    if (currentCoverUri.isNotBlank()) {
                        AsyncImage(
                            model = ImageLoaderHelper.getSafeImageModel(currentCoverUri),
                            contentDescription = "Cover Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = user.college.uppercase(),
                            color = Color(0xFFFBBF24),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )

                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { showCoverOptionsSheet = true }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("✏️", fontSize = 16.sp)
                            }
                        }
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Box(modifier = Modifier.offset(y = (-45).dp)) {
                        Box(
                            modifier = Modifier
                                .size(95.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (isOpenToWorkEnabled) 3.5.dp else 2.dp,
                                    color = if (isOpenToWorkEnabled) Color(0xFF16A34A) else Color(0xFFCBD5E1),
                                    shape = CircleShape
                                )
                                .background(Color(0xFFE2E8F0))
                                .clickable { showAvatarOptionsSheet = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentProfileUri.isNotBlank()) {
                                AsyncImage(
                                    model = ImageLoaderHelper.getSafeImageModel(currentProfileUri),
                                    contentDescription = "Profile Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = user.name.take(1).uppercase(),
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                            }
                        }

                        if (isOpenToWorkEnabled) {
                            Surface(
                                color = Color(0xFF16A34A),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .offset(y = 8.dp)
                            ) {
                                Text(
                                    "#OPENTOWORK",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isOpenToWorkEnabled) (-25).dp else (-35).dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(
                                text = user.name,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFFEFF6FF),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🛡️", fontSize = 10.sp)
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Verified Student", color = Color(0xFF1D4ED8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Prominent Big Red Logout Button
                        Button(
                            onClick = onLogout,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🚪", fontSize = 15.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Logout", color = Color(0xFFDC2626), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = user.headline,
                        fontSize = 13.sp,
                        color = Color(0xFF334155),
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🏛️ ${user.college} • ${user.year} (${user.department})",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "✉️ ${user.email}",
                        fontSize = 12.sp,
                        color = Color(0xFF2563EB)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { showCreatePostModal = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("➕ Share Skill / Post", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = { showAvatarOptionsSheet = true },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(0.9f)
                        ) {
                            Text("✏️ Edit Photos", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF475569))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ABOUT & TOP SKILLS SECTION CARD (With Edit Pen Icon)
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("About", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.size(32.dp).clickable { showEditAboutModal = true }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✏️", fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = user.about,
                    fontSize = 13.sp,
                    color = Color(0xFF475569),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth().clickable { showEditAboutModal = true }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💎", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Top Skills", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                                Text("✏️ Edit", color = Color(0xFF2563EB), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = (user.skills ?: emptyList()).joinToString(" • "),
                                fontSize = 12.sp,
                                color = Color(0xFF2563EB),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ACTIVITY & POSTS SECTION
        if (userPosts.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Activity", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            Text("${userPosts.size} posts shared", fontSize = 12.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = { showCreatePostModal = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("Create a post", color = Color(0xFF1E40AF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        userPosts.forEach { post ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = Color(0xFF2563EB),
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(user.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("${post.studentName} • You", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("${user.department} • ${post.postedTime}", fontSize = 11.sp, color = Color.Gray)
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                onDeletePost(post)
                                                Toast.makeText(context, "Post deleted from profile!", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Text("🗑️", fontSize = 16.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (post.content.isNotBlank()) {
                                        Text(
                                            text = post.content,
                                            fontSize = 13.sp,
                                            color = Color(0xFF1E293B),
                                            lineHeight = 17.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    if (post.imageUri.isNotBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .wrapContentHeight()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { fullScreenImageUri = post.imageUri }
                                        ) {
                                            AsyncImage(
                                                model = ImageLoaderHelper.getSafeImageModel(post.imageUri),
                                                contentDescription = "Post Image",
                                                contentScale = ContentScale.FillWidth,
                                                modifier = Modifier.fillMaxWidth().wrapContentHeight()
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    HorizontalDivider(color = Color(0xFFE2E8F0))
                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceAround
                                    ) {
                                        TextButton(onClick = { Toast.makeText(context, "Liked!", Toast.LENGTH_SHORT).show() }) {
                                            Text("👍 Like", fontSize = 12.sp, color = Color(0xFF475569))
                                        }
                                        TextButton(onClick = { Toast.makeText(context, "Comment feature coming soon", Toast.LENGTH_SHORT).show() }) {
                                            Text("💬 Comment", fontSize = 12.sp, color = Color(0xFF475569))
                                        }
                                        TextButton(onClick = { Toast.makeText(context, "Link Copied!", Toast.LENGTH_SHORT).show() }) {
                                            Text("↗️ Share", fontSize = 12.sp, color = Color(0xFF475569))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun DeadlinesScreen(
    user: StudentUser,
    events: List<CollegeEvent>,
    registeredIds: List<String>
) {
    val context = LocalContext.current

    // Strict filtration: Exclude events whose registration deadline day has passed or are expired
    val activeUpcomingEvents = remember(events) {
        events.filter { !NotificationHelper.isDeadlinePassed(it.deadline) && !it.isExpired() }
            .sortedBy { event ->
                NotificationHelper.parseDeadlineDate(event.deadline)?.time ?: Long.MAX_VALUE
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("⏰ Upcoming Deadlines", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        Text("Active registration closing dates (strictly filtered by valid deadline)", fontSize = 12.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(14.dp))

        if (activeUpcomingEvents.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Text("No active upcoming deadlines available.", modifier = Modifier.padding(20.dp), color = Color.Gray)
            }
        } else {
            activeUpcomingEvents.forEach { event ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(event.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Surface(color = Color(0xFFFEF2F2), shape = RoundedCornerShape(6.dp)) {
                                Text("Deadline: ${event.deadline}", color = Color(0xFFDC2626), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("🏛️ ${event.college} • Category: ${event.category}", fontSize = 12.sp, color = Color(0xFF475569))

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "📅 Event Date: ${event.eventDate} | ⏰ Time: ${event.startTime} - ${event.endTime}",
                            fontSize = 12.sp,
                            color = Color(0xFF2563EB),
                            fontWeight = FontWeight.SemiBold
                        )

                        val regLink = event.externalRegLink
                        if (regLink.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "🔗 Register Link: $regLink",
                                fontSize = 12.sp,
                                color = Color(0xFF16A34A),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    try {
                                        val url = if (!regLink.startsWith("http")) "https://$regLink" else regLink
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (_: Exception) {}
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}