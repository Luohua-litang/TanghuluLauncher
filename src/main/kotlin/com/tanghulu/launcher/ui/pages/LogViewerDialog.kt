package com.tanghulu.launcher.ui.pages

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.tanghulu.launcher.ui.AppState
import com.tanghulu.launcher.util.Log
import com.tanghulu.launcher.util.OperatingSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * 启动器日志查看器：加载完整日志文件（不截断），支持导出、复制全部、打开日志目录。
 * 最新日志显示在顶部（reverseLayout）。
 */
@Composable
fun LogViewerDialog(state: AppState, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    // 完整日志来自日志文件（文件保留全部行，内存列表最多 5000 行会裁剪）
    var fullLogs by remember { mutableStateOf<List<String>?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        fullLogs = withContext(Dispatchers.IO) {
            val f = Log.logFile()
            if (f != null && Files.isRegularFile(f)) {
                runCatching { Files.readAllLines(f, StandardCharsets.UTF_8) }.getOrNull()
            } else null
        }
        loading = false
    }

    val displayLogs = fullLogs ?: state.logs.toList()

    DialogWindow(
        onCloseRequest = onDismiss,
        title = "启动器日志",
        resizable = true,
        state = rememberDialogState(size = DpSize(760.dp, 560.dp)),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("启动器日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "共 ${displayLogs.size} 行 · 完整日志 · 最新在上",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = { exportLogs(state, displayLogs.joinToString("\n")) }) {
                        Icon(Icons.Rounded.SaveAlt, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("导出日志")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(displayLogs.joinToString("\n"))) }) {
                        Text("复制全部")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { openLogDir() }) {
                        Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("日志目录")
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, "关闭")
                    }
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
                    Box(Modifier.fillMaxWidth().height(1.dp))
                }
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("正在加载完整日志…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    displayLogs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> LazyColumn(Modifier.fillMaxSize().padding(16.dp), reverseLayout = true) {
                        items(displayLogs) { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 1.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 弹出保存对话框，将完整日志写入用户指定的文件。 */
private fun exportLogs(state: AppState, content: String) {
    val fd = FileDialog(null as Frame?, "导出日志", FileDialog.SAVE)
    fd.file = "tanghulu-launcher.log"
    fd.isVisible = true
    val dir = fd.directory
    val file = fd.file
    if (dir != null && file != null) {
        val target = File(dir, file)
        state.scope.launch(Dispatchers.IO) {
            try {
                Files.write(target.toPath(), content.toByteArray(StandardCharsets.UTF_8))
                Log.info("日志已导出到: ${target.absolutePath}")
            } catch (e: Exception) {
                Log.error("导出日志失败: ${e.message}")
            }
        }
    }
}

/** 在系统文件管理器中打开日志目录。 */
private fun openLogDir() {
    val dir = OperatingSystem.appDataDir().resolve("logs").toFile()
    if (!dir.exists()) dir.mkdirs()
    runCatching { Desktop.getDesktop().open(dir) }
}
