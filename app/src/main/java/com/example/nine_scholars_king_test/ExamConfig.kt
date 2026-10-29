package com.example.nine_scholars_king_test

/** Keep the ID and token as strings, including the hash route. */
internal data class ExamEntry(
    val title: String,
    val examinationId: String,
    val token: String,
    val paperKind: String = "mock"
) {
    val url: String
        get() = "${ExamConfig.PAGE_URL}#/exam" +
            "?examination_id=${encode(examinationId)}&exam_token=${encode(token)}" +
            "&title=${encode(title)}&paper_kind=${encode(paperKind)}"

    private fun encode(value: String) = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

internal object ExamConfig {
    const val PAGE_URL = "https://staging-public.hskmock.com/hskmock_webview/index.html"
    const val API_ORIGIN = "https://staging-api.hskmock.com"

    // Generated from ignored local integration settings; editable in native settings.
    val defaultCredentials = HostCredentials(
        BuildConfig.INTEGRATION_APP_ID,
        BuildConfig.INTEGRATION_APP_KEY
    )

    val exams = listOf(
        ExamEntry("HSK1", "2032384598771499116",
            BuildConfig.INTEGRATION_HSK1_EXAM_TOKEN),
        ExamEntry("HSK2", "2032384598771499117",
            BuildConfig.INTEGRATION_HSK2_EXAM_TOKEN),
        ExamEntry("HSK3", "2032384598771499118",
            BuildConfig.INTEGRATION_HSK3_EXAM_TOKEN),
        ExamEntry("HSK4", "2032384598771499119",
            BuildConfig.INTEGRATION_HSK4_EXAM_TOKEN),
        ExamEntry("HSK5", "2032384598771499120",
            BuildConfig.INTEGRATION_HSK5_EXAM_TOKEN),
        ExamEntry("HSK6", "2032384598771499107",
            BuildConfig.INTEGRATION_HSK6_EXAM_TOKEN)
    )
}
