package soy.iko.crush.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "crush_settings")

/** Persists the last-used server host and workspace path. */
class SettingsStore(private val context: Context) {

    private val keyHost = stringPreferencesKey("server_host")
    private val keyWorkspace = stringPreferencesKey("workspace_path")

    val host: Flow<String> = context.dataStore.data.map { it[keyHost] ?: "" }
    val workspace: Flow<String> = context.dataStore.data.map { it[keyWorkspace] ?: "" }

    suspend fun setHost(value: String) {
        context.dataStore.edit { it[keyHost] = value }
    }

    suspend fun setWorkspace(value: String) {
        context.dataStore.edit { it[keyWorkspace] = value }
    }
}
