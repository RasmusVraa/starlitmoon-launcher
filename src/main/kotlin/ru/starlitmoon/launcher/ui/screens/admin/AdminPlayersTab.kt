package ru.starlitmoon.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ru.starlitmoon.launcher.api.AdminPlayerDto
import ru.starlitmoon.launcher.ui.components.StarlitPrimaryButton
import ru.starlitmoon.launcher.ui.components.StarlitSecondaryButton
import ru.starlitmoon.launcher.ui.components.StarlitTextField
import ru.starlitmoon.launcher.ui.components.StatusDot
import ru.starlitmoon.launcher.ui.components.StarlitConfirmDialog
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel

private val KNOWN_RANKS = listOf(
    "admin" to "Админ",
    "mod" to "Модер",
    "shine" to "Shine",
    "sponsor" to "Спонсор",
)

@Composable
fun AdminPlayersSection(vm: LauncherViewModel) {
    var sub by remember { mutableStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AdminSubTabsRow(listOf("Профили", "Аккаунты", "Уведомления"), sub) { sub = it }
        when (sub) {
            0 -> AdminProfilesTab(vm)
            1 -> AdminAccountsTab(vm)
            else -> AdminBroadcastTab(vm)
        }
    }
}

@Composable
private fun AdminProfilesTab(vm: LauncherViewModel) {
    var search by remember { mutableStateOf(vm.adminSearch) }
    var editing by remember { mutableStateOf<AdminPlayerDto?>(null) }

    AdminSectionCard("Профили на сайте", "Поиск, баны, варны, ранги и статус. Значки — во вкладке «Значки».") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StarlitTextField(search, { search = it }, "Поиск по нику…", modifier = Modifier.weight(1f))
            StarlitPrimaryButton(text = "Найти", onClick = { vm.searchAdminPlayers(search) }, modifier = Modifier.width(110.dp), compact = true)
        }
        if (vm.adminPlayers.isEmpty()) AdminEmpty("Ничего не найдено")
        vm.adminPlayers.take(100).forEach { p ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(p.online)
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.name ?: "—", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                    Text(
                        buildString {
                            if (p.banned) append("бан · ")
                            if (p.warnCount > 0) append("варн ${p.warnCount} · ")
                            append(p.ranks.joinToString(", ").ifBlank { "игрок" })
                        },
                        color = StarlitColors.TextMuted,
                        fontSize = 11.sp,
                    )
                }
                StarlitSecondaryButton(text = "Изменить", onClick = { editing = p }, compact = true, modifier = Modifier.width(120.dp))
            }
        }
    }

    editing?.let { player ->
        PlayerEditorDialog(vm, player) { editing = null }
    }
}

@Composable
private fun PlayerEditorDialog(vm: LauncherViewModel, player: AdminPlayerDto, onDismiss: () -> Unit) {
    val id = player.name.orEmpty()
    var banned by remember { mutableStateOf(player.banned) }
    var banReason by remember { mutableStateOf("") }
    var warn by remember { mutableStateOf(player.warnCount.toString()) }
    var status by remember { mutableStateOf("") }
    val ranks = remember { mutableStateListOf<String>().apply { addAll(player.ranks) } }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmPurge by remember { mutableStateOf(false) }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(StarlitColors.OverlayScrim),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 420.dp, max = 520.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF161C2B), Color(0xFF0E121C))))
                    .border(1.dp, StarlitColors.BorderStrong, RoundedCornerShape(18.dp))
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Профиль: $id", color = StarlitColors.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)

                AdminCheckbox(banned, "Заблокирован", { banned = it })
                StarlitTextField(banReason, { banReason = it }, "Причина бана (опц.)")
                StarlitTextField(warn, { warn = it.filter(Char::isDigit) }, "Кол-во варнов")
                StarlitTextField(status, { status = it }, "Статус профиля")

                Text("Ранги", color = StarlitColors.TextMuted, fontSize = 12.sp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KNOWN_RANKS.forEach { (rankId, label) ->
                        AdminCheckbox(
                            checked = ranks.contains(rankId),
                            label = label,
                            onToggle = { if (it) { if (!ranks.contains(rankId)) ranks.add(rankId) } else ranks.remove(rankId) },
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StarlitPrimaryButton(
                        text = "Сохранить",
                        onClick = {
                            vm.savePlayer(id, banned, banReason, warn.toIntOrNull() ?: 0, status, ranks.toList())
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        compact = true,
                    )
                    StarlitSecondaryButton(text = "Закрыть", onClick = onDismiss, modifier = Modifier.weight(1f), compact = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StarlitPrimaryButton(text = "Удалить профиль", onClick = { confirmDelete = true }, modifier = Modifier.weight(1f), danger = true, compact = true)
                    StarlitPrimaryButton(text = "Стереть данные", onClick = { confirmPurge = true }, modifier = Modifier.weight(1f), danger = true, compact = true)
                }
            }
        }
    }

    if (confirmDelete) {
        StarlitConfirmDialog(
            title = "Удалить профиль?",
            message = "Профиль игрока $id будет удалён с сайта.",
            danger = true,
            onConfirm = { vm.deletePlayer(id, purge = false); onDismiss() },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmPurge) {
        StarlitConfirmDialog(
            title = "Стереть все данные?",
            message = "Полное удаление данных игрока $id (банк, кланы, метки и т.д.). Действие необратимо.",
            danger = true,
            onConfirm = { vm.deletePlayer(id, purge = true); onDismiss() },
            onDismiss = { confirmPurge = false },
        )
    }
}

@Composable
private fun AdminAccountsTab(vm: LauncherViewModel) {
    var search by remember { mutableStateOf(vm.accountSearch) }
    AdminSectionCard("Аккаунты (mcAuth)", "Сброс пароля и удаление аккаунтов.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StarlitTextField(search, { search = it }, "Поиск", modifier = Modifier.weight(1f))
            StarlitPrimaryButton(text = "Найти", onClick = { vm.searchAdminAccounts(search) }, modifier = Modifier.width(110.dp), compact = true)
        }
        if (!vm.lastResetPassword.isNullOrBlank()) {
            Text("Последний пароль: ${vm.lastResetPassword}", color = StarlitColors.Gold, fontSize = 13.sp)
        }
        if (vm.adminAccounts.isEmpty()) AdminEmpty("Ничего не найдено")
        vm.adminAccounts.take(80).forEach { acc ->
            val nick = acc.name
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(nick ?: "—", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                    Text(acc.uuid ?: "", color = StarlitColors.TextDim, fontSize = 11.sp)
                }
                if (!nick.isNullOrBlank()) {
                    StarlitSecondaryButton(text = "Сброс", onClick = { vm.resetAccountPassword(nick) }, compact = true, modifier = Modifier.width(96.dp))
                    StarlitPrimaryButton(text = "Удалить", onClick = { vm.deleteAccount(nick) }, compact = true, danger = true, modifier = Modifier.width(100.dp))
                }
            }
        }
    }
}

@Composable
private fun AdminBroadcastTab(vm: LauncherViewModel) {
    AdminSectionCard("Уведомления игрокам", "Отправка в колокольчик на сайте и в чат на сервере (StarlitWebSync).") {
        StarlitTextField(vm.notifyTitle, { vm.notifyTitle = it }, "Заголовок")
        StarlitTextField(vm.notifyMessage, { vm.notifyMessage = it }, "Текст")
        StarlitTextField(vm.notifyHref, { vm.notifyHref = it }, "Ссылка (опц.)")
        StarlitTextField(vm.notifyTargets, { vm.notifyTargets = it }, "Ники через запятую (для адресной рассылки)")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StarlitPrimaryButton(text = "Отправить всем", onClick = { vm.sendBroadcast(toAll = true) }, modifier = Modifier.width(170.dp), compact = true)
            StarlitSecondaryButton(text = "Выбранным", onClick = { vm.sendBroadcast(toAll = false) }, modifier = Modifier.width(150.dp), compact = true)
        }
    }
}
