package com.example.nine_scholars_king_test

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertTextContains
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import androidx.webkit.WebViewFeature

/** Local HTML fixtures exercise the shell without using or submitting a real exam. */
@RunWith(AndroidJUnit4::class)
class ExamWebViewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule(order = 0)
    val credentialsRule = TestHostCredentialsRule()

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Test
    fun playsAudioWithoutGestureAndHasBridgeBeforePageScripts() {
        launchExam().use { scenario ->
            val view = findWebView()
            // A short silent WAV verifies playback can start without a user gesture.
            val pcmBytes = 4000
            val wave = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".toByteArray()).putInt(36 + pcmBytes)
                .put("WAVEfmt ".toByteArray()).putInt(16)
                .putShort(1).putShort(1).putInt(8000).putInt(16000)
                .putShort(2).putShort(16).put("data".toByteArray()).putInt(pcmBytes)
                .array()
            val audio = Base64.encodeToString(wave, Base64.NO_WRAP)
            loadFixture(scenario, view, """
                <audio id="audio" src="data:audio/wav;base64,$audio"></audio>
                <script>
                  window.bridgeAtStartup = typeof window.JxwBridge.sign;
                  window.playback = 'waiting';
                  const audio = document.getElementById('audio');
                  audio.onended = () => window.playback = 'ended';
                  audio.play().catch(() => window.playback = 'blocked');
                </script>
            """.trimIndent())
            assertEquals("\"function\"", evaluate(scenario, view, "window.bridgeAtStartup"))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            var playback: String
            do {
                playback = evaluate(scenario, view, "window.playback")
                if (playback == "\"ended\"" || playback == "\"blocked\"") break
                Thread.sleep(100)
            } while (System.nanoTime() < deadline)
            assertEquals("\"ended\"", playback)
        }
    }

    @Test
    fun storageSurvivesClosingAndSystemBackExitsDespiteWebHistory() {
        val key = "shell-storage-test-${System.nanoTime()}"
        launchExam().use { scenario ->
            val view = findWebView()
            loadFixture(scenario, view)
            assertEquals("\"answer-A\"", evaluate(
                scenario, view,
                "localStorage.setItem('$key', 'answer-A'); localStorage.getItem('$key')"
            ))
        }
        launchExam().use { scenario ->
            val view = findWebView()
            loadFixture(scenario, view)
            assertEquals("\"answer-A\"", evaluate(scenario, view, "localStorage.getItem('$key')"))
            evaluate(scenario, view, "localStorage.removeItem('$key'); history.pushState({}, '', '#question2')")
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (scenario.state != Lifecycle.State.DESTROYED && System.nanoTime() < deadline) {
                Thread.sleep(100)
            }
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    @Test
    fun signingReturnsLowercaseHeaderFieldsAndKeepsKeyInHost() {
        launchExam().use { scenario ->
            val view = findWebView()
            loadFixture(scenario, view)
            val url = "/mock/open/exams/${ExamConfig.exams.first().examinationId}/paper?exam_token=a%2Bb&z=2&a=1"
            val request = JSONObject().put("method", "GET").put("requestUrl", url).put("body", "")
            val result = evaluate(scenario, view,
                "window.JxwBridge.sign(${JSONObject.quote(request.toString())})")
            val headers = JSONObject(org.json.JSONTokener(result).nextValue() as String)
            assertEquals(setOf("appid", "timestamp", "nonce", "sign"), headers.keys().asSequence().toSet())
            val expected = ExamSigner(HostCredentials("fixture-app", "fixture-secret")).sign(
                "GET", url, "", headers.getString("timestamp"), headers.getString("nonce"))
            assertEquals(expected.sign, headers.getString("sign"))
            assertFalse(result.contains("fixture-secret"))
        }
    }

    @Test
    fun loadedUrlRemainsVisibleOnApiFailureAndShowsActualSignedTimestamp() {
        launchExam().use { scenario ->
            val view = findWebView()
            val expectedUrl = ExamConfig.exams.first().url
            composeRule.onNodeWithTag("show-exam-tools").performClick()
            composeRule.onNodeWithTag("loaded-url").assertTextContains(expectedUrl)
            loadFixture(scenario, view)
            val path = "/mock/open/exams/${ExamConfig.exams.first().examinationId}/paper?exam_token=fixture"
            val request = JSONObject().put("method", "GET").put("requestUrl", path).put("body", "")
            val raw = evaluateText(scenario, view,
                "window.JxwBridge.sign(${JSONObject.quote(request.toString())})")
            val signedTimestamp = JSONObject(raw).getString("timestamp")
            val payload = JSONObject().put("examination_id", ExamConfig.exams.first().examinationId)
                .put("errcode", 3006).put("errmsg", "fixture paper unavailable").toString()
            evaluate(scenario, view, "window.JxwBridge.notify('exam_init_failed', ${JSONObject.quote(payload)});")
            composeRule.waitUntil(timeoutMillis = 5000) {
                composeRule.onAllNodesWithText("试卷接口初始化失败").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("loaded-url").assertTextContains(expectedUrl)
            composeRule.onNodeWithText("时间诊断").performClick()
            composeRule.onNodeWithTag("last-signing-request").assertTextContains(signedTimestamp, substring = true)
            composeRule.onNodeWithTag("last-signing-request").assertTextContains(path, substring = true)
        }
    }

    @Test
    fun submittedExamFailureShowsCodeAndNewSessionGuidance() {
        launchExam().use { scenario ->
            val view = findWebView()
            loadFixture(scenario, view)
            val payload = JSONObject().put("examination_id", ExamConfig.exams.first().examinationId)
                .put("errcode", 3016).put("errmsg", "already submitted").toString()
            evaluate(scenario, view, "window.JxwBridge.notify('exam_init_failed', ${JSONObject.quote(payload)});")
            composeRule.waitUntil(timeoutMillis = 5000) {
                composeRule.onAllNodesWithText("接口错误码：3016", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("请由宿主创建新场次", substring = true)
                .assertTextContains("接口原始错误：already submitted", substring = true)
        }
    }

    @Test
    fun bridgeUsesCalibratedClockAndReportsTheExactReturnedTimestamp() {
        var elapsed = 5000L
        val clock = SigningClock({ 1_793_188_800_000L + 86_400_000L }, { elapsed })
        clock.calibrate(1_793_188_800_000L, elapsed, elapsed)
        elapsed += 2000L
        var reported: SigningDiagnostic? = null
        val examId = ExamConfig.exams.first().examinationId
        val bridge = ExamHostBridge(examId, HostCredentials("fixture-app", "fixture-secret"),
            onEvent = { _, _ -> }, onProgressSaved = { _, _ -> },
            signingClock = clock, onSigned = { reported = it })
        val request = JSONObject().put("method", "GET")
            .put("requestUrl", "/mock/open/exams/$examId/paper?exam_token=fixture").put("body", "")
        val headers = JSONObject(bridge.sign(request.toString()))
        assertEquals("1793188802", headers.getString("timestamp"))
        assertEquals(headers.getString("timestamp"), reported?.time?.timestamp)
        assertTrue(requireNotNull(reported).time.usesServerTime)
    }

    @Test
    fun selectedTransportMakesCredentialsAvailableThroughItsOwnChannel() {
        val context = instrumentation.targetContext
        val credentials = HostCredentials("transport id&中文", "transport+'\"\\\n</script>")
        HostCredentialsStore(context).save(credentials)
        for (mode in CredentialTransferMode.entries) {
            CredentialTransferStore(context).save(mode)
            launchExam().use { scenario ->
                val view = findWebView()
                val expectedUrl = CredentialTransfer.url(ExamConfig.exams.first(), mode, credentials)
                scenario.onActivity { assertEquals(expectedUrl, view.url) }
                loadFixture(scenario, view, """
                    <script>window.__configAtStartup = window.JxwHostConfig || null;</script>
                """.trimIndent(), baseUrl = expectedUrl, keepHostClient = true)
                assertEquals(JSONObject.quote(mode.value), evaluate(scenario, view,
                    "window.JxwBridge.getTransferMode()"))
                when (mode) {
                    CredentialTransferMode.URL -> {
                        val readQuery = "new URLSearchParams(location.hash.split('?')[1])"
                        assertEquals(credentials.appId,
                            evaluateText(scenario, view, "$readQuery.get('app_id')"))
                        assertEquals(credentials.appKey,
                            evaluateText(scenario, view, "$readQuery.get('app_key')"))
                    }
                    CredentialTransferMode.JAVASCRIPT -> {
                        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                        while (evaluate(scenario, view, "typeof window.JxwHostConfig") != "\"object\"" &&
                            System.nanoTime() < deadline) Thread.sleep(100)
                        assertEquals(credentials.appId,
                            evaluateText(scenario, view, "window.JxwHostConfig.appId"))
                        assertEquals(credentials.appKey,
                            evaluateText(scenario, view, "window.JxwHostConfig.appKey"))
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                            assertEquals(credentials.appId,
                                evaluateText(scenario, view, "window.__configAtStartup.appId"))
                        }
                    }
                    CredentialTransferMode.NATIVE -> {
                        assertEquals(credentials.appId, evaluateText(scenario, view,
                            "JSON.parse(window.JxwBridge.getCredentials()).appId"))
                        assertEquals(credentials.appKey, evaluateText(scenario, view,
                            "JSON.parse(window.JxwBridge.getCredentials()).appKey"))
                    }
                }
                if (mode != CredentialTransferMode.NATIVE) {
                    assertEquals("\"{}\"", evaluate(scenario, view, "window.JxwBridge.getCredentials()"))
                }
                if (mode != CredentialTransferMode.JAVASCRIPT) {
                    assertEquals("\"undefined\"", evaluate(scenario, view, "typeof window.JxwHostConfig"))
                }
            }
        }
    }

    @Test
    fun switchingModeInExamWaitsForSaveAndRecreatesWebView() {
        launchExam().use { scenario ->
            val original = findWebView()
            loadFixture(scenario, original, """
                <script>window.JxwExam = { saveProgress: function () {
                  return new Promise(function (resolve) { setTimeout(function () { resolve(true); }, 600); });
                }};</script>
            """.trimIndent())
            composeRule.onNodeWithTag("show-exam-tools").performClick()
            composeRule.onNodeWithTag("transfer-mode-url").performClick()
            val expectedUrl = CredentialTransfer.url(ExamConfig.exams.first(), CredentialTransferMode.URL,
                HostCredentials("fixture-app", "fixture-secret"))
            composeRule.waitUntil(timeoutMillis = 8000) {
                var changed = false
                scenario.onActivity { activity ->
                    val root = activity.window.decorView
                    fun find(view: android.view.View): WebView? {
                        if (view is WebView) return view
                        if (view is android.view.ViewGroup) for (index in 0 until view.childCount) {
                            find(view.getChildAt(index))?.let { return it }
                        }
                        return null
                    }
                    val replacement = find(root)
                    changed = replacement != null && replacement !== original && replacement.url == expectedUrl
                }
                changed
            }
            assertEquals(CredentialTransferMode.URL, CredentialTransferStore(instrumentation.targetContext).load())
        }
    }

    @Test
    fun landscapeExamToolsStartHiddenAndTogglingKeepsPageState() {
        launchExam().use { scenario ->
            val view = findWebView()
            loadFixture(scenario, view, "<script>window.toolToggleMarker = 'unchanged';</script>")
            scenario.onActivity { activity ->
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, activity.requestedOrientation)
                assertEquals(Configuration.ORIENTATION_LANDSCAPE, activity.resources.configuration.orientation)
            }
            composeRule.onNodeWithTag("loaded-url").assertDoesNotExist()
            composeRule.onNodeWithTag("show-exam-tools").performClick()
            composeRule.onNodeWithTag("loaded-url").assertTextContains(ExamConfig.exams.first().url)
            composeRule.onNodeWithTag("hide-exam-tools").performClick()
            composeRule.onNodeWithTag("loaded-url").assertDoesNotExist()
            assertEquals("\"unchanged\"", evaluate(scenario, view, "window.toolToggleMarker"))
        }
    }

    @Test
    fun examTitleIsHiddenAndItsReservedSpaceIsReclaimedForLateRenderedContent() {
        launchExam().use { scenario ->
            val view = findWebView()
            loadFixture(scenario, view, """
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                  .exam-title-bar { display: flex; height: 56px; }
                  .sidebar-wrapper, .header-wrapper { position: fixed; top: 80px; }
                  .content-wrapper { padding-top: 192px; }
                </style>
                <header id="other-title">Other section</header>
                <div class="exam-page">
                  <div class="exam-title-bar">HSK2</div>
                  <aside class="sidebar-wrapper"></aside>
                  <div class="header-wrapper"></div>
                  <main class="content-wrapper"><input id="answer" value="A"></main>
                </div>
            """.trimIndent(), baseUrl = ExamConfig.exams.first().url, keepHostClient = true)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (evaluateText(scenario, view,
                "getComputedStyle(document.querySelector('.exam-title-bar')).display") != "none" &&
                System.nanoTime() < deadline) Thread.sleep(100)
            assertEquals("none", evaluateText(scenario, view,
                "getComputedStyle(document.querySelector('.exam-title-bar')).display"))
            assertEquals("24px", evaluateText(scenario, view,
                "getComputedStyle(document.querySelector('.sidebar-wrapper')).top"))
            assertEquals("24px", evaluateText(scenario, view,
                "getComputedStyle(document.querySelector('.header-wrapper')).top"))
            assertEquals("true", evaluate(scenario, view, """
                getComputedStyle(document.querySelector('.content-wrapper')).paddingTop ===
                  (innerWidth >= 768 ? '136px' : '16px')
            """.trimIndent()))
            evaluate(scenario, view, """
                document.querySelector('.exam-title-bar').remove();
                var lateTitle = document.createElement('div');
                lateTitle.className = 'exam-title-bar';
                document.querySelector('.exam-page').appendChild(lateTitle);
            """.trimIndent())
            assertEquals("none", evaluateText(scenario, view,
                "getComputedStyle(document.querySelector('.exam-title-bar')).display"))
            assertEquals("A", evaluateText(scenario, view, "document.getElementById('answer').value"))
            assertEquals("false", evaluate(scenario, view,
                "getComputedStyle(document.getElementById('other-title')).display === 'none'"))
        }
    }

    @Test
    fun backWaitsForAsynchronousSaveBeforeDestroyingWebView() {
        val key = "shell-async-save-${System.nanoTime()}"
        launchExam().use { scenario ->
            val view = findWebView()
            loadFixture(scenario, view, """
                <script>
                  window.JxwExam = { saveProgress: function () {
                    return new Promise(function (resolve) {
                      setTimeout(function () { localStorage.setItem('$key', 'saved'); resolve(true); }, 700);
                    });
                  }};
                </script>
            """.trimIndent())
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            assertTrue(scenario.state != Lifecycle.State.DESTROYED)
            waitUntilClosed(scenario)
        }
        launchExam().use { scenario ->
            val view = findWebView()
            loadFixture(scenario, view)
            assertEquals("\"saved\"", evaluate(scenario, view, "localStorage.getItem('$key')"))
            evaluate(scenario, view, "localStorage.removeItem('$key')")
        }
    }

    @Test
    fun failedSaveKeepsExamOpenAndCanRetry() {
        launchExam().use { scenario ->
            val view = findWebView()
            loadFixture(scenario, view, """
                <script>window.JxwExam = { saveProgress: function () { return Promise.resolve(false); }};</script>
            """.trimIndent())
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeRule.waitUntil(timeoutMillis = 5000) {
                composeRule.onAllNodesWithText("答题进度未能保存").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(scenario.state != Lifecycle.State.DESTROYED)
            evaluate(scenario, view, "window.JxwExam.saveProgress = function () { return Promise.resolve(true); }")
            composeRule.onNodeWithText("重试").performClick()
            waitUntilClosed(scenario)
        }
    }

    @Test
    fun submittedAndRequestCloseEventsCloseWithoutSavingAgain() {
        launchExam().use { scenario ->
            val view = findWebView()
            loadFixture(scenario, view, """
                <script>window.JxwExam = { saveProgress: function () { throw new Error('already submitted'); }};</script>
            """.trimIndent())
            val payload = JSONObject().put("examination_id", ExamConfig.exams.first().examinationId)
                .put("status", "scoring").toString()
            evaluate(scenario, view, "window.JxwBridge.notify('exam_submitted', ${JSONObject.quote(payload)});")
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            waitUntilClosed(scenario)
        }
        launchExam().use { scenario ->
            val view = findWebView()
            loadFixture(scenario, view)
            val payload = JSONObject().put("examination_id", ExamConfig.exams.first().examinationId)
                .put("status", "scored").toString()
            evaluate(scenario, view, "window.JxwBridge.notify('request_close', ${JSONObject.quote(payload)});")
            waitUntilClosed(scenario)
        }
    }

    private fun waitUntilClosed(scenario: ActivityScenario<ExamActivity>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (scenario.state != Lifecycle.State.DESTROYED && System.nanoTime() < deadline) {
            Thread.sleep(100)
        }
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    private fun launchExam(): ActivityScenario<ExamActivity> = ActivityScenario.launch(
        Intent(instrumentation.targetContext, ExamActivity::class.java)
            .putExtra(ExamActivity.EXTRA_EXAM_ID, ExamConfig.exams.first().examinationId)
    )

    private fun findWebView(): WebView {
        var result: WebView? = null
        onView(isAssignableFrom(WebView::class.java)).check { view, error ->
            if (error != null) throw error
            result = view as WebView
        }
        return requireNotNull(result)
    }

    private fun loadFixture(
        scenario: ActivityScenario<ExamActivity>, view: WebView,
        html: String = "<html><body>Exam shell fixture</body></html>",
        baseUrl: String = "https://exam-shell.test/",
        keepHostClient: Boolean = false
    ) {
        val marker = UUID.randomUUID().toString()
        scenario.onActivity {
            view.stopLoading()
            if (!keepHostClient) view.webViewClient = WebViewClient()
            view.loadDataWithBaseURL(baseUrl,
                "$html<script>window.__shellFixture = '$marker';</script>",
                "text/html", "UTF-8", null)
        }
        // Rapidly replacing an in-flight remote navigation can omit onPageFinished on older
        // providers. The fixture's executed marker is the readiness condition we actually need.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        var actual: String
        do {
            actual = evaluate(scenario, view, "window.__shellFixture")
            if (actual == JSONObject.quote(marker)) return
            Thread.sleep(100)
        } while (System.nanoTime() < deadline)
        assertEquals("Fixture script did not run", JSONObject.quote(marker), actual)
    }

    private fun evaluate(
        scenario: ActivityScenario<ExamActivity>, view: WebView, script: String
    ): String {
        val evaluated = CountDownLatch(1)
        var result = ""
        scenario.onActivity {
            view.evaluateJavascript(script) { value ->
                result = value
                evaluated.countDown()
            }
        }
        assertTrue("JavaScript did not finish", evaluated.await(10, TimeUnit.SECONDS))
        return result
    }

    private fun evaluateText(scenario: ActivityScenario<ExamActivity>, view: WebView, script: String): String =
        org.json.JSONTokener(evaluate(scenario, view, script)).nextValue() as String
}
