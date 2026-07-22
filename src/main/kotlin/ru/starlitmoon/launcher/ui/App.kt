package ru.starlitmoon.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.starlitmoon.launcher.api.StarlitApiClient
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
                SideNav(vm)
                Column(modifier = Modifier.fillMaxSize()) {
                    TopBar(vm)
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 12.dp)) {
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
}

@Composable
private fun SideNav(vm: LauncherViewModel) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(72.dp)
            .background(Color(0xE6080C18))
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(listOf(StarlitColors.Accent, StarlitColors.Purple)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("★", color = StarlitColors.BgDeep, fontSize = 20.sp)
        }
        Spacer(Modifier.height(28.dp))
        NavIcon(Icons.Default.Home, vm.currentTab == LauncherTab.Play) { vm.currentTab = LauncherTab.Play }
        Spacer(Modifier.height(10.dp))
        NavIcon(Icons.Default.Person, vm.currentTab == LauncherTab.Cabinet) { vm.currentTab = LauncherTab.Cabinet }
        if (vm.isAdmin) {
            Spacer(Modifier.height(10.dp))
            NavIcon(Icons.Default.AdminPanelSettings, vm.currentTab == LauncherTab.Admin) {
                vm.currentTab = LauncherTab.Admin
            }
        }
        Spacer(Modifier.height(10.dp))
        NavIcon(Icons.Default.Settings, vm.currentTab == LauncherTab.Settings) { vm.currentTab = LauncherTab.Settings }
        Spacer(Modifier.weight(1f))
        if (vm.isLoggedIn) {
            NavIcon(Icons.Default.Logout, false) { vm.logout() }
        }
    }
}

@Composable
private fun NavIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) StarlitColors.Accent.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) StarlitColors.Accent else StarlitColors.TextMuted,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun TopBar(vm: LauncherViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusPill(
            text = "Сейчас играют ${vm.serverStatus.playersOnline}",
            accent = StarlitColors.Purple,
        )
        StatusPill(
            text = if (vm.serverStatus.online) "Сервер работает" else "Сервер оффлайн",
            accent = if (vm.serverStatus.online) StarlitColors.Online else StarlitColors.Offline,
            dot = true,
            online = vm.serverStatus.online,
        )
        Spacer(Modifier.weight(1f))
        if (vm.isLoggedIn) {
            Column(horizontalAlignment = Alignment.End) {
                Text(vm.userName, color = StarlitColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("StarlitMoon", color = StarlitColors.TextMuted, fontSize = 11.sp)
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(2.dp, StarlitColors.Accent.copy(alpha = 0.5f), CircleShape)
                    .background(StarlitColors.PurpleSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(vm.userName.take(1).uppercase(), color = StarlitColors.Accent, fontWeight = FontWeight.Bold)
            }
        } else {
            Text(
                "Гость",
                color = StarlitColors.TextMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { vm.currentTab = LauncherTab.Cabinet }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, accent: Color, dot: Boolean = false, online: Boolean = true) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xCC12182A))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            StatusDot(online)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = StarlitColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
