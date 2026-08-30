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
    // Initialize logging: write into the app data directory, retained for 7 days
    Log.init(OperatingSystem.appDataDir().resolve("logs"))

    // Window icon: load Tanghulu.png from resources, fall back to the system default icon if missing
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
