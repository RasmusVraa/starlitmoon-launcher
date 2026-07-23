package ru.starlitmoon.launcher

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
import java.awt.Taskbar
import javax.imageio.ImageIO

private fun loadWindowIcon(): Painter? {
    val bytes = object {}.javaClass.getResourceAsStream("/icon.png")?.readBytes() ?: return null
    return runCatching {
        BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
    }.getOrNull()
}

private fun applyTaskbarIcon() {
    runCatching {
        if (!Taskbar.isTaskbarSupported()) return
        val stream = object {}.javaClass.getResourceAsStream("/icon.png") ?: return
        val image = ImageIO.read(stream) ?: return
        Taskbar.getTaskbar().iconImage = image
    }
}

fun main() {
    // Вне application{}, иначе при рекомпозиции создаётся новый ViewModel и сбрасывается логин.
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
    val api = StarlitApiClient()
    val vm = LauncherViewModel(scope, api)
    applyTaskbarIcon()

    application {
        val windowIcon = remember { loadWindowIcon() }
        Window(
            onCloseRequest = {
                vm.dispose()
                api.close()
                exitApplication()
            },
            title = "StarlitMoon Launcher v${LauncherVersion.CURRENT}",
            state = rememberWindowState(
                width = 1100.dp,
                height = 720.dp,
            ),
            icon = windowIcon,
        ) {
            LauncherApp(
                vm = vm,
                api = api,
                onRequestExit = {
                    vm.dispose()
                    api.close()
                    exitApplication()
                },
            )
        }
    }
}
