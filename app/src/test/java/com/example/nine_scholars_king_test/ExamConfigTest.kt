package com.example.nine_scholars_king_test

import org.junit.Test

import org.junit.Assert.*
import java.net.URI

/** Guards the six exam links against truncating IDs or losing their hash route. */
class ExamConfigTest {
    @Test
    fun examLinkPreservesHashRouteAndFullPrecisionId() {
        val expectedIds = listOf(
            "2032384598771499116", "2032384598771499117", "2032384598771499118",
            "2032384598771499119", "2032384598771499120", "2032384598771499107"
        )
        assertEquals((1..6).map { "HSK$it" }, ExamConfig.exams.map { it.title })
        assertEquals(expectedIds, ExamConfig.exams.map { it.examinationId })
        assertEquals(6, ExamConfig.exams.map { it.token }.distinct().size)
        ExamConfig.exams.forEachIndexed { index, exam ->
            val uri = URI(exam.url)
            assertEquals("https", uri.scheme)
            assertEquals("staging-public.hskmock.com", uri.host)
            assertEquals(-1, uri.port)
            assertEquals("/hskmock_webview/index.html", uri.path)
            assertNull(uri.query)
            assertTrue(uri.fragment.startsWith("/exam?"))
            val params = uri.fragment.substringAfter('?').split('&').associate {
                val pair = it.split('=', limit = 2)
                pair[0] to pair[1]
            }
            assertEquals(expectedIds[index], params["examination_id"])
            assertEquals(64, params.getValue("exam_token").length)
            assertEquals("HSK${index + 1}", params["title"])
            assertEquals("mock", params["paper_kind"])
        }
    }
}
