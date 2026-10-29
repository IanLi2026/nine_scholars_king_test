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
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class OpenExamForm(
    val base: String = ExamConfig.API_ORIGIN,
    val externalUserId: String = "partner-user-12345",
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

internal data class ExamCatalogOption(val id: String, val name: String) {
    val label: String get() = "$name（ID: $id）"
}

internal data class ExamQuotaRow(val title: String, val limit: String, val used: String, val remaining: String)

internal data class OpenExamResponse(val status: Int, val text: String) {
    val json: JSONObject? get() = runCatching { JSONObject(text) }.getOrNull()
    val successful: Boolean get() = status in 200..299 && json?.optInt("errcode", -1) == 0
    fun catalog(action: OpenExamAction): List<ExamCatalogOption> {
        require(action == OpenExamAction.LEVELS || action == OpenExamAction.PAPERS)
        check(successful) { json?.optString("errmsg")?.takeIf { it.isNotBlank() }
            ?: "列表请求失败（HTTP $status）" }
        val isLevel = action == OpenExamAction.LEVELS
        val arrayKey = if (isLevel) "levels" else "papers"
        val idKey = if (isLevel) "level_id" else "paper_id"
        val nameKey = if (isLevel) "level_name" else "name"
        val rows = requireNotNull(json?.optJSONObject("data")?.optJSONArray(arrayKey)) {
            "响应缺少 data.$arrayKey"
        }
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val id = row.optString(idKey)
            require(id.toLongOrNull()?.let { it > 0 } == true) { "列表包含无效 $idKey" }
            val name = row.optString(nameKey).takeUnless { it.isBlank() || it == "null" }
                ?: if (isLevel) "等级 $id" else "试卷 $id"
            ExamCatalogOption(id, name)
        }.distinctBy { it.id }
    }

    fun quotaRows(): List<ExamQuotaRow> {
        check(successful)
        val data = requireNotNull(json?.optJSONObject("data"))
        fun row(title: String, data: JSONObject) = ExamQuotaRow(title,
            data.optString("limit", "—"), data.optString("used", "—"), data.optString("remaining", "—"))
        return buildList {
            data.optJSONObject("tenant")?.let { add(row("租户权益", it)) }
            data.optJSONObject("user")?.let { add(row("当前考生", it)) }
            data.optJSONArray("levels")?.let { levels ->
                for (index in 0 until levels.length()) {
                    val level = levels.getJSONObject(index)
                    add(row(level.optString("level_name", level.optString("level_id")), level))
                }
            }
        }
    }

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

internal class OpenExamViewModel(
    application: Application,
    private val execute: (String, HostCredentials, OpenExamRequest) -> OpenExamResponse
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, OpenExamApi::execute)

    private var levelsVersion = 0
    private var papersVersion = 0
    private var levelsJob: Job? = null
    private var papersJob: Job? = null
    private data class LevelSource(val base: String, val kind: String, val credentials: HostCredentials?)
    private data class PaperSource(val levels: LevelSource, val user: String, val level: String)
    private fun levelSource(credentials: HostCredentials?) = LevelSource(form.base.trim(), form.paperKind, credentials)
    private fun paperSource(credentials: HostCredentials?) = PaperSource(levelSource(credentials), form.externalUserId.trim(), form.levelId)
    private var loadedLevels: LevelSource? = null
    private var loadedPapers: PaperSource? = null
    var levels by mutableStateOf<List<ExamCatalogOption>>(emptyList())
        private set
    var levelsRevision by mutableStateOf(0)
        private set
    var papers by mutableStateOf<List<ExamCatalogOption>>(emptyList())
        private set
    var levelsLoading by mutableStateOf(false)
        private set
    var papersLoading by mutableStateOf(false)
        private set
    var levelsError by mutableStateOf<String?>(null)
        private set
    var papersError by mutableStateOf<String?>(null)
        private set

    fun hasCurrentLevels(credentials: HostCredentials?) = loadedLevels == levelSource(credentials) &&
        levels.any { it.id == form.levelId }

    fun canCreate(credentials: HostCredentials?, ignoreBusy: Boolean = false) = (ignoreBusy || !busy) && !levelsLoading && !papersLoading &&
        hasCurrentLevels(credentials) && (form.paperKind == "official" ||
            (loadedPapers == paperSource(credentials) && papers.any { it.id == form.paperId }))

    private fun clearPapers() {
        papersVersion++
        papersJob?.cancel()
        papers = emptyList()
        loadedPapers = null
        papersLoading = false
        papersError = null
    }

    fun refreshLevels(credentials: HostCredentials?) {
        val version = ++levelsVersion
        levelsJob?.cancel()
        clearPapers()
        levels = emptyList()
        loadedLevels = null
        levelsError = null
        levelsJob = scope.launch {
            levelsLoading = true
            try {
                val snapshot = form.copy(base = form.base.trim())
                val source = levelSource(credentials)
                requireNotNull(credentials) { "请先配置 appId / appKey" }
                val request = OpenExamRequest.from(OpenExamAction.LEVELS, snapshot)
                val response = withContext(Dispatchers.IO) { execute(snapshot.base, credentials, request) }
                val options = response.catalog(OpenExamAction.LEVELS)
                levels = options
                loadedLevels = source
                update(form.copy(levelId = options.firstOrNull { it.id == form.levelId }?.id
                    ?: options.firstOrNull()?.id.orEmpty()))
                levelsRevision++
                if (options.isEmpty()) levelsError = "暂无可用等级，请刷新列表或检查租户权益"
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                levelsError = error.message ?: "等级列表加载失败"
            } finally { if (version == levelsVersion) levelsLoading = false }
        }
    }

    fun refreshPapers(credentials: HostCredentials?) {
        val preferredId = form.paperId
        clearPapers()
        if (form.paperKind != "mock" || !hasCurrentLevels(credentials)) return
        val version = papersVersion
        papersJob = scope.launch {
            papersLoading = true
            try {
                val snapshot = form.copy(base = form.base.trim(), externalUserId = form.externalUserId.trim())
                val source = paperSource(credentials)
                requireNotNull(credentials) { "请先配置 appId / appKey" }
                val request = OpenExamRequest.from(OpenExamAction.PAPERS, snapshot)
                val response = withContext(Dispatchers.IO) { execute(snapshot.base, credentials, request) }
                val options = response.catalog(OpenExamAction.PAPERS)
                papers = options
                loadedPapers = source
                update(form.copy(paperId = options.firstOrNull { it.id == preferredId }?.id
                    ?: options.firstOrNull()?.id.orEmpty()))
                if (options.isEmpty()) papersError = "该等级暂无模拟卷，请刷新列表或选择其他等级"
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                papersError = error.message ?: "模拟卷加载失败"
            } finally { if (version == papersVersion) papersLoading = false }
        }
    }

    private val preferences = application.getSharedPreferences("open_exam_form", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var form by mutableStateOf(OpenExamForm(
        base = preferences.getString("base", ExamConfig.API_ORIGIN)!!,
        externalUserId = preferences.getString("user", "partner-user-12345")!!,
        paperKind = preferences.getString("kind", "mock")!!,
        levelId = preferences.getString("level", "1")!!,
        paperId = preferences.getString("paper", "24")!!,
        requestId = preferences.getString("request", null) ?: OpenExamForm.newRequestId()
    ))
        private set
    var busy by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var quota by mutableStateOf<List<ExamQuotaRow>?>(null)
    var pendingExam by mutableStateOf<ExamEntry?>(null)
    var lastExam by mutableStateOf<ExamEntry?>(null)
        private set

    init { update(form) }

    fun update(updated: OpenExamForm) {
        val levelContextChanged = updated.base != form.base || updated.paperKind != form.paperKind
        val paperContextChanged = levelContextChanged || updated.levelId != form.levelId ||
            updated.externalUserId != form.externalUserId
        if (levelContextChanged) {
            levelsVersion++
            levelsJob?.cancel()
            levels = emptyList()
            loadedLevels = null
            levelsLoading = false
            levelsError = null
        }
        if (paperContextChanged) clearPapers()
        form = updated.copy(
            levelId = if (levelContextChanged) "" else updated.levelId,
            paperId = if (paperContextChanged) "" else updated.paperId
        )
        preferences.edit().putString("base", form.base).putString("user", form.externalUserId)
            .putString("kind", form.paperKind).putString("level", form.levelId)
            .putString("paper", form.paperId).putString("request", form.requestId).apply()
    }

    fun run(action: OpenExamAction, credentials: HostCredentials?) {
        if (busy) return
        if (action == OpenExamAction.LEVELS) { refreshLevels(credentials); return }
        if (action == OpenExamAction.PAPERS) { refreshPapers(credentials); return }
        val snapshot = form.copy(base = form.base.trim(), externalUserId = form.externalUserId.trim(),
            levelId = form.levelId.trim(), paperId = form.paperId.trim(), requestId = form.requestId.trim())
        scope.launch {
            busy = true
            notice = null
            try {
                require(credentials != null && credentials.appId.isNotBlank() && credentials.appKey.isNotBlank()) {
                    "请先在接入配置中填写 appId / appKey"
                }
                if (action == OpenExamAction.CREATE) require(canCreate(credentials, ignoreBusy = true)) {
                    "请先从接口列表选择有效等级和试卷"
                }
                val request = OpenExamRequest.from(action, snapshot)
                val response = withContext(Dispatchers.IO) { execute(snapshot.base, credentials, request) }
                check(response.successful) {
                    response.json?.optString("errmsg")?.takeIf { it.isNotBlank() }
                        ?: "HTTP ${response.status}"
                }
                if (action == OpenExamAction.QUOTA) {
                    val rows = response.quotaRows()
                    require(rows.isNotEmpty()) { "暂无可用权益信息" }
                    quota = rows
                }
                if (action == OpenExamAction.CREATE && response.successful) {
                    val selectedLevel = levels.first { it.id == snapshot.levelId }
                    val exam = response.createdExam(snapshot).copy(title = selectedLevel.name)
                    lastExam = exam
                    pendingExam = exam
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                notice = "${action.label}失败：${error.message ?: "请稍后重试"}"
            } finally {
                busy = false
            }
        }
    }

    override fun onCleared() { scope.cancel() }
}
