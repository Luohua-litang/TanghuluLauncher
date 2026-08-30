package com.tanghulu.launcher

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tanghulu.launcher.ui.App
import com.tanghulu.launcher.util.Log
import com.tanghulu.launcher.util.OperatingSystem

fun main() {
    // 初始化日志：写入应用数据目录，保留 7 天
    Log.init(OperatingSystem.appDataDir().resolve("logs"))

    // 窗口图标：加载资源里的 Tanghulu.png，缺失时回退到系统默认图标
    val icon = runCatching {
        object {}.javaClass.getResourceAsStream("/images/Tanghulu.png")?.use {
            BitmapPainter(loadImageBitmap(it))
        }
    }.getOrNull()

    application {
        Window(
            onCloseRequest = { exitApplication() },
            title = "Tanghulu Launcher",
            icon = icon,
            state = rememberWindowState(width = 1280.dp, height = 820.dp),
        ) {
            App()
        }
    }
}
