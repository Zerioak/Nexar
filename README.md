# NEXAR — Android AI Assistant

**Owner:** Hasbi  
**App ID:** `com.nexar.assistant`  
**Version:** 1.0.0  
**Min SDK:** 26 (Android 8.0)  
**Target SDK:** 35 (Android 15)

---

## What is NEXAR?

NEXAR is a real Android AI assistant powered by Google Gemini Live. It uses the device microphone for voice input, plays AI responses through the device speaker using Gemini's Aoede voice, and can interact with other Android apps through the Accessibility Service.

---

## Build Instructions

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- Java 17 JDK
- Android device or emulator running Android 8.0+
- Gemini API key with Live API access (from https://aistudio.google.com)

### Steps

1. **Open in Android Studio:**
   ```
   File → Open → select the `nexar/` folder
   ```

2. **Get gradle-wrapper.jar:**
   Android Studio will automatically download the Gradle wrapper on first open. Alternatively, copy `gradle-wrapper.jar` from any existing Android project into `gradle/wrapper/`.

3. **Build:**
   ```bash
   ./gradlew assembleDebug
   ```
   Or use Android Studio: `Build → Make Project`

4. **Install:**
   ```bash
   ./gradlew installDebug
   # OR
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

---

## Setup After Installation

### 1. Enter your Gemini API Key
- Open NEXAR
- Tap the **Settings** gear icon (top right)
- Enter your Gemini API key in the **AI Configuration** section
- Tap **Save Key**
- Tap **Test Connection** to verify

Get your API key at: https://aistudio.google.com/app/apikey  
_(Ensure the key has Gemini Live API access enabled)_

### 2. Grant Permissions
Tap the **Permissions** screen and grant:

| Permission | Required For |
|---|---|
| Microphone | Voice input to Gemini Live |
| Contacts | Calling and messaging contacts |
| Phone | Making phone calls |
| Display Over Apps | Floating NEXAR orb |
| Accessibility Service | Screen reading and UI interaction |
| Notification Access | Reading and announcing notifications |

### 3. Connect and Talk
- Return to the main screen
- Tap the **NEXAR orb** to connect to Gemini
- Tap the **microphone button** or tap the orb to start listening
- Say anything — NEXAR responds in Aoede's voice

---

## Features

### Voice Conversation
- Real-time bidirectional audio with Gemini Live
- Aoede voice (Gemini's native TTS)
- Supports interruption mid-response
- Auto-reconnect on network loss

### App Control
Say things like:
- *"Open WhatsApp"*
- *"Message Rahul on WhatsApp: I'll call you later"*
- *"Open YouTube and search for Minecraft"*
- *"Open Settings"*
- *"Turn on flashlight"*
- *"Volume up"*
- *"Call Mom"*

### Screen Interaction (requires Accessibility)
- *"What's on my screen?"*
- *"Tap the search button"*
- *"Type hello"*
- *"Scroll down"*
- *"Go back"*

### Memory
- *"Remember that my favorite game is Minecraft"*
- *"What do you know about me?"*

### Notifications
- *"Read my latest notification"*
- *"Open the WhatsApp notification"*

### Floating Orb
- Enable in Settings → Floating Orb toggle
- Drag anywhere on screen
- Tap to open NEXAR
- Long press to hide

---

## Architecture

```
User Voice
  → AudioRecord (16kHz PCM mono)
  → WebSocket → Gemini Live (gemini-2.0-flash-live-001)
  → Tool calls → NexarToolExecutor
                  → Android APIs
                  → AccessibilityService
                  → Contacts/Telecom/Camera/Audio
  → Audio response (24kHz PCM)
  → AudioTrack → Speaker/Headphones (Aoede voice)
```

### Key Components

| Component | File |
|---|---|
| Gemini Live Session | `ai/live/NexarLiveSessionManager.kt` |
| Tool Execution | `ai/tools/NexarToolExecutor.kt` |
| Tool Definitions | `ai/tools/NexarToolDefinitions.kt` |
| Agent Loop | `assistant/agent/NexarAgentController.kt` |
| Accessibility | `accessibility/NexarAccessibilityService.kt` |
| Audio | `audio/NexarAudioManager.kt` |
| Memory (Room DB) | `memory/NexarMemoryRepository.kt` |
| Floating Orb | `service/NexarOrbWindowManager.kt` |
| Screen Capture | `screen/capture/NexarScreenCaptureManager.kt` |
| Notifications | `notifications/NexarNotificationListenerService.kt` |
| Settings (DataStore) | `settings/NexarSettingsRepository.kt` |
| Main ViewModel | `ui/viewmodel/NexarViewModel.kt` |

---

## Security

- API key is stored in Android DataStore (encrypted on API 23+), never logged
- No hardcoded secrets anywhere in the source
- All tool executions are gated through the registered tool system — Gemini cannot execute arbitrary code
- Sensitive actions (send message, make call) require explicit user confirmation
- Accessibility service only activates for user-requested actions
- Memory storage blocks keys containing: password, api_key, token, secret, credential, pin, bank

---

## Supported Languages

English, Hindi, Hinglish, Urdu, Marathi, Gujarati, Bengali, Tamil, Telugu, Kannada, Malayalam, Punjabi (via Gemini's multilingual capabilities)

---

## Troubleshooting

**"API key not configured"**  
→ Go to Settings and add your Gemini API key

**"Invalid API key"**  
→ Check that your key is correct and has Gemini Live API access enabled

**"Accessibility Service not available"**  
→ Go to Permissions → Grant Accessibility Service → find "NEXAR Accessibility" in the list and enable it

**Orb not showing**  
→ Grant "Display Over Apps" permission first, then enable the Floating Orb toggle in Settings

**No audio response**  
→ Check that media volume is not muted; NEXAR plays audio on the media stream

**WhatsApp message not sending automatically**  
→ Enable Accessibility Service to allow NEXAR to interact with WhatsApp's UI

---

## License

Built for personal use by Hasbi. Powered by Google Gemini.
