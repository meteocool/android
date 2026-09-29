package com.meteocool.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

/**
 * One barometer reading in hPa, sent along with a registration. Returns -1
 * when the device has no barometer or does not answer within two seconds, so
 * a missing reading never holds up the registration. Concurrent callers share
 * one reading.
 */
class PressureReader(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val inFlight = AtomicReference<Deferred<Float>?>(null)

    suspend fun read(): Float {
        val manager = sensorManager ?: return -1f
        val barometer = sensor ?: return -1f
        val reading = inFlight.get() ?: start(manager, barometer)
        return withTimeoutOrNull(2_000) { reading.await() } ?: -1f
    }

    private fun start(manager: SensorManager, barometer: Sensor): Deferred<Float> {
        val result = CompletableDeferred<Float>()
        if (!inFlight.compareAndSet(null, result)) return inFlight.get() ?: result
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                manager.unregisterListener(this)
                inFlight.compareAndSet(result, null)
                result.complete(event.values.firstOrNull() ?: -1f)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (!manager.registerListener(listener, barometer, SensorManager.SENSOR_DELAY_NORMAL)) {
            inFlight.compareAndSet(result, null)
            result.complete(-1f)
        }
        // A sensor that never answers must not pin the listener forever.
        result.invokeOnCompletion { manager.unregisterListener(listener) }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (result.complete(-1f)) inFlight.compareAndSet(result, null)
        }, 2_000)
        return result
    }
}
