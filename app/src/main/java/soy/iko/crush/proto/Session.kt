package soy.iko.crush.proto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val id: String,
    @SerialName("parent_session_id") val parentSessionId: String = "",
    val title: String = "",
    @SerialName("message_count") val messageCount: Long = 0,
    @SerialName("prompt_tokens") val promptTokens: Long = 0,
    @SerialName("completion_tokens") val completionTokens: Long = 0,
    @SerialName("summary_message_id") val summaryMessageId: String = "",
    val cost: Double = 0.0,
    val todos: List<Todo> = emptyList(),
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
    @SerialName("is_busy") val isBusy: Boolean = false,
    @SerialName("attached_clients") val attachedClients: Int = 0,
)

@Serializable
data class Todo(
    val content: String,
    val status: String,
    @SerialName("active_form") val activeForm: String = "",
)

@Serializable
data class CreateSessionRequest(
    val title: String = "",
)
