package com.example.nine_scholars_king_test

internal data class SigningTimeReading(
    val deviceMillis: Long,
    val signingMillis: Long,
    val usesServerTime: Boolean
) {
    val timestamp: String get() = (signingMillis / 1000).toString()
    val offsetSeconds: Long get() = (signingMillis - deviceMillis) / 1000
}

/** A calibration is process-local and advances with monotonic time, independent of wall-clock edits. */
internal class SigningClock(
    private val wallMillis: () -> Long,
    private val elapsedMillis: () -> Long
) {
    private data class Calibration(val serverAtReceipt: Long, val receivedAt: Long)
    @Volatile private var calibration: Calibration? = null

    fun read(): SigningTimeReading {
        val device = wallMillis()
        val sample = calibration ?: return SigningTimeReading(device, device, false)
        val age = elapsedMillis() - sample.receivedAt
        return if (age in 0..MAX_CALIBRATION_AGE) {
            SigningTimeReading(device, sample.serverAtReceipt + age, true)
        } else SigningTimeReading(device, device, false)
    }

    fun calibrate(serverDateMillis: Long, startedAt: Long, receivedAt: Long): SigningTimeReading {
        require(serverDateMillis / 1000 in 1_000_000_000L..9_999_999_999L)
        val roundTrip = receivedAt - startedAt
        require(roundTrip in 0..MAX_ROUND_TRIP)
        calibration = Calibration(serverDateMillis + roundTrip / 2, receivedAt)
        return read()
    }

    companion object {
        const val MAX_CALIBRATION_AGE = 6 * 60 * 60 * 1000L
        const val MAX_ROUND_TRIP = 15_000L
    }
}

internal data class SigningDiagnostic(
    val method: String,
    val requestUrl: String,
    val time: SigningTimeReading
)
