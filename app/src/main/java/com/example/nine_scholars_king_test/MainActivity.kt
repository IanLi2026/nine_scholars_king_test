package com.example.nine_scholars_king_test

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
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
        val model = ViewModelProvider(this)[OpenExamViewModel::class.java]
        setContent {
            LaunchedEffect(model.pendingExam) {
                model.pendingExam?.let { exam ->
                    model.pendingExam = null
                    openExam(exam)
                }
            }
            var credentials by remember { mutableStateOf(runCatching { credentialsStore.loadOrDefault() }.getOrNull()) }
            var showSettings by remember { mutableStateOf(false) }
            Nine_scholars_king_testTheme {
                if (showSettings) {
                    HostSettingsDialog(credentials, onSave = { updated ->
                        val saved = runCatching { credentialsStore.save(updated) }.isSuccess
                        if (saved) { credentials = updated; showSettings = false }
                        saved
                    }, onDismiss = { showSettings = false })
                }
                Scaffold { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 12.dp)
                        .testTag("exam-list"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.medium) {
                                Text("考", modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.choose_exam), style = MaterialTheme.typography.headlineSmall)
                                Text("选择等级与试卷，开始本次考试", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { showSettings = true }, enabled = !model.busy,
                                modifier = Modifier.testTag("host-settings-open")) {
                                Text(stringResource(if (credentials == null) R.string.host_settings_required else R.string.host_settings))
                            }
                        }
                        OpenExamPanel(model, credentials, onOpen = ::openExam,
                            modifier = Modifier.weight(1f), connectionContent = {
                                var showCredentials by rememberSaveable { mutableStateOf(false) }
                                TextButton(onClick = { showCredentials = !showCredentials },
                                    modifier = Modifier.testTag("home-credentials-toggle")) {
                                    Text(if (showCredentials) "收起凭据" else "查看接入凭据")
                                }
                                if (showCredentials) credentials?.let { current ->
                                    SelectionContainer {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("appId: ${current.appId}", style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.testTag("credential-app-id"))
                                            Text("appKey: ${current.appKey}", style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.testTag("credential-app-key"))
                                        }
                                    }
                                }
                                CredentialTransferSelector(transferMode, onSelect = { mode ->
                                    transferMode = mode
                                    transferStore.save(mode)
                                }, enabled = !model.busy, stacked = true)
                                HorizontalDivider()
                                HostEnvironmentInfo()
                            })
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
