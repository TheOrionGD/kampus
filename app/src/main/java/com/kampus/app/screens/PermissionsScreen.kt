package com.kampus.app.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

data class AppPermissionItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: String,
    val permissionManifest: String?,
    val isSystemNormal: Boolean = false
)

@Composable
fun PermissionsScreen(
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // List of permissions required in Kampus
    val permissionsList = remember {
        listOf(
            AppPermissionItem(
                id = "notifications",
                title = "Push Notifications",
                subtitle = "Instant alerts for newly published hackathons, symposiums & peer messages",
                icon = "🔔",
                permissionManifest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null,
                isSystemNormal = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            ),
            AppPermissionItem(
                id = "sms",
                title = "SMS Communications",
                subtitle = "Send emergency campus alerts and team invitations directly to teammates",
                icon = "📱",
                permissionManifest = Manifest.permission.SEND_SMS,
                isSystemNormal = false
            ),
            AppPermissionItem(
                id = "alarms",
                title = "Exact Reminders & Deadlines",
                subtitle = "Trigger background alarms before event registrations and project submissions close",
                icon = "⏰",
                permissionManifest = null,
                isSystemNormal = true
            ),
            AppPermissionItem(
                id = "network",
                title = "Real-time Cloud Network",
                subtitle = "Direct SSL synchronization with MongoDB Atlas database over Wi-Fi & 4G/5G",
                icon = "🌐",
                permissionManifest = null,
                isSystemNormal = true
            )
        )
    }

    // Reactive state map tracking granted status for each permission item
    val permissionStatusMap = remember { mutableStateMapOf<String, Boolean>() }

    fun refreshPermissionStatuses() {
        for (item in permissionsList) {
            val isGranted = if (item.isSystemNormal || item.permissionManifest == null) {
                true
            } else {
                ContextCompat.checkSelfPermission(context, item.permissionManifest) == PackageManager.PERMISSION_GRANTED
            }
            permissionStatusMap[item.id] = isGranted
        }
    }

    // Check permissions immediately on composition
    val permissionsChecked = remember { mutableStateOf(false) }
    
    // Initial check and auto-refresh on lifecycle resume (e.g. returning from App Settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissionStatuses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (!permissionsChecked.value) {
            refreshPermissionStatuses()
            permissionsChecked.value = true
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Single permission launcher for individual card tap
    var targetPermissionId by remember { mutableStateOf<String?>(null) }
    val singlePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        targetPermissionId?.let { pid ->
            permissionStatusMap[pid] = isGranted
        }
        refreshPermissionStatuses()
    }

    // Multiple permissions launcher for the master "Grant All" button
    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        refreshPermissionStatuses()
    }

    val pendingRuntimePermissions = permissionsList.filter { item ->
        !item.isSystemNormal && item.permissionManifest != null && permissionStatusMap[item.id] != true
    }.mapNotNull { it.permissionManifest }

    val allGranted = permissionsList.all { permissionStatusMap[it.id] == true }

    // Auto-continue when all permissions are granted (avoid showing screen again)
    LaunchedEffect(allGranted) {
        if (allGranted) {
            onContinue()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F172A), Color(0xFF1E3A8A), Color(0xFFF8FAFC))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Brand Header Icon
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.15f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(62.dp),
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🛡️", fontSize = 28.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "App Permissions",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Grant permissions for seamless real-time notifications, event alerts & secure campus sync.",
                fontSize = 12.sp,
                color = Color(0xFFDBEAFE),
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Main Card Container
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(14.dp, RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Required Features & Access",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )

                    permissionsList.forEach { item ->
                        val isGranted = permissionStatusMap[item.id] == true

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isGranted && item.permissionManifest != null) {
                                        targetPermissionId = item.id
                                        singlePermissionLauncher.launch(item.permissionManifest)
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isGranted) Color(0xFFF0FDF4) else Color(0xFFF8FAFC),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isGranted) Color(0xFF86EFAC) else Color(0xFFE2E8F0)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isGranted) Color(0xFFDCFCE7) else Color(0xFFE2E8F0),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(item.icon, fontSize = 20.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.subtitle,
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B),
                                        lineHeight = 15.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                if (isGranted) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF22C55E)
                                    ) {
                                        Text(
                                            text = "✓ ACTIVE",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            if (item.permissionManifest != null) {
                                                targetPermissionId = item.id
                                                singlePermissionLauncher.launch(item.permissionManifest)
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("GRANT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Primary Action Button
                    Button(
                        onClick = {
                            if (allGranted) {
                                onContinue()
                            } else if (pendingRuntimePermissions.isNotEmpty()) {
                                multiplePermissionLauncher.launch(pendingRuntimePermissions.toTypedArray())
                            } else {
                                onContinue()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (allGranted) Color(0xFF16A34A) else Color(0xFF2563EB)
                        ),
                        elevation = ButtonDefaults.buttonElevation(4.dp)
                    ) {
                        Text(
                            text = if (allGranted) "All Permissions Granted • Continue ➔" else "Grant All Permissions ➔",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }

                    // Secondary Options & Settings Navigation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Open Settings > Apps > Kampus to configure permissions", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("⚙️ Open App Settings", fontSize = 12.sp, color = Color(0xFF64748B))
                        }

                        TextButton(
                            onClick = { onContinue() }
                        ) {
                            Text("Skip / Continue ➔", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
