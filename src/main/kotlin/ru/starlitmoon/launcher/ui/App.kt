package ru.starlitmoon.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.starlitmoon.launcher.api.StarlitApiClient
import ru.starlitmoon.launcher.ui.components.BrandMark
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
fun LauncherApp(vm: LauncherViewModel, @Suppress("UNUSED_PARAMETER") api: StarlitApiClient) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = StarlitColors.BgDeep,
            surface = StarlitColors.BgCard,
            primary = StarlitColors.Accent,
            secondary = StarlitColors.Purple,
            onBackground = StarlitColors.Text,
            onSurface = StarlitColors.Text,
        ),
        typography = StarlitTypography,
    ) {
        StarlitBackground {
            LaunchedEffect(Unit) { vm.boot() }

            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(92.dp)
                        .background(Color(0xCC0C1020))
                        .padding(top = 16.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BrandMark()
                    Spacer(Modifier.height(18.dp))
                    NavigationRail(containerColor = Color.Transparent, modifier = Modifier.weight(1f)) {
                        NavigationRailItem(
                            selected = vm.currentTab == LauncherTab.Play,
                            onClick = { vm.currentTab = LauncherTab.Play },
                            icon = { Icon(Icons.Default.Home, null) },
                            label = { Text("Игра", fontSize = 11.sp) },
                            colors = navColors(),
                        )
                        NavigationRailItem(
                            selected = vm.currentTab == LauncherTab.Cabinet,
                            onClick = { vm.currentTab = LauncherTab.Cabinet },
                            icon = { Icon(Icons.Default.Person, null) },
                            label = { Text("Кабинет", fontSize = 11.sp) },
                            colors = navColors(),
                        )
                        if (vm.isAdmin) {
                            NavigationRailItem(
                                selected = vm.currentTab == LauncherTab.Admin,
                                onClick = { vm.currentTab = LauncherTab.Admin },
                                icon = { Icon(Icons.Default.AdminPanelSettings, null) },
                                label = { Text("Админ", fontSize = 11.sp) },
                                colors = navColors(),
                            )
                        }
                        NavigationRailItem(
                            selected = vm.currentTab == LauncherTab.Settings,
                            onClick = { vm.currentTab = LauncherTab.Settings },
                            icon = { Icon(Icons.Default.Settings, null) },
                            label = { Text("Опции", fontSize = 11.sp) },
                            colors = navColors(),
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
                    if (vm.updateInfo != null && !vm.updateDismissed) {
                        UpdateBanner(vm.updateInfo!!, vm::downloadUpdate, vm::dismissUpdate)
                    }
                    vm.errorMessage?.let { MessageBanner(it, true, vm::clearMessages) }
                    vm.infoMessage?.let { MessageBanner(it, false, vm::clearMessages) }

                    when (vm.currentTab) {
                        LauncherTab.Play -> PlayScreen(vm)
                        LauncherTab.Cabinet -> CabinetScreen(vm)
                        LauncherTab.Admin -> AdminScreen(vm)
                        LauncherTab.Settings -> SettingsScreen(vm)
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
