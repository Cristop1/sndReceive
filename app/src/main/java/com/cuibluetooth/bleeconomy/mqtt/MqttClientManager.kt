package com.cuibluetooth.bleeconomy.mqtt

import android.content.Context
import android.util.Log
import com.cuibluetooth.bleeconomy.BuildConfig
import com.cuibluetooth.bleeconomy.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed class ConnectionStatus {
    object Disconnected : ConnectionStatus()
    object Connecting : ConnectionStatus()
    data class Connected(val serverUri: String) : ConnectionStatus()
    data class Error(val throwable: Throwable?) : ConnectionStatus()
}

data class MqttConfig(
    val serverUri: String,
    val clientId: String,
    val topic: String,
    val username: String? = null,
    val password: String? = null,
    val keepAliveSeconds: Int = 30
)

class MqttClientManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val clientFactory: (Context, MqttConfig) -> ManagedMqttClient = { ctx, config ->
        AndroidMqttClient(MqttAndroidClient(ctx, config.serverUri, config.clientId))
    },
    private val defaultConfigProvider: () -> MqttConfig = { createDefaultConfig(context) }
) {

    private val mutex = Mutex()
    private var currentClient: ManagedMqttClient? = null
    private var currentConfig: MqttConfig = defaultConfig()
    private var reconnectJob: Job? = null

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    fun defaultConfig(): MqttConfig = defaultConfigProvider()

    fun connect(config: MqttConfig = currentConfig) {
        currentConfig = config
        startReconnectLoop(resetAttempt = true)
    }

    fun disconnect() {
        reconnectJob?.cancel()
        scope.launch {
            mutex.withLock {
                currentClient?.disconnect()
                currentClient?.release()
                currentClient = null
                _status.value = ConnectionStatus.Disconnected
            }
        }
    }

    fun publish(topic: String, payload: ByteArray, qos: Int = 1, retained: Boolean = false): Boolean {
        val client = currentClient
        if (client?.isConnected != true) {
            return false
        }
        return try {
            client.publish(topic, payload, qos, retained)
            true
        } catch (ex: MqttException) {
            Log.w(TAG, "Failed to publish", ex)
            false
        }
    }

    private fun startReconnectLoop(resetAttempt: Boolean) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var attempt = if (resetAttempt) 0 else 1
            while (isActive) {
                _status.value = ConnectionStatus.Connecting
                val result = runCatching { connectOnce(currentConfig) }
                if (result.isSuccess) {
                    _status.value = ConnectionStatus.Connected(currentConfig.serverUri)
                    return@launch
                }
                val throwable = result.exceptionOrNull()
                Log.w(TAG, "MQTT connection attempt failed", throwable)
                _status.value = ConnectionStatus.Error(throwable)
                val delayDuration = calculateBackoff(attempt)
                attempt = (attempt + 1).coerceAtMost(MAX_BACKOFF_POWER)
                delay(delayDuration)
            }
        }
    }

    private suspend fun connectOnce(config: MqttConfig) {
        mutex.withLock {
            currentClient?.disconnect()
            currentClient?.release()
            currentClient = null
            val client = clientFactory(context.applicationContext, config)
            client.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Log.i(TAG, "MQTT connect complete: reconnect=$reconnect uri=$serverURI")
                    _status.value = ConnectionStatus.Connected(serverURI ?: config.serverUri)
                }

                override fun connectionLost(cause: Throwable?) {
                    Log.w(TAG, "MQTT connection lost", cause)
                    _status.value = ConnectionStatus.Error(cause)
                    startReconnectLoop(resetAttempt = false)
                }

                override fun messageArrived(topic: String?, message: org.eclipse.paho.client.mqttv3.MqttMessage?) = Unit

                override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
            })
            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = false
                isCleanSession = true
                keepAliveInterval = config.keepAliveSeconds
                connectionTimeout = 10
                config.username?.let { userName = it }
                config.password?.let { password = it.toCharArray() }
            }
            try {
                suspendCancellableConnect(client, options)
                currentClient = client
            } catch (throwable: Throwable) {
                client.release()
                throw throwable
            }
        }
    }

    private suspend fun suspendCancellableConnect(client: ManagedMqttClient, options: MqttConnectOptions) {
        suspendCancellableCoroutine { cont ->
            client.connect(options, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: org.eclipse.paho.client.mqttv3.IMqttToken?) {
                    cont.resume(Unit)
                }

                override fun onFailure(
                    asyncActionToken: org.eclipse.paho.client.mqttv3.IMqttToken?,
                    exception: Throwable?
                ) {
                    val error = exception ?: MqttException(MqttException.REASON_CODE_CONNECTION_LOST.toInt())
                    cont.resumeWithException(error)
                }
            })
            cont.invokeOnCancellation {
                try {
                    client.disconnect()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun calculateBackoff(attempt: Int): Long {
        val base = BASE_BACKOFF.inWholeMilliseconds
        val multiplier = 2.0.pow(attempt.toDouble()).toLong().coerceAtLeast(1L)
        val capped = (base * multiplier).coerceAtMost(MAX_BACKOFF.inWholeMilliseconds)
        return capped
    }

    companion object {
        private const val TAG = "MqttClientManager"
        private val BASE_BACKOFF: Duration = 1.seconds
        private val MAX_BACKOFF: Duration = 30.seconds
        private const val MAX_BACKOFF_POWER = 6

        private fun createDefaultConfig(context: Context): MqttConfig {
            val resources = context.applicationContext.resources
            val topic = resources.getString(R.string.default_mqtt_topic)
            val username = resources.getString(R.string.default_mqtt_username).takeIf { it.isNotBlank() }
            val password = resources.getString(R.string.default_mqtt_password).takeIf { it.isNotBlank() }
            val serverUri = "${BuildConfig.MQTT_SCHEME}://${BuildConfig.MQTT_HOST}:${BuildConfig.MQTT_PORT}"
            val clientId = "${BuildConfig.MQTT_CLIENT_ID_PREFIX}-${UUID.randomUUID().toString().take(8)}"
            return MqttConfig(serverUri = serverUri, clientId = clientId, topic = topic, username = username, password = password)
        }
    }
}

interface ManagedMqttClient {
    val isConnected: Boolean
    fun connect(options: MqttConnectOptions, listener: IMqttActionListener)
    fun disconnect()
    fun publish(topic: String, payload: ByteArray, qos: Int, retained: Boolean)
    fun setCallback(callback: MqttCallbackExtended)
    fun release()
}

private class AndroidMqttClient(private val delegate: MqttAndroidClient) : ManagedMqttClient {
    override val isConnected: Boolean
        get() = delegate.isConnected

    override fun connect(options: MqttConnectOptions, listener: IMqttActionListener) {
        delegate.connect(options, null, listener)
    }

    override fun disconnect() {
        try {
            if (delegate.isConnected) {
                delegate.disconnect()
            }
        } catch (ex: MqttException) {
            Log.w("AndroidMqttClient", "Error while disconnecting", ex)
        }
    }

    override fun publish(topic: String, payload: ByteArray, qos: Int, retained: Boolean) {
        delegate.publish(topic, payload, qos, retained)
    }

    override fun setCallback(callback: MqttCallbackExtended) {
        delegate.setCallback(callback)
    }

    override fun release() {
        delegate.unregisterResources()
        delegate.close()
    }
}