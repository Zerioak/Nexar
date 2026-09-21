package com.nexar.assistant.ai.tools

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.nexar.assistant.accessibility.NexarAccessibilityService
import com.nexar.assistant.memory.NexarMemoryRepository
import com.nexar.assistant.notifications.NexarNotificationRepository
import com.nexar.assistant.utils.NexarLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NexarToolExecutor(private val context: Context) {

    companion object {
        private const val TAG = "NexarToolExecutor"
    }

    private val memoryRepository = NexarMemoryRepository(context)
    private val notificationRepository = NexarNotificationRepository()

    private var torchManager: CameraManager? = null
    private var torchCameraId: String? = null
    private var torchState = false

    init {
        initTorch()
    }

    private fun initTorch() {
        try {
            torchManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            torchManager?.cameraIdList?.forEach { id ->
                val chars = torchManager?.getCameraCharacteristics(id)
                if (chars?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        torchCameraId = id
                        return@forEach
                    }
                }
            }
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Torch init failed", e)
        }
    }

    suspend fun executeTool(toolName: String, args: Map<String, Any>): ToolResult =
        withContext(Dispatchers.IO) {
            NexarLogger.d(TAG, "Executing tool: $toolName with args: $args")
            try {
                when (toolName) {
                    "openApp" -> withContext(Dispatchers.Main) { executeOpenApp(args) }
                    "openUrl" -> withContext(Dispatchers.Main) { executeOpenUrl(args) }
                    "openSettings" -> withContext(Dispatchers.Main) { executeOpenSettings(args) }
                    "inspectScreen" -> executeInspectScreen()
                    "tapElement" -> executeTapElement(args)
                    "longPressElement" -> executeLongPressElement(args)
                    "typeText" -> executeTypeText(args)
                    "clearText" -> executeClearText()
                    "scroll" -> executeScroll(args)
                    "pressBack" -> executePressBack()
                    "openRecentApps" -> executeOpenRecentApps()
                    "makePhoneCall" -> withContext(Dispatchers.Main) { executeMakePhoneCall(args) }
                    "findContact" -> executeFindContact(args)
                    "sendWhatsAppMessage" -> withContext(Dispatchers.Main) { executeSendWhatsAppMessage(args) }
                    "getNotifications" -> executeGetNotifications(args)
                    "openNotification" -> executeOpenNotification(args)
                    "toggleFlashlight" -> executeToggleFlashlight(args)
                    "controlVolume" -> executeControlVolume(args)
                    "getDeviceInfo" -> executeGetDeviceInfo(args)
                    "rememberFact" -> executeRememberFact(args)
                    "recallFact" -> executeRecallFact(args)
                    "searchYouTube" -> withContext(Dispatchers.Main) { executeSearchYouTube(args) }
                    "confirmAction" -> executeConfirmAction(args)
                    "startScreenShare" -> executeStartScreenShare()
                    "stopScreenShare" -> executeStopScreenShare()
                    else -> ToolResult.unavailable("Unknown tool: $toolName")
                }
            } catch (e: Exception) {
                NexarLogger.e(TAG, "Tool execution error for $toolName", e)
                ToolResult.failed("Tool error: ${e.message}")
            }
        }

    private fun executeOpenApp(args: Map<String, Any>): ToolResult {
        val appName = args["appName"]?.toString() ?: return ToolResult.failed("App name required")

        val pm = context.packageManager
        val packageName = resolveAppPackage(appName.lowercase().trim())

        return if (packageName != null) {
            try {
                val intent = pm.getLaunchIntentForPackage(packageName)
                    ?: return ToolResult.notFound("$appName is not installed")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                ToolResult.success("Opened $appName")
            } catch (e: Exception) {
                ToolResult.failed("Failed to open $appName: ${e.message}")
            }
        } else {
            // Try by app name search
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val activities = pm.queryIntentActivities(intent, 0)
            val match = activities.find {
                pm.getApplicationLabel(it.activityInfo.applicationInfo)
                    .toString().lowercase().contains(appName.lowercase())
            }
            if (match != null) {
                val launchIntent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    ToolResult.success("Opened ${pm.getApplicationLabel(match.activityInfo.applicationInfo)}")
                } else {
                    ToolResult.notFound("Cannot launch $appName")
                }
            } else {
                ToolResult.notFound("$appName is not installed on this device")
            }
        }
    }

    private fun resolveAppPackage(appName: String): String? {
        val appMap = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "camera" to "com.android.camera2",
            "settings" to "com.android.settings",
            "calendar" to "com.google.android.calendar",
            "clock" to "com.google.android.deskclock",
            "calculator" to "com.google.android.calculator",
            "photos" to "com.google.android.apps.photos",
            "google photos" to "com.google.android.apps.photos",
            "play store" to "com.android.vending",
            "google play" to "com.android.vending",
            "twitter" to "com.twitter.android",
            "instagram" to "com.instagram.android",
            "facebook" to "com.facebook.katana",
            "telegram" to "org.telegram.messenger",
            "spotify" to "com.spotify.music",
            "netflix" to "com.netflix.mediaclient",
            "amazon" to "com.amazon.mShop.android.shopping",
            "flipkart" to "com.flipkart.android",
            "phonepe" to "com.phonepe.app",
            "paytm" to "net.one97.paytm",
            "gpay" to "com.google.android.apps.nbu.paisa.user",
            "google pay" to "com.google.android.apps.nbu.paisa.user",
            "dialer" to "com.android.dialer",
            "phone" to "com.android.dialer",
            "contacts" to "com.google.android.contacts",
            "messages" to "com.google.android.apps.messaging",
            "files" to "com.google.android.apps.nbu.files",
            "drive" to "com.google.android.apps.docs",
            "google drive" to "com.google.android.apps.docs",
            "docs" to "com.google.android.apps.docs.editors.docs",
            "sheets" to "com.google.android.apps.docs.editors.sheets",
            "slides" to "com.google.android.apps.docs.editors.slides",
            "meet" to "com.google.android.apps.tachyon",
            "google meet" to "com.google.android.apps.tachyon",
            "zoom" to "us.zoom.videomeetings",
            "linkedin" to "com.linkedin.android",
            "snapchat" to "com.snapchat.android",
            "tiktok" to "com.zhiliaoapp.musically",
            "reddit" to "com.reddit.frontpage",
            "discord" to "com.discord",
            "netflix" to "com.netflix.mediaclient",
            "hotstar" to "in.startv.hotstar",
            "disney+" to "com.disney.disneyplus",
            "amazon prime" to "com.amazon.avod.thirdpartyclient",
            "prime video" to "com.amazon.avod.thirdpartyclient",
        )

        val key = appName.lowercase().trim()
        if (appMap.containsKey(key)) return appMap[key]

        for ((k, v) in appMap) {
            if (key.contains(k) || k.contains(key)) return v
        }
        return null
    }

    private fun executeOpenUrl(args: Map<String, Any>): ToolResult {
        val url = args["url"]?.toString() ?: return ToolResult.failed("URL required")
        return try {
            val finalUrl = if (!url.startsWith("http")) "https://$url" else url
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.success("Opened URL: $finalUrl")
        } catch (e: ActivityNotFoundException) {
            ToolResult.failed("No browser available to open URL")
        }
    }

    private fun executeOpenSettings(args: Map<String, Any>): ToolResult {
        val settingsType = args["settingsType"]?.toString() ?: "main"
        val action = when (settingsType.lowercase()) {
            "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "notifications" -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "developer" -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
            "nfc" -> Settings.ACTION_NFC_SETTINGS
            "data", "mobile data" -> Settings.ACTION_DATA_ROAMING_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            ToolResult.success("Opened ${settingsType} settings")
        } catch (e: Exception) {
            // Fallback to main settings
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
                ToolResult.success("Opened Settings")
            } catch (e2: Exception) {
                ToolResult.failed("Failed to open settings")
            }
        }
    }

    private fun executeInspectScreen(): ToolResult {
        val service = NexarAccessibilityService.getInstance()
            ?: return ToolResult.requiresPermission("Accessibility Service")

        val info = service.inspectCurrentScreen()
        return if (info.isNotEmpty()) {
            ToolResult.success(info)
        } else {
            ToolResult.success("Screen is empty or cannot be inspected")
        }
    }

    private fun executeTapElement(args: Map<String, Any>): ToolResult {
        val description = args["elementDescription"]?.toString()
            ?: return ToolResult.failed("Element description required")
        val exactMatch = args["exactMatch"] as? Boolean ?: false

        val service = NexarAccessibilityService.getInstance()
            ?: return ToolResult.requiresPermission("Accessibility Service")

        return if (service.tapElement(description, exactMatch)) {
            ToolResult.success("Tapped: $description")
        } else {
            ToolResult.notFound("Element not found or not tappable: $description")
        }
    }

    private fun executeLongPressElement(args: Map<String, Any>): ToolResult {
        val description = args["elementDescription"]?.toString()
            ?: return ToolResult.failed("Element description required")

        val service = NexarAccessibilityService.getInstance()
            ?: return ToolResult.requiresPermission("Accessibility Service")

        return if (service.longPressElement(description)) {
            ToolResult.success("Long pressed: $description")
        } else {
            ToolResult.notFound("Element not found: $description")
        }
    }

    private fun executeTypeText(args: Map<String, Any>): ToolResult {
        val text = args["text"]?.toString() ?: return ToolResult.failed("Text required")
        val targetField = args["targetField"]?.toString()

        val service = NexarAccessibilityService.getInstance()
            ?: return ToolResult.requiresPermission("Accessibility Service")

        return if (service.typeText(text, targetField)) {
            ToolResult.success("Typed text successfully")
        } else {
            ToolResult.failed("Could not type text. No focused text field found.")
        }
    }

    private fun executeClearText(): ToolResult {
        val service = NexarAccessibilityService.getInstance()
            ?: return ToolResult.requiresPermission("Accessibility Service")

        return if (service.clearText()) {
            ToolResult.success("Text cleared")
        } else {
            ToolResult.failed("No text field to clear")
        }
    }

    private fun executeScroll(args: Map<String, Any>): ToolResult {
        val direction = args["direction"]?.toString() ?: return ToolResult.failed("Direction required")
        val amount = args["amount"]?.toString() ?: "medium"

        val service = NexarAccessibilityService.getInstance()
            ?: return ToolResult.requiresPermission("Accessibility Service")

        return if (service.scroll(direction, amount)) {
            ToolResult.success("Scrolled $direction")
        } else {
            ToolResult.failed("Could not scroll $direction")
        }
    }

    private fun executePressBack(): ToolResult {
        val service = NexarAccessibilityService.getInstance()
            ?: return ToolResult.requiresPermission("Accessibility Service")

        service.pressBack()
        return ToolResult.success("Pressed back")
    }

    private fun executeOpenRecentApps(): ToolResult {
        val service = NexarAccessibilityService.getInstance()
            ?: return ToolResult.requiresPermission("Accessibility Service")

        service.openRecentApps()
        return ToolResult.success("Opened recent apps")
    }

    private fun executeMakePhoneCall(args: Map<String, Any>): ToolResult {
        val target = args["target"]?.toString() ?: return ToolResult.failed("Target required")
        val confirmed = args["confirmed"] as? Boolean ?: false

        if (!confirmed) {
            return ToolResult.requiresConfirmation("Please confirm: Call $target?")
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {
            return ToolResult.requiresPermission("CALL_PHONE")
        }

        // Try to find a contact first if it looks like a name
        val phoneNumber = if (target.matches(Regex("[+\\d\\s\\-()]+"))) {
            target.replace(Regex("[^+\\d]"), "")
        } else {
            findPhoneForContact(target) ?: return ToolResult.notFound("Contact not found: $target")
        }

        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.success("Calling $target ($phoneNumber)")
        } catch (e: Exception) {
            ToolResult.failed("Failed to make call: ${e.message}")
        }
    }

    private fun executeFindContact(args: Map<String, Any>): ToolResult {
        val name = args["name"]?.toString() ?: return ToolResult.failed("Name required")

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            return ToolResult.requiresPermission("READ_CONTACTS")
        }

        val contacts = findContactsByName(name)
        return if (contacts.isEmpty()) {
            ToolResult.notFound("No contacts found matching: $name")
        } else {
            val result = contacts.joinToString("; ") { "${it.first}: ${it.second.joinToString(", ")}" }
            ToolResult.success("Found: $result", mapOf("contacts" to result))
        }
    }

    private fun findPhoneForContact(name: String): String? {
        return try {
            val contacts = findContactsByName(name)
            contacts.firstOrNull()?.second?.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun findContactsByName(name: String): List<Pair<String, List<String>>> {
        val results = mutableListOf<Pair<String, List<String>>>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER
                ),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?",
                arrayOf("%$name%"),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                    val displayName = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
                    val hasPhone = it.getInt(it.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER))

                    if (hasPhone > 0) {
                        val phones = mutableListOf<String>()
                        val phoneCursor = context.contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(id),
                            null
                        )
                        phoneCursor?.use { pc ->
                            while (pc.moveToNext()) {
                                phones.add(pc.getString(pc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)))
                            }
                        }
                        if (phones.isNotEmpty()) {
                            results.add(Pair(displayName, phones))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Error finding contacts", e)
        }
        return results
    }

    private fun executeSendWhatsAppMessage(args: Map<String, Any>): ToolResult {
        val contactName = args["contactName"]?.toString() ?: return ToolResult.failed("Contact name required")
        val message = args["message"]?.toString() ?: return ToolResult.failed("Message required")
        val confirmed = args["confirmed"] as? Boolean ?: false

        if (!confirmed) {
            return ToolResult.requiresConfirmation(
                "Send WhatsApp message to $contactName: \"$message\"?"
            )
        }

        val pm = context.packageManager
        val whatsappInstalled = try {
            pm.getPackageInfo("com.whatsapp", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) { false }

        if (!whatsappInstalled) {
            return ToolResult.notFound("WhatsApp is not installed")
        }

        // Try to use accessibility service to send the message via WhatsApp UI
        val service = NexarAccessibilityService.getInstance()
        if (service != null) {
            // Open WhatsApp
            val waIntent = pm.getLaunchIntentForPackage("com.whatsapp")
                ?: return ToolResult.failed("Cannot launch WhatsApp")
            waIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(waIntent)

            // Return with instructions for the agent loop to continue
            return ToolResult.success(
                "WhatsApp opened. Agent loop: 1) Inspect screen, 2) Tap search/new chat, " +
                "3) Type '$contactName', 4) Tap contact, 5) Tap message field, " +
                "6) Type message, 7) Confirm send with user before tapping send button.",
                mapOf(
                    "contactName" to contactName,
                    "message" to message,
                    "requiresAgentLoop" to "true"
                )
            )
        } else {
            // Fallback: open WhatsApp with intent
            return try {
                val encodedMessage = Uri.encode(message)
                // Try to find contact phone number first
                val phoneNumber = findPhoneForContact(contactName)

                if (phoneNumber != null) {
                    val cleanPhone = phoneNumber.replace(Regex("[^+\\d]"), "")
                    val intent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://wa.me/$cleanPhone?text=$encodedMessage")).apply {
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult.success(
                        "Opened WhatsApp chat with $contactName. " +
                        "Please verify the recipient and tap Send to send the message.",
                        mapOf("note" to "Accessibility service not active - manual send required")
                    )
                } else {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://wa.me/?text=$encodedMessage")
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolResult.success(
                        "Opened WhatsApp. Please select $contactName and send the message manually.",
                        mapOf("note" to "Contact phone number not found, manual selection required")
                    )
                }
            } catch (e: Exception) {
                ToolResult.failed("Failed to open WhatsApp: ${e.message}")
            }
        }
    }

    private fun executeGetNotifications(args: Map<String, Any>): ToolResult {
        val appFilter = args["appFilter"]?.toString()

        val notifications = notificationRepository.getNotifications(appFilter)
        return if (notifications.isEmpty()) {
            ToolResult.success("No active notifications${if (appFilter != null) " from $appFilter" else ""}")
        } else {
            val summary = notifications.take(10).joinToString("\n") {
                "${it.appName}: ${it.title} - ${it.text}"
            }
            ToolResult.success("Notifications:\n$summary", mapOf("count" to notifications.size.toString()))
        }
    }

    private fun executeOpenNotification(args: Map<String, Any>): ToolResult {
        val key = args["notificationKey"]?.toString() ?: return ToolResult.failed("Notification key required")

        val success = notificationRepository.openNotification(key)
        return if (success) {
            ToolResult.success("Opened notification")
        } else {
            ToolResult.notFound("Notification not found: $key")
        }
    }

    private fun executeToggleFlashlight(args: Map<String, Any>): ToolResult {
        val state = args["state"]?.toString() ?: "toggle"
        val cameraId = torchCameraId ?: return ToolResult.unavailable("No flashlight available")
        val manager = torchManager ?: return ToolResult.unavailable("Camera service unavailable")

        return try {
            val newState = when (state.lowercase()) {
                "on" -> true
                "off" -> false
                "toggle" -> !torchState
                else -> !torchState
            }
            manager.setTorchMode(cameraId, newState)
            torchState = newState
            ToolResult.success("Flashlight turned ${if (newState) "ON" else "OFF"}")
        } catch (e: CameraAccessException) {
            ToolResult.failed("Failed to control flashlight: ${e.message}")
        }
    }

    private fun executeControlVolume(args: Map<String, Any>): ToolResult {
        val action = args["action"]?.toString() ?: return ToolResult.failed("Action required")
        val streamTypeStr = args["streamType"]?.toString() ?: "media"

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val streamType = when (streamTypeStr.lowercase()) {
            "ring", "ringtone" -> AudioManager.STREAM_RING
            "notification" -> AudioManager.STREAM_NOTIFICATION
            "alarm" -> AudioManager.STREAM_ALARM
            "voice", "call" -> AudioManager.STREAM_VOICE_CALL
            else -> AudioManager.STREAM_MUSIC
        }

        return try {
            when (action.lowercase()) {
                "up" -> {
                    audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    val current = audioManager.getStreamVolume(streamType)
                    val max = audioManager.getStreamMaxVolume(streamType)
                    ToolResult.success("Volume increased to $current/$max")
                }
                "down" -> {
                    audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    val current = audioManager.getStreamVolume(streamType)
                    val max = audioManager.getStreamMaxVolume(streamType)
                    ToolResult.success("Volume decreased to $current/$max")
                }
                "mute" -> {
                    audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                    ToolResult.success("${streamTypeStr.replaceFirstChar { it.uppercase() }} volume muted")
                }
                "unmute" -> {
                    audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                    ToolResult.success("${streamTypeStr.replaceFirstChar { it.uppercase() }} volume unmuted")
                }
                else -> ToolResult.failed("Unknown volume action: $action")
            }
        } catch (e: Exception) {
            ToolResult.failed("Volume control failed: ${e.message}")
        }
    }

    private fun executeGetDeviceInfo(args: Map<String, Any>): ToolResult {
        val infoType = args["infoType"]?.toString() ?: "all"

        val info = StringBuilder()

        when (infoType.lowercase()) {
            "battery" -> {
                val batteryInfo = getBatteryInfo()
                info.append(batteryInfo)
            }
            "storage" -> {
                val storageInfo = getStorageInfo()
                info.append(storageInfo)
            }
            "device" -> {
                info.append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                info.append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
                info.append("Build: ${Build.DISPLAY}")
            }
            else -> {
                info.append(getBatteryInfo())
                info.append("\n")
                info.append(getStorageInfo())
                info.append("\n")
                info.append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                info.append("Android: ${Build.VERSION.RELEASE}")
            }
        }

        return ToolResult.success(info.toString())
    }

    private fun getBatteryInfo(): String {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = batteryManager.isCharging
        return "Battery: $level%${if (isCharging) " (Charging)" else ""}"
    }

    private fun getStorageInfo(): String {
        return try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val totalGb = stat.totalBytes / (1024 * 1024 * 1024)
            val availGb = stat.availableBytes / (1024 * 1024 * 1024)
            "Storage: ${availGb}GB free of ${totalGb}GB"
        } catch (e: Exception) {
            "Storage info unavailable"
        }
    }

    private suspend fun executeRememberFact(args: Map<String, Any>): ToolResult {
        val key = args["key"]?.toString() ?: return ToolResult.failed("Key required")
        val value = args["value"]?.toString() ?: return ToolResult.failed("Value required")
        val description = args["description"]?.toString() ?: "$key: $value"

        memoryRepository.saveFact(key, value, description)
        return ToolResult.success("Remembered: $description")
    }

    private suspend fun executeRecallFact(args: Map<String, Any>): ToolResult {
        val query = args["query"]?.toString() ?: return ToolResult.failed("Query required")

        val facts = memoryRepository.searchFacts(query)
        return if (facts.isEmpty()) {
            ToolResult.success("Nothing found in memory for: $query")
        } else {
            val result = facts.joinToString("\n") { "${it.description}: ${it.value}" }
            ToolResult.success("From memory:\n$result")
        }
    }

    private fun executeSearchYouTube(args: Map<String, Any>): ToolResult {
        val query = args["query"]?.toString() ?: return ToolResult.failed("Query required")

        return try {
            val encodedQuery = Uri.encode(query)
            val intent = Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")).apply {
                setPackage("com.google.android.youtube")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                ToolResult.success("Opened YouTube searching for: $query")
            } catch (e: ActivityNotFoundException) {
                // YouTube not installed, open in browser
                val browserIntent = Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                ToolResult.success("Opened YouTube search for '$query' in browser (app not installed)")
            }
        } catch (e: Exception) {
            ToolResult.failed("Failed to search YouTube: ${e.message}")
        }
    }

    private fun executeConfirmAction(args: Map<String, Any>): ToolResult {
        val action = args["action"]?.toString() ?: return ToolResult.failed("Action required")
        val details = args["details"]?.toString() ?: ""
        return ToolResult.requiresConfirmation("Confirm: $action\nDetails: $details")
    }

    private fun executeStartScreenShare(): ToolResult {
        // This signals the UI layer to initiate MediaProjection
        return ToolResult.success(
            "Screen share request initiated. Please approve the screen capture permission.",
            mapOf("action" to "START_SCREEN_SHARE")
        )
    }

    private fun executeStopScreenShare(): ToolResult {
        return ToolResult.success(
            "Screen share stopped.",
            mapOf("action" to "STOP_SCREEN_SHARE")
        )
    }
}
