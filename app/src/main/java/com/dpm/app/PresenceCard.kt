package com.dpm.app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PresenceCard(info: Info?, now: Long, empty: String, imagesOn: Boolean, hasAppId: Boolean) {
    val cs = MaterialTheme.colorScheme
    if (info == null) {
        Text(empty, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        return
    }
    val isStatic = info.pkg == null
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val isPlaying = info.type == 0
        Text(
            if (isStatic && isPlaying) Presence.verb(info.type) else "${Presence.verb(info.type)} ${info.name}",
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = cs.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (imagesOn && (info.pkg != null || info.largeFile != null || info.smallFile != null)) {
                if (info.pkg != null) ArtBox(info) else StaticArtBox(info)
                Spacer(Modifier.width(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (isStatic && isPlaying) info.name else info.details ?: info.name,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (isStatic && isPlaying && !info.details.isNullOrBlank()) {
                    Text(info.details, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (!info.state.isNullOrBlank()) {
                    Text(info.state, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val extra = info.album ?: info.largeText
                if (!extra.isNullOrBlank()) {
                    Text(
                        extra,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        val s = info.start
        val e = info.end
        if (s != null && e != null && e > s) {
            val done = (now - s).coerceIn(0L, e - s)
            LinearWavyProgressIndicator(
                progress = { (done.toFloat() / (e - s)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(clock(done), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text(clock(e - s), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        } else if (s != null) {
            Text("${clock(now - s)} elapsed", style = MaterialTheme.typography.labelLarge, color = cs.primary)
        } else if (e != null && e > now) {
            Text("${clock(e - now)} left", style = MaterialTheme.typography.labelLarge, color = cs.primary)
        }

        if (info.buttons.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                info.buttons.forEach { (label, _) ->
                    Box(
                        Modifier.weight(1f).clip(MaterialTheme.shapes.medium)
                            .background(cs.surfaceContainerHighest).padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

    }
}

@Composable
private fun StaticArtBox(info: Info) {
    val ctx = LocalContext.current
    val big = rememberPicture(info.largeFile.orEmpty())
    val small = rememberPicture(info.smallFile.orEmpty())
    val main = big ?: small
    val badge = if (big != null) small else null
    Box(Modifier.size(80.dp)) {
        if (main != null) {
            Image(
                bitmap = main.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(24.dp))
            )
        }
        if (badge != null) {
            Image(
                bitmap = badge.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(6.dp, 6.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape)
            )
        }
    }
}

@Composable
private fun ArtBox(info: Info) {
    val ctx = LocalContext.current
    val pkg = info.pkg ?: return
    val icon by produceState<Bitmap?>(AppIcons.cached(pkg, 192), pkg) {
        if (value == null) value = withContext(Dispatchers.IO) { AppIcons.get(ctx, pkg, 192) }
    }
    val cover = info.bitmap
    val big = cover ?: icon
    val small = if (cover != null) icon else null
    Box(Modifier.size(80.dp)) {
        if (big != null) {
            Image(
                bitmap = big.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(24.dp))
            )
        }
        if (small != null) {
            Image(
                bitmap = small.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(6.dp, 6.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape)
            )
        }
    }
}
