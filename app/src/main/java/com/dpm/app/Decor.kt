package com.dpm.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val FIELD_W = 1.6f
private const val FIELD_H = 2.2f
private const val SHIFT_FROM = 0.3f

private class Piece(
    val shape: Int,
    val x: Float,
    val y: Float,
    val size: Float,
    val sideways: Float,
    val vertical: Float,
    val spin: Float,
    val tint: Int
)

private val pieces = listOf(
    Piece(0, 0.10f, 0.05f, 220f, 0.45f, 0.18f, 0f, 0),
    Piece(1, 0.70f, 0.12f, 170f, -0.30f, 0.10f, 25f, 1),
    Piece(2, 0.25f, 0.38f, 240f, 0.60f, 0.22f, -15f, 2),
    Piece(3, 0.85f, 0.45f, 130f, -0.55f, 0.30f, 10f, 0),
    Piece(4, 0.05f, 0.70f, 180f, 0.35f, 0.12f, 40f, 1),
    Piece(0, 0.60f, 0.80f, 280f, -0.20f, 0.25f, -30f, 2),
    Piece(1, 0.40f, 0.25f, 110f, 0.80f, 0.40f, 60f, 0),
    Piece(2, 0.95f, 0.90f, 200f, -0.40f, 0.15f, 5f, 1)
)

private fun wrap(v: Float, period: Float): Float = ((v % period) + period) % period

@Composable
fun DecorBackground(scrollY: () -> Float, pageX: () -> Float) {
    val cs = MaterialTheme.colorScheme
    val shapes: List<Shape> = listOf(
        MaterialShapes.Sunny.toShape(),
        MaterialShapes.Pill.toShape(),
        MaterialShapes.Flower.toShape(),
        MaterialShapes.Clover4Leaf.toShape(),
        MaterialShapes.Cookie9Sided.toShape()
    )
    val tints = listOf(cs.primaryContainer, cs.tertiaryContainer, cs.secondaryContainer)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val fieldW = w * FIELD_W
        val fieldH = h * FIELD_H
        pieces.forEach { p ->
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (wrap(p.x * fieldW + pageX() * w * p.sideways, fieldW) - w * SHIFT_FROM).roundToInt(),
                            (wrap(p.y * fieldH - scrollY() * p.vertical, fieldH) - h * SHIFT_FROM).roundToInt()
                        )
                    }
                    .size(p.size.dp)
                    .graphicsLayer { rotationZ = p.spin + scrollY() * 0.02f + pageX() * 6f }
                    .clip(shapes[p.shape])
                    .background(tints[p.tint].copy(alpha = 0.3f))
            )
        }
    }
}
