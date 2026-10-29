package com.example.nine_scholars_king_test

import android.content.Intent
import android.os.Build
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertTextContains
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExamListTest {
    @get:Rule(order = 0)
    val credentialsRule = TestHostCredentialsRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun startupAndActivityRecreationKeepListUsable() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, composeRule.activity.requestedOrientation)
        composeRule.onNodeWithText("选择考试").assertIsDisplayed()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("选择考试").assertIsDisplayed()
        composeRule.onNodeWithTag("open-create").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("open-request").performScrollTo().performTextReplacement("request-persist-fixture")
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag("open-request").performScrollTo().assertTextContains("request-persist-fixture")
    }

    @Test
    fun rendererCrashShowsRetryAndCreatesNewWebView() {
        assumeTrue(Build.VERSION.SDK_INT >= 26)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(ExamActivity::class.java.name, null, false)
        try {
            instrumentation.runOnMainSync {
                composeRule.activity.startActivity(Intent(composeRule.activity, ExamActivity::class.java)
                    .putExtra(ExamActivity.EXTRA_EXAM_ID, ExamConfig.exams.first().examinationId))
            }
            val activity = requireNotNull(
                instrumentation.waitForMonitorWithTimeout(monitor, 5000) as? ExamActivity
            )
            var original: WebView? = null
            instrumentation.runOnMainSync {
                original = findWebView(activity.window.decorView)
                requireNotNull(original).loadUrl("chrome://crash")
            }
            composeRule.waitUntil(timeoutMillis = 15000) {
                composeRule.onAllNodesWithText("网页组件意外停止，请点击重试重新打开本场考试。")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            assertFalse(activity.isFinishing)
            composeRule.onNodeWithText("重试").performClick()
            composeRule.waitUntil(timeoutMillis = 5000) {
                var recovered = false
                instrumentation.runOnMainSync {
                    val replacement = findWebView(activity.window.decorView)
                    recovered = replacement != null && replacement !== original &&
                        replacement.url == ExamConfig.exams.first().url
                }
                recovered
            }
            instrumentation.runOnMainSync { activity.finish() }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun returnedDynamicSessionOpensWebViewAndClosingReturnsToForm() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val exam = ExamEntry("HSK 测试", "2032384598771499999", "fixture-token-a+b", "official")
        val monitor = instrumentation.addMonitor(ExamActivity::class.java.name, null, false)
        try {
            instrumentation.runOnMainSync {
                composeRule.activity.startActivity(Intent(composeRule.activity, ExamActivity::class.java)
                    .putExtra(ExamActivity.EXTRA_EXAM_ID, exam.examinationId)
                    .putExtra(ExamActivity.EXTRA_EXAM_TOKEN, exam.token)
                    .putExtra(ExamActivity.EXTRA_EXAM_TITLE, exam.title)
                    .putExtra(ExamActivity.EXTRA_PAPER_KIND, exam.paperKind))
            }
            val activity = requireNotNull(instrumentation.waitForMonitorWithTimeout(monitor, 5000) as? ExamActivity)
            composeRule.waitUntil(timeoutMillis = 5000) {
                var matches = false
                instrumentation.runOnMainSync { matches = findWebView(activity.window.decorView)?.url == exam.url }
                matches
            }
            instrumentation.runOnMainSync { activity.finish() }
            composeRule.waitUntil(timeoutMillis = 5000) {
                composeRule.onAllNodesWithTag("open-create").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("open-create").performScrollTo().assertIsDisplayed()
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test
    fun invalidLevelShowsErrorWithoutCreatingExam() {
        composeRule.onNodeWithTag("open-level").performScrollTo().performTextReplacement("0")
        composeRule.onNodeWithTag("open-create").performScrollTo().performClick()
        composeRule.onNodeWithTag("open-result").performScrollTo().assertTextContains("level_id 须为正整数", substring = true)
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findWebView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
