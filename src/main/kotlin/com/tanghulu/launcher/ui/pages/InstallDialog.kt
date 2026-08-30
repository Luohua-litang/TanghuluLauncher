package com.tanghulu.launcher.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tanghulu.launcher.core.ModLoader
import com.tanghulu.launcher.ui.AppState
import com.tanghulu.launcher.ui.components.CardShape
import com.tanghulu.launcher.ui.components.PillShape
import com.tanghulu.launcher.ui.fetchLoaderVersions
import com.tanghulu.launcher.ui.installGame

@Composable
fun InstallDialog(versionId: String, state: AppState, onDismiss: () -> Unit) {
    var instanceName by remember(versionId) { mutableStateOf(versionId) }
    val loaders = remember { ModLoader.values().filter { it.supported } }
    val versions = remember { mutableStateMapOf<ModLoader, List<String>>() }
    val picked = remember { mutableStateMapOf<ModLoader, String>() }

    LaunchedEffect(versionId) {
        loaders.forEach { l -> state.fetchLoaderVersions(l, versionId) { versions[l] = it } }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = CardShape, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("安装游戏", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("原版 $versionId + 可选加载器", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = instanceName, onValueChange = { instanceName = it },
                    label = { Text("实例名称") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text("加载器（可选）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    loaders.forEach { l -> LoaderRow(l, versions[l], picked) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            state.installGame(versionId, instanceName.trim(), picked.toMap())
                            onDismiss()
                        },
                        enabled = instanceName.isNotBlank(),
                    ) { Text("安装") }
                }
            }
        }
    }
}

@Composable
private fun LoaderRow(loader: ModLoader, loaderVersions: List<String>?, picked: MutableMap<ModLoader, String>) {
    val checked = loader in picked
    val toggle: (Boolean) -> Unit = { c ->
        if (c) loaderVersions?.firstOrNull()?.let { picked[loader] = it }
        else picked.remove(loader)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = { toggle(!checked) },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = toggle)
            Text(loader.displayName, Modifier.weight(1f), fontWeight = FontWeight.Medium)
            if (checked) {
                if (loaderVersions.isNullOrEmpty()) {
                    Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                } else {
                    LoaderVersionMenu(loaderVersions, picked[loader] ?: "") { picked[loader] = it }
                }
            }
        }
    }
}

@Composable
private fun LoaderVersionMenu(items: List<String>, current: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(shape = PillShape, color = MaterialTheme.colorScheme.background, onClick = { open = true }) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(current, style = MaterialTheme.typography.labelMedium)
                Icon(Icons.Rounded.ArrowDropDown, null, Modifier.width(18.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { v ->
                DropdownMenuItem(text = { Text(v) }, onClick = { onPick(v); open = false })
            }
        }
    }
}
