package com.example.nine_scholars_king_test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun OpenExamPanel(model: OpenExamViewModel, credentials: HostCredentials?, onOpen: (ExamEntry) -> Unit) {
    val form = model.form
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("连接与创建考试", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(form.base, { model.update(form.copy(base = it)) },
                label = { Text("测试环境地址") }, singleLine = true, enabled = !model.busy,
                modifier = Modifier.fillMaxWidth().testTag("open-base"))
            Text("考试 WebView 使用当前部署的测试环境；修改接口地址时须与考试页环境一致。",
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(form.externalUserId, { model.update(form.copy(externalUserId = it)) },
                label = { Text("external_user_id") }, singleLine = true, enabled = !model.busy,
                modifier = Modifier.fillMaxWidth().testTag("open-user"))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("paper_kind", modifier = Modifier.padding(top = 16.dp))
                listOf("mock" to "模拟考", "official" to "正式考").forEach { (value, label) ->
                    FilterChip(selected = form.paperKind == value,
                        onClick = { model.update(form.copy(paperKind = value)) }, enabled = !model.busy,
                        label = { Text("$value $label") }, modifier = Modifier.testTag("open-kind-$value"))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(form.levelId, { model.update(form.copy(levelId = it)) },
                    label = { Text("level_id") }, singleLine = true, enabled = !model.busy,
                    modifier = Modifier.weight(1f).testTag("open-level"))
                OutlinedTextField(form.paperId, { model.update(form.copy(paperId = it)) },
                    label = { Text("paper_id（正式考不传）") }, singleLine = true,
                    enabled = !model.busy && form.paperKind == "mock",
                    modifier = Modifier.weight(1f).testTag("open-paper"))
            }
            OutlinedTextField(form.requestId, { model.update(form.copy(requestId = it)) },
                label = { Text("request_id") }, singleLine = true, enabled = !model.busy,
                modifier = Modifier.fillMaxWidth().testTag("open-request"))
            Text("相同 request_id 用于重试同一次创建；换新后再次创建会重新扣次。",
                style = MaterialTheme.typography.bodySmall)
            // Two rows keep all actions reachable in compact landscape windows.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { model.update(form.copy(requestId = OpenExamForm.newRequestId())) },
                    enabled = !model.busy, modifier = Modifier.testTag("open-new-request")) { Text("换新 request_id") }
                TextButton(onClick = { model.run(OpenExamAction.LEVELS, credentials) }, enabled = !model.busy,
                    modifier = Modifier.testTag("open-levels")) { Text("等级列表") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { model.run(OpenExamAction.PAPERS, credentials) }, enabled = !model.busy,
                    modifier = Modifier.testTag("open-papers")) { Text("该等级模拟卷") }
                TextButton(onClick = { model.run(OpenExamAction.QUOTA, credentials) }, enabled = !model.busy,
                    modifier = Modifier.testTag("open-quota")) { Text("权益") }
            }
            Button(onClick = { model.run(OpenExamAction.CREATE, credentials) }, enabled = !model.busy,
                modifier = Modifier.fillMaxWidth().testTag("open-create")) { Text("创建考试并进入") }
            if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            model.lastExam?.let { exam ->
                TextButton(onClick = { onOpen(exam) }, enabled = !model.busy) {
                    Text("重新进入已创建场次 ${exam.examinationId}")
                }
            }
            Text("接口结果（查看等级/试卷 ID 后填入上方参数）", style = MaterialTheme.typography.titleMedium)
            SelectionContainer {
                Text(model.result, color = if (model.failed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("open-result"))
            }
        }
    }
}
