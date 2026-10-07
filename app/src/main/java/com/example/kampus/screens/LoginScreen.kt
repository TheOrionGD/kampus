package com.example.kampus.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(
    onStudentLoginAttempt: (String, String) -> Unit,
    onFacultyLoginAttempt: (String, String) -> Unit,
    onSuperAdminLoginAttempt: (String, String) -> Unit,
    onStudentRegister: () -> Unit,
    onFacultyRegister: () -> Unit
) {
    var selectedRoleIndex by remember { mutableIntStateOf(0) } // 0: Student, 1: Faculty, 2: Super Admin
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1E3A8A), Color(0xFF2563EB), Color(0xFFF8FAFC))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Brand Header with Glowing Icon
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(90.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(70.dp),
                        shadowElevation = 8.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🎓", fontSize = 34.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Kampus Hub",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "Connect • Learn • Compete",
                fontSize = 13.sp,
                color = Color(0xFFDBEAFE),
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Main Auth Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(26.dp)),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Segmented Role Selector
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val roles = listOf("Student", "Faculty", "Admin")
                            roles.forEachIndexed { index, roleName ->
                                val isSelected = selectedRoleIndex == index
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .clickable {
                                            selectedRoleIndex = index
                                            email = ""
                                            password = ""
                                        },
                                    color = if (isSelected) Color(0xFF2563EB) else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp),
                                    shadowElevation = if (isSelected) 3.dp else 0.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = roleName,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp,
                                            color = if (isSelected) Color.White else Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    val emailLabel = when (selectedRoleIndex) {
                        0 -> "Student Email"
                        1 -> "Faculty / Staff Email"
                        else -> "Super Admin Email"
                    }
                    val emailHint = when (selectedRoleIndex) {
                        0 -> "student@college.ac.in"
                        1 -> "faculty@college.edu.in"
                        else -> "admin@kampus.com"
                    }

                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(emailLabel) },
                        placeholder = { Text(emailHint, fontSize = 12.sp, color = Color.LightGray) },
                        leadingIcon = { Text("✉️", fontSize = 16.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2563EB),
                            unfocusedBorderColor = Color(0xFFE2E8F0)
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        placeholder = { Text("••••••••", fontSize = 14.sp) },
                        leadingIcon = { Text("🔒", fontSize = 16.sp) },
                        trailingIcon = {
                            Text(
                                text = if (isPasswordVisible) "👁️" else "🙈",
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .clickable { isPasswordVisible = !isPasswordVisible }
                                    .padding(8.dp)
                            )
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF2563EB),
                            unfocusedBorderColor = Color(0xFFE2E8F0)
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action Button
                    Button(
                        onClick = {
                            if (email.isBlank() || password.isBlank()) {
                                Toast.makeText(context, "Please enter email & password", Toast.LENGTH_SHORT).show()
                            } else {
                                when (selectedRoleIndex) {
                                    0 -> onStudentLoginAttempt(email.trim(), password.trim())
                                    1 -> onFacultyLoginAttempt(email.trim(), password.trim())
                                    2 -> onSuperAdminLoginAttempt(email.trim(), password.trim())
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        elevation = ButtonDefaults.buttonElevation(4.dp)
                    ) {
                        Text("Log In ➔", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bottom Register Links
                    when (selectedRoleIndex) {
                        0 -> {
                            Row(
                                modifier = Modifier.clickable { onStudentRegister() },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("New student? ", color = Color(0xFF64748B), fontSize = 13.sp)
                                Text("Create Profile ➔", color = Color(0xFF2563EB), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        1 -> {
                            OutlinedButton(
                                onClick = onFacultyRegister,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                            ) {
                                Text("📝 Faculty Registration / KYC ➔", color = Color(0xFF0F172A), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                        2 -> {
                            Text("Kampus Master Governance Portal", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}