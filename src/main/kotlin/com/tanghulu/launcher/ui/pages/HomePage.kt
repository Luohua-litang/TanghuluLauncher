package com.tanghulu.launcher.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanghulu.launcher.ui.AppState
import com.tanghulu.launcher.ui.components.AnimatedAppear
import com.tanghulu.launcher.ui.components.CardShape
import com.tanghulu.launcher.ui.launchGame
import com.tanghulu.launcher.ui.selectVersion
import com.tanghulu.launcher.ui.components.PillShape
import com.tanghulu.launcher.ui.components.SkinAvatar
import com.tanghulu.launcher.ui.components.decodeImage
import com.tanghulu.launcher.util.SkinManager
import java.nio.file.Files

@Composable
fun HomePage(state: AppState) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AnimatedAppear(Modifier.weight(1.45f)) { HeroCard(state, Modifier.fillMaxWidth()) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AnimatedAppear(Modifier.fillMaxWidth(), delayMillis = 60) { ProfileCard(state) }
                AnimatedAppear(Modifier.fillMaxWidth(), delayMillis = 120) { LoaderCard(state) }
            }
        }
        AnimatedAppear(Modifier.fillMaxWidth(), delayMillis = 180) { LogCard(state) }
    }
}

@Composable
private fun HeroCard(state: AppState, modifier: Modifier = Modifier) {
    val grad = Brush.horizontalGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
    )
    Surface(modifier, shape = CardShape, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.background(grad).padding(28.dp)) {
            Text("准备开始你的冒险", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                state.selectedVersionId ?: "尚未选择版本",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            VersionPicker(state)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { state.launchGame() },
                enabled = !state.launching,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (state.launching) "启动中…" else "启 动 游 戏", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                state.launchStatus,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun VersionPicker(state: AppState) {
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(state.versions, query) {
        val q = query.trim()
        if (q.isEmpty()) state.versions.take(200)
        else state.versions.filter { it.id.contains(q, ignoreCase = true) }.take(200)
    }
    Box {
        Surface(
            shape = PillShape,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f),
            onClick = { open = true },
        ) {
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(state.selectedVersionId ?: "选择版本", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Rounded.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.width(300.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("搜索版本…") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                singleLine = true,
            )
            filtered.forEach { v ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(v.id)
                            if (v.local) {
                                Spacer(Modifier.width(6.dp))
                                Text("本地", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    },
                    onClick = { state.selectVersion(v.id); open = false; query = "" },
                )
            }
        }
    }
}

@Composable
private fun rememberSkinBitmap(state: AppState): ImageBitmap? {
    return remember(state.username, state.skinVersion) {
        val f = SkinManager.skinFile(state.username.trim())
        if (f != null && Files.isRegularFile(f)) runCatching { decodeImage(Files.readAllBytes(f)) }.getOrNull() else null
    }
}

@Composable
private fun ProfileCard(state: AppState) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            val bmp = rememberSkinBitmap(state)
            if (bmp != null) SkinAvatar(bmp, size = 56.dp)
            else Box(
                Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Text("?", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(state.username.ifBlank { "Steve" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(if (bmp != null) "本地皮肤已启用" else "使用在线皮肤", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LoaderCard(state: AppState) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("已安装加载器", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            if (state.installedLoaders.isEmpty()) {
                Text("当前版本未安装任何加载器", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.installedLoaders.forEach { (loader, version) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            Spacer(Modifier.width(8.dp))
                            Text(loader.displayName, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.weight(1f))
                            Text(version, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogCard(state: AppState) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("启动日志", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            if (state.logs.isEmpty()) {
                Text("暂无日志", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(Modifier.heightIn(max = 220.dp), reverseLayout = true) {
                    items(state.logs.takeLast(300)) { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
