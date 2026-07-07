package soy.iko.crush.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import soy.iko.crush.proto.AgentMessage
import soy.iko.crush.proto.CurrentSession
import soy.iko.crush.proto.PermissionGrant
import soy.iko.crush.proto.PermissionGrantResponse
import soy.iko.crush.proto.PermissionRequest
import soy.iko.crush.proto.Session
import soy.iko.crush.proto.Workspace
import soy.iko.crush.proto.Message
import soy.iko.crush.proto.VersionInfo
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Low-level HTTP client for the crush `crush server` REST API (v1).
 *
 * The server is typically reached at `http://<host>:<port>` (TCP mode via
 * `crush server --host`). Every workspace-scoped request must include a
 * stable `client_id` query parameter so the server can track attached
 * clients for SSE lifecycle management.
 */
class CrushApiClient(
    var baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
) {
    val clientId: String = UUID.randomUUID().toString()

    fun health(): Boolean = runCatching {
        val req = Request.Builder().url("$baseUrl/v1/health").get().build()
        client.newCall(req).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    fun version(): VersionInfo = getJson("/v1/version")

    fun listWorkspaces(): List<Workspace> = getJson("/v1/workspaces")

    fun getWorkspace(id: String): Workspace = getJson("/v1/workspaces/$id")

    fun createWorkspace(path: String, yolo: Boolean = false): Workspace {
        val body = CrushJson.encodeToString(Workspace.serializer(), Workspace(id = path, path = path, yolo = yolo))
        return postJsonReturning("/v1/workspaces", body, Workspace.serializer())
    }

    fun listSessions(workspaceId: String): List<Session> =
        getJson("/v1/workspaces/$workspaceId/sessions?client_id=$clientId")

    fun createSession(workspaceId: String, title: String = ""): Session {
        val titleEscaped = title.replace("\\", "\\\\").replace("\"", "\\\"")
        val body = """{"title":"$titleEscaped"}"""
        return postJsonReturning("/v1/workspaces/$workspaceId/sessions?client_id=$clientId", body, Session.serializer())
    }

    fun getMessages(workspaceId: String, sessionId: String): List<Message> =
        getJson("/v1/workspaces/$workspaceId/sessions/$sessionId/messages?client_id=$clientId")

    fun sendMessage(workspaceId: String, msg: AgentMessage): Boolean {
        val body = CrushJson.encodeToString(AgentMessage.serializer(), msg)
        val req = Request.Builder()
            .url("$baseUrl/v1/workspaces/$workspaceId/agent")
            .post(body.toRequestBody(JSON))
            .build()
        return client.newCall(req).execute().use { it.isSuccessful }
    }

    fun setCurrentSession(workspaceId: String, sessionId: String) {
        val body = CrushJson.encodeToString(CurrentSession.serializer(), CurrentSession(sessionId))
        postVoid("/v1/workspaces/$workspaceId/current-session?client_id=$clientId", body)
    }

    fun grantPermission(workspaceId: String, req: PermissionRequest, action: String): PermissionGrantResponse {
        val body = CrushJson.encodeToString(PermissionGrant.serializer(), PermissionGrant(req, action))
        return postJsonReturning("/v1/workspaces/$workspaceId/permissions/grant?client_id=$clientId", body, PermissionGrantResponse.serializer())
    }

    fun cancelRun(workspaceId: String, sessionId: String): Boolean {
        val req = Request.Builder()
            .url("$baseUrl/v1/workspaces/$workspaceId/agent/sessions/$sessionId/cancel?client_id=$clientId")
            .post("{}".toRequestBody(JSON))
            .build()
        return client.newCall(req).execute().use { it.isSuccessful }
    }

    /** Build the SSE request for a workspace event stream. */
    fun eventsRequest(workspaceId: String): Request =
        Request.Builder()
            .url("$baseUrl/v1/workspaces/$workspaceId/events?client_id=$clientId")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .get()
            .build()

    fun client(): OkHttpClient = client

    // ── helpers ───────────────────────────────────────────────────────────

    private inline fun <reified T> getJson(path: String): T {
        val req = Request.Builder().url("$baseUrl$path").get().build()
        return client.newCall(req).execute().use { resp ->
            checkBody(resp)
            CrushJson.decodeFromString<T>(resp.body!!.string())
        }
    }

    private inline fun <reified T> postJsonReturning(path: String, body: String, deserializer: kotlinx.serialization.KSerializer<T>): T {
        val req = Request.Builder().url("$baseUrl$path").post(body.toRequestBody(JSON)).build()
        return client.newCall(req).execute().use { resp ->
            checkBody(resp)
            val raw = resp.body?.string().orEmpty()
            if (raw.isBlank()) {
                @Suppress("UNCHECKED_CAST")
                (Unit as T)
            } else {
                CrushJson.decodeFromString(deserializer, raw)
            }
        }
    }

    private fun postVoid(path: String, body: String) {
        val req = Request.Builder().url("$baseUrl$path").post(body.toRequestBody(JSON)).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${resp.body?.string().orEmpty()}")
        }
    }

    private fun checkBody(resp: Response) {
        if (!resp.isSuccessful) {
            val body = resp.body?.string().orEmpty()
            throw IOException("HTTP ${resp.code}: $body")
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
