# crush-android

An Android client for the [crush](https://github.com/charmbracelet/crush) AI coding
assistant's server mode. Built with Kotlin and Jetpack Compose.

## How it works

Crush has a client/server architecture. Running `crush server --host 0.0.0.0:8080`
starts a daemon that exposes a REST API (v1) with real-time streaming via
Server-Sent Events (SSE). This app connects to that server over HTTP, lets you
pick a workspace (a directory on the server), and chat with the crush agent —
sending prompts, receiving streamed responses, and approving/denying tool
permission requests — all from your phone.

```
┌─────────────┐     HTTP + SSE      ┌──────────────┐
│  Android    │ ──────────────────→ │  crush       │
│  (this app) │ ←────────────────── │  server      │
└─────────────┘   /v1/* REST + SSE  └──────────────┘
```

## Features

- **Connect screen** — enter the server host and workspace path; connection
  settings are persisted via DataStore.
- **Chat UI** — message bubbles for user/assistant/tool messages, with
  reasoning, tool calls, and tool results rendered inline.
- **Session management** — create new sessions and switch between existing ones.
- **SSE event streaming** — real-time message updates, agent events, and
  run-completion via the `/v1/workspaces/{id}/events` endpoint.
- **Permission requests** — tool permission requests from the agent surface as
  dialogs; allow or deny with one tap.
- **Cancel runs** — stop a busy agent run mid-stream.

## Building

This project uses Nix for its dev shell (see `flake.nix`). The SDK platform
directory from Nix is named `android-37.0`, but AGP expects `android-37`, so a
small local SDK overlay is needed:

```sh
# Enter the dev shell
nix develop

# Create the corrected SDK overlay (one-time)
SDK_ROOT="$ANDROID_HOME"
LOCAL_SDK=".android-sdk"
mkdir -p "$LOCAL_SDK/platforms"
for d in build-tools cmdline-tools platform-tools tools licenses cmake; do
  ln -s "$SDK_ROOT/$d" "$LOCAL_SDK/$d"
done
cp -rL "$SDK_ROOT/platforms/android-37.0" "$LOCAL_SDK/platforms/android-37"
sed -i 's/AndroidVersion.ApiLevel=37\.0/AndroidVersion.ApiLevel=37/' \
  "$LOCAL_SDK/platforms/android-37/source.properties"
sed -i 's/<api-level>37\.0<\/api-level>/<api-level>37<\/api-level>/' \
  "$LOCAL_SDK/platforms/android-37/package.xml"

# Point Gradle at the overlay
echo "sdk.dir=$LOCAL_SDK" > local.properties

# Build
./gradlew assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Using

1. Start the crush server on a machine reachable from your phone:
   ```sh
   crush server --host 0.0.0.0:8080
   ```
2. Install the APK on your Android device.
3. Open the app, enter `http://<server-ip>:8080` and the workspace path
   (e.g. `/home/you/project`).
4. Tap **Connect**, then **+** to create a session, and start chatting.

## Project structure

```
app/src/main/java/soy/iko/crush/
├── CrushApp.kt              # Application + ViewModel factory
├── MainActivity.kt          # Single-activity Compose host
├── proto/                   # Protocol model classes (match crush's Go structs)
│   ├── Workspace.kt
│   ├── Session.kt
│   ├── Message.kt           # Message, ContentPart, PartWrapper (discriminated union)
│   ├── Events.kt            # AgentEvent, RunComplete, PermissionRequest, etc.
│   ├── Payload.kt           # SSE payload envelope (PayloadType discriminator)
│   └── Control.kt
├── net/
│   ├── CrushJson.kt         # Shared kotlinx.serialization Json instance
│   ├── CrushApiClient.kt    # OkHttp REST client for the v1 API
│   └── EventStream.kt       # SSE parsing → Flow<Payload>
├── data/
│   ├── CrushRepository.kt   # State aggregator: API + SSE → StateFlows
│   └── SettingsStore.kt     # DataStore preferences (host, workspace)
└── ui/
    ├── ChatViewModel.kt     # ViewModel exposing ChatUiState
    ├── MessageRenderer.kt   # Flattens message parts → display text
    ├── theme/CrushTheme.kt  # Material 3 color scheme
    └── screens/
        ├── ConnectScreen.kt # Server connection form
        └── ChatScreen.kt    # Chat + session picker + permission dialogs
```

## Protocol notes

The crush server uses a discriminated-union pattern for message parts. Each part
is wrapped as `{"type": "text"|"tool_call"|"tool_result"|…, "data": {…}}`. The
Kotlin `ContentPart` data class has all possible fields as nullable, and
`PartWrapper` carries the type discriminator. SSE events are wrapped in a
`Payload` envelope: `{"type": "message"|"session"|"permission_request"|…,
"payload": {…}}`.
