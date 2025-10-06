package com.cuibluetooth.bleeconomy.service

import android.util.Log
import com.cuibluetooth.bleeconomy.mqtt.MqttClientManager
import com.cuibluetooth.bleeconomy.mqtt.MqttConfig
import com.cuibluetooth.bleeconomy.mqtt.StreamingSettings
import com.cuibluetooth.bleeconomy.sensor.ImuFrame
import com.cuibluetooth.bleeconomy.sensor.ImuSensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.pow
import kotlin.text.Charsets

class ImuPublisher(
    private val scope: CoroutineScope,
    private val sensorManager: ImuSensorManager,
    private val mqttClientManager: MqttClientManager,
    private val json: Json = Json { encodeDefaults = true }
) {

    private var collectorJob: Job? = null
    private var publisherJob: Job? = null
    private var frameChannel: Channel<ImuFrame>? = null
    private var currentConfig: MqttConfig? = null

    fun updateSettings(settings: StreamingSettings) {
        if (!settings.enabled) {
            stop()
            mqttClientManager.disconnect()
            return
        }
        stop()
        start(settings)
    }

    fun stop() {
        collectorJob?.cancel()
        collectorJob = null
        publisherJob?.cancel()
        publisherJob = null
        frameChannel?.close()
        frameChannel = null
        sensorManager.stop()
    }

    private fun start(settings: StreamingSettings) {
        val baseConfig = mqttClientManager.defaultConfig()
        val config = settings.toMqttConfig(baseConfig)
        currentConfig = config
        val channel = Channel<ImuFrame>(BUFFER_CAPACITY, BufferOverflow.DROP_OLDEST)
        frameChannel = channel
        if (!sensorManager.start()) {
            Log.w(TAG, "IMU sensors unavailable; skipping streaming")
            channel.close()
            return
        }
        mqttClientManager.connect(config)
        collectorJob = scope.launch {
            sensorManager.frames.collect { frame ->
                channel.trySend(frame)
            }
        }
        publisherJob = scope.launch {
            publishLoop(channel, config)
        }
    }

    private suspend fun publishLoop(channel: Channel<ImuFrame>, config: MqttConfig) {
        var attempt = 0
        for (frame in channel) {
            val payload = json.encodeToString(ImuFrame.serializer(), frame).toByteArray(Charsets.UTF_8)
            while (scope.isActive) {
                val published = mqttClientManager.publish(config.topic, payload)
                if (published) {
                    attempt = 0
                    break
                }
                attempt = (attempt + 1).coerceAtMost(MAX_PUBLISH_BACKOFF_POWER)
                val delayMillis = calculateBackoff(attempt)
                delay(delayMillis)
            }
        }
    }

    private fun calculateBackoff(attempt: Int): Long {
        val multiplier = 2.0.pow(attempt.toDouble()).toLong().coerceAtLeast(1L)
        val capped = (BASE_PUBLISH_BACKOFF_MS * multiplier).coerceAtMost(MAX_PUBLISH_BACKOFF_MS)
        return capped
    }

    companion object {
        private const val TAG = "ImuPublisher"
        private const val BUFFER_CAPACITY = 64
        private const val BASE_PUBLISH_BACKOFF_MS = 250L
        private const val MAX_PUBLISH_BACKOFF_MS = 10_000L
        private const val MAX_PUBLISH_BACKOFF_POWER = 6
    }
}