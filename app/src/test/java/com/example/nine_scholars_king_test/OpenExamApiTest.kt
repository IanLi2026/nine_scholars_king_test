package com.example.nine_scholars_king_test

import java.net.URI
import java.net.URLDecoder
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OpenExamApiTest {
    @Test fun queriesMatchHtmlAndEncodeExternalUserId() {
        val form = OpenExamForm(externalUserId = "用户 a+&/", levelId = "2", paperKind = "official")
        assertEquals("/mock/open/levels?paper_kind=official", OpenExamRequest.from(OpenExamAction.LEVELS, form).path)
        val encoded = "%E7%94%A8%E6%88%B7%20a%2B%26%2F"
        assertEquals("/mock/open/levels/2/papers?external_user_id=$encoded", OpenExamRequest.from(OpenExamAction.PAPERS, form).path)
        assertEquals("/mock/open/tenant/quota?external_user_id=$encoded", OpenExamRequest.from(OpenExamAction.QUOTA, form).path)
    }

    @Test fun mockAndOfficialCreateBodiesPreserveRequestIdAndOmitOfficialPaper() {
        val form = OpenExamForm(externalUserId = "用户\"甲", requestId = "retry-same-id")
        val mock = JSONObject(OpenExamRequest.from(OpenExamAction.CREATE, form).body)
        assertEquals("用户\"甲", mock.getString("external_user_id"))
        assertEquals(24, mock.getInt("paper_id"))
        assertEquals("retry-same-id", mock.getString("request_id"))
        val official = JSONObject(form.copy(paperKind = "official", paperId = "ignored").createBody())
        assertFalse(official.has("paper_id"))
        assertEquals("official", official.getString("paper_kind"))
        assertEquals("retry-same-id", official.getString("request_id"))
    }

    @Test fun invalidConfigurationCannotReachNetwork() {
        listOf(OpenExamForm(levelId = "0"), OpenExamForm(paperId = "abc"),
            OpenExamForm(requestId = ""), OpenExamForm(externalUserId = ""),
            OpenExamForm(base = "http://example.com"), OpenExamForm(base = "https://example.com/path")
        ).forEach { form ->
            assertTrue(runCatching { OpenExamRequest.from(OpenExamAction.CREATE, form) }.isFailure)
        }
        assertNotEquals(OpenExamForm.newRequestId(), OpenExamForm.newRequestId())
    }

    @Test fun createdSessionPreservesLongIdAndEncodesUrlWithOfficialKind() {
        val response = OpenExamResponse(200, """{"errcode":0,"data":{"examination_id":2032384598771499116,"exam_token":"a+&=?汉"}}""")
        val exam = response.createdExam(OpenExamForm(paperKind = "official"))
        assertEquals("2032384598771499116", exam.examinationId)
        val fragment = URI(exam.url).rawFragment
        assertTrue(fragment.startsWith("/exam?"))
        val params = fragment.substringAfter('?').split('&').associate {
            val pair = it.split('=', limit = 2)
            pair[0] to URLDecoder.decode(pair[1], "UTF-8")
        }
        assertEquals("a+&=?汉", params["exam_token"])
        assertEquals("official", params["paper_kind"])
    }

    @Test fun failedOrIncompleteResponsesNeverProduceSession() {
        listOf(OpenExamResponse(401, """{"errcode":0,"data":{}}"""),
            OpenExamResponse(200, """{"errcode":6,"errmsg":"rejected"}"""),
            OpenExamResponse(200, "not json"),
            OpenExamResponse(200, """{"errcode":0,"data":{"examination_id":"1"}}"""),
            OpenExamResponse(200, """{"errcode":0,"data":{"examination_id":null,"exam_token":"a"}}""")
        ).forEach { assertTrue(runCatching { it.createdExam(OpenExamForm()) }.isFailure) }
    }

    @Test fun catalogSigningUsesExactSameSixLinesWhileBridgeSignerStaysRestricted() {
        val credentials = HostCredentials("fixture", "secret")
        val request = OpenExamRequest.from(OpenExamAction.CREATE, OpenExamForm(requestId = "same"))
        val headers = ExamSigner(credentials, allowCatalog = true).sign(request.method, request.path,
            request.body, "1793188800", "AbCdEf0123456789")
        val source = "POST\n/mock/open/exams\n${request.body}\n1793188800\nAbCdEf0123456789\nsecret"
        val expected = java.security.MessageDigest.getInstance("MD5").digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it.toInt() and 255) }
        assertEquals(expected, headers.sign)
        assertTrue(runCatching { ExamSigner(credentials).sign("POST", request.path, request.body) }.isFailure)
    }
    @Test fun quotaMapsResponseToReadableSummaryRows() {
        val rows = OpenExamResponse(200,
            """{"errcode":0,"data":{"tenant":{"limit":100,"used":20,"remaining":80},"user":{"limit":5,"used":2,"remaining":3},"levels":[{"level_id":31,"level_name":"New HSK1","limit":30,"used":1,"remaining":29}]}}""")
            .quotaRows()
        assertEquals(3, rows.size)
        assertEquals("80", rows[0].remaining)
        assertEquals("3", rows[1].remaining)
        assertEquals("New HSK1", rows[2].title)
        assertEquals("29", rows[2].remaining)
    }

    @Test fun catalogUsesServerIdsAndNamesIncludingNewHskLevels() {
        val levels = OpenExamResponse(200,
            """{"errcode":0,"data":{"levels":[{"level_id":31,"level_name":"New HSK1"},{"level_id":32,"level_name":"New HSK2"}]}}""")
            .catalog(OpenExamAction.LEVELS)
        assertEquals(listOf("31", "32"), levels.map { it.id })
        assertEquals("New HSK1", levels.first().name)
        val papers = OpenExamResponse(200,
            """{"errcode":0,"data":{"papers":[{"paper_id":2032384598771499116,"name":"模拟卷（一）"}]}}""")
            .catalog(OpenExamAction.PAPERS)
        assertEquals("2032384598771499116", papers.first().id)
        assertEquals("模拟卷（一）", papers.first().name)
    }

    @Test fun catalogDistinguishesEmptyListFromMalformedAndFailedResponses() {
        assertTrue(OpenExamResponse(200, """{"errcode":0,"data":{"levels":[]}}""")
            .catalog(OpenExamAction.LEVELS).isEmpty())
        listOf(
            OpenExamResponse(200, """{"errcode":0,"data":{}}"""),
            OpenExamResponse(200, """{"errcode":0,"data":{"levels":[{"level_id":0}]}}"""),
            OpenExamResponse(200, """{"errcode":6,"errmsg":"无权限"}"""),
            OpenExamResponse(500, "invalid json")
        ).forEach { assertTrue(runCatching { it.catalog(OpenExamAction.LEVELS) }.isFailure) }
    }
}
