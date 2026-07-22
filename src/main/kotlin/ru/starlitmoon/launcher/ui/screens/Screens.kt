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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.starlitmoon.launcher.LauncherConfig
import ru.starlitmoon.launcher.LauncherVersion
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel
import ru.starlitmoon.launcher.api.ServerStatus
import ru.starlitmoon.launcher.ui.components.SectionTitle
import ru.starlitmoon.launcher.ui.components.StarlitCard
import ru.starlitmoon.launcher.ui.components.StarlitPrimaryButton
import ru.starlitmoon.launcher.ui.components.StarlitSecondaryButton
import ru.starlitmoon.launcher.ui.components.StatRow
import ru.starlitmoon.launcher.ui.theme.StarlitColors

@Composable
fun PlayScreen(vm: LauncherViewModel) {
    val config = LauncherConfig.load()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(
            title = "StarlitMoon",
            subtitle = "Исследуй мир под звёздным небом",
        )

        ServerStatusCard(
            status = vm.serverStatus,
            version = vm.serverVersion,
            host = config.serverHost,
        )

        OnlinePlayersCard(players = vm.onlinePlayers)

        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Готов к запуску",
                    color = StarlitColors.Text,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (vm.isLoggedIn) "Аккаунт: ${vm.userName}" else "Войдите в аккаунт перед запуском",
                    color = StarlitColors.TextMuted,
                )
                if (vm.launchProgress != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(vm.launchProgress!!, color = StarlitColors.Accent)
                }
                Spacer(Modifier.height(16.dp))
                StarlitPrimaryButton(
                    text = "Играть",
                    onClick = vm::play,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = vm.isLoggedIn,
                    loading = vm.isLoading,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "После подключения к play.starlit-moon.ru используйте /login с паролем сервера (mcAuth).",
                    color = StarlitColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ServerStatusCard(status: ServerStatus, version: String, host: String) {
    StarlitCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Сервер", color = StarlitColors.Text, fontWeight = FontWeight.Bold)
            StatRow("Адрес", host)
            StatRow("Статус", if (status.online) "Онлайн" else "Оффлайн", status.online)
            StatRow("Игроков", "${status.playersOnline} / ${status.playersMax}", status.online)
            StatRow("Версия", version.ifBlank { status.version.ifBlank { "—" } })
        }
    }
}

@Composable
private fun OnlinePlayersCard(players: List<String>) {
    StarlitCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Сейчас на сервере", color = StarlitColors.Text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (players.isEmpty()) {
                Text("Никого нет в списке или сервер недоступен", color = StarlitColors.TextMuted)
            } else {
                LazyColumn(modifier = Modifier.height(120.dp)) {
                    items(players) { name ->
                        Text("• $name", color = StarlitColors.Text, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(vm: LauncherViewModel) {
    var nickname = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var password = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SectionTitle("Вход в кабинет", "Ник и пароль с сервера Minecraft")
        StarlitCard(modifier = Modifier.width(420.dp)) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ru.starlitmoon.launcher.ui.components.StarlitTextField(
                    value = nickname.value,
                    onValueChange = { nickname.value = it },
                    label = "Ник в Minecraft",
                )
                ru.starlitmoon.launcher.ui.components.StarlitTextField(
                    value = password.value,
                    onValueChange = { password.value = it },
                    label = "Пароль",
                    isPassword = true,
                )
                StarlitPrimaryButton(
                    text = "Войти",
                    onClick = { vm.login(nickname.value, password.value) },
                    modifier = Modifier.fillMaxWidth(),
                    loading = vm.isLoading,
                )
                StarlitSecondaryButton(
                    text = "Войти через Discord",
                    onClick = vm::openDiscordLogin,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun CabinetScreen(vm: LauncherViewModel, apiBaseUrl: String) {
    if (!vm.isLoggedIn) {
        LoginScreen(vm)
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        SectionTitle("Личный кабинет", "Профиль и настройки аккаунта")
        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = StarlitColors.Accent,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(vm.userName, color = StarlitColors.Text, fontWeight = FontWeight.ExtraBold)
                        Text(
                            if (vm.isAdmin) "Администратор" else "Игрок",
                            color = if (vm.isAdmin) StarlitColors.Accent else StarlitColors.TextMuted,
                        )
                    }
                }
                StatRow("UUID", vm.userUuid ?: "—")
                StatRow("Профиль на сайте", if (vm.meData?.cabinet?.found == true) "Найден" else "Не найден")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StarlitSecondaryButton("Открыть кабинет на сайте", onClick = {
                        java.awt.Desktop.getDesktop().browse(java.net.URI("$apiBaseUrl/cabinet"))
                    })
                    StarlitSecondaryButton("Выйти", onClick = vm::logout)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: LauncherViewModel) {
    val config = LauncherConfig.load()
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        SectionTitle("Настройки", "Конфигурация лаунчера")
        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatRow("Версия лаунчера", LauncherVersion.CURRENT)
                StatRow("GitHub", "${config.githubOwner}/${config.githubRepo}")
                StatRow("API", config.apiBaseUrl)
                StatRow("Сервер", config.serverHost)
                StatRow("Папка игры", config.gameDir.toString())
                StatRow("Minecraft version id", config.minecraftVersionId.ifBlank { "(из API: ${config.defaultMcVersion})" })
                StatRow("Java", config.javaPath.ifBlank { "авто" })
                StatRow("Память", "${config.minMemoryMb}–${config.maxMemoryMb} MB")
                Spacer(Modifier.height(8.dp))
                StarlitPrimaryButton(
                    text = if (vm.isCheckingUpdates) "Проверка…" else "Проверить обновления",
                    onClick = vm::checkForUpdates,
                    enabled = !vm.isCheckingUpdates,
                    loading = vm.isCheckingUpdates,
                )
                if (vm.updateInfo == null && !vm.isCheckingUpdates) {
                    Text("Обновлений нет — установлена последняя версия.", color = StarlitColors.TextMuted)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Измените ~/.starlitmoon-launcher/config.json и перезапустите лаунчер.",
                    color = StarlitColors.TextMuted,
                )
            }
        }
    }
}
