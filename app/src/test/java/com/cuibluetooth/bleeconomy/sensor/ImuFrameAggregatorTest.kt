package com.cuibluetooth.bleeconomy.sensor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class ImuFrameAggregatorTest {

    @Test
    fun throttlesRapidSamplesAndNormalizesAcceleration() {
        var fakeTime = 0L
        val frames = mutableListOf<ImuFrame>()
        val aggregator = ImuFrameAggregator(
            throttleWindow = 50.milliseconds,
            timeSource = { fakeTime },
            onFrame = { frames.add(it) }
        )

        aggregator.updateAccelerometer(1_000_000L, floatArrayOf(9.80665f, 0f, 0f))
        aggregator.updateGyroscope(1_000_000L, floatArrayOf(0.1f, 0.2f, 0.3f))

        assertEquals(1, frames.size)
        val first = frames.first()
        assertEquals(1.0f, first.accelerationG.x, 1e-3f)
        assertEquals(0.0f, first.accelerationG.y, 1e-3f)
        assertEquals(0.0f, first.accelerationG.z, 1e-3f)

        aggregator.updateAccelerometer(2_000_000L, floatArrayOf(19.6133f, 0f, 0f))
        aggregator.updateGyroscope(2_000_000L, floatArrayOf(0.4f, 0.5f, 0.6f))
        assertEquals("throttle prevents immediate second emission", 1, frames.size)

        fakeTime += 60_000_000L // advance by 60ms
        aggregator.updateGyroscope(3_000_000L, floatArrayOf(0.7f, 0.8f, 0.9f))
        assertTrue(frames.size >= 2)
        val second = frames.last()
        assertEquals(2.0f, second.accelerationG.x, 1e-3f)
        assertEquals(0.7f, second.angularVelocityRad.x, 1e-3f)
    }
}