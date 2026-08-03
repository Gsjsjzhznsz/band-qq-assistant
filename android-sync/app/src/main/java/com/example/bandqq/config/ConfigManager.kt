package com.example.bandqq.config

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "sync_config")

data class AppConfig(
    val wsUrl: String = "ws://127.0.0.1:3001",
    val httpUrl: String = "http://127.0.0.1:3000",
    val token: String = ""
)

object ConfigHolder {
    var config: AppConfig = AppConfig()
}

class ConfigManager(private val context: Context) {

    private object Keys {
        val WS = stringPreferencesKey("ws_url")
        val HTTP = stringPreferencesKey("http_url")
        val TOKEN = stringPreferencesKey("token")
    }

    suspend fun load(): AppConfig {
        val prefs = context.dataStore.data.first()
        val cfg = AppConfig(
            wsUrl = prefs[Keys.WS] ?: AppConfig().wsUrl,
            httpUrl = prefs[Keys.HTTP] ?: AppConfig().httpUrl,
            token = prefs[Keys.TOKEN] ?: ""
        )
        ConfigHolder.config = cfg
        return cfg
    }

    suspend fun save(config: AppConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WS] = config.wsUrl
            prefs[Keys.HTTP] = config.httpUrl
            prefs[Keys.TOKEN] = config.token
        }
        ConfigHolder.config = config
    }
}
