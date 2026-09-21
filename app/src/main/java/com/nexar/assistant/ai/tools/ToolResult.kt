package com.nexar.assistant.ai.tools

enum class ToolStatus {
    SUCCESS,
    FAILED,
    NOT_FOUND,
    REQUIRES_PERMISSION,
    REQUIRES_CONFIRMATION,
    UNAVAILABLE
}

data class ToolResult(
    val status: ToolStatus,
    val message: String,
    val data: Map<String, Any> = emptyMap()
) {
    companion object {
        fun success(message: String, data: Map<String, Any> = emptyMap()) =
            ToolResult(ToolStatus.SUCCESS, message, data)

        fun failed(message: String) =
            ToolResult(ToolStatus.FAILED, message)

        fun notFound(message: String) =
            ToolResult(ToolStatus.NOT_FOUND, message)

        fun requiresPermission(permission: String) =
            ToolResult(ToolStatus.REQUIRES_PERMISSION, "Permission required: $permission")

        fun requiresConfirmation(message: String) =
            ToolResult(ToolStatus.REQUIRES_CONFIRMATION, message)

        fun unavailable(message: String) =
            ToolResult(ToolStatus.UNAVAILABLE, message)
    }

    fun toJson(): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"status\":\"${status.name}\"")
        sb.append(",\"message\":\"${message.replace("\"", "\\\"")}\"")
        if (data.isNotEmpty()) {
            sb.append(",\"data\":{")
            data.entries.forEachIndexed { index, entry ->
                if (index > 0) sb.append(",")
                sb.append("\"${entry.key}\":\"${entry.value.toString().replace("\"", "\\\"")}\"")
            }
            sb.append("}")
        }
        sb.append("}")
        return sb.toString()
    }
}
