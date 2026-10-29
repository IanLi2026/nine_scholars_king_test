package com.example.nine_scholars_king_test

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.nine_scholars_king_test.ui.theme.Nine_scholars_king_testTheme

class MainActivity : ComponentActivity() {
    private var transferMode by mutableStateOf(CredentialTransferMode.NATIVE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        enableEdgeToEdge()
        val credentialsStore = HostCredentialsStore(this)
        val transferStore = CredentialTransferStore(this)
        transferMode = transferStore.load()
        val openExamModel = ViewModelProvider(this)[OpenExamViewModel::class.java]
        setContent {
            LaunchedEffect(openExamModel.pendingExam) {
                openExamModel.pendingExam?.let { exam ->
                    openExamModel.pendingExam = null
                    openExam(exam)
                }
            }
            var credentials by remember { mutableStateOf(runCatching { credentialsStore.loadOrDefault() }.getOrNull()) }
            var showSettings by remember { mutableStateOf(false) }
            Nine_scholars_king_testTheme {
                if (showSettings) {
                    HostSettingsDialog(credentials, onSave = { updated ->
                        val saved = runCatching { credentialsStore.save(updated) }.isSuccess
                        if (saved) {
                            credentials = updated
                            showSettings = false
                        }
                        saved
                    }, onDismiss = { showSettings = false })
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()
                                .testTag("exam-list").verticalScroll(rememberScrollState()).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    stringResource(R.string.choose_exam),
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                HostEnvironmentInfo()
                                Text(
                                    "填写创建参数，按需查询等级、模拟卷和权益，再创建考试进入答题。",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = { showSettings = true }, enabled = !openExamModel.busy,
                                    modifier = Modifier.testTag("host-settings-open")) {
                                    Text(stringResource(if (credentials == null)
                                        R.string.host_settings_required else R.string.host_settings))
                                }
                                credentials?.let { current ->
                                    SelectionContainer {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("appId: ${current.appId}",
                                                modifier = Modifier.testTag("credential-app-id"))
                                            Text("appKey: ${current.appKey}",
                                                modifier = Modifier.testTag("credential-app-key"))
                                        }
                                    }
                                }
                                CredentialTransferSelector(transferMode, onSelect = { mode ->
                                    transferMode = mode
                                    transferStore.save(mode)
                                })
                            }
                            OpenExamPanel(openExamModel, credentials, onOpen = ::openExam)
                        }
                    }
                }
            }
        }
    }

    private fun openExam(exam: ExamEntry) {
        startActivity(Intent(this, ExamActivity::class.java)
            .putExtra(ExamActivity.EXTRA_EXAM_ID, exam.examinationId)
            .putExtra(ExamActivity.EXTRA_EXAM_TOKEN, exam.token)
            .putExtra(ExamActivity.EXTRA_EXAM_TITLE, exam.title)
            .putExtra(ExamActivity.EXTRA_PAPER_KIND, exam.paperKind))
    }

    override fun onResume() {
        super.onResume()
        transferMode = CredentialTransferStore(this).load()
    }
}
