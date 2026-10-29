package com.example.nine_scholars_king_test

import org.junit.Assert.*
import org.junit.Test

class SigningClockTest {
    private val server = 1_793_188_800_000L
    private var device = server + 86_400_000L
    private var elapsed = 5000L
    private val clock = SigningClock({ device }, { elapsed })

    @Test
    fun incorrectDeviceDateUsesCalibratedServerSeconds() {
        val reading = clock.calibrate(server, elapsed, elapsed)
        assertEquals("1793188800", reading.timestamp)
        assertEquals(-86_400L, reading.offsetSeconds)
        assertTrue(reading.usesServerTime)
        val signature = ExamSigner(HostCredentials("test-app", "test-secret")).sign("GET",
            "/mock/open/exams/2032384598771499116/paper?exam_token=a%2Bb&z=2&a=1", "",
            reading.timestamp, "AbCdEf0123456789")
        assertEquals(reading.timestamp, signature.timestamp)
        assertEquals("C33574B9BBED4FA4AE84D2D5F48D6310", signature.sign)
    }

    @Test
    fun wallClockChangesDoNotDisturbCalibratedTime() {
        clock.calibrate(server, elapsed, elapsed)
        device -= 3600_000L
        elapsed += 10_000L
        assertEquals(server + 10_000L, clock.read().signingMillis)
        assertEquals(device, clock.read().deviceMillis)
    }

    @Test
    fun compensatesForHalfTheRoundTrip() {
        clock.calibrate(server, elapsed - 200, elapsed)
        assertEquals(server + 100, clock.read().signingMillis)
    }

    @Test
    fun expiredCalibrationFallsBackToDeviceAndCanBeRefreshed() {
        clock.calibrate(server, elapsed, elapsed)
        elapsed += SigningClock.MAX_CALIBRATION_AGE + 1
        assertFalse(clock.read().usesServerTime)
        assertEquals(device, clock.read().signingMillis)
        clock.calibrate(server + SigningClock.MAX_CALIBRATION_AGE, elapsed, elapsed)
        assertTrue(clock.read().usesServerTime)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSlowTimeSample() {
        clock.calibrate(server, 0, SigningClock.MAX_ROUND_TRIP + 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingServerDate() {
        clock.calibrate(0, elapsed, elapsed)
    }
}
