package ru.starlitmoon.launcher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            SectionTitle("Админ-панель", "Нет доступа")
            StarlitCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("У этого аккаунта нет прав администратора.", color = StarlitColors.TextMuted)
                    StarlitSecondaryButton("В кабинет", onClick = { vm.currentTab = LauncherTab.Cabinet })
                }
            }
        }
        return
    }

    val search = remember { mutableStateOf(vm.adminSearch) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
            .padding(end = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionTitle("Админ-панель", "Управление сервером")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Вы: ${vm.userName}", color = StarlitColors.Accent, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            StarlitSecondaryButton("Обновить", onClick = vm::refreshAdmin)
        }

        val stats = vm.adminStats
        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Сводка", color = StarlitColors.Text, fontWeight = FontWeight.Bold)
                StatRow("Игроки", (stats?.players ?: 0).toString())
                StatRow("Онлайн", (stats?.online ?: 0).toString())
                StatRow("Аккаунты", (stats?.accounts ?: 0).toString())
                StatRow("Заявки", (stats?.pendingApplications ?: 0).toString())
                StatRow("Кланы", (stats?.pendingClans ?: 0).toString())
                StatRow("Заказы", (stats?.orders ?: 0).toString())
                StatRow("Банк", (stats?.totalBankBalance ?: 0).toString())
            }
        }

        if (vm.adminMe?.permissions?.isNotEmpty() == true) {
            StarlitCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Права", color = StarlitColors.Text, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(vm.adminMe!!.permissions.joinToString(" · "), color = StarlitColors.TextMuted, fontSize = 13.sp)
                }
            }
        }

        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Заявки", color = StarlitColors.Text, fontWeight = FontWeight.Bold)
                if (vm.adminApps.isEmpty()) {
                    Text("Нет ожидающих заявок", color = StarlitColors.TextMuted, fontSize = 13.sp)
                } else {
                    vm.adminApps.take(12).forEach { app ->
                        Text(
                            "${app.minecraftNick ?: "—"} · ${app.status ?: "pending"}",
                            color = StarlitColors.Text,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Игроки", color = StarlitColors.Text, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StarlitTextField(
                        value = search.value,
                        onValueChange = { search.value = it },
                        label = "Поиск по нику",
                        modifier = Modifier.weight(1f),
                    )
                    StarlitPrimaryButton("Найти", onClick = { vm.searchAdminPlayers(search.value) })
                }
                LazyColumn(modifier = Modifier.height(260.dp)) {
                    items(vm.adminPlayers.take(80)) { p ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StatusDot(p.online)
                            Text(p.name ?: "—", color = StarlitColors.Text, modifier = Modifier.weight(1f), fontSize = 13.sp)
                            if (p.banned) Text("бан", color = StarlitColors.Offline, fontSize = 12.sp)
                            if (p.warnCount > 0) Text("варн ${p.warnCount}", color = StarlitColors.Accent, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
