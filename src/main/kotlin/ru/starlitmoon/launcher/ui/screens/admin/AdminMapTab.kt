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
import ru.starlitmoon.launcher.ui.components.StarlitTextField
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel

@Composable
fun AdminMapSection(vm: LauncherViewModel) {
    var owner by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var x by remember { mutableStateOf("") }
    var y by remember { mutableStateOf("") }
    var z by remember { mutableStateOf("") }

    val worlds = vm.adminMapWorlds
    var world by remember(worlds) { mutableStateOf(worlds.firstOrNull()?.id ?: "minecraft:overworld") }
    val icons = vm.adminMapIcons
    var icon by remember(icons) { mutableStateOf(icons.firstOrNull()?.id ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AdminSectionCard("Видимость карты", "Кому доступна карта на сайте.") {
            AdminFilterChips(
                options = listOf("public" to "Всем (public)", "all" to "Игрокам (all)", "admins" to "Админам"),
                selected = vm.adminMapVisibility,
                onSelect = { vm.saveMapVisibility(it) },
            )
        }

        AdminSectionCard("Новая метка", "Создать метку на карте от имени игрока.") {
            StarlitTextField(owner, { owner = it }, "Ник владельца")
            StarlitTextField(name, { name = it }, "Название метки")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StarlitTextField(x, { x = it }, "X", modifier = Modifier.weight(1f))
                StarlitTextField(y, { y = it }, "Y", modifier = Modifier.weight(1f))
                StarlitTextField(z, { z = it }, "Z", modifier = Modifier.weight(1f))
            }
            if (worlds.isNotEmpty()) {
                Text("Мир", color = StarlitColors.TextMuted, fontSize = 12.sp)
                AdminFilterChips(worlds.map { (it.id ?: "") to (it.label ?: it.id ?: "") }, world) { world = it }
            }
            if (icons.isNotEmpty()) {
                Text("Иконка", color = StarlitColors.TextMuted, fontSize = 12.sp)
                AdminFilterChips(icons.map { (it.id ?: "") to (it.label ?: it.id ?: "") }, icon) { icon = it }
            }
            StarlitPrimaryButton(
                text = "Создать метку",
                onClick = {
                    vm.createMapMarker(
                        owner, name, world,
                        x.toDoubleOrNull() ?: 0.0,
                        y.toDoubleOrNull() ?: 0.0,
                        z.toDoubleOrNull() ?: 0.0,
                        icon.ifBlank { null },
                    )
                    name = ""; x = ""; y = ""; z = ""
                },
                modifier = Modifier.width(170.dp),
                compact = true,
            )
        }

        AdminSectionCard("Метки (${vm.adminMarkers.size})", "Существующие метки игроков.") {
            if (vm.adminMarkers.isEmpty()) AdminEmpty("Меток нет")
            vm.adminMarkers.take(120).forEach { m ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${m.name ?: "—"} · ${m.ownerName ?: ""}", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            "${m.world ?: ""}  (${m.x?.toInt() ?: 0}, ${m.y?.toInt() ?: 0}, ${m.z?.toInt() ?: 0})",
                            color = StarlitColors.TextMuted,
                            fontSize = 11.sp,
                        )
                    }
                    if (!m.id.isNullOrBlank()) {
                        StarlitPrimaryButton(text = "Удалить", onClick = { vm.deleteMapMarker(m.id!!) }, compact = true, danger = true, modifier = Modifier.width(100.dp))
                    }
                }
            }
        }
    }
}
