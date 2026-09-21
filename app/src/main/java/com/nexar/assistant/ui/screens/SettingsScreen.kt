package com.nexar.assistant.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexar.assistant.ai.live.NexarSessionState
import com.nexar.assistant.ui.theme.NexarCyan
import com.nexar.assistant.ui.theme.NexarError
import com.nexar.assistant.ui.theme.NexarSuccess
import com.nexar.assistant.ui.theme.NexarSurface
import com.nexar.assistant.ui.theme.NexarSurfaceVariant
import com.nexar.assistant.ui.viewmodel.NexarViewModel

@Composable
fun SettingsScreen(
    viewModel: NexarViewModel,
    onBack: () -> Unit,
    onNavigateToPermissions: () -> Unit
) {
    val apiKey by viewModel.apiKey.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val floatingOrbEnabled by viewModel.floatingOrbEnabled.collectAsState()
    val userName by viewModel.userName.collectAsState()

    var apiKeyInput by remember(apiKey) { mutableStateOf(apiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var userNameInput by remember(userName) { mutableStateOf(userName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050A14))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top bar
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
                text = "Settings",
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // API Key section
            SettingsCard(title = "AI Configuration", icon = Icons.Default.Key) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Gemini API Key",
                        color = Color(0xFF8AABCC),
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Required for NEXAR to function. Get your key at aistudio.google.com",
                        color = Color(0xFF445566),
                        fontSize = 11.sp
                    )
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Key", color = Color(0xFF556677)) },
                        visualTransformation = if (showApiKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = Color(0xFF556677)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NexarCyan.copy(alpha = 0.6f),
                            unfocusedBorderColor = Color(0xFF1A3040),
                            focusedTextColor = Color(0xFFE0F0FF),
                            unfocusedTextColor = Color(0xFFE0F0FF),
                            cursorColor = NexarCyan,
                            focusedContainerColor = NexarSurfaceVariant,
                            unfocusedContainerColor = NexarSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    // Connection status
                    val (statusIcon, statusColor, statusText) = when (sessionState) {
                        NexarSessionState.CONNECTED,
                        NexarSessionState.LISTENING,
                        NexarSessionState.THINKING,
                        NexarSessionState.SPEAKING,
                        NexarSessionState.EXECUTING -> Triple(
                            Icons.Default.CheckCircle, NexarSuccess, "Connected"
                        )
                        NexarSessionState.CONNECTING -> Triple(
                            Icons.Default.Link, NexarCyan, "Connecting…"
                        )
                        NexarSessionState.ERROR -> Triple(
                            Icons.Default.Error, NexarError, "Connection error"
                        )
                        else -> Triple(
                            Icons.Default.Link, Color(0xFF445566), "Not connected"
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(statusText, color = statusColor, fontSize = 13.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                viewModel.saveApiKey(apiKeyInput.trim())
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(NexarCyan.copy(alpha = 0.1f))
                                .border(1.dp, NexarCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        ) {
                            Text("Save Key", color = NexarCyan, fontWeight = FontWeight.Medium)
                        }

                        TextButton(
                            onClick = { viewModel.testApiConnection() },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0A1628))
                                .border(1.dp, Color(0xFF1A3040), RoundedCornerShape(8.dp))
                        ) {
                            Text("Test Connection", color = Color(0xFF667788))
                        }

                        if (apiKey.isNotBlank()) {
                            TextButton(onClick = { viewModel.deleteApiKey() }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = NexarError,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Delete", color = NexarError)
                            }
                        }
                    }
                }
            }

            // User settings
            SettingsCard(title = "Profile", icon = Icons.Default.Person) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = userNameInput,
                        onValueChange = { userNameInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Your Name", color = Color(0xFF556677)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NexarCyan.copy(alpha = 0.6f),
                            unfocusedBorderColor = Color(0xFF1A3040),
                            focusedTextColor = Color(0xFFE0F0FF),
                            unfocusedTextColor = Color(0xFFE0F0FF),
                            cursorColor = NexarCyan,
                            focusedContainerColor = NexarSurfaceVariant,
                            unfocusedContainerColor = NexarSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    TextButton(
                        onClick = { viewModel.setUserName(userNameInput) },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NexarCyan.copy(alpha = 0.1f))
                            .border(1.dp, NexarCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    ) {
                        Text("Save Name", color = NexarCyan)
                    }
                }
            }

            // Floating orb
            SettingsCard(title = "Floating Orb", icon = Icons.Default.Security) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsToggle(
                        label = "Show Floating Orb",
                        description = "Floating NEXAR orb visible over other apps (requires overlay permission)",
                        checked = floatingOrbEnabled,
                        onCheckedChange = { viewModel.setFloatingOrbEnabled(it) }
                    )
                }
            }

            // Permissions shortcut
            TextButton(
                onClick = onNavigateToPermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NexarSurface)
                    .border(1.dp, Color(0xFF1A3040), RoundedCornerShape(12.dp))
            ) {
                Text("Manage Permissions", color = NexarCyan, fontWeight = FontWeight.Medium)
            }

            // Version info
            Text(
                text = "NEXAR v1.0.0 · Powered by Gemini · Built for Hasbi",
                color = Color(0xFF334455),
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NexarSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.5.dp, Color(0xFF1A3040)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = NexarCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = NexarCyan, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.5.sp)
            }
            HorizontalDivider(color = Color(0xFF1A3040))
            content()
        }
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color(0xFFD0E0F0), fontSize = 14.sp)
            if (description != null) {
                Text(description, color = Color(0xFF556677), fontSize = 11.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NexarCyan,
                uncheckedThumbColor = Color(0xFF445566),
                uncheckedTrackColor = Color(0xFF1A2A3A)
            )
        )
    }
}
