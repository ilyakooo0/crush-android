package soy.iko.crush.proto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MessageRole { assistant, user, system, tool }

@Serializable
enum class FinishReason {
    @SerialName("end_turn") EndTurn,
    @SerialName("max_tokens") MaxTokens,
    @SerialName("tool_use") ToolUse,
    @SerialName("canceled") Canceled,
    @SerialName("error") Error,
    @SerialName("unknown") Unknown,
}

@Serializable
enum class PartType {
    @SerialName("reasoning") Reasoning,
    @SerialName("text") Text,
    @SerialName("image_url") ImageUrl,
    @SerialName("binary") Binary,
    @SerialName("tool_call") ToolCall,
    @SerialName("tool_result") ToolResult,
    @SerialName("finish") Finish,
    @SerialName("shell_command") ShellCommand,
}

@Serializable
data class PartWrapper(
    val type: PartType,
    val data: ContentPart,
)

@Serializable
data class ContentPart(
    val text: String? = null,
    val thinking: String? = null,
    val signature: String? = null,
    @SerialName("thought_signature") val thoughtSignature: String? = null,
    @SerialName("tool_id") val toolId: String? = null,
    @SerialName("started_at") val startedAt: Long? = null,
    @SerialName("finished_at") val finishedAt: Long? = null,
    val url: String? = null,
    val detail: String? = null,
    val id: String? = null,
    val name: String? = null,
    val input: String? = null,
    @SerialName("provider_executed") val providerExecuted: Boolean? = null,
    val finished: Boolean? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val content: String? = null,
    val data: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    val metadata: String? = null,
    @SerialName("is_error") val isError: Boolean? = null,
    val reason: FinishReason? = null,
    val time: Long? = null,
    val message: String? = null,
    val details: String? = null,
    val command: String? = null,
    val output: String? = null,
    @SerialName("exit_code") val exitCode: Int? = null,
)

@Serializable
data class Message(
    val id: String,
    val role: MessageRole,
    @SerialName("session_id") val sessionId: String,
    val parts: List<PartWrapper> = emptyList(),
    val model: String = "",
    val provider: String = "",
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
)

@Serializable
data class AgentMessage(
    @SerialName("session_id") val sessionId: String,
    @SerialName("run_id") val runId: String? = null,
    val prompt: String,
    val attachments: List<Attachment> = emptyList(),
)

@Serializable
data class Attachment(
    @SerialName("file_path") val filePath: String = "",
    @SerialName("file_name") val fileName: String = "",
    @SerialName("mime_type") val mimeType: String = "",
    val content: String = "",
)
