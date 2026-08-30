package com.tanghulu.launcher.ui.pages

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanghulu.launcher.core.ModDownloader
import com.tanghulu.launcher.core.ModLoader
import com.tanghulu.launcher.ui.AppState
import com.tanghulu.launcher.ui.components.CardShape
import com.tanghulu.launcher.ui.components.NetworkImage
import com.tanghulu.launcher.ui.components.PillShape
import com.tanghulu.launcher.ui.components.StaggerInItem
import com.tanghulu.launcher.ui.downloadMod
import com.tanghulu.launcher.ui.loadTrendingMods
import com.tanghulu.launcher.ui.modrinthId
import com.tanghulu.launcher.ui.searchMods

private val ModrinthLoaders = ModLoader.values().filter { it.modrinthId() != null }

@Composable
fun ModsPage(state: AppState) {
    LaunchedEffect(Unit) { state.loadTrendingMods() }
    val playedKeys = remember { mutableStateOf(setOf<String>()) }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Mod 中心", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.modQuery, onValueChange = { state.modQuery = it },
                placeholder = { Text("搜索 Mod，例如 sodium") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            LoaderMenu(state)
            Button(onClick = { state.searchMods() }, modifier = Modifier.height(56.dp)) { Text("搜索") }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.modStatus, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            if (state.modProgress != null) {
                LinearProgressIndicator(
                    progress = { state.modProgress ?: 0f },
                    modifier = Modifier.width(160.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (state.modLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("搜索中…") }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(state.modResults, key = { _, mod -> mod.slug }) { index, mod ->
                    StaggerInItem(index, mod.slug in playedKeys.value, { playedKeys.value = playedKeys.value + mod.slug }) {
                        ModCard(mod, state)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoaderMenu(state: AppState) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(shape = PillShape, color = MaterialTheme.colorScheme.surfaceVariant, onClick = { open = true }) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.modLoader.displayName, fontWeight = FontWeight.Medium)
                Icon(Icons.Rounded.ArrowDropDown, null)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ModrinthLoaders.forEach { l ->
                DropdownMenuItem(text = { Text(l.displayName) }, onClick = { state.modLoader = l; open = false })
            }
        }
    }
}

@Composable
private fun ModCard(mod: ModDownloader.Mod, state: AppState) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            NetworkImage(mod.iconUrl, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(mod.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    mod.description ?: "暂无简介",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(formatDownloads(mod.downloads), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = { state.downloadMod(mod) }, enabled = state.modProgress == null) {
                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("下载")
            }
        }
    }
}

private fun formatDownloads(n: Long): String = when {
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format("%.1fk", n / 1_000.0)
    else -> "$n"
} + " 次下载"
