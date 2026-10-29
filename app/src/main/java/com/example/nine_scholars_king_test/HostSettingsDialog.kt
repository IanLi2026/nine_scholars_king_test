package com.example.nine_scholars_king_test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun HostSettingsDialog(
    initial: HostCredentials?,
    onSave: (HostCredentials) -> Boolean,
    onDismiss: () -> Unit
) {
    var appId by remember { mutableStateOf(initial?.appId.orEmpty()) }
    var appKey by remember { mutableStateOf(initial?.appKey.orEmpty()) }
    var saveFailed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.host_settings), Modifier.testTag("host-settings-title")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.host_settings_hint))
                TextButton(onClick = {
                    appId = ExamConfig.defaultCredentials.appId
                    appKey = ExamConfig.defaultCredentials.appKey
                }) { Text("使用本次测试凭据") }
                OutlinedTextField(
                    value = appId, onValueChange = { appId = it },
                    label = { Text("appId") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = appKey, onValueChange = { appKey = it },
                    label = { Text("appKey") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (saveFailed) Text(stringResource(R.string.host_settings_save_failed),
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = appId.isNotBlank() && appKey.isNotBlank(),
                onClick = {
                    saveFailed = !onSave(HostCredentials(appId.trim(), appKey))
                }
            ) { Text(stringResource(R.string.save_settings)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
