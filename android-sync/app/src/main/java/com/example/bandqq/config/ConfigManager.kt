package com.example.bandqq.config

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "sync_config")

enum class ProtocolType { NAPCAT, SNOWLUMA }

data class EndpointConfig(
    val wsUrl: String,
    val httpUrl: String,
    val token: String
)

data class AppConfig(
    val napcat: EndpointConfig = EndpointConfig("ws://127.0.0.1:3001", "http://127.0.0.1:3000", ""),
    val snowluma: EndpointConfig = EndpointConfig("ws://127.0.0.1:3001", "http://127.0.0.1:3001", ""),
    val activeType: ProtocolType = ProtocolType.NAPCAT
)

fun AppConfig.getActiveEndpoint(): EndpointConfig = when (activeType) {
    ProtocolType.NAPCAT -> napcat
    ProtocolType.SNOWLUMA -> snowluma
}

object ConfigHolder {
    var config: AppConfig = AppConfig()
}

class ConfigManager(private val context: Context) {

    private object Keys {
        val WS = stringPreferencesKey("ws_url")
        val HTTP = stringPreferencesKey("http_url")
        val TOKEN = stringPreferencesKey("token")
        val SNOW_WS = stringPreferencesKey("snowluma_ws_url")
        val SNOW_HTTP = stringPreferencesKey("snowluma_http_url")
        val SNOW_TOKEN = stringPreferencesKey("snowluma_token")
        val ACTIVE = stringPreferencesKey("active_type")
    }

    suspend fun load(): AppConfig {
        val prefs = context.dataStore.data.first()
        val default = AppConfig()
        val cfg = AppConfig(
            napcat = EndpointConfig(
                wsUrl = prefs[Keys.WS] ?: default.napcat.wsUrl,
                httpUrl = prefs[Keys.HTTP] ?: default.napcat.httpUrl,
                token = prefs[Keys.TOKEN] ?: ""
            ),
            snowluma = EndpointConfig(
                wsUrl = prefs[Keys.SNOW_WS] ?: default.snowluma.wsUrl,
                httpUrl = prefs[Keys.SNOW_HTTP] ?: default.snowluma.httpUrl,
                token = prefs[Keys.SNOW_TOKEN] ?: ""
            ),
            activeType = prefs[Keys.ACTIVE]?.let { runCatching { ProtocolType.valueOf(it) }.getOrNull() }
                ?: ProtocolType.NAPCAT
        )
        ConfigHolder.config = cfg
        return cfg
    }

    suspend fun save(config: AppConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WS] = config.napcat.wsUrl
            prefs[Keys.HTTP] = config.napcat.httpUrl
            prefs[Keys.TOKEN] = config.napcat.token
            prefs[Keys.SNOW_WS] = config.snowluma.wsUrl
            prefs[Keys.SNOW_HTTP] = config.snowluma.httpUrl
            prefs[Keys.SNOW_TOKEN] = config.snowluma.token
            prefs[Keys.ACTIVE] = config.activeType.name
        }
        ConfigHolder.config = config
    }
}