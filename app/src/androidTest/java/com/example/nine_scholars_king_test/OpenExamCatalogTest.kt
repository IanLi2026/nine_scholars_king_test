package com.example.nine_scholars_king_test

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.nine_scholars_king_test.ui.theme.Nine_scholars_king_testTheme
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** All responses are local fixtures; these tests never create a real exam. */
class OpenExamCatalogTest {
    @get:Rule(order = 0) val credentialsRule = TestHostCredentialsRule()
    @get:Rule(order = 1) val composeRule = createComposeRule()
    private val credentials = HostCredentials("fixture-app", "fixture-key")
    private val store = ViewModelStore()
    private lateinit var model: OpenExamViewModel
    private val requests = CopyOnWriteArrayList<OpenExamRequest>()
    private val failLevels = AtomicBoolean(false)
    private val emptyPapers = AtomicBoolean(false)

    private fun launch() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            model = OpenExamViewModel(
                InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
            ) { _, _, request ->
                requests.add(request)
                when {
                    request.path.startsWith("/mock/open/levels?") -> {
                        if (failLevels.get()) OpenExamResponse(200, """{"errcode":6,"errmsg":"列表鉴权失败"}""")
                        else if (request.path.endsWith("official")) OpenExamResponse(200,
                            """{"errcode":0,"data":{"levels":[{"level_id":31,"level_name":"New HSK1"}]}}""")
                        else OpenExamResponse(200,
                            """{"errcode":0,"data":{"levels":[{"level_id":31,"level_name":"New HSK1"},{"level_id":32,"level_name":"New HSK2"}]}}""")
                    }
                    request.path.contains("/papers?") -> {
                        val level = request.path.substringAfter("/levels/").substringBefore('/')
                        val paper = if (request.path.endsWith("=other-user")) "${level}777" else "${level}024"
                        OpenExamResponse(200, if (emptyPapers.get()) """{"errcode":0,"data":{"papers":[]}}"""
                            else """{"errcode":0,"data":{"papers":[{"paper_id":$paper,"name":"模拟卷 $level"},{"paper_id":${level}025,"name":"模拟卷二 $level"}]}}""")
                    }
                    request.path == "/mock/open/exams" -> OpenExamResponse(200,
                        """{"errcode":0,"data":{"examination_id":"2032384598771499999","exam_token":"fixture-token"}}""")
                    else -> error("Unexpected request ${request.path}")
                }
            }
            store.put("catalog-test", model)
        }
        composeRule.setContent {
            Nine_scholars_king_testTheme {
                Column(Modifier.fillMaxSize()) {
                    OpenExamPanel(model, credentials, onOpen = {})
                }
            }
        }
    }

    private fun awaitReady() = composeRule.waitUntil(timeoutMillis = 10000) { model.canCreate(credentials) }

    @After fun clear() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
    }

    @Test fun selectingServerLevelReloadsPapersAndCreationUsesSelectedIds() {
        launch()
        awaitReady()
        composeRule.onNodeWithTag("open-result").assertDoesNotExist()
        composeRule.onNodeWithTag("open-level").performScrollTo().assertTextContains("New HSK1", substring = true).performClick()
        composeRule.onNodeWithTag("open-level-option-32").performClick()
        awaitReady()
        composeRule.onNodeWithTag("open-paper").performScrollTo().assertTextContains("ID: 32024", substring = true).performClick()
        composeRule.onNodeWithTag("open-paper-option-32025").performClick()
        // Refreshing identical level data must still reload its papers and keep a valid selection.
        val paperReads = requests.count { it.path.contains("/papers?") }
        composeRule.onNodeWithTag("open-levels").performScrollTo().performClick()
        awaitReady()
        assertTrue(requests.count { it.path.contains("/papers?") } > paperReads)
        assertEquals("32025", model.form.paperId)
        composeRule.onNodeWithTag("open-create").performClick()
        composeRule.waitUntil(5000) { model.pendingExam != null }
        val body = JSONObject(requests.last { it.method == "POST" }.body)
        assertEquals(32, body.getInt("level_id"))
        assertEquals(32025, body.getInt("paper_id"))
        assertEquals("New HSK2", model.pendingExam?.title)
        assertTrue(requests.any { it.path.contains("/levels/32/papers?") })
    }

    @Test fun switchingToOfficialUsesItsLevelsAndHidesPaperSelection() {
        launch()
        awaitReady()
        composeRule.onNodeWithTag("open-kind-official").performScrollTo().performClick()
        awaitReady()
        composeRule.onNodeWithTag("open-paper").assertDoesNotExist()
        composeRule.onNodeWithTag("open-create").assertIsEnabled().performClick()
        composeRule.waitUntil(5000) { model.pendingExam != null }
        val body = JSONObject(requests.last { it.method == "POST" }.body)
        assertEquals("official", body.getString("paper_kind"))
        assertFalse(body.has("paper_id"))
        assertEquals("official", model.pendingExam?.paperKind)
    }

    @Test fun failedLevelsAndEmptyPapersDisableCreationAndCanBeRetried() {
        failLevels.set(true)
        emptyPapers.set(true)
        launch()
        composeRule.waitUntil(5000) { model.levelsError != null }
        composeRule.onNodeWithTag("open-create").assertIsNotEnabled()
        failLevels.set(false)
        composeRule.onNodeWithTag("open-levels").performScrollTo().performClick()
        composeRule.waitUntil(10000) { model.papersError != null }
        composeRule.onNodeWithTag("open-create").assertIsNotEnabled()
        emptyPapers.set(false)
        composeRule.onNodeWithTag("open-papers").performScrollTo().performClick()
        awaitReady()
        composeRule.onNodeWithTag("open-create").assertIsEnabled()
    }

    @Test fun changingExternalUserInvalidatesOldPaperSelection() {
        launch()
        awaitReady()
        assertEquals("31024", model.form.paperId)
        composeRule.onNodeWithTag("open-user").performScrollTo().performTextReplacement("other-user")
        composeRule.runOnIdle { assertFalse(model.canCreate(credentials)); assertTrue(model.papers.isEmpty()) }
        awaitReady()
        assertEquals("31777", model.form.paperId)
        assertTrue(requests.last().path.endsWith("external_user_id=other-user"))
    }
}
