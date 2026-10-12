package com.dpm.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val LIMIT = 128

@Composable
fun StaticEditor(ui: UiState, now: Long) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Your own status",
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            SavedTag(ui, now)
        }
        TypeChips(ui.sType) { ui.sType = it }
        Field(
            "Name",
            ui.sName,
            hint = if (ui.sName.isBlank()) "Required. This is the title Discord shows" else "${ui.sName.length}/$LIMIT"
        ) { ui.sName = it.take(LIMIT) }
        Field("Details", ui.sDetails, hint = "${ui.sDetails.length}/$LIMIT") { ui.sDetails = it.take(LIMIT) }
        Field("State", ui.sState, hint = "${ui.sState.length}/$LIMIT", last = true) { ui.sState = it.take(LIMIT) }
        ToggleRow(
            "Show elapsed time",
            "Counts up from the moment you turn this on",
            ui.sTimer
        ) { ui.setTimer(it) }
        if (ui.sName.isNotEmpty() || ui.sDetails.isNotEmpty() || ui.sState.isNotEmpty()) {
            TextButton(onClick = { ui.clearStatic() }, modifier = Modifier.align(Alignment.End)) {
                Text("Clear all")
            }
        }
    }
}
