package com.example.nine_scholars_king_test

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertIsSelected
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostSettingsTest {
    @get:Rule(order = 0)
    val credentialsRule = TestHostCredentialsRule(seedCredentials = false)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun providedCredentialsAreVisibleAndSettingsCanBeCancelled() {
        composeRule.onNodeWithTag("home-credentials-toggle").performScrollTo().performClick()
        composeRule.onNodeWithText("appId: ${ExamConfig.defaultCredentials.appId}").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("appKey: ${ExamConfig.defaultCredentials.appKey}").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("host-settings-open").performClick()
        composeRule.onNodeWithTag("host-settings-title").assertIsDisplayed()
        composeRule.onNodeWithText("appId").assertIsDisplayed()
        composeRule.onNodeWithText("appKey").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("选择考试").assertIsDisplayed()
    }

    @Test
    fun savesEncryptedCredentialsAndDisplaysValuesAfterRecreation() {
        composeRule.onNodeWithTag("host-settings-open").performClick()
        composeRule.onNodeWithText("appId").performTextReplacement("config-fixture-app")
        composeRule.onNodeWithText("appKey").performTextReplacement("config-fixture-secret")
        composeRule.onNodeWithText("保存").performClick()
        composeRule.onNodeWithText("接入配置").assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = HostCredentialsStore(context)
        assertEquals(HostCredentials("config-fixture-app", "config-fixture-secret"), store.load())
        val encrypted = context.getSharedPreferences("host_credentials", Context.MODE_PRIVATE)
            .getString("encrypted", null).orEmpty()
        assertFalse(encrypted.contains("config-fixture-app"))
        assertFalse(encrypted.contains("config-fixture-secret"))
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag("host-settings-open").performClick()
        composeRule.onNodeWithText("config-fixture-secret").assertIsDisplayed()
        composeRule.onNodeWithText("保存").performClick()
        assertEquals("config-fixture-secret", store.load()?.appKey)
    }

    @Test
    fun nativeInterfaceIsSelectedByDefaultWithoutSavedPreference() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("host_transfer", Context.MODE_PRIVATE).edit().remove("mode").commit()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag("transfer-mode-native").performScrollTo().assertIsSelected()
        assertEquals(CredentialTransferMode.NATIVE, CredentialTransferStore(context).load())
    }

    @Test
    fun transferModeCanBeSwitchedAndSurvivesRecreation() {
        composeRule.onNodeWithTag("transfer-mode-url").performScrollTo().performClick().assertIsSelected()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag("transfer-mode-url").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("transfer-mode-javascript").performScrollTo().performClick().assertIsSelected()
        composeRule.onNodeWithTag("transfer-mode-native").performScrollTo().performClick().assertIsSelected()
    }
}
