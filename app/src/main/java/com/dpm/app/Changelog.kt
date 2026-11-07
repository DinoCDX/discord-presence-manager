package com.dpm.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    val cs = MaterialTheme.colorScheme
    val releases = Updater.history
    val status = Updater.historyStatus
    LaunchedEffect(Unit) { Updater.loadHistory() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tag.isEmpty()) "Changelog" else "Changelog") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                if (releases.isEmpty()) {
                    item {
                        Text(
                            cleanNotes(notes).ifEmpty { "No notes for this release." },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (status == "loading") {
                        item {
                            Text(
                                "Loading earlier versions...",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }
                    if (status == "failed") {
                        item {
                            Text(
                                "Couldn't load earlier versions. Check your connection and open this again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }
                } else {
                    items(releases, key = { it.tag }) { r ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(
                                listOf(r.tag, r.date).filter { it.isNotEmpty() }.joinToString("  ·  "),
                                style = MaterialTheme.typography.titleSmall,
                                color = cs.primary
                            )
                            Text(
                                cleanNotes(r.body).ifEmpty { "No notes." },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            HorizontalDivider(Modifier.padding(top = 10.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
