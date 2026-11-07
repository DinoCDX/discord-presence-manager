package com.dpm.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

private const val FIELD_W = 1.6f
private const val FIELD_H = 2.2f
private const val SHIFT_FROM = 0.3f
private const val MORPH_RATE = 0.6f
private const val DRIFT_X = 0.5f
private const val DRIFT_Y = 0.25f
private const val MIN_SIZE = 110f
private const val PIECE_COUNT = 8

private class Piece(
    val start: Int,
    val x: Float,
    val y: Float,
    val size: Float,
    val spin: Float,
    val tint: Color,
    val depth: Float
)

private val palette = listOf(
    Color(0xFFE53935),
    Color(0xFF1E88E5),
    Color(0xFF43A047)
)

private val sessionSeed: Long = Random.nextLong()

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

private val pieces: List<Piece> by lazy {
    val rnd = Random(sessionSeed)
    List(PIECE_COUNT) {
        val size = 110f + rnd.nextFloat() * 170f
        Piece(
            start = rnd.nextInt(morphs.size),
            x = rnd.nextFloat(),
            y = rnd.nextFloat(),
            size = size,
            spin = rnd.nextFloat() * 60f - 30f,
            tint = palette[rnd.nextInt(palette.size)],
            depth = (MIN_SIZE / size).coerceIn(0.35f, 1f)
        )
    }
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
                            (wrap(p.x * fieldW + pageX() * w * DRIFT_X * p.depth, fieldW) - w * SHIFT_FROM).roundToInt(),
                            (wrap(p.y * fieldH - scrollY() * DRIFT_Y * p.depth, fieldH) - h * SHIFT_FROM).roundToInt()
                        )
                    }
                    .size(p.size.dp)
                    .graphicsLayer {
                        rotationZ = p.spin + scrollY() * 0.02f * p.depth + pageX() * 6f
                        alpha = 0.45f + 0.35f * p.depth
                    }
                    .clip(shape)
                    .background(p.tint.copy(alpha = 0.3f))
            )
        }
    }
}
