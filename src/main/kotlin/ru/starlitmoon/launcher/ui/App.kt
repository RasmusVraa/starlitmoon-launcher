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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import ru.starlitmoon.launcher.ui.theme.StarlitAccentGradient
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
            surface = StarlitColors.Glass,
            primary = StarlitColors.Gold,
            secondary = StarlitColors.Violet,
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
                            .padding(horizontal = 32.dp, vertical = 12.dp),
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
            .width(68.dp)
            .background(StarlitColors.GlassStrong)
            .border(
                width = 1.dp,
                color = StarlitColors.Stroke,
                shape = RoundedCornerShape(topEnd = 0.dp, bottomEnd = 0.dp),
            )
            .padding(vertical = 24.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(StarlitAccentGradient)
                .clickable { vm.currentTab = LauncherTab.Play },
            contentAlignment = Alignment.Center,
        ) {
            Text("★", color = StarlitColors.Void, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(28.dp))
        NavIcon(Icons.Default.Home, vm.currentTab == LauncherTab.Play) { vm.currentTab = LauncherTab.Play }
        Spacer(Modifier.height(6.dp))
        NavIcon(Icons.Default.Person, vm.currentTab == LauncherTab.Cabinet) { vm.currentTab = LauncherTab.Cabinet }
        if (vm.isAdmin) {
            Spacer(Modifier.height(6.dp))
            NavIcon(Icons.Default.AdminPanelSettings, vm.currentTab == LauncherTab.Admin) {
                vm.currentTab = LauncherTab.Admin
            }
        }
        Spacer(Modifier.height(6.dp))
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
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (selected) {
                    Modifier
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    StarlitColors.GoldSoft,
                                    StarlitColors.VioletSoft,
                                ),
                            ),
                        )
                        .drawBehind {
                            drawRoundRect(
                                color = StarlitColors.Gold,
                                topLeft = Offset(0f, size.height * 0.2f),
                                size = Size(3.dp.toPx(), size.height * 0.6f),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                            )
                        }
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) StarlitColors.Gold else StarlitColors.TextMuted,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun TopBar(vm: LauncherViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusPill(
            text = "Сейчас играют ${vm.serverStatus.playersOnline}",
            accent = StarlitColors.Violet,
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
            .background(StarlitColors.GlassStrong)
            .border(1.dp, StarlitColors.Stroke, RoundedCornerShape(50))
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
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
            .background(StarlitColors.Glass)
            .border(1.dp, StarlitColors.Stroke, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Гость", color = StarlitColors.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(StarlitColors.VioletSoft)
                .border(1.dp, StarlitColors.Stroke, CircleShape),
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
                    listOf(StarlitColors.Violet.copy(alpha = 0.55f), StarlitColors.Gold.copy(alpha = 0.45f)),
                ),
            )
            .border(2.dp, StarlitColors.Gold.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).uppercase(),
            color = StarlitColors.Gold,
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
            .background(StarlitColors.GlassStrong)
            .border(1.dp, StarlitColors.Stroke, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            StatusDot(online)
            Spacer(Modifier.width(8.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = StarlitColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
