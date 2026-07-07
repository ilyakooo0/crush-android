package soy.iko.crush.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import soy.iko.crush.proto.MessageRole
import soy.iko.crush.ui.ChatUiState
import soy.iko.crush.ui.MessageRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    onNewSession: () -> Unit,
    onPickSession: (String) -> Unit,
    onGrantPermission: (soy.iko.crush.proto.PermissionRequest, Boolean) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var showSessionPicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Crush", fontWeight = FontWeight.Bold)
                        val subtitle = state.currentSessionId?.let { "session $it" } ?: "no session"
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (state.isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Filled.Stop, contentDescription = "Cancel run")
                        }
                    }
                    IconButton(onClick = { showSessionPicker = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Sessions")
                    }
                },
            )
        },
        bottomBar = {
            InputBar(
                text = input,
                onTextChange = { input = it },
                onSend = {
                    if (input.isNotBlank()) {
                        onSend(input)
                        input = ""
                    }
                },
                enabled = state.currentSessionId != null && !state.isBusy,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.error != null) {
                ErrorBanner(state.error)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    MessageBubble(msg)
                }
                if (state.isBusy && state.messages.isEmpty()) {
                    item { TypingIndicator() }
                }
            }
            PermissionQueue(
                requests = state.pendingPermissions,
                onGrant = onGrantPermission,
            )
        }
    }

    if (showSessionPicker) {
        SessionPickerSheet(
            sessions = state.sessions,
            currentId = state.currentSessionId,
            onPick = {
                showSessionPicker = false
                if (it != null) onPickSession(it)
            },
            onNew = {
                showSessionPicker = false
                onNewSession()
            },
            onDismiss = { showSessionPicker = false },
        )
    }
}

@Composable
private fun MessageBubble(message: soy.iko.crush.proto.Message) {
    val isUser = message.role == MessageRole.user
    val bg = if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Text(
            MessageRenderer.roleLabel(message.role),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp, start = 4.dp, end = 4.dp),
        )
        Box(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .padding(12.dp),
        ) {
            val text = MessageRenderer.renderText(message)
            if (text.isNotEmpty()) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = if (isUser) FontFamily.Default else FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else if (message.parts.isNotEmpty()) {
                // Non-text parts (tool calls etc.) — render compactly.
                Text(
                    message.parts.joinToString("\n") { MessageRenderer.renderPart(it) },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message Crush…") },
                maxLines = 5,
                enabled = enabled,
            )
            Spacer(Modifier.size(8.dp))
            IconButton(onClick = onSend, enabled = enabled && text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
            .padding(8.dp),
    ) {
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text("Crush is thinking…", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PermissionQueue(
    requests: List<soy.iko.crush.proto.PermissionRequest>,
    onGrant: (soy.iko.crush.proto.PermissionRequest, Boolean) -> Unit,
) {
    if (requests.isEmpty()) return
    val req = requests.first()
    AlertDialog(
        onDismissRequest = { onGrant(req, false) },
        title = { Text("Permission required") },
        text = {
            Column {
                Text("Tool: ${req.toolName}")
                Spacer(Modifier.height(4.dp))
                Text(req.description, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button({ onGrant(req, true) }) { Text("Allow") } },
        dismissButton = { TextButton({ onGrant(req, false) }) { Text("Deny") } },
    )
}

@Composable
private fun SessionPickerSheet(
    sessions: List<soy.iko.crush.proto.Session>,
    currentId: String?,
    onPick: (String?) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sessions") },
        text = {
            Column {
                sessions.forEach { s ->
                    val active = s.id == currentId
                    Text(
                        s.title.ifBlank { s.id.take(8) },
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(8.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (sessions.isEmpty()) {
                    Text("No sessions yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { Button(onNew) { Text("New session") } },
        dismissButton = { TextButton(onDismiss) { Text("Close") } },
    )
}
