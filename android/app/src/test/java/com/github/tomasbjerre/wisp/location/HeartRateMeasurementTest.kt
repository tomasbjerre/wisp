package com.github.tomasbjerre.wisp.location

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Verifies specs/heart-rate.md#reading-a-measurement. */
class HeartRateMeasurementTest {
    @Test
    fun `an 8-bit value is read when the format flag is clear`() {
        assertThat(HeartRateMeasurement.parse(byteArrayOf(0x00, 72))).isEqualTo(72)
    }

    @Test
    fun `an 8-bit value above 127 is unsigned`() {
        assertThat(HeartRateMeasurement.parse(byteArrayOf(0x00, 0xB4.toByte()))).isEqualTo(180)
    }

    @Test
    fun `a 16-bit little-endian value is read when the format flag is set`() {
        // 0x0102 = 258
        assertThat(HeartRateMeasurement.parse(byteArrayOf(0x01, 0x02, 0x01))).isEqualTo(258)
    }

    @Test
    fun `other flag bits, like sensor contact, do not change the value`() {
        assertThat(HeartRateMeasurement.parse(byteArrayOf(0x16, 65))).isEqualTo(65)
    }

    @Test
    fun `a reading of zero is not a valid heart rate`() {
        assertThat(HeartRateMeasurement.parse(byteArrayOf(0x00, 0))).isNull()
    }

    @Test
    fun `a value too short for its format is ignored`() {
        assertThat(HeartRateMeasurement.parse(byteArrayOf())).isNull()
        assertThat(HeartRateMeasurement.parse(byteArrayOf(0x00))).isNull()
        assertThat(HeartRateMeasurement.parse(byteArrayOf(0x01, 0x50))).isNull()
    }
}
