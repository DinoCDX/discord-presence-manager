package com.dpm.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LIMIT = 128

@Composable
fun StaticEditor(ui: UiState, now: Long) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Your own status",
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            SavedTag(ui, now)
        }

        PresetsRow(ui)

        TypeChips(ui.sType) { ui.sType = it }
        Field(
            "Name",
            ui.sName,
            hint = if (ui.sName.isBlank()) "Required. This is the title Discord shows" else "${ui.sName.length}/$LIMIT"
        ) { ui.sName = it.take(LIMIT) }
        Field("Details", ui.sDetails, hint = "${ui.sDetails.length}/$LIMIT") { ui.sDetails = it.take(LIMIT) }
        Field("State", ui.sState, hint = "${ui.sState.length}/$LIMIT", last = true) { ui.sState = it.take(LIMIT) }

        Text("Images", style = MaterialTheme.typography.titleSmall, color = cs.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ImagePick("Large", ui.sLarge) { ui.setImage(true, it) }
            ImagePick("Small", ui.sSmall) { ui.setImage(false, it) }
        }
        if (ui.appId.isBlank()) {
            Text(
                "Images and buttons need an application ID (Settings).",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }

        TimerEditor(ui, now)

        Text("Buttons", style = MaterialTheme.typography.titleSmall, color = cs.primary)
        ButtonFields(1, ui.sB1L, ui.sB1U, { ui.sB1L = it.take(32) }, { ui.sB1U = it })
        ButtonFields(2, ui.sB2L, ui.sB2U, { ui.sB2L = it.take(32) }, { ui.sB2U = it })

        if (ui.sName.isNotEmpty() || ui.sDetails.isNotEmpty() || ui.sState.isNotEmpty()) {
            ExpTextButton(onClick = { ui.clearStatic() }, modifier = Modifier.align(Alignment.End)) {
                Text("Clear text")
            }
        }
    }
}

@Composable
private fun PresetsRow(ui: UiState) {
    var naming by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    Box {
        ExpOutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Presets")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ui.presets.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.label, maxLines = 1) },
                    onClick = { ui.applyPreset(p); open = false },
                    trailingIcon = {
                        IconButton(onClick = { ui.deletePreset(p) }) { Icon(Icons.Default.Close, "Delete preset") }
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Save current") },
                enabled = ui.sName.isNotBlank(),
                leadingIcon = { Icon(Icons.Default.Add, null) },
                onClick = { open = false; naming = true }
            )
        }
    }
    if (naming) {
        var label by remember { mutableStateOf(ui.sName) }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Save preset") },
            text = { Field("Preset name", label, last = true) { label = it.take(24) } },
            confirmButton = {
                ExpButton(onClick = { ui.savePreset(label); naming = false }) { Text("Save") }
            },
            dismissButton = { ExpTextButton(onClick = { naming = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ImagePick(label: String, file: String, onPicked: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val name = withContext(Dispatchers.IO) { StaticFiles.save(ctx, uri) }
                if (name != null) onPicked(name) else ctx.toast("Could not use that picture")
            }
        }
    }
    val bmp by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            StaticFiles.file(ctx, file)?.let { BitmapFactory.decodeFile(it.path) }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(88.dp).clip(MaterialTheme.shapes.large).background(cs.surfaceContainerHigh)
                .clickable { launcher.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            val b = bmp
            if (b != null) {
                Image(b.asImageBitmap(), null, Modifier.size(88.dp), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.Add, "Pick $label image", tint = cs.onSurfaceVariant)
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
        if (file.isNotEmpty()) {
            ExpTextButton(onClick = { onPicked("") }) { Text("Remove") }
        }
    }
}

@Composable
private fun TimerEditor(ui: UiState, now: Long) {
    val cs = MaterialTheme.colorScheme
    var picking by remember { mutableStateOf(false) }
    Text("Timer", style = MaterialTheme.typography.titleSmall, color = cs.primary)
    Segmented(
        listOf("Off", "Elapsed", "Countdown"),
        listOf("off", "up", "down").indexOf(ui.sTimerMode).coerceAtLeast(0),
        { ui.setTimerMode(listOf("off", "up", "down")[it]) }
    )
    when (ui.sTimerMode) {
        "up" -> {
            Text(
                "Started ${clock24(ui.sStarted)}, ${clock(now - ui.sStarted)} elapsed",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpTonalButton(onClick = { ui.resetElapsed() }) { Text("Reset to now") }
                ExpOutlinedButton(onClick = { picking = true }) { Text("Set start time") }
            }
        }
        "down" -> {
            val hours = ui.sDur / 60
            val mins = ui.sDur % 60
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Hours", hours.toString(), Modifier.weight(1f)) {
                    ui.setDuration((it.filter(Char::isDigit).take(2).toIntOrNull() ?: 0) * 60 + mins)
                }
                Field("Minutes", mins.toString(), Modifier.weight(1f), last = true) {
                    ui.setDuration(hours * 60 + (it.filter(Char::isDigit).take(2).toIntOrNull() ?: 0))
                }
            }
            val left = ui.sEnd - now
            Text(
                if (ui.sEnd > 0L && left > 0) "Ends in ${clock(left)}" else "Not running",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            ExpTonalButton(onClick = { ui.startCountdown() }) { Text(if (left > 0) "Restart countdown" else "Start countdown") }
        }
        else -> Text(
            "No timer.",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant
        )
    }
    if (picking) StartTimeDialog({ picking = false }) { h, m ->
        ui.setStartedAt(h, m)
        picking = false
    }
}

@Composable
private fun StartTimeDialog(onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    val cal = java.util.Calendar.getInstance()
    val state = rememberTimePickerState(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLargeIncreased,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(16.dp).fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("When did it start?", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                TimeInput(state)
                Text(
                    "A time still ahead today counts as yesterday.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ExpTextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    ExpButton(onClick = { onSave(state.hour, state.minute) }) { Text("Set") }
                }
            }
        }
    }
}

@Composable
private fun ButtonFields(n: Int, label: String, url: String, onLabel: (String) -> Unit, onUrl: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Field("Button $n label", label, hint = "${label.length}/32", onChange = onLabel)
        Field(
            "Button $n link",
            url,
            hint = if (url.isNotBlank() && !url.trim().startsWith("http")) "Must start with https://" else null,
            last = n == 2,
            onChange = onUrl
        )
    }
}

private fun clock24(t: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = t }
    return "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
}
