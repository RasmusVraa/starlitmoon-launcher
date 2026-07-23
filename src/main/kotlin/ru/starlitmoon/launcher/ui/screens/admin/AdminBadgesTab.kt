package ru.starlitmoon.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import ru.starlitmoon.launcher.api.AdminBadgeDto
import ru.starlitmoon.launcher.ui.components.StarlitPrimaryButton
import ru.starlitmoon.launcher.ui.components.StarlitSecondaryButton
import ru.starlitmoon.launcher.ui.components.StarlitTextField
import ru.starlitmoon.launcher.ui.components.StarlitConfirmDialog
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel

@Composable
fun AdminBadgesSection(vm: LauncherViewModel) {
    var editing by remember { mutableStateOf<AdminBadgeDto?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<AdminBadgeDto?>(null) }
    var grantNick by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AdminSectionCard(
            "Значки",
            "Создание значков и выдача игрокам по нику.",
            trailing = { StarlitPrimaryButton(text = "Создать", onClick = { creating = true }, compact = true, modifier = Modifier.width(110.dp)) },
        ) {
            if (vm.adminBadges.isEmpty()) AdminEmpty("Значков пока нет")
            vm.adminBadges.forEach { b ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(b.emoji.orEmpty(), fontSize = 20.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(b.name.orEmpty(), color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                        if (!b.description.isNullOrBlank()) Text(b.description!!, color = StarlitColors.TextMuted, fontSize = 11.sp)
                    }
                    StarlitSecondaryButton(text = "Изменить", onClick = { editing = b }, compact = true, modifier = Modifier.width(104.dp))
                    StarlitPrimaryButton(text = "Удалить", onClick = { deleteTarget = b }, compact = true, danger = true, modifier = Modifier.width(96.dp))
                }
            }
        }

        AdminSectionCard("Выдача значков", "Укажите ник, затем выдайте или снимите значок.") {
            StarlitTextField(grantNick, { grantNick = it }, "Ник игрока")
            if (vm.adminBadges.isEmpty()) AdminEmpty("Сначала создайте значок")
            vm.adminBadges.forEach { b ->
                val bid = b.id
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("${b.emoji.orEmpty()} ${b.name.orEmpty()}", color = StarlitColors.Text, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    if (!bid.isNullOrBlank()) {
                        StarlitSecondaryButton(text = "Выдать", onClick = { if (grantNick.isNotBlank()) vm.grantPlayerBadge(grantNick.trim(), bid) }, compact = true, modifier = Modifier.width(96.dp))
                        StarlitSecondaryButton(text = "Снять", onClick = { if (grantNick.isNotBlank()) vm.revokePlayerBadge(grantNick.trim(), bid) }, compact = true, modifier = Modifier.width(90.dp))
                    }
                }
            }
        }
    }

    if (creating) BadgeEditorDialog(vm, null) { creating = false }
    editing?.let { BadgeEditorDialog(vm, it) { editing = null } }
    deleteTarget?.let { b ->
        StarlitConfirmDialog(
            title = "Удалить значок?",
            message = "Значок «${b.name}» будет удалён у всех игроков.",
            danger = true,
            onConfirm = { b.id?.let { vm.deleteBadge(it) }; deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun BadgeEditorDialog(vm: LauncherViewModel, badge: AdminBadgeDto?, onDismiss: () -> Unit) {
    val isNew = badge == null
    var emoji by remember { mutableStateOf(badge?.emoji ?: "") }
    var name by remember { mutableStateOf(badge?.name ?: "") }
    var description by remember { mutableStateOf(badge?.description ?: "") }

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
                    .widthIn(min = 380.dp, max = 460.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF161C2B), Color(0xFF0E121C))))
                    .border(1.dp, StarlitColors.BorderStrong, RoundedCornerShape(18.dp))
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(if (isNew) "Новый значок" else "Значок", color = StarlitColors.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                StarlitTextField(emoji, { emoji = it }, "Эмодзи")
                StarlitTextField(name, { name = it }, "Название")
                StarlitTextField(description, { description = it }, "Описание")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StarlitPrimaryButton(
                        text = "Сохранить",
                        onClick = {
                            if (isNew) vm.createBadge(emoji, name, description)
                            else vm.updateBadge(badge!!.id!!, emoji, name, description)
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
