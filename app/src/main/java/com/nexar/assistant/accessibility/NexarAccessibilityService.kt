package com.nexar.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nexar.assistant.utils.NexarLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NexarAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "NexarAccessibility"
        private var instance: NexarAccessibilityService? = null

        fun getInstance(): NexarAccessibilityService? = instance

        private val _isActive = MutableStateFlow(false)
        val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
    }

    private val _currentPackage = MutableStateFlow("")
    val currentPackage: StateFlow<String> = _currentPackage.asStateFlow()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isActive.value = true
        NexarLogger.d(TAG, "Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            val pkg = it.packageName?.toString() ?: ""
            if (pkg.isNotEmpty()) {
                _currentPackage.value = pkg
            }
        }
    }

    override fun onInterrupt() {
        NexarLogger.d(TAG, "Accessibility Service interrupted")
    }

    override fun onDestroy() {
        instance = null
        _isActive.value = false
        super.onDestroy()
        NexarLogger.d(TAG, "Accessibility Service destroyed")
    }

    /**
     * Inspect the current screen and return a text description of visible elements.
     */
    fun inspectCurrentScreen(): String {
        return try {
            val rootNode = rootInActiveWindow ?: return "Screen not accessible"
            val sb = StringBuilder()
            sb.append("Current app: ${_currentPackage.value}\n")
            sb.append("Screen elements:\n")
            inspectNode(rootNode, sb, 0, maxDepth = 8)
            rootNode.recycle()
            sb.toString().take(3000) // Limit response size
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Screen inspection error", e)
            "Error inspecting screen: ${e.message}"
        }
    }

    private fun inspectNode(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int, maxDepth: Int) {
        if (node == null || depth > maxDepth) return

        try {
            val text = node.text?.toString()
            val contentDesc = node.contentDescription?.toString()
            val className = node.className?.toString()?.substringAfterLast(".")
            val isClickable = node.isClickable
            val isEditable = node.isEditable
            val isEnabled = node.isEnabled
            val isVisible = node.isVisibleToUser

            if (!isVisible) return

            val description = when {
                !text.isNullOrBlank() -> text
                !contentDesc.isNullOrBlank() -> "[${contentDesc}]"
                else -> null
            }

            val indent = "  ".repeat(depth)
            if (description != null) {
                sb.append("$indent")
                when {
                    isEditable -> sb.append("TextField")
                    isClickable && className == "Button" -> sb.append("Button")
                    isClickable -> sb.append("Clickable")
                    else -> sb.append("Text")
                }
                sb.append(": $description")
                if (!isEnabled) sb.append(" (disabled)")
                sb.append("\n")
            } else if (isClickable && isEnabled) {
                sb.append("${indent}Clickable: [$className]\n")
            }

            val childCount = node.childCount
            for (i in 0 until childCount) {
                val child = node.getChild(i) ?: continue
                inspectNode(child, sb, depth + 1, maxDepth)
                child.recycle()
            }
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Error inspecting node", e)
        }
    }

    /**
     * Find and tap an element matching the description.
     */
    fun tapElement(description: String, exactMatch: Boolean = false): Boolean {
        return try {
            val root = rootInActiveWindow ?: return false
            val node = findNode(root, description, exactMatch)
            if (node != null) {
                val result = performClickOnNode(node)
                node.recycle()
                root.recycle()
                result
            } else {
                root.recycle()
                false
            }
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Tap element error", e)
            false
        }
    }

    /**
     * Long press an element.
     */
    fun longPressElement(description: String): Boolean {
        return try {
            val root = rootInActiveWindow ?: return false
            val node = findNode(root, description, false)
            if (node != null) {
                val result = node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                node.recycle()
                root.recycle()
                result
            } else {
                root.recycle()
                false
            }
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Long press error", e)
            false
        }
    }

    /**
     * Type text into focused or specified field.
     */
    fun typeText(text: String, targetField: String? = null): Boolean {
        return try {
            val root = rootInActiveWindow ?: return false
            val node = if (targetField != null) {
                findEditableNode(root, targetField) ?: findFocusedNode(root)
            } else {
                findFocusedNode(root) ?: findEditableNode(root, null)
            }

            if (node != null) {
                // Focus the node first
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

                // Set text via bundle
                val bundle = Bundle().apply {
                    putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                val result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                node.recycle()
                root.recycle()
                result
            } else {
                root.recycle()
                false
            }
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Type text error", e)
            false
        }
    }

    /**
     * Clear text in focused field.
     */
    fun clearText(): Boolean {
        return try {
            val root = rootInActiveWindow ?: return false
            val node = findFocusedNode(root) ?: findEditableNode(root, null)
            if (node != null) {
                val bundle = Bundle().apply {
                    putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                }
                val result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                node.recycle()
                root.recycle()
                result
            } else {
                root.recycle()
                false
            }
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Clear text error", e)
            false
        }
    }

    /**
     * Scroll in the given direction.
     */
    fun scroll(direction: String, amount: String = "medium"): Boolean {
        return try {
            val root = rootInActiveWindow ?: return false

            // Find the first scrollable view
            val scrollNode = findScrollableNode(root)
            if (scrollNode != null) {
                val action = when (direction.lowercase()) {
                    "down" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    "up" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    "left" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    "right" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                }
                val result = scrollNode.performAction(action)
                scrollNode.recycle()
                root.recycle()
                result
            } else {
                // Fallback to gesture-based scroll
                root.recycle()
                performScrollGesture(direction)
            }
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Scroll error", e)
            false
        }
    }

    private fun performScrollGesture(direction: String): Boolean {
        return try {
            val displayMetrics = resources.displayMetrics
            val width = displayMetrics.widthPixels.toFloat()
            val height = displayMetrics.heightPixels.toFloat()

            val path = Path()
            when (direction.lowercase()) {
                "down" -> {
                    path.moveTo(width / 2, height * 0.7f)
                    path.lineTo(width / 2, height * 0.3f)
                }
                "up" -> {
                    path.moveTo(width / 2, height * 0.3f)
                    path.lineTo(width / 2, height * 0.7f)
                }
                "right" -> {
                    path.moveTo(width * 0.2f, height / 2)
                    path.lineTo(width * 0.8f, height / 2)
                }
                "left" -> {
                    path.moveTo(width * 0.8f, height / 2)
                    path.lineTo(width * 0.2f, height / 2)
                }
                else -> return false
            }

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
                .build()

            dispatchGesture(gesture, null, null)
            true
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Gesture scroll error", e)
            false
        }
    }

    /**
     * Press the Android back button.
     */
    fun pressBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Open recent apps overview.
     */
    fun openRecentApps(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    /**
     * Find a node matching the description (text/contentDesc/resourceId).
     */
    private fun findNode(
        root: AccessibilityNodeInfo,
        description: String,
        exactMatch: Boolean
    ): AccessibilityNodeInfo? {
        // Try text match
        val textNodes = root.findAccessibilityNodeInfosByText(description)
        if (textNodes.isNotEmpty()) {
            // Find the most clickable/actionable one
            val best = textNodes.find { it.isClickable && it.isEnabled && it.isVisibleToUser }
                ?: textNodes.find { it.isEnabled && it.isVisibleToUser }
                ?: textNodes.firstOrNull()
            // Recycle others
            textNodes.filter { it != best }.forEach { it.recycle() }
            if (best != null) return best
        }

        // Try content description match
        val descNodes = root.findAccessibilityNodeInfosByText(description)
        if (descNodes.isNotEmpty()) {
            val best = descNodes.firstOrNull()
            descNodes.drop(1).forEach { it.recycle() }
            return best
        }

        // Deep search with partial matching
        return if (!exactMatch) findNodeDeep(root, description.lowercase()) else null
    }

    private fun findNodeDeep(node: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
        if (!node.isVisibleToUser) return null

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        if ((text.contains(query) || desc.contains(query) || viewId.contains(query))
            && (node.isClickable || node.isEditable) && node.isEnabled) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeDeep(child, query)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun findFocusedNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    }

    private fun findEditableNode(root: AccessibilityNodeInfo, hint: String?): AccessibilityNodeInfo? {
        return findEditableNodeDeep(root, hint?.lowercase())
    }

    private fun findEditableNodeDeep(node: AccessibilityNodeInfo, hint: String?): AccessibilityNodeInfo? {
        if (!node.isVisibleToUser) return null

        if (node.isEditable && node.isEnabled) {
            if (hint == null) return node
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val nodeHint = node.hintText?.toString()?.lowercase() ?: ""
            if (text.contains(hint) || desc.contains(hint) || nodeHint.contains(hint)) {
                return node
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableNodeDeep(child, hint)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun findScrollableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findScrollableNodeDeep(root)
    }

    private fun findScrollableNodeDeep(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable && node.isEnabled && node.isVisibleToUser) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollableNodeDeep(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun performClickOnNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.isEnabled) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        // Try parent if node itself not clickable
        val parent = node.parent
        if (parent != null) {
            if (parent.isClickable && parent.isEnabled) {
                val result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent.recycle()
                return result
            }
            parent.recycle()
        }

        // Try gesture tap at node bounds
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.isEmpty) {
            return performTapGesture(
                bounds.exactCenterX(),
                bounds.exactCenterY()
            )
        }
        return false
    }

    private fun performTapGesture(x: Float, y: Float): Boolean {
        return try {
            val path = Path().apply { moveTo(x, y) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                .build()
            dispatchGesture(gesture, null, null)
            true
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Gesture tap error", e)
            false
        }
    }
}
