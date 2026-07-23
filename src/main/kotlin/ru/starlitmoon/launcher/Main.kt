package ru.starlitmoon.launcher

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.swing.Swing
import org.jetbrains.skia.Image
import ru.starlitmoon.launcher.api.StarlitApiClient
import ru.starlitmoon.launcher.ui.LauncherApp
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel

private fun loadWindowIcon(): Painter? {
    val bytes = object {}.javaClass.getResourceAsStream("/icon.png")?.readBytes() ?: return null
    return runCatching {
        BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
    }.getOrNull()
}

fun main() {
    WindowsShell.applyAppUserModelId()

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
    val api = StarlitApiClient()
    val vm = LauncherViewModel(scope, api)

    application {
        val windowIcon = remember { loadWindowIcon() }
        val windowState = rememberWindowState(width = 1360.dp, height = 860.dp)
        fun shutdown() {
            vm.dispose()
            api.close()
            exitApplication()
        }
        Window(
            onCloseRequest = { shutdown() },
            title = "StarlitMoon",
            state = windowState,
            undecorated = true,
            icon = windowIcon,
        ) {
            LaunchedEffect(Unit) {
                val images = WindowsShell.loadWindowIconImages()
                if (images.isNotEmpty()) {
                    window.iconImages = images
                }
            }
            LauncherApp(
                vm = vm,
                api = api,
                windowState = windowState,
                onClose = { shutdown() },
                onRequestExit = { shutdown() },
            )
        }
    }
}
