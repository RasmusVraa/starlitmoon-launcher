package ru.starlitmoon.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import ru.starlitmoon.launcher.ui.theme.StarlitDimens
import ru.starlitmoon.launcher.viewmodel.LauncherTab
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel

/** Tabs in the same order as starlit-moon.ru/admin */
private val adminTabs = listOf(
    "Игроки",
    "Банк",
    "Значки",
    "Карта",
    "Кланы",
    "Заявки",
    "Конкурс",
    "Вики",
    "Сборки",
    "Настройки",
    "Доступ",
    "Консоль",
)

@Composable
fun AdminScreen(vm: LauncherViewModel) {
    if (!vm.isLoggedIn) {
        LoginScreen(vm)
        return
    }
    LaunchedEffect(vm.adminSubTab) { vm.refreshAdmin() }
    if (!vm.isAdmin) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Админ-панель", "Нет доступа")
            StarlitSecondaryButton(text = "В кабинет", onClick = { vm.currentTab = LauncherTab.Cabinet })
        }
        return
    }

    var search by remember { mutableStateOf(vm.adminSearch) }
    var accountSearch by remember { mutableStateOf(vm.accountSearch) }
    var bankNick by remember { mutableStateOf("") }
    var bankBal by remember { mutableStateOf("0") }
    var playersSub by remember { mutableStateOf(0) } // 0 profiles, 1 accounts, 2 notify
    var bankSub by remember { mutableStateOf(0) } // 0 cards, 1 treasury, 2 orders

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Админ-панель",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            style = androidx.compose.ui.text.TextStyle(
                brush = Brush.linearGradient(
                    listOf(StarlitColors.Text, StarlitColors.Gold, StarlitColors.Purple),
                ),
            ),
        )
        Text("Управление данными сайта и игроками", color = StarlitColors.TextMuted, fontSize = 14.sp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Вы вошли как ${vm.userName}",
                color = StarlitColors.TextMuted,
                fontSize = 13.sp,
            )
            StarlitSecondaryButton(text = "Обновить", onClick = { vm.refreshAdmin() }, modifier = Modifier.width(110.dp))
        }

        AdminStatsStrip(vm)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            adminTabs.forEachIndexed { index, title ->
                AdminTabChip(
                    title = title,
                    selected = vm.adminSubTab == index,
                    onClick = { vm.adminSubTab = index },
                )
            }
        }

        when (vm.adminSubTab) {
            0 -> {
                AdminSubTabs(
                    listOf("Профили", "Аккаунты", "Уведомления"),
                    playersSub,
                ) { playersSub = it }
                when (playersSub) {
                    0 -> AdminPlayersTab(vm, search) { search = it }
                    1 -> AdminAccountsTab(vm, accountSearch) { accountSearch = it }
                    else -> AdminBroadcastTab(vm)
                }
            }
            1 -> {
                AdminSubTabs(
                    listOf("Карты", "Казна", "Товары"),
                    bankSub,
                ) { bankSub = it }
                when (bankSub) {
                    0 -> AdminBankTab(vm, bankNick, bankBal, { bankNick = it }, { bankBal = it })
                    1 -> AdminTreasuryTab(vm)
                    else -> AdminProductsTab(vm)
                }
            }
            2 -> AdminBadgesTab(vm)
            3 -> AdminNativeSection(
                title = "Карта",
                lead = "Видимость карты и метки игроков.",
            ) {
                Text(
                    "На сайте карта берётся с squaremap.starlit-moon.ru. " +
                        "Метки и доступ настраиваются в этом же разделе админки сайта; " +
                        "здесь отображается сводка прав.",
                    color = StarlitColors.TextMuted,
                    fontSize = 13.sp,
                )
                val perms = vm.adminMe?.permissions.orEmpty()
                Text(
                    if (perms.contains("map")) "У вас есть право map" else "Нет права map",
                    color = StarlitColors.Gold,
                    fontSize = 12.sp,
                )
            }
            4 -> AdminClansTab(vm)
            5 -> AdminAppsTab(vm)
            6 -> AdminNativeSection("Конкурс", "Модерация конкурсных работ.") {
                Text("Список работ и настройки страницы конкурса.", color = StarlitColors.TextMuted, fontSize = 13.sp)
                Text(
                    if (vm.adminMe?.permissions?.contains("contest") == true) "Право contest есть"
                    else "Нет права contest",
                    color = StarlitColors.Gold,
                    fontSize = 12.sp,
                )
            }
            7 -> AdminNativeSection("Вики", "Редактор статей вики.") {
                Text("Статьи и черновики вики управляются в этом разделе.", color = StarlitColors.TextMuted, fontSize = 13.sp)
                Text(
                    if (vm.adminMe?.permissions?.contains("wiki") == true) "Право wiki есть"
                    else "Нет права wiki",
                    color = StarlitColors.Gold,
                    fontSize = 12.sp,
                )
            }
            8 -> AdminModpacksTab(vm)
            9 -> AdminNativeSection("Настройки", "Общие параметры сайта.") {
                StatRow("Версия сервера (сайт)", vm.serverVersion.ifBlank { "—" })
                StatRow("API", vm.configState.apiBaseUrl)
            }
            10 -> AdminNativeSection("Доступ", "Админы и права.") {
                Text("Вы: ${vm.adminMe?.user?.name ?: vm.userName}", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                val perms = vm.adminMe?.permissions.orEmpty()
                if (perms.isEmpty()) {
                    Text("Права не загружены", color = StarlitColors.TextMuted, fontSize = 13.sp)
                } else {
                    Text(perms.joinToString(" · "), color = StarlitColors.TextMuted, fontSize = 12.sp)
                }
            }
            11 -> AdminConsoleTab(vm)
        }
    }
}

@Composable
private fun AdminTabChip(title: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) StarlitColors.Gold else StarlitColors.Surface)
            .border(1.dp, if (selected) StarlitColors.Gold else Color(0x40788CDC), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            title,
            color = if (selected) StarlitColors.OnGold else StarlitColors.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AdminSubTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        titles.forEachIndexed { i, title ->
            if (i == selected) {
                StarlitPrimaryButton(text = title, onClick = { onSelect(i) }, modifier = Modifier.widthIn(min = 100.dp))
            } else {
                StarlitSecondaryButton(text = title, onClick = { onSelect(i) }, modifier = Modifier.widthIn(min = 100.dp))
            }
        }
    }
}

@Composable
private fun AdminStatsStrip(vm: LauncherViewModel) {
    val s = vm.adminStats
    LaunchedEffect(Unit) {
        if (s == null) {
            // ensure stats load even when not on a data tab
            vm.adminSubTab = vm.adminSubTab
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatChip("Игроки", (s?.players ?: 0).toString())
        StatChip("Онлайн", (s?.online ?: 0).toString())
        StatChip("Баны", (s?.banned ?: 0).toString())
        StatChip("Заявки", (s?.pendingApplications ?: 0).toString())
        StatChip("Кланы", (s?.pendingClans ?: 0).toString())
        StatChip("Банк", (s?.totalBankBalance ?: 0).toString())
        StatChip("Казна", (s?.treasuryBalance ?: 0).toString())
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(StarlitDimens.Radius))
            .background(Color(0xF5161C30))
            .border(1.dp, Color(0x33788CDC), RoundedCornerShape(StarlitDimens.Radius))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, color = StarlitColors.TextDim, fontSize = 11.sp)
        Text(value, color = StarlitColors.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun AdminNativeSection(
    title: String,
    lead: String,
    content: @Composable () -> Unit,
) {
    StarlitCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Text(lead, color = StarlitColors.TextMuted, fontSize = 13.sp)
            content()
        }
    }
}

@Composable
private fun AdminModpacksTab(vm: LauncherViewModel) {
    LaunchedEffect(Unit) { vm.fetchModpacks(force = false) }
    StarlitCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Сборки лаунчера", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Text(
                "Список клиентских сборок с /api/modpacks.",
                color = StarlitColors.TextMuted,
                fontSize = 13.sp,
            )
            if (vm.modpacks.isEmpty()) {
                Text("Сборок пока нет", color = StarlitColors.TextDim, fontSize = 13.sp)
            }
            vm.modpacks.forEach { pack ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pack.name ?: pack.slug ?: "—", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(pack.loader, pack.mcVersion?.let { "MC $it" }).joinToString(" · "),
                            color = StarlitColors.TextMuted,
                            fontSize = 12.sp,
                        )
                    }
                    if (pack.hasArchive) {
                        Text("архив", color = StarlitColors.Gold, fontSize = 11.sp)
                    }
                }
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
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Профили на сайте", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StarlitTextField(search, onSearchChange, "Поиск по нику…", modifier = Modifier.weight(1f))
                StarlitPrimaryButton(text = "Найти", onClick = { vm.searchAdminPlayers(search) }, modifier = Modifier.width(100.dp))
            }
            vm.adminPlayers.take(80).forEach { p ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
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
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Заявки", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
            if (vm.adminApps.isEmpty()) {
                Text("Нет pending-заявок", color = StarlitColors.TextMuted, fontSize = 13.sp)
            }
            vm.adminApps.forEach { app ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.minecraftNick ?: app.id ?: "—", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                        Text(app.status ?: "", color = StarlitColors.TextMuted, fontSize = 12.sp)
                    }
                    val id = app.id
                    if (!id.isNullOrBlank()) {
                        StarlitPrimaryButton(text = "Принять", onClick = { vm.acceptApp(id) })
                        StarlitSecondaryButton(text = "Отклонить", onClick = { vm.rejectApp(id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminClansTab(vm: LauncherViewModel) {
    StarlitCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Кланы", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
            if (vm.adminClans.isEmpty()) {
                Text("Нет pending-кланов", color = StarlitColors.TextMuted, fontSize = 13.sp)
            }
            vm.adminClans.forEach { clan ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(clan.name ?: clan.id ?: "—", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                        Text(listOfNotNull(clan.tag, clan.status).joinToString(" · "), color = StarlitColors.TextMuted, fontSize = 12.sp)
                    }
                    val id = clan.id
                    if (!id.isNullOrBlank()) {
                        StarlitPrimaryButton(text = "Одобрить", onClick = { vm.approveClan(id) })
                        StarlitSecondaryButton(text = "Отклонить", onClick = { vm.rejectClan(id) })
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
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Банковские карты", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StarlitTextField(nick, onNick, "Ник", modifier = Modifier.weight(1f))
                StarlitTextField(bal, onBal, "Баланс", modifier = Modifier.width(120.dp))
                StarlitPrimaryButton(
                    text = "Задать",
                    onClick = { vm.setBank(nick, bal.toLongOrNull() ?: 0L) },
                    modifier = Modifier.width(100.dp),
                )
            }
            vm.adminBank.take(60).forEach { card ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(card.ownerName ?: "—", color = StarlitColors.Text, modifier = Modifier.weight(1f))
                    Text((card.balance ?: 0).toString(), color = StarlitColors.Gold, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AdminBroadcastTab(vm: LauncherViewModel) {
    StarlitCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Уведомления игрокам", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
            Text(
                "Сообщение появится в колокольчике на сайте. В Minecraft — через StarlitWebSync.",
                color = StarlitColors.TextMuted,
                fontSize = 12.sp,
            )
            StarlitTextField(vm.notifyTitle, { vm.notifyTitle = it }, "Заголовок")
            StarlitTextField(vm.notifyMessage, { vm.notifyMessage = it }, "Текст")
            StarlitPrimaryButton(text = "Отправить всем", onClick = { vm.sendBroadcast() }, modifier = Modifier.width(180.dp))
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
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Аккаунты (mcAuth)", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StarlitTextField(search, onSearchChange, "Поиск", modifier = Modifier.weight(1f))
                StarlitPrimaryButton(text = "Найти", onClick = { vm.searchAdminAccounts(search) }, modifier = Modifier.width(100.dp))
            }
            if (!vm.lastResetPassword.isNullOrBlank()) {
                Text("Последний пароль: ${vm.lastResetPassword}", color = StarlitColors.Gold, fontSize = 13.sp)
            }
            vm.adminAccounts.take(60).forEach { acc ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(acc.name ?: "—", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                        Text(acc.uuid ?: "", color = StarlitColors.TextDim, fontSize = 11.sp)
                    }
                    val nick = acc.name
                    if (!nick.isNullOrBlank()) {
                        StarlitSecondaryButton(text = "Сброс", onClick = { vm.resetAccountPassword(nick) })
                        StarlitSecondaryButton(text = "Удалить", onClick = { vm.deleteAccount(nick) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminConsoleTab(vm: LauncherViewModel) {
    StarlitCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Консоль", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
            StarlitTextField(vm.adminConsoleServerId, { vm.adminConsoleServerId = it }, "serverId (опц.)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StarlitSecondaryButton(text = "Лог", onClick = { vm.loadConsoleOutput() }, modifier = Modifier.width(90.dp))
                StarlitTextField(vm.rconCommand, { vm.rconCommand = it }, "RCON команда", modifier = Modifier.weight(1f))
                StarlitPrimaryButton(text = "Exec", onClick = { vm.execRcon() }, modifier = Modifier.width(90.dp))
            }
            if (vm.adminRconResponse.isNotBlank()) {
                Text(vm.adminRconResponse, color = StarlitColors.Gold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            if (!vm.adminConsoleError.isNullOrBlank()) {
                Text(vm.adminConsoleError!!, color = StarlitColors.Offline, fontSize = 12.sp)
            }
            Text(
                vm.adminConsoleOutput.ifBlank { "Лог пуст" },
                color = StarlitColors.TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF080A12))
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun AdminTreasuryTab(vm: LauncherViewModel) {
    StarlitCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Казна", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
            StatRow("Баланс", (vm.adminTreasury?.treasury?.balance ?: 0).toString())
            StarlitTextField(vm.treasuryPayoutCode, { vm.treasuryPayoutCode = it }, "Код карты")
            StarlitTextField(vm.treasuryPayoutAmount, { vm.treasuryPayoutAmount = it }, "Сумма")
            StarlitTextField(vm.treasuryPayoutReason, { vm.treasuryPayoutReason = it }, "Причина")
            StarlitTextField(vm.treasuryPayoutNote, { vm.treasuryPayoutNote = it }, "Заметка")
            StarlitPrimaryButton(text = "Выплатить", onClick = { vm.treasuryPayout() }, modifier = Modifier.width(140.dp))
        }
    }
}

@Composable
private fun AdminBadgesTab(vm: LauncherViewModel) {
    StarlitCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Значки", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
            if (vm.adminBadges.isEmpty()) {
                Text("Значков пока нет", color = StarlitColors.TextMuted, fontSize = 13.sp)
            }
            vm.adminBadges.forEach { b ->
                Text(
                    "${b.emoji.orEmpty()} ${b.name.orEmpty()} (${b.id})",
                    color = StarlitColors.Text,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun AdminProductsTab(vm: LauncherViewModel) {
    StarlitCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Товары и заказы", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
            Text("Товары: ${vm.adminProducts.size}", color = StarlitColors.TextMuted, fontSize = 13.sp)
            vm.adminProducts.take(30).forEach { p ->
                Text("${p.name ?: p.id} — ${p.price ?: 0}", color = StarlitColors.Text, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text("Заказы: ${vm.adminOrders.size}", color = StarlitColors.TextMuted, fontSize = 13.sp)
            vm.adminOrders.take(30).forEach { o ->
                Text("${o.id} · ${o.nickname} · ${o.status}", color = StarlitColors.Text, fontSize = 12.sp)
            }
        }
    }
}
