package ru.starlitmoon.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState
import ru.starlitmoon.launcher.api.StarlitApiClient
import ru.starlitmoon.launcher.ui.components.IntegratedChromeBar
import ru.starlitmoon.launcher.ui.components.MessageBanner
import ru.starlitmoon.launcher.ui.components.SidebarNav
import ru.starlitmoon.launcher.ui.components.StarlitBackground
import ru.starlitmoon.launcher.ui.components.TopStatusBar
import ru.starlitmoon.launcher.ui.components.UpdateOverlay
import ru.starlitmoon.launcher.ui.components.WindowControlButtons
import ru.starlitmoon.launcher.ui.screens.AdminScreen
import ru.starlitmoon.launcher.ui.screens.BuildsScreen
import ru.starlitmoon.launcher.ui.screens.CabinetScreen
import ru.starlitmoon.launcher.ui.screens.HomeScreen
import ru.starlitmoon.launcher.ui.screens.LoginScreen
import ru.starlitmoon.launcher.ui.screens.SettingsScreen
import ru.starlitmoon.launcher.ui.screens.SkinsScreen
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.ui.theme.StarlitTypography
import ru.starlitmoon.launcher.viewmodel.LauncherTab
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel

@Composable
fun FrameWindowScope.LauncherApp(
    vm: LauncherViewModel,
    @Suppress("UNUSED_PARAMETER") api: StarlitApiClient,
    windowState: WindowState,
    onClose: () -> Unit,
    onRequestExit: () -> Unit = {},
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = StarlitColors.Background,
            surface = StarlitColors.Surface,
            primary = StarlitColors.Gold,
            secondary = StarlitColors.Purple,
            onBackground = StarlitColors.Text,
            onSurface = StarlitColors.Text,
        ),
        typography = StarlitTypography,
    ) {
        StarlitBackground {
            LaunchedEffect(Unit) { vm.boot() }
            LaunchedEffect(vm.requestExit) {
                if (vm.requestExit) onRequestExit()
            }

            if (!vm.isLoggedIn) {
                Column(modifier = Modifier.fillMaxSize()) {
                    IntegratedChromeBar(windowState = windowState, onClose = onClose)
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        LoginScreen(vm)
                        if (vm.updateInfo != null && (!vm.updateDismissed || vm.isApplyingUpdate)) {
                            UpdateOverlay(
                                update = vm.updateInfo!!,
                                onDownload = vm::downloadUpdate,
                                onDismiss = vm::dismissUpdate,
                                applying = vm.isApplyingUpdate,
                                progressLabel = vm.updateProgress,
                                progressFraction = vm.updateProgressFraction,
                            )
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    SidebarNav(vm)
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            WindowDraggableArea {
                                TopStatusBar(
                                    vm = vm,
                                    windowControls = {
                                        WindowControlButtons(
                                            windowState = windowState,
                                            onClose = onClose,
                                        )
                                    },
                                )
                            }
                            Box(modifier = Modifier.fillMaxSize()) {
                                val contentPad = if (vm.currentTab == LauncherTab.Home) 0.dp else 28.dp
                                val contentVPad = if (vm.currentTab == LauncherTab.Home) 0.dp else 12.dp
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = contentPad, vertical = contentVPad),
                                ) {
                                    when (vm.currentTab) {
                                        LauncherTab.Home -> HomeScreen(vm)
                                        LauncherTab.Builds -> BuildsScreen(vm)
                                        LauncherTab.Cabinet -> CabinetScreen(vm)
                                        LauncherTab.Skins -> SkinsScreen(vm)
                                        LauncherTab.Admin -> AdminScreen(vm)
                                        LauncherTab.Settings -> SettingsScreen(vm)
                                    }
                                }
                                if (vm.errorMessage != null || vm.infoMessage != null) {
                                    val err = vm.errorMessage
                                    val info = vm.infoMessage
                                    LaunchedEffect(err, info) {
                                        kotlinx.coroutines.delay(5_000)
                                        if (vm.errorMessage == err && vm.infoMessage == info) {
                                            vm.clearMessages()
                                        }
                                    }
                                    Column(
                                        modifier = Modifier
                                            .align(androidx.compose.ui.Alignment.TopCenter)
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                    ) {
                                        err?.let { MessageBanner(it, true, vm::clearMessages) }
                                        info?.let { MessageBanner(it, false, vm::clearMessages) }
                                    }
                                }
                                if (vm.updateInfo != null && (!vm.updateDismissed || vm.isApplyingUpdate)) {
                                    UpdateOverlay(
                                        update = vm.updateInfo!!,
                                        onDownload = vm::downloadUpdate,
                                        onDismiss = vm::dismissUpdate,
                                        applying = vm.isApplyingUpdate,
                                        progressLabel = vm.updateProgress,
                                        progressFraction = vm.updateProgressFraction,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
