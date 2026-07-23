package ru.starlitmoon.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.starlitmoon.launcher.api.StarlitApiClient
import ru.starlitmoon.launcher.ui.components.BrandMark
import ru.starlitmoon.launcher.ui.components.BrandWordmark
import ru.starlitmoon.launcher.ui.components.MessageBanner
import ru.starlitmoon.launcher.ui.components.StarlitBackground
import ru.starlitmoon.launcher.ui.components.StatusDot
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
fun LauncherApp(
    vm: LauncherViewModel,
    @Suppress("UNUSED_PARAMETER") api: StarlitApiClient,
    onRequestExit: () -> Unit = {},
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = StarlitColors.Background,
            surface = StarlitColors.Surface,
            primary = StarlitColors.Gold,
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

            Column(modifier = Modifier.fillMaxSize()) {
                TopNav(vm)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 40.dp, vertical = 20.dp),
                ) {
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
private fun TopNav(vm: LauncherViewModel) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark(size = 34.dp)
            Spacer(Modifier.width(12.dp))
            BrandWordmark()

            Spacer(Modifier.width(48.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NavTab("Играть", vm.currentTab == LauncherTab.Play) { vm.currentTab = LauncherTab.Play }
                NavTab("Кабинет", vm.currentTab == LauncherTab.Cabinet) { vm.currentTab = LauncherTab.Cabinet }
                if (vm.isAdmin) {
                    NavTab("Админ", vm.currentTab == LauncherTab.Admin) { vm.currentTab = LauncherTab.Admin }
                }
                NavTab("Настройки", vm.currentTab == LauncherTab.Settings) { vm.currentTab = LauncherTab.Settings }
            }

            Spacer(Modifier.weight(1f))

            ServerStatusPill(vm)
            Spacer(Modifier.width(16.dp))

            if (vm.isLoggedIn) {
                ProfileChip(name = vm.userName, onLogout = vm::logout)
            } else {
                GuestChip(onClick = { vm.currentTab = LauncherTab.Cabinet })
            }
        }
        HorizontalDivider(color = StarlitColors.Border, thickness = 1.dp)
    }
}

@Composable
private fun NavTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) StarlitColors.Text else StarlitColors.TextMuted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun ServerStatusPill(vm: LauncherViewModel) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(StarlitColors.Surface)
            .border(1.dp, StarlitColors.Border, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(vm.serverStatus.online)
        Spacer(Modifier.width(8.dp))
        Text(
            if (vm.serverStatus.online) "Онлайн · ${vm.serverStatus.playersOnline}" else "Оффлайн",
            color = StarlitColors.TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ProfileChip(name: String, onLogout: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(StarlitColors.Surface)
            .border(1.dp, StarlitColors.Border, RoundedCornerShape(50))
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AvatarInitial(name)
        Text(name, color = StarlitColors.Text, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onLogout)
                .padding(6.dp),
        ) {
            Icon(
                Icons.Default.Logout,
                contentDescription = "Выйти",
                tint = StarlitColors.TextMuted,
                modifier = Modifier.width(16.dp),
            )
        }
    }
}

@Composable
private fun GuestChip(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(StarlitColors.Surface)
            .border(1.dp, StarlitColors.Border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Войти", color = StarlitColors.Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AvatarInitial(name: String) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(StarlitColors.GoldMuted)
            .border(1.dp, StarlitColors.Gold.copy(alpha = 0.4f), CircleShape)
            .padding(1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.width(28.dp).height(28.dp), contentAlignment = Alignment.Center) {
            Text(
                name.take(1).uppercase().ifBlank { "?" },
                color = StarlitColors.Gold,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
    }
}
