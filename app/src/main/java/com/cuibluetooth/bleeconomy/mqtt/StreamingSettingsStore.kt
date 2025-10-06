package com.cuibluetooth.bleeconomy.mqtt

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cuibluetooth.bleeconomy.BuildConfig
import com.cuibluetooth.bleeconomy.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID


private const val DATASTORE_NAME = "imu_streaming"
private val Context.streamingSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)

data class StreamingSettings(
    val enabled: Boolean,
    val host: String,
    val port: Int,
    val useTls: Boolean,
    val topic: String,
    val username: String?,
    val password: String?
) {
    fun toMqttConfig(base: MqttConfig): MqttConfig {
        val scheme = if (useTls) "ssl" else base.serverUri.substringBefore("://")
        val serverUri = "$scheme://$host:$port"
        val resolvedTopic = topic.ifBlank { base.topic }
        return base.copy(
            serverUri = serverUri,
            topic = resolvedTopic,
            clientId = base.clientId.substringBefore('-', base.clientId) + "-" + UUID.randomUUID().toString().take(8),
            username = username?.takeIf { it.isNotBlank() },
            password = password?.takeIf { it.isNotBlank() }
        )
    }
}

class StreamingSettingsStore private constructor(private val context: Context) {

    private val dataStore = context.applicationContext.streamingSettingsDataStore
    private val defaults = defaultSettings(context)

    val settingsFlow: Flow<StreamingSettings> = dataStore.data.map { prefs ->
        StreamingSettings(
            enabled = prefs[KEY_ENABLED] ?: defaults.enabled,
            host = prefs[KEY_HOST] ?: defaults.host,
            port = prefs[KEY_PORT] ?: defaults.port,
            useTls = prefs[KEY_TLS] ?: defaults.useTls,
            topic = prefs[KEY_TOPIC] ?: defaults.topic,
            username = prefs[KEY_USERNAME]?.let { if (it.isBlank()) null else it }
                ?: defaults.username,
            password = prefs[KEY_PASSWORD]?.let { if (it.isBlank()) null else it }
                ?: defaults.password
        )
    }

    suspend fun update(block: StreamingSettings.() -> StreamingSettings) {
        dataStore.edit { prefs ->
            val current = StreamingSettings(
                enabled = prefs[KEY_ENABLED] ?: defaults.enabled,
                host = prefs[KEY_HOST] ?: defaults.host,
                port = prefs[KEY_PORT] ?: defaults.port,
                useTls = prefs[KEY_TLS] ?: defaults.useTls,
                topic = prefs[KEY_TOPIC] ?: defaults.topic,
                username = prefs[KEY_USERNAME]?.let { if (it.isBlank()) null else it }
                    ?: defaults.username,
                password = prefs[KEY_PASSWORD]?.let { if (it.isBlank()) null else it }
                    ?: defaults.password
            )
            val updated = current.block()
            prefs[KEY_ENABLED] = updated.enabled
            prefs[KEY_HOST] = updated.host
            prefs[KEY_PORT] = updated.port
            prefs[KEY_TLS] = updated.useTls
            prefs[KEY_TOPIC] = updated.topic
            prefs[KEY_USERNAME] = updated.username ?: ""
            prefs[KEY_PASSWORD] = updated.password ?: ""
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        update { copy(enabled = enabled) }
    }

    suspend fun setBroker(host: String, port: Int, useTls: Boolean) {
        update { copy(host = host, port = port, useTls = useTls) }
    }

    suspend fun setCredentials(username: String?, password: String?) {
        update { copy(username = username, password = password) }
    }

    suspend fun setTopic(topic: String) {
        update { copy(topic = topic) }
    }

    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("enabled")
        private val KEY_HOST = stringPreferencesKey("host")
        private val KEY_PORT = intPreferencesKey("port")
        private val KEY_TLS = booleanPreferencesKey("tls")
        private val KEY_TOPIC = stringPreferencesKey("topic")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_PASSWORD = stringPreferencesKey("password")

        fun getInstance(context: Context): StreamingSettingsStore =
            StreamingSettingsStore(context.applicationContext)

        fun defaultSettings(context: Context): StreamingSettings {
            val resources = context.applicationContext.resources
            return StreamingSettings(
                enabled = false,
                host = BuildConfig.MQTT_HOST,
                port = BuildConfig.MQTT_PORT,
                useTls = false,
                topic = resources.getString(R.string.default_mqtt_topic),
                username = resources.getString(R.string.default_mqtt_username).ifBlank { null },
                password = resources.getString(R.string.default_mqtt_password).ifBlank { null }
            )
        }
    }
}