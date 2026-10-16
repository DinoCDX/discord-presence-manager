package com.dpm.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

private const val FIELD_W = 1.6f
private const val FIELD_H = 2.2f
private const val SHIFT_FROM = 0.3f
private const val TAU = 6.2831855f
private const val MORPH_RATE = 0.6f

private class Piece(
    val start: Int,
    val x: Float,
    val y: Float,
    val size: Float,
    val sideways: Float,
    val vertical: Float,
    val spin: Float,
    val tint: Int,
    val depth: Float,
    val phase: Float
)

private val pieces = listOf(
    Piece(0, 0.10f, 0.05f, 220f, 0.45f, 0.18f, 0f, 0, 0.6f, 0.00f),
    Piece(1, 0.70f, 0.12f, 170f, -0.30f, 0.10f, 25f, 1, 0.9f, 0.31f),
    Piece(2, 0.25f, 0.38f, 240f, 0.60f, 0.22f, -15f, 2, 0.5f, 0.62f),
    Piece(3, 0.85f, 0.45f, 130f, -0.55f, 0.30f, 10f, 0, 1.0f, 0.13f),
    Piece(4, 0.05f, 0.70f, 180f, 0.35f, 0.12f, 40f, 1, 0.7f, 0.84f),
    Piece(0, 0.60f, 0.80f, 280f, -0.20f, 0.25f, -30f, 2, 0.4f, 0.47f),
    Piece(1, 0.40f, 0.25f, 110f, 0.80f, 0.40f, 60f, 0, 1.1f, 0.22f),
    Piece(2, 0.95f, 0.90f, 200f, -0.40f, 0.15f, 5f, 1, 0.8f, 0.71f)
)

private val morphPolygons: List<RoundedPolygon> = listOf(
    MaterialShapes.Sunny,
    MaterialShapes.Pill,
    MaterialShapes.Flower,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Cookie9Sided
)

private val morphs: List<Morph> = morphPolygons.mapIndexed { i, p ->
    Morph(p, morphPolygons[(i + 1) % morphPolygons.size])
}

private fun wrap(v: Float, period: Float): Float = ((v % period) + period) % period

private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

private fun blobShape(piece: Piece, pageX: () -> Float): Shape = GenericShape { size, _ ->
    val n = morphs.size
    val x = wrap(pageX() * MORPH_RATE + piece.start, n.toFloat())
    val index = floor(x).toInt() % n
    val t = smooth(x - floor(x))
    val w = size.width
    val h = size.height
    morphs[index].asCubics(t).forEachIndexed { k, c ->
        if (k == 0) moveTo(((c.anchor0X + 1f) / 2f) * w, ((c.anchor0Y + 1f) / 2f) * h)
        cubicTo(
            ((c.control0X + 1f) / 2f) * w, ((c.control0Y + 1f) / 2f) * h,
            ((c.control1X + 1f) / 2f) * w, ((c.control1Y + 1f) / 2f) * h,
            ((c.anchor1X + 1f) / 2f) * w, ((c.anchor1Y + 1f) / 2f) * h
        )
    }
    close()
}

@Composable
fun DecorBackground(scrollY: () -> Float, pageX: () -> Float) {
    val cs = MaterialTheme.colorScheme
    val tints = listOf(cs.primaryContainer, cs.tertiaryContainer, cs.secondaryContainer)
    val breath by rememberInfiniteTransition(label = "decor").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse),
        label = "breath"
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val fieldW = w * FIELD_W
        val fieldH = h * FIELD_H
        pieces.forEach { p ->
            val shape = remember(p) { blobShape(p, pageX) }
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (wrap(p.x * fieldW + pageX() * w * p.sideways, fieldW) - w * SHIFT_FROM).roundToInt(),
                            (wrap(p.y * fieldH - scrollY() * p.vertical * p.depth, fieldH) - h * SHIFT_FROM).roundToInt()
                        )
                    }
                    .size(p.size.dp)
                    .graphicsLayer {
                        val pulse = 1f + 0.05f * sin((breath + p.phase) * TAU)
                        rotationZ = p.spin + scrollY() * 0.02f * p.depth + pageX() * 6f
                        scaleX = pulse
                        scaleY = pulse
                        alpha = 0.45f + 0.35f * p.depth
                    }
                    .clip(shape)
                    .background(tints[p.tint].copy(alpha = 0.3f))
            )
        }
    }
}
