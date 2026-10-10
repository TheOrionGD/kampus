package com.kampus.app.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.kampus.app.models.FacultyKYC
import com.kampus.app.models.FacultyRole
import com.kampus.app.models.ImageLoaderHelper
import java.io.File

@Composable
fun SuperAdminScreen(
    superAdminEmail: String = "",
    pendingList: MutableList<FacultyKYC>,
    allFacultiesList: List<FacultyKYC>,
    onFacultyApproved: (FacultyKYC) -> Unit,
    onFacultyDeleted: (FacultyKYC) -> Unit,
    onLogout: () -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    var showProfilePage by remember { mutableStateOf(false) }
    val context = LocalContext.current

    var selectedCollegeDetail by remember { mutableStateOf<String?>(null) }
    var selectedDeptFilter by remember { mutableStateOf<String?>(null) }
    var selectedFacultyForDetails by remember { mutableStateOf<FacultyKYC?>(null) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }

    // FULL SCREEN IMAGE VIEWER DIALOG
    fullScreenImageUrl?.let { imageUrl ->
        Dialog(
            onDismissRequest = { fullScreenImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable { fullScreenImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageLoaderHelper.getSafeImageModel(imageUrl),
                    contentDescription = "Full Image View",
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
                        .clickable { fullScreenImageUrl = null }
                ) {
                    Text("✕", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }
    }

    // COMPLETE KYC DETAILS DIALOG
    selectedFacultyForDetails?.let { faculty ->
        AlertDialog(
            onDismissRequest = { selectedFacultyForDetails = null },
            confirmButton = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(
                        onClick = {
                            val wasCollegeAdmin = faculty.role == FacultyRole.COLLEGE_ADMIN ||
                                    faculty.department.contains("Admin", ignoreCase = true) ||
                                    faculty.designation.contains("Admin", ignoreCase = true)
                            onFacultyDeleted(faculty)
                            selectedFacultyForDetails = null
                            if (wasCollegeAdmin) {
                                selectedCollegeDetail = null
                                selectedDeptFilter = null
                            }
                            Toast.makeText(context, if (wasCollegeAdmin) "College Admin & all related faculty details deleted!" else "Faculty deleted from database!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) {
                        Text("🗑️ Delete")
                    }
                    Button(onClick = { selectedFacultyForDetails = null }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))) {
                        Text("Close")
                    }
                }
            },
            title = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(faculty.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Surface(
                        color = if (faculty.isVerifiedBySuperAdmin || faculty.isVerifiedByCollegeAdmin) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (faculty.isVerifiedBySuperAdmin || faculty.isVerifiedByCollegeAdmin) "✔ VERIFIED" else "PENDING",
                            color = if (faculty.isVerifiedBySuperAdmin || faculty.isVerifiedByCollegeAdmin) Color(0xFF15803D) else Color(0xFF92400E),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Text("🏛️ College: ${faculty.collegeName}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (faculty.role == FacultyRole.DEPT_FACULTY) {
                        Text("📋 Department: ${faculty.department}", fontSize = 13.sp)
                    }
                    Text("🎖️ Designation: ${faculty.designation}", fontSize = 13.sp)
                    if (faculty.accreditation.isNotBlank()) {
                        Text("📜 Accreditation: ${faculty.accreditation}", fontSize = 13.sp, color = Color.Gray)
                    }
                    Text("✉️ Email: ${faculty.collegeEmail}", fontSize = 13.sp, color = Color(0xFF2563EB))
                    Text("📞 Contact: ${faculty.contactNumber.ifEmpty { "Not Provided" }}", fontSize = 13.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        if (faculty.contactNumber.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${faculty.contactNumber}")))
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("📞 Call", fontSize = 12.sp)
                            }
                        }

                        if (faculty.collegeWebsite.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    val url = if (!faculty.collegeWebsite.startsWith("http")) "https://${faculty.collegeWebsite}" else faculty.collegeWebsite
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("🌐 Website", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    if (faculty.collegePhotoUri.isNotBlank()) {
                        Text("🏫 Campus Photo (Tap to view full):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        AsyncImage(
                            model = ImageLoaderHelper.getSafeImageModel(faculty.collegePhotoUri),
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().wrapContentHeight().clip(RoundedCornerShape(8.dp)).clickable { fullScreenImageUrl = faculty.collegePhotoUri }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (faculty.idProofUri.isNotBlank()) {
                        Text("📄 ID Proof Document (Tap to zoom):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                .clickable { fullScreenImageUrl = faculty.idProofUri }
                        ) {
                            AsyncImage(
                                model = ImageLoaderHelper.getSafeImageModel(faculty.idProofUri),
                                contentDescription = "ID Proof Full",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth().wrapContentHeight()
                            )
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
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text("🛡️ Governance Portal", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text("College & Department Hierarchy Directory", fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val approvedCollegesCount = allFacultiesList
            .filter { it.isVerifiedBySuperAdmin && it.collegeName.isNotBlank() }
            .map { it.collegeName.trim().uppercase() }
            .distinct()
            .size

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = tabIndex == 0,
                onClick = {
                    tabIndex = 0
                    selectedCollegeDetail = null
                },
                label = { Text("Pending Approvals (${pendingList.size})") }
            )

            FilterChip(
                selected = tabIndex == 1,
                onClick = {
                    tabIndex = 1
                },
                label = { Text("Approved Colleges ($approvedCollegesCount)") }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (tabIndex == 0) {
            val pendingSnapshot = pendingList.toList()
            if (pendingSnapshot.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No pending KYC verification requests.", color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(pendingSnapshot) { kyc ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFacultyForDetails = kyc },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(3.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(kyc.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    Surface(color = Color(0xFFFEF3C7), shape = RoundedCornerShape(6.dp)) {
                                        Text(if (kyc.role == FacultyRole.COLLEGE_ADMIN) "College Admin KYC" else "Dept Faculty KYC", color = Color(0xFF92400E), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }

                                Text("🏛️ ${kyc.collegeName} • ${kyc.designation}", fontSize = 13.sp, color = Color(0xFF475569))
                                Text("✉️ ${kyc.collegeEmail} | 📞 ${kyc.contactNumber}", fontSize = 12.sp, color = Color(0xFF2563EB))

                                if (kyc.idProofUri.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .wrapContentHeight()
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                            .clickable { fullScreenImageUrl = kyc.idProofUri }
                                    ) {
                                        AsyncImage(
                                            model = ImageLoaderHelper.getSafeImageModel(kyc.idProofUri),
                                            contentDescription = "ID Card Preview",
                                            contentScale = ContentScale.FillWidth,
                                            modifier = Modifier.fillMaxWidth().wrapContentHeight()
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text("🔍 Tap Card to view full ID proof & details ➔", color = Color(0xFF2563EB), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onFacultyApproved(kyc) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)), modifier = Modifier.weight(1f)) {
                                        Text("Approve KYC")
                                    }
                                    OutlinedButton(onClick = { onFacultyDeleted(kyc) }, modifier = Modifier.weight(0.6f)) {
                                        Text("Reject", color = Color.Red)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (selectedCollegeDetail == null) {
                val approvedCollegeNames = allFacultiesList
                    .filter { it.isVerifiedBySuperAdmin && it.collegeName.isNotBlank() }
                    .map { it.collegeName.trim() }
                    .distinctBy { it.uppercase() }

                if (approvedCollegeNames.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No verified colleges yet.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(approvedCollegeNames) { collegeName ->
                            val collegeRepresentative = allFacultiesList.find {
                                it.collegeName.equals(collegeName, ignoreCase = true) && it.role == FacultyRole.COLLEGE_ADMIN
                            } ?: allFacultiesList.find {
                                it.collegeName.equals(collegeName, ignoreCase = true)
                            }

                            val deptFacultiesCount = allFacultiesList.count {
                                it.collegeName.equals(collegeName, ignoreCase = true) && it.role == FacultyRole.DEPT_FACULTY
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedCollegeDetail = collegeName
                                    selectedDeptFilter = null
                                },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(3.dp)
                            ) {
                                Column {
                                    if (collegeRepresentative?.collegePhotoUri?.isNotBlank() == true) {
                                        AsyncImage(
                                            model = ImageLoaderHelper.getSafeImageModel(collegeRepresentative.collegePhotoUri),
                                            contentDescription = collegeName,
                                            contentScale = ContentScale.FillWidth,
                                            modifier = Modifier.fillMaxWidth().wrapContentHeight().clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                                        )
                                    }

                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = collegeName.uppercase(), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                                            Surface(color = Color(0xFFDCFCE7), shape = RoundedCornerShape(6.dp)) {
                                                Text("✔ ACTIVE", color = Color(0xFF15803D), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                            }
                                        }

                                        if (collegeRepresentative?.accreditation?.isNotBlank() == true) {
                                            Text("Accreditation: ${collegeRepresentative.accreditation}", fontSize = 12.sp, color = Color(0xFF64748B))
                                        }

                                        if (collegeRepresentative?.collegeWebsite?.isNotBlank() == true) {
                                            Text("🌐 ${collegeRepresentative.collegeWebsite}", fontSize = 12.sp, color = Color(0xFF2563EB))
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = Color(0xFFF1F5F9))
                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Column {
                                                Text("Admin: ${collegeRepresentative?.name ?: "Not Assigned"}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                Text("👥 $deptFacultiesCount Department Faculty Registered", fontSize = 12.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                                            }
                                            Text("View Hierarchy ➔", color = Color(0xFF2563EB), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val targetCollegeName = selectedCollegeDetail!!
                val collegeAdmin = allFacultiesList.find {
                    it.collegeName.equals(targetCollegeName, ignoreCase = true) && it.role == FacultyRole.COLLEGE_ADMIN
                }
                val departmentFaculties = allFacultiesList.filter {
                    it.collegeName.equals(targetCollegeName, ignoreCase = true) && it.role == FacultyRole.DEPT_FACULTY
                }

                val allDeptsInCollege = departmentFaculties.map { it.department.uppercase() }.distinct()

                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            selectedCollegeDetail = null
                            selectedDeptFilter = null
                        }) {
                            Text("← Back to All Colleges", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(targetCollegeName.uppercase(), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E3A8A))
                            if (collegeAdmin != null && collegeAdmin.collegeWebsite.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = {
                                        val url = if (!collegeAdmin.collegeWebsite.startsWith("http")) "https://${collegeAdmin.collegeWebsite}" else collegeAdmin.collegeWebsite
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("🌐 Visit College Official Website: ${collegeAdmin.collegeWebsite}", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("🏛️ COLLEGE ADMIN (HEAD)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                    Spacer(modifier = Modifier.height(6.dp))

                    if (collegeAdmin != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { selectedFacultyForDetails = collegeAdmin },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(collegeAdmin.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(color = Color(0xFFDCFCE7), shape = RoundedCornerShape(6.dp)) {
                                            Text("Admin Verified", color = Color(0xFF15803D), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                                        }
                                        IconButton(onClick = {
                                            onFacultyDeleted(collegeAdmin)
                                            selectedCollegeDetail = null
                                            selectedDeptFilter = null
                                            Toast.makeText(context, "College Admin & all related faculty details deleted!", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Text("🗑️", fontSize = 16.sp)
                                        }
                                    }
                                }
                                Text("Designation: ${collegeAdmin.designation}", fontSize = 13.sp, color = Color(0xFF334155))
                                Text("✉️ Email: ${collegeAdmin.collegeEmail} | 📞 Phone: ${collegeAdmin.contactNumber}", fontSize = 12.sp, color = Color(0xFF2563EB))

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("🔍 Tap Name/Card to view full ID Proof & Details ➔", color = Color(0xFF16A34A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text("🏢 SELECT DEPARTMENT DIRECTORY", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                    Text("Tap a department to expand faculty members", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))

                    val deptIcons = mapOf("CSE" to "💻", "IT" to "🌐", "ECE" to "📡", "EEE" to "⚡", "MECH" to "⚙️", "CIVIL" to "🏗️", "AI" to "🤖")

                    val availableDepts = if (allDeptsInCollege.isNotEmpty()) allDeptsInCollege else listOf("CSE", "ECE", "MECH", "IT")

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.heightIn(max = 240.dp)
                    ) {
                        items(availableDepts) { deptName ->
                            val isSelected = selectedDeptFilter == deptName
                            val count = departmentFaculties.count { it.department.equals(deptName, ignoreCase = true) }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(95.dp)
                                    .clickable {
                                        selectedDeptFilter = if (isSelected) null else deptName
                                    },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = compileColor(isSelected)),
                                elevation = CardDefaults.cardElevation(if (isSelected) 6.dp else 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(12.dp),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(deptIcons[deptName] ?: "🎓", fontSize = 22.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "$deptName Department",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isSelected) Color.White else Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = if (isSelected) "▼ Showing $count Faculty" else "► $count Faculty (Tap to view)",
                                        fontSize = 11.sp,
                                        color = if (isSelected) Color(0xFFDBEAFE) else Color.Gray,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (selectedDeptFilter != null) {
                        val filteredFaculties = departmentFaculties.filter { it.department.equals(selectedDeptFilter, ignoreCase = true) }

                        Surface(
                            color = Color(0xFFDBEAFE),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("👥 $selectedDeptFilter DEPARTMENT FACULTIES (${filteredFaculties.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E40AF))
                                TextButton(onClick = { selectedDeptFilter = null }, contentPadding = PaddingValues(0.dp)) {
                                    Text("✕ Close", color = Color(0xFF1E40AF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (filteredFaculties.isEmpty()) {
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Text("No faculty registered under $selectedDeptFilter Department yet.", modifier = Modifier.padding(16.dp), color = Color.Gray, fontSize = 13.sp)
                            }
                        } else {
                            for (fac in filteredFaculties) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { selectedFacultyForDetails = fac },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(fac.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                                            Surface(
                                                color = if (fac.isVerifiedByCollegeAdmin) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    if (fac.isVerifiedByCollegeAdmin) "✔ Dept Approved" else "Awaiting Admin Approval",
                                                    color = if (fac.isVerifiedByCollegeAdmin) Color(0xFF15803D) else Color(0xFF92400E),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Text("Role: ${fac.designation} (${fac.department})", fontSize = 13.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold)
                                        Text("✉️ ${fac.collegeEmail} | 📞 ${fac.contactNumber}", fontSize = 12.sp, color = Color.Gray)

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("🔍 Tap to View Complete Profile & ID ➔", color = Color(0xFF2563EB), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            IconButton(onClick = {
                                                onFacultyDeleted(fac)
                                                Toast.makeText(context, "${fac.name} deleted", Toast.LENGTH_SHORT).show()
                                            }) {
                                                Text("🗑️", fontSize = 16.sp)
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
        }
    }
}

private fun compileColor(isSelected: Boolean): Color {
    return if (isSelected) Color(0xFF2563EB) else Color.White
}