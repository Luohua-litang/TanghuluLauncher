package com.tanghulu.launcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanghulu.launcher.util.HttpUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

/** Decode bytes into an ImageBitmap (shared by skins / network images). */
fun decodeImage(bytes: ByteArray): ImageBitmap? = try {
    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
} catch (_: Exception) {
    null
}

/** Network image with automatic loading + placeholder. */
@Composable
fun NetworkImage(url: String?, modifier: Modifier = Modifier, contentDescription: String? = null) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) { failed = true; return@LaunchedEffect }
        bitmap = null; failed = false
        val bytes = withContext(Dispatchers.IO) { try { HttpUtil.get(url) } catch (_: Exception) { null } }
        if (bytes == null) { failed = true; return@LaunchedEffect }
        val decoded = decodeImage(bytes)
        if (decoded == null) failed = true else bitmap = decoded
    }

    val b = bitmap
    when {
        b != null -> Image(b, contentDescription, modifier)
        failed -> Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

/** Circular avatar container. */
@Composable
fun AvatarFrame(bitmap: ImageBitmap?, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) Image(bitmap, null, Modifier.size(size).clip(CircleShape))
        else Text("?", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Rounded panel background shape. */
val CardShape = RoundedCornerShape(16.dp)
val PillShape = RoundedCornerShape(50)

/** Human-readable byte size. */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}

/**
 * Plays a fade-in + slight upward entrance animation when first entering composition.
 * Use [delayMillis] to stagger multiple components' appearance.
 */
@Composable
fun AnimatedAppear(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    content: @Composable () -> Unit,
) {
    val visibleState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        visibleState.targetState = true
    }
    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 8 },
    ) {
        content()
    }
}

/**
 * Slides in horizontally from the right + fades in. Use [delayMillis] to stagger multiple components.
 */
@Composable
fun AnimatedFlyIn(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    content: @Composable () -> Unit,
) {
    val visibleState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        visibleState.targetState = true
    }
    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = slideInHorizontally(tween(360)) { it } + fadeIn(tween(240)),
    ) {
        content()
    }
}

/**
 * Staggered entrance animation for list items: fade-in + slight upward movement.
 * Each item plays only once, then is shown directly, avoiding repeated replays when the Lazy list
 * recycles items during scrolling, which would cause jank.
 *
 * @param index position in the list, used to compute the stagger delay (capped within the first 8 cycles to avoid a long wait).
 * @param played whether this item has already played its animation.
 * @param onShown called when the animation starts playing; callers should record that the item has played here.
 */
@Composable
fun StaggerInItem(
    index: Int,
    played: Boolean,
    onShown: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val visibleState = remember { MutableTransitionState(played) }
    LaunchedEffect(Unit) {
        if (!played) {
            val d = (index % 8) * 40
            if (d > 0) delay(d.toLong())
            visibleState.targetState = true
            onShown()
        }
    }
    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 5 },
    ) {
        content()
    }
}
