package soy.iko.crush.proto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PayloadType {
    @SerialName("lsp_event") LspEvent,
    @SerialName("mcp_event") McpEvent,
    @SerialName("permission_request") PermissionRequest,
    @SerialName("permission_notification") PermissionNotification,
    @SerialName("message") Message,
    @SerialName("session") Session,
    @SerialName("file") File,
    @SerialName("agent_event") AgentEvent,
    @SerialName("config_changed") ConfigChanged,
    @SerialName("skills_event") SkillsEvent,
    @SerialName("run_complete") RunComplete,
}

@Serializable
data class Payload(
    val type: PayloadType,
    val payload: kotlinx.serialization.json.JsonElement,
)
