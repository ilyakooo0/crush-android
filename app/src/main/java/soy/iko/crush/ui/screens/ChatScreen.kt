package soy.iko.crush.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import soy.iko.crush.proto.Message
import soy.iko.crush.proto.MessageRole
import soy.iko.crush.proto.PermissionRequest
import soy.iko.crush.proto.Session
import soy.iko.crush.ui.ChatUiState
import soy.iko.crush.ui.MessageRenderer
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    onNewSession: () -> Unit,
    onPickSession: (String) -> Unit,
    onGrantPermission: (PermissionRequest, Boolean) -> Unit,
    onDisconnect: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var showSessionPicker by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Crush", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.size(6.dp))
                            StatusDot(state)
                        }
                        val subtitle = state.currentSessionId?.let { "session ${it.take(8)}" } ?: "no session"
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
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Switch session") },
                                onClick = {
                                    showOverflow = false
                                    showSessionPicker = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Disconnect") },
                                onClick = {
                                    showOverflow = false
                                    onDisconnect()
                                },
                            )
                        }
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
            if (state.messages.isEmpty() && !state.isBusy) {
                EmptyConversation(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else {
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
private fun StatusDot(state: ChatUiState) {
    val color = when {
        state.isBusy -> MaterialTheme.colorScheme.tertiary
        state.currentSessionId != null -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun MessageBubble(message: Message) {
    val isUser = message.role == MessageRole.user
    val bg = if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 2.dp, start = 4.dp, end = 4.dp),
        ) {
            Text(
                MessageRenderer.roleLabel(message.role),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (message.createdAt > 0) {
                Text(
                    "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatTimestamp(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .clickable(
                    enabled = message.parts.any { it.type == soy.iko.crush.proto.PartType.Text },
                    onClick = {
                        val text = MessageRenderer.renderText(message)
                        if (text.isNotEmpty()) {
                            val clipboard =
                                context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("Crush", text),
                            )
                        }
                    },
                )
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (enabled && text.isNotBlank()) {
                            onSend()
                        }
                    },
                ),
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
    Surface(color = MaterialTheme.colorScheme.errorContainer, tonalElevation = 2.dp) {
        Text(
            message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
private fun EmptyConversation(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "💬",
            style = MaterialTheme.typography.displayMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Say hello to Crush",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Send a message below to start the conversation.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionQueue(
    requests: List<PermissionRequest>,
    onGrant: (PermissionRequest, Boolean) -> Unit,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionPickerSheet(
    sessions: List<Session>,
    currentId: String?,
    onPick: (String?) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Sessions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onNew) { Text("New session") }
            }
            Spacer(Modifier.height(8.dp))
            if (sessions.isEmpty()) {
                Text(
                    "No sessions yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                sessions.forEach { s ->
                    SessionRow(s, isActive = s.id == currentId, onClick = { onPick(s.id) })
                    HorizontalDivider()
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SessionRow(session: Session, isActive: Boolean, onClick: () -> Unit) {
    val title = session.title.ifBlank { session.id.take(8) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        if (session.messageCount > 0) {
            Text(
                "${session.messageCount} messages",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTimestamp(epochSeconds: Long): String {
    val timeMs = epochSeconds * 1000L
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timeMs))
}
