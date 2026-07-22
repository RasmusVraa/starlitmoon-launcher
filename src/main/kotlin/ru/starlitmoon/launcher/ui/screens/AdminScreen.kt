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
import androidx.compose.ui.text.font.FontFamily
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

private val adminTabTitles = listOf(
    "Сводка",
    "Игроки",
    "Заявки",
    "Кланы",
    "Банк",
    "Рассылка",
    "Аккаунты",
    "Консоль",
    "Казна",
    "Значки",
    "Товары",
)

@Composable
fun AdminScreen(vm: LauncherViewModel) {
    if (!vm.isLoggedIn) {
        LoginScreen(vm)
        return
    }
    LaunchedEffect(vm.adminSubTab) { vm.refreshAdmin() }
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
    val accountSearch = remember { mutableStateOf(vm.accountSearch) }
    val bankNick = remember { mutableStateOf("") }
    val bankBal = remember { mutableStateOf("0") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("Админ-панель", "Управление")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StarlitSecondaryButton(
                text = "Полная админка на сайте",
                onClick = { vm.openAdminWebsite() },
            )
            Spacer(Modifier.weight(1f))
            StarlitSecondaryButton(text = "Обновить", onClick = { vm.refreshAdmin() })
        }
        AdminTabRow(vm, adminTabTitles.take(6), startIndex = 0)
        AdminTabRow(vm, adminTabTitles.drop(6), startIndex = 6)

        when (vm.adminSubTab) {
            0 -> AdminStatsTab(vm)
            1 -> AdminPlayersTab(vm, search.value) { search.value = it }
            2 -> AdminAppsTab(vm)
            3 -> AdminClansTab(vm)
            4 -> AdminBankTab(vm, bankNick.value, bankBal.value, { bankNick.value = it }, { bankBal.value = it })
            5 -> AdminBroadcastTab(vm)
            6 -> AdminAccountsTab(vm, accountSearch.value) { accountSearch.value = it }
            7 -> AdminConsoleTab(vm)
            8 -> AdminTreasuryTab(vm)
            9 -> AdminBadgesTab(vm)
            10 -> AdminProductsTab(vm)
        }
    }
}

@Composable
private fun AdminTabRow(vm: LauncherViewModel, titles: List<String>, startIndex: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        titles.forEachIndexed { offset, title ->
            val index = startIndex + offset
            if (index == vm.adminSubTab) {
                StarlitPrimaryButton(text = title, onClick = { vm.adminSubTab = index })
            } else {
                StarlitSecondaryButton(text = title, onClick = { vm.adminSubTab = index })
            }
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
                            StarlitSecondaryButton(text = "Удалить", onClick = { vm.deleteApp(id) })
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
                            StarlitSecondaryButton(text = "Удалить", onClick = { vm.deleteClan(id) })
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        StatRow(card.ownerName ?: "—", (card.balance ?: 0).toString())
                        if (!card.cardCode.isNullOrBlank()) {
                            Text(card.cardCode, color = StarlitColors.TextMuted, fontSize = 11.sp)
                        }
                    }
                    val owner = card.ownerName
                    if (!owner.isNullOrBlank()) {
                        StarlitSecondaryButton(text = "Удалить", onClick = { vm.deleteBankCard(owner) })
                    }
                }
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

@Composable
private fun AdminAccountsTab(
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
                StarlitPrimaryButton(text = "Найти", onClick = { vm.searchAdminAccounts(search) })
            }
            vm.lastResetPassword?.let { pwd ->
                Text("Последний сброс: $pwd", color = StarlitColors.Gold, fontSize = 12.sp)
            }
            if (vm.adminAccounts.isEmpty()) {
                Text("Нет аккаунтов", color = StarlitColors.TextMuted)
            } else {
                vm.adminAccounts.take(80).forEach { acc ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(acc.name ?: "—", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                            Text(acc.uuid ?: "", color = StarlitColors.TextMuted, fontSize = 11.sp)
                        }
                        val nick = acc.name
                        if (!nick.isNullOrBlank()) {
                            StarlitSecondaryButton(text = "Сброс пароля", onClick = { vm.resetAccountPassword(nick) })
                            StarlitSecondaryButton(text = "Удалить", onClick = { vm.deleteAccount(nick) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminConsoleTab(vm: LauncherViewModel) {
    StarlitCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StarlitTextField(
                vm.adminConsoleServerId,
                { vm.adminConsoleServerId = it },
                "ID сервера (serverId)",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StarlitPrimaryButton(text = "Загрузить вывод", onClick = { vm.loadConsoleOutput() })
            }
            val output = when {
                vm.adminConsoleError != null -> "⚠ ${vm.adminConsoleError}"
                vm.adminConsoleOutput.isNotBlank() -> vm.adminConsoleOutput
                else -> "Нет вывода. Укажи serverId и нажми «Загрузить вывод»."
            }
            Text(
                output,
                color = StarlitColors.TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text("RCON", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StarlitTextField(
                    vm.rconCommand,
                    { vm.rconCommand = it },
                    "Команда",
                    modifier = Modifier.weight(1f),
                )
                StarlitPrimaryButton(text = "Выполнить", onClick = { vm.execRcon() })
            }
            if (vm.adminRconResponse.isNotBlank()) {
                Text(
                    vm.adminRconResponse,
                    color = StarlitColors.Gold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun AdminTreasuryTab(vm: LauncherViewModel) {
    val t = vm.adminTreasury
    StarlitCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatRow("Баланс казны", (t?.treasury?.balance ?: 0).toString())
            StatRow("Код", t?.treasury?.cardCode ?: "—")
            StatRow("Штрафы (ожидают)", (t?.penaltiesPending ?: 0).toString())
            StatRow("Штрафы (оплачено)", (t?.penaltiesPaidTotal ?: 0).toString())
            Text("Выплата", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            StarlitTextField(vm.treasuryPayoutCode, { vm.treasuryPayoutCode = it }, "Код карты SM-XXXX-XXXX")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StarlitTextField(
                    vm.treasuryPayoutAmount,
                    { vm.treasuryPayoutAmount = it },
                    "Сумма",
                    modifier = Modifier.width(120.dp),
                )
                StarlitTextField(
                    vm.treasuryPayoutReason,
                    { vm.treasuryPayoutReason = it },
                    "Причина (id)",
                    modifier = Modifier.weight(1f),
                )
            }
            StarlitTextField(vm.treasuryPayoutNote, { vm.treasuryPayoutNote = it }, "Комментарий")
            val reasons = t?.payoutReasons.orEmpty()
            if (reasons.isNotEmpty()) {
                Text(
                    reasons.joinToString(" · ") { "${it.id}: ${it.label}" },
                    color = StarlitColors.TextMuted,
                    fontSize = 11.sp,
                )
            }
            StarlitPrimaryButton(text = "Выплатить", onClick = { vm.treasuryPayout() })
        }
    }
}

@Composable
private fun AdminBadgesTab(vm: LauncherViewModel) {
    StarlitCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (vm.adminBadges.isEmpty()) {
                Text("Нет значков", color = StarlitColors.TextMuted)
            } else {
                vm.adminBadges.forEach { badge ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(badge.emoji ?: "🏷", fontSize = 18.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(badge.name ?: "—", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${badge.id.orEmpty()} · ${badge.description.orEmpty()}",
                                color = StarlitColors.TextMuted,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminProductsTab(vm: LauncherViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Товары", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                if (vm.adminProducts.isEmpty()) {
                    Text("Нет товаров", color = StarlitColors.TextMuted)
                } else {
                    vm.adminProducts.forEach { product ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(product.icon ?: "🎁", fontSize = 18.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${product.name.orEmpty()} — ${product.price ?: 0} ◆",
                                    color = StarlitColors.Text,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    product.id.orEmpty(),
                                    color = StarlitColors.TextMuted,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Заказы", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                if (vm.adminOrders.isEmpty()) {
                    Text("Нет заказов", color = StarlitColors.TextMuted)
                } else {
                    vm.adminOrders.take(60).forEach { order ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${order.nickname.orEmpty()} — ${order.productName.orEmpty()}",
                                    color = StarlitColors.Text,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    buildString {
                                        append(order.status.orEmpty())
                                        append(" · ")
                                        append(order.price ?: 0)
                                        append(" ◆")
                                        if (order.delivered) append(" · доставлен")
                                    },
                                    color = StarlitColors.TextMuted,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
