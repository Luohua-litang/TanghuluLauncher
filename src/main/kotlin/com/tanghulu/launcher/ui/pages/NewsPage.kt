package com.tanghulu.launcher.ui.pages

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanghulu.launcher.ui.AppState
import com.tanghulu.launcher.ui.components.CardShape
import com.tanghulu.launcher.ui.components.StaggerInItem
import com.tanghulu.launcher.ui.loadNews
import com.tanghulu.launcher.util.MinecraftNewsService
import java.awt.Desktop
import java.net.URI

@Composable
fun NewsPage(state: AppState) {
    val playedKeys = remember { mutableStateOf(setOf<String>()) }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Minecraft 新闻", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { state.loadNews(force = true) }) {
                Icon(Icons.Rounded.Refresh, "刷新", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        when {
            state.newsLoading && state.news.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("加载中…") }
            state.newsError -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("新闻加载失败，请稍后刷新", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(state.news, key = { _, item -> item.title ?: item.date ?: "" }) { index, item ->
                    val key = item.title ?: item.date ?: ""
                    StaggerInItem(index, key in playedKeys.value, { playedKeys.value = playedKeys.value + key }) {
                        NewsCard(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsCard(item: MinecraftNewsService.NewsItem, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        onClick = { item.link?.let { openUrl(it) } },
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.tag?.let { tag ->
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(tag, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.size(10.dp))
                }
                Text(item.date ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Text(item.title ?: "无标题", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            item.text?.let { text ->
                Spacer(Modifier.height(6.dp))
                Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun openUrl(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}
