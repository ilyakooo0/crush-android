package soy.iko.crush.net

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.ResponseBody
import soy.iko.crush.proto.Payload
import soy.iko.crush.proto.PayloadType
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Decodes the SSE stream from `/v1/workspaces/{id}/events` into a cold
 * [Flow] of [Payload] objects.
 *
 * The crush server emits standard SSE frames:
 * ```
 * data: {"type":"message","payload":{"type":"updated","payload":{...}}}
 *
 * data: {"type":"run_complete","payload":{...}}
 * ```
 */
fun CrushApiClient.eventStream(workspaceId: String): Flow<Payload> = callbackFlow {
    val request = eventsRequest(workspaceId)
    val call = client().newCall(request)

    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            trySendBlockingOrClose(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (!response.isSuccessful) {
                close(IOException("SSE HTTP ${response.code}"))
                return
            }
            val body: ResponseBody = response.body ?: run {
                close(IOException("SSE: empty body"))
                return
            }
            try {
                val reader = BufferedReader(InputStreamReader(body.byteStream()))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line.isNullOrEmpty()) {
                        // blank line = frame boundary
                        if (sb.isNotEmpty()) {
                            val frame = sb.toString()
                            sb.setLength(0)
                            parseFrame(frame)?.let { trySendBlockingOrClose(it) }
                        }
                    } else if (line.startsWith("data:")) {
                        if (sb.isNotEmpty()) sb.append('\n')
                        sb.append(line.substring(5).trimStart())
                    }
                }
                // stream ended (server closed)
                channel.close()
            } catch (e: Exception) {
                close(e)
            }
        }
    })

    awaitClose { call.cancel() }
}

private fun parseFrame(frame: String): Payload? {
    val trimmed = frame.trim()
    if (trimmed.isEmpty()) return null
    return runCatching {
        CrushJson.decodeFromString(Payload.serializer(), trimmed)
    }.getOrElse {
        // Skip keepalive comments or non-JSON frames.
        null
    }
}

@Suppress("TooGenericExceptionThrown")
private fun <T> kotlinx.coroutines.channels.ProducerScope<T>.trySendBlockingOrClose(value: T) {
    val result = trySend(value)
    if (result.isFailure) {
        close(IOException("Channel closed"))
    }
}

private fun <T> kotlinx.coroutines.channels.ProducerScope<T>.trySendBlockingOrClose(e: Throwable) {
    close(e)
}
