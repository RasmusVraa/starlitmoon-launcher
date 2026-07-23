package ru.starlitmoon.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.starlitmoon.launcher.LauncherConfig
import ru.starlitmoon.launcher.LauncherVersion
import ru.starlitmoon.launcher.api.ModpackDto
import ru.starlitmoon.launcher.ui.components.BrandMark
import ru.starlitmoon.launcher.ui.components.HeroBackground
import ru.starlitmoon.launcher.ui.components.MessageBanner
import ru.starlitmoon.launcher.ui.components.NetworkAvatar
import ru.starlitmoon.launcher.ui.components.SectionTitle
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
import ru.starlitmoon.launcher.viewmodel.LauncherTab
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel
import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val showProgress = vm.launchProgress != null || vm.isLoading
    val packName = vm.selectedModpack?.name ?: "Vanilla"

    HeroBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "STARLITMOON",
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = StarlitColors.Text,
                letterSpacing = 4.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Ванильный сервер под звёздным небом.\nСборка: $packName · Minecraft ${vm.configState.minecraftVersionId}",
                color = StarlitColors.TextMuted,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            )
            Spacer(Modifier.height(36.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (vm.isGameRunning) {
                    StarlitPrimaryButton(
                        text = "ОСТАНОВИТЬ",
                        onClick = { vm.stopGame() },
                        modifier = Modifier.width(180.dp),
                        danger = true,
                    )
                } else {
                    StarlitPrimaryButton(
                        text = "ИГРАТЬ",
                        onClick = { vm.play() },
                        modifier = Modifier.width(180.dp),
                        loading = vm.isLoading,
                        enabled = !vm.isLoading,
                    )
                }
                StarlitSecondaryButton(
                    text = "НАСТРОИТЬ",
                    onClick = { vm.currentTab = LauncherTab.Settings },
                    modifier = Modifier.width(180.dp),
                )
            }
            if (showProgress) {
                Spacer(Modifier.height(28.dp))
                StarlitProgressBar(
                    progress = vm.launchProgressFraction,
                    label = vm.launchProgress ?: "Запуск…",
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(0.55f),
                )
            }
            Spacer(Modifier.height(48.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                FooterLink("Telegram") {
                    openUrl("https://t.me/starlitmoon")
                }
                FooterLink("Сайт") {
                    openUrl("https://starlit-moon.ru")
                }
            }
        }
    }
}

@Composable
private fun FooterLink(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = StarlitColors.Purple,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private fun openUrl(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuildsScreen(vm: LauncherViewModel) {
    LaunchedEffect(Unit) { vm.fetchModpacks() }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "ОФИЦИАЛЬНЫЕ СБОРКИ",
            color = StarlitColors.Purple,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "ВЫБЕРИ СБОРКУ",
            color = StarlitColors.Text,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Это клиентские сборки для одного сервера StarlitMoon — не отдельные миры. ZIP распаковывается в папку сборки.",
            color = StarlitColors.TextMuted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StarlitSecondaryButton(
                text = "Папка сборки",
                onClick = { vm.openPackFolder() },
                modifier = Modifier.width(150.dp),
            )
            StarlitSecondaryButton(
                text = "Обновить список",
                onClick = { vm.fetchModpacks() },
                modifier = Modifier.width(160.dp),
            )
        }
        Spacer(Modifier.height(20.dp))

        when {
            vm.isLoadingModpacks -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = StarlitColors.Gold, strokeWidth = 2.dp)
                }
            }
            vm.modpacks.isEmpty() -> {
                StarlitCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Сборки пока недоступны", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                        Text(
                            "API /api/modpacks не ответил. Попробуйте обновить позже.",
                            color = StarlitColors.TextMuted,
                            fontSize = 13.sp,
                        )
                        StarlitSecondaryButton(
                            text = "Обновить",
                            onClick = { vm.fetchModpacks() },
                            modifier = Modifier.width(140.dp),
                        )
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 240.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(vm.modpacks, key = { it.id ?: it.slug ?: it.name.orEmpty() }) { pack ->
                        ModpackCard(
                            pack = pack,
                            selected = pack.id == vm.selectedModpack?.id ||
                                (pack.slug != null && pack.slug == vm.selectedModpack?.slug),
                            onSelect = { vm.selectModpack(pack) },
                            onOpenFolder = { vm.openPackFolder(pack) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModpackCard(
    pack: ModpackDto,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpenFolder: () -> Unit,
) {
    StarlitCard(
        selected = selected,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                pack.name ?: pack.slug ?: "Сборка",
                color = StarlitColors.Text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                pack.description?.ifBlank { null } ?: "Без описания",
                color = StarlitColors.TextMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TagChip(pack.loader?.uppercase() ?: "VANILLA")
                TagChip("MC ${pack.mcVersion ?: "—"}")
                when {
                    pack.hasArchive -> TagChip(formatArchiveSize(pack.archive?.size))
                    pack.modsCount > 0 -> TagChip("${pack.modsCount} модов")
                    else -> TagChip("Без архива")
                }
                pack.tags.take(3).forEach { TagChip(it) }
            }
            if (selected) {
                Text("Выбрана", color = StarlitColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                StarlitSecondaryButton(
                    text = "Открыть папку",
                    onClick = onOpenFolder,
                    modifier = Modifier.width(140.dp),
                )
            }
        }
    }
}

private fun formatArchiveSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return "ZIP"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 10) "ZIP ${mb.toInt()} МБ" else "ZIP ${"%.1f".format(mb)} МБ"
}

@Composable
private fun TagChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(StarlitColors.PurpleMuted)
            .border(1.dp, StarlitColors.Purple.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = StarlitColors.Text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun LoginScreen(vm: LauncherViewModel) {
    var nickname by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    HeroBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrandMark(size = 64.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                "STARLITMOON",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = StarlitColors.Text,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Войдите, чтобы открыть лаунчер",
                color = StarlitColors.TextMuted,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(28.dp))
            vm.errorMessage?.let {
                MessageBanner(it, true, vm::clearMessages)
                Spacer(Modifier.height(8.dp))
            }
            StarlitCard(modifier = Modifier.width(400.dp)) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    StarlitTextField(nickname, { nickname = it }, "Ник")
                    StarlitTextField(password, { password = it }, "Пароль", isPassword = true)
                    Spacer(Modifier.height(4.dp))
                    StarlitPrimaryButton(
                        text = "Войти",
                        onClick = { vm.login(nickname, password) },
                        modifier = Modifier.fillMaxWidth(),
                        loading = vm.isLoading,
                    )
                }
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
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(
            title = "Личный кабинет",
            subtitle = vm.userName,
        )
        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(vm.userName, color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    if (player?.online == true) StatusDot(true)
                    player?.activeBadge?.let {
                        Text(
                            "${it.emoji.orEmpty()} ${it.name.orEmpty()}",
                            color = StarlitColors.Gold,
                            fontSize = 13.sp,
                        )
                    }
                }
                StatRow("UUID", vm.userUuid ?: "—")
                StatRow("Ранги", player?.ranks?.joinToString(", ")?.ifBlank { "—" } ?: "—")
                StatRow("Предупреждения", (player?.warnCount ?: 0).toString())
                if (player?.banned == true) {
                    Text(
                        "Бан: ${player.banReason ?: "без причины"}",
                        color = StarlitColors.Offline,
                        fontWeight = FontWeight.SemiBold,
                    )
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

        SkinSection(vm)

        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Статус профиля", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
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
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Приватность", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                sections.forEach { section ->
                    val id = section.id ?: return@forEach
                    val visible = section.visible && !section.hidden
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(section.label ?: id, color = StarlitColors.TextMuted)
                        StarlitSecondaryButton(
                            text = if (visible) "Видно" else "Скрыто",
                            onClick = { vm.setPrivacy(id, !visible) },
                            modifier = Modifier.width(100.dp),
                        )
                    }
                }
            }
        }

        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Уведомления", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                listOf("ingame" to "В игре", "discord" to "Discord").forEach { (key, label) ->
                    val enabled = notify[key] != false
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, color = StarlitColors.TextMuted)
                        StarlitSecondaryButton(
                            text = if (enabled) "Вкл" else "Выкл",
                            onClick = { vm.setNotifyPref(key, !enabled) },
                            modifier = Modifier.width(88.dp),
                        )
                    }
                }
                val badgeVisible = player?.badgeVisible != false
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Значок", color = StarlitColors.TextMuted)
                    StarlitSecondaryButton(
                        text = if (badgeVisible) "Виден" else "Скрыт",
                        onClick = { vm.setBadgeVisible(!badgeVisible) },
                        modifier = Modifier.width(100.dp),
                    )
                }
            }
        }

        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Лента", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                if (vm.notifications.isEmpty()) {
                    Text("Пока пусто", color = StarlitColors.TextMuted, fontSize = 13.sp)
                } else {
                    vm.notifications.take(10).forEach { n ->
                        Text(
                            n.title ?: "Уведомление",
                            color = StarlitColors.Text,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                        )
                        Text(n.message.orEmpty(), color = StarlitColors.TextMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        if (vm.isAdmin) {
            StarlitSecondaryButton(
                text = "Открыть админ-панель",
                onClick = { vm.currentTab = LauncherTab.Admin },
                modifier = Modifier.width(220.dp),
            )
        }
    }
}

@Composable
private fun SkinSection(vm: LauncherViewModel) {
    var selectedPath by remember { mutableStateOf("") }
    val avatarUrl = remember(vm.userName) {
        "https://starlit-moon.ru/api/avatar?player=${URLEncoder.encode(vm.userName, "UTF-8")}&size=128"
    }

    StarlitCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Скин", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                NetworkAvatar(url = avatarUrl, fallbackName = vm.userName, size = 96.dp)

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (vm.skinLocalPath.isNotBlank()) {
                        StarlitTextField(
                            value = vm.skinLocalPath,
                            onValueChange = {},
                            label = "Установленный файл скина",
                            readOnly = true,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StarlitSecondaryButton(
                            text = "Выбрать PNG",
                            onClick = {
                                val chooser = JFileChooser().apply {
                                    fileSelectionMode = JFileChooser.FILES_ONLY
                                    dialogTitle = "Выберите файл скина"
                                    fileFilter = FileNameExtensionFilter("PNG изображение", "png")
                                }
                                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                    selectedPath = chooser.selectedFile?.absolutePath.orEmpty()
                                }
                            },
                            modifier = Modifier.width(160.dp),
                        )
                        StarlitPrimaryButton(
                            text = "Установить скин",
                            onClick = { vm.installSkin(selectedPath) },
                            enabled = selectedPath.isNotBlank() && !vm.skinBusy,
                            loading = vm.skinBusy,
                            modifier = Modifier.width(180.dp),
                        )
                    }
                    if (selectedPath.isNotBlank()) {
                        Text(selectedPath, color = StarlitColors.TextDim, fontSize = 11.sp)
                    }
                }
            }

            if (vm.skinCommand.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        vm.skinCommand,
                        color = StarlitColors.Gold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    StarlitSecondaryButton(
                        text = "Копировать команду",
                        onClick = { vm.copySkinCommand() },
                        modifier = Modifier.width(190.dp),
                    )
                }
            }

            Text(
                "Скин загружается на starlit-moon.ru и применяется на сервере через SkinsRestorer автоматически.",
                color = StarlitColors.TextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
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
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            "STARLITMOON CRAFT",
            color = StarlitColors.Purple,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
        )
        Text(
            "НАСТРОЙКИ",
            color = StarlitColors.Text,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Text(
            "Клиент, память и папка игры",
            color = StarlitColors.TextMuted,
            fontSize = 14.sp,
        )

        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                SettingsRow(
                    title = "Память (RAM)",
                    subtitle = memoryLabel(),
                    icon = {
                        Icon(Icons.Default.Memory, null, tint = StarlitColors.Gold)
                    },
                ) {
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
                            thumbColor = StarlitColors.Gold,
                            activeTrackColor = StarlitColors.Gold,
                            inactiveTrackColor = StarlitColors.Border,
                        ),
                    )
                }

                HorizontalDivider(color = StarlitColors.Border)

                SettingsRow(
                    title = "Полный экран",
                    subtitle = "Запуск Minecraft на весь экран",
                    icon = { Icon(Icons.Default.Fullscreen, null, tint = StarlitColors.Gold) },
                ) {
                    StarlitToggle(checked = fullscreen, onCheckedChange = { fullscreen = it })
                }

                HorizontalDivider(color = StarlitColors.Border)

                SettingsRow(
                    title = "Авто-подключение",
                    subtitle = "Подключаться к ${base.serverHost} после запуска",
                    icon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = StarlitColors.Gold) },
                ) {
                    StarlitToggle(checked = autoJoinServer, onCheckedChange = { autoJoinServer = it })
                }

                HorizontalDivider(color = StarlitColors.Border)

                SettingsRow(
                    title = "Оставить лаунчер открытым",
                    subtitle = "Не закрывать окно после запуска игры",
                    icon = { Icon(Icons.Default.VideogameAsset, null, tint = StarlitColors.Gold) },
                ) {
                    StarlitToggle(checked = keepLauncherOpen, onCheckedChange = { keepLauncherOpen = it })
                }

                HorizontalDivider(color = StarlitColors.Border)

                SettingsRow(
                    title = "Авто-вход на сервере",
                    subtitle = "Команда /login после входа в мир",
                    icon = { Icon(Icons.AutoMirrored.Filled.Login, null, tint = StarlitColors.Gold) },
                ) {
                    StarlitToggle(checked = autoLogin, onCheckedChange = { autoLogin = it })
                }

                HorizontalDivider(color = StarlitColors.Border)

                SettingsRow(
                    title = "Сохранять пароль",
                    subtitle = "Хранить пароль локально (не рекомендуется)",
                    icon = { Icon(Icons.Default.Save, null, tint = StarlitColors.Gold) },
                ) {
                    StarlitToggle(checked = savePassword, onCheckedChange = { savePassword = it })
                }

                HorizontalDivider(color = StarlitColors.Border)

                SettingsRow(
                    title = "VSync",
                    subtitle = "Вертикальная синхронизация кадров",
                    icon = { Icon(Icons.Default.Sync, null, tint = StarlitColors.Gold) },
                ) {
                    StarlitToggle(checked = vsync, onCheckedChange = { vsync = it })
                }
            }
        }

        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsRow(
                    title = "Папка игры",
                    subtitle = "Общие assets и версии клиента",
                    icon = { Icon(Icons.Default.FolderOpen, null, tint = StarlitColors.Gold) },
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
                        modifier = Modifier.width(100.dp),
                    )
                }
                StarlitTextField(
                    value = gamePath,
                    onValueChange = { gamePath = it },
                    label = "Путь (пусто = по умолчанию)",
                )
                SettingsRow(
                    title = "Папка сборки",
                    subtitle = "ZIP модпака: mods, resourcepacks, config",
                    icon = { Icon(Icons.Default.FolderOpen, null, tint = StarlitColors.Gold) },
                ) {
                    StarlitSecondaryButton(
                        text = "Открыть",
                        onClick = { vm.openPackFolder() },
                        modifier = Modifier.width(100.dp),
                    )
                }
            }
        }

        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Обновления", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                StatRow("Версия лаунчера", LauncherVersion.CURRENT)
                StatRow("Клиент", base.minecraftVersionId)
                StatRow("Сборка", vm.selectedModpack?.name ?: base.selectedModpackId.ifBlank { "—" })
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
