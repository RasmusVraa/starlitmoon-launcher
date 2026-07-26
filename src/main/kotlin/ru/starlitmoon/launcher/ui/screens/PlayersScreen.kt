package ru.starlitmoon.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.starlitmoon.launcher.api.ProfileCommentDto
import ru.starlitmoon.launcher.api.ProfileViewerDto
import ru.starlitmoon.launcher.api.PublicPlayerDto
import ru.starlitmoon.launcher.api.PublicProfilePlayerDto
import ru.starlitmoon.launcher.ui.components.NetworkAvatar
import ru.starlitmoon.launcher.ui.components.SkinPreview3D
import ru.starlitmoon.launcher.ui.components.StarlitPrimaryButton
import ru.starlitmoon.launcher.ui.components.StarlitSecondaryButton
import ru.starlitmoon.launcher.ui.components.StarlitTextField
import ru.starlitmoon.launcher.ui.theme.PlayerRanks
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.ui.theme.StarlitDimens
import ru.starlitmoon.launcher.util.ImageDiskCache
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PlayersScreen(vm: LauncherViewModel) {
    if (vm.selectedPublicPlayer != null) {
        PublicProfileDetail(vm)
    } else {
        PublicPlayersList(vm)
    }
}

@Composable
private fun PublicPlayersList(vm: LauncherViewModel) {
    LaunchedEffect(Unit) {
        if (vm.publicPlayers.isEmpty() && !vm.publicPlayersLoading) {
            vm.refreshPublicPlayers()
        }
    }

    val query = vm.publicPlayersQuery.trim()
    val filtered = remember(vm.publicPlayers, query) {
        if (query.isEmpty()) vm.publicPlayers
        else vm.publicPlayers.filter { it.name.orEmpty().contains(query, ignoreCase = true) }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Игроки", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = StarlitColors.Text)
                val meta = buildString {
                    if (vm.publicPlayersTotal > 0) {
                        append("Онлайн ${vm.publicPlayersOnlineCount} · всего ${vm.publicPlayersTotal}")
                    }
                    vm.publicPlayersUpdatedAt?.let { raw ->
                        formatShortDate(raw)?.let {
                            if (isNotEmpty()) append(" · ")
                            append("обновлено $it")
                        }
                    }
                }
                if (meta.isNotEmpty()) {
                    Text(meta, color = StarlitColors.TextMuted, fontSize = 13.sp)
                }
            }
            StarlitSecondaryButton(
                text = "Обновить",
                onClick = { vm.refreshPublicPlayers(force = true) },
                compact = true,
                enabled = !vm.publicPlayersLoading,
                modifier = Modifier.width(120.dp),
            )
        }

        StarlitTextField(
            value = vm.publicPlayersQuery,
            onValueChange = { vm.publicPlayersQuery = it },
            label = "Поиск по нику",
            modifier = Modifier.fillMaxWidth(),
        )

        if (vm.publicPlayersDemo) {
            StatusBanner("Демо-режим: тестовые игроки без подключения к Minecraft-серверу.", warn = true)
        }
        vm.publicPlayersError?.let { StatusBanner(it, warn = false) }

        when {
            vm.publicPlayersLoading && vm.publicPlayers.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = StarlitColors.Gold, strokeWidth = 2.dp)
                }
            }
            filtered.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isNotEmpty()) "Никого не найдено"
                        else "Пока никого нет. Зайди на сервер — профили появятся здесь.",
                        color = StarlitColors.TextMuted,
                        fontSize = 14.sp,
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filtered, key = { it.uuid ?: it.name.orEmpty() }) { player ->
                        PlayerCard(vm, player)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerCard(vm: LauncherViewModel, player: PublicPlayerDto) {
    val name = player.name.orEmpty()
    val avatar = vm.playerAvatarUrl(name, player.uuid, player.skinTextureHash, player.skinUrl, 72)
    val ranks = PlayerRanks.normalize(player.ranks)
    val online = player.online && !player.banned

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StarlitDimens.Radius))
            .background(StarlitColors.Surface)
            .border(
                1.dp,
                when {
                    player.banned -> StarlitColors.Offline.copy(alpha = 0.55f)
                    online -> StarlitColors.Online.copy(alpha = 0.45f)
                    else -> StarlitColors.Border
                },
                RoundedCornerShape(StarlitDimens.Radius),
            )
            .clickable { vm.openPublicPlayer(name) }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            NetworkAvatar(url = avatar, fallbackName = name, size = 68.dp)
            if (online) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(StarlitColors.Online)
                        .border(2.dp, StarlitColors.Surface, CircleShape),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                name.ifBlank { "-" },
                color = StarlitColors.Text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (player.badgeVisible != false && player.activeBadge != null) {
                Text(
                    player.activeBadge.emoji.orEmpty(),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        if (ranks.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ranks.take(2).forEach { RankPill(it.labelRu, it, compact = true) }
            }
        }
    }
}

@Composable
private fun PublicProfileDetail(vm: LauncherViewModel) {
    val player = vm.publicProfile
    val viewer = vm.publicProfileViewer

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(StarlitDimens.RadiusSm))
                    .background(StarlitColors.Surface)
                    .border(1.dp, StarlitColors.Border, RoundedCornerShape(StarlitDimens.RadiusSm))
                    .clickable { vm.closePublicPlayer() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = StarlitColors.Text, modifier = Modifier.size(18.dp))
            }
            Text("Профиль", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = StarlitColors.Text)
            Spacer(Modifier.weight(1f))
            StarlitSecondaryButton(
                text = "На сайте",
                onClick = {
                    val n = player?.name ?: vm.selectedPublicPlayer.orEmpty()
                    if (n.isNotBlank()) {
                        vm.openSitePath("/player?player=${java.net.URLEncoder.encode(n, Charsets.UTF_8)}")
                    }
                },
                compact = true,
                modifier = Modifier.width(110.dp),
            )
        }

        when {
            vm.publicProfileLoading && player == null -> {
                Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = StarlitColors.Gold, strokeWidth = 2.dp)
                }
            }
            vm.publicProfileError != null && player == null -> {
                StatusBanner(vm.publicProfileError!!, warn = false)
            }
            player != null -> {
                ProfileHero(vm, player)
                if (player.banned) {
                    StatusBanner("Бан: ${player.banReason?.takeIf { it.isNotBlank() } ?: "без причины"}", warn = false)
                }
                ProfileStatsSection(player)
                ProfileCommentsSection(vm, viewer)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileHero(vm: LauncherViewModel, player: PublicProfilePlayerDto) {
    val name = player.name.orEmpty()
    val skinUrl = vm.playerSkinProxyUrl(name, player.uuid, player.skinTextureHash, player.skinUrl)
    val capeUrl = if (!player.capeUrl.isNullOrBlank() || !player.capeTexture.isNullOrBlank()) {
        vm.playerCapeProxyUrl(name, player.uuid, player.capeUrl, player.capeTexture)
    } else {
        null
    }
    var skinPath by remember(skinUrl) { mutableStateOf<Path?>(null) }
    var capePath by remember(capeUrl) { mutableStateOf<Path?>(null) }
    LaunchedEffect(skinUrl, capeUrl) {
        skinPath = withContext(Dispatchers.IO) { ImageDiskCache.cachedPath(skinUrl) }
        capePath = capeUrl?.let { withContext(Dispatchers.IO) { ImageDiskCache.cachedPath(it) } }
    }

    val ranks = PlayerRanks.normalize(player.ranks)
    val hidden = player.privacy?.hidden.orEmpty().map { it.lowercase() }.toSet()
    val showStatus = "status" !in hidden && !player.profileStatus.isNullOrBlank()
    val showDiscord = "discord" !in hidden && !player.discord?.username.isNullOrBlank()
    val showTelegram = "telegram" !in hidden && !player.telegram?.username.isNullOrBlank()
    val online = player.online && !player.banned

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StarlitColors.Surface)
            .border(
                1.dp,
                when {
                    player.banned -> StarlitColors.Offline.copy(alpha = 0.5f)
                    online -> StarlitColors.Online.copy(alpha = 0.4f)
                    else -> StarlitColors.Border
                },
                RoundedCornerShape(16.dp),
            )
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (skinPath != null) {
                SkinPreview3D(skinPath = skinPath, capePath = capePath, previewSize = 200.dp, username = name)
            } else {
                NetworkAvatar(
                    url = vm.playerAvatarUrl(name, player.uuid, player.skinTextureHash, player.skinUrl, 128),
                    fallbackName = name,
                    size = 128.dp,
                )
            }
            Text(
                if (online) "Онлайн" else "Оффлайн",
                color = if (online) StarlitColors.Online else StarlitColors.TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, color = StarlitColors.Text, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                if (player.badgeVisible != false && player.activeBadge != null) {
                    Text(
                        "${player.activeBadge.emoji.orEmpty()} ${player.activeBadge.name.orEmpty()}".trim(),
                        color = StarlitColors.Gold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (ranks.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ranks.forEach { RankPill(it.labelRu, it) }
                }
            }
            if (showStatus) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Статус", color = StarlitColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(player.profileStatus!!.replace("/n", "\n"), color = StarlitColors.Text, fontSize = 14.sp)
                }
            }
            if (showDiscord || showTelegram) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Контакты", color = StarlitColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (showDiscord) {
                        Text("Discord · ${player.discord?.username}", color = StarlitColors.TextMuted, fontSize = 13.sp)
                    }
                    if (showTelegram) {
                        Text("Telegram · ${player.telegram?.username}", color = StarlitColors.TextMuted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileStatsSection(player: PublicProfilePlayerDto) {
    val hidden = player.privacy?.hidden.orEmpty().map { it.lowercase() }.toSet()
    if ("stats" in hidden) return
    val s = player.stats
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StarlitColors.Surface)
            .border(1.dp, StarlitColors.Border, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Статистика", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        if (s == null) {
            Text("Пока нет данных статистики.", color = StarlitColors.TextMuted, fontSize = 13.sp)
        } else {
            val cards = buildList {
                add("Время в игре" to formatPlaytime(s.playtimeMinutes))
                add("Смерти" to formatNumber(s.deaths))
                add("Убийства мобов" to formatNumber(s.mobKills))
                add("Убийства игроков" to formatNumber(s.playerKills))
                add("Добыто блоков" to formatNumber(s.blocksMined))
                s.distanceKm?.let { add("Пройдено" to "${formatNumber(it.toLong())} км") }
                s.jumps?.let { add("Прыжки" to formatNumber(it)) }
                s.damageDealt?.let { add("Урон нанесён" to formatNumber(it.toLong())) }
                s.fishCaught?.let { add("Выловлено рыбы" to formatNumber(it)) }
                if ((s.blocksPlaced ?: 0) > 0) add("Поставлено блоков" to formatNumber(s.blocksPlaced))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                cards.forEach { (label, value) ->
                    Column(
                        modifier = Modifier
                            .width(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(StarlitColors.SurfaceElevated)
                            .border(1.dp, StarlitColors.Border, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(label, color = StarlitColors.TextMuted, fontSize = 11.sp)
                        Text(value, color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCommentsSection(vm: LauncherViewModel, viewer: ProfileViewerDto?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StarlitColors.Surface)
            .border(1.dp, StarlitColors.Border, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Комментарии", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

        if (!vm.publicCommentsEnabled) {
            Text("Комментарии отключены владельцем профиля.", color = StarlitColors.TextMuted, fontSize = 13.sp)
        } else {
            val canWrite = viewer?.canWriteComments == true
            when {
                viewer?.loggedIn != true -> {
                    Text("Войдите, чтобы оставить комментарий.", color = StarlitColors.TextMuted, fontSize = 13.sp)
                }
                !canWrite -> {
                    Text("Вам запрещено оставлять комментарии.", color = StarlitColors.TextMuted, fontSize = 13.sp)
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StarlitTextField(
                            value = vm.commentDraft,
                            onValueChange = { if (it.length <= 600) vm.commentDraft = it },
                            label = "Напиши комментарий...",
                            modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                            singleLine = false,
                            minLines = 3,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${vm.commentDraft.length}/600", color = StarlitColors.TextDim, fontSize = 11.sp)
                            StarlitPrimaryButton(
                                text = "Отправить",
                                onClick = { vm.postPublicComment() },
                                loading = vm.commentBusy,
                                enabled = vm.commentDraft.isNotBlank() && !vm.commentBusy,
                                compact = true,
                                modifier = Modifier.width(130.dp),
                            )
                        }
                    }
                }
            }

            when {
                vm.publicCommentsLoading && vm.publicComments.isEmpty() -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = StarlitColors.Gold,
                            strokeWidth = 2.dp,
                        )
                    }
                }
                vm.publicComments.isEmpty() -> {
                    Text("Пока нет комментариев", color = StarlitColors.TextMuted, fontSize = 13.sp)
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        vm.publicComments.forEach { CommentCard(vm, it, viewer) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentCard(vm: LauncherViewModel, comment: ProfileCommentDto, viewer: ProfileViewerDto?) {
    val author = comment.authorName.orEmpty()
    val canDelete = viewer?.canManageComments == true ||
        (viewer?.name != null && viewer.name.equals(author, ignoreCase = true))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StarlitColors.SurfaceElevated)
            .border(1.dp, StarlitColors.Border, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NetworkAvatar(
            url = vm.playerAvatarUrl(author, size = 48),
            fallbackName = author,
            size = 40.dp,
            modifier = Modifier.clickable(enabled = author.isNotBlank()) { vm.openPublicPlayer(author) },
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    author.ifBlank { "Игрок" },
                    color = StarlitColors.Gold,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(enabled = author.isNotBlank()) { vm.openPublicPlayer(author) },
                )
                if (canDelete && !comment.id.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !vm.commentBusy) { vm.deletePublicComment(comment.id!!) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Удалить",
                            tint = StarlitColors.TextMuted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Text(comment.text.orEmpty(), color = StarlitColors.Text, fontSize = 13.sp)
            formatShortDate(comment.createdAt)?.let {
                Text(it, color = StarlitColors.TextDim, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun RankPill(label: String, style: PlayerRanks.Style, compact: Boolean = false) {
    val h = if (compact) 18.dp else 22.dp
    val padH = if (compact) 8.dp else 10.dp
    val fs = if (compact) 10.sp else 11.sp
    Box(
        modifier = Modifier
            .height(h)
            .clip(RoundedCornerShape(999.dp))
            .background(style.background)
            .border(1.dp, style.border, RoundedCornerShape(999.dp))
            .padding(horizontal = padH),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = style.foreground, fontSize = fs, lineHeight = fs, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusBanner(text: String, warn: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (warn) StarlitColors.GoldMuted else StarlitColors.Offline.copy(alpha = 0.12f))
            .border(
                1.dp,
                if (warn) StarlitColors.Gold.copy(alpha = 0.35f) else StarlitColors.Offline.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, color = if (warn) StarlitColors.Gold else StarlitColors.Offline, fontSize = 13.sp)
    }
}

private fun formatPlaytime(minutes: Long?): String {
    val m = (minutes ?: 0L).coerceAtLeast(0L)
    if (m < 60) return "$m мин"
    val h = m / 60
    val rest = m % 60
    if (h < 24) return if (rest > 0) "$h ч $rest мин" else "$h ч"
    val d = h / 24
    val rh = h % 24
    return if (rh > 0) "$d д $rh ч" else "$d д"
}

private fun formatNumber(n: Number?): String {
    val v = n?.toLong() ?: 0L
    return String.format(Locale("ru", "RU"), "%,d", v)
}

private fun formatShortDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val instant = Instant.parse(raw)
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale("ru", "RU"))
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }.getOrNull()
}
