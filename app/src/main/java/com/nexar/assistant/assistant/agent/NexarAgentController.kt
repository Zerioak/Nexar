package com.nexar.assistant.assistant.agent

import com.nexar.assistant.ai.live.NexarEvent
import com.nexar.assistant.ai.live.NexarLiveSessionManager
import com.nexar.assistant.ai.tools.NexarToolExecutor
import com.nexar.assistant.ai.tools.ToolResult
import com.nexar.assistant.ai.tools.ToolStatus
import com.nexar.assistant.utils.NexarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConversationMessage(
    val role: MessageRole,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageRole {
    USER, ASSISTANT, SYSTEM, TOOL
}

class NexarAgentController(
    private val liveSessionManager: NexarLiveSessionManager,
    private val toolExecutor: NexarToolExecutor
) {
    companion object {
        private const val TAG = "NexarAgent"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _conversation = MutableStateFlow<List<ConversationMessage>>(emptyList())
    val conversation: StateFlow<List<ConversationMessage>> = _conversation.asStateFlow()

    private val _agentStatus = MutableStateFlow<String>("")
    val agentStatus: StateFlow<String> = _agentStatus.asStateFlow()

    private val _uiActions = MutableSharedFlow<AgentUiAction>(extraBufferCapacity = 8)
    val uiActions: SharedFlow<AgentUiAction> = _uiActions.asSharedFlow()

    init {
        observeEvents()
    }

    private fun observeEvents() {
        scope.launch {
            liveSessionManager.events.collect { event ->
                handleEvent(event)
            }
        }
    }

    private suspend fun handleEvent(event: NexarEvent) {
        when (event.type) {
            NexarEvent.EventType.SESSION_READY -> {
                addMessage(ConversationMessage(
                    role = MessageRole.SYSTEM,
                    text = "Hello, I'm NEXAR, your personal AI assistant. How can I help you?"
                ))
            }

            NexarEvent.EventType.ASSISTANT_TEXT -> {
                val text = event.textContent ?: return
                updateAssistantMessage(text)
            }

            NexarEvent.EventType.TURN_COMPLETE -> {
                finalizeAssistantMessage()
            }

            NexarEvent.EventType.TOOL_CALL -> {
                val toolName = event.toolName ?: return
                val toolId = event.toolId ?: toolName
                val args = event.toolArgs ?: emptyMap()

                NexarLogger.d(TAG, "Agent executing tool: $toolName")
                _agentStatus.value = "Executing: $toolName"

                val result = toolExecutor.executeTool(toolName, args)
                NexarLogger.d(TAG, "Tool result: ${result.status} - ${result.message}")

                // Handle special UI actions
                when {
                    result.data["action"] == "START_SCREEN_SHARE" -> {
                        _uiActions.emit(AgentUiAction.StartScreenShare)
                    }
                    result.data["action"] == "STOP_SCREEN_SHARE" -> {
                        _uiActions.emit(AgentUiAction.StopScreenShare)
                    }
                    result.status == ToolStatus.REQUIRES_PERMISSION -> {
                        addMessage(ConversationMessage(
                            role = MessageRole.SYSTEM,
                            text = result.message
                        ))
                    }
                    result.status == ToolStatus.REQUIRES_CONFIRMATION -> {
                        _uiActions.emit(AgentUiAction.ShowConfirmation(
                            message = result.message,
                            toolName = toolName,
                            toolId = toolId,
                            args = args
                        ))
                        return // Don't send tool result yet - wait for confirmation
                    }
                }

                // Add tool result to conversation for tracking
                addMessage(ConversationMessage(
                    role = MessageRole.TOOL,
                    text = "[Tool: $toolName] ${result.status}: ${result.message}"
                ))

                // Send result back to Gemini
                liveSessionManager.sendToolResult(
                    toolId = toolId,
                    result = result.toJson(),
                    isError = result.status == ToolStatus.FAILED
                )
                _agentStatus.value = ""
            }

            NexarEvent.EventType.ERROR -> {
                val errMsg = event.errorMessage ?: "Unknown error"
                addMessage(ConversationMessage(
                    role = MessageRole.SYSTEM,
                    text = "⚠️ $errMsg"
                ))
            }

            else -> {}
        }
    }

    fun sendConfirmedToolResult(toolName: String, toolId: String, args: Map<String, Any>) {
        scope.launch {
            val confirmedArgs = args.toMutableMap().apply {
                put("confirmed", true)
            }
            val result = toolExecutor.executeTool(toolName, confirmedArgs)
            addMessage(ConversationMessage(
                role = MessageRole.TOOL,
                text = "[Confirmed: $toolName] ${result.status}: ${result.message}"
            ))
            liveSessionManager.sendToolResult(
                toolId = toolId,
                result = result.toJson(),
                isError = result.status == ToolStatus.FAILED
            )
        }
    }

    fun sendDeniedToolResult(toolId: String, reason: String) {
        scope.launch {
            liveSessionManager.sendToolResult(
                toolId = toolId,
                result = "{\"status\":\"CANCELLED\",\"message\":\"User cancelled: $reason\"}",
                isError = false
            )
        }
    }

    private var currentAssistantText = StringBuilder()
    private var assistantMessageAdded = false

    private fun updateAssistantMessage(text: String) {
        currentAssistantText.append(text)
        if (!assistantMessageAdded) {
            addMessage(ConversationMessage(
                role = MessageRole.ASSISTANT,
                text = currentAssistantText.toString()
            ))
            assistantMessageAdded = true
        } else {
            updateLastAssistantMessage(currentAssistantText.toString())
        }
    }

    private fun updateLastAssistantMessage(text: String) {
        val current = _conversation.value.toMutableList()
        val lastIndex = current.indexOfLast { it.role == MessageRole.ASSISTANT }
        if (lastIndex >= 0) {
            current[lastIndex] = current[lastIndex].copy(text = text)
            _conversation.value = current
        }
    }

    private fun finalizeAssistantMessage() {
        currentAssistantText.clear()
        assistantMessageAdded = false
    }

    private fun addMessage(message: ConversationMessage) {
        val current = _conversation.value.toMutableList()
        current.add(message)
        // Keep conversation reasonable size
        if (current.size > 200) {
            current.removeAt(0)
        }
        _conversation.value = current
    }

    fun addUserMessage(text: String) {
        addMessage(ConversationMessage(role = MessageRole.USER, text = text))
        liveSessionManager.sendTextMessage(text)
    }

    fun clearConversation() {
        _conversation.value = emptyList()
    }

    fun destroy() {
        scope.cancel()
    }
}

sealed class AgentUiAction {
    object StartScreenShare : AgentUiAction()
    object StopScreenShare : AgentUiAction()
    data class ShowConfirmation(
        val message: String,
        val toolName: String,
        val toolId: String,
        val args: Map<String, Any>
    ) : AgentUiAction()
    data class RequestPermission(val permission: String) : AgentUiAction()
}
