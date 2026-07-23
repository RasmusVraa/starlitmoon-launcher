package ru.starlitmoon.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.starlitmoon.launcher.ui.components.SectionTitle
import ru.starlitmoon.launcher.ui.components.StarlitCard
import ru.starlitmoon.launcher.ui.components.StarlitSecondaryButton
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.ui.theme.StarlitDimens
import ru.starlitmoon.launcher.viewmodel.LauncherTab
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel

/** Tabs in the same order as starlit-moon.ru/admin (with the required permission id). */
private val adminTabs = listOf(
    "Игроки" to "players",
    "Банк" to "bank",
    "Значки" to "badges",
    "Карта" to "map",
    "Кланы" to "clans",
    "Заявки" to "applications",
    "Конкурс" to "contest",
    "Вики" to "wiki",
    "Сборки" to "modpacks",
    "Настройки" to "access",
    "Доступ" to "access",
    "Консоль" to "console",
)

fun adminHasPerm(vm: LauncherViewModel, id: String): Boolean =
    vm.adminMe?.permissions?.contains(id) == true

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
            color = StarlitColors.Text,
        )
        Text("Управление данными сайта и игроками", color = StarlitColors.TextMuted, fontSize = 14.sp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Вы вошли как ${vm.userName}", color = StarlitColors.TextMuted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StarlitSecondaryButton(text = "Сайт", onClick = { vm.openAdminWebsite() }, modifier = Modifier.width(90.dp), compact = true)
                StarlitSecondaryButton(text = "Обновить", onClick = { vm.refreshAdmin() }, modifier = Modifier.width(120.dp), compact = true)
            }
        }

        AdminStatsStrip(vm)

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            adminTabs.forEachIndexed { index, (title, _) ->
                AdminTabChip(
                    title = title,
                    selected = vm.adminSubTab == index,
                    onClick = { vm.adminSubTab = index },
                )
            }
        }

        val (_, perm) = adminTabs[vm.adminSubTab]
        if (!adminHasPerm(vm, perm)) {
            AdminNoPermission(perm)
            return@Column
        }

        when (vm.adminSubTab) {
            0 -> AdminPlayersSection(vm)
            1 -> AdminBankSection(vm)
            2 -> AdminBadgesSection(vm)
            3 -> AdminMapSection(vm)
            4 -> AdminClansSection(vm)
            5 -> AdminAppsSection(vm)
            6 -> AdminContestSection(vm)
            7 -> AdminWikiSection(vm)
            8 -> AdminModpacksSection(vm)
            9 -> AdminSettingsSection(vm)
            10 -> AdminAccessSection(vm)
            11 -> AdminConsoleSection(vm)
        }
    }
}

@Composable
private fun AdminNoPermission(perm: String) {
    AdminSectionCard("Нет доступа", "У вашей учётной записи нет права «$perm».") {
        Text(
            "Обратитесь к администратору с правом «Доступ», чтобы получить доступ к этому разделу.",
            color = StarlitColors.TextMuted,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun AdminTabChip(title: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) StarlitColors.Gold else StarlitColors.Surface)
            .border(1.dp, if (selected) StarlitColors.Gold else StarlitColors.BorderStrong, shape)
            .clickable(onClick = onClick)
            .height(34.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            color = if (selected) StarlitColors.OnGold else StarlitColors.Text,
            fontSize = 13.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AdminStatsStrip(vm: LauncherViewModel) {
    val s = vm.adminStats
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AdminStat("Игроки", (s?.players ?: 0).toString())
        AdminStat("Онлайн", (s?.online ?: 0).toString())
        AdminStat("Баны", (s?.banned ?: 0).toString())
        AdminStat("Заявки", (s?.pendingApplications ?: 0).toString())
        AdminStat("Кланы", (s?.pendingClans ?: 0).toString())
        AdminStat("Банк", (s?.totalBankBalance ?: 0).toString())
        AdminStat("Казна", (s?.treasuryBalance ?: 0).toString())
    }
}

@Composable
private fun AdminStat(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(StarlitDimens.Radius))
            .background(StarlitColors.Surface)
            .border(1.dp, StarlitColors.Border, RoundedCornerShape(StarlitDimens.Radius))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, color = StarlitColors.TextDim, fontSize = 11.sp)
        Text(value, color = StarlitColors.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

// ---------------------------------------------------------------------------
// Shared building blocks (package-visible, reused across admin tab files)
// ---------------------------------------------------------------------------

@Composable
fun AdminSectionCard(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    StarlitCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    if (subtitle != null) {
                        Spacer(Modifier.height(3.dp))
                        Text(subtitle, color = StarlitColors.TextMuted, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

/** Segmented sub-tab selector — fixed height chips with centered text. */
@Composable
fun AdminSubTabsRow(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        titles.forEachIndexed { i, title -> AdminChip(title, i == selected) { onSelect(i) } }
    }
}

/** Filter chips: list of (id,label). */
@Composable
fun AdminFilterChips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (id, label) -> AdminChip(label, id == selected) { onSelect(id) } }
    }
}

@Composable
fun AdminChip(text: String, selected: Boolean, onClick: () -> Unit) {
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

@Composable
fun AdminCheckbox(checked: Boolean, label: String, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clickable { onToggle(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) StarlitColors.Gold else StarlitColors.Surface)
                .border(1.dp, if (checked) StarlitColors.Gold else StarlitColors.BorderStrong, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text("✓", color = StarlitColors.OnGold, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 13.sp)
            }
        }
        Text(label, color = StarlitColors.Text, fontSize = 13.sp)
    }
}

/** Multiline text field matching StarlitTextField styling. */
@Composable
fun AdminMultilineField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minHeight: Int = 96,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth().heightIn(min = minHeight.dp),
        singleLine = false,
        shape = RoundedCornerShape(StarlitDimens.RadiusSm),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = StarlitColors.Text,
            unfocusedTextColor = StarlitColors.Text,
            focusedBorderColor = StarlitColors.Gold,
            unfocusedBorderColor = StarlitColors.Border,
            focusedLabelColor = StarlitColors.Gold,
            unfocusedLabelColor = StarlitColors.TextMuted,
            cursorColor = StarlitColors.Gold,
            focusedContainerColor = StarlitColors.Surface,
            unfocusedContainerColor = StarlitColors.Surface,
        ),
    )
}

@Composable
fun AdminEmpty(text: String) {
    Text(text, color = StarlitColors.TextDim, fontSize = 13.sp)
}

/** A divider-like row separator used inside dense lists. */
@Composable
fun AdminRowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(StarlitColors.Border),
    )
}

/** Interaction source helper to avoid ripple on custom rows. */
@Composable
fun rememberNoRipple(): MutableInteractionSource = androidx.compose.runtime.remember { MutableInteractionSource() }
