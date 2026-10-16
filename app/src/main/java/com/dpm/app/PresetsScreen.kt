package com.dpm.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsScreen(ui: UiState) {
    val cs = MaterialTheme.colorScheme
    var previewing by remember { mutableStateOf<Preset?>(null) }
    var renaming by remember { mutableStateOf<Preset?>(null) }
    var deleting by remember { mutableStateOf<Preset?>(null) }
    BackHandler { ui.presetsOpen = false }

    Surface(Modifier.fillMaxSize(), color = cs.surface) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { ui.presetsOpen = false }) {
                    Icon(painterResource(R.drawable.ic_back), "Back")
                }
                Text(
                    "Presets",
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "Reorder, rename or preview your saved statuses. Use loads one into the static editor.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp)
            )
            if (ui.presets.isEmpty()) {
                SectionCard {
                    Text(
                        "No presets yet",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = cs.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Open the static editor, tap Presets, then Save current.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(ui.presets, key = { _, p -> p.id }) { i, p ->
                        PresetRow(
                            p = p,
                            first = i == 0,
                            last = i == ui.presets.lastIndex,
                            onUse = {
                                ui.applyPreset(p)
                                ui.presetsOpen = false
                            },
                            onPreview = { previewing = p },
                            onUp = { ui.movePreset(p, -1) },
                            onDown = { ui.movePreset(p, 1) },
                            onRename = { renaming = p },
                            onUpdate = { ui.overwritePreset(p) },
                            onDuplicate = { ui.duplicatePreset(p) },
                            onDelete = { deleting = p }
                        )
                    }
                }
            }
        }
    }

    previewing?.let { p ->
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { previewing = null }, sheetState = sheet) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(p.label, style = MaterialTheme.typography.titleLargeEmphasized, color = cs.onSurface)
                SectionCard {
                    PresenceCard(
                        info = p.toInfo(),
                        now = System.currentTimeMillis(),
                        empty = "Nothing to preview",
                        imagesOn = true,
                        hasAppId = ui.appId.isNotBlank()
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    renaming?.let { p ->
        var label by remember(p.id) { mutableStateOf(p.label) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename preset") },
            text = { Field("Preset name", label, last = true) { label = it.take(24) } },
            confirmButton = {
                ExpButton(
                    enabled = label.isNotBlank(),
                    onClick = {
                        ui.renamePreset(p, label)
                        renaming = null
                    }
                ) { Text("Save") }
            },
            dismissButton = { ExpTextButton(onClick = { renaming = null }) { Text("Cancel") } }
        )
    }

    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${p.label}?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                ExpTextButton(onClick = {
                    ui.deletePreset(p)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { ExpTextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PresetRow(
    p: Preset,
    first: Boolean,
    last: Boolean,
    onUse: () -> Unit,
    onPreview: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRename: () -> Unit,
    onUpdate: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val summary = listOf(Presence.verb(p.type), p.name.trim()).filter { it.isNotBlank() }.joinToString(" ")
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    p.label,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onUp, enabled = !first) {
                Icon(Icons.Default.KeyboardArrowUp, "Move up")
            }
            IconButton(onClick = onDown, enabled = !last) {
                Icon(Icons.Default.KeyboardArrowDown, "Move down")
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, "More options")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = {
                            menu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Update from editor") },
                        onClick = {
                            menu = false
                            onUpdate()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        onClick = {
                            menu = false
                            onDuplicate()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = {
                            menu = false
                            onDelete()
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            FilledTonalButton(
                onClick = onUse,
                modifier = Modifier.weight(1f),
                shapes = ButtonDefaults.shapes(
                    shape = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 12, bottomEndPercent = 12)
                )
            ) { Text("Use") }
            OutlinedButton(
                onClick = onPreview,
                modifier = Modifier.weight(1f),
                shapes = ButtonDefaults.shapes(
                    shape = RoundedCornerShape(topStartPercent = 12, bottomStartPercent = 12, topEndPercent = 50, bottomEndPercent = 50)
                )
            ) { Text("Preview") }
        }
    }
}
