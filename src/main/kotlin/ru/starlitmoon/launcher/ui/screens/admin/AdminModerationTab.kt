package ru.starlitmoon.launcher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.starlitmoon.launcher.ui.components.StarlitPrimaryButton
import ru.starlitmoon.launcher.ui.components.StarlitSecondaryButton
import ru.starlitmoon.launcher.ui.components.StarlitTextField
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel

private val STATUS_FILTERS = listOf(
    "pending" to "На модерации",
    "approved" to "Одобрены",
    "rejected" to "Отклонены",
    "all" to "Все",
)

@Composable
fun AdminClansSection(vm: LauncherViewModel) {
    AdminSectionCard("Кланы", "Модерация заявок на кланы.") {
        AdminFilterChips(STATUS_FILTERS, vm.adminClansFilter) { vm.setClansFilter(it) }
        if (vm.adminClans.isEmpty()) AdminEmpty("Кланов нет")
        vm.adminClans.forEach { clan ->
            val id = clan.id
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(clan.name ?: id ?: "—", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(clan.owner, clan.status, "${clan.members.size} уч.").joinToString(" · "),
                        color = StarlitColors.TextMuted,
                        fontSize = 11.sp,
                    )
                }
                if (!id.isNullOrBlank()) {
                    if (clan.status == "pending") {
                        StarlitPrimaryButton(text = "Одобрить", onClick = { vm.approveClan(id) }, compact = true, modifier = Modifier.width(110.dp))
                        StarlitSecondaryButton(text = "Отклонить", onClick = { vm.rejectClan(id) }, compact = true, modifier = Modifier.width(110.dp))
                    } else {
                        StarlitPrimaryButton(text = "Удалить", onClick = { vm.deleteClan(id) }, compact = true, danger = true, modifier = Modifier.width(100.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AdminAppsSection(vm: LauncherViewModel) {
    AdminSectionCard("Заявки на сервер", "Приём и отклонение заявок whitelist.") {
        AdminFilterChips(STATUS_FILTERS, vm.adminAppsFilter) { vm.setAppsFilter(it) }
        if (vm.adminApps.isEmpty()) AdminEmpty("Заявок нет")
        vm.adminApps.forEach { app ->
            val id = app.id
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.minecraftNick ?: id ?: "—", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                    Text(listOfNotNull(app.discordNick, app.status).joinToString(" · "), color = StarlitColors.TextMuted, fontSize = 11.sp)
                }
                if (!id.isNullOrBlank()) {
                    if (app.status == "pending") {
                        StarlitPrimaryButton(text = "Принять", onClick = { vm.acceptApp(id) }, compact = true, modifier = Modifier.width(104.dp))
                        StarlitSecondaryButton(text = "Отклонить", onClick = { vm.rejectApp(id) }, compact = true, modifier = Modifier.width(110.dp))
                    } else {
                        StarlitPrimaryButton(text = "Удалить", onClick = { vm.deleteApp(id) }, compact = true, danger = true, modifier = Modifier.width(100.dp))
                    }
                }
            }
        }
    }
}

private val CONTEST_FILTERS = listOf(
    "all" to "Все",
    "pending" to "На модерации",
    "approved" to "Одобрены",
    "winner" to "Победители",
    "rejected" to "Отклонены",
)

@Composable
fun AdminContestSection(vm: LauncherViewModel) {
    val settings = vm.adminContestSettings
    var enabled by remember(settings) { mutableStateOf(settings?.enabled ?: true) }
    var title by remember(settings) { mutableStateOf(settings?.page?.title ?: "") }
    var season by remember(settings) { mutableStateOf(settings?.page?.seasonName ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AdminSectionCard("Настройки конкурса", "Включение и заголовок страницы конкурса.") {
            AdminCheckbox(enabled, "Приём заявок открыт", { enabled = it })
            StarlitTextField(title, { title = it }, "Заголовок")
            StarlitTextField(season, { season = it }, "Название сезона")
            StarlitPrimaryButton(text = "Сохранить", onClick = { vm.saveContestSettings(enabled, title, season) }, modifier = Modifier.width(140.dp), compact = true)
        }

        AdminSectionCard("Работы", "Модерация конкурсных работ.") {
            AdminFilterChips(CONTEST_FILTERS, vm.adminContestFilter) { vm.setContestFilter(it) }
            if (vm.adminContest.isEmpty()) AdminEmpty("Работ нет")
            vm.adminContest.forEach { e ->
                val id = e.id
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${e.baseName ?: "—"} · ${e.minecraftNick ?: ""}", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                    Text("${e.status ?: ""} · ${e.buildOrigin ?: ""}", color = StarlitColors.TextMuted, fontSize = 11.sp)
                    if (!id.isNullOrBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StarlitSecondaryButton(text = "Одобрить", onClick = { vm.patchContestEntry(id, "approved") }, compact = true, modifier = Modifier.width(104.dp))
                            StarlitSecondaryButton(text = "Победитель", onClick = { vm.patchContestEntry(id, "winner") }, compact = true, modifier = Modifier.width(116.dp))
                            StarlitSecondaryButton(text = "Отклонить", onClick = { vm.patchContestEntry(id, "rejected") }, compact = true, modifier = Modifier.width(110.dp))
                            StarlitPrimaryButton(text = "Удалить", onClick = { vm.deleteContestEntry(id) }, compact = true, danger = true, modifier = Modifier.width(100.dp))
                        }
                    }
                }
            }
        }
    }
}
