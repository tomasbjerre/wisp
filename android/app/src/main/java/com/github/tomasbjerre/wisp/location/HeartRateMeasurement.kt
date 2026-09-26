package com.github.tomasbjerre.wisp.location

/**
 * Decodes the standard BLE Heart Rate Measurement characteristic (0x2A37) — see
 * specs/heart-rate.md#reading-a-measurement. Pure, so it's unit-testable without Bluetooth.
 */
object HeartRateMeasurement {
    private const val FORMAT_UINT16_FLAG = 0x01

    /** The beats per minute in [value], or null if it isn't a valid reading. */
    fun parse(value: ByteArray): Int? {
        if (value.isEmpty()) return null
        val isUint16 = value[0].toInt() and FORMAT_UINT16_FLAG != 0
        val bpm =
            if (isUint16) {
                if (value.size < 3) return null
                (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
            } else {
                if (value.size < 2) return null
                value[1].toInt() and 0xFF
            }
        return bpm.takeIf { it > 0 }
    }
}
