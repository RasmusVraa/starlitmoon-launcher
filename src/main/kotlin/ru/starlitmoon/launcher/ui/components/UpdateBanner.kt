package ru.starlitmoon.launcher.ui.components

import androidx.compose.runtime.Composable
import ru.starlitmoon.launcher.update.UpdateInfo

/** Kept for compatibility; prefer [UpdateOverlay]. */
@Composable
fun UpdateBanner(
    update: UpdateInfo,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    UpdateOverlay(update, onDownload, onDismiss)
}
