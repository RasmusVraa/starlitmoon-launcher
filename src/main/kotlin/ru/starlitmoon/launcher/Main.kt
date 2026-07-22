package ru.starlitmoon.launcher

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.swing.Swing
import ru.starlitmoon.launcher.api.StarlitApiClient
import ru.starlitmoon.launcher.ui.LauncherApp
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel

fun main() = application {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
    val api = StarlitApiClient()
    val vm = LauncherViewModel(scope, api)

    Window(
        onCloseRequest = {
            vm.dispose()
            api.close()
            exitApplication()
        },
        title = "StarlitMoon Launcher v${ru.starlitmoon.launcher.LauncherVersion.CURRENT}",
        state = rememberWindowState(
            width = 1100.dp,
            height = 720.dp,
        ),
    ) {
        LauncherApp(vm, api)
    }
}
