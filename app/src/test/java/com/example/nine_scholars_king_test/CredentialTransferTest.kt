package com.example.nine_scholars_king_test

import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.*
import org.junit.Test

class CredentialTransferTest {
    @Test
    fun urlModePreservesExamRouteAndEncodesCredentialValues() {
        val credentials = HostCredentials("app id&=中文", "secret+/#?&=\"\\\n")
        val exam = ExamConfig.exams.first()
        val url = CredentialTransfer.url(exam, CredentialTransferMode.URL, credentials)
        val uri = URI(url)
        assertNull(uri.query)
        assertEquals("/hskmock_webview/index.html", uri.path)
        val params = uri.rawFragment.substringAfter('?').split('&').associate {
            val pair = it.split('=', limit = 2)
            pair[0] to URLDecoder.decode(pair[1], "UTF-8")
        }
        assertEquals(exam.examinationId, params["examination_id"])
        assertEquals(exam.token, params["exam_token"])
        assertEquals(credentials.appId, params["app_id"])
        assertEquals(credentials.appKey, params["app_key"])
    }

    @Test
    fun otherModesDoNotAddCredentialsToUrl() {
        val exam = ExamConfig.exams.first()
        listOf(CredentialTransferMode.JAVASCRIPT, CredentialTransferMode.NATIVE).forEach {
            assertEquals(exam.url, CredentialTransfer.url(exam, it, HostCredentials("id", "key")))
        }
    }

    @Test
    fun unknownStoredModeDefaultsToNativeInterface() {
        assertEquals(CredentialTransferMode.NATIVE, CredentialTransferMode.from(null))
        assertEquals(CredentialTransferMode.NATIVE, CredentialTransferMode.from(""))
        assertEquals(CredentialTransferMode.NATIVE, CredentialTransferMode.from("unknown"))
    }
}
