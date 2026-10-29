package com.example.nine_scholars_king_test

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
        enableEdgeToEdge()
        val credentialsStore = HostCredentialsStore(this)
        val transferStore = CredentialTransferStore(this)
        transferMode = transferStore.load()
        setContent {
            var credentials by remember { mutableStateOf(runCatching { credentialsStore.loadOrDefault() }.getOrNull()) }
            var showSettings by remember { mutableStateOf(false) }
            var pendingExam by remember { mutableStateOf<ExamEntry?>(null) }
            Nine_scholars_king_testTheme {
                if (showSettings) {
                    HostSettingsDialog(credentials, onSave = { updated ->
                        val saved = runCatching { credentialsStore.save(updated) }.isSuccess
                        if (saved) {
                            credentials = updated
                            showSettings = false
                            pendingExam?.let { openExam(it) }
                            pendingExam = null
                        }
                        saved
                    }, onDismiss = { showSettings = false; pendingExam = null })
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        LazyColumn(
                            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()
                                .testTag("exam-list"),
                            contentPadding = PaddingValues(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        stringResource(R.string.choose_exam),
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                    Text(
                                        stringResource(R.string.choose_exam_hint),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(onClick = { showSettings = true },
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
                            }
                            items(ExamConfig.exams, key = { it.examinationId }) { exam ->
                                Card(
                                    onClick = {
                                        if (credentials == null) {
                                            pendingExam = exam
                                            showSettings = true
                                        } else {
                                            openExam(exam)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                        .testTag("exam-entry-${exam.title}")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(exam.title, style = MaterialTheme.typography.titleLarge)
                                            Text(
                                                stringResource(R.string.mock_exam),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            stringResource(R.string.enter_exam),
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openExam(exam: ExamEntry) {
        startActivity(Intent(this, ExamActivity::class.java)
            .putExtra(ExamActivity.EXTRA_EXAM_ID, exam.examinationId))
    }

    override fun onResume() {
        super.onResume()
        transferMode = CredentialTransferStore(this).load()
    }
}
