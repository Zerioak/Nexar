package com.nexar.assistant.ai.live

import android.content.Context
import com.nexar.assistant.ai.tools.NexarToolDefinitions
import com.nexar.assistant.audio.NexarAudioManager
import com.nexar.assistant.settings.NexarSettingsRepository
import com.nexar.assistant.utils.NexarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

enum class NexarSessionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    LISTENING,
    THINKING,
    SPEAKING,
    EXECUTING,
    ERROR
}

data class NexarEvent(
    val type: EventType,
    val textContent: String? = null,
    val audioData: ByteArray? = null,
    val toolName: String? = null,
    val toolId: String? = null,
    val toolArgs: Map<String, Any>? = null,
    val errorMessage: String? = null
) {
    enum class EventType {
        ASSISTANT_TEXT,
        ASSISTANT_AUDIO,
        TOOL_CALL,
        SESSION_READY,
        SESSION_ENDED,
        ERROR,
        TURN_COMPLETE,
        INPUT_TRANSCRIPTION
    }
}

class NexarLiveSessionManager(
    private val context: Context,
    private val settingsRepository: NexarSettingsRepository,
    private val audioManager: NexarAudioManager
) {
    companion object {
        private const val TAG = "NexarLiveSession"
        private const val GEMINI_MODEL = "gemini-2.0-flash-live-001"
        private const val RECONNECT_DELAY_MS = 3000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val NEXAR_SYSTEM_PROMPT = """You are NEXAR, a personal AI assistant for Android, owned by Hasbi.

Personality:
- Friendly, calm, confident, natural, helpful, and human-like
- Concise when appropriate — never robotic
- Never reveal internal model details

Greeting: "Hello, I'm NEXAR, your personal AI assistant. How can I help you?"

Capabilities:
- Open apps, control device settings, flashlight, volume
- WhatsApp: message contacts, read chats
- YouTube: search, play, like, subscribe
- Contacts: call, find, read
- Screen inspection via accessibility tools
- Notifications: read and respond
- Memory: remember user preferences

Rules:
- Always confirm before sending messages, making calls, or performing account actions
- Never claim success if an action failed
- If you cannot do something, explain exactly why
- Support: English, Hindi, Hinglish, Urdu, and major Indian languages
- Use tools when the user requests actions

Tool use: When user requests actions, always use the appropriate tool. Report actual results."""

        // Live API endpoint for Gemini 2.0
        private const val LIVE_API_BASE = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isConnecting = AtomicBoolean(false)
    private val isClosed = AtomicBoolean(false)

    private val _sessionState = MutableStateFlow(NexarSessionState.DISCONNECTED)
    val sessionState: StateFlow<NexarSessionState> = _sessionState.asStateFlow()

    private val _events = MutableSharedFlow<NexarEvent>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<NexarEvent> = _events.asSharedFlow()

    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription.asStateFlow()

    private val _assistantText = MutableStateFlow("")
    val assistantText: StateFlow<String> = _assistantText.asStateFlow()

    private var webSocket: okhttp3.WebSocket? = null
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private var micStreamJob: Job? = null
    private var currentApiKey: String = ""

    private val conversationHistory = mutableListOf<Map<String, Any>>()

    fun connect(apiKey: String) {
        if (isClosed.get()) {
            NexarLogger.d(TAG, "Session closed, cannot connect")
            return
        }
        if (isConnecting.get()) {
            NexarLogger.d(TAG, "Already connecting")
            return
        }
        if (_sessionState.value == NexarSessionState.CONNECTED ||
            _sessionState.value == NexarSessionState.LISTENING) {
            NexarLogger.d(TAG, "Already connected")
            return
        }

        currentApiKey = apiKey
        reconnectAttempts = 0
        doConnect(apiKey)
    }

    private fun doConnect(apiKey: String) {
        if (apiKey.isBlank()) {
            _sessionState.value = NexarSessionState.ERROR
            emitEvent(NexarEvent(
                type = NexarEvent.EventType.ERROR,
                errorMessage = "API key not configured. Please add your Gemini API key in Settings."
            ))
            return
        }

        isConnecting.set(true)
        _sessionState.value = NexarSessionState.CONNECTING

        scope.launch {
            try {
                val url = "$LIVE_API_BASE?key=$apiKey"
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .build()

                val client = okhttp3.OkHttpClient.Builder()
                    .pingInterval(20, java.util.concurrent.TimeUnit.SECONDS)
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS) // No timeout for streaming
                    .build()

                val listener = NexarWebSocketListener()
                webSocket = client.newWebSocket(request, listener)

            } catch (e: Exception) {
                NexarLogger.e(TAG, "Connection failed", e)
                isConnecting.set(false)
                handleConnectionError(e.message ?: "Unknown error")
            }
        }
    }

    private fun sendSetupMessage() {
        val toolDeclarations = NexarToolDefinitions.getAllToolDeclarations()

        val setup = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", "models/$GEMINI_MODEL")
                put("generation_config", JSONObject().apply {
                    put("response_modalities", JSONArray().apply {
                        put("AUDIO")
                        put("TEXT")
                    })
                    put("speech_config", JSONObject().apply {
                        put("voice_config", JSONObject().apply {
                            put("prebuilt_voice_config", JSONObject().apply {
                                put("voice_name", "Aoede")
                            })
                        })
                    })
                })
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", NEXAR_SYSTEM_PROMPT)
                        })
                    })
                })
                if (toolDeclarations.isNotEmpty()) {
                    put("tools", JSONArray().apply {
                        put(JSONObject().apply {
                            put("function_declarations", toolDeclarations)
                        })
                    })
                }
            })
        }

        webSocket?.send(setup.toString())
        NexarLogger.d(TAG, "Setup message sent")
    }

    fun sendTextMessage(text: String) {
        if (_sessionState.value == NexarSessionState.DISCONNECTED ||
            _sessionState.value == NexarSessionState.ERROR) {
            NexarLogger.w(TAG, "Cannot send message - not connected")
            return
        }

        scope.launch {
            try {
                val message = JSONObject().apply {
                    put("client_content", JSONObject().apply {
                        put("turns", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("parts", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("text", text)
                                    })
                                })
                            })
                        })
                        put("turn_complete", true)
                    })
                }
                webSocket?.send(message.toString())
                _sessionState.value = NexarSessionState.THINKING
            } catch (e: Exception) {
                NexarLogger.e(TAG, "Error sending text message", e)
            }
        }
    }

    fun sendAudioChunk(audioData: ByteArray) {
        if (_sessionState.value != NexarSessionState.LISTENING &&
            _sessionState.value != NexarSessionState.CONNECTED) {
            return
        }

        scope.launch {
            try {
                val base64Audio = android.util.Base64.encodeToString(audioData, android.util.Base64.NO_WRAP)
                val message = JSONObject().apply {
                    put("realtime_input", JSONObject().apply {
                        put("media_chunks", JSONArray().apply {
                            put(JSONObject().apply {
                                put("mime_type", "audio/pcm;rate=16000")
                                put("data", base64Audio)
                            })
                        })
                    })
                }
                webSocket?.send(message.toString())
            } catch (e: Exception) {
                NexarLogger.e(TAG, "Error sending audio chunk", e)
            }
        }
    }

    fun sendToolResult(toolId: String, result: String, isError: Boolean = false) {
        scope.launch {
            try {
                val message = JSONObject().apply {
                    put("tool_response", JSONObject().apply {
                        put("function_responses", JSONArray().apply {
                            put(JSONObject().apply {
                                put("id", toolId)
                                put("name", toolId)
                                if (isError) {
                                    put("error", JSONObject().apply {
                                        put("message", result)
                                    })
                                } else {
                                    put("response", JSONObject().apply {
                                        put("output", result)
                                    })
                                }
                            })
                        })
                    })
                }
                webSocket?.send(message.toString())
                _sessionState.value = NexarSessionState.THINKING
            } catch (e: Exception) {
                NexarLogger.e(TAG, "Error sending tool result", e)
            }
        }
    }

    fun startListening() {
        if (_sessionState.value == NexarSessionState.CONNECTED) {
            _sessionState.value = NexarSessionState.LISTENING
            startMicStream()
        }
    }

    fun stopListening() {
        stopMicStream()
        if (_sessionState.value == NexarSessionState.LISTENING) {
            _sessionState.value = NexarSessionState.CONNECTED

            // Signal end of turn
            scope.launch {
                try {
                    val message = JSONObject().apply {
                        put("client_content", JSONObject().apply {
                            put("turn_complete", true)
                        })
                    }
                    webSocket?.send(message.toString())
                } catch (e: Exception) {
                    NexarLogger.e(TAG, "Error signaling turn complete", e)
                }
            }
        }
    }

    private fun startMicStream() {
        stopMicStream()
        micStreamJob = scope.launch {
            audioManager.startMicCapture { audioData ->
                sendAudioChunk(audioData)
            }
        }
    }

    private fun stopMicStream() {
        micStreamJob?.cancel()
        micStreamJob = null
        audioManager.stopMicCapture()
    }

    fun interrupt() {
        scope.launch {
            try {
                stopMicStream()
                audioManager.stopPlayback()
                val message = JSONObject().apply {
                    put("client_content", JSONObject().apply {
                        put("turns", JSONArray())
                        put("turn_complete", true)
                    })
                }
                webSocket?.send(message.toString())
                _sessionState.value = NexarSessionState.LISTENING
            } catch (e: Exception) {
                NexarLogger.e(TAG, "Error during interrupt", e)
            }
        }
    }

    fun disconnect() {
        stopMicStream()
        audioManager.stopPlayback()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _sessionState.value = NexarSessionState.DISCONNECTED
        reconnectJob?.cancel()
    }

    fun destroy() {
        isClosed.set(true)
        disconnect()
        scope.cancel()
    }

    private fun handleConnectionError(errorMsg: String) {
        _sessionState.value = NexarSessionState.ERROR
        emitEvent(NexarEvent(
            type = NexarEvent.EventType.ERROR,
            errorMessage = errorMsg
        ))
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (isClosed.get()) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val autoReconnect = settingsRepository.autoReconnectFlow.first()
            if (autoReconnect && reconnectAttempts < MAX_RECONNECT_ATTEMPTS && currentApiKey.isNotBlank()) {
                reconnectAttempts++
                NexarLogger.d(TAG, "Scheduling reconnect attempt $reconnectAttempts")
                delay(RECONNECT_DELAY_MS * reconnectAttempts)
                if (!isClosed.get()) {
                    doConnect(currentApiKey)
                }
            }
        }
    }

    private fun emitEvent(event: NexarEvent) {
        scope.launch {
            _events.emit(event)
        }
    }

    private inner class NexarWebSocketListener : okhttp3.WebSocketListener() {

        override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
            NexarLogger.d(TAG, "WebSocket opened")
            isConnecting.set(false)
            reconnectAttempts = 0
            sendSetupMessage()
        }

        override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
            parseServerMessage(text)
        }

        override fun onMessage(webSocket: okhttp3.WebSocket, bytes: okio.ByteString) {
            parseServerMessage(bytes.utf8())
        }

        override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
            NexarLogger.d(TAG, "WebSocket closing: $code $reason")
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
            NexarLogger.d(TAG, "WebSocket closed: $code $reason")
            isConnecting.set(false)
            _sessionState.value = NexarSessionState.DISCONNECTED
            emitEvent(NexarEvent(type = NexarEvent.EventType.SESSION_ENDED))
            if (!isClosed.get() && code != 1000) {
                scheduleReconnect()
            }
        }

        override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
            NexarLogger.e(TAG, "WebSocket failure", t)
            isConnecting.set(false)
            val errMsg = when {
                t.message?.contains("401") == true -> "Invalid API key. Please check your Gemini API key."
                t.message?.contains("403") == true -> "Access denied. Ensure your API key has Live API permissions."
                t.message?.contains("404") == true -> "Model not found. The Gemini Live API may not be available."
                t.message?.contains("Unable to resolve host") == true -> "No internet connection."
                else -> t.message ?: "Connection failed"
            }
            handleConnectionError(errMsg)
        }
    }

    private fun parseServerMessage(text: String) {
        try {
            val json = JSONObject(text)

            when {
                json.has("setupComplete") -> {
                    NexarLogger.d(TAG, "Setup complete")
                    _sessionState.value = NexarSessionState.CONNECTED
                    emitEvent(NexarEvent(type = NexarEvent.EventType.SESSION_READY))
                }

                json.has("serverContent") -> {
                    val serverContent = json.getJSONObject("serverContent")
                    parseServerContent(serverContent)
                }

                json.has("toolCall") -> {
                    val toolCall = json.getJSONObject("toolCall")
                    parseToolCall(toolCall)
                }

                json.has("error") -> {
                    val error = json.getJSONObject("error")
                    val errMsg = error.optString("message", "Unknown server error")
                    NexarLogger.e(TAG, "Server error: $errMsg")
                    _sessionState.value = NexarSessionState.ERROR
                    emitEvent(NexarEvent(
                        type = NexarEvent.EventType.ERROR,
                        errorMessage = errMsg
                    ))
                }
            }

        } catch (e: Exception) {
            NexarLogger.e(TAG, "Error parsing server message", e)
        }
    }

    private fun parseServerContent(serverContent: JSONObject) {
        val modelTurn = serverContent.optJSONObject("modelTurn")
        val turnComplete = serverContent.optBoolean("turnComplete", false)
        val interrupted = serverContent.optBoolean("interrupted", false)

        if (interrupted) {
            NexarLogger.d(TAG, "Turn interrupted")
            audioManager.stopPlayback()
            _sessionState.value = NexarSessionState.LISTENING
            return
        }

        modelTurn?.let { turn ->
            val parts = turn.optJSONArray("parts")
            parts?.let { partsArray ->
                for (i in 0 until partsArray.length()) {
                    val part = partsArray.getJSONObject(i)
                    when {
                        part.has("text") -> {
                            val text = part.getString("text")
                            _assistantText.value = (_assistantText.value + text)
                            emitEvent(NexarEvent(
                                type = NexarEvent.EventType.ASSISTANT_TEXT,
                                textContent = text
                            ))
                            _sessionState.value = NexarSessionState.SPEAKING
                        }
                        part.has("inlineData") -> {
                            val inlineData = part.getJSONObject("inlineData")
                            val mimeType = inlineData.optString("mimeType", "")
                            val data = inlineData.optString("data", "")
                            if (mimeType.startsWith("audio/") && data.isNotEmpty()) {
                                val audioBytes = android.util.Base64.decode(data, android.util.Base64.DEFAULT)
                                _sessionState.value = NexarSessionState.SPEAKING
                                audioManager.playAudio(audioBytes, mimeType)
                                emitEvent(NexarEvent(
                                    type = NexarEvent.EventType.ASSISTANT_AUDIO,
                                    audioData = audioBytes
                                ))
                            }
                        }
                    }
                }
            }
        }

        if (turnComplete) {
            NexarLogger.d(TAG, "Turn complete")
            emitEvent(NexarEvent(type = NexarEvent.EventType.TURN_COMPLETE))
            _sessionState.value = NexarSessionState.CONNECTED
            _assistantText.value = ""
        }
    }

    private fun parseToolCall(toolCallJson: JSONObject) {
        try {
            val functionCalls = toolCallJson.optJSONArray("functionCalls")
            functionCalls?.let { calls ->
                for (i in 0 until calls.length()) {
                    val call = calls.getJSONObject(i)
                    val toolName = call.optString("name", "")
                    val toolId = call.optString("id", toolName)
                    val argsJson = call.optJSONObject("args")

                    val args = mutableMapOf<String, Any>()
                    argsJson?.keys()?.forEach { key ->
                        args[key] = argsJson.get(key)
                    }

                    NexarLogger.d(TAG, "Tool call received: $toolName with args: $args")
                    _sessionState.value = NexarSessionState.EXECUTING

                    emitEvent(NexarEvent(
                        type = NexarEvent.EventType.TOOL_CALL,
                        toolName = toolName,
                        toolId = toolId,
                        toolArgs = args
                    ))
                }
            }
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Error parsing tool call", e)
        }
    }
}
