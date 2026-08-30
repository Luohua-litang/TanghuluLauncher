package com.tanghulu.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanghulu.launcher.ui.components.PillShape
import com.tanghulu.launcher.ui.pages.AccountPage
import com.tanghulu.launcher.ui.pages.DownloadProgressDialog
import com.tanghulu.launcher.ui.pages.HomePage
import com.tanghulu.launcher.ui.pages.ModsPage
import com.tanghulu.launcher.ui.pages.NewsPage
import com.tanghulu.launcher.ui.pages.SettingsPage
import com.tanghulu.launcher.ui.pages.VersionsPage
import com.tanghulu.launcher.ui.theme.TanghuluTheme

@Composable
fun App() {
    val state = remember { AppState() }

    TanghuluTheme(dark = state.darkMode, accent = state.accent()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                TopBar(state)
                AnimatedContent(
                    targetState = state.page,
                    transitionSpec = {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (fadeIn(tween(160)) + slideInHorizontally(tween(180)) { it / 4 * dir })
                            .togetherWith(fadeOut(tween(140)))
                    },
                    label = "page",
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { page ->
                    when (page) {
                        AppPage.Home -> HomePage(state)
                        AppPage.Versions -> VersionsPage(state)
                        AppPage.Mods -> ModsPage(state)
                        AppPage.Account -> AccountPage(state)
                        AppPage.News -> NewsPage(state)
                        AppPage.Settings -> SettingsPage(state)
                    }
                }
            }
        }

        if (state.launchProgress != null) {
            DownloadProgressDialog(state)
        }
    }

    LaunchedEffect(Unit) {
        state.loadVersions()
        state.loadNews()
    }
    DisposableEffect(Unit) {
        onDispose { state.saveNow() }
    }
}

@Composable
private fun TopBar(state: AppState) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("T", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Text("Tanghulu", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(28.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AppPage.entries.forEach { p ->
                    NavChip(p.title, state.page == p) { state.page = p }
                }
            }
            IconButton(onClick = {
                state.darkMode = !state.darkMode
                state.scheduleSave()
            }) {
                Icon(
                    if (state.darkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = "切换主题",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NavChip(title: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(PillShape)
            .clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            title,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
