package com.nexar.assistant.ai.tools

import org.json.JSONArray
import org.json.JSONObject

object NexarToolDefinitions {

    fun getAllToolDeclarations(): JSONArray {
        val tools = JSONArray()
        tools.put(openApp())
        tools.put(openUrl())
        tools.put(openSettings())
        tools.put(inspectScreen())
        tools.put(tapElement())
        tools.put(longPressElement())
        tools.put(typeText())
        tools.put(clearText())
        tools.put(scroll())
        tools.put(pressBack())
        tools.put(openRecentApps())
        tools.put(makePhoneCall())
        tools.put(findContact())
        tools.put(sendWhatsAppMessage())
        tools.put(getNotifications())
        tools.put(openNotification())
        tools.put(toggleFlashlight())
        tools.put(controlVolume())
        tools.put(getDeviceInfo())
        tools.put(rememberFact())
        tools.put(recallFact())
        tools.put(searchYouTube())
        tools.put(confirmAction())
        tools.put(startScreenShare())
        tools.put(stopScreenShare())
        return tools
    }

    private fun openApp() = toolDecl(
        name = "openApp",
        description = "Open an Android application by its name",
        params = mapOf(
            "appName" to ("string" to "The human-readable name of the app to open (e.g., 'WhatsApp', 'YouTube', 'Settings', 'Camera')")
        ),
        required = listOf("appName")
    )

    private fun openUrl() = toolDecl(
        name = "openUrl",
        description = "Open a URL in the default browser or appropriate app",
        params = mapOf(
            "url" to ("string" to "The full URL to open")
        ),
        required = listOf("url")
    )

    private fun openSettings() = toolDecl(
        name = "openSettings",
        description = "Open an Android Settings screen",
        params = mapOf(
            "settingsType" to ("string" to "Type of settings: 'main', 'wifi', 'bluetooth', 'display', 'sound', 'battery', 'apps', 'notifications', 'location', 'accessibility', 'developer'")
        ),
        required = listOf("settingsType")
    )

    private fun inspectScreen() = toolDecl(
        name = "inspectScreen",
        description = "Inspect the current screen's accessible UI elements - text, buttons, and interactive components",
        params = emptyMap(),
        required = emptyList()
    )

    private fun tapElement() = toolDecl(
        name = "tapElement",
        description = "Tap/click a UI element on screen identified by text, content description, or resource ID",
        params = mapOf(
            "elementDescription" to ("string" to "Text content, content description, or resource ID of the element to tap"),
            "exactMatch" to ("boolean" to "Whether to match exactly (true) or use partial matching (false, default)")
        ),
        required = listOf("elementDescription")
    )

    private fun longPressElement() = toolDecl(
        name = "longPressElement",
        description = "Long press a UI element",
        params = mapOf(
            "elementDescription" to ("string" to "Text content, content description, or resource ID of the element to long press")
        ),
        required = listOf("elementDescription")
    )

    private fun typeText() = toolDecl(
        name = "typeText",
        description = "Type text into the currently focused or specified text field",
        params = mapOf(
            "text" to ("string" to "The text to type"),
            "targetField" to ("string" to "Optional: description of the text field to type into")
        ),
        required = listOf("text")
    )

    private fun clearText() = toolDecl(
        name = "clearText",
        description = "Clear the text in the currently focused text field",
        params = emptyMap(),
        required = emptyList()
    )

    private fun scroll() = toolDecl(
        name = "scroll",
        description = "Scroll the screen or a scrollable element",
        params = mapOf(
            "direction" to ("string" to "Scroll direction: 'up', 'down', 'left', 'right'"),
            "amount" to ("string" to "Amount to scroll: 'small', 'medium', 'large' (default: medium)")
        ),
        required = listOf("direction")
    )

    private fun pressBack() = toolDecl(
        name = "pressBack",
        description = "Press the Android back button",
        params = emptyMap(),
        required = emptyList()
    )

    private fun openRecentApps() = toolDecl(
        name = "openRecentApps",
        description = "Open the recent apps overview",
        params = emptyMap(),
        required = emptyList()
    )

    private fun makePhoneCall() = toolDecl(
        name = "makePhoneCall",
        description = "Make a phone call to a contact name or phone number",
        params = mapOf(
            "target" to ("string" to "Contact name or phone number to call"),
            "confirmed" to ("boolean" to "Whether the user has confirmed the call (false by default, require confirmation first)")
        ),
        required = listOf("target")
    )

    private fun findContact() = toolDecl(
        name = "findContact",
        description = "Find a contact by name and return their phone numbers",
        params = mapOf(
            "name" to ("string" to "The contact name to search for")
        ),
        required = listOf("name")
    )

    private fun sendWhatsAppMessage() = toolDecl(
        name = "sendWhatsAppMessage",
        description = "Send a WhatsApp message to a contact. Always confirm with user before sending.",
        params = mapOf(
            "contactName" to ("string" to "Name of the WhatsApp contact"),
            "message" to ("string" to "The message text to send"),
            "confirmed" to ("boolean" to "Whether the user has explicitly confirmed sending this message")
        ),
        required = listOf("contactName", "message")
    )

    private fun getNotifications() = toolDecl(
        name = "getNotifications",
        description = "Get the current active notifications (requires notification listener permission)",
        params = mapOf(
            "appFilter" to ("string" to "Optional: filter notifications by app name")
        ),
        required = emptyList()
    )

    private fun openNotification() = toolDecl(
        name = "openNotification",
        description = "Open a specific notification",
        params = mapOf(
            "notificationKey" to ("string" to "The key or title of the notification to open")
        ),
        required = listOf("notificationKey")
    )

    private fun toggleFlashlight() = toolDecl(
        name = "toggleFlashlight",
        description = "Turn the device flashlight on or off",
        params = mapOf(
            "state" to ("string" to "Desired state: 'on', 'off', or 'toggle'")
        ),
        required = listOf("state")
    )

    private fun controlVolume() = toolDecl(
        name = "controlVolume",
        description = "Control the device volume",
        params = mapOf(
            "action" to ("string" to "Action: 'up', 'down', 'mute', 'unmute'"),
            "streamType" to ("string" to "Stream: 'media', 'ring', 'notification', 'alarm' (default: media)")
        ),
        required = listOf("action")
    )

    private fun getDeviceInfo() = toolDecl(
        name = "getDeviceInfo",
        description = "Get device information such as battery level, storage, model name",
        params = mapOf(
            "infoType" to ("string" to "Type of info: 'battery', 'storage', 'device', 'all'")
        ),
        required = listOf("infoType")
    )

    private fun rememberFact() = toolDecl(
        name = "rememberFact",
        description = "Store a fact or preference in NEXAR's memory about the user",
        params = mapOf(
            "key" to ("string" to "A short category/key for the memory (e.g., 'favorite_game', 'home_city')"),
            "value" to ("string" to "The value to remember"),
            "description" to ("string" to "Human-readable description of what is being remembered")
        ),
        required = listOf("key", "value", "description")
    )

    private fun recallFact() = toolDecl(
        name = "recallFact",
        description = "Recall a stored fact from NEXAR's memory",
        params = mapOf(
            "query" to ("string" to "What to search for in memory")
        ),
        required = listOf("query")
    )

    private fun searchYouTube() = toolDecl(
        name = "searchYouTube",
        description = "Open YouTube and search for a query",
        params = mapOf(
            "query" to ("string" to "The search query for YouTube")
        ),
        required = listOf("query")
    )

    private fun confirmAction() = toolDecl(
        name = "confirmAction",
        description = "Request explicit user confirmation for a sensitive action before proceeding",
        params = mapOf(
            "action" to ("string" to "Description of the action requiring confirmation"),
            "details" to ("string" to "Specific details: recipient, content, etc.")
        ),
        required = listOf("action", "details")
    )

    private fun startScreenShare() = toolDecl(
        name = "startScreenShare",
        description = "Start screen capture/sharing (requires user permission flow)",
        params = emptyMap(),
        required = emptyList()
    )

    private fun stopScreenShare() = toolDecl(
        name = "stopScreenShare",
        description = "Stop active screen capture/sharing",
        params = emptyMap(),
        required = emptyList()
    )

    // Helper to build tool declaration JSONObject
    private fun toolDecl(
        name: String,
        description: String,
        params: Map<String, Pair<String, String>>,
        required: List<String>
    ): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            if (params.isNotEmpty()) {
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        params.forEach { (paramName, typeAndDesc) ->
                            put(paramName, JSONObject().apply {
                                put("type", typeAndDesc.first.uppercase())
                                put("description", typeAndDesc.second)
                            })
                        }
                    })
                    if (required.isNotEmpty()) {
                        put("required", JSONArray(required))
                    }
                })
            }
        }
    }
}
