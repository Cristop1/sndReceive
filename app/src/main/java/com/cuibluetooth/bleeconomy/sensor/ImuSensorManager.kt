package com.cuibluetooth.bleeconomy.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.serializer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Timestamped IMU frame containing normalized accelerometer and gyroscope readings.
 */
@kotlinx.serialization.Serializable
data class ImuFrame(
    val timestampMillis: Long,
    val accelerationG: Vector3,
    val angularVelocityRad: Vector3
) {
    companion object {
        fun serializer(): kotlinx.serialization.KSerializer<ImuFrame> = serializer()
    }
}


@kotlinx.serialization.Serializable
data class Vector3(val x: Float, val y: Float, val z: Float)

/**
 * Registers accelerometer and gyroscope listeners, normalizes readings and exposes them as a hot [kotlinx.coroutines.flow.Flow].
 */
class ImuSensorManager @JvmOverloads constructor(
    context: Context,
    private val throttleWindow: Duration = 20.milliseconds,
    private val sensorDelay: Int = SensorManager.SENSOR_DELAY_GAME,
    private val timeSource: () -> Long = { SystemClock.elapsedRealtimeNanos() }
) {

    private val sensorManager: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val running = AtomicBoolean(false)

    private val frameAggregator = ImuFrameAggregator(throttleWindow, timeSource, { frame ->
        _frames.tryEmit(frame)
    })

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> frameAggregator.updateAccelerometer(event.timestamp, event.values)
                Sensor.TYPE_GYROSCOPE -> frameAggregator.updateGyroscope(event.timestamp, event.values)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    private val _frames = kotlinx.coroutines.flow.MutableSharedFlow<ImuFrame>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    val frames: kotlinx.coroutines.flow.SharedFlow<ImuFrame> = _frames

    fun start(): Boolean {
        if (running.compareAndSet(false, true)) {
            val manager = sensorManager
            val accel = accelerometer
            val gyro = gyroscope
            if (manager == null || accel == null || gyro == null) {
                Log.w(TAG, "IMU sensors unavailable; accelerometer=$accel gyroscope=$gyro manager=$manager")
                running.set(false)
                return false
            }
            frameAggregator.reset()
            manager.registerListener(listener, accel, sensorDelay)
            manager.registerListener(listener, gyro, sensorDelay)
            Log.i(TAG, "IMU sensor listeners registered with delay=$sensorDelay")
        }
        return running.get()
    }

    fun stop() {
        if (running.compareAndSet(true, false)) {
            sensorManager?.unregisterListener(listener)
            frameAggregator.reset()
            Log.i(TAG, "IMU sensor listeners unregistered")
        }
    }

    companion object {
        private const val TAG = "ImuSensorManager"
    }
}

internal class ImuFrameAggregator(
    private val throttleWindow: Duration,
    private val timeSource: () -> Long,
    private val onFrame: (ImuFrame) -> Unit,
    private val gravity: Float = 9.80665f
) {
    private var lastAccel: Vector3? = null
    private var lastGyro: Vector3? = null
    private var lastEmissionNanos: Long = Long.MIN_VALUE

    fun updateAccelerometer(timestampNanos: Long, values: FloatArray) {
        lastAccel = Vector3(
            normalizeAcceleration(values.getOrNull(0) ?: 0f),
            normalizeAcceleration(values.getOrNull(1) ?: 0f),
            normalizeAcceleration(values.getOrNull(2) ?: 0f)
        )
        maybeEmit(timestampNanos)
    }

    fun updateGyroscope(timestampNanos: Long, values: FloatArray) {
        lastGyro = Vector3(
            values.getOrNull(0) ?: 0f,
            values.getOrNull(1) ?: 0f,
            values.getOrNull(2) ?: 0f
        )
        maybeEmit(timestampNanos)
    }

    fun reset() {
        lastAccel = null
        lastGyro = null
        lastEmissionNanos = Long.MIN_VALUE
    }

    private fun normalizeAcceleration(value: Float): Float = value / gravity

    private fun maybeEmit(eventTimestamp: Long) {
        val accel = lastAccel ?: return
        val gyro = lastGyro ?: return
        val now = timeSource()
        val minInterval = max(0, throttleWindow.inWholeNanoseconds)
        if (lastEmissionNanos != Long.MIN_VALUE && now - lastEmissionNanos < minInterval) {
            return
        }
        lastEmissionNanos = now
        val timestampMillis = TimeUnit.NANOSECONDS.toMillis(eventTimestamp)
        onFrame(ImuFrame(timestampMillis, accel, gyro))
    }
}