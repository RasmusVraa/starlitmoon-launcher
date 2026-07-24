package ru.starlitmoon.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.starlitmoon.launcher.api.BankDesignDto
import ru.starlitmoon.launcher.api.BankHistoryItemDto
import ru.starlitmoon.launcher.api.BankPenaltyDto
import ru.starlitmoon.launcher.ui.components.NetworkAvatar
import ru.starlitmoon.launcher.ui.components.StarlitCard
import ru.starlitmoon.launcher.ui.components.StarlitPrimaryButton
import ru.starlitmoon.launcher.ui.components.StarlitSecondaryButton
import ru.starlitmoon.launcher.ui.components.StarlitTextField
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.ui.theme.StarlitDimens
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.text.NumberFormat
import java.util.Locale

private val RuInteger: NumberFormat = NumberFormat.getIntegerInstance(Locale("ru", "RU"))

private fun formatMoney(amount: Long): String = RuInteger.format(amount)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BankScreen(vm: LauncherViewModel) {
    LaunchedEffect(Unit) { vm.refreshPlayerBank() }

    val bank = vm.playerBank
    var toCode by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var copiedHint by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "Банк",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = StarlitColors.Text,
                )
                Text(
                    "Карта, переводы, штрафы и дизайны",
                    color = StarlitColors.TextMuted,
                    fontSize = 14.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StarlitSecondaryButton(
                    text = "На сайте",
                    onClick = { vm.openSitePath("/bank") },
                    compact = true,
                )
                StarlitSecondaryButton(
                    text = "Обновить",
                    onClick = { vm.refreshPlayerBank() },
                    compact = true,
                    loading = vm.isLoadingBank,
                    enabled = !vm.isLoadingBank,
                )
            }
        }

        if (vm.isLoadingBank && bank == null) {
            Box(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = StarlitColors.Gold, strokeWidth = 2.dp)
            }
            return@Column
        }

        if (bank == null || !bank.hasCard) {
            StarlitCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Банковская карта StarlitMoon",
                        color = StarlitColors.Text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                    Text(
                        "Выпустите карту, чтобы хранить игровые средства, переводить другим игрокам и оплачивать штрафы.",
                        color = StarlitColors.TextMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                    StarlitPrimaryButton(
                        text = "Выпустить карту",
                        onClick = { vm.issueBankCard() },
                        loading = vm.isLoadingBank,
                        enabled = !vm.isLoadingBank,
                        modifier = Modifier.width(200.dp),
                    )
                }
            }
            return@Column
        }

        val card = bank.card
        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Баланс", color = StarlitColors.TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatMoney(card?.balance ?: 0)} ¤",
                    color = StarlitColors.Gold,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Номер карты", color = StarlitColors.TextDim, fontSize = 11.sp)
                        Text(
                            card?.cardCode ?: "—",
                            color = StarlitColors.Text,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Владелец", color = StarlitColors.TextDim, fontSize = 11.sp)
                        Text(
                            card?.ownerName ?: vm.userName.ifBlank { "—" },
                            color = StarlitColors.Text,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                    }
                    StarlitSecondaryButton(
                        text = if (copiedHint) "Скопировано" else "Копировать",
                        onClick = {
                            val code = card?.cardCode.orEmpty()
                            if (code.isBlank()) return@StarlitSecondaryButton
                            runCatching {
                                Toolkit.getDefaultToolkit().systemClipboard
                                    .setContents(StringSelection(code), null)
                            }.onSuccess { copiedHint = true }
                        },
                        compact = true,
                        enabled = !card?.cardCode.isNullOrBlank(),
                    )
                }
            }
        }

        // —— Transfer ——
        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Перевод", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                StarlitTextField(
                    value = toCode,
                    onValueChange = { toCode = it },
                    label = "Номер карты получателя",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StarlitTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                        label = "Сумма",
                        modifier = Modifier.weight(1f),
                    )
                    StarlitTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = "Комментарий",
                        modifier = Modifier.weight(1.4f),
                    )
                }
                val treasuryCode = bank.treasuryDonationCode
                val treasuryName = bank.treasuryDonationName
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!treasuryCode.isNullOrBlank()) {
                        StarlitSecondaryButton(
                            text = "В казну${treasuryName?.let { " · $it" }.orEmpty()}",
                            onClick = { toCode = treasuryCode },
                            compact = true,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    StarlitPrimaryButton(
                        text = "Перевести",
                        onClick = {
                            val amount = amountText.toLongOrNull() ?: 0L
                            if (toCode.isBlank() || amount <= 0L) return@StarlitPrimaryButton
                            vm.transferBank(toCode.trim(), amount, comment.trim().ifBlank { null })
                            amountText = ""
                            comment = ""
                        },
                        loading = vm.isLoadingBank,
                        enabled = !vm.isLoadingBank && toCode.isNotBlank() && (amountText.toLongOrNull() ?: 0L) > 0L,
                        compact = true,
                        modifier = Modifier.width(140.dp),
                    )
                }
            }
        }

        // —— Penalties ——
        val penalties = bank.penalties
        if (penalties.isNotEmpty()) {
            StarlitCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Штрафы", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    penalties.forEach { penalty ->
                        PenaltyRow(penalty = penalty, busy = vm.isLoadingBank) {
                            val id = penalty.id ?: return@PenaltyRow
                            vm.payBankPenalty(id)
                        }
                    }
                }
            }
        }

        // —— History ——
        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("История", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "all" to "Все",
                        "deposit" to "Пополнение",
                        "withdraw" to "Списание",
                        "transfer" to "Переводы",
                    ).forEach { (id, label) ->
                        BankFilterChip(
                            text = label,
                            selected = vm.bankHistoryFilter == id,
                            onClick = { vm.bankHistoryFilter = id },
                        )
                    }
                }
                val filtered = remember(bank.history, vm.bankHistoryFilter) {
                    filterHistory(bank.history, vm.bankHistoryFilter)
                }
                if (filtered.isEmpty()) {
                    Text("Операций пока нет", color = StarlitColors.TextMuted, fontSize = 13.sp)
                } else {
                    filtered.forEach { item -> HistoryRow(item) }
                }
            }
        }

        // —— Designs ——
        val designs = bank.designs
        val catalog = designs?.catalog.orEmpty()
        if (catalog.isNotEmpty()) {
            StarlitCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Дизайны карты", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    designs?.active?.let { activeId ->
                        val activeName = catalog.firstOrNull { it.id == activeId }?.name
                        Text(
                            "Активный: ${activeName ?: activeId}",
                            color = StarlitColors.Gold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        catalog.forEach { design ->
                            DesignCard(
                                design = design,
                                isActive = designs?.active != null && designs.active == design.id,
                                busy = vm.isLoadingBank,
                                onBuy = { design.id?.let { vm.purchaseBankDesign(it) } },
                                onEquip = { design.id?.let { vm.equipBankDesign(it) } },
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(copiedHint) {
        if (copiedHint) {
            kotlinx.coroutines.delay(1_600)
            copiedHint = false
        }
    }
}

@Composable
private fun PenaltyRow(penalty: BankPenaltyDto, busy: Boolean, onPay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StarlitDimens.RadiusSm))
            .background(StarlitColors.SurfaceElevated)
            .border(1.dp, if (penalty.overdue) StarlitColors.Offline.copy(alpha = 0.5f) else StarlitColors.Border, RoundedCornerShape(StarlitDimens.RadiusSm))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                penalty.reason?.ifBlank { null } ?: "Штраф",
                color = StarlitColors.Text,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            )
            val meta = buildList {
                if (penalty.overdue) add("Просрочен")
                penalty.dueAt?.takeIf { it.isNotBlank() }?.let { add("до $it") }
                penalty.issuedBy?.takeIf { it.isNotBlank() }?.let { add("от $it") }
            }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, color = if (penalty.overdue) StarlitColors.Offline else StarlitColors.TextDim, fontSize = 11.sp)
            }
        }
        Text(
            "${formatMoney(penalty.amount)} ¤",
            color = StarlitColors.Text,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
        StarlitPrimaryButton(
            text = "Оплатить",
            onClick = onPay,
            compact = true,
            loading = busy,
            enabled = !busy && !penalty.id.isNullOrBlank(),
            modifier = Modifier.width(110.dp),
        )
    }
}

@Composable
private fun HistoryRow(item: BankHistoryItemDto) {
    val positive = item.direction.equals("in", ignoreCase = true) ||
        item.kind.equals("deposit", ignoreCase = true)
    val amountColor = when {
        positive -> StarlitColors.Online
        item.direction.equals("out", ignoreCase = true) ||
            item.kind.equals("withdraw", ignoreCase = true) ||
            item.kind.equals("transfer", ignoreCase = true) -> StarlitColors.Offline
        else -> StarlitColors.Text
    }
    val sign = if (positive) "+" else "−"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.title?.ifBlank { null }
                    ?: item.kind?.replaceFirstChar { it.uppercase() }
                    ?: "Операция",
                color = StarlitColors.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(
                item.subtitle?.takeIf { it.isNotBlank() },
                item.counterparty?.takeIf { it.isNotBlank() },
                item.comment?.takeIf { it.isNotBlank() },
                item.createdAt?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, color = StarlitColors.TextDim, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            "$sign${formatMoney(kotlin.math.abs(item.amount))} ¤",
            color = amountColor,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun DesignCard(
    design: BankDesignDto,
    isActive: Boolean,
    busy: Boolean,
    onBuy: () -> Unit,
    onEquip: () -> Unit,
) {
    val rarityColor = remember(design.rarityColor) {
        parseHexColor(design.rarityColor) ?: StarlitColors.Gold
    }
    Column(
        modifier = Modifier
            .width(180.dp)
            .clip(RoundedCornerShape(StarlitDimens.Radius))
            .background(StarlitColors.SurfaceElevated)
            .border(
                1.dp,
                if (isActive) StarlitColors.Gold else StarlitColors.Border,
                RoundedCornerShape(StarlitDimens.Radius),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val imageUrl = design.imageUrl?.trim().orEmpty()
        if (imageUrl.isNotBlank()) {
            NetworkAvatar(
                url = imageUrl,
                fallbackName = design.name ?: design.emoji ?: "?",
                size = 72.dp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(StarlitDimens.RadiusSm))
                    .background(StarlitColors.Surface)
                    .border(1.dp, StarlitColors.Border, RoundedCornerShape(StarlitDimens.RadiusSm)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    design.emoji?.ifBlank { null } ?: "✦",
                    fontSize = 28.sp,
                )
            }
        }
        Text(
            design.name ?: "Дизайн",
            color = StarlitColors.Text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        design.rarityLabel?.takeIf { it.isNotBlank() }?.let { label ->
            Text(label, color = rarityColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        design.description?.takeIf { it.isNotBlank() }?.let { desc ->
            Text(
                desc,
                color = StarlitColors.TextDim,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            isActive -> {
                Text("На карте", color = StarlitColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            design.isOwned -> {
                StarlitPrimaryButton(
                    text = "Надеть",
                    onClick = onEquip,
                    compact = true,
                    loading = busy,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            design.canBuy -> {
                StarlitPrimaryButton(
                    text = if (design.free) "Бесплатно" else "${formatMoney(design.price)} ¤",
                    onClick = onBuy,
                    compact = true,
                    loading = busy,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            design.grantOnly -> {
                Text(
                    design.grantOnlyLabel?.ifBlank { null } ?: "Только за достижение",
                    color = StarlitColors.TextMuted,
                    fontSize = 11.sp,
                )
            }
            else -> {
                Text(
                    if (design.free) "Бесплатно" else "${formatMoney(design.price)} ¤",
                    color = StarlitColors.TextMuted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun BankFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) StarlitColors.GoldMuted else StarlitColors.Surface)
            .border(1.dp, if (selected) StarlitColors.Gold else StarlitColors.BorderStrong, shape)
            .clickable(onClick = onClick)
            .height(32.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) StarlitColors.Gold else StarlitColors.TextMuted,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun filterHistory(items: List<BankHistoryItemDto>, filter: String): List<BankHistoryItemDto> {
    if (filter == "all" || filter.isBlank()) return items
    return items.filter { item ->
        val kind = item.kind?.lowercase().orEmpty()
        val dir = item.direction?.lowercase().orEmpty()
        when (filter) {
            "deposit" -> kind == "deposit" || dir == "in"
            "withdraw" -> kind == "withdraw" || (dir == "out" && kind != "transfer")
            "transfer" -> kind == "transfer"
            else -> true
        }
    }
}

private fun parseHexColor(raw: String?): Color? {
    val s = raw?.trim()?.removePrefix("#").orEmpty()
    if (s.length != 6 && s.length != 8) return null
    return runCatching {
        val value = s.toLong(16)
        if (s.length == 8) {
            Color(
                alpha = ((value shr 24) and 0xFF).toInt() / 255f,
                red = ((value shr 16) and 0xFF).toInt() / 255f,
                green = ((value shr 8) and 0xFF).toInt() / 255f,
                blue = (value and 0xFF).toInt() / 255f,
            )
        } else {
            Color(
                red = ((value shr 16) and 0xFF).toInt() / 255f,
                green = ((value shr 8) and 0xFF).toInt() / 255f,
                blue = (value and 0xFF).toInt() / 255f,
            )
        }
    }.getOrNull()
}
