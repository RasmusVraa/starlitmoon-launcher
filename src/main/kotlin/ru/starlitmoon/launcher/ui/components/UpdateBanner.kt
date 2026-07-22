package ru.starlitmoon.launcher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.update.UpdateInfo

@Composable
fun UpdateBanner(
    update: UpdateInfo,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    StarlitCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Доступно обновление ${update.latestVersion}",
                color = StarlitColors.Accent,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Установлена версия ${update.currentVersion}",
                color = StarlitColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (update.releaseNotes.isNotBlank()) {
                Text(
                    update.releaseNotes.lines().take(4).joinToString("\n"),
                    color = StarlitColors.Text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StarlitPrimaryButton(
                    text = if (update.installerUrl != null) "Скачать установщик" else "Открыть релиз",
                    onClick = onDownload,
                )
                StarlitSecondaryButton(text = "Позже", onClick = onDismiss)
            }
        }
    }
}
