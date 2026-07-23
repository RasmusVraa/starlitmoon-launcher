package ru.starlitmoon.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ru.starlitmoon.launcher.api.AdminAdminDto
import ru.starlitmoon.launcher.ui.components.StarlitPrimaryButton
import ru.starlitmoon.launcher.ui.components.StarlitSecondaryButton
import ru.starlitmoon.launcher.ui.components.StarlitTextField
import ru.starlitmoon.launcher.ui.components.StatRow
import ru.starlitmoon.launcher.ui.components.StarlitConfirmDialog
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel

@Composable
fun AdminSettingsSection(vm: LauncherViewModel) {
    var version by remember(vm.serverVersionDraft) { mutableStateOf(vm.serverVersionDraft.ifBlank { vm.serverVersion }) }
    AdminSectionCard("Настройки сайта", "Версия сервера, отображаемая на сайте.") {
        StarlitTextField(version, { version = it }, "Версия сервера")
        StarlitPrimaryButton(text = "Сохранить", onClick = { vm.saveServerVersion(version) }, modifier = Modifier.width(140.dp), compact = true)
        StatRow("API", vm.configState.apiBaseUrl)
    }
}

@Composable
fun AdminAccessSection(vm: LauncherViewModel) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AdminAdminDto?>(null) }
    var removeTarget by remember { mutableStateOf<AdminAdminDto?>(null) }

    AdminSectionCard(
        "Доступ",
        "Администраторы и их права.",
        trailing = { StarlitPrimaryButton(text = "Добавить", onClick = { adding = true }, compact = true, modifier = Modifier.width(110.dp)) },
    ) {
        if (vm.adminAdmins.isEmpty()) AdminEmpty("Список пуст")
        vm.adminAdmins.forEach { a ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(a.nickname ?: "—", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                    Text(a.permissions.joinToString(" · ").ifBlank { "—" }, color = StarlitColors.TextMuted, fontSize = 11.sp)
                }
                StarlitSecondaryButton(text = "Права", onClick = { editing = a }, compact = true, modifier = Modifier.width(96.dp))
                StarlitPrimaryButton(text = "Удалить", onClick = { removeTarget = a }, compact = true, danger = true, modifier = Modifier.width(100.dp))
            }
        }
    }

    if (adding) AccessEditorDialog(vm, null) { adding = false }
    editing?.let { AccessEditorDialog(vm, it) { editing = null } }
    removeTarget?.let { a ->
        StarlitConfirmDialog(
            title = "Удалить админа?",
            message = "У ${a.nickname} будет отозван доступ к админ-панели.",
            danger = true,
            onConfirm = { a.nickname?.let { vm.removeAdmin(it) }; removeTarget = null },
            onDismiss = { removeTarget = null },
        )
    }
}

@Composable
private fun AccessEditorDialog(vm: LauncherViewModel, admin: AdminAdminDto?, onDismiss: () -> Unit) {
    val isNew = admin == null
    var nick by remember { mutableStateOf(admin?.nickname ?: "") }
    val selected = remember { mutableStateListOf<String>().apply { addAll(admin?.permissions ?: emptyList()) } }
    val defs = vm.adminPermissionDefs

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
                    .widthIn(min = 440.dp, max = 560.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF161C2B), Color(0xFF0E121C))))
                    .border(1.dp, StarlitColors.BorderStrong, RoundedCornerShape(18.dp))
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(if (isNew) "Новый админ" else "Права: ${admin?.nickname}", color = StarlitColors.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (isNew) StarlitTextField(nick, { nick = it }, "Ник")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    defs.forEach { def ->
                        AdminCheckbox(
                            checked = selected.contains(def.id),
                            label = "${def.label ?: def.id}",
                            onToggle = { if (it) { if (!selected.contains(def.id)) selected.add(def.id) } else selected.remove(def.id) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StarlitPrimaryButton(
                        text = "Сохранить",
                        onClick = {
                            if (isNew) vm.addAdmin(nick, selected.toList())
                            else vm.updateAdmin(admin!!.nickname!!, selected.toList())
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        compact = true,
                    )
                    StarlitSecondaryButton(text = "Отмена", onClick = onDismiss, modifier = Modifier.weight(1f), compact = true)
                }
            }
        }
    }
}

@Composable
fun AdminConsoleSection(vm: LauncherViewModel) {
    AdminSectionCard("Консоль", "RCON-команды и вывод screen на игровом сервере.") {
        if (vm.adminMcServers.isNotEmpty()) {
            Text("Сервер", color = StarlitColors.TextMuted, fontSize = 12.sp)
            AdminFilterChips(
                vm.adminMcServers.map { (it.id ?: "") to (it.name ?: it.id ?: "") },
                vm.adminConsoleServerId,
            ) { vm.adminConsoleServerId = it; vm.loadConsoleOutput() }
        } else {
            StarlitTextField(vm.adminConsoleServerId, { vm.adminConsoleServerId = it }, "serverId (опц.)")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StarlitSecondaryButton(text = "Лог", onClick = { vm.loadConsoleOutput() }, modifier = Modifier.width(90.dp), compact = true)
            StarlitTextField(vm.rconCommand, { vm.rconCommand = it }, "RCON команда", modifier = Modifier.weight(1f))
            StarlitPrimaryButton(text = "Exec", onClick = { vm.execRcon() }, modifier = Modifier.width(90.dp), compact = true)
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
                .height(240.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF080A12))
                .padding(10.dp)
                .verticalScroll(rememberScrollState()),
        )
    }
}
