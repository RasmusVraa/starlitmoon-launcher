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
fun LauncherApp(
    vm: LauncherViewModel,
    @Suppress("UNUSED_PARAMETER") api: StarlitApiClient,
    onRequestExit: () -> Unit = {},
) {
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
            LaunchedEffect(vm.requestExit) {
                if (vm.requestExit) onRequestExit()
            }

            Row(modifier = Modifier.fillMaxSize()) {
                SideNav(vm)
                Column(modifier = Modifier.fillMaxSize()) {
                    TopBar(vm)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp, vertical = 8.dp),
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
}

@Composable
private fun SideNav(vm: LauncherViewModel) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(76.dp)
            .background(Color(0xE6050812))
            .border(
                width = 1.dp,
                color = StarlitColors.CardBorder,
                shape = RoundedCornerShape(topEnd = 0.dp, bottomEnd = 0.dp),
            )
            .padding(vertical = 20.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(listOf(StarlitColors.Accent, StarlitColors.Purple)),
                )
                .clickable { vm.currentTab = LauncherTab.Play },
            contentAlignment = Alignment.Center,
        ) {
            Text("★", color = StarlitColors.BgDeep, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(32.dp))
        NavIcon(Icons.Default.Home, vm.currentTab == LauncherTab.Play) { vm.currentTab = LauncherTab.Play }
        Spacer(Modifier.height(8.dp))
        NavIcon(Icons.Default.Person, vm.currentTab == LauncherTab.Cabinet) { vm.currentTab = LauncherTab.Cabinet }
        if (vm.isAdmin) {
            Spacer(Modifier.height(8.dp))
            NavIcon(Icons.Default.AdminPanelSettings, vm.currentTab == LauncherTab.Admin) {
                vm.currentTab = LauncherTab.Admin
            }
        }
        Spacer(Modifier.height(8.dp))
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
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) {
                    Brush.linearGradient(
                        listOf(
                            StarlitColors.Accent.copy(alpha = 0.22f),
                            StarlitColors.Purple.copy(alpha = 0.18f),
                        ),
                    )
                } else {
                    Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                },
            )
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) StarlitColors.Accent.copy(alpha = 0.4f) else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
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
            .padding(horizontal = 28.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
            ProfileChip(name = vm.userName)
        } else {
            GuestChip(onClick = { vm.currentTab = LauncherTab.Cabinet })
        }
    }
}

@Composable
private fun ProfileChip(name: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xCC12182A))
            .border(1.dp, StarlitColors.Accent.copy(alpha = 0.3f), RoundedCornerShape(50))
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Text(name, color = StarlitColors.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("StarlitMoon", color = StarlitColors.TextMuted, fontSize = 10.sp)
        }
        AvatarInitial(name)
    }
}

@Composable
private fun GuestChip(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x9912182A))
            .border(1.dp, StarlitColors.CardBorder, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Гость", color = StarlitColors.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(StarlitColors.PurpleSoft)
                .border(1.dp, StarlitColors.CardBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("?", color = StarlitColors.TextMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AvatarInitial(name: String) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(StarlitColors.Purple.copy(alpha = 0.55f), StarlitColors.Accent.copy(alpha = 0.45f)),
                ),
            )
            .border(2.dp, StarlitColors.Accent.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).uppercase(),
            color = StarlitColors.Accent,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun StatusPill(text: String, accent: Color, dot: Boolean = false, online: Boolean = true) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xCC12182A))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            StatusDot(online)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = StarlitColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
