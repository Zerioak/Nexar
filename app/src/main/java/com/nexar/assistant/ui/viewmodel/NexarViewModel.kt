package com.nexar.assistant.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexar.assistant.NexarApplication
import com.nexar.assistant.ai.live.NexarSessionState
import com.nexar.assistant.assistant.agent.AgentUiAction
import com.nexar.assistant.assistant.agent.ConversationMessage
import com.nexar.assistant.memory.MemoryFact
import com.nexar.assistant.permissions.PermissionHelper
import com.nexar.assistant.service.NexarForegroundService
import com.nexar.assistant.service.ScreenCaptureForegroundService
import com.nexar.assistant.utils.NexarLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConfirmationRequest(
    val message: String,
    val toolName: String,
    val toolId: String,
    val args: Map<String, Any>
)

class NexarViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "NexarViewModel"
    private val container = (application as NexarApplication).container
    private val liveSession = container.liveSessionManager
    private val agentController = container.agentController
    private val settingsRepo = container.settingsRepository
    private val memoryRepo = container.memoryRepository

    // Session state
    val sessionState: StateFlow<NexarSessionState> = liveSession.sessionState
    val conversation: StateFlow<List<ConversationMessage>> = agentController.conversation
    val assistantText: StateFlow<String> = liveSession.assistantText

    // Settings
    val apiKey: StateFlow<String> = settingsRepo.apiKeyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val floatingOrbEnabled: StateFlow<Boolean> = settingsRepo.floatingOrbEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val userName: StateFlow<String> = settingsRepo.userNameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Hasbi")

    // Memory
    val memories: StateFlow<List<MemoryFact>> = memoryRepo.allFacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI state
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _textInput = MutableStateFlow("")
    val textInput: StateFlow<String> = _textInput.asStateFlow()

    private val _isScreenSharing = MutableStateFlow(false)
    val isScreenSharing: StateFlow<Boolean> = _isScreenSharing.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<ConfirmationRequest?>(null)
    val pendingConfirmation: StateFlow<ConfirmationRequest?> = _pendingConfirmation.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private val _requestScreenShare = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val requestScreenShare: SharedFlow<Intent> = _requestScreenShare.asSharedFlow()

    // Permission states
    private val _accessibilityEnabled = MutableStateFlow(false)
    val accessibilityEnabled: StateFlow<Boolean> = _accessibilityEnabled.asStateFlow()

    private val _overlayEnabled = MutableStateFlow(false)
    val overlayEnabled: StateFlow<Boolean> = _overlayEnabled.asStateFlow()

    private val _notifListenerEnabled = MutableStateFlow(false)
    val notifListenerEnabled: StateFlow<Boolean> = _notifListenerEnabled.asStateFlow()

    init {
        observeAgentActions()
        refreshPermissionStates()
    }

    private fun observeAgentActions() {
        viewModelScope.launch {
            agentController.uiActions.collect { action ->
                handleAgentUiAction(action)
            }
        }
    }

    private suspend fun handleAgentUiAction(action: AgentUiAction) {
        when (action) {
            is AgentUiAction.StartScreenShare -> {
                initiateScreenShare()
            }
            is AgentUiAction.StopScreenShare -> {
                stopScreenShare()
            }
            is AgentUiAction.ShowConfirmation -> {
                _pendingConfirmation.value = ConfirmationRequest(
                    message = action.message,
                    toolName = action.toolName,
                    toolId = action.toolId,
                    args = action.args
                )
            }
            is AgentUiAction.RequestPermission -> {
                emitSnackbar("Permission needed: ${action.permission}")
            }
        }
    }

    fun connectToGemini() {
        viewModelScope.launch {
            val key = settingsRepo.apiKeyFlow.first()
            if (key.isBlank()) {
                emitSnackbar("Please add your Gemini API key in Settings")
                return@launch
            }
            liveSession.connect(key)
        }
    }

    fun disconnect() {
        liveSession.disconnect()
        _isListening.value = false
    }

    fun startListening() {
        if (sessionState.value != NexarSessionState.CONNECTED &&
            sessionState.value != NexarSessionState.LISTENING) {
            emitSnackbar("Not connected. Tap the connect button first.")
            return
        }
        _isListening.value = true
        liveSession.startListening()
    }

    fun stopListening() {
        _isListening.value = false
        liveSession.stopListening()
    }

    fun toggleListening() {
        if (_isListening.value) stopListening() else startListening()
    }

    fun sendTextMessage() {
        val text = _textInput.value.trim()
        if (text.isBlank()) return
        _textInput.value = ""
        agentController.addUserMessage(text)
    }

    fun updateTextInput(text: String) {
        _textInput.value = text
    }

    fun interrupt() {
        liveSession.interrupt()
        _isListening.value = false
    }

    fun clearConversation() {
        agentController.clearConversation()
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            settingsRepo.saveApiKey(key)
        }
    }

    fun deleteApiKey() {
        viewModelScope.launch {
            settingsRepo.saveApiKey("")
            liveSession.disconnect()
            emitSnackbar("API key deleted")
        }
    }

    fun testApiConnection() {
        connectToGemini()
    }

    fun setFloatingOrbEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setFloatingOrbEnabled(enabled)
            val app = getApplication<NexarApplication>()
            val intent = Intent(app, NexarForegroundService::class.java).apply {
                action = if (enabled) NexarForegroundService.ACTION_SHOW_ORB
                else NexarForegroundService.ACTION_HIDE_ORB
            }
            app.startService(intent)
        }
    }

    fun setUserName(name: String) {
        viewModelScope.launch {
            settingsRepo.setUserName(name)
        }
    }

    fun setAutoReconnect(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setAutoReconnect(enabled)
        }
    }

    fun confirmPendingAction() {
        val request = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        agentController.sendConfirmedToolResult(
            toolName = request.toolName,
            toolId = request.toolId,
            args = request.args
        )
    }

    fun denyPendingAction() {
        val request = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        agentController.sendDeniedToolResult(
            toolId = request.toolId,
            reason = "User declined the action"
        )
    }

    fun deleteMemory(fact: MemoryFact) {
        viewModelScope.launch {
            memoryRepo.deleteFact(fact)
        }
    }

    fun clearAllMemory() {
        viewModelScope.launch {
            memoryRepo.clearAll()
            emitSnackbar("All memories cleared")
        }
    }

    fun startForegroundService() {
        val app = getApplication<NexarApplication>()
        val intent = Intent(app, NexarForegroundService::class.java).apply {
            action = NexarForegroundService.ACTION_START
        }
        app.startForegroundService(intent)
    }

    fun refreshPermissionStates() {
        val ctx = getApplication<NexarApplication>()
        _accessibilityEnabled.value = PermissionHelper.hasAccessibilityPermission(ctx)
        _overlayEnabled.value = PermissionHelper.hasOverlayPermission(ctx)
        _notifListenerEnabled.value = PermissionHelper.hasNotificationListenerPermission(ctx)
    }

    private fun initiateScreenShare() {
        viewModelScope.launch {
            try {
                val app = getApplication<NexarApplication>()
                val projMgr = app.getSystemService(MediaProjectionManager::class.java)
                val captureIntent = projMgr.createScreenCaptureIntent()
                _requestScreenShare.tryEmit(captureIntent)
            } catch (e: Exception) {
                NexarLogger.e(TAG, "Error initiating screen share", e)
                emitSnackbar("Screen share not available")
            }
        }
    }

    fun onScreenShareGranted(resultCode: Int, data: Intent) {
        val app = getApplication<NexarApplication>()
        // Start the dedicated mediaProjection foreground service with the granted token.
        // The service calls startForeground(FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) before
        // any MediaProjection API, satisfying Android 10-14 requirements.
        val serviceIntent = ScreenCaptureForegroundService.buildStartIntent(app, resultCode, data)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(serviceIntent)
        } else {
            app.startService(serviceIntent)
        }
        _isScreenSharing.value = true
        emitSnackbar("Screen sharing started")
    }

    fun stopScreenShare() {
        val app = getApplication<NexarApplication>()
        val stopIntent = ScreenCaptureForegroundService.buildStopIntent(app)
        app.startService(stopIntent)   // service handles stop internally and calls stopSelf()
        _isScreenSharing.value = false
    }

    private fun emitSnackbar(message: String) {
        viewModelScope.launch { _snackbarMessage.emit(message) }
    }

    override fun onCleared() {
        super.onCleared()
        // Screen capture is managed by ScreenCaptureForegroundService; no cleanup needed here.
    }
}
