package com.tanghulu.launcher.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanghulu.launcher.core.DownloadSource
import com.tanghulu.launcher.ui.AppState
import com.tanghulu.launcher.ui.components.CardShape
import com.tanghulu.launcher.ui.components.PillShape
import com.tanghulu.launcher.ui.theme.AccentOptions
import com.tanghulu.launcher.util.JavaInfo
import com.tanghulu.launcher.util.JavaRuntime
import com.tanghulu.launcher.util.OperatingSystem
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsPage(state: AppState) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SettingSection("外观") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("深色模式", fontWeight = FontWeight.Medium)
                    Text("在深色与浅色主题间切换", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = state.darkMode, onCheckedChange = { state.darkMode = it; state.scheduleSave() })
            }
            Spacer(Modifier.height(12.dp))
            Text("强调色", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AccentOptions.forEach { opt ->
                    val selected = state.customAccent == null && state.accentName == opt.name
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(opt.color)
                            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { state.setAccent(opt.name) },
                    )
                }
                CustomColorPicker(state)
            }
        }

        SettingSection("Java 运行时") {
            JavaRuntimeSection(state)
        }

        SettingSection("内存与参数") {
            OutlinedTextField(
                value = state.memory, onValueChange = { state.memory = it; state.scheduleSave() },
                label = { Text("分配内存") },
                supportingText = { Text("例如 2 GB 或 2048 MB") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.jvmArgs, onValueChange = { state.jvmArgs = it; state.scheduleSave() },
                label = { Text("附加 JVM 参数") },
                supportingText = { Text("留空使用默认参数") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }

        SettingSection("下载") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("下载源", fontWeight = FontWeight.Medium)
                    Text("选择版本与资源下载的来源", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                SourceMenu(state)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("下载游戏资源", fontWeight = FontWeight.Medium)
                    Text("启动前自动下载缺失的资源文件", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = state.downloadAssets, onCheckedChange = { state.downloadAssets = it; state.scheduleSave() })
            }
        }

        SettingSection("游戏目录") {
            OutlinedTextField(
                value = state.gameDir, onValueChange = { state.gameDir = it; state.scheduleSave() },
                label = { Text("游戏目录") },
                supportingText = { Text("留空使用默认目录") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = { openFolder(state.effectiveGameDir().toString()) }) {
                Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("打开目录")
            }
        }
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable () -> Unit) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SourceMenu(state: AppState) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(shape = PillShape, color = MaterialTheme.colorScheme.surfaceVariant, onClick = { open = true }) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.source.getName(), fontWeight = FontWeight.Medium)
                Icon(Icons.Rounded.ArrowDropDown, null)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DownloadSource.all().forEach { s ->
                DropdownMenuItem(text = { Text(s.getName()) }, onClick = { state.source = s; state.scheduleSave(); open = false })
            }
        }
    }
}

@Composable
private fun CustomColorPicker(state: AppState) {
    var hex by remember(state.accentName, state.customAccent) {
        mutableStateOf(state.customAccent?.let { colorToHex(it) } ?: "")
    }
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(
                state.customAccent ?: MaterialTheme.colorScheme.surfaceVariant
            ).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).clickable { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(Modifier.padding(12.dp)) {
                Text("自定义颜色", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = hex, onValueChange = { v ->
                        hex = v
                        parseHex(v)?.let { state.setCustomAccent(it) }
                    },
                    placeholder = { Text("#RRGGBB") },
                    singleLine = true,
                )
            }
        }
    }
}

private fun parseHex(s: String): Color? {
    val h = s.trim().removePrefix("#")
    if (h.length != 6) return null
    return runCatching { Color(h.toLong(16) or 0xFF000000L) }.getOrNull()
}

private fun colorToHex(c: Color): String =
    String.format("#%02x%02x%02x", (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

private fun openFolder(path: String) {
    val dir = File(path)
    if (!dir.exists()) dir.mkdirs()
    runCatching { Desktop.getDesktop().open(dir) }
}

@Composable
private fun JavaRuntimeSection(state: AppState) {
    LaunchedEffect(Unit) { state.scanJavas() }

    val current = state.javaPath.trim()
    var currentMajor by remember { mutableStateOf(-1) }
    LaunchedEffect(current) {
        currentMajor = if (current.isEmpty()) -1
        else withContext(Dispatchers.IO) { JavaRuntime.detectMajor(Path.of(current)) }
    }

    OutlinedTextField(
        value = state.javaPath, onValueChange = { state.javaPath = it; state.scheduleSave() },
        label = { Text("Java 可执行文件路径") },
        placeholder = { Text("例如 C:\\Program Files\\Java\\jdk-17\\bin\\java.exe") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        trailingIcon = { if (currentMajor > 0) VersionBadge(currentMajor) },
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { state.scanJavas(force = true) }, enabled = !state.javaScanning) {
            Icon(Icons.Rounded.Search, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (state.javaScanning) "扫描中…" else "扫描本机")
        }
        OutlinedButton(onClick = { pickJava(state) }) {
            Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("手动选择")
        }
    }

    if (state.detectedJavas.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text(
            "已检测到 ${state.detectedJavas.size} 个 Java 运行时，点击即可选用",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.detectedJavas.forEach { info ->
                JavaItem(info, info.path.toString() == current) {
                    state.javaPath = info.path.toString()
                    state.scheduleSave()
                }
            }
        }
    }
}

@Composable
private fun JavaItem(info: JavaInfo, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VersionBadge(info.major)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    info.homeName.ifBlank { info.path.toString() },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    info.path.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "使用中",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun VersionBadge(major: Int) {
    Surface(shape = PillShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
        Text(
            "Java $major",
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun pickJava(state: AppState) {
    val exe = OperatingSystem.CURRENT_OS.javaExecutable()
    val fd = FileDialog(null as Frame?, "选择 Java 可执行文件", FileDialog.LOAD)
    fd.file = exe
    fd.setFilenameFilter { _, name -> name.equals(exe, ignoreCase = true) }
    fd.isVisible = true
    val f = fd.files?.firstOrNull()
    if (f != null) {
        state.javaPath = f.absolutePath
        state.scheduleSave()
        state.scanJavas(force = true)
    }
}
