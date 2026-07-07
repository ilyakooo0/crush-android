package soy.iko.crush.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement
import soy.iko.crush.net.CrushApiClient
import soy.iko.crush.net.CrushJson
import soy.iko.crush.net.eventStream
import soy.iko.crush.proto.AgentEvent
import soy.iko.crush.proto.AgentMessage
import soy.iko.crush.proto.EventEnvelope
import soy.iko.crush.proto.EventType
import soy.iko.crush.proto.Message
import soy.iko.crush.proto.Payload
import soy.iko.crush.proto.PayloadType
import soy.iko.crush.proto.PermissionNotification
import soy.iko.crush.proto.PermissionRequest
import soy.iko.crush.proto.RunComplete
import soy.iko.crush.proto.Session
import soy.iko.crush.proto.Workspace
import java.util.UUID

/**
 * Aggregates the crush server API + SSE event stream into a simple
 * observable state holder. The repository owns the active workspace and
 * session, exposes the message log as a [StateFlow], and surfaces
 * permission requests and run-completion as a [SharedFlow].
 */
class CrushRepository(
    private val api: CrushApiClient,
) {
    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _workspaces = MutableStateFlow<List<Workspace>>(emptyList())
    val workspaces: StateFlow<List<Workspace>> = _workspaces.asStateFlow()

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _permissions = MutableStateFlow<List<PermissionRequest>>(emptyList())
    val permissions: StateFlow<List<PermissionRequest>> = _permissions.asStateFlow()

    private val _events = MutableSharedFlow<CrushEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<CrushEvent> = _events.asSharedFlow()

    var workspaceId: String? = null
        private set
    var sessionId: String? = null
        private set

    // ── connection / workspace ────────────────────────────────────────────

    suspend fun connect(host: String): Boolean {
        api.baseUrl = host.trimEnd('/')
        if (!api.health()) {
            _connection.value = ConnectionState.Disconnected
            return false
        }
        _connection.value = ConnectionState.Connected(api.version())
        refreshWorkspaces()
        return true
    }

    fun disconnect() {
        _connection.value = ConnectionState.Disconnected
        workspaceId = null
        sessionId = null
        _messages.value = emptyList()
        _sessions.value = emptyList()
        _permissions.value = emptyList()
        _workspaces.value = emptyList()
    }

    suspend fun refreshWorkspaces() {
        _workspaces.value = runCatching { api.listWorkspaces() }.getOrDefault(emptyList())
    }

    suspend fun openWorkspace(path: String): Workspace? {
        val ws = runCatching { api.createWorkspace(path) }.getOrNull()
            ?: runCatching { api.getWorkspace(path) }.getOrNull()
        if (ws != null) {
            workspaceId = ws.id
            refreshSessions()
        }
        return ws
    }

    suspend fun refreshSessions() {
        val wid = workspaceId ?: return
        _sessions.value = runCatching { api.listSessions(wid) }.getOrDefault(emptyList())
    }

    suspend fun openSession(id: String) {
        sessionId = id
        val wid = workspaceId ?: return
        runCatching { api.setCurrentSession(wid, id) }
        _messages.value = runCatching { api.getMessages(wid, id) }.getOrDefault(emptyList())
    }

    suspend fun newSession(): Session? {
        val wid = workspaceId ?: return null
        val s = runCatching { api.createSession(wid) }.getOrNull() ?: return null
        _sessions.value = _sessions.value + s
        openSession(s.id)
        return s
    }

    suspend fun sendPrompt(text: String): Boolean {
        val wid = workspaceId ?: return false
        val sid = sessionId ?: return false
        val runId = UUID.randomUUID().toString()
        val msg = AgentMessage(sessionId = sid, runId = runId, prompt = text)
        return api.sendMessage(wid, msg)
    }

    suspend fun grantPermission(req: PermissionRequest, allow: Boolean) {
        val wid = workspaceId ?: return
        val action = if (allow) "allow" else "deny"
        runCatching { api.grantPermission(wid, req, action) }
        _permissions.value = _permissions.value.filter { it.id != req.id }
    }

    suspend fun cancelCurrentRun() {
        val wid = workspaceId ?: return
        val sid = sessionId ?: return
        runCatching { api.cancelRun(wid, sid) }
    }

    // ── SSE event subscription ────────────────────────────────────────────

    fun subscribeEvents(): Flow<Payload> {
        val wid = workspaceId ?: throw IllegalStateException("No workspace open")
        return api.eventStream(wid)
    }

    /** Consume a [Payload] from the SSE stream, mutating internal state. */
    fun handlePayload(payload: Payload) {
        when (payload.type) {
            PayloadType.Message -> handleMessageEvent(payload.payload)
            PayloadType.Session -> handleSessionEvent(payload.payload)
            PayloadType.PermissionRequest -> handlePermissionEvent(payload.payload)
            PayloadType.PermissionNotification -> handlePermissionNotification(payload.payload)
            PayloadType.AgentEvent -> handleAgentEvent(payload.payload)
            PayloadType.RunComplete -> handleRunComplete(payload.payload)
            PayloadType.ConfigChanged,
            PayloadType.File,
            PayloadType.LspEvent,
            PayloadType.McpEvent,
            PayloadType.SkillsEvent -> {
                _events.tryEmit(CrushEvent.Info(payload.type.name, payload.payload.toString()))
            }
        }
    }

    private fun handleMessageEvent(element: JsonElement) {
        val env = runCatching {
            CrushJson.decodeFromString(EventEnvelope.serializer(Message.serializer()), element.toString())
        }.getOrNull() ?: return
        val msg = env.payload
        val list = _messages.value.toMutableList()
        val idx = list.indexOfFirst { it.id == msg.id }
        if (idx >= 0) list[idx] = msg else list.add(msg)
        _messages.value = list
        _events.tryEmit(CrushEvent.MessageUpdated(msg, env.type))
    }

    private fun handleSessionEvent(element: JsonElement) {
        val env = runCatching {
            CrushJson.decodeFromString(EventEnvelope.serializer(Session.serializer()), element.toString())
        }.getOrNull() ?: return
        val s = env.payload
        val list = _sessions.value.toMutableList()
        val idx = list.indexOfFirst { it.id == s.id }
        if (env.type == EventType.Deleted) {
            list.removeAll { it.id == s.id }
        } else if (idx >= 0) {
            list[idx] = s
        } else {
            list.add(s)
        }
        _sessions.value = list
        _events.tryEmit(CrushEvent.SessionUpdated(s, env.type))
    }

    private fun handlePermissionEvent(element: JsonElement) {
        val req = runCatching {
            CrushJson.decodeFromString(PermissionRequest.serializer(), element.toString())
        }.getOrNull() ?: return
        val list = _permissions.value.toMutableList()
        if (list.none { it.id == req.id }) list.add(req)
        _permissions.value = list
        _events.tryEmit(CrushEvent.PermissionRequested(req))
    }

    private fun handlePermissionNotification(element: JsonElement) {
        val note = runCatching {
            CrushJson.decodeFromString(PermissionNotification.serializer(), element.toString())
        }.getOrNull() ?: return
        _permissions.value = _permissions.value.filter { it.toolCallId != note.toolCallId }
    }

    private fun handleAgentEvent(element: JsonElement) {
        val ev = runCatching {
            CrushJson.decodeFromString(AgentEvent.serializer(), element.toString())
        }.getOrNull() ?: return
        _events.tryEmit(CrushEvent.AgentEvent(ev))
    }

    private fun handleRunComplete(element: JsonElement) {
        val rc = runCatching {
            CrushJson.decodeFromString(RunComplete.serializer(), element.toString())
        }.getOrNull() ?: return
        _events.tryEmit(CrushEvent.RunComplete(rc))
    }
}

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data class Connected(val version: soy.iko.crush.proto.VersionInfo) : ConnectionState()
}

val ConnectionState.isConnected: Boolean
    get() = this is ConnectionState.Connected

sealed class CrushEvent {
    data class MessageUpdated(val message: Message, val eventType: EventType) : CrushEvent()
    data class SessionUpdated(val session: Session, val eventType: EventType) : CrushEvent()
    data class PermissionRequested(val request: PermissionRequest) : CrushEvent()
    data class AgentEvent(val event: soy.iko.crush.proto.AgentEvent) : CrushEvent()
    data class RunComplete(val complete: soy.iko.crush.proto.RunComplete) : CrushEvent()
    data class Info(val kind: String, val raw: String) : CrushEvent()
}
