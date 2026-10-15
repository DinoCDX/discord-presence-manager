package com.dpm.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun SourcesCard(ui: UiState, found: Map<String, Info?>, winner: String?, live: Boolean) {
    val cs = MaterialTheme.colorScheme
    SectionCard("Sources and priority", Modifier.spatialSize()) {
        ui.srcOrder.forEachIndexed { index, key ->
            val on = ui.srcOn[key] != false
            val hit = found[key]
            val isWinner = live && on && winner == key
            val rowBg by animateColorAsState(
                if (isWinner) cs.primaryContainer else cs.surfaceContainerHigh,
                MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "srcRow"
            )
            val fg = if (isWinner) cs.onPrimaryContainer else cs.onSurface
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(rowBg)
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(36.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(if (isWinner) cs.primary else cs.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = if (isWinner) cs.onPrimary else cs.onSecondaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(Sources.label(key), style = MaterialTheme.typography.titleSmall, color = fg)
                }
                IconButton(onClick = { ui.moveSource(key, -1) }, enabled = index > 0) {
                    Icon(painterResource(R.drawable.ic_arrow_up), "Move up")
                }
                IconButton(onClick = { ui.moveSource(key, 1) }, enabled = index < ui.srcOrder.lastIndex) {
                    Icon(painterResource(R.drawable.ic_arrow_down), "Move down")
                }
                Switch(
                    checked = on,
                    onCheckedChange = { ui.setSourceOn(key, it) },
                    thumbContent = if (on) {
                        { Icon(Icons.Default.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                    } else {
                        null
                    }
                )
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}
