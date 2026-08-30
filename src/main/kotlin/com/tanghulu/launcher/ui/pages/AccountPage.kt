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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanghulu.launcher.ui.AppState
import com.tanghulu.launcher.ui.components.CardShape
import com.tanghulu.launcher.ui.components.NetworkImage
import com.tanghulu.launcher.ui.components.SkinAvatar
import com.tanghulu.launcher.ui.components.SkinFigure
import com.tanghulu.launcher.ui.components.decodeImage
import com.tanghulu.launcher.ui.removeSkin
import com.tanghulu.launcher.ui.skinFilePath
import com.tanghulu.launcher.ui.uploadSkin
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Files

@Composable
fun AccountPage(state: AppState) {
    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("账户", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = state.username, onValueChange = { state.username = it; state.scheduleSave() },
                label = { Text("玩家名") },
                supportingText = { Text("用于离线登录，将写入启动参数") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { pickAndUpload(state) }) {
                    Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("上传皮肤 PNG")
                }
                OutlinedButton(onClick = { state.removeSkin() }) {
                    Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("移除皮肤")
                }
            }
            if (state.skinStatus.isNotEmpty()) {
                Text(state.skinStatus, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Surface(shape = CardShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("皮肤预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                val bmp = rememberSkin(state)
                if (bmp != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        SkinFigure(bmp, height = 200.dp)
                        SkinAvatar(bmp, size = 72.dp)
                    }
                } else {
                    NetworkImage(
                        "https://mc-heads.net/body/${state.username.trim().ifBlank { "Steve" }}/right",
                        Modifier.height(200.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(state.username.trim().ifBlank { "Steve" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun rememberSkin(state: AppState): ImageBitmap? {
    return remember(state.username, state.skinVersion) {
        val f = state.skinFilePath()
        if (f != null && Files.isRegularFile(f)) runCatching { decodeImage(Files.readAllBytes(f)) }.getOrNull() else null
    }
}

private fun pickAndUpload(state: AppState) {
    val fd = FileDialog(null as Frame?, "选择皮肤 PNG", FileDialog.LOAD)
    fd.setFilenameFilter { _, name -> name.lowercase().endsWith(".png") }
    fd.isVisible = true
    val f = fd.files?.firstOrNull()
    if (f != null) state.uploadSkin(f.toPath())
}
