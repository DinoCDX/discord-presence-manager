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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "${Presence.verb(info.type)} ${info.name}",
            style = MaterialTheme.typography.labelLarge,
            color = cs.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (imagesOn && info.pkg != null) {
                ArtBox(info)
                Spacer(Modifier.width(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    info.details ?: info.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!info.state.isNullOrBlank()) {
                    Text(info.state, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!info.album.isNullOrBlank()) {
                    Text(
                        info.album,
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
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(cs.surfaceVariant)) {
                Box(
                    Modifier
                        .fillMaxWidth((done.toFloat() / (e - s)).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(cs.primary)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(clock(done), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text(clock(e - s), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        } else if (s != null) {
            Text("${clock(now - s)} elapsed", style = MaterialTheme.typography.labelLarge, color = cs.primary)
        }

        if (imagesOn && info.pkg != null && !hasAppId) {
            Text(
                "Discord shows text only. Add an application ID in Settings to send images.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
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
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(16.dp))
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
