package ru.starlitmoon.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.starlitmoon.launcher.api.StarlitApiClient
import ru.starlitmoon.launcher.ui.components.MessageBanner
import ru.starlitmoon.launcher.ui.components.StarlitBackground
import ru.starlitmoon.launcher.ui.components.UpdateBanner
import ru.starlitmoon.launcher.ui.screens.AdminScreen
import ru.starlitmoon.launcher.ui.screens.CabinetScreen
import ru.starlitmoon.launcher.ui.screens.PlayScreen
import ru.starlitmoon.launcher.ui.screens.SettingsScreen
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.ui.theme.StarlitTypography
import ru.starlitmoon.launcher.viewmodel.LauncherTab
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel

@Composable
fun LauncherApp(vm: LauncherViewModel, api: StarlitApiClient) {
    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme(), typography = StarlitTypography) {
        StarlitBackground {
            LaunchedEffect(Unit) { vm.boot() }

            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(containerColor = StarlitColors.BgCard.copy(alpha = 0.95f)) {
                    NavigationRailItem(
                        selected = vm.currentTab == LauncherTab.Play,
                        onClick = { vm.currentTab = LauncherTab.Play },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Игра") },
                        colors = navColors(),
                    )
                    NavigationRailItem(
                        selected = vm.currentTab == LauncherTab.Cabinet,
                        onClick = { vm.currentTab = LauncherTab.Cabinet },
                        icon = { Icon(Icons.Default.Person, contentDescription = null) },
                        label = { Text("Кабинет") },
                        colors = navColors(),
                    )
                    if (vm.isAdmin) {
                        NavigationRailItem(
                            selected = vm.currentTab == LauncherTab.Admin,
                            onClick = { vm.currentTab = LauncherTab.Admin },
                            icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                            label = { Text("Админка") },
                            colors = navColors(),
                        )
                    }
                    NavigationRailItem(
                        selected = vm.currentTab == LauncherTab.Settings,
                        onClick = { vm.currentTab = LauncherTab.Settings },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Настройки") },
                        colors = navColors(),
                    )
                }

                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    if (vm.updateInfo != null && !vm.updateDismissed) {
                        UpdateBanner(
                            update = vm.updateInfo!!,
                            onDownload = vm::downloadUpdate,
                            onDismiss = vm::dismissUpdate,
                        )
                    }
                    vm.errorMessage?.let {
                        MessageBanner(it, isError = true, onDismiss = vm::clearMessages)
                    }
                    vm.infoMessage?.let {
                        MessageBanner(it, isError = false, onDismiss = vm::clearMessages)
                    }

                    if (vm.isBootLoading) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = StarlitColors.BgDeep.copy(alpha = 0.4f),
                        ) {
                            Text(
                                "Загрузка…",
                                modifier = Modifier.padding(32.dp),
                                color = StarlitColors.Text,
                            )
                        }
                    } else {
                        when (vm.currentTab) {
                            LauncherTab.Play -> PlayScreen(vm)
                            LauncherTab.Cabinet -> CabinetScreen(vm, api.baseUrl)
                            LauncherTab.Admin -> AdminScreen(vm, api.adminUrl(), api.sessionCookie())
                            LauncherTab.Settings -> SettingsScreen(vm)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun navColors() = NavigationRailItemDefaults.colors(
    selectedIconColor = StarlitColors.Accent,
    selectedTextColor = StarlitColors.Accent,
    unselectedIconColor = StarlitColors.TextMuted,
    unselectedTextColor = StarlitColors.TextMuted,
    indicatorColor = StarlitColors.PurpleSoft,
)
