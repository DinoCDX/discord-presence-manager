package com.dpm.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

fun cleanNotes(raw: String): String =
    raw.lines().joinToString("\n") { line ->
        line.trimEnd()
            .replace(Regex("^#{1,6}\\s*"), "")
            .replace(Regex("^[*-]\\s+"), "• ")
            .replace("**", "")
            .replace("__", "")
    }.trim()

@Composable
fun ChangelogDialog(tag: String, notes: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tag.isEmpty()) "Changelog" else "Changelog $tag") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    cleanNotes(notes).ifEmpty { "No notes for this release." },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
