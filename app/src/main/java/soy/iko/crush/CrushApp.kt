package soy.iko.crush

import android.app.Application
import okhttp3.OkHttpClient
import soy.iko.crush.data.CrushRepository
import soy.iko.crush.data.SettingsStore
import soy.iko.crush.net.CrushApiClient
import soy.iko.crush.ui.ChatViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class CrushApp : Application() {

    lateinit var settings: SettingsStore
        private set
    lateinit var api: CrushApiClient
        private set
    lateinit var repo: CrushRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        api = CrushApiClient("http://localhost:8080")
        repo = CrushRepository(api)
    }

    @Suppress("UNCHECKED_CAST")
    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(repo, settings) as T
        }
    }
}
