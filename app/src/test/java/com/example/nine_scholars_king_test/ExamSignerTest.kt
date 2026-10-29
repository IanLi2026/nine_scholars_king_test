package com.example.nine_scholars_king_test

import org.junit.Assert.*
import org.junit.Test

class ExamSignerTest {
    private val signer = ExamSigner(HostCredentials("test-app", "test-secret"))

    @Test
    fun matchesPublishedGetContractVector() {
        val exampleSigner = ExamSigner(HostCredentials("a1b2c3d4e5f64789",
            "DFHufkk6CBy2RsFYBKm22EBg8uD9Q6LgtRv3tbZ8Bh3ZbHGBNpNwX2mVyhyvXrJc"))
        val headers = exampleSigner.sign("GET",
            "/mock/open/exams/880021/paper?exam_token=7f3c9a2e4b8156d0a1c24e8f90ab12cd", "",
            "1692274431", "VmBJ5egUsr4hh5yR")
        assertEquals("F300D23B6255C9A402AC604AEC720E4E", headers.sign)
    }

    @Test
    fun getIncludesEmptyBodyLineAndPreservesRawQueryOrder() {
        val headers = signer.sign("GET",
            "/mock/open/exams/2032384598771499116/paper?exam_token=a%2Bb&z=2&a=1", "",
            "1793188800", "AbCdEf0123456789")
        assertEquals("test-app", headers.appid)
        assertEquals("1793188800", headers.timestamp)
        assertEquals("AbCdEf0123456789", headers.nonce)
        assertEquals("C33574B9BBED4FA4AE84D2D5F48D6310", headers.sign)
    }

    @Test
    fun postSignsExactUtf8JsonWithoutTrailingNewline() {
        val headers = signer.sign("POST",
            "/mock/open/exams/2032384598771499116/answers?exam_token=token",
            """{"answer":"汉字","anchor_index":12}""", "1793188800", "AbCdEf0123456789")
        assertEquals("04801C85CF7B06B73088696D84703BF7", headers.sign)
        assertNotEquals(headers.sign, signer.sign("POST",
            "/mock/open/exams/2032384598771499116/answers?exam_token=token",
            """{ "answer":"汉字","anchor_index":12}""", "1793188800", "AbCdEf0123456789").sign)
    }

    @Test
    fun generatedTimestampUsesCurrentSecondsAndNonceHasSixteenAlphanumerics() {
        val before = System.currentTimeMillis() / 1000
        val first = signer.sign("GET", "/mock/open/exams/1/paper", "")
        val after = System.currentTimeMillis() / 1000
        assertTrue(first.timestamp.toLong() in before..after)
        assertTrue(first.timestamp.matches(Regex("[0-9]{10}")))
        assertTrue(first.nonce.matches(Regex("[A-Za-z0-9]{16}")))
        assertTrue(first.sign.matches(Regex("[A-F0-9]{32}")))
        assertNotEquals(first.nonce, signer.sign("GET", "/mock/open/exams/1/paper", "").nonce)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMillisecondTimestamp() {
        signer.sign("GET", "/mock/open/exams/1/paper", "", "1793188800000", "AbCdEf0123456789")
    }
}
