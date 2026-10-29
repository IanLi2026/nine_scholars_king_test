package com.example.nine_scholars_king_test

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.nine_scholars_king_test.ui.theme.Nine_scholars_king_testTheme
import java.util.UUID
import java.util.concurrent.Executors
import org.json.JSONObject

class ExamActivity : ComponentActivity() {
    private lateinit var exam: ExamEntry
    private var webView by mutableStateOf<WebView?>(null)
    private var loading by mutableStateOf(true)
    private var loadProgress by mutableIntStateOf(0)
    private var errorMessage by mutableStateOf<String?>(null)
    private var errorTitle by mutableIntStateOf(R.string.exam_load_failed)
    private var loadedUrl by mutableStateOf("")
    private var diagnosticsExpanded by mutableStateOf(false)
    private var toolsVisible by mutableStateOf(false)
    private var lastSignature by mutableStateOf<SigningDiagnostic?>(null)
    private var timeReading by mutableStateOf(HostSigningClock.clock.read())
    private var timeStatus by mutableStateOf("尚未检测服务器时间")
    private var syncingTime by mutableStateOf(false)
    private var timestampRejected by mutableStateOf(false)
    private var automaticTimeRetryAttempted = false
    private val timeSyncExecutor = Executors.newSingleThreadExecutor()
    private var savingProgress by mutableStateOf(false)
    private var saveFailed by mutableStateOf(false)
    private var webViewFailureDetails by mutableStateOf<String?>(null)
    private var pageStarted = false
    private var savedWebViewState: Bundle? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingSaveId: String? = null
    private var submitted = false
    private var transferMode by mutableStateOf(CredentialTransferMode.NATIVE)
    private var compatibleJsInjection by mutableStateOf(false)
    private var switchingTo by mutableStateOf<CredentialTransferMode?>(null)
    private var afterSave: (() -> Unit)? = null
    private val saveTimeout = Runnable { completeProgressSave(pendingSaveId, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val selectedExam = ExamConfig.exams.firstOrNull {
            it.examinationId == intent.getStringExtra(EXTRA_EXAM_ID)
        }
        if (selectedExam == null) {
            // A stale shortcut or restored intent must return to a usable screen.
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
            return
        }
        exam = selectedExam
        transferMode = CredentialTransferMode.from(savedInstanceState?.getString(TRANSFER_MODE)
            ?: CredentialTransferStore(this).load().value)
        enableEdgeToEdge()
        savedWebViewState = savedInstanceState?.getBundle(WEBVIEW_STATE)
        automaticTimeRetryAttempted = savedInstanceState?.getBoolean(TIME_RETRY_ATTEMPTED) ?: false
        toolsVisible = savedInstanceState?.getBoolean(TOOLS_VISIBLE) ?: false

        setContent {
            Nine_scholars_king_testTheme {
                BackHandler { closeAfterSaving() }
                if (savingProgress) {
                    AlertDialog(onDismissRequest = {}, confirmButton = {},
                        text = { Text(stringResource(R.string.saving_progress)) })
                }
                if (saveFailed) {
                    AlertDialog(
                        onDismissRequest = {
                            saveFailed = false
                            afterSave = null
                            switchingTo = null
                        },
                        title = { Text(stringResource(R.string.progress_save_failed)) },
                        text = { Text(stringResource(R.string.progress_save_failed_hint)) },
                        confirmButton = {
                            TextButton(onClick = {
                                saveFailed = false
                                saveBefore(afterSave ?: { finish() })
                            }) {
                                Text(stringResource(R.string.retry))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                saveFailed = false
                                val action = afterSave
                                afterSave = null
                                action?.invoke()
                            }) {
                                Text(stringResource(if (switchingTo == null)
                                    R.string.leave_without_saving else R.string.switch_without_saving))
                            }
                        }
                    )
                }
                Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
                    if (toolsVisible) {
                    Surface {
                        Column(Modifier.fillMaxWidth().statusBarsPadding().heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                            TextButton(onClick = { toolsVisible = false },
                                modifier = Modifier.align(Alignment.End).testTag("hide-exam-tools")) {
                                Text("隐藏工具")
                            }
                            CredentialTransferSelector(transferMode,
                                onSelect = { switchTransferMode(it) },
                                enabled = !savingProgress && !saveFailed && !syncingTime)
                            ExamDiagnosticsPanel(
                                url = loadedUrl, lastSignature = lastSignature,
                                timeReading = timeReading, timeStatus = timeStatus,
                                expanded = diagnosticsExpanded, syncing = syncingTime,
                                canSync = !savingProgress && !saveFailed,
                                onCopy = {
                                    getSystemService(ClipboardManager::class.java).setPrimaryClip(
                                        ClipData.newPlainText("考试 URL", loadedUrl))
                                },
                                onExpand = {
                                    timeReading = HostSigningClock.clock.read()
                                    diagnosticsExpanded = !diagnosticsExpanded
                                },
                                onSync = { synchronizeServerTime(reloadOnSuccess = false) },
                                onDateSettings = { openDateSettings() }
                            )
                            if (compatibleJsInjection) {
                                Text("当前网页组件使用兼容 JS 注入，页面加载后可读取凭据。",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    }
                }) { innerPadding ->
                    Box(Modifier.fillMaxSize().padding(innerPadding)) {
                        webView?.let { activeView ->
                            key(activeView) {
                                AndroidView(factory = { activeView }, modifier = Modifier.fillMaxSize())
                            }
                        }
                        if (loading && errorMessage == null) {
                            LinearProgressIndicator(
                                progress = { loadProgress / 100f },
                                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                            )
                        }
                        errorMessage?.let { message ->
                            Surface(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier.fillMaxSize()
                                        .verticalScroll(rememberScrollState()).padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(
                                        16.dp, Alignment.CenterVertically
                                    ),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        stringResource(errorTitle),
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Text(message, textAlign = TextAlign.Center)
                                    webViewFailureDetails?.let { details ->
                                        Text(details, style = MaterialTheme.typography.bodySmall)
                                        TextButton(onClick = { openWebViewSettings() }) {
                                            Text(stringResource(R.string.open_webview_settings))
                                        }
                                    }
                                    if (timestampRejected) {
                                        Button(onClick = { synchronizeServerTime(reloadOnSuccess = true) },
                                            enabled = !syncingTime) {
                                            Text(if (syncingTime) "正在校时…" else
                                                stringResource(R.string.sync_time_and_retry))
                                        }
                                        TextButton(onClick = { openDateSettings() }) {
                                            Text("日期和时间设置")
                                        }
                                    }
                                    Button(onClick = { openExam() }, enabled = !syncingTime) {
                                        Text(stringResource(R.string.retry))
                                    }
                                    TextButton(onClick = { closeAfterSaving() }) {
                                        Text(stringResource(R.string.close_exam))
                                    }
                                }
                            }
                        }
                        if (!toolsVisible) {
                            Surface(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                shape = MaterialTheme.shapes.small, tonalElevation = 2.dp) {
                                TextButton(onClick = { toolsVisible = true },
                                    modifier = Modifier.testTag("show-exam-tools")) {
                                    Text("工具")
                                }
                            }
                        }
                    }
                }
            }
        }
        openExam()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createExamWebView(credentials: HostCredentials) = WebView(this).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = false
            allowContentAccess = false
        }
        CookieManager.getInstance().setAcceptCookie(true)
        val configScript = if (transferMode == CredentialTransferMode.JAVASCRIPT)
            CredentialTransfer.script(credentials) else null
        compatibleJsInjection = false
        if (configScript != null) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(this, configScript,
                    setOf("https://staging-public.hskmock.com"))
            } else {
                compatibleJsInjection = true
            }
        }
        val hostView = this
        addJavascriptInterface(ExamHostBridge(exam.examinationId, credentials,
            onEvent = { event, payload ->
                mainHandler.post {
                    if (!isDestroyed && webView === hostView) handlePageEvent(event, payload)
                }
            },
            onProgressSaved = { requestId, success ->
                mainHandler.post { completeProgressSave(requestId, success) }
            }, transferMode = transferMode,
            onSigned = { diagnostic ->
                mainHandler.post {
                    if (!isDestroyed && webView === hostView) {
                        lastSignature = diagnostic
                        timeReading = diagnostic.time
                    }
                }
            }
        ), "JxwBridge")
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean = !isExamPage(request.url)

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (url == null || !isExamPage(url.toUri())) {
                    view.stopLoading()
                    return
                }
                errorMessage = null
                timestampRejected = false
                loadedUrl = url
                loading = true
                loadProgress = 0
                if (compatibleJsInjection && configScript != null) {
                    view.evaluateJavascript(configScript, null)
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                loading = false
                if (compatibleJsInjection && configScript != null &&
                    url?.let { isExamPage(it.toUri()) } == true) {
                    view.evaluateJavascript(configScript, null)
                }
            }

            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                // A failed image/audio/API request must not hide the whole exam.
                if (request.isForMainFrame) {
                    showError(getString(R.string.network_error, error.errorCode))
                }
            }

            override fun onReceivedHttpError(
                view: WebView, request: WebResourceRequest, response: WebResourceResponse
            ) {
                if (request.isForMainFrame) {
                    showError(getString(R.string.http_error, response.statusCode))
                }
            }

            override fun onRenderProcessGone(
                view: WebView, detail: RenderProcessGoneDetail
            ): Boolean {
                // A dead renderer cannot be reused. Keep the Activity alive for retry.
                if (webView === view) {
                    webView = null
                    pageStarted = false
                    savedWebViewState = null
                    if (savingProgress) completeProgressSave(pendingSaveId, false)
                    showError(getString(R.string.webview_renderer_stopped))
                }
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
                return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                loadProgress = newProgress
            }
        }
    }

    private fun openExam() {
        if (syncingTime || isFinishing) return
        webViewFailureDetails = null
        val credentials = runCatching { HostCredentialsStore(this).loadOrDefault() }.getOrNull()
        if (credentials == null) {
            showError(getString(R.string.host_credentials_missing))
            return
        }
        val activeView = webView ?: try {
            createExamWebView(credentials).also { webView = it }
        } catch (error: RuntimeException) {
            Log.e("ExamShell", "Unable to initialize system WebView", error)
            webViewFailureDetails = describeWebViewFailure(error)
            showError(getString(R.string.webview_unavailable))
            return
        }
        // Debugging is optional; a vendor restriction must not prevent an exam opening.
        try {
            WebView.setWebContentsDebuggingEnabled(
                applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            )
        } catch (error: RuntimeException) {
            Log.w("ExamShell", "WebView debugging is unavailable", error)
        }
        errorMessage = null
        timestampRejected = false
        loading = true
        loadProgress = 0
        if (!pageStarted) {
            pageStarted = true
            val restored = savedWebViewState?.let { activeView.restoreState(it) }
            savedWebViewState = null
            val restoredUrl = restored?.currentItem?.url
            if (restoredUrl?.let { isExamPage(it.toUri()) } == true) {
                loadedUrl = restoredUrl
                return
            }
        }
        loadedUrl = CredentialTransfer.url(exam, transferMode, credentials)
        activeView.loadUrl(loadedUrl)
    }

    private fun describeWebViewFailure(error: RuntimeException): String {
        val provider = if (Build.VERSION.SDK_INT >= 26) {
            try {
                WebViewCompat.getCurrentWebViewPackage(this)?.let { "${it.packageName} ${it.versionName}" }
                    ?: getString(R.string.webview_provider_not_found)
            } catch (_: RuntimeException) {
                getString(R.string.webview_provider_unknown)
            }
        } else {
            getString(R.string.webview_provider_legacy)
        }
        val cause = generateSequence(error as Throwable) { it.cause }.take(8).last()
        return getString(
            R.string.webview_diagnostics,
            Build.VERSION.RELEASE, Build.VERSION.SDK_INT,
            Build.SUPPORTED_ABIS.joinToString(), provider,
            "${cause.javaClass.simpleName}: ${cause.message.orEmpty().take(400)}"
        )
    }

    private fun openWebViewSettings() {
        for (action in listOf(Settings.ACTION_WEBVIEW_SETTINGS, Settings.ACTION_APPLICATION_SETTINGS)) {
            try {
                startActivity(Intent(action))
                return
            } catch (_: ActivityNotFoundException) {
                // Some custom firmware omits the WebView settings page.
            } catch (_: SecurityException) {
                // Restricted devices may only allow their vendor to repair system apps.
            }
        }
        Toast.makeText(this, R.string.webview_settings_unavailable, Toast.LENGTH_LONG).show()
    }

    private fun showError(message: String, title: Int = R.string.exam_load_failed) {
        loading = false
        errorTitle = title
        errorMessage = message
    }

    private fun openDateSettings() {
        try {
            startActivity(Intent(Settings.ACTION_DATE_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.date_settings_unavailable, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.date_settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun synchronizeServerTime(reloadOnSuccess: Boolean) {
        if (syncingTime || savingProgress || saveFailed || isFinishing) return
        val originalView = webView
        syncingTime = true
        diagnosticsExpanded = true
        timeStatus = "正在读取 ${ExamConfig.API_ORIGIN} 的 HTTPS 服务器时间…"
        timeSyncExecutor.execute {
            val result = runCatching { ServerTimeSync.synchronize() }
            mainHandler.post {
                if (isDestroyed || isFinishing) return@post
                syncingTime = false
                timeReading = HostSigningClock.clock.read()
                result.fold(onSuccess = {
                    timeStatus = "校时成功，后续签名使用服务器时间；校准在本进程内有效期为 6 小时。"
                    if (reloadOnSuccess && webView === originalView && errorMessage != null) {
                        savedWebViewState = null
                        openExam()
                    }
                }, onFailure = { error ->
                    timeStatus = "校时失败（${error.javaClass.simpleName}）：${error.message.orEmpty().take(160)}。" +
                        "请检查网络，并在系统日期和时间设置中开启自动校时。"
                })
            }
        }
    }

    private fun isExamPage(uri: Uri): Boolean = uri.scheme == "https" &&
        uri.host == "staging-public.hskmock.com" && (uri.port == -1 || uri.port == 443) &&
        uri.path == "/hskmock_webview/index.html" && uri.userInfo == null

    private fun handlePageEvent(event: String, payload: JSONObject) {
        when (event) {
            "exam_submitted" -> {
                if (payload.optString("status") !in listOf("scoring", "scored")) return
                submitted = true
                Toast.makeText(this, if (payload.optString("status") == "scored")
                    R.string.exam_submitted_scored else R.string.exam_submitted_scoring,
                    Toast.LENGTH_SHORT).show()
            }
            "request_close" -> {
                if (payload.optString("status") in listOf("scoring", "scored")) {
                    submitted = true
                    finish()
                }
            }
            "exam_init_failed" -> {
                val message = payload.optString("errmsg").ifBlank { getString(R.string.exam_init_failed) }
                val code = if (payload.has("errcode") && !payload.isNull("errcode"))
                    payload.optString("errcode").toIntOrNull() else null
                val failure = ExamApiFailure(code, message)
                timestampRejected = failure.isTimestampFailure
                if (timestampRejected) {
                    diagnosticsExpanded = true
                    showError(getString(R.string.timestamp_rejected, failure.displayMessage),
                        R.string.exam_init_failed_title)
                    if (!automaticTimeRetryAttempted) {
                        automaticTimeRetryAttempted = true
                        synchronizeServerTime(reloadOnSuccess = true)
                    }
                } else showError(failure.displayMessage, R.string.exam_init_failed_title)
            }
            "answer_save_failed" -> Toast.makeText(this, R.string.answer_save_failed,
                Toast.LENGTH_LONG).show()
        }
    }

    private fun closeAfterSaving() {
        switchingTo = null
        saveBefore { finish() }
    }

    private fun switchTransferMode(mode: CredentialTransferMode) {
        if (mode == transferMode || savingProgress || saveFailed || syncingTime) return
        switchingTo = mode
        saveBefore {
            switchingTo = null
            destroyExamWebView()
            transferMode = mode
            CredentialTransferStore(this).save(mode)
            savedWebViewState = null
            pageStarted = false
            submitted = false
            compatibleJsInjection = false
            openExam()
        }
    }

    private fun saveBefore(action: () -> Unit) {
        if (savingProgress || isFinishing) return
        afterSave = action
        val activeView = webView
        if (activeView == null || submitted) {
            afterSave = null
            action()
            return
        }
        savingProgress = true
        val requestId = UUID.randomUUID().toString()
        pendingSaveId = requestId
        mainHandler.postDelayed(saveTimeout, 10_000)
        // evaluateJavascript itself does not await a Promise. Complete through the native bridge.
        activeView.evaluateJavascript("""
            (function () {
              if (!window.JxwExam || typeof window.JxwExam.saveProgress !== 'function') {
                window.JxwBridge.progressSaved('$requestId', true);
                return;
              }
              Promise.resolve().then(function () { return window.JxwExam.saveProgress(); })
                .then(function (ok) { window.JxwBridge.progressSaved('$requestId', ok === true); },
                      function () { window.JxwBridge.progressSaved('$requestId', false); });
            })();
        """.trimIndent(), null)
    }

    private fun completeProgressSave(requestId: String?, success: Boolean) {
        if (requestId == null || requestId != pendingSaveId || isDestroyed) return
        mainHandler.removeCallbacks(saveTimeout)
        pendingSaveId = null
        savingProgress = false
        if (success) {
            val action = afterSave
            afterSave = null
            action?.invoke()
        } else saveFailed = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(TOOLS_VISIBLE, toolsVisible)
        outState.putBoolean(TIME_RETRY_ATTEMPTED, automaticTimeRetryAttempted)
        outState.putString(TRANSFER_MODE, transferMode.value)
        if (pageStarted) {
            webView?.let { activeView ->
                outState.putBundle(WEBVIEW_STATE, Bundle().also { activeView.saveState(it) })
            }
        }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        webView?.onResume()
    }

    override fun onPause() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        webView?.let {
            if (!isFinishing && !savingProgress && !submitted) {
                it.evaluateJavascript("""
                    (function () {
                      if (window.JxwExam && typeof window.JxwExam.saveProgress === 'function') {
                        Promise.resolve().then(function () { return window.JxwExam.saveProgress(); })
                          .catch(function () {});
                      }
                    })();
                """.trimIndent(), null)
            }
            it.onPause()
            CookieManager.getInstance().flush()
        }
        super.onPause()
    }

    override fun onDestroy() {
        timeSyncExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        afterSave = null
        destroyExamWebView()
        super.onDestroy()
    }

    private fun destroyExamWebView() {
        webView?.let {
            webView = null
            (it.parent as? ViewGroup)?.removeView(it)
            it.stopLoading()
            it.removeJavascriptInterface("JxwBridge")
            it.destroy()
        }
    }

    companion object {
        const val EXTRA_EXAM_ID = "exam_id"
        private const val WEBVIEW_STATE = "exam_webview_state"
        private const val TRANSFER_MODE = "credential_transfer_mode"
        private const val TIME_RETRY_ATTEMPTED = "time_retry_attempted"
        private const val TOOLS_VISIBLE = "exam_tools_visible"
    }
}
