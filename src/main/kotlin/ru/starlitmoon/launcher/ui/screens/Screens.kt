package ru.starlitmoon.launcher.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ru.starlitmoon.launcher.LauncherConfig
import ru.starlitmoon.launcher.LauncherVersion
import ru.starlitmoon.launcher.api.ModpackDto
import ru.starlitmoon.launcher.ui.components.BrandMark
import ru.starlitmoon.launcher.ui.components.HeroBackground
import ru.starlitmoon.launcher.ui.components.MessageBanner
import ru.starlitmoon.launcher.ui.components.SectionTitle
import ru.starlitmoon.launcher.ui.components.SettingsRow
import ru.starlitmoon.launcher.ui.components.StarlitCard
import ru.starlitmoon.launcher.ui.components.StarlitConfirmDialog
import ru.starlitmoon.launcher.ui.components.StarlitPrimaryButton
import ru.starlitmoon.launcher.ui.components.StarlitProgressBar
import ru.starlitmoon.launcher.ui.components.StarlitSecondaryButton
import ru.starlitmoon.launcher.ui.components.StarlitTextField
import ru.starlitmoon.launcher.ui.components.StarlitToggle
import ru.starlitmoon.launcher.ui.components.StatRow
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.ui.theme.StarlitDimens
import ru.starlitmoon.launcher.viewmodel.LauncherTab
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel
import java.awt.Desktop
import java.net.URI
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val showProgress = vm.launchProgress != null
    val packName = vm.selectedModpack?.name ?: "Не выбрана"
    val packLoader = vm.selectedModpack?.loader?.replaceFirstChar { it.uppercase() } ?: "—"
    val mcVer = vm.selectedModpack?.mcVersion?.trim()?.ifBlank { null }
        ?: vm.configState.minecraftVersionId
    val online = vm.serverStatus.online
    val players = vm.serverStatus.playersOnline
    val rank = ru.starlitmoon.launcher.ui.theme.PlayerRanks.highest(
        vm.meData?.cabinet?.player?.ranks.orEmpty(),
    )

    HeroBackground {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1.15f),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Text(
                    "Добро пожаловать,",
                    color = StarlitColors.TextMuted,
                    fontSize = 15.sp,
                )
                Text(
                    vm.userName.ifBlank { "игрок" },
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = StarlitColors.Text,
                )
                if (rank != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        rank.labelRu,
                        color = rank.foreground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (vm.isGameRunning) {
                        StarlitPrimaryButton(
                            text = "ОСТАНОВИТЬ",
                            onClick = { vm.stopGame() },
                            modifier = Modifier.width(200.dp),
                            danger = true,
                        )
                    } else {
                        StarlitPrimaryButton(
                            text = "ИГРАТЬ",
                            onClick = { vm.play() },
                            modifier = Modifier.width(200.dp),
                            loading = showProgress,
                            enabled = !showProgress,
                        )
                    }
                    StarlitSecondaryButton(
                        text = "СБОРКИ",
                        onClick = { vm.currentTab = LauncherTab.Builds },
                        modifier = Modifier.width(160.dp),
                    )
                    StarlitSecondaryButton(
                        text = "СКИНЫ",
                        onClick = { vm.currentTab = LauncherTab.Skins },
                        modifier = Modifier.width(140.dp),
                    )
                }
                if (showProgress) {
                    Spacer(Modifier.height(22.dp))
                    StarlitProgressBar(
                        progress = vm.launchProgressFraction,
                        label = vm.launchProgress ?: "Запуск…",
                        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(0.85f),
                    )
                }
                Spacer(Modifier.height(36.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    FooterLink("Telegram") {
                        openUrl("https://t.me/starlitmoon")
                    }
                    FooterLink("Сайт") {
                        openUrl("https://starlit-moon.ru")
                    }
                    FooterLink("Настройки") {
                        vm.currentTab = LauncherTab.Settings
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .widthIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HomeInfoCard(
                    eyebrow = "СЕРВЕР",
                    title = if (online) "Онлайн" else "Оффлайн",
                    subtitle = if (online) {
                        "Игроков сейчас: $players · ${vm.configState.serverHost}"
                    } else {
                        vm.configState.serverHost
                    },
                    accentOnline = online,
                )
                HomeInfoCard(
                    eyebrow = "СБОРКА",
                    title = packName,
                    subtitle = "$packLoader · Minecraft $mcVer",
                    actionLabel = "Выбрать другую",
                    onAction = { vm.currentTab = LauncherTab.Builds },
                )
                HomeInfoCard(
                    eyebrow = "ПРОФИЛЬ",
                    title = "Скин и плащ",
                    subtitle = if (vm.activeSkinPath != null) {
                        "Активный скин из библиотеки"
                    } else {
                        "Добавь скин в библиотеку — он появится в игре"
                    },
                    actionLabel = "Открыть скины",
                    onAction = { vm.currentTab = LauncherTab.Skins },
                )
            }
        }
    }
}

@Composable
private fun HomeInfoCard(
    eyebrow: String,
    title: String,
    subtitle: String,
    accentOnline: Boolean? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x38101828))
            .border(1.dp, Color(0x22A8B8D8), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (accentOnline != null) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (accentOnline) StarlitColors.Online else StarlitColors.Offline),
                )
            }
            Text(
                eyebrow,
                color = StarlitColors.TextDim,
                fontSize = 11.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
        }
        Text(title, color = StarlitColors.Text.copy(alpha = 0.92f), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(subtitle, color = StarlitColors.TextMuted.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 18.sp)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            StarlitSecondaryButton(
                text = actionLabel,
                onClick = onAction,
                compact = true,
                modifier = Modifier.width(168.dp),
            )
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
    LaunchedEffect(Unit) { vm.fetchModpacks(force = false) }
    var reinstallTarget by remember { mutableStateOf<ModpackDto?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "ВЫБЕРИ СБОРКУ",
                color = StarlitColors.Text,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Клиентские сборки для сервера.",
                color = StarlitColors.TextMuted,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(12.dp))
            StarlitSecondaryButton(
                text = "Обновить список",
                onClick = { vm.fetchModpacks(force = true) },
                modifier = Modifier.width(160.dp),
            )
            Spacer(Modifier.height(20.dp))

            when {
                vm.isLoadingModpacks && vm.modpacks.isEmpty() -> {
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
                                onClick = { vm.fetchModpacks(force = true) },
                                modifier = Modifier.width(140.dp),
                            )
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 260.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(vm.modpacks, key = { it.id ?: it.slug ?: it.name.orEmpty() }) { pack ->
                            ModpackCard(
                                pack = pack,
                                selected = pack.id == vm.selectedModpack?.id ||
                                    (pack.slug != null && pack.slug == vm.selectedModpack?.slug),
                                needsUpdate = run {
                                    vm.packUiRevision
                                    vm.packNeedsUpdate(pack)
                                },
                                onSelect = { vm.selectModpack(pack) },
                                onOpenFolder = { vm.openPackFolder(pack) },
                                onReinstall = { reinstallTarget = pack },
                                apiBaseUrl = vm.configState.apiBaseUrl,
                                busy = vm.launchProgress != null,
                            )
                        }
                    }
                }
            }
        }

        reinstallTarget?.let { pack ->
            val name = pack.name ?: pack.slug ?: "сборку"
            StarlitConfirmDialog(
                title = "Переустановка сборки",
                message = "Переустановить «$name»?\nТекущие файлы сборки (кроме сохранений) будут заменены.",
                confirmText = "Переустановить",
                cancelText = "Отмена",
                danger = true,
                onConfirm = { vm.reinstallModpack(pack) },
                onDismiss = { reinstallTarget = null },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModpackCard(
    pack: ModpackDto,
    selected: Boolean,
    needsUpdate: Boolean,
    onSelect: () -> Unit,
    onOpenFolder: () -> Unit,
    onReinstall: () -> Unit,
    apiBaseUrl: String,
    busy: Boolean,
) {
    val cover = remember(pack.coverUrl, apiBaseUrl) {
        resolvePackCoverUrl(pack.coverUrl, apiBaseUrl)
    }
    val sizeLabel = remember(pack.archive?.size, pack.hasArchive) {
        when {
            pack.hasArchive && (pack.archive?.size ?: 0L) > 0L -> formatArchiveSize(pack.archive?.size)
            else -> null
        }
    }
    StarlitCard(
        selected = selected,
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clickable(onClick = onSelect),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box {
                NetworkCover(
                    url = cover,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                )
                IconButton(
                    onClick = onOpenFolder,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xCC0A0C12)),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Открыть папку сборки",
                        tint = StarlitColors.Gold,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (needsUpdate) {
                    Text(
                        "Требуется обновление",
                        color = StarlitColors.OnGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(StarlitColors.Offline)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                } else if (selected) {
                    Text(
                        "Выбрана",
                        color = StarlitColors.OnGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(StarlitColors.Gold)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                if (selected && needsUpdate) {
                    Text(
                        "Выбрана",
                        color = StarlitColors.OnGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(StarlitColors.Gold)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        pack.name ?: pack.slug ?: "Сборка",
                        color = StarlitColors.Text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (sizeLabel != null) {
                        Text(
                            sizeLabel,
                            color = StarlitColors.TextDim,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Text(
                    pack.description?.ifBlank { null } ?: "Без описания",
                    color = StarlitColors.TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        pack.loader?.replaceFirstChar { it.uppercase() },
                        pack.mcVersion?.let { "MC $it" },
                    ).joinToString(" · "),
                    color = StarlitColors.TextDim,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.weight(1f))
                if (pack.hasArchive) {
                    StarlitSecondaryButton(
                        text = if (needsUpdate) "Обновить сборку" else "Переустановить",
                        onClick = onReinstall,
                        modifier = Modifier.fillMaxWidth(),
                        compact = true,
                        enabled = !busy,
                    )
                }
            }
        }
    }
}

private fun resolvePackCoverUrl(coverUrl: String?, apiBaseUrl: String): String {
    val raw = coverUrl?.trim().orEmpty()
    if (raw.isEmpty()) return ""
    if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
    val base = apiBaseUrl.trimEnd('/')
    return if (raw.startsWith("/")) "$base$raw" else "$base/$raw"
}

@Composable
private fun NetworkCover(
    url: String,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        if (url.isBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        // Keep previous frame while refreshing so cached banners don't flicker.
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = ru.starlitmoon.launcher.util.ImageDiskCache.loadOrFetch(url) ?: return@runCatching null
                org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
            }.getOrNull()
        } ?: bitmap
    }
    Box(
        modifier = modifier
            .background(StarlitColors.SurfaceElevated)
            .clip(RoundedCornerShape(topStart = StarlitDimens.Radius, topEnd = StarlitDimens.Radius)),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text("STARLIT", color = StarlitColors.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun formatArchiveSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return ""
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1.0) return "%.1f ГБ".format(gb)
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 10) "${mb.toInt()} МБ" else "%.1f МБ".format(mb)
}

@Composable
private fun TagChip(text: String) {
    Box(
        modifier = Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(50))
            .background(StarlitColors.PurpleMuted)
            .border(1.dp, StarlitColors.Purple.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = StarlitColors.Text,
            fontSize = 11.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Medium,
        )
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
fun SettingsScreen(vm: LauncherViewModel) {
    val base = vm.configState
    var memoryAuto by remember(base) { mutableStateOf(base.memoryAuto) }
    var memoryGb by remember(base) {
        mutableStateOf(if (base.memoryAuto) 0f else base.maxMemoryMb / 1024f)
    }
    var fullscreen by remember(base) { mutableStateOf(base.fullscreen) }
    var keepLauncherOpen by remember(base) { mutableStateOf(base.keepLauncherOpen) }
    var savePassword by remember(base) { mutableStateOf(base.savePassword) }
    var vsync by remember(base) { mutableStateOf(base.vsync) }
    var discordRpcEnabled by remember(base) { mutableStateOf(base.discordRpcEnabled) }
    var saveHint by remember { mutableStateOf(false) }

    fun memoryLabel(): String = when {
        memoryAuto || memoryGb <= 0f -> "Автоматически"
        else -> "${memoryGb.toInt()} ГБ"
    }

    fun draftConfig(): LauncherConfig {
        val maxMb = if (memoryAuto) base.maxMemoryMb else (memoryGb.toInt().coerceIn(1, 16) * 1024)
        return base.copy(
            memoryAuto = memoryAuto,
            maxMemoryMb = maxMb,
            minMemoryMb = (maxMb / 2).coerceAtLeast(1024),
            fullscreen = fullscreen,
            autoJoinServer = false,
            keepLauncherOpen = keepLauncherOpen,
            autoLogin = false,
            savePassword = savePassword,
            vsync = vsync,
            discordRpcEnabled = discordRpcEnabled,
            gamePath = "",
        )
    }

    LaunchedEffect(memoryAuto, memoryGb, fullscreen, keepLauncherOpen, savePassword, vsync, discordRpcEnabled) {
        delay(400)
        val next = draftConfig()
        if (next != vm.configState) {
            vm.saveSettings(next, notify = false)
            saveHint = true
            delay(1200)
            saveHint = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            "НАСТРОЙКИ",
            color = StarlitColors.Text,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Text(
            if (saveHint) "Изменения сохранены автоматически" else "Клиент и память · автосохранение",
            color = if (saveHint) StarlitColors.Online else StarlitColors.TextMuted,
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
                    title = "Оставить лаунчер открытым",
                    subtitle = "Не закрывать окно после запуска игры",
                    icon = { Icon(Icons.Default.VideogameAsset, null, tint = StarlitColors.Gold) },
                ) {
                    StarlitToggle(checked = keepLauncherOpen, onCheckedChange = { keepLauncherOpen = it })
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

                HorizontalDivider(color = StarlitColors.Border)

                SettingsRow(
                    title = "Discord RPC",
                    subtitle = "Статус в Discord: лаунчер и выбранная сборка",
                    icon = { Icon(Icons.Default.VideogameAsset, null, tint = StarlitColors.Gold) },
                ) {
                    StarlitToggle(checked = discordRpcEnabled, onCheckedChange = { discordRpcEnabled = it })
                }
            }
        }

        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsRow(
                    title = "Папка сборки",
                    subtitle = "ZIP модпака: mods, resourcepacks, config",
                    icon = { Icon(Icons.Default.FolderOpen, null, tint = StarlitColors.Gold) },
                ) {
                    StarlitSecondaryButton(
                        text = "Открыть",
                        onClick = { vm.openPackFolder() },
                        modifier = Modifier.width(110.dp),
                        compact = true,
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
                        compact = true,
                    )
                    if (vm.updateInfo != null) {
                        StarlitPrimaryButton(
                            text = when {
                                vm.isApplyingUpdate -> "Обновление…"
                                else -> "Обновить до ${vm.updateInfo!!.latestVersion}"
                            },
                            onClick = { vm.downloadUpdate() },
                            loading = vm.isApplyingUpdate,
                            enabled = !vm.isApplyingUpdate,
                            compact = true,
                        )
                    }
                    if (vm.isApplyingUpdate && vm.updateProgress != null) {
                        Text(vm.updateProgress!!, color = StarlitColors.Gold, fontSize = 12.sp)
                    }
                }
            }
        }

        StarlitSecondaryButton(
            text = "Сбросить настройки",
            onClick = {
                memoryAuto = true
                memoryGb = 0f
                fullscreen = false
                keepLauncherOpen = false
                savePassword = false
                vsync = true
                discordRpcEnabled = true
                vm.saveSettings(
                    base.copy(
                        memoryAuto = true,
                        maxMemoryMb = 4096,
                        minMemoryMb = 2048,
                        fullscreen = false,
                        keepLauncherOpen = false,
                        savePassword = false,
                        vsync = true,
                        discordRpcEnabled = true,
                        autoJoinServer = false,
                        autoLogin = false,
                        gamePath = "",
                    ),
                    notify = true,
                )
            },
            modifier = Modifier.width(220.dp),
            compact = true,
        )
    }
}
