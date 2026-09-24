package com.github.tomasbjerre.wisp.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Thin wrapper around SensorManager's step-counter sensor. See
 * specs/tracking.md#step-count — not every device has this sensor, and the
 * platform may withhold events without the Activity Recognition permission on
 * versions that require it; either way [start] never crashes, it just never
 * calls back.
 */
class StepCounterTracker(
    context: Context,
) {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private var listener: SensorEventListener? = null

    fun start(onStepCounterChanged: (Long) -> Unit) {
        stop()
        val availableSensor = sensor ?: return
        val newListener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    onStepCounterChanged(event.values[0].toLong())
                }

                override fun onAccuracyChanged(
                    sensor: Sensor?,
                    accuracy: Int,
                ) = Unit
            }
        listener = newListener
        sensorManager?.registerListener(newListener, availableSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        listener?.let { sensorManager?.unregisterListener(it) }
        listener = null
    }
}
