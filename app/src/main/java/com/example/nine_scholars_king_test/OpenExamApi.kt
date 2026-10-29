package com.example.nine_scholars_king_test

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.net.URI
import java.net.URLEncoder
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class OpenExamForm(
    val base: String = ExamConfig.API_ORIGIN,
    val externalUserId: String = "partner-user-10001",
    val paperKind: String = "mock",
    val levelId: String = "1",
    val paperId: String = "24",
    val requestId: String = newRequestId()
) {
    fun validateConnection() {
        val uri = runCatching { URI(base) }.getOrNull()
        require(uri != null && uri.scheme == "https" && !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            uri.path.orEmpty().trim('/').isEmpty()) { "接口地址须为 HTTPS 域名，不包含路径、查询或账号信息" }
    }

    fun validateLevel() {
        require(levelId.toLongOrNull()?.let { it > 0 } == true) { "level_id 须为正整数" }
    }

    fun createBody(): String {
        require(externalUserId.isNotBlank()) { "请填写 external_user_id" }
        require(requestId.isNotBlank()) { "请填写 request_id" }
        require(paperKind in listOf("mock", "official")) { "请选择考试类型" }
        validateLevel()
        val body = JSONObject().put("external_user_id", externalUserId)
            .put("level_id", levelId.toLong()).put("paper_kind", paperKind)
        if (paperKind == "mock") {
            require(paperId.toLongOrNull()?.let { it > 0 } == true) { "模拟考的 paper_id 须为正整数" }
            body.put("paper_id", paperId.toLong())
        }
        return body.put("request_id", requestId).toString()
    }

    companion object {
        fun newRequestId() = "req-${UUID.randomUUID()}"
    }
}

internal enum class OpenExamAction(val label: String) {
    LEVELS("等级列表"), PAPERS("该等级模拟卷"), QUOTA("权益"), CREATE("创建考试")
}

internal data class OpenExamRequest(val method: String, val path: String, val body: String = "") {
    companion object {
        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        fun from(action: OpenExamAction, form: OpenExamForm): OpenExamRequest {
            form.validateConnection()
            return when (action) {
                OpenExamAction.LEVELS -> OpenExamRequest("GET", "/mock/open/levels?paper_kind=${encode(form.paperKind)}")
                OpenExamAction.PAPERS -> {
                    form.validateLevel()
                    require(form.externalUserId.isNotBlank()) { "请填写 external_user_id" }
                    OpenExamRequest("GET", "/mock/open/levels/${form.levelId}/papers?external_user_id=${encode(form.externalUserId)}")
                }
                OpenExamAction.QUOTA -> {
                    require(form.externalUserId.isNotBlank()) { "请填写 external_user_id" }
                    OpenExamRequest("GET", "/mock/open/tenant/quota?external_user_id=${encode(form.externalUserId)}")
                }
                OpenExamAction.CREATE -> OpenExamRequest("POST", "/mock/open/exams", form.createBody())
            }
        }
    }
}

internal data class OpenExamResponse(val status: Int, val text: String) {
    val json: JSONObject? get() = runCatching { JSONObject(text) }.getOrNull()
    val successful: Boolean get() = status in 200..299 && json?.optInt("errcode", -1) == 0
    fun createdExam(form: OpenExamForm): ExamEntry {
        check(successful) { "创建考试失败，请查看接口结果" }
        val data = requireNotNull(json?.optJSONObject("data")) { "创建响应缺少 data" }
        val id = data.optString("examination_id").takeUnless { it == "null" }.orEmpty()
        val token = data.optString("exam_token").takeUnless { it == "null" }.orEmpty()
        require(id.isNotBlank() && token.isNotBlank()) { "创建响应缺少 examination_id 或 exam_token，无法进入考试" }
        return ExamEntry("HSK${form.levelId}", id, token, form.paperKind)
    }
}

internal object OpenExamApi {
    fun execute(base: String, credentials: HostCredentials, request: OpenExamRequest): OpenExamResponse {
        val timestamp = if (base.trimEnd('/') == ExamConfig.API_ORIGIN)
            HostSigningClock.clock.read().timestamp else (System.currentTimeMillis() / 1000).toString()
        val headers = ExamSigner(credentials, allowCatalog = true)
            .sign(request.method, request.path, request.body, timestamp)
        val connection = URI(base.trimEnd('/') + request.path).toURL().openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("appid", headers.appid)
            connection.setRequestProperty("timestamp", headers.timestamp)
            connection.setRequestProperty("nonce", headers.nonce)
            connection.setRequestProperty("sign", headers.sign)
            if (request.method == "POST") {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            return OpenExamResponse(status, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }
}

internal class OpenExamViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("open_exam_form", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var form by mutableStateOf(OpenExamForm(
        base = preferences.getString("base", ExamConfig.API_ORIGIN)!!,
        externalUserId = preferences.getString("user", "partner-user-10001")!!,
        paperKind = preferences.getString("kind", "mock")!!,
        levelId = preferences.getString("level", "1")!!,
        paperId = preferences.getString("paper", "24")!!,
        requestId = preferences.getString("request", null) ?: OpenExamForm.newRequestId()
    ))
        private set
    var busy by mutableStateOf(false)
        private set
    var result by mutableStateOf("还没有请求。")
        private set
    var failed by mutableStateOf(false)
        private set
    var pendingExam by mutableStateOf<ExamEntry?>(null)
    var lastExam by mutableStateOf<ExamEntry?>(null)
        private set

    init { update(form) }

    fun update(updated: OpenExamForm) {
        form = updated
        preferences.edit().putString("base", updated.base).putString("user", updated.externalUserId)
            .putString("kind", updated.paperKind).putString("level", updated.levelId)
            .putString("paper", updated.paperId).putString("request", updated.requestId).apply()
    }

    fun run(action: OpenExamAction, credentials: HostCredentials?) {
        if (busy) return
        val snapshot = form.copy(base = form.base.trim(), externalUserId = form.externalUserId.trim(),
            levelId = form.levelId.trim(), paperId = form.paperId.trim(), requestId = form.requestId.trim())
        scope.launch {
            busy = true
            failed = false
            try {
                require(credentials != null && credentials.appId.isNotBlank() && credentials.appKey.isNotBlank()) {
                    "请先在接入配置中填写 appId / appKey"
                }
                val request = OpenExamRequest.from(action, snapshot)
                result = "${action.label} 请求中…"
                val response = withContext(Dispatchers.IO) { OpenExamApi.execute(snapshot.base, credentials, request) }
                failed = !response.successful
                result = "${action.label} · ${request.method} ${request.path}\nHTTP ${response.status}\n" +
                    (response.json?.toString(2) ?: response.text)
                if (action == OpenExamAction.CREATE && response.successful) {
                    val exam = response.createdExam(snapshot)
                    lastExam = exam
                    pendingExam = exam
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failed = true
                result = "${action.label}：${error.message}\n\n$result"
            } finally {
                busy = false
            }
        }
    }

    override fun onCleared() { scope.cancel() }
}
