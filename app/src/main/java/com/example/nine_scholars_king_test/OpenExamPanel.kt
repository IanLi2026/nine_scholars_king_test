package com.example.nine_scholars_king_test

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun OpenExamPanel(
    model: OpenExamViewModel, credentials: HostCredentials?, onOpen: (ExamEntry) -> Unit,
    modifier: Modifier = Modifier, connectionContent: @Composable () -> Unit = {}
) {
    val form = model.form
    LaunchedEffect(form.base, form.paperKind, credentials) {
        delay(400)
        model.refreshLevels(credentials)
    }
    LaunchedEffect(form.base, form.paperKind, form.externalUserId, form.levelId, credentials, model.levelsRevision) {
        if (form.paperKind == "mock" && model.hasCurrentLevels(credentials)) {
            delay(400)
            model.refreshPapers(credentials)
        }
    }
    model.quota?.let { rows ->
        AlertDialog(onDismissRequest = { model.quota = null }, title = { Text("考试权益") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows.forEach { row ->
                        Column {
                            Text(row.title, style = MaterialTheme.typography.titleSmall)
                            Text("剩余 ${row.remaining} 次 · 已用 ${row.used} / 共 ${row.limit} 次",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { model.quota = null }) { Text("知道了") } })
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= 840.dp) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1.7f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        ExamSelectionCard(model, credentials)
                    }
                    ExamActions(model, credentials, onOpen)
                }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    ConnectionCard(model, connectionContent)
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ExamSelectionCard(model, credentials)
                    ConnectionCard(model, connectionContent)
                }
                ExamActions(model, credentials, onOpen)
            }
        }
    }
}

@Composable
private fun ExamSelectionCard(model: OpenExamViewModel, credentials: HostCredentials?) {
    val form = model.form
    var showRequest by rememberSaveable { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("考试信息", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { model.run(OpenExamAction.QUOTA, credentials) }, enabled = !model.busy,
                    modifier = Modifier.testTag("open-quota")) { Text("查看权益") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("mock" to "模拟考试", "official" to "正式考试").forEach { (value, label) ->
                    FilterChip(selected = form.paperKind == value,
                        onClick = { model.update(form.copy(paperKind = value)) }, enabled = !model.busy,
                        label = { Text(label) }, modifier = Modifier.testTag("open-kind-$value"))
                }
            }
            OutlinedTextField(form.externalUserId, { model.update(form.copy(externalUserId = it)) },
                label = { Text("考生标识") }, placeholder = { Text("external_user_id") },
                singleLine = true, enabled = !model.busy,
                modifier = Modifier.fillMaxWidth().testTag("open-user"))
            BoxWithConstraints {
                val level: @Composable () -> Unit = {
                    CatalogSelect("考试等级", form.levelId, model.levels, model.levelsLoading, !model.busy,
                        "请选择等级", onSelect = { model.update(form.copy(levelId = it)) }, tag = "open-level")
                }
                val paper: @Composable () -> Unit = {
                    CatalogSelect("模拟试卷", form.paperId, model.papers, model.papersLoading,
                        !model.busy && model.hasCurrentLevels(credentials), "请选择模拟卷",
                        onSelect = { model.update(form.copy(paperId = it)) }, tag = "open-paper")
                }
                if (maxWidth >= 480.dp && form.paperKind == "mock") {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f)) { level() }
                        Column(Modifier.weight(1.35f)) { paper() }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        level()
                        if (form.paperKind == "mock") paper()
                        else Text("正式考试由系统分配试卷", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { model.refreshLevels(credentials) }, enabled = !model.busy && !model.levelsLoading,
                    modifier = Modifier.testTag("open-levels")) { Text("刷新等级") }
                if (form.paperKind == "mock") TextButton(onClick = { model.refreshPapers(credentials) },
                    enabled = !model.busy && !model.papersLoading && model.hasCurrentLevels(credentials),
                    modifier = Modifier.testTag("open-papers")) { Text("刷新试卷") }
                TextButton(onClick = { showRequest = !showRequest }, enabled = !model.busy,
                    modifier = Modifier.testTag("open-request-settings")) {
                    Text(if (showRequest) "收起请求设置 ▴" else "请求设置 ▾")
                }
            }
            if (showRequest) {
                OutlinedTextField(form.requestId, { model.update(form.copy(requestId = it)) },
                    label = { Text("请求编号 · request_id") }, singleLine = true, enabled = !model.busy,
                    modifier = Modifier.fillMaxWidth().testTag("open-request"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("重试使用相同编号；换新后创建会重新扣次。",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { model.update(form.copy(requestId = OpenExamForm.newRequestId())) },
                        enabled = !model.busy, modifier = Modifier.testTag("open-new-request")) { Text("换新编号") }
                }
            }
            model.levelsError?.let {
                FormMessage("等级加载失败：$it，请点击“刷新等级”重试。", "open-level-error")
            }
            if (form.paperKind == "mock") model.papersError?.let {
                FormMessage("试卷加载失败：$it，请点击“刷新试卷”重试。", "open-paper-error")
            }
            model.notice?.let { FormMessage(it, "open-notice") }
            if (model.busy || model.levelsLoading || model.papersLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

        }
    }
}

@Composable
private fun ExamActions(model: OpenExamViewModel, credentials: HostCredentials?, onOpen: (ExamEntry) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val selected = model.levels.firstOrNull { it.id == model.form.levelId }
            Text(selected?.let { "已选：${it.name} · ${if (model.form.paperKind == "mock") "模拟考试" else "正式考试"}" }
                ?: "请选择考试等级与试卷", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { model.run(OpenExamAction.CREATE, credentials) }, enabled = model.canCreate(credentials),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("open-create")) {
                    Text(if (model.busy) "正在处理…" else "开始考试", style = MaterialTheme.typography.titleMedium)
                }
                model.lastExam?.let { exam ->
                    OutlinedButton(onClick = { onOpen(exam) }, enabled = !model.busy,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("继续上次考试") }
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(model: OpenExamViewModel, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("连接设置", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(model.form.base, { model.update(model.form.copy(base = it)) },
                label = { Text("接口地址") }, singleLine = true, enabled = !model.busy,
                modifier = Modifier.fillMaxWidth().testTag("open-base"))
            Text("请使用与考试页面一致的接口环境。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun FormMessage(message: String, tag: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
        Text(message, modifier = Modifier.fillMaxWidth().padding(12.dp).testTag(tag),
            color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CatalogSelect(
    label: String, selectedId: String, options: List<ExamCatalogOption>, loading: Boolean,
    enabled: Boolean, placeholder: String, onSelect: (String) -> Unit, tag: String
) {
    var expanded by remember { mutableStateOf(false) }
    val available = enabled && !loading && options.isNotEmpty()
    LaunchedEffect(available) { if (!available) expanded = false }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = available,
                shape = MaterialTheme.shapes.small, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag)) {
                Text(if (loading) "正在加载…" else options.firstOrNull { it.id == selectedId }?.label ?: placeholder,
                    modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge)
                Text("▾", modifier = Modifier.padding(start = 8.dp))
            }
            DropdownMenu(expanded = expanded && available, onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 320.dp).widthIn(max = 560.dp)) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option.label) }, onClick = {
                        expanded = false
                        onSelect(option.id)
                    }, modifier = Modifier.testTag("$tag-option-${option.id}"))
                }
            }
        }
    }
}
