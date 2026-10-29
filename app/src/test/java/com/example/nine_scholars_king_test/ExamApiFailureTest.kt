package com.example.nine_scholars_king_test

import org.junit.Assert.*
import org.junit.Test

class ExamApiFailureTest {
    @Test
    fun timestampAuthenticationFailureIsEligibleForTimeCorrection() {
        val failure = ExamApiFailure(6, "Signature verification failed, Invalid argument timestamp")
        assertTrue(failure.isTimestampFailure)
        assertTrue(failure.displayMessage.contains("接口错误码：6"))
        assertTrue(failure.displayMessage.contains(failure.message))
    }

    @Test
    fun nonTimestampAuthenticationErrorDoesNotTriggerClockCorrection() {
        val failure = ExamApiFailure(6, "Invalid argument appid")
        assertFalse(failure.isTimestampFailure)
        assertTrue(failure.displayMessage.contains("appId / appKey"))
    }

    @Test
    fun unrelatedBusinessErrorDoesNotTriggerClockCorrection() {
        assertFalse(ExamApiFailure(3008, "timestamp in diagnostic text").isTimestampFailure)
    }

    @Test
    fun submittedAndMismatchedTokenErrorsHaveDifferentGuidance() {
        assertTrue(ExamApiFailure(3016, "already submitted").displayMessage.contains("已经交卷"))
        assertTrue(ExamApiFailure(3025, "invalid token").displayMessage.contains("appId 不匹配"))
    }

    @Test
    fun unknownCodeKeepsTheOriginalServerMessage() {
        val failure = ExamApiFailure(9999, "new server error")
        assertTrue(failure.displayMessage.contains("9999"))
        assertTrue(failure.displayMessage.contains("new server error"))
        assertNull(failure.guidance)
    }
}
