package ru.starlitmoon.launcher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.starlitmoon.launcher.LauncherConfig
import ru.starlitmoon.launcher.LauncherVersion
import ru.starlitmoon.launcher.api.ServerStatus
import ru.starlitmoon.launcher.ui.components.SectionTitle
import ru.starlitmoon.launcher.ui.components.StarlitCard
import ru.starlitmoon.launcher.ui.components.StarlitPrimaryButton
import ru.starlitmoon.launcher.ui.components.StarlitSecondaryButton
import ru.starlitmoon.launcher.ui.components.StarlitTextField
import ru.starlitmoon.launcher.ui.components.StatRow
import ru.starlitmoon.launcher.ui.components.StatusDot
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.ui.theme.StarlitTitleGradient
import ru.starlitmoon.launcher.viewmodel.LauncherTab
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel

@Composable
fun PlayScreen(vm: LauncherViewModel) {
    val config = remember { LauncherConfig.load() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 24.dp, end = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Java Edition · Vanilla", color = StarlitColors.Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Добро пожаловать на", color = StarlitColors.TextMuted, fontSize = 15.sp)
        Text(
            "StarlitMoon",
            fontSize = 42.sp,
            fontWeight = FontWeight.ExtraBold,
            style = TextStyle(brush = StarlitTitleGradient),
        )
        Text(
            "Исследуй мир под звёздным небом: дружное комьюнити, ивенты и атмосфера, в которую хочется возвращаться.",
            color = StarlitColors.TextMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            modifier = Modifier.width(520.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StarlitPrimaryButton(
                text = if (vm.isLoggedIn) "Играть" else "Войти и играть",
                onClick = {
                    if (vm.isLoggedIn) vm.play() else vm.currentTab = LauncherTab.Cabinet
                },
                modifier = Modifier.width(180.dp),
                loading = vm.isLoading,
            )
            Text(config.serverHost, color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }

        if (vm.launchProgress != null) {
            Text(vm.launchProgress!!, color = StarlitColors.Accent, fontSize = 13.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ServerStatusCard(vm.serverStatus, vm.serverVersion, config.serverHost, vm.isRefreshingStatus, Modifier.weight(1f))
            OnlinePlayersCard(vm.onlinePlayers, Modifier.weight(1f))
        }

        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    if (vm.isLoggedIn) "Аккаунт: ${vm.userName}" else "Войдите ником и паролем с сервера",
                    color = StarlitColors.Text,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "После запуска используйте /login с паролем сервера.",
                    color = StarlitColors.TextMuted,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun ServerStatusCard(
    status: ServerStatus,
    version: String,
    host: String,
    refreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    StarlitCard(modifier = modifier) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Сервер", color = StarlitColors.Text, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                StatusDot(status.online)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (status.online) "Онлайн" else if (refreshing) "…" else "Оффлайн",
                    color = if (status.online) StarlitColors.Online else StarlitColors.TextMuted,
                    fontSize = 13.sp,
                )
            }
            StatRow("Адрес", host)
            StatRow("Игроков", "${status.playersOnline} / ${status.playersMax}")
            StatRow("Версия", version.ifBlank { status.version.ifBlank { "—" } })
        }
    }
}

@Composable
private fun OnlinePlayersCard(players: List<String>, modifier: Modifier = Modifier) {
    StarlitCard(modifier = modifier) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("Сейчас онлайн", color = StarlitColors.Text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (players.isEmpty()) {
                Text("Никого нет в списке", color = StarlitColors.TextMuted, fontSize = 13.sp)
            } else {
                LazyColumn(modifier = Modifier.height(110.dp)) {
                    items(players) { name ->
                        Text("• $name", color = StarlitColors.Text, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(vm: LauncherViewModel) {
    val nickname = remember { mutableStateOf("") }
    val password = remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SectionTitle("Вход", "Ник и пароль с сервера")
        StarlitCard(modifier = Modifier.width(400.dp)) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StarlitTextField(nickname.value, { nickname.value = it }, "Ник в Minecraft")
                StarlitTextField(password.value, { password.value = it }, "Пароль", isPassword = true)
                StarlitPrimaryButton(
                    text = "Войти",
                    onClick = { vm.login(nickname.value, password.value) },
                    modifier = Modifier.fillMaxWidth(),
                    loading = vm.isLoading,
                )
            }
        }
    }
}

@Composable
fun CabinetScreen(vm: LauncherViewModel) {
    if (!vm.isLoggedIn) {
        LoginScreen(vm)
        return
    }

    LaunchedEffect(Unit) { vm.refreshCabinet() }
    val player = vm.meData?.cabinet?.player

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
            .padding(end = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionTitle("Личный кабинет", "Профиль игрока")
        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, null, tint = StarlitColors.Accent, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(vm.userName, color = StarlitColors.Text, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text(
                            if (vm.isAdmin) "Администратор" else "Игрок",
                            color = if (vm.isAdmin) StarlitColors.Accent else StarlitColors.TextMuted,
                        )
                    }
                }
                StatRow("UUID", vm.userUuid ?: "—")
                StatRow("Статус", player?.profileStatus?.ifBlank { "—" } ?: "—")
                StatRow("Предупреждения", (player?.warnCount ?: 0).toString())
                StatRow("Ранги", player?.ranks?.joinToString(", ")?.ifBlank { "—" } ?: "—")
                if (player?.banned == true) {
                    Text("Аккаунт заблокирован", color = StarlitColors.Offline, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                StarlitSecondaryButton("Выйти", onClick = vm::logout)
            }
        }

        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Уведомления", color = StarlitColors.Text, fontWeight = FontWeight.Bold)
                if (vm.notifications.isEmpty()) {
                    Text("Пока пусто", color = StarlitColors.TextMuted, fontSize = 13.sp)
                } else {
                    vm.notifications.take(8).forEach { n ->
                        Text(
                            n.title?.ifBlank { n.message }.orEmpty().ifBlank { n.message.orEmpty() },
                            color = StarlitColors.Text,
                            fontSize = 13.sp,
                        )
                        if (!n.message.isNullOrBlank() && n.title != null) {
                            Text(n.message!!, color = StarlitColors.TextMuted, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: LauncherViewModel) {
    val config = remember { LauncherConfig.load() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
            .padding(end = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionTitle("Настройки", "Лаунчер и обновления")
        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatRow("Версия", LauncherVersion.CURRENT)
                StatRow("Сервер", config.serverHost)
                StatRow("Клиент", config.minecraftVersionId)
                StatRow("Память", "${config.minMemoryMb}–${config.maxMemoryMb} MB")
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StarlitPrimaryButton(
                        text = if (vm.isCheckingUpdates) "Проверка…" else "Проверить обновления",
                        onClick = vm::checkForUpdates,
                        enabled = !vm.isCheckingUpdates,
                        loading = vm.isCheckingUpdates,
                    )
                    if (vm.updateInfo != null) {
                        StarlitSecondaryButton("Скачать ${vm.updateInfo!!.latestVersion}", onClick = vm::downloadUpdate)
                    }
                }
            }
        }
    }
}
