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

/** 把字节解码为 ImageBitmap（皮肤 / 网络图通用）。 */
fun decodeImage(bytes: ByteArray): ImageBitmap? = try {
    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
} catch (_: Exception) {
    null
}

/** 网络图片，自动加载 + 占位。 */
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

/** 圆形头像容器。 */
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

/** 圆角面板背景形状。 */
val CardShape = RoundedCornerShape(16.dp)
val PillShape = RoundedCornerShape(50)

/** 人类可读的字节大小。 */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}

/**
 * 首次进入组合时播放淡入 + 轻微上移的进场动画。
 * 可用 [delayMillis] 让多个组件错开依次出现（stagger）。
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
 * 从右侧水平飞入 + 淡入。可用 [delayMillis] 让多个组件依次飞入（stagger）。
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
 * 列表项的错峰（stagger）入场动画：淡入 + 轻微上移。
 * 每个项只播放一次，之后直接显示，避免在 Lazy 列表滚动回收重建时反复重播造成卡顿。
 *
 * @param index 在列表中的位置，用于计算错峰延迟（限制在前 8 个周期内，避免长时间等待）。
 * @param played 该项是否已播放过动画。
 * @param onShown 动画开始播放时回调，调用方应在此记录该项已播放。
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
