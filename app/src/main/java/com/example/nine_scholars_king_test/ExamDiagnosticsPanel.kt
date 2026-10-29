package com.example.nine_scholars_king_test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ExamDiagnosticsPanel(
    url: String,
    lastSignature: SigningDiagnostic?,
    timeReading: SigningTimeReading,
    timeStatus: String,
    expanded: Boolean,
    syncing: Boolean,
    canSync: Boolean,
    onCopy: () -> Unit,
    onExpand: () -> Unit,
    onSync: () -> Unit,
    onDateSettings: () -> Unit
) {
    Row(Modifier.fillMaxWidth()) {
        Text("加载 URL", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        TextButton(onClick = onCopy, enabled = url.isNotEmpty(),
            modifier = Modifier.testTag("copy-loaded-url")) { Text("复制 URL") }
        TextButton(onClick = onExpand) { Text(if (expanded) "收起诊断" else "时间诊断") }
    }
    SelectionContainer {
        Text(url.ifEmpty { "等待加载" }, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().heightIn(max = 80.dp)
                .verticalScroll(rememberScrollState()).testTag("loaded-url"))
    }
    if (expanded) {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.getDefault())
        Column(Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            HostEnvironmentInfo()
            Text("设备时间：${format.format(Date(timeReading.deviceMillis))}",
                style = MaterialTheme.typography.bodySmall)
            Text("签名时间来源：${if (timeReading.usesServerTime) "HTTPS 服务器时间" else "设备时间"}",
                style = MaterialTheme.typography.bodySmall)
            if (timeReading.usesServerTime) {
                Text("服务器时间：${format.format(Date(timeReading.signingMillis))}；服务器 − 设备：${timeReading.offsetSeconds} 秒",
                    style = MaterialTheme.typography.bodySmall)
            }
            Text(timeStatus, style = MaterialTheme.typography.bodySmall)
            lastSignature?.let { signature ->
                SelectionContainer {
                    Text("最近签名 timestamp：${signature.time.timestamp}（Unix 秒）\n" +
                        "${signature.method} ${ExamConfig.API_ORIGIN}${signature.requestUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("last-signing-request"))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSync, enabled = canSync && !syncing) {
                    Text(if (syncing) "正在检测时间…" else "检测服务器时间")
                }
                TextButton(onClick = onDateSettings) { Text("日期和时间设置") }
            }
        }
    }
}
