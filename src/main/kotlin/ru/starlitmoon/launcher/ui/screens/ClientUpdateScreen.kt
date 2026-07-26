package ru.starlitmoon.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.viewmodel.ClientUpdateLabels
import ru.starlitmoon.launcher.viewmodel.ClientUpdateProgress

/** Accent — pastel purple (brand Gold token). */
private val UpdateAccent = StarlitColors.Gold
private val TrackColor = StarlitColors.BorderStrong
private val PanelBg = Color(0xFF101218)

@Composable
fun ClientUpdateScreen(
    progress: ClientUpdateProgress,
    paused: Boolean = false,
    onPauseToggle: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PanelBg)
            .padding(horizontal = 36.dp, vertical = 28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                progress.title,
                color = StarlitColors.Text,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (paused) "Пауза" else progress.status,
                        color = StarlitColors.Text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        progress.stageCaption,
                        color = StarlitColors.TextMuted,
                        fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = UpdateAccent,
                    strokeWidth = 2.dp,
                    trackColor = TrackColor,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        if (onPauseToggle != null || onCancel != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onPauseToggle != null) {
                    ru.starlitmoon.launcher.ui.components.StarlitSecondaryButton(
                        text = if (paused) "Продолжить" else "Пауза",
                        onClick = onPauseToggle,
                        compact = true,
                    )
                }
                if (onCancel != null) {
                    ru.starlitmoon.launcher.ui.components.StarlitSecondaryButton(
                        text = "Отменить",
                        onClick = onCancel,
                        compact = true,
                        danger = true,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        Text(
            progress.detail,
            color = StarlitColors.TextMuted,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            UpdateProgressBar(
                title = "Общий прогресс",
                percent = progress.overallPercent,
                value = progress.overall,
                fill = UpdateAccent,
                modifier = Modifier.weight(1f),
            )
            UpdateProgressBar(
                title = "Текущий этап · ${progress.status}",
                percent = progress.stagePercent,
                value = progress.stageProgress,
                fill = StarlitColors.Text.copy(alpha = 0.75f),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier.height(28.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF16181F))
                .padding(vertical = 18.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val downloadedLabel = buildString {
                append(ClientUpdateLabels.formatBytes(progress.downloadedBytes))
                append(" / ")
                append(progress.totalBytes?.let { ClientUpdateLabels.formatBytes(it) } ?: "—")
            }
            val filesLabel = buildString {
                append(progress.filesDone)
                append(" / ")
                append(progress.filesTotal?.toString() ?: "—")
            }
            StatCell(Icons.AutoMirrored.Filled.InsertDriveFile, "СКАЧАНО", downloadedLabel, Modifier.weight(1f))
            StatDivider()
            StatCell(
                Icons.Default.Speed,
                "СКОРОСТЬ",
                progress.speedBps?.let { ClientUpdateLabels.formatSpeed(it) } ?: "—",
                Modifier.weight(1f),
            )
            StatDivider()
            StatCell(Icons.Default.Sync, "ОСТАЛОСЬ", progress.remainingLabel, Modifier.weight(1f))
            StatDivider()
            StatCell(Icons.Default.FolderCopy, "ФАЙЛЫ", filesLabel, Modifier.weight(1f))
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(end = 24.dp),
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = StarlitColors.TextMuted,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "ТЕКУЩИЙ ФАЙЛ",
                        color = StarlitColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                    )
                    Text(
                        progress.currentFile.ifBlank { "ожидание…" },
                        color = StarlitColors.TextMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "АКТИВНЫЕ ПОТОКИ",
                    color = StarlitColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                )
                Text(
                    progress.activeThreads.toString(),
                    color = StarlitColors.Text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun UpdateProgressBar(
    title: String,
    percent: Int,
    value: Float,
    fill: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, color = StarlitColors.TextMuted, fontSize = 12.sp)
            Text(
                "$percent%",
                color = StarlitColors.Text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(TrackColor),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(50))
                    .background(fill),
            )
        }
    }
}

@Composable
private fun StatCell(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E222B)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = StarlitColors.TextMuted, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(
                label,
                color = StarlitColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            )
            Text(
                value,
                color = StarlitColors.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(StarlitColors.Border),
    )
}
