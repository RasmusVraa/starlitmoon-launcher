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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.starlitmoon.launcher.LauncherConfig
import ru.starlitmoon.launcher.LauncherVersion
import ru.starlitmoon.launcher.ui.components.BrandMark
import ru.starlitmoon.launcher.ui.components.SettingsRow
import ru.starlitmoon.launcher.ui.components.StarlitCard
import ru.starlitmoon.launcher.ui.components.StarlitPrimaryButton
import ru.starlitmoon.launcher.ui.components.StarlitProgressBar
import ru.starlitmoon.launcher.ui.components.StarlitSecondaryButton
import ru.starlitmoon.launcher.ui.components.StarlitTextField
import ru.starlitmoon.launcher.ui.components.StarlitToggle
import ru.starlitmoon.launcher.ui.components.StatRow
import ru.starlitmoon.launcher.ui.components.StatusDot
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.ui.theme.StarlitTitleGradient
import ru.starlitmoon.launcher.viewmodel.LauncherTab
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel
import javax.swing.JFileChooser

@Composable
fun PlayScreen(vm: LauncherViewModel) {
    val config = vm.configState
    val showProgress = vm.launchProgress != null || vm.isLoading
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            "Java Edition · Vanilla",
            color = StarlitColors.Accent,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
        )
        Text(
            "STARLITMOON",
            fontSize = 56.sp,
            fontWeight = FontWeight.ExtraBold,
            style = TextStyle(brush = StarlitTitleGradient),
            letterSpacing = 2.sp,
            lineHeight = 60.sp,
        )
        Text(
            "Ванильный сервер под звёздным небом. Версия ${vm.serverVersion}. Честный геймплей, экономика и живое комьюнити.",
            color = StarlitColors.TextMuted,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.width(580.dp),
        )
        Spacer(Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StarlitPrimaryButton(
                text = "ИГРАТЬ",
                onClick = {
                    if (vm.isLoggedIn) vm.play() else vm.currentTab = LauncherTab.Cabinet
                },
                modifier = Modifier.width(168.dp),
                loading = vm.isLoading,
            )
            StarlitSecondaryButton(
                text = "НАСТРОИТЬ",
                onClick = { vm.currentTab = LauncherTab.Settings },
                modifier = Modifier.width(168.dp),
            )
        }
        if (showProgress) {
            StarlitProgressBar(
                progress = vm.launchProgressFraction,
                label = vm.launchProgress ?: "Запуск…",
                modifier = Modifier.width(520.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            StarlitCard(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Сервер", color = StarlitColors.Text, fontWeight = FontWeight.Bold)
                    StatRow("Адрес", config.serverHost)
                    StatRow(
                        "Онлайн",
                        "${vm.serverStatus.playersOnline}/${vm.serverStatus.playersMax}",
                        vm.serverStatus.online,
                    )
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
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(config.serverHost, color = StarlitColors.TextMuted, fontSize = 12.sp)
            Text("v${LauncherVersion.CURRENT}", color = StarlitColors.TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
fun LoginScreen(vm: LauncherViewModel) {
    var nickname by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark(modifier = Modifier.padding(bottom = 20.dp))
        Text(
            "ВХОД",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            style = TextStyle(brush = StarlitTitleGradient),
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Ник и пароль с сервера StarlitMoon",
            color = StarlitColors.TextMuted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(24.dp))
        StarlitCard(modifier = Modifier.width(420.dp)) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StarlitTextField(nickname, { nickname = it }, "Ник")
                StarlitTextField(password, { password = it }, "Пароль", isPassword = true)
                StarlitPrimaryButton(
                    "Войти",
                    onClick = { vm.login(nickname, password) },
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
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshCabinet() }
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
        Text(
            "ЛИЧНЫЙ КАБИНЕТ",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = StarlitColors.Text,
        )
        Text(vm.userName, color = StarlitColors.TextMuted, fontSize = 14.sp)
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
    val base = vm.configState
    var memoryAuto by remember(base) { mutableStateOf(base.memoryAuto) }
    var memoryGb by remember(base) {
        mutableStateOf(if (base.memoryAuto) 0f else base.maxMemoryMb / 1024f)
    }
    var fullscreen by remember(base) { mutableStateOf(base.fullscreen) }
    var autoJoinServer by remember(base) { mutableStateOf(base.autoJoinServer) }
    var keepLauncherOpen by remember(base) { mutableStateOf(base.keepLauncherOpen) }
    var autoLogin by remember(base) { mutableStateOf(base.autoLogin) }
    var savePassword by remember(base) { mutableStateOf(base.savePassword) }
    var vsync by remember(base) { mutableStateOf(base.vsync) }
    var gamePath by remember(base) { mutableStateOf(base.gamePath) }

    fun memoryLabel(): String = when {
        memoryAuto || memoryGb <= 0f -> "Автоматически"
        else -> "${memoryGb.toInt()} ГБ"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "НАСТРОЙКИ",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            style = TextStyle(brush = StarlitTitleGradient),
            letterSpacing = 2.sp,
        )
        Text(
            "Клиент, память и папка игры",
            color = StarlitColors.TextMuted,
            fontSize = 14.sp,
        )

        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                SettingsRow(
                    title = "Память (RAM)",
                    subtitle = memoryLabel(),
                    icon = {
                        Icon(Icons.Default.Memory, null, tint = StarlitColors.Accent)
                    },
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Slider(
                            value = if (memoryAuto) 0f else memoryGb.coerceIn(1f, 16f),
                            onValueChange = { v ->
                                if (v <= 0.5f) {
                                    memoryAuto = true
                                    memoryGb = 0f
                                } else {
                                    memoryAuto = false
                                    memoryGb = v.coerceIn(1f, 16f)
                                }
                            },
                            valueRange = 0f..16f,
                            steps = 15,
                            modifier = Modifier.width(200.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = StarlitColors.Accent,
                                activeTrackColor = StarlitColors.Accent,
                                inactiveTrackColor = StarlitColors.CardBorder,
                            ),
                        )
                    }
                }

                HorizontalDivider(color = StarlitColors.CardBorder)

                SettingsRow(
                    title = "Полный экран",
                    subtitle = "Запуск Minecraft на весь экран",
                    icon = { Icon(Icons.Default.Fullscreen, null, tint = StarlitColors.Accent) },
                ) {
                    StarlitToggle(checked = fullscreen, onCheckedChange = { fullscreen = it })
                }

                HorizontalDivider(color = StarlitColors.CardBorder)

                SettingsRow(
                    title = "Авто-подключение",
                    subtitle = "Подключаться к ${base.serverHost} после запуска",
                    icon = { Icon(Icons.Default.OpenInNew, null, tint = StarlitColors.Accent) },
                ) {
                    StarlitToggle(checked = autoJoinServer, onCheckedChange = { autoJoinServer = it })
                }

                HorizontalDivider(color = StarlitColors.CardBorder)

                SettingsRow(
                    title = "Оставить лаунчер открытым",
                    subtitle = "Не закрывать окно после запуска игры",
                    icon = { Icon(Icons.Default.VideogameAsset, null, tint = StarlitColors.Accent) },
                ) {
                    StarlitToggle(checked = keepLauncherOpen, onCheckedChange = { keepLauncherOpen = it })
                }

                HorizontalDivider(color = StarlitColors.CardBorder)

                SettingsRow(
                    title = "Авто-вход на сервере",
                    subtitle = "Команда /login после входа в мир",
                    icon = { Icon(Icons.Default.Login, null, tint = StarlitColors.Accent) },
                ) {
                    StarlitToggle(checked = autoLogin, onCheckedChange = { autoLogin = it })
                }

                HorizontalDivider(color = StarlitColors.CardBorder)

                SettingsRow(
                    title = "Сохранять пароль",
                    subtitle = "Хранить пароль локально (не рекомендуется)",
                    icon = { Icon(Icons.Default.Save, null, tint = StarlitColors.Accent) },
                ) {
                    StarlitToggle(checked = savePassword, onCheckedChange = { savePassword = it })
                }

                HorizontalDivider(color = StarlitColors.CardBorder)

                SettingsRow(
                    title = "VSync",
                    subtitle = "Вертикальная синхронизация кадров",
                    icon = { Icon(Icons.Default.Sync, null, tint = StarlitColors.Accent) },
                ) {
                    StarlitToggle(checked = vsync, onCheckedChange = { vsync = it })
                }
            }
        }

        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsRow(
                    title = "Папка игры",
                    subtitle = "Minecraft, assets и версии",
                    icon = { Icon(Icons.Default.FolderOpen, null, tint = StarlitColors.Accent) },
                ) {
                    StarlitSecondaryButton(
                        text = "Обзор",
                        onClick = {
                            val chooser = JFileChooser().apply {
                                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                dialogTitle = "Выберите папку игры"
                                currentDirectory = base.gameDir.toFile()
                            }
                            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                gamePath = chooser.selectedFile?.absolutePath.orEmpty()
                            }
                        },
                    )
                }
                StarlitTextField(
                    value = gamePath,
                    onValueChange = { gamePath = it },
                    label = "Путь (пусто = по умолчанию)",
                )
            }
        }

        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Обновления", color = StarlitColors.Text, fontWeight = FontWeight.Bold)
                StatRow("Версия лаунчера", LauncherVersion.CURRENT)
                StatRow("Клиент", base.minecraftVersionId)
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

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StarlitPrimaryButton(
                text = "Сохранить",
                onClick = {
                    val maxMb = if (memoryAuto) base.maxMemoryMb else (memoryGb.toInt().coerceIn(1, 16) * 1024)
                    vm.saveSettings(
                        base.copy(
                            memoryAuto = memoryAuto,
                            maxMemoryMb = maxMb,
                            minMemoryMb = (maxMb / 2).coerceAtLeast(1024),
                            fullscreen = fullscreen,
                            autoJoinServer = autoJoinServer,
                            keepLauncherOpen = keepLauncherOpen,
                            autoLogin = autoLogin,
                            savePassword = savePassword,
                            vsync = vsync,
                            gamePath = gamePath.trim(),
                        ),
                    )
                },
                modifier = Modifier.width(180.dp),
            )
            StarlitSecondaryButton(
                text = "Сбросить",
                onClick = {
                    val defaults = LauncherConfig.load()
                    memoryAuto = defaults.memoryAuto
                    memoryGb = if (defaults.memoryAuto) 0f else defaults.maxMemoryMb / 1024f
                    fullscreen = defaults.fullscreen
                    autoJoinServer = defaults.autoJoinServer
                    keepLauncherOpen = defaults.keepLauncherOpen
                    autoLogin = defaults.autoLogin
                    savePassword = defaults.savePassword
                    vsync = defaults.vsync
                    gamePath = defaults.gamePath
                },
            )
        }
    }
}
