package com.example.kampus.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.kampus.MongoDBHelper
import com.example.kampus.models.*

@Composable
fun StudentProfileSetupScreen(
    onComplete: (StudentUser) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") } // 📱 Added phone number state
    var college by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("CSE") }
    var year by remember { mutableStateOf("3rd Year") }
    var skillsInput by remember { mutableStateOf("Jetpack Compose, Python, Web Dev") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Surface(
                color = Color.White,
                shape = CircleShape,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onBack() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("←", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Student Profile Setup", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
            Text("Create your campus portfolio to discover events & find team members", fontSize = 12.sp, color = Color(0xFF64748B))

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("👤 Personal Credentials", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E3A8A))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full Name *") },
                        placeholder = { Text("e.g. Rahul Sharma") },
                        leadingIcon = { Text("🧑‍🎓", fontSize = 16.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Student Email *") },
                        placeholder = { Text("student@gmail.com") },
                        leadingIcon = { Text("✉️", fontSize = 16.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Account Password *") },
                        placeholder = { Text("••••••••") },
                        leadingIcon = { Text("🔒", fontSize = 16.sp) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 📱 Added Mobile Number input field right below password
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Mobile Number *") },
                        placeholder = { Text("9876543210") },
                        leadingIcon = { Text("📞", fontSize = 16.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("🏛️ Campus & Department Info", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E3A8A))

                    OutlinedTextField(
                        value = college,
                        onValueChange = { college = it },
                        label = { Text("College Name (e.g. KRCT, MKCE, KRCE) *") },
                        leadingIcon = { Text("🏫", fontSize = 16.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = department,
                            onValueChange = { department = it },
                            label = { Text("Department") },
                            placeholder = { Text("CSE, IT, ECE...") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = year,
                            onValueChange = { year = it },
                            label = { Text("Year") },
                            placeholder = { Text("3rd Year") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = skillsInput,
                        onValueChange = { skillsInput = it },
                        label = { Text("Skills (Comma Separated)") },
                        leadingIcon = { Text("⚡", fontSize = 16.sp) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (name.isBlank() || email.isBlank() || password.isBlank() || phoneNumber.isBlank() || college.isBlank()) {
                                Toast.makeText(context, "Please fill in all mandatory fields (*)", Toast.LENGTH_SHORT).show()
                            } else {
                                val skillsList = skillsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                onComplete(
                                    StudentUser(
                                        name = name.trim(),
                                        email = email.trim(),
                                        password = password.trim(),
                                        college = college.trim(),
                                        department = department.trim(),
                                        year = year.trim(),
                                        skills = skillsList,
                                        phoneNumber = phoneNumber.trim() // 📱 Passed phone number into model
                                    )
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("Complete Registration ➔", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun FacultyKYCScreen(
    onSubmitKYC: (FacultyKYC) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isCollegeAdminTier by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var collegeName by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var designation by remember { mutableStateOf("") }

    var accreditation by remember { mutableStateOf("NAAC A++ / NBA") }
    var websiteUrl by remember { mutableStateOf("") }
    var campusEntranceUri by remember { mutableStateOf("") }
    var campusLayoutUri by remember { mutableStateOf("") }

    var idProofUri by remember { mutableStateOf("") }

    val idProofPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val uriString = it.toString()
            idProofUri = uriString
            MongoDBHelper.uploadImageToStorage(context, uriString, "faculty_id_proofs") { cloudUrl ->
                idProofUri = cloudUrl
            }
        }
    }

    val campusEntrancePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val uriString = it.toString()
            campusEntranceUri = uriString
            MongoDBHelper.uploadImageToStorage(context, uriString, "campus_banners") { cloudUrl ->
                campusEntranceUri = cloudUrl
            }
        }
    }

    val campusLayoutPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val uriString = it.toString()
            campusLayoutUri = uriString
            MongoDBHelper.uploadImageToStorage(context, uriString, "campus_layouts") { cloudUrl ->
                campusLayoutUri = cloudUrl
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Surface(
                color = Color.White,
                shape = CircleShape,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onBack() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("←", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Institutional Faculty Verification (KYC)", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
            Text("Register institutional faculty credentials for posting official events", fontSize = 12.sp, color = Color(0xFF64748B))

            Spacer(modifier = Modifier.height(16.dp))

            // Tier Selector
            Surface(
                color = Color(0xFFE2E8F0),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clickable { isCollegeAdminTier = false },
                        color = if (!isCollegeAdminTier) Color(0xFF2563EB) else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Department Faculty", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (!isCollegeAdminTier) Color.White else Color(0xFF475569))
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clickable { isCollegeAdminTier = true },
                        color = if (isCollegeAdminTier) Color(0xFF7C3AED) else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🏛️️ College Admin", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isCollegeAdminTier) Color.White else Color(0xFF475569))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = if (isCollegeAdminTier) "🏛️ College Admin KYC Details" else "🎓 Department / Cell Staff KYC",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isCollegeAdminTier) Color(0xFF6D28D9) else Color(0xFF1E3A8A)
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Faculty Full Name *") },
                        placeholder = { Text("Dr. A. Sharma") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Official Institutional Email *") },
                        placeholder = { Text("name@college.edu.in") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Contact Number *") },
                        placeholder = { Text("9876543210") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Set Account Password *") },
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = collegeName,
                        onValueChange = { collegeName = it },
                        label = { Text("College / University Name *") },
                        placeholder = { Text("e.g. KRCT, MKCE, KRCE") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!isCollegeAdminTier) {
                        OutlinedTextField(
                            value = department,
                            onValueChange = { department = it },
                            label = { Text("Department / Wing *") },
                            placeholder = { Text("CSE, ECE, Sports, Placement, R&D, Clubs...") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = designation,
                            onValueChange = { designation = it },
                            label = { Text("Official Role / Designation *") },
                            placeholder = { Text("Asst. Professor, PD, Placement Head...") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = designation,
                            onValueChange = { designation = it },
                            label = { Text("Admin Designation *") },
                            placeholder = { Text("Principal / Dean / Administrative Head") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = accreditation,
                            onValueChange = { accreditation = it },
                            label = { Text("Institutional Accreditation") },
                            placeholder = { Text("NAAC A++ / NBA") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = websiteUrl,
                            onValueChange = { websiteUrl = it },
                            label = { Text("Official Website URL") },
                            placeholder = { Text("https://krct.ac.in") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("🖼️ Campus Entrance Photo", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (campusEntranceUri.isNotBlank()) {
                            AsyncImage(
                                model = ImageLoaderHelper.getSafeImageModel(campusEntranceUri),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                        OutlinedButton(
                            onClick = { campusEntrancePicker.launch("image/*") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (campusEntranceUri.isNotBlank()) "✔ Change Campus Photo" else "📁 Select Campus Entrance Photo")
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("🗺️ Campus Blueprint Layout Map", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (campusLayoutUri.isNotBlank()) {
                            AsyncImage(
                                model = ImageLoaderHelper.getSafeImageModel(campusLayoutUri),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                        OutlinedButton(
                            onClick = { campusLayoutPicker.launch("image/*") },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (campusLayoutUri.isNotBlank()) "✔ Change Blueprint Map" else "📁 Select Blueprint Layout Map")
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("📄 Faculty Institutional ID Proof *", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    if (idProofUri.isNotBlank()) {
                        AsyncImage(
                            model = ImageLoaderHelper.getSafeImageModel(idProofUri),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                    OutlinedButton(
                        onClick = { idProofPicker.launch("image/*") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (idProofUri.isNotBlank()) "✔ Change ID Proof" else "📁 Upload Faculty ID Card / Appointment Order")
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (name.isBlank() || email.isBlank() || phone.isBlank() || password.isBlank() || collegeName.isBlank() || idProofUri.isBlank()) {
                                Toast.makeText(context, "Please complete all fields and upload ID proof!", Toast.LENGTH_SHORT).show()
                            } else {
                                val role = if (isCollegeAdminTier) FacultyRole.COLLEGE_ADMIN else FacultyRole.DEPT_FACULTY
                                onSubmitKYC(
                                    FacultyKYC(
                                        name = name.trim(),
                                        collegeEmail = email.trim(),
                                        contactNumber = phone.trim(),
                                        password = password.trim(),
                                        collegeName = collegeName.trim(),
                                        department = if (isCollegeAdminTier) "College Administration" else department.trim(),
                                        designation = designation.trim(),
                                        accreditation = accreditation.trim(),
                                        collegeWebsite = websiteUrl.trim(),
                                        collegePhotoUri = campusEntranceUri,
                                        campusLayoutUri = campusLayoutUri,
                                        idProofUri = idProofUri,
                                        role = role,
                                        isVerifiedBySuperAdmin = false,
                                        isVerifiedByCollegeAdmin = false
                                    )
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCollegeAdminTier) Color(0xFF7C3AED) else Color(0xFF2563EB)
                        )
                    ) {
                        Text("Submit Verification Request ➔", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))
        }
    }
}