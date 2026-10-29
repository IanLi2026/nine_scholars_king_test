package com.example.nine_scholars_king_test

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun CredentialTransferSelector(
    selected: CredentialTransferMode,
    onSelect: (CredentialTransferMode) -> Unit,
    enabled: Boolean = true
) {
    Column {
        Text("凭据传入方式")
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CredentialTransferMode.entries.forEach { mode ->
                FilterChip(selected = selected == mode, enabled = enabled,
                    onClick = { if (selected != mode) onSelect(mode) },
                    label = { Text(mode.label) },
                    modifier = Modifier.testTag("transfer-mode-${mode.value}"))
            }
        }
    }
}
