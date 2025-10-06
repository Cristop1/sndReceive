package com.cuibluetooth.bleeconomy.mqtt

import android.content.Context
import android.test.mock.MockContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MqttClientManagerTest {

    @Test
    fun reconnectsUntilSuccess() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher + SupervisorJob())
        val attempts = mutableListOf<Unit>()
        val outcomes = ArrayDeque(listOf(false, false, true))
        val manager = MqttClientManager(
            context = object : MockContext() {
                override fun getApplicationContext(): Context = this
            },
            scope = scope,
            clientFactory = { _, _ -> FakeMqttClient(outcomes) { attempts.add(Unit) } },
            defaultConfigProvider = { MqttConfig("tcp://localhost:1883", "client", "topic") }
        )

        val states = mutableListOf<ConnectionStatus>()
        val collectJob = scope.launch { manager.status.collect { states.add(it) } }

        manager.connect()
        scope.advanceUntilIdle()

        assertTrue(states.any { it is ConnectionStatus.Error })
        assertTrue(states.any { it is ConnectionStatus.Connected })
        assertEquals(3, attempts.size)

        collectJob.cancel()
        manager.disconnect()
    }

    private class FakeMqttClient(
        private val outcomes: ArrayDeque<Boolean>,
        private val onAttempt: () -> Unit
    ) : ManagedMqttClient {
        private var callback: MqttCallbackExtended? = null
        private var connected = false

        override val isConnected: Boolean
            get() = connected

        override fun connect(options: MqttConnectOptions, listener: IMqttActionListener) {
            onAttempt()
            val succeed = outcomes.removeFirstOrNull() ?: true
            if (succeed) {
                connected = true
                listener.onSuccess(null)
                callback?.connectComplete(false, "tcp://localhost:1883")
            } else {
                connected = false
                listener.onFailure(null, MqttException(MqttException.REASON_CODE_SERVER_CONNECT_ERROR))
            }
        }

        override fun disconnect() {
            connected = false
        }

        override fun publish(topic: String, payload: ByteArray, qos: Int, retained: Boolean) {
            if (!connected) throw MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED)
        }

        override fun setCallback(callback: MqttCallbackExtended) {
            this.callback = callback
        }

        override fun release() {
            connected = false
        }
    }
}