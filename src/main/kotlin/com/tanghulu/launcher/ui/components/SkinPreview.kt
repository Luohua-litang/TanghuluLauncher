package com.tanghulu.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Skin pixel sampler. Skin textures are 64x64 (new) or 64x32 (legacy).
 * Maps UV coordinates (0..64) to actual pixels.
 */
private class SkinSampler(val pm: PixelMap) {
    val w = pm.width
    val h = pm.height
    val isNew = h >= 64

    private fun c(u: Int, v: Int): Color {
        val x = (u * w / 64).coerceIn(0, w - 1)
        val y = (v * h / 64).coerceIn(0, h - 1)
        return pm[x, y]
    }

    fun head(x: Int, y: Int) = c(8 + x, 8 + y)
    fun body(x: Int, y: Int) = c(20 + x, 20 + y)
    fun rightArm(x: Int, y: Int) = c(44 + x, 20 + y)
    fun leftArm(x: Int, y: Int) = if (isNew) c(32 + x, 20 + y) else c(32 + x, 20 + y)
    fun rightLeg(x: Int, y: Int) = c(4 + x, 20 + y)
    fun leftLeg(x: Int, y: Int) = if (isNew) c(20 + x, 52 + y) else c(12 + x, 20 + y)
}

private fun DrawScope.drawRegion(gx: Int, gy: Int, gw: Int, gh: Int, cell: Float, sample: (Int, Int) -> Color) {
    for (y in 0 until gh) for (x in 0 until gw) {
        val c = sample(x, y)
        if (c.alpha > 0.05f) {
            drawRect(c, topLeft = Offset((gx + x) * cell, (gy + y) * cell), size = Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

/** Circular skin avatar (the 8x8 front of the head, enlarged). */
@Composable
fun SkinAvatar(bitmap: ImageBitmap, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val sampler = remember(bitmap) { SkinSampler(bitmap.toPixelMap()) }
    val bg = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier.size(size).clip(CircleShape)) {
        drawCircle(bg)
        val cell = size.toPx() / 8f
        drawRegion(0, 0, 8, 8, cell) { x, y -> sampler.head(x, y) }
        drawCircle(outline, radius = size.toPx() / 2f, style = Stroke(width = 1.5f))
    }
}

/** Full-body skin figure (blocky character, 1:2 aspect ratio). */
@Composable
fun SkinFigure(bitmap: ImageBitmap, modifier: Modifier = Modifier, height: Dp = 180.dp) {
    val sampler = remember(bitmap) { SkinSampler(bitmap.toPixelMap()) }
    Canvas(modifier.height(height).aspectRatio(0.5f)) {
        val cell = size.width / 16f
        drawRegion(4, 0, 8, 8, cell) { x, y -> sampler.head(x, y) }          // head
        drawRegion(4, 8, 8, 12, cell) { x, y -> sampler.body(x, y) }         // body
        drawRegion(0, 8, 4, 12, cell) { x, y -> sampler.leftArm(x, y) }      // left arm
        drawRegion(12, 8, 4, 12, cell) { x, y -> sampler.rightArm(x, y) }    // right arm
        drawRegion(4, 20, 4, 12, cell) { x, y -> sampler.leftLeg(x, y) }     // left leg
        drawRegion(8, 20, 4, 12, cell) { x, y -> sampler.rightLeg(x, y) }    // right leg
    }
}
