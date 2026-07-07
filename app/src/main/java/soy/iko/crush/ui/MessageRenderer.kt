package soy.iko.crush.ui

import soy.iko.crush.proto.ContentPart
import soy.iko.crush.proto.FinishReason
import soy.iko.crush.proto.Message
import soy.iko.crush.proto.MessageRole
import soy.iko.crush.proto.PartType
import soy.iko.crush.proto.PartWrapper

/**
 * Render helpers that flatten the discriminated `parts` list of a [Message]
 * into simple text segments suitable for the chat UI.
 */
object MessageRenderer {

    /** Concatenate all text segments from assistant/user messages. */
    fun renderText(message: Message): String {
        val sb = StringBuilder()
        for (wrapper in message.parts) {
            val text = renderPart(wrapper)
            if (text.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(text)
            }
        }
        return sb.toString()
    }

    fun renderPart(wrapper: PartWrapper): String = when (wrapper.type) {
        PartType.Text -> wrapper.data.text.orEmpty()
        PartType.Reasoning -> {
            val thinking = wrapper.data.thinking.orEmpty()
            if (thinking.isNotEmpty()) "💭 $thinking" else ""
        }
        PartType.ToolCall -> {
            val name = wrapper.data.name.orEmpty()
            val input = wrapper.data.input.orEmpty()
            val preview = if (input.length > 120) input.take(120) + "…" else input
            "🔧 $name($preview)"
        }
        PartType.ToolResult -> {
            val name = wrapper.data.name.orEmpty()
            val content = wrapper.data.content.orEmpty()
            val isError = wrapper.data.isError == true
            val icon = if (isError) "❌" else "✅"
            val preview = if (content.length > 200) content.take(200) + "…" else content
            "$icon $name: $preview"
        }
        PartType.Finish -> {
            val reason = wrapper.data.reason ?: FinishReason.Unknown
            "⟡ finished: ${reason.name}"
        }
        PartType.ShellCommand -> {
            val cmd = wrapper.data.command.orEmpty()
            val code = wrapper.data.exitCode ?: -1
            "⚙ \$ $cmd (exit $code)"
        }
        PartType.ImageUrl -> "[image: ${wrapper.data.url.orEmpty()}]"
        PartType.Binary -> "[binary: ${wrapper.data.mimeType.orEmpty()}]"
    }

    /** True if the message has any visible text content. */
    fun hasVisibleContent(message: Message): Boolean =
        message.parts.any { it.type == PartType.Text && !it.data.text.isNullOrEmpty() }

    /** A short label for the role. */
    fun roleLabel(role: MessageRole): String = when (role) {
        MessageRole.user -> "You"
        MessageRole.assistant -> "Crush"
        MessageRole.system -> "System"
        MessageRole.tool -> "Tool"
    }
}
