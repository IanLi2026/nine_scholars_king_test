package com.example.nine_scholars_king_test

/** Keep the service's original message and code alongside actionable host-side guidance. */
internal data class ExamApiFailure(val code: Int?, val message: String) {
    val isTimestampFailure: Boolean
        get() = (code == 6 || code == null) && message.contains("timestamp", ignoreCase = true)

    val guidance: String?
        get() = when (code) {
            6 -> if (isTimestampFailure) null else
                "接口鉴权失败，请核对接入配置中的 appId / appKey 和租户凭据有效期。"
            3008 -> "场次不存在或不属于当前租户，请核对场次链接与接入配置。"
            3016 -> "本场考试已经交卷，不能再次加载试卷。请由宿主创建新场次后重新进入考试。"
            3025 -> "exam_token 无效或与当前 appId 不匹配，请核对同一场次的 examination_id、exam_token 和租户凭据。"
            else -> null
        }

    val displayMessage: String
        get() = listOfNotNull(guidance, code?.let { "接口错误码：$it" },
            "接口原始错误：$message").joinToString("\n")
}
