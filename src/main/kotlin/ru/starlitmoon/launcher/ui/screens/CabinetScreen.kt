package ru.starlitmoon.launcher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.starlitmoon.launcher.api.PrivacySectionDto
import ru.starlitmoon.launcher.ui.components.NetworkAvatar
import ru.starlitmoon.launcher.ui.components.StarlitPrimaryButton
import ru.starlitmoon.launcher.ui.components.StarlitSecondaryButton
import ru.starlitmoon.launcher.ui.components.StarlitTextField
import ru.starlitmoon.launcher.ui.components.StarlitToggle
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.ui.theme.StarlitDimens
import ru.starlitmoon.launcher.viewmodel.LauncherTab
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CabinetScreen(vm: LauncherViewModel) {
    if (!vm.isLoggedIn) {
        LoginScreen(vm)
        return
    }
    LaunchedEffect(Unit) { vm.refreshCabinet() }

    val cabinet = vm.meData?.cabinet
    val player = cabinet?.player
    val badges = cabinet?.badges
    val profileMissing = cabinet?.found == false
    val sections = cabinet?.sections.orEmpty().ifEmpty {
        listOf(
            PrivacySectionDto("stats", "Статистика", hint = "Часы, смерти, убийства", visible = true),
            PrivacySectionDto("discord", "Discord", visible = true),
            PrivacySectionDto("telegram", "Telegram", visible = true),
            PrivacySectionDto("status", "Статус", visible = true),
            PrivacySectionDto("bank", "Банк", visible = true),
            PrivacySectionDto("activity", "Активность", visible = true),
            PrivacySectionDto("clan", "Клан", visible = true),
        )
    }
    val notifyChannels = cabinet?.notificationChannels.orEmpty().ifEmpty {
        listOf(
            ru.starlitmoon.launcher.api.NotifyChannelDto("ingame", "В игре", "Сообщение в чат Minecraft", enabled = true),
            ru.starlitmoon.launcher.api.NotifyChannelDto("discord", "Discord", "ЛС от бота", enabled = true),
        )
    }
    val ownedBadges = badges?.owned.orEmpty()
    val activeBadgeId = badges?.activeBadgeId ?: player?.activeBadgeId
    val activeBadge = badges?.activeBadge ?: player?.activeBadge
    val avatarUrl = remember(vm.userName, vm.avatarRevision, vm.skinTextureHash) {
        vm.avatarUrl(160) + "&r=${vm.avatarRevision}"
    }
    var selectedSkinPath by remember { mutableStateOf("") }
    var notifyOpen by remember { mutableStateOf(false) }
    var privacyOpen by remember { mutableStateOf(false) }
    var badgeDraft by remember(activeBadgeId) { mutableStateOf(activeBadgeId.orEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Личный кабинет",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            style = androidx.compose.ui.text.TextStyle(
                brush = Brush.linearGradient(
                    listOf(StarlitColors.Text, StarlitColors.Gold, StarlitColors.Purple),
                ),
            ),
        )

        // —— cabinet-shell ——
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xF5161C30), Color(0xF00A0E1C)),
                    ),
                )
                .border(1.dp, Color(0x33788CDC), RoundedCornerShape(20.dp)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                // —— left column ——
                Column(
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight()
                        .border(width = 1.dp, color = Color(0x1F788CDC)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0x556B5CE7), Color(0x22121830), Color(0xFF0A0E1A)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        NetworkAvatar(url = avatarUrl, fallbackName = vm.userName, size = 140.dp)
                    }

                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Скин", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StarlitSecondaryButton(
                                text = "PNG",
                                onClick = {
                                    val chooser = JFileChooser().apply {
                                        fileSelectionMode = JFileChooser.FILES_ONLY
                                        dialogTitle = "Выберите файл скина"
                                        fileFilter = FileNameExtensionFilter("PNG", "png")
                                    }
                                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                        selectedSkinPath = chooser.selectedFile?.absolutePath.orEmpty()
                                    }
                                },
                                modifier = Modifier.width(72.dp),
                            )
                            StarlitPrimaryButton(
                                text = "Установить",
                                onClick = { vm.installSkin(selectedSkinPath) },
                                enabled = selectedSkinPath.isNotBlank() && !vm.skinBusy,
                                loading = vm.skinBusy,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (selectedSkinPath.isNotBlank()) {
                            Text(
                                selectedSkinPath.substringAfterLast('\\').substringAfterLast('/'),
                                color = StarlitColors.TextDim,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (vm.skinCommand.isNotBlank()) {
                            Text(vm.skinCommand, color = StarlitColors.Gold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    if (!profileMissing) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Контакты", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            val discord = player?.discord?.username
                            val telegram = player?.telegram?.username
                            if (discord.isNullOrBlank() && telegram.isNullOrBlank()) {
                                Text("Не привязаны", color = StarlitColors.TextMuted, fontSize = 12.sp)
                            } else {
                                discord?.takeIf { it.isNotBlank() }?.let {
                                    Text("Discord · $it", color = StarlitColors.TextMuted, fontSize = 12.sp)
                                }
                                telegram?.takeIf { it.isNotBlank() }?.let {
                                    Text("Telegram · $it", color = StarlitColors.TextMuted, fontSize = 12.sp)
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Предупреждения", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            WarnDots(player?.warnCount ?: 0)
                            if (player?.banned == true) {
                                Text(
                                    "Бан: ${player.banReason ?: "без причины"}",
                                    color = StarlitColors.Offline,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            } else {
                                Text("Бана нет", color = StarlitColors.TextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // —— main column ——
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                vm.userName,
                                color = StarlitColors.Text,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 24.sp,
                            )
                            if (activeBadge != null && (badges?.badgeVisible != false)) {
                                Text(
                                    "${activeBadge.emoji.orEmpty()} ${activeBadge.name.orEmpty()}".trim(),
                                    color = StarlitColors.Gold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!profileMissing) {
                                StarlitSecondaryButton(
                                    text = "Профиль",
                                    onClick = { vm.openPublicProfile() },
                                    modifier = Modifier.width(100.dp),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(StarlitDimens.Radius))
                                    .background(StarlitColors.Surface)
                                    .border(1.dp, StarlitColors.Purple.copy(alpha = 0.45f), RoundedCornerShape(StarlitDimens.Radius))
                                    .clickable { vm.logout() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, null, tint = StarlitColors.Text, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    if (profileMissing) {
                        Text(
                            "Профиль на сайте появится после первого захода на сервер.",
                            color = StarlitColors.TextMuted,
                            fontSize = 13.sp,
                        )
                    } else {
                        val ranks = player?.ranks.orEmpty()
                        if (ranks.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ranks.forEach { RankPill(it) }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Статус в профиле", color = StarlitColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                StarlitTextField(
                                    value = vm.statusDraft,
                                    onValueChange = { vm.statusDraft = it },
                                    label = "Например: Строю базу · ищу соседей",
                                    modifier = Modifier.weight(1f),
                                )
                                StarlitPrimaryButton(
                                    text = "Сохранить",
                                    onClick = { vm.saveProfileStatus() },
                                    loading = vm.isLoading,
                                    modifier = Modifier.width(120.dp),
                                )
                            }
                            Text(
                                "Текст под ником на публичной странице. Перенос: /n",
                                color = StarlitColors.TextDim,
                                fontSize = 11.sp,
                            )
                        }

                        if (ownedBadges.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Значок рядом с ником", color = StarlitColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BadgeChoice(
                                        label = "Без значка",
                                        selected = badgeDraft.isBlank(),
                                        onClick = { badgeDraft = "" },
                                    )
                                    ownedBadges.forEach { badge ->
                                        val id = badge.id.orEmpty()
                                        BadgeChoice(
                                            label = "${badge.emoji.orEmpty()} ${badge.name.orEmpty()}".trim(),
                                            selected = badgeDraft == id,
                                            onClick = { badgeDraft = id },
                                        )
                                    }
                                }
                                StarlitPrimaryButton(
                                    text = "Сохранить значок",
                                    onClick = { vm.setActiveBadge(badgeDraft.ifBlank { null }) },
                                    modifier = Modifier.width(180.dp),
                                )
                            }
                        }

                        if (vm.isAdmin) {
                            StarlitSecondaryButton(
                                text = "Админ-панель",
                                onClick = { vm.currentTab = LauncherTab.Admin },
                                modifier = Modifier.width(160.dp),
                            )
                        }
                    }
                }
            }

            // —— bottom accordions ——
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = Color(0x1F788CDC))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AccordionBlock(
                    title = "Уведомления",
                    hint = "Куда ещё присылать уведомления",
                    expanded = notifyOpen,
                    onToggle = { notifyOpen = !notifyOpen },
                ) {
                    Text(
                        "Уведомления в колокольчике на сайте включены всегда.",
                        color = StarlitColors.TextDim,
                        fontSize = 12.sp,
                    )
                    notifyChannels.forEach { ch ->
                        val id = ch.id ?: return@forEach
                        val enabled = ch.enabled && (cabinet?.notificationPrefs?.get(id) != false)
                        ToggleRow(
                            label = ch.label ?: id,
                            hint = if (ch.available) ch.hint else ch.disabledReason,
                            checked = enabled && ch.available,
                            enabled = ch.available && !profileMissing,
                            onToggle = { vm.setNotifyPref(id, !enabled) },
                        )
                    }
                }

                AccordionBlock(
                    title = "Приватность профиля",
                    hint = "Что видят другие на публичной странице",
                    expanded = privacyOpen,
                    onToggle = { privacyOpen = !privacyOpen },
                ) {
                    ToggleRow(
                        label = "Разрешить комментарии",
                        hint = "Только залогиненные игроки смогут писать",
                        checked = cabinet?.commentsEnabled != false && player?.commentsEnabled != false,
                        enabled = !profileMissing,
                        onToggle = {
                            val next = !(cabinet?.commentsEnabled != false && player?.commentsEnabled != false)
                            vm.setCommentsEnabled(next)
                        },
                    )
                    sections.forEach { section ->
                        val id = section.id ?: return@forEach
                        val visible = section.visible && !section.hidden
                        ToggleRow(
                            label = section.label ?: id,
                            hint = section.hint,
                            checked = visible,
                            enabled = !profileMissing,
                            onToggle = { vm.setPrivacy(id, !visible) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RankPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(StarlitColors.PurpleMuted)
            .border(1.dp, StarlitColors.Purple.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = StarlitColors.Text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BadgeChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) StarlitColors.GoldMuted else StarlitColors.Surface)
            .border(
                1.dp,
                if (selected) StarlitColors.Gold else StarlitColors.BorderStrong,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) StarlitColors.Gold else StarlitColors.TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun WarnDots(count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(5) { i ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (i < count) StarlitColors.Offline else StarlitColors.BorderStrong),
            )
        }
        Text("$count / 5", color = StarlitColors.TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun AccordionBlock(
    title: String,
    hint: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x22101828))
            .border(1.dp, Color(0x28788CDC), RoundedCornerShape(14.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(hint, color = StarlitColors.TextDim, fontSize = 12.sp)
            }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                tint = StarlitColors.TextMuted,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    hint: String?,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = StarlitColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (!hint.isNullOrBlank()) {
                Text(hint, color = StarlitColors.TextDim, fontSize = 11.sp)
            }
        }
        StarlitToggle(checked = checked && enabled, onCheckedChange = { if (enabled) onToggle() }, enabled = enabled)
    }
}
