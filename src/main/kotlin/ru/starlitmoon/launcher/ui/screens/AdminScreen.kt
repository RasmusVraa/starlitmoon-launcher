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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.starlitmoon.launcher.ui.components.SectionTitle
import ru.starlitmoon.launcher.ui.components.StarlitCard
import ru.starlitmoon.launcher.ui.components.StarlitPrimaryButton
import ru.starlitmoon.launcher.ui.components.StarlitSecondaryButton
import ru.starlitmoon.launcher.ui.components.StarlitTextField
import ru.starlitmoon.launcher.ui.components.StatRow
import ru.starlitmoon.launcher.ui.components.StatusDot
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.viewmodel.LauncherTab
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel

@Composable
fun AdminScreen(vm: LauncherViewModel) {
    if (!vm.isLoggedIn) {
        LoginScreen(vm)
        return
    }
    LaunchedEffect(Unit) { vm.refreshAdmin() }
    if (!vm.isAdmin) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            SectionTitle("Админ-панель", "Нет доступа")
            StarlitSecondaryButton(
                text = "В кабинет",
                onClick = { vm.currentTab = LauncherTab.Cabinet },
            )
        }
        return
    }

    val search = remember { mutableStateOf(vm.adminSearch) }
    val bankNick = remember { mutableStateOf("") }
    val bankBal = remember { mutableStateOf("0") }
    val tabs = listOf("Сводка", "Игроки", "Заявки", "Кланы", "Банк", "Рассылка")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("Админ-панель", "Управление")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEachIndexed { index, title ->
                if (index == vm.adminSubTab) {
                    StarlitPrimaryButton(text = title, onClick = { vm.adminSubTab = index })
                } else {
                    StarlitSecondaryButton(text = title, onClick = { vm.adminSubTab = index })
                }
            }
            Spacer(Modifier.weight(1f))
            StarlitSecondaryButton(text = "Обновить", onClick = { vm.refreshAdmin() })
        }

        when (vm.adminSubTab) {
            0 -> AdminStatsTab(vm)
            1 -> AdminPlayersTab(vm, search.value) { search.value = it }
            2 -> AdminAppsTab(vm)
            3 -> AdminClansTab(vm)
            4 -> AdminBankTab(vm, bankNick.value, bankBal.value, { bankNick.value = it }, { bankBal.value = it })
            5 -> AdminBroadcastTab(vm)
        }
    }
}

@Composable
private fun AdminStatsTab(vm: LauncherViewModel) {
    val s = vm.adminStats
    StarlitCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatRow("Игроки", (s?.players ?: 0).toString())
            StatRow("Онлайн", (s?.online ?: 0).toString())
            StatRow("Баны", (s?.banned ?: 0).toString())
            StatRow("Аккаунты", (s?.accounts ?: 0).toString())
            StatRow("Заявки", (s?.pendingApplications ?: 0).toString())
            StatRow("Кланы", (s?.pendingClans ?: 0).toString())
            StatRow("Банк", (s?.totalBankBalance ?: 0).toString())
            StatRow("Казна", (s?.treasuryBalance ?: 0).toString())
            StatRow("Заказы", (s?.orders ?: 0).toString())
            val perms = vm.adminMe?.permissions.orEmpty()
            if (perms.isNotEmpty()) {
                Text(perms.joinToString(" · "), color = StarlitColors.TextMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AdminPlayersTab(
    vm: LauncherViewModel,
    search: String,
    onSearchChange: (String) -> Unit,
) {
    StarlitCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StarlitTextField(search, onSearchChange, "Поиск", modifier = Modifier.weight(1f))
                StarlitPrimaryButton(text = "Найти", onClick = { vm.searchAdminPlayers(search) })
            }
            vm.adminPlayers.take(80).forEach { p ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    val nick = p.name
                    if (!nick.isNullOrBlank()) {
                        if (p.banned) {
                            StarlitSecondaryButton(text = "Разбан", onClick = { vm.banPlayer(nick, false) })
                        } else {
                            StarlitSecondaryButton(text = "Бан", onClick = { vm.banPlayer(nick, true) })
                        }
                        StarlitSecondaryButton(text = "+варн", onClick = { vm.warnPlayer(nick, p.warnCount + 1) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminAppsTab(vm: LauncherViewModel) {
    StarlitCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (vm.adminApps.isEmpty()) {
                Text("Нет ожидающих заявок", color = StarlitColors.TextMuted)
            } else {
                vm.adminApps.forEach { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.minecraftNick ?: "—", color = StarlitColors.Text, fontWeight = FontWeight.Bold)
                            Text(app.discordNick ?: "", color = StarlitColors.TextMuted, fontSize = 12.sp)
                        }
                        val id = app.id
                        if (id != null) {
                            StarlitPrimaryButton(text = "Принять", onClick = { vm.acceptApp(id) })
                            StarlitSecondaryButton(text = "Отклонить", onClick = { vm.rejectApp(id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminClansTab(vm: LauncherViewModel) {
    StarlitCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (vm.adminClans.isEmpty()) {
                Text("Нет ожидающих кланов", color = StarlitColors.TextMuted)
            } else {
                vm.adminClans.forEach { clan ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "${clan.tag.orEmpty()} ${clan.name.orEmpty()}".trim(),
                            color = StarlitColors.Text,
                            modifier = Modifier.weight(1f),
                        )
                        val id = clan.id
                        if (id != null) {
                            StarlitPrimaryButton(text = "Одобрить", onClick = { vm.approveClan(id) })
                            StarlitSecondaryButton(text = "Отклонить", onClick = { vm.rejectClan(id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminBankTab(
    vm: LauncherViewModel,
    nick: String,
    bal: String,
    onNick: (String) -> Unit,
    onBal: (String) -> Unit,
) {
    StarlitCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StarlitTextField(nick, onNick, "Ник", modifier = Modifier.weight(1f))
                StarlitTextField(bal, onBal, "Баланс", modifier = Modifier.width(120.dp))
                StarlitPrimaryButton(
                    text = "Сохранить",
                    onClick = {
                        val amount = bal.toLongOrNull()
                        if (amount != null) vm.setBank(nick.trim(), amount)
                    },
                )
            }
            vm.adminBank.take(60).forEach { card ->
                StatRow(card.ownerName ?: "—", (card.balance ?: 0).toString())
            }
        }
    }
}

@Composable
private fun AdminBroadcastTab(vm: LauncherViewModel) {
    StarlitCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StarlitTextField(vm.notifyTitle, { vm.notifyTitle = it }, "Заголовок")
            StarlitTextField(vm.notifyMessage, { vm.notifyMessage = it }, "Сообщение")
            StarlitPrimaryButton(text = "Отправить всем", onClick = { vm.sendBroadcast() })
        }
    }
}
