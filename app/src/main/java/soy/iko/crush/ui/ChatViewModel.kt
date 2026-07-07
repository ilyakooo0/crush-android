package soy.iko.crush.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import soy.iko.crush.data.ConnectionState
import soy.iko.crush.data.CrushEvent
import soy.iko.crush.data.CrushRepository
import soy.iko.crush.data.SettingsStore
import soy.iko.crush.proto.Message
import soy.iko.crush.proto.PermissionRequest

/** Immutable UI state. */
data class ChatUiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val host: String = "",
    val workspacePath: String = "",
    val workspaceOpen: Boolean = false,
    val sessions: List<soy.iko.crush.proto.Session> = emptyList(),
    val currentSessionId: String? = null,
    val messages: List<Message> = emptyList(),
    val pendingPermissions: List<PermissionRequest> = emptyList(),
    val isBusy: Boolean = false,
    val error: String? = null,
)

class ChatViewModel(
    private val repo: CrushRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    private val _busy = MutableStateFlow(false)

    private var sseJob: Job? = null

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<ChatUiState> =
        combine(
            repo.connection as Flow<Any>,
            settings.host as Flow<Any>,
            settings.workspace as Flow<Any>,
            repo.sessions as Flow<Any>,
            repo.messages as Flow<Any>,
            repo.permissions as Flow<Any>,
            _busy as Flow<Any>,
            _error as Flow<Any>,
        ) { values ->
            ChatUiState(
                connection = values[0] as ConnectionState,
                host = values[1] as String,
                workspacePath = values[2] as String,
                workspaceOpen = repo.workspaceId != null,
                sessions = values[3] as List<soy.iko.crush.proto.Session>,
                currentSessionId = repo.sessionId,
                messages = values[4] as List<Message>,
                pendingPermissions = values[5] as List<PermissionRequest>,
                isBusy = values[6] as Boolean,
                error = values[7] as String?,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    // ── actions ───────────────────────────────────────────────────────────

    fun connect(host: String, workspacePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _error.value = null
            settings.setHost(host)
            settings.setWorkspace(workspacePath)
            val ok = repo.connect(host)
            if (!ok) {
                _error.value = "Could not reach crush server at $host"
                return@launch
            }
            val ws = repo.openWorkspace(workspacePath)
            if (ws == null) {
                _error.value = "Could not open workspace '$workspacePath'"
                return@launch
            }
            startEventStream()
        }
    }

    fun newSession() {
        viewModelScope.launch(Dispatchers.IO) {
            _error.value = null
            val s = repo.newSession()
            if (s == null) _error.value = "Failed to create session"
        }
    }

    fun openSession(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _error.value = null
            repo.openSession(id)
        }
    }

    fun sendPrompt(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _error.value = null
            _busy.value = true
            val ok = repo.sendPrompt(text)
            if (!ok) _error.value = "Failed to send message"
        }
    }

    fun grantPermission(req: PermissionRequest, allow: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.grantPermission(req, allow)
        }
    }

    fun cancelRun() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.cancelCurrentRun()
        }
    }

    fun clearError() { _error.value = null }

    private fun startEventStream() {
        sseJob?.cancel()
        sseJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.subscribeEvents().collect { payload ->
                    repo.handlePayload(payload)
                    if (payload.type == soy.iko.crush.proto.PayloadType.RunComplete) {
                        _busy.value = false
                    }
                }
            } catch (e: Exception) {
                _error.value = "Stream lost: ${e.message}"
                _busy.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sseJob?.cancel()
    }
}
