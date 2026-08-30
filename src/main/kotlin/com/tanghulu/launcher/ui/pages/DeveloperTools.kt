package com.tanghulu.launcher.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.tanghulu.launcher.core.DownloadSource
import com.tanghulu.launcher.ui.AppState
import com.tanghulu.launcher.util.HttpUtil
import com.tanghulu.launcher.util.SysInfoGroup
import com.tanghulu.launcher.util.SystemInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 系统诊断面板：展示操作系统 / Java 运行时 / 启动器 / 磁盘与游戏目录大小等信息。
 */
@Composable
fun SystemInfoDialog(state: AppState, onDismiss: () -> Unit) {
    var groups by remember { mutableStateOf<List<SysInfoGroup>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        groups = withContext(Dispatchers.IO) { SystemInfo.collect(state.effectiveGameDir()) }
        loading = false
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        title = "系统诊断",
        resizable = true,
        state = rememberDialogState(size = DpSize(720.dp, 560.dp)),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("系统诊断", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭") }
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
                    Box(Modifier.fillMaxWidth().height(1.dp))
                }
                if (loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        groups.forEach { group ->
                            Column {
                                Text(
                                    group.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.height(8.dp))
                                group.items.forEach { item ->
                                    Column(Modifier.padding(vertical = 6.dp)) {
                                        Text(
                                            item.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(item.value, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class LatencyStatus { PENDING, TESTING, OK, FAIL }

private data class SourceLatency(
    val source: DownloadSource,
    val status: LatencyStatus,
    val latencyMs: Long,
    val error: String?,
)

/**
 * 下载源延迟测试：逐个 ping 各下载源的 version manifest 地址，测量连通性与响应延迟。
 */
@Composable
fun LatencyTestDialog(state: AppState, onDismiss: () -> Unit) {
    val results = remember {
        mutableStateListOf<SourceLatency>().apply {
            DownloadSource.all().forEach { add(SourceLatency(it, LatencyStatus.PENDING, -1L, null)) }
        }
    }
    var testing by remember { mutableStateOf(false) }

    fun runTest() {
        if (testing) return
        testing = true
        val scope = state.scope
        scope.launch {
            results.indices.forEach { i ->
                results[i] = results[i].copy(status = LatencyStatus.TESTING)
            }
            results.indices.forEach { i ->
                val src = results[i].source
                val url = src.manifestUrl()
                val outcome = runCatching { withContext(Dispatchers.IO) { HttpUtil.ping(url) } }
                outcome
                    .onSuccess { ms -> results[i] = results[i].copy(status = LatencyStatus.OK, latencyMs = ms, error = null) }
                    .onFailure { e -> results[i] = results[i].copy(status = LatencyStatus.FAIL, latencyMs = -1L, error = e.message) }
            }
            testing = false
        }
    }

    LaunchedEffect(Unit) { runTest() }

    DialogWindow(
        onCloseRequest = onDismiss,
        title = "下载源延迟测试",
        resizable = true,
        state = rememberDialogState(size = DpSize(640.dp, 420.dp)),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("下载源延迟测试", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "测试各下载源的连通性与响应延迟",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { runTest() }, enabled = !testing) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (testing) "测试中…" else "重新测试")
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭") }
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
                    Box(Modifier.fillMaxWidth().height(1.dp))
                }
                Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    results.forEach { r ->
                        SourceLatencyRow(r)
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceLatencyRow(r: SourceLatency) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(r.source.getName(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    r.source.manifestUrl(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            when (r.status) {
                LatencyStatus.PENDING -> Text("等待", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                LatencyStatus.TESTING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("测试中…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
                LatencyStatus.OK -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = Color(0xFF4CAF50))
                    Spacer(Modifier.width(6.dp))
                    Text(latencyText(r.latencyMs), fontWeight = FontWeight.SemiBold, color = latencyColor(r.latencyMs))
                }
                LatencyStatus.FAIL -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Error, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text("失败", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun latencyText(ms: Long): String = if (ms >= 0) "$ms ms" else "超时"

private fun latencyColor(ms: Long): Color = when {
    ms < 150 -> Color(0xFF4CAF50)
    ms < 400 -> Color(0xFFFF9800)
    else -> Color(0xFFE53935)
}
