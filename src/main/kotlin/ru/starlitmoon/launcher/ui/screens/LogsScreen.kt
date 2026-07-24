package ru.starlitmoon.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.starlitmoon.launcher.ui.components.StarlitCard
import ru.starlitmoon.launcher.ui.components.StarlitSecondaryButton
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel

@Composable
fun LogsScreen(vm: LauncherViewModel) {
    LaunchedEffect(Unit) { vm.refreshLogs() }

    LaunchedEffect(vm.logsSubTab) {
        if (vm.logsSubTab != 1) return@LaunchedEffect
        while (true) {
            delay(3_000)
            vm.refreshLogs()
        }
    }

    val logText = if (vm.logsSubTab == 0) vm.launcherLogText else vm.gameLogText
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Логи",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = StarlitColors.Text,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogsTabChip("Лаунчер", selected = vm.logsSubTab == 0) { vm.logsSubTab = 0 }
            LogsTabChip("Игра", selected = vm.logsSubTab == 1) { vm.logsSubTab = 1 }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StarlitSecondaryButton(
                    text = "Обновить",
                    onClick = { vm.refreshLogs() },
                    compact = true,
                )
                if (vm.logsSubTab == 0) {
                    StarlitSecondaryButton(
                        text = "Очистить",
                        onClick = { vm.clearLauncherLog() },
                        compact = true,
                        danger = true,
                    )
                }
                StarlitSecondaryButton(
                    text = "Открыть папку",
                    onClick = { vm.openLogsFolder() },
                    compact = true,
                )
            }
        }

        StarlitCard(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
                    .verticalScroll(scroll),
            ) {
                Text(
                    text = logText.ifBlank { "Лог пуст" },
                    color = StarlitColors.TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun LogsTabChip(title: String, selected: Boolean, onClick: () -> Unit) {
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
            title,
            color = if (selected) StarlitColors.Gold else StarlitColors.TextMuted,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
