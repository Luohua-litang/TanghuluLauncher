package com.tanghulu.launcher.ui.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanghulu.launcher.ui.AppState
import com.tanghulu.launcher.ui.VersionOption
import com.tanghulu.launcher.ui.components.CardShape
import com.tanghulu.launcher.ui.components.StaggerInItem
import com.tanghulu.launcher.ui.deleteVersion
import com.tanghulu.launcher.ui.launchGame
import com.tanghulu.launcher.ui.loadVersions
import com.tanghulu.launcher.ui.selectVersion

@Composable
fun VersionsPage(state: AppState) {
    var installing by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val playedKeys = remember { mutableStateOf(setOf<String>()) }
    val query = state.versionQuery
    val filtered = remember(state.versions, query) {
        val q = query.trim()
        if (q.isEmpty()) state.versions else state.versions.filter { it.id.contains(q, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("游戏版本", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(state.versionStatus, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = { state.loadVersions() }) {
                Icon(Icons.Rounded.Refresh, "刷新", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query, onValueChange = { state.versionQuery = it },
            placeholder = { Text("搜索版本，例如 1.20.4") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(16.dp))
        if (state.versionsLoading && state.versions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("加载中…") }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 260.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(filtered, key = { _, v -> v.id }) { index, v ->
                    StaggerInItem(index, v.id in playedKeys.value, { playedKeys.value = playedKeys.value + v.id }) {
                        VersionCard(v, state, v.id == state.selectedVersionId, onInstall = { installing = v.id }, onDelete = { deleting = v.id })
                    }
                }
            }
        }
    }

    installing?.let { vid -> InstallDialog(vid, state) { installing = null } }

    deleting?.let { vid ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除本地版本") },
            text = { Text("确定要删除 \"$vid\" 吗？该版本目录将被移除，此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = { state.deleteVersion(vid); deleting = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun VersionCard(
    v: VersionOption,
    state: AppState,
    selected: Boolean,
    onInstall: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        onClick = { state.selectVersion(v.id) },
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VersionIcon(v.type)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.id, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(typeLabel(v.type), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "发布时间 " + (v.releaseTime ?: "未知"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(14.dp))
            if (v.local) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { state.selectVersion(v.id); state.launchGame() }, modifier = Modifier.weight(1f)) {
                        Text("启动")
                    }
                    OutlinedButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Icon(Icons.Rounded.Delete, "删除", Modifier.size(18.dp))
                    }
                }
            } else {
                Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                    Text("安装")
                }
            }
        }
    }
}

@Composable
private fun VersionIcon(type: String, size: Dp = 40.dp) {
    val top = when (type) {
        "snapshot" -> Color(0xFFFFC266)
        "old_beta" -> Color(0xFF7EC8F2)
        "old_alpha" -> Color(0xFFB9C4CC)
        else -> Color(0xFF6BBD5E)
    }
    val side = when (type) {
        "snapshot" -> Color(0xFFD99A3D)
        "old_beta" -> Color(0xFF4C9ED4)
        "old_alpha" -> Color(0xFF8E9AA6)
        else -> Color(0xFF8D6E63)
    }
    val sideDark = when (type) {
        "snapshot" -> Color(0xFFB57D2C)
        "old_beta" -> Color(0xFF3C7FA8)
        "old_alpha" -> Color(0xFF6F7A85)
        else -> Color(0xFF6B4F43)
    }
    Canvas(Modifier.size(size)) {
        val s = size.toPx()
        val h = s * 0.45f
        drawRect(top, size = Size(s, h))
        drawRect(sideDark, topLeft = Offset(0f, h), size = Size(s * 0.42f, s - h))
        drawRect(side, topLeft = Offset(s * 0.42f, h), size = Size(s * 0.58f, s - h))
        // Grass tuft detail
        val dot = s / 8f
        drawRect(Color(0xFF3E7A36), topLeft = Offset(s * 0.15f, h - dot * 0.8f), size = Size(dot, dot))
        drawRect(Color(0xFF84C96F), topLeft = Offset(s * 0.55f, h - dot * 0.8f), size = Size(dot, dot))
    }
}

private fun typeLabel(type: String): String = when (type) {
    "release" -> "正式版"
    "snapshot" -> "快照版"
    "old_beta" -> "旧 Beta"
    "old_alpha" -> "旧 Alpha"
    "本地" -> "本地版本"
    else -> type
}
