package soy.iko.crush

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import soy.iko.crush.data.ConnectionState
import soy.iko.crush.data.isConnected
import soy.iko.crush.ui.ChatViewModel
import soy.iko.crush.ui.screens.ChatScreen
import soy.iko.crush.ui.screens.ConnectScreen
import soy.iko.crush.ui.theme.CrushTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as CrushApp
        setContent {
            CrushTheme {
                val vm: ChatViewModel = viewModel(factory = app.viewModelFactory)
                val state by vm.uiState.collectAsState()
                if (state.connection.isConnected) {
                    ChatScreen(
                        state = state,
                        onSend = vm::sendPrompt,
                        onCancel = vm::cancelRun,
                        onNewSession = vm::newSession,
                        onPickSession = vm::openSession,
                        onGrantPermission = vm::grantPermission,
                    )
                } else {
                    ConnectScreen(
                        defaultHost = state.host,
                        defaultWorkspace = state.workspacePath,
                        error = state.error,
                        onConnect = vm::connect,
                    )
                }
            }
        }
    }
}
