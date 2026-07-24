package ru.starlitmoon.launcher.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.starlitmoon.launcher.api.BankDesignDto
import ru.starlitmoon.launcher.api.BankHistoryItemDto
import ru.starlitmoon.launcher.api.BankPenaltyDto
import ru.starlitmoon.launcher.ui.components.StarlitCard
import ru.starlitmoon.launcher.ui.components.StarlitSecondaryButton
import ru.starlitmoon.launcher.ui.components.StarlitTextField
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.util.ImageDiskCache
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val RuInteger: NumberFormat = NumberFormat.getIntegerInstance(Locale("ru", "RU"))

private fun formatAr(amount: Long): String = "${RuInteger.format(amount)} АР"

/** Kind icons — same as site `bank.js` TX_ICONS. */
private val TxIcons = mapOf(
    "deposit" to "↓",
    "withdraw" to "↑",
    "transfer_in" to "←",
    "transfer_out" to "→",
    "penalty_pay" to "⚠",
    "treasury_contribution" to "🏛",
    "treasury_payout_in" to "🏛",
    "design_purchase" to "🎨",
)

private object BankImageLoader {
    private val memory = ConcurrentHashMap<String, ImageBitmap>()

    fun peek(url: String): ImageBitmap? = memory[url]

    suspend fun load(url: String): ImageBitmap? {
        if (url.isBlank()) return null
        memory[url]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val bytes = ImageDiskCache.loadOrFetch(url) ?: return@runCatching null
                org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
            }.getOrNull()?.also { memory[url] = it }
        }
    }
}

@Composable
fun BankScreen(vm: LauncherViewModel) {
    LaunchedEffect(Unit) { vm.refreshPlayerBank() }

    val bank = vm.playerBank
    val apiBase = vm.configState.apiBaseUrl
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Банк",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = StarlitColors.Text,
                )
                Text(
                    "Алмазная руда · карта · переводы",
                    color = StarlitColors.TextMuted,
                    fontSize = 13.sp,
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
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BankPlasticCard(
                        balance = 0,
                        code = "SM-XXXX-XXXX",
                        owner = vm.userName.ifBlank { "Игрок" },
                        themeId = "starlit",
                        imageUrl = null,
                        modifier = Modifier.width(280.dp),
                        compact = true,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Твоя банковская карта",
                            color = StarlitColors.Text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                        Text(
                            "Храни алмазную руду, переводи по коду карты и оплачивай штрафы. Пополнение и вывод — командами на сервере.",
                            color = StarlitColors.TextMuted,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                        Text(
                            "/bank deposit  ·  /bank withdraw  ·  /bank balance",
                            color = StarlitColors.TextDim,
                            fontSize = 12.sp,
                        )
                        BankActionButton(
                            text = "Получить карту",
                            onClick = { vm.issueBankCard() },
                            loading = vm.isLoadingBank,
                            enabled = !vm.isLoadingBank,
                            emphasized = true,
                            modifier = Modifier.width(180.dp),
                        )
                    }
                }
            }
            return@Column
        }

        val card = bank.card
        val designs = bank.designs
        val catalog = designs?.catalog.orEmpty()
        val activeId = designs?.active ?: card?.cardDesign ?: "starlit"
        val activeDesign = catalog.firstOrNull { it.id == activeId }
        val themeId = (card?.cardDesignTheme ?: activeDesign?.themePreset ?: activeId)
            .orEmpty().ifBlank { "starlit" }
        val imageUrl = resolveBankImageUrl(
            card?.cardDesignImage ?: designs?.activeImage ?: activeDesign?.imageUrl,
            apiBase,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            BankPlasticCard(
                balance = card?.balance ?: 0,
                code = card?.cardCode ?: "—",
                owner = card?.ownerName ?: vm.userName.ifBlank { "—" },
                themeId = themeId,
                imageUrl = imageUrl,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                fillBounds = true,
                onCopyCode = {
                    val code = card?.cardCode.orEmpty()
                    if (code.isBlank()) return@BankPlasticCard
                    runCatching {
                        Toolkit.getDefaultToolkit().systemClipboard
                            .setContents(StringSelection(code), null)
                    }.onSuccess { copiedHint = true }
                },
                copiedHint = copiedHint,
            )

            StarlitCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Перевод", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        StarlitTextField(
                            value = toCode,
                            onValueChange = { toCode = it },
                            label = "Код карты получателя",
                        )
                        StarlitTextField(
                            value = amountText,
                            onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                            label = "Сумма (АР)",
                        )
                        StarlitTextField(
                            value = comment,
                            onValueChange = { comment = it },
                            label = "Комментарий",
                        )
                    }
                    val treasuryCode = bank.treasuryDonationCode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!treasuryCode.isNullOrBlank()) {
                            BankActionButton(
                                text = "В казну",
                                onClick = { toCode = treasuryCode },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        BankActionButton(
                            text = "Перевести",
                            onClick = {
                                val amount = amountText.toLongOrNull() ?: 0L
                                if (toCode.isBlank() || amount <= 0L) return@BankActionButton
                                vm.transferBank(toCode.trim(), amount, comment.trim().ifBlank { null })
                                amountText = ""
                                comment = ""
                            },
                            loading = vm.isLoadingBank,
                            enabled = !vm.isLoadingBank && toCode.isNotBlank() &&
                                (amountText.toLongOrNull() ?: 0L) > 0L,
                            emphasized = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        val penalties = bank.penalties
        if (penalties.isNotEmpty()) {
            StarlitCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Штрафы", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    penalties.forEach { penalty ->
                        PenaltyRow(penalty = penalty, busy = vm.isLoadingBank) {
                            val id = penalty.id ?: return@PenaltyRow
                            vm.payBankPenalty(id)
                        }
                    }
                }
            }
        }

        StarlitCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("История", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
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
                    filtered.take(40).forEach { item -> HistoryRow(item) }
                }
            }
        }

        if (catalog.isNotEmpty()) {
            StarlitCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Дизайны карты", color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(
                        "Оформление применяется к карте выше",
                        color = StarlitColors.TextMuted,
                        fontSize = 12.sp,
                    )
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val gap = 12.dp
                        val minTile = 148.dp
                        val cols = ((maxWidth + gap) / (minTile + gap)).toInt().coerceAtLeast(2)
                        val tileW = (maxWidth - gap * (cols - 1)) / cols
                        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                            catalog.chunked(cols).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(gap),
                                ) {
                                    rowItems.forEach { design ->
                                        DesignShopTile(
                                            design = design,
                                            apiBase = apiBase,
                                            isActive = designs?.active != null && designs.active == design.id,
                                            busy = vm.isLoadingBank,
                                            onBuy = { design.id?.let { vm.purchaseBankDesign(it) } },
                                            onEquip = { design.id?.let { vm.equipBankDesign(it) } },
                                            modifier = Modifier.width(tileW),
                                        )
                                    }
                                    // Stretch leftover slots so the last row still fills width.
                                    repeat(cols - rowItems.size) {
                                        Spacer(Modifier.width(tileW))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BankActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    emphasized: Boolean = false,
    danger: Boolean = false,
) {
    val shape = RoundedCornerShape(10.dp)
    val active = enabled && !loading
    val borderColor = when {
        danger -> StarlitColors.Offline.copy(alpha = if (active) 0.65f else 0.3f)
        emphasized -> StarlitColors.Gold.copy(alpha = if (active) 0.55f else 0.25f)
        else -> StarlitColors.BorderStrong
    }
    val bg = when {
        danger -> StarlitColors.Offline.copy(alpha = if (active) 0.16f else 0.08f)
        emphasized -> StarlitColors.Gold.copy(alpha = if (active) 0.14f else 0.06f)
        else -> StarlitColors.SurfaceElevated
    }
    val fg = when {
        !active -> StarlitColors.TextDim
        danger -> StarlitColors.Offline
        emphasized -> StarlitColors.Gold
        else -> StarlitColors.Text
    }
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = active, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = fg, strokeWidth = 2.dp)
        } else {
            Text(
                text,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
        }
    }
}

@Composable
private fun BankPlasticCard(
    balance: Long,
    code: String,
    owner: String,
    themeId: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    fillBounds: Boolean = false,
    onCopyCode: (() -> Unit)? = null,
    copiedHint: Boolean = false,
) {
    val shape = RoundedCornerShape(14.dp)
    val brush = remember(themeId) { themeBrush(themeId) }
    val hasArt = !imageUrl.isNullOrBlank()
    Box(
        modifier = modifier
            .then(if (fillBounds) Modifier else Modifier.aspectRatio(1.586f))
            .clip(shape)
            .background(Color(0xFF0E0C16))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape),
    ) {
        if (hasArt) {
            CachedBankImage(url = imageUrl!!, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.78f),
                                Color.Black.copy(alpha = 0.12f),
                                Color.Black.copy(alpha = 0.28f),
                                Color.Black.copy(alpha = 0.82f),
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent),
                        ),
                    ),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(brush))
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.1f), Color.Transparent),
                            radius = 520f,
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Starlit Bank",
                    color = Color.White.copy(alpha = 0.95f),
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 12.sp else 14.sp,
                    letterSpacing = 0.5.sp,
                )
                Box(
                    modifier = Modifier
                        .size(if (compact) 20.dp else 26.dp, if (compact) 14.dp else 18.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFE8C878), Color(0xFFC9A227), Color(0xFF8B6914)),
                            ),
                        ),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Баланс",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        RuInteger.format(balance),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (compact) 24.sp else 32.sp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "АР",
                        color = Color(0xFFFDE68A),
                        fontWeight = FontWeight.Bold,
                        fontSize = if (compact) 13.sp else 15.sp,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Код", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            code,
                            color = Color(0xFFFDE68A),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = if (compact) 12.sp else 14.sp,
                            letterSpacing = 0.6.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (onCopyCode != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.White.copy(alpha = 0.14f))
                                    .border(1.dp, Color(0xFFFDE68A).copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                                    .clickable(onClick = onCopyCode)
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (copiedHint) "Скопировано" else "Копировать",
                                    color = Color(0xFFFDE68A),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Владелец", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp)
                    Text(
                        owner,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = if (compact) 11.sp else 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Shop tile preview — matches site: theme/image + brand + emoji, no balance. */
@Composable
private fun BankDesignPreview(
    themeId: String,
    imageUrl: String?,
    emoji: String,
    rarityLabel: String?,
    rarityColor: Color,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val shape = RoundedCornerShape(10.dp)
    val brush = remember(themeId) { themeBrush(themeId) }
    val hasArt = !imageUrl.isNullOrBlank()
    Box(
        modifier = modifier
            .aspectRatio(1.586f)
            .clip(shape)
            .background(Color(0xFF0E0C16))
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) StarlitColors.Gold.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.08f),
                shape = shape,
            ),
    ) {
        if (hasArt) {
            CachedBankImage(url = imageUrl!!, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.72f),
                                Color.Black.copy(alpha = 0.15f),
                                Color.Black.copy(alpha = 0.55f),
                            ),
                        ),
                    ),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(brush))
        }

        Text(
            "Starlit",
            color = Color.White.copy(alpha = 0.92f),
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp, 8.dp),
        )

        if (!rarityLabel.isNullOrBlank()) {
            Text(
                rarityLabel,
                color = rarityColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
        }

        Text(
            emoji,
            fontSize = 18.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp, 8.dp),
        )
    }
}

@Composable
private fun DesignShopTile(
    design: BankDesignDto,
    apiBase: String,
    isActive: Boolean,
    busy: Boolean,
    onBuy: () -> Unit,
    onEquip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rarityColor = remember(design.rarityColor) {
        parseHexColor(design.rarityColor) ?: StarlitColors.Gold
    }
    val theme = (design.themePreset ?: design.id).orEmpty().ifBlank { "starlit" }
    val imageUrl = resolveBankImageUrl(design.imageUrl, apiBase)
    val emoji = design.emoji?.takeIf { it.isNotBlank() } ?: "◆"
    val tileShape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .clip(tileShape)
            .background(StarlitColors.Surface.copy(alpha = 0.55f))
            .border(
                1.dp,
                when {
                    isActive -> StarlitColors.Gold.copy(alpha = 0.55f)
                    design.isOwned -> Color(0xFF4ADE80).copy(alpha = 0.28f)
                    else -> StarlitColors.Border
                },
                tileShape,
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BankDesignPreview(
            themeId = theme,
            imageUrl = imageUrl,
            emoji = emoji,
            rarityLabel = null,
            rarityColor = rarityColor,
            modifier = Modifier.fillMaxWidth(),
            active = isActive,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                design.name ?: "Дизайн",
                color = StarlitColors.Text,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            design.rarityLabel?.takeIf { it.isNotBlank() }?.let { label ->
                Text(label, color = rarityColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        when {
            isActive -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(StarlitColors.Gold.copy(alpha = 0.12f))
                        .border(1.dp, StarlitColors.Gold.copy(alpha = 0.4f), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Выбрано", color = StarlitColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            design.isOwned -> {
                BankActionButton(
                    text = "Применить",
                    onClick = onEquip,
                    loading = busy,
                    enabled = !busy,
                    emphasized = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            design.canBuy -> {
                BankActionButton(
                    text = if (design.free) "Бесплатно" else "Купить · ${formatAr(design.price)}",
                    onClick = onBuy,
                    loading = busy,
                    enabled = !busy,
                    emphasized = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            design.grantOnly -> {
                Text(
                    design.grantOnlyLabel?.ifBlank { null } ?: "Только награда",
                    color = StarlitColors.TextMuted,
                    fontSize = 11.sp,
                )
            }
            else -> {
                Text(formatAr(design.price), color = StarlitColors.TextDim, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CachedBankImage(url: String, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf(BankImageLoader.peek(url)) }
    LaunchedEffect(url) {
        bitmap = BankImageLoader.load(url) ?: bitmap
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
        )
    }
}

@Composable
private fun PenaltyRow(penalty: BankPenaltyDto, busy: Boolean, onPay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(StarlitColors.SurfaceElevated)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                penalty.reason ?: "Штраф",
                color = StarlitColors.Text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            val due = listOfNotNull(
                if (penalty.overdue) "Просрочен" else null,
                penalty.dueAt?.take(10),
                penalty.issuedBy?.let { "от $it" },
            ).joinToString(" · ")
            if (due.isNotBlank()) {
                Text(due, color = StarlitColors.TextDim, fontSize = 11.sp)
            }
        }
        Text(
            formatAr(penalty.amount),
            color = StarlitColors.Offline,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
        BankActionButton(
            text = "Оплатить",
            onClick = onPay,
            loading = busy,
            enabled = !busy,
            danger = true,
        )
    }
}

@Composable
private fun HistoryRow(item: BankHistoryItemDto) {
    val incoming = item.direction.equals("in", ignoreCase = true)
    val amountColor = if (incoming) Color(0xFF6EE7B7) else StarlitColors.Offline
    val sign = if (incoming) "+" else "−"
    val icon = TxIcons[item.kind.orEmpty()] ?: "◆"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(StarlitColors.Surface.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(StarlitColors.SurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, fontSize = 15.sp, color = StarlitColors.Text)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.title ?: item.kind ?: "Операция",
                color = StarlitColors.Text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(
                item.counterparty,
                item.counterpartyCode,
                item.subtitle,
                item.createdAt?.take(16)?.replace('T', ' '),
            ).distinct().joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, color = StarlitColors.TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            "$sign${RuInteger.format(kotlin.math.abs(item.amount))} АР",
            color = amountColor,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun BankFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(shape)
            .background(if (selected) StarlitColors.GoldMuted else StarlitColors.SurfaceHover)
            .border(
                1.dp,
                if (selected) StarlitColors.Gold.copy(alpha = 0.7f) else StarlitColors.Border,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) StarlitColors.Gold else StarlitColors.TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun filterHistory(items: List<BankHistoryItemDto>, filter: String): List<BankHistoryItemDto> =
    when (filter) {
        "deposit" -> items.filter { it.kind == "deposit" }
        "withdraw" -> items.filter { it.kind == "withdraw" }
        "transfer" -> items.filter {
            it.kind in setOf(
                "transfer_in",
                "transfer_out",
                "treasury_payout_in",
                "treasury_contribution",
            )
        }
        else -> items
    }

private fun resolveBankImageUrl(raw: String?, apiBase: String): String? {
    val u = raw?.trim().orEmpty()
    if (u.isBlank()) return null
    if (u.startsWith("http://", ignoreCase = true) || u.startsWith("https://", ignoreCase = true)) return u
    val base = apiBase.trimEnd('/')
    return if (u.startsWith("/")) "$base$u" else "$base/$u"
}

private fun themeBrush(themeId: String): Brush {
    val id = themeId.lowercase().trim()
    return when {
        id.contains("grass") -> Brush.verticalGradient(
            listOf(Color(0xFF6EB5FF), Color(0xFF6EB5FF), Color(0xFF5A9E3A), Color(0xFF3D6B24)),
        )
        id.contains("dirt") -> Brush.verticalGradient(
            listOf(Color(0xFF5A9E3A), Color(0xFF5A9E3A), Color(0xFF8B5A2B), Color(0xFF6B4423)),
        )
        id.contains("stone") && !id.contains("deep") -> Brush.linearGradient(
            listOf(Color(0xFF9A9A9A), Color(0xFF7A7A7A), Color(0xFF6E6E6E), Color(0xFF5A5A5A)),
        )
        id.contains("deepslate") || id == "deep" -> Brush.linearGradient(
            listOf(Color(0xFF3D4450), Color(0xFF2B3038), Color(0xFF1A1D24)),
        )
        id.contains("diamond") -> Brush.linearGradient(
            listOf(Color(0xFF1E4D6B), Color(0xFF2D6A8F), Color(0xFF0F2D44)),
        )
        id.contains("emerald") -> Brush.linearGradient(
            listOf(Color(0xFF14532D), Color(0xFF166534), Color(0xFF052E16)),
        )
        id.contains("gold") -> Brush.linearGradient(
            listOf(Color(0xFFCA8A04), Color(0xFFEAB308), Color(0xFFA16207)),
        )
        id.contains("redstone") -> Brush.linearGradient(
            listOf(Color(0xFF7F1D1D), Color(0xFFB91C1C), Color(0xFF450A0A)),
        )
        id.contains("lapis") -> Brush.linearGradient(
            listOf(Color(0xFF1E3A8A), Color(0xFF2563EB), Color(0xFF172554)),
        )
        id.contains("nether") -> Brush.linearGradient(
            listOf(Color(0xFF450A0A), Color(0xFF7C2D12), Color(0xFF292524)),
        )
        id.contains("crimson") -> Brush.linearGradient(
            listOf(Color(0xFF4C0519), Color(0xFF9F1239), Color(0xFF3F0D1A)),
        )
        id.contains("end") -> Brush.verticalGradient(
            listOf(Color(0xFF0F0A1A), Color(0xFF1E1B2E), Color(0xFFDDD6C8), Color(0xFFC9C0B0)),
        )
        id.contains("ocean") -> Brush.linearGradient(
            listOf(Color(0xFF0C4A6E), Color(0xFF0E7490), Color(0xFF164E63)),
        )
        id.contains("amethyst") -> Brush.linearGradient(
            listOf(Color(0xFF3B0764), Color(0xFF6B21A8), Color(0xFF2E1065)),
        )
        else -> Brush.linearGradient(
            listOf(Color(0xFF1A0F3D), Color(0xFF2D1F6E), Color(0xFF163A5C)),
        )
    }
}

private fun parseHexColor(raw: String?): Color? {
    val s = raw?.trim().orEmpty().removePrefix("#")
    if (s.length != 6 && s.length != 8) return null
    return runCatching {
        val r = s.substring(0, 2).toInt(16)
        val g = s.substring(2, 4).toInt(16)
        val b = s.substring(4, 6).toInt(16)
        val a = if (s.length == 8) s.substring(6, 8).toInt(16) else 255
        Color(r, g, b, a)
    }.getOrNull()
}
