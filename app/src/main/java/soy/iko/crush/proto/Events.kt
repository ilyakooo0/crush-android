package soy.iko.crush.proto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PermissionRequest(
    val id: String = "",
    @SerialName("session_id") val sessionId: String = "",
    @SerialName("tool_call_id") val toolCallId: String = "",
    @SerialName("tool_name") val toolName: String = "",
    val description: String = "",
    val action: String = "",
    val params: JsonElement? = null,
    val path: String = "",
)

@Serializable
data class PermissionNotification(
    @SerialName("tool_call_id") val toolCallId: String = "",
    val granted: Boolean = false,
    val denied: Boolean = false,
)

@Serializable
data class PermissionGrant(
    val permission: PermissionRequest,
    val action: String,
)

@Serializable
data class PermissionGrantResponse(val resolved: Boolean = false)

@Serializable
enum class AgentEventType {
    @SerialName("error") Error,
    @SerialName("response") Response,
    @SerialName("summarize") Summarize,
}

@Serializable
data class AgentEvent(
    val type: AgentEventType,
    val message: Message? = null,
    val error: String? = null,
    @SerialName("run_id") val runId: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("session_title") val sessionTitle: String? = null,
    val progress: String? = null,
    val done: Boolean? = null,
)

@Serializable
data class RunComplete(
    @SerialName("session_id") val sessionId: String = "",
    @SerialName("run_id") val runId: String? = null,
    @SerialName("message_id") val messageId: String = "",
    val text: String? = null,
    val error: String? = null,
    val cancelled: Boolean? = null,
)

@Serializable
enum class EventType {
    @SerialName("created") Created,
    @SerialName("updated") Updated,
    @SerialName("deleted") Deleted,
}

@Serializable
data class EventEnvelope<T>(
    val type: EventType,
    val payload: T,
)

@Serializable
data class CurrentSession(
    @SerialName("session_id") val sessionId: String,
)
