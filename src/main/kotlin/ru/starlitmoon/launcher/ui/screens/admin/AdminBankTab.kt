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
import ru.starlitmoon.launcher.api.AdminProductDto
import ru.starlitmoon.launcher.ui.components.StarlitPrimaryButton
import ru.starlitmoon.launcher.ui.components.StarlitSecondaryButton
import ru.starlitmoon.launcher.ui.components.StarlitTextField
import ru.starlitmoon.launcher.ui.components.StatRow
import ru.starlitmoon.launcher.ui.components.StarlitConfirmDialog
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel

@Composable
fun AdminBankSection(vm: LauncherViewModel) {
    var sub by remember { mutableStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AdminSubTabsRow(listOf("Карты", "Казна", "Товары"), sub) { sub = it }
        when (sub) {
            0 -> AdminCardsTab(vm)
            1 -> AdminTreasuryTab(vm)
            else -> AdminProductsTab(vm)
        }
    }
}

@Composable
private fun AdminCardsTab(vm: LauncherViewModel) {
    var nick by remember { mutableStateOf("") }
    var bal by remember { mutableStateOf("0") }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    AdminSectionCard("Банковские карты", "Задать баланс по нику или удалить карту.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StarlitTextField(nick, { nick = it }, "Ник", modifier = Modifier.weight(1f))
            StarlitTextField(bal, { bal = it }, "Баланс", modifier = Modifier.width(120.dp))
            StarlitPrimaryButton(text = "Задать", onClick = { vm.setBank(nick, bal.toLongOrNull() ?: 0L) }, modifier = Modifier.width(100.dp), compact = true)
        }
        if (vm.adminBank.isEmpty()) AdminEmpty("Карт нет")
        vm.adminBank.take(80).forEach { card ->
            val owner = card.ownerName
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(owner ?: "—", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                    if (!card.cardCode.isNullOrBlank()) Text(card.cardCode!!, color = StarlitColors.TextDim, fontSize = 11.sp)
                }
                Text((card.balance ?: 0).toString(), color = StarlitColors.Gold, fontWeight = FontWeight.SemiBold)
                if (!owner.isNullOrBlank()) {
                    StarlitPrimaryButton(text = "Удалить", onClick = { deleteTarget = owner }, compact = true, danger = true, modifier = Modifier.width(100.dp))
                }
            }
        }
    }

    deleteTarget?.let { owner ->
        StarlitConfirmDialog(
            title = "Удалить карту?",
            message = "Карта игрока $owner будет удалена.",
            danger = true,
            onConfirm = { vm.deleteBankCard(owner); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun AdminTreasuryTab(vm: LauncherViewModel) {
    AdminSectionCard("Казна", "Баланс казны и выплаты на карты.") {
        StatRow("Баланс", (vm.adminTreasury?.treasury?.balance ?: 0).toString())
        StarlitTextField(vm.treasuryPayoutCode, { vm.treasuryPayoutCode = it }, "Код карты")
        StarlitTextField(vm.treasuryPayoutAmount, { vm.treasuryPayoutAmount = it }, "Сумма")
        StarlitTextField(vm.treasuryPayoutReason, { vm.treasuryPayoutReason = it }, "Причина (id)")
        StarlitTextField(vm.treasuryPayoutNote, { vm.treasuryPayoutNote = it }, "Заметка")
        StarlitPrimaryButton(text = "Выплатить", onClick = { vm.treasuryPayout() }, modifier = Modifier.width(150.dp), compact = true)
    }
}

@Composable
private fun AdminProductsTab(vm: LauncherViewModel) {
    var editing by remember { mutableStateOf<AdminProductDto?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteOrderId by remember { mutableStateOf<String?>(null) }

    AdminSectionCard(
        "Товары",
        "Донат-товары: цена, описание и RCON-команды.",
        trailing = { StarlitPrimaryButton(text = "Добавить", onClick = { creating = true }, compact = true, modifier = Modifier.width(110.dp)) },
    ) {
        if (vm.adminProducts.isEmpty()) AdminEmpty("Товаров нет")
        vm.adminProducts.forEach { p ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${p.icon ?: ""} ${p.name ?: p.id}".trim(), color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
                    Text("${p.price ?: 0} ◆ · ${p.commands.size} команд", color = StarlitColors.TextMuted, fontSize = 11.sp)
                }
                StarlitSecondaryButton(text = "Изменить", onClick = { editing = p }, compact = true, modifier = Modifier.width(110.dp))
                if (!p.id.isNullOrBlank()) {
                    StarlitPrimaryButton(text = "Удалить", onClick = { vm.deleteProduct(p.id!!) }, compact = true, danger = true, modifier = Modifier.width(100.dp))
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    AdminSectionCard("Заказы", "Последние заказы доната.") {
        if (vm.adminOrders.isEmpty()) AdminEmpty("Заказов нет")
        vm.adminOrders.take(60).forEach { o ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${o.nickname ?: "—"} · ${o.productName ?: o.productId ?: ""}", color = StarlitColors.Text, fontSize = 13.sp)
                    Text("${o.status ?: ""} · ${o.price ?: 0} ◆", color = StarlitColors.TextMuted, fontSize = 11.sp)
                }
                if (!o.id.isNullOrBlank()) {
                    StarlitPrimaryButton(text = "Удалить", onClick = { deleteOrderId = o.id }, compact = true, danger = true, modifier = Modifier.width(100.dp))
                }
            }
        }
    }

    if (creating) ProductEditorDialog(vm, null) { creating = false }
    editing?.let { ProductEditorDialog(vm, it) { editing = null } }
    deleteOrderId?.let { oid ->
        StarlitConfirmDialog(
            title = "Удалить заказ?",
            message = "Заказ $oid будет удалён.",
            danger = true,
            onConfirm = { vm.deleteOrder(oid); deleteOrderId = null },
            onDismiss = { deleteOrderId = null },
        )
    }
}

@Composable
private fun ProductEditorDialog(vm: LauncherViewModel, product: AdminProductDto?, onDismiss: () -> Unit) {
    val isNew = product == null
    var id by remember { mutableStateOf(product?.id ?: "") }
    var name by remember { mutableStateOf(product?.name ?: "") }
    var price by remember { mutableStateOf((product?.price ?: 0).toString()) }
    var description by remember { mutableStateOf(product?.description ?: "") }
    var commands by remember { mutableStateOf(product?.commands?.joinToString("\n") ?: "") }

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
                    .widthIn(min = 420.dp, max = 540.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF161C2B), Color(0xFF0E121C))))
                    .border(1.dp, StarlitColors.BorderStrong, RoundedCornerShape(18.dp))
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(if (isNew) "Новый товар" else "Товар: ${product?.name}", color = StarlitColors.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (isNew) StarlitTextField(id, { id = it }, "id (латиница, напр. shine_30d)")
                StarlitTextField(name, { name = it }, "Название")
                StarlitTextField(price, { price = it.filter(Char::isDigit) }, "Цена (◆)")
                StarlitTextField(description, { description = it }, "Описание")
                AdminMultilineField(commands, { commands = it }, "RCON-команды (по одной в строке, {player} — ник)")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StarlitPrimaryButton(
                        text = "Сохранить",
                        onClick = {
                            val p = price.toIntOrNull() ?: 0
                            if (isNew) vm.createProduct(id, name, p, description, commands)
                            else vm.updateProduct(product!!.id!!, name, p, description, commands)
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
