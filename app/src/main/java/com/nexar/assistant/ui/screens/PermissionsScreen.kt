package com.nexar.assistant.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexar.assistant.permissions.PermissionHelper
import com.nexar.assistant.ui.theme.NexarCyan
import com.nexar.assistant.ui.theme.NexarError
import com.nexar.assistant.ui.theme.NexarSuccess
import com.nexar.assistant.ui.theme.NexarSurface
import com.nexar.assistant.ui.viewmodel.NexarViewModel

@Composable
fun PermissionsScreen(
    viewModel: NexarViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val accessibilityEnabled by viewModel.accessibilityEnabled.collectAsState()
    val overlayEnabled by viewModel.overlayEnabled.collectAsState()
    val notifListenerEnabled by viewModel.notifListenerEnabled.collectAsState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshPermissionStates() }

    // Refresh on resume
    LaunchedEffect(Unit) {
        viewModel.refreshPermissionStates()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050A14))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NexarCyan)
            }
            Text(
                text = "Permissions",
                color = NexarCyan,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "NEXAR needs these permissions to function fully. Each permission has a specific purpose and can be revoked at any time.",
                color = Color(0xFF667788),
                fontSize = 13.sp
            )

            Spacer(Modifier.height(4.dp))

            // Microphone
            val hasMic = PermissionHelper.hasPermission(context, Manifest.permission.RECORD_AUDIO)
            PermissionItem(
                icon = Icons.Default.PhoneAndroid,
                title = "Microphone",
                description = "Required for voice input and Gemini Live audio conversation.",
                isGranted = hasMic,
                isRequired = true,
                onGrant = {
                    micPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                }
            )

            // Contacts
            val hasContacts = PermissionHelper.hasPermission(context, Manifest.permission.READ_CONTACTS)
            PermissionItem(
                icon = Icons.Default.PhoneAndroid,
                title = "Contacts",
                description = "Allows NEXAR to find contacts for calls and messaging.",
                isGranted = hasContacts,
                onGrant = {
                    micPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                }
            )

            // Phone
            val hasPhone = PermissionHelper.hasPermission(context, Manifest.permission.CALL_PHONE)
            PermissionItem(
                icon = Icons.Default.PhoneAndroid,
                title = "Phone Calls",
                description = "Allows NEXAR to place phone calls on your behalf.",
                isGranted = hasPhone,
                onGrant = {
                    micPermissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE))
                }
            )

            // Overlay
            PermissionItem(
                icon = Icons.Default.ScreenShare,
                title = "Display Over Apps",
                description = "Required for the floating NEXAR orb to appear over other apps.",
                isGranted = overlayEnabled,
                requiresSystemSettings = true,
                onGrant = { PermissionHelper.openOverlayPermissionSettings(context) }
            )

            // Accessibility
            PermissionItem(
                icon = Icons.Default.AccessibilityNew,
                title = "Accessibility Service",
                description = "Allows NEXAR to read screen content and interact with other apps on your behalf (e.g., type text, tap buttons, scroll).",
                isGranted = accessibilityEnabled,
                requiresSystemSettings = true,
                onGrant = { PermissionHelper.openAccessibilitySettings(context) }
            )

            // Notification listener
            PermissionItem(
                icon = Icons.Default.NotificationsActive,
                title = "Notification Access",
                description = "Allows NEXAR to read your notifications and announce important ones.",
                isGranted = notifListenerEnabled,
                requiresSystemSettings = true,
                onGrant = { PermissionHelper.openNotificationListenerSettings(context) }
            )

            // Screen capture is on-demand, no static permission

            Spacer(Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1420)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, Color(0xFF1A2A3A))
            ) {
                Text(
                    text = "⚠️ NEXAR never uses permissions to bypass app security, access private data without consent, or perform actions you haven't requested. All interactions are user-initiated.",
                    color = Color(0xFF556677),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(12.dp),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    isRequired: Boolean = false,
    requiresSystemSettings: Boolean = false,
    onGrant: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) Color(0xFF0A1E14) else NexarSurface
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            0.5.dp,
            if (isGranted) NexarSuccess.copy(alpha = 0.3f) else Color(0xFF1A3040)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isGranted) NexarSuccess else Color(0xFF667788),
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        color = if (isGranted) Color(0xFFE0F0FF) else Color(0xFFAABBCC),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    if (isRequired) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Required",
                            color = NexarError.copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(NexarError.copy(alpha = 0.1f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(description, color = Color(0xFF556677), fontSize = 12.sp, lineHeight = 17.sp)

                if (!isGranted) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = onGrant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NexarCyan.copy(alpha = 0.1f))
                            .border(1.dp, NexarCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    ) {
                        if (requiresSystemSettings) {
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = NexarCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text("Grant Permission", color = NexarCyan, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.width(8.dp))
            Icon(
                if (isGranted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isGranted) NexarSuccess else Color(0xFF334455),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
