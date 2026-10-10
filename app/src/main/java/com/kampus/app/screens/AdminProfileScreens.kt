package com.kampus.app.screens

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
import com.kampus.app.MongoDBHelper
import com.kampus.app.models.AdminUser
import com.kampus.app.models.FacultyKYC
import com.kampus.app.models.FacultyRole
import com.kampus.app.models.ImageLoaderHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacultyProfileScreen(
    faculty: FacultyKYC,
    onUpdateFaculty: (FacultyKYC) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current

    var showCoverOptions by remember { mutableStateOf(false) }
    var showAvatarOptions by remember { mutableStateOf(false) }
    var showEditAboutModal by remember { mutableStateOf(false) }
    var showEditInfoModal by remember { mutableStateOf(false) }
    var fullScreenImageUri by remember { mutableStateOf<String?>(null) }

    var currentCoverUri by remember { mutableStateOf(faculty.collegePhotoUri) }
    var currentProfileUri by remember { mutableStateOf(faculty.profilePhotoUri) }

    LaunchedEffect(faculty.collegePhotoUri, faculty.profilePhotoUri) {
        currentCoverUri = faculty.collegePhotoUri
        currentProfileUri = faculty.profilePhotoUri
    }

    val coverPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            MongoDBHelper.processPickedImageUri(context, it, "campus_banners") { cloudDataUrl ->
                currentCoverUri = cloudDataUrl
                onUpdateFaculty(faculty.copy(collegePhotoUri = cloudDataUrl))
                Toast.makeText(context, "Cover Photo Saved to Cloud!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val avatarPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            MongoDBHelper.processPickedImageUri(context, it, "faculty_avatars") { cloudDataUrl ->
                currentProfileUri = cloudDataUrl
                onUpdateFaculty(faculty.copy(profilePhotoUri = cloudDataUrl))
                Toast.makeText(context, "Profile Photo Saved to Cloud!", Toast.LENGTH_SHORT).show()
            }
        }
    }

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

    // EDIT ABOUT & HEADLINE MODAL
    if (showEditAboutModal) {
        var editHeadline by remember { mutableStateOf(faculty.headline.ifBlank { faculty.designation }) }
        var editAbout by remember { mutableStateOf(faculty.about) }

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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateFaculty(
                            faculty.copy(
                                headline = editHeadline.trim(),
                                about = editAbout.trim()
                            )
                        )
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

    // EDIT PERSONAL INFO MODAL (name, designation, contact, website, accreditation)
    if (showEditInfoModal) {
        var editName by remember { mutableStateOf(faculty.name) }
        var editDesignation by remember { mutableStateOf(faculty.designation) }
        var editContact by remember { mutableStateOf(faculty.contactNumber) }
        var editWebsite by remember { mutableStateOf(faculty.collegeWebsite) }
        var editAccreditation by remember { mutableStateOf(faculty.accreditation) }

        AlertDialog(
            onDismissRequest = { showEditInfoModal = false },
            title = { Text("Edit Professional Info", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = editDesignation, onValueChange = { editDesignation = it }, label = { Text("Designation / Role") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = editContact, onValueChange = { editContact = it }, label = { Text("Contact Number") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = editWebsite, onValueChange = { editWebsite = it }, label = { Text("College Website") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = editAccreditation, onValueChange = { editAccreditation = it }, label = { Text("Accreditation") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateFaculty(
                            faculty.copy(
                                name = editName.trim().ifBlank { faculty.name },
                                designation = editDesignation.trim(),
                                contactNumber = editContact.trim(),
                                collegeWebsite = editWebsite.trim(),
                                accreditation = editAccreditation.trim()
                            )
                        )
                        showEditInfoModal = false
                        Toast.makeText(context, "Professional info updated!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditInfoModal = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // COVER PHOTO BOTTOM SHEET
    if (showCoverOptions) {
        ModalBottomSheet(onDismissRequest = { showCoverOptions = false }, containerColor = Color.White) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Cover Photo Options", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF0F172A))
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        showCoverOptions = false
                        coverPhotoPicker.launch("image/*")
                    }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📷", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(if (currentCoverUri.isNotBlank()) "Change Campus Cover Photo" else "Upload Campus Cover Photo", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }

                if (currentCoverUri.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            currentCoverUri = ""
                            onUpdateFaculty(faculty.copy(collegePhotoUri = ""))
                            showCoverOptions = false
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
    if (showAvatarOptions) {
        ModalBottomSheet(onDismissRequest = { showAvatarOptions = false }, containerColor = Color.White) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Profile Photo Options", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF0F172A))
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        showAvatarOptions = false
                        avatarPhotoPicker.launch("image/*")
                    }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🖼️", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(if (currentProfileUri.isNotBlank()) "Change Profile Photo" else "Upload Profile Photo", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }

                if (currentProfileUri.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            currentProfileUri = ""
                            onUpdateFaculty(faculty.copy(profilePhotoUri = ""))
                            showAvatarOptions = false
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

    val isCollegeAdmin = faculty.role == FacultyRole.COLLEGE_ADMIN
    val roleLabel = if (isCollegeAdmin) "College Admin" else "Department Faculty"
    val headlineText = faculty.headline.ifBlank { faculty.designation }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
            .verticalScroll(rememberScrollState())
    ) {
        // COVER BANNER & PROFILE CARD
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
                            contentDescription = "Campus Cover Photo",
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
                            text = faculty.collegeName.uppercase(),
                            color = Color(0xFFFBBF24),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )

                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { showCoverOptions = true }
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
                                .border(width = 2.dp, color = Color(0xFFCBD5E1), shape = CircleShape)
                                .background(Color(0xFFE2E8F0))
                                .clickable { showAvatarOptions = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentProfileUri.isNotBlank()) {
                                AsyncImage(
                                    model = ImageLoaderHelper.getSafeImageModel(currentProfileUri),
                                    contentDescription = "Faculty Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = faculty.name.take(1).uppercase(),
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height((-35).dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Text(
                                text = faculty.name,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))

                            if (faculty.isVerifiedBySuperAdmin) {
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
                                        Text("Super Admin Verified", color = Color(0xFF1D4ED8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else if (faculty.isVerifiedByCollegeAdmin) {
                                Surface(
                                    color = Color(0xFFF0FDF4),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("🛡️", fontSize = 10.sp)
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("College Admin Verified", color = Color(0xFF15803D), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                Surface(
                                    color = Color(0xFFFFFBEB),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("⏳", fontSize = 10.sp)
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("KYC Pending", color = Color(0xFF92400E), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = onLogout,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🚪", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Logout", color = Color(0xFFDC2626), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = headlineText,
                        fontSize = 13.sp,
                        color = Color(0xFF334155),
                        lineHeight = 17.sp,
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🏛️ ${faculty.collegeName} • ${faculty.department} ($roleLabel)",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "✉️ ${faculty.collegeEmail}",
                        fontSize = 12.sp,
                        color = Color(0xFF2563EB),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { showEditInfoModal = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "✏️ Edit Profile Info",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = { showEditAboutModal = true },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(0.9f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "📝 Edit About",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF475569),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ABOUT SECTION CARD
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
                    text = faculty.about.ifBlank {
                        "Dedicated ${faculty.designation} at ${faculty.collegeName} managing the ${faculty.department} department and coordinating campus events."
                    },
                    fontSize = 13.sp,
                    color = Color(0xFF475569),
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // CONTACT & CREDENTIALS CARD
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Contact & Credentials", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Spacer(modifier = Modifier.height(10.dp))

                ProfileInfoRow(icon = "📞", label = "Contact Number", value = faculty.contactNumber.ifBlank { "Not Provided" })
                ProfileInfoRow(icon = "✉️", label = "Official Email", value = faculty.collegeEmail)
                if (faculty.accreditation.isNotBlank()) {
                    ProfileInfoRow(icon = "📜", label = "Accreditation", value = faculty.accreditation)
                }
                if (faculty.collegeWebsite.isNotBlank()) {
                    ProfileInfoRow(icon = "🌐", label = "College Website", value = faculty.collegeWebsite)
                }
                ProfileInfoRow(icon = "🎖️", label = "Designation", value = faculty.designation)
                ProfileInfoRow(icon = "🏢", label = "Department", value = faculty.department)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ID PROOF CARD
        if (faculty.idProofUri.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📄 Institutional ID Proof", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Text("Stored securely in cloud database • Tap to view full", fontSize = 11.sp, color = Color(0xFF64748B))
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(10.dp))
                            .clickable { fullScreenImageUri = faculty.idProofUri }
                    ) {
                        AsyncImage(
                            model = ImageLoaderHelper.getSafeImageModel(faculty.idProofUri),
                            contentDescription = "ID Proof",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().wrapContentHeight()
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // CAMPUS LAYOUT CARD
        if (faculty.campusLayoutUri.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🗺️ Campus Blueprint Layout", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Text("Campus architectural map • Tap to view full", fontSize = 11.sp, color = Color(0xFF64748B))
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(10.dp))
                            .clickable { fullScreenImageUri = faculty.campusLayoutUri }
                    ) {
                        AsyncImage(
                            model = ImageLoaderHelper.getSafeImageModel(faculty.campusLayoutUri),
                            contentDescription = "Campus Layout",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().wrapContentHeight()
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ProfileInfoRow(icon: String, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 15.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = Color(0xFF64748B))
            Text(
                value,
                fontSize = 13.sp,
                color = Color(0xFF0F172A),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminProfileScreen(
    superAdminEmail: String,
    pendingKycCount: Int,
    approvedCollegesCount: Int,
    totalFacultiesCount: Int,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var adminProfile by remember { mutableStateOf<AdminUser?>(null) }
    var showEditModal by remember { mutableStateOf(false) }
    var showAvatarOptions by remember { mutableStateOf(false) }
    var fullScreenImageUri by remember { mutableStateOf<String?>(null) }
    var currentProfileUri by remember { mutableStateOf("") }

    LaunchedEffect(superAdminEmail) {
        MongoDBHelper.fetchSuperAdminProfile(superAdminEmail) { profile ->
            adminProfile = profile
            currentProfileUri = profile?.profilePhotoUri ?: ""
        }
    }

    val avatarPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            MongoDBHelper.processPickedImageUri(context, it, "super_admin_avatars") { cloudDataUrl ->
                currentProfileUri = cloudDataUrl
                val current = adminProfile
                if (current != null) {
                    adminProfile = current.copy(profilePhotoUri = cloudDataUrl)
                    MongoDBHelper.saveSuperAdminProfile(
                        current.email, current.name, cloudDataUrl, current.headline, current.about
                    ) { success ->
                        if (success) {
                            Toast.makeText(context, "Profile photo saved to cloud!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
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

    if (showEditModal) {
        val current = adminProfile
        if (current != null) {
            var editName by remember { mutableStateOf(current.name) }
            var editHeadline by remember { mutableStateOf(current.headline) }
            var editAbout by remember { mutableStateOf(current.about) }

            AlertDialog(
                onDismissRequest = { showEditModal = false },
                title = { Text("Edit Super Admin Profile", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = editHeadline, onValueChange = { editHeadline = it }, label = { Text("Headline / Title") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = editAbout, onValueChange = { editAbout = it }, label = { Text("About") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val updated = current.copy(
                                name = editName.trim().ifBlank { current.name },
                                headline = editHeadline.trim(),
                                about = editAbout.trim()
                            )
                            adminProfile = updated
                            MongoDBHelper.saveSuperAdminProfile(
                                updated.email, updated.name, updated.profilePhotoUri, updated.headline, updated.about
                            ) { success ->
                                Toast.makeText(
                                    context,
                                    if (success) "Super Admin profile synced to cloud!" else "Failed to sync profile",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            showEditModal = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("Save Changes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditModal = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    if (showAvatarOptions) {
        ModalBottomSheet(onDismissRequest = { showAvatarOptions = false }, containerColor = Color.White) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Profile Photo Options", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF0F172A))
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        showAvatarOptions = false
                        avatarPhotoPicker.launch("image/*")
                    }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🖼️", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(if (currentProfileUri.isNotBlank()) "Change Profile Photo" else "Upload Profile Photo", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                if (currentProfileUri.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            currentProfileUri = ""
                            val current = adminProfile
                            if (current != null) {
                                adminProfile = current.copy(profilePhotoUri = "")
                                MongoDBHelper.saveSuperAdminProfile(current.email, current.name, "", current.headline, current.about) { _ -> }
                            }
                            showAvatarOptions = false
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

    val profile = adminProfile

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
            .verticalScroll(rememberScrollState())
    ) {
        // COVER BANNER & PROFILE CARD
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
                        .height(120.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF4C1D95), Color(0xFF6D28D9), Color(0xFF8B5CF6))
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("🛡️ SUPER ADMIN", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Box(modifier = Modifier.offset(y = (-40).dp)) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .border(width = 2.dp, color = Color(0xFFDDD6FE), shape = CircleShape)
                                .background(Color(0xFFEDE9FE))
                                .clickable { showAvatarOptions = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentProfileUri.isNotBlank()) {
                                AsyncImage(
                                    model = ImageLoaderHelper.getSafeImageModel(currentProfileUri),
                                    contentDescription = "Super Admin Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = (profile?.name ?: "A").take(1).uppercase(),
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF5B21B6)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height((-30).dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Text(
                                text = profile?.name ?: "Super Administrator",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFFF5F3FF),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDDD6FE))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🛡️", fontSize = 10.sp)
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Root Access", color = Color(0xFF6D28D9), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Button(
                            onClick = onLogout,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🚪", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Logout", color = Color(0xFFDC2626), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = profile?.headline ?: "Kampus Super Administrator",
                        fontSize = 13.sp,
                        color = Color(0xFF334155),
                        lineHeight = 17.sp,
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "✉️ ${superAdminEmail}",
                        fontSize = 12.sp,
                        color = Color(0xFF2563EB),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { showEditModal = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "✏️ Edit Profile",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = onBack,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(0.9f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "← Governance Portal",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF475569),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // GOVERNANCE STATS CARD
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Governance Overview", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatBlock(value = "$approvedCollegesCount", label = "Approved Colleges", tint = Color(0xFF16A34A))
                    StatBlock(value = "$totalFacultiesCount", label = "Total Faculties", tint = Color(0xFF2563EB))
                    StatBlock(value = "$pendingKycCount", label = "Pending KYCs", tint = Color(0xFFD97706))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ABOUT SECTION CARD
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
                        modifier = Modifier.size(32.dp).clickable { showEditModal = true }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✏️", fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = (profile?.about ?: "").ifBlank { "Platform administrator responsible for verifying colleges and managing the Kampus governance hierarchy." },
                    fontSize = 13.sp,
                    color = Color(0xFF475569),
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // PROFILE PHOTO CARD
        if (currentProfileUri.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🖼️ Profile Photo", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    Text("Stored securely in cloud database • Tap to view full", fontSize = 11.sp, color = Color(0xFF64748B))
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(10.dp))
                            .clickable { fullScreenImageUri = currentProfileUri }
                    ) {
                        AsyncImage(
                            model = ImageLoaderHelper.getSafeImageModel(currentProfileUri),
                            contentDescription = "Super Admin Profile Photo",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().wrapContentHeight()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun RowScope.StatBlock(value: String, label: String, tint: Color) {
    Column(
        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = tint, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(
            label,
            fontSize = 11.sp,
            color = Color(0xFF64748B),
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
