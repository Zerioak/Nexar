package com.nexar.assistant.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexar.assistant.ai.live.NexarSessionState
import com.nexar.assistant.assistant.agent.ConversationMessage
import com.nexar.assistant.assistant.agent.MessageRole
import com.nexar.assistant.ui.components.NexarOrb
import com.nexar.assistant.ui.theme.NexarCyan
import com.nexar.assistant.ui.theme.NexarError
import com.nexar.assistant.ui.theme.NexarSuccess
import com.nexar.assistant.ui.theme.NexarSurface
import com.nexar.assistant.ui.theme.NexarSurfaceVariant
import com.nexar.assistant.ui.theme.NexarWarning
import com.nexar.assistant.ui.viewmodel.ConfirmationRequest
import com.nexar.assistant.ui.viewmodel.NexarViewModel
import kotlinx.coroutines.launch

@Composable
fun ConversationScreen(
    viewModel: NexarViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToMemory: () -> Unit
) {
    val sessionState by viewModel.sessionState.collectAsState()
    val conversation by viewModel.conversation.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val textInput by viewModel.textInput.collectAsState()
    val isScreenSharing by viewModel.isScreenSharing.collectAsState()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(conversation.size) {
        if (conversation.isNotEmpty()) {
            listState.animateScrollToItem(conversation.size - 1)
        }
    }

    // Confirmation dialog
    pendingConfirmation?.let { req ->
        ConfirmationDialog(
            request = req,
            onConfirm = { viewModel.confirmPendingAction() },
            onDeny = { viewModel.denyPendingAction() }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF050A14)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Top bar
            NexarTopBar(
                sessionState = sessionState,
                isScreenSharing = isScreenSharing,
                onSettingsClick = onNavigateToSettings,
                onMemoryClick = onNavigateToMemory,
                onPermissionsClick = onNavigateToPermissions,
                onClearClick = { viewModel.clearConversation() },
                onStopScreenShare = { viewModel.stopScreenShare() }
            )

            // Orb section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                NexarOrb(
                    state = sessionState,
                    size = 180.dp,
                    onClick = {
                        when (sessionState) {
                            NexarSessionState.DISCONNECTED, NexarSessionState.ERROR ->
                                viewModel.connectToGemini()
                            NexarSessionState.CONNECTED ->
                                viewModel.startListening()
                            NexarSessionState.LISTENING ->
                                viewModel.stopListening()
                            NexarSessionState.SPEAKING ->
                                viewModel.interrupt()
                            else -> {}
                        }
                    }
                )

                // State label below orb
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = sessionStateLabel(sessionState),
                        color = sessionStateColor(sessionState),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Conversation list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(conversation) { message ->
                    MessageBubble(message = message)
                }
            }

            // Input row
            NexarInputRow(
                textInput = textInput,
                isListening = isListening,
                sessionState = sessionState,
                onTextChange = { viewModel.updateTextInput(it) },
                onSendText = { viewModel.sendTextMessage() },
                onToggleMic = {
                    if (sessionState == NexarSessionState.DISCONNECTED ||
                        sessionState == NexarSessionState.ERROR) {
                        viewModel.connectToGemini()
                    } else {
                        viewModel.toggleListening()
                    }
                }
            )
        }
    }
}

@Composable
private fun NexarTopBar(
    sessionState: NexarSessionState,
    isScreenSharing: Boolean,
    onSettingsClick: () -> Unit,
    onMemoryClick: () -> Unit,
    onPermissionsClick: () -> Unit,
    onClearClick: () -> Unit,
    onStopScreenShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // NEXAR title
        Text(
            text = "NEXAR",
            color = NexarCyan,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            modifier = Modifier.padding(start = 8.dp)
        )

        Spacer(Modifier.weight(1f))

        // Screen share indicator
        if (isScreenSharing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(NexarError.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(NexarError)
                )
                Spacer(Modifier.width(4.dp))
                Text("Sharing", color = NexarError, fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onStopScreenShare, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = NexarError, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(4.dp))
        }

        IconButton(onClick = onClearClick) {
            Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color(0xFF667788))
        }
        IconButton(onClick = onMemoryClick) {
            Icon(Icons.Default.Memory, contentDescription = "Memory", tint = Color(0xFF667788))
        }
        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color(0xFF667788))
        }
    }
}

@Composable
private fun NexarInputRow(
    textInput: String,
    isListening: Boolean,
    sessionState: NexarSessionState,
    onTextChange: (String) -> Unit,
    onSendText: () -> Unit,
    onToggleMic: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = textInput,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    text = when (sessionState) {
                        NexarSessionState.DISCONNECTED -> "Tap orb to connect…"
                        NexarSessionState.CONNECTING -> "Connecting…"
                        NexarSessionState.LISTENING -> "Listening… (or type)"
                        NexarSessionState.THINKING -> "NEXAR is thinking…"
                        NexarSessionState.SPEAKING -> "NEXAR is speaking…"
                        NexarSessionState.EXECUTING -> "Executing…"
                        NexarSessionState.ERROR -> "Error. Tap orb to retry."
                        else -> "Message NEXAR…"
                    },
                    color = Color(0xFF445566),
                    fontSize = 14.sp
                )
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
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSendText() }),
            maxLines = 3,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
        )

        Spacer(Modifier.width(8.dp))

        // Send button (only show when text present)
        AnimatedVisibility(visible = textInput.isNotBlank()) {
            IconButton(
                onClick = onSendText,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(NexarCyan)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFF002233))
            }
        }

        Spacer(Modifier.width(4.dp))

        // Mic button
        IconButton(
            onClick = onToggleMic,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isListening -> NexarSuccess.copy(alpha = 0.9f)
                        sessionState == NexarSessionState.DISCONNECTED -> Color(0xFF1A3040)
                        else -> NexarCyan.copy(alpha = 0.15f)
                    }
                )
                .border(
                    1.dp,
                    when {
                        isListening -> NexarSuccess
                        sessionState == NexarSessionState.DISCONNECTED -> Color(0xFF223344)
                        else -> NexarCyan.copy(alpha = 0.4f)
                    },
                    CircleShape
                )
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop listening" else "Start listening",
                tint = if (isListening) Color.White else NexarCyan,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ConversationMessage) {
    val isUser = message.role == MessageRole.USER
    val isSystem = message.role == MessageRole.SYSTEM
    val isTool = message.role == MessageRole.TOOL

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = when {
            isUser -> Arrangement.End
            isTool -> Arrangement.Start
            else -> Arrangement.Start
        }
    ) {
        if (isTool) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0A1E1A)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Text(
                    text = message.text,
                    color = NexarSuccess.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(8.dp),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isUser -> Color(0xFF0D2A50)
                        isSystem -> Color(0xFF0A1A10)
                        else -> NexarSurfaceVariant
                    }
                ),
                shape = RoundedCornerShape(
                    topStart = if (isUser) 18.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp
                ),
                modifier = Modifier.fillMaxWidth(if (isUser) 0.82f else 0.9f),
                border = if (!isUser) BorderStroke(
                    0.5.dp, NexarCyan.copy(alpha = 0.2f)
                ) else null
            ) {
                if (!isUser) {
                    Text(
                        text = if (isSystem) "NEXAR" else "NEXAR",
                        color = NexarCyan.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp)
                    )
                }
                Text(
                    text = message.text,
                    color = if (isUser) Color(0xFFD0E8FF)
                    else if (isSystem) NexarSuccess.copy(alpha = 0.9f)
                    else Color(0xFFE0F0FF),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = if (isUser) 10.dp else 4.dp,
                        bottom = 10.dp
                    )
                )
            }
        }
    }
}

@Composable
private fun ConfirmationDialog(
    request: ConfirmationRequest,
    onConfirm: () -> Unit,
    onDeny: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDeny,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = NexarWarning
            )
        },
        title = {
            Text("Confirm Action", color = Color(0xFFE0F0FF), fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                text = request.message,
                color = Color(0xFFB0CCDD),
                fontSize = 14.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Confirm", color = NexarCyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) {
                Text("Cancel", color = Color(0xFF667788))
            }
        },
        containerColor = NexarSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

private fun sessionStateLabel(state: NexarSessionState): String = when (state) {
    NexarSessionState.DISCONNECTED -> "Tap to connect"
    NexarSessionState.CONNECTING -> "Connecting…"
    NexarSessionState.CONNECTED -> "Ready"
    NexarSessionState.LISTENING -> "Listening…"
    NexarSessionState.THINKING -> "Thinking…"
    NexarSessionState.SPEAKING -> "Speaking…"
    NexarSessionState.EXECUTING -> "Executing…"
    NexarSessionState.ERROR -> "Connection error"
}

private fun sessionStateColor(state: NexarSessionState): Color = when (state) {
    NexarSessionState.DISCONNECTED -> Color(0xFF445566)
    NexarSessionState.CONNECTING -> Color(0xFF5577BB)
    NexarSessionState.CONNECTED -> NexarCyan
    NexarSessionState.LISTENING -> NexarSuccess
    NexarSessionState.THINKING -> Color(0xFF7799FF)
    NexarSessionState.SPEAKING -> NexarCyan
    NexarSessionState.EXECUTING -> NexarWarning
    NexarSessionState.ERROR -> NexarError
}
