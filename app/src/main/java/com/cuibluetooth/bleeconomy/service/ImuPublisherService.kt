package com.cuibluetooth.bleeconomy.service

import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import com.cuibluetooth.bleeconomy.mqtt.MqttClientManager
import com.cuibluetooth.bleeconomy.mqtt.StreamingSettingsStore
import com.cuibluetooth.bleeconomy.sensor.ImuSensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ImuPublisherService : LifecycleService() {

    private val serviceJob: Job = SupervisorJob()
    private val serviceScope: CoroutineScope = CoroutineScope(serviceJob + Dispatchers.Default)
    private lateinit var mqttManager: MqttClientManager
    private lateinit var imuSensorManager: ImuSensorManager
    private lateinit var imuPublisher: ImuPublisher
    private lateinit var settingsStore: StreamingSettingsStore
    private var settingsJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        settingsStore = StreamingSettingsStore.getInstance(applicationContext)
        imuSensorManager = ImuSensorManager(this)
        mqttManager = MqttClientManager(this, serviceScope)
        imuPublisher = ImuPublisher(serviceScope, imuSensorManager, mqttManager)
        settingsJob = serviceScope.launch {
            settingsStore.settingsFlow.collect { settings ->
                imuPublisher.updateSettings(settings)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        settingsJob?.cancel()
        imuPublisher.stop()
        mqttManager.disconnect()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}