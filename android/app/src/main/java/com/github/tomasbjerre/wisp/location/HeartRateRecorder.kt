package com.github.tomasbjerre.wisp.location

/**
 * Pure decision logic for a session's heart rate — see specs/heart-rate.md#recording:
 * the current reading (lost after [STALE_AFTER_MILLIS] without a new one) and the
 * session's maximum, excluding paused time. Mirrors [StepRecorder]: no Android types,
 * no I/O. One instance covers exactly one recording session.
 */
class HeartRateRecorder {
    private var latestBpm: Int? = null
    private var latestAtMillis = 0L
    private var isPaused = false
    private var max: Int? = null

    /** Highest reading so far, or null if none was ever received. */
    val maxBpm: Int? get() = max

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
        // Whatever was current before the pause is long stale by now.
        latestBpm = null
    }

    fun onReading(
        bpm: Int,
        nowMillis: Long,
    ) {
        if (isPaused) return
        latestBpm = bpm
        latestAtMillis = nowMillis
        max = maxOf(max ?: bpm, bpm)
    }

    /** The current heart rate, or null if there's none or the last reading is stale. */
    fun currentBpm(nowMillis: Long): Int? = latestBpm?.takeIf { nowMillis - latestAtMillis <= STALE_AFTER_MILLIS }

    companion object {
        const val STALE_AFTER_MILLIS = 10_000L
    }
}
