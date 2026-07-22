package ru.starlitmoon.launcher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        Text("Java Edition · Vanilla", color = StarlitColors.Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(
            "STARLITMOON",
            fontSize = 52.sp,
            fontWeight = FontWeight.ExtraBold,
            style = TextStyle(brush = StarlitTitleGradient),
            letterSpacing = 1.sp,
        )
        Text(
            "Ванильный сервер под звёздным небом. Версия ${vm.serverVersion}. Честный геймплей, экономика и живое комьюнити.",
            color = StarlitColors.TextMuted,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.width(560.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StarlitPrimaryButton(
                text = "ИГРАТЬ",
                onClick = {
                    if (vm.isLoggedIn) vm.play() else vm.currentTab = LauncherTab.Cabinet
                },
                modifier = Modifier.width(160.dp).height(52.dp),
                loading = vm.isLoading,
            )
            StarlitSecondaryButton(
                text = "НАСТРОИТЬ",
                onClick = { vm.currentTab = LauncherTab.Settings },
                modifier = Modifier.width(150.dp),
            )
        }
        if (vm.launchProgress != null) {
            Text(vm.launchProgress!!, color = StarlitColors.Accent, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StarlitCard(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Сервер", color = StarlitColors.Text, fontWeight = FontWeight.Bold)
                    StatRow("Адрес", config.serverHost)
                    StatRow("Онлайн", "${vm.serverStatus.playersOnline}/${vm.serverStatus.playersMax}", vm.serverStatus.online)
                    StatRow("Версия", vm.serverVersion)
                }
            }
            StarlitCard(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Игроки онлайн", color = StarlitColors.Text, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (vm.onlinePlayers.isEmpty()) {
                        Text("Список пуст", color = StarlitColors.TextMuted, fontSize = 13.sp)
                    } else {
                        LazyColumn(modifier = Modifier.height(100.dp)) {
                            items(vm.onlinePlayers) {
                                Text("• $it", color = StarlitColors.Text, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(config.serverHost, color = StarlitColors.TextMuted, fontSize = 12.sp)
            Text("v${LauncherVersion.CURRENT}", color = StarlitColors.TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
fun LoginScreen(vm: LauncherViewModel) {
    val nickname = remember { mutableStateOf("") }
    val password = remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SectionTitle("Вход", "Ник и пароль с сервера")
        StarlitCard(modifier = Modifier.width(400.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StarlitTextField(nickname.value, { nickname.value = it }, "Ник")
                StarlitTextField(password.value, { password.value = it }, "Пароль", isPassword = true)
                StarlitPrimaryButton(
                    "Войти",
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
    val sections = vm.meData?.cabinet?.sections.orEmpty().ifEmpty {
        listOf(
            ru.starlitmoon.launcher.api.PrivacySectionDto("stats", "Статистика", visible = true),
            ru.starlitmoon.launcher.api.PrivacySectionDto("discord", "Discord", visible = true),
            ru.starlitmoon.launcher.api.PrivacySectionDto("telegram", "Telegram", visible = true),
            ru.starlitmoon.launcher.api.PrivacySectionDto("status", "Статус", visible = true),
            ru.starlitmoon.launcher.api.PrivacySectionDto("bank", "Банк", visible = true),
            ru.starlitmoon.launcher.api.PrivacySectionDto("activity", "Активность", visible = true),
            ru.starlitmoon.launcher.api.PrivacySectionDto("clan", "Клан", visible = true),
        )
    }
    val notify = vm.meData?.cabinet?.notificationPrefs.orEmpty()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionTitle("Личный кабинет", vm.userName)
        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(vm.userName, color = StarlitColors.Text, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                    Spacer(Modifier.width(10.dp))
                    if (player?.online == true) StatusDot(true)
                    player?.activeBadge?.let {
                        Text("${it.emoji.orEmpty()} ${it.name.orEmpty()}", color = StarlitColors.Accent, fontSize = 13.sp)
                    }
                }
                StatRow("UUID", vm.userUuid ?: "—")
                StatRow("Ранги", player?.ranks?.joinToString(", ")?.ifBlank { "—" } ?: "—")
                StatRow("Предупреждения", (player?.warnCount ?: 0).toString())
                if (player?.banned == true) {
                    Text("Бан: ${player.banReason ?: "без причины"}", color = StarlitColors.Offline, fontWeight = FontWeight.Bold)
                }
                player?.stats?.let { s ->
                    StatRow("Наиграно", "${(s.playtimeMinutes ?: 0) / 60} ч")
                    StatRow("Смерти", (s.deaths ?: 0).toString())
                    StatRow("Убийства мобов", (s.mobKills ?: 0).toString())
                }
                player?.discord?.username?.let { StatRow("Discord", it) }
                player?.telegram?.username?.let { StatRow("Telegram", it) }
            }
        }

        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Статус профиля", color = StarlitColors.Text, fontWeight = FontWeight.Bold)
                StarlitTextField(vm.statusDraft, { vm.statusDraft = it }, "Текст статуса")
                StarlitPrimaryButton(
                    text = "Сохранить",
                    onClick = { vm.saveProfileStatus() },
                    loading = vm.isLoading,
                )
            }
        }

        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Приватность", color = StarlitColors.Text, fontWeight = FontWeight.Bold)
                sections.forEach { section ->
                    val id = section.id ?: return@forEach
                    val visible = section.visible && !section.hidden
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(section.label ?: id, color = StarlitColors.TextMuted)
                        StarlitSecondaryButton(
                            text = if (visible) "Видно" else "Скрыто",
                            onClick = { vm.setPrivacy(id, !visible) },
                        )
                    }
                }
            }
        }

        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Уведомления", color = StarlitColors.Text, fontWeight = FontWeight.Bold)
                listOf("ingame" to "В игре", "discord" to "Discord").forEach { (key, label) ->
                    val enabled = notify[key] != false
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, color = StarlitColors.TextMuted)
                        StarlitSecondaryButton(
                            text = if (enabled) "Вкл" else "Выкл",
                            onClick = { vm.setNotifyPref(key, !enabled) },
                        )
                    }
                }
                val badgeVisible = player?.badgeVisible != false
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Значок", color = StarlitColors.TextMuted)
                    StarlitSecondaryButton(
                        text = if (badgeVisible) "Виден" else "Скрыт",
                        onClick = { vm.setBadgeVisible(!badgeVisible) },
                    )
                }
            }
        }

        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Лента", color = StarlitColors.Text, fontWeight = FontWeight.Bold)
                if (vm.notifications.isEmpty()) {
                    Text("Пока пусто", color = StarlitColors.TextMuted, fontSize = 13.sp)
                } else {
                    vm.notifications.take(10).forEach { n ->
                        Text(n.title ?: "Уведомление", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(n.message.orEmpty(), color = StarlitColors.TextMuted, fontSize = 12.sp)
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionTitle("Настройки", "Клиент и обновления")
        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatRow("Версия лаунчера", LauncherVersion.CURRENT)
                StatRow("Сервер", config.serverHost)
                StatRow("Клиент", config.minecraftVersionId)
                StatRow("Память", "${config.minMemoryMb}–${config.maxMemoryMb} MB")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StarlitPrimaryButton(
                        text = if (vm.isCheckingUpdates) "Проверка…" else "Проверить обновления",
                        onClick = { vm.checkForUpdates(false) },
                        loading = vm.isCheckingUpdates,
                    )
                    if (vm.updateInfo != null) {
                        StarlitSecondaryButton(
                            text = "Скачать ${vm.updateInfo!!.latestVersion}",
                            onClick = { vm.downloadUpdate() },
                        )
                    }
                }
            }
        }
    }
}
