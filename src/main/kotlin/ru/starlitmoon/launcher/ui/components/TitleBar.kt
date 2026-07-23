package ru.starlitmoon.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import ru.starlitmoon.launcher.ui.theme.StarlitColors

private val TitleBarHover = Color(0x22FFFFFF)
private val TitleBarCloseHover = Color(0xFFE81123)

/** Window controls only — no icon / title text (integrated into app chrome). */
@Composable
fun FrameWindowScope.WindowControlButtons(
    windowState: WindowState,
    onClose: () -> Unit,
) {
    Row {
        WindowChromeButton(onClick = { window.isMinimized = true }) {
            Icon(Icons.Default.Remove, null, tint = StarlitColors.TextMuted, modifier = Modifier.size(16.dp))
        }
        WindowChromeButton(
            onClick = {
                windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                    WindowPlacement.Floating
                } else {
                    WindowPlacement.Maximized
                }
            },
        ) {
            Icon(
                imageVector = if (windowState.placement == WindowPlacement.Maximized) {
                    Icons.Default.FilterNone
                } else {
                    Icons.Default.CropSquare
                },
                contentDescription = null,
                tint = StarlitColors.TextMuted,
                modifier = Modifier.size(13.dp),
            )
        }
        WindowChromeButton(onClick = onClose, danger = true) {
            Icon(Icons.Default.Close, null, tint = StarlitColors.TextMuted, modifier = Modifier.size(15.dp))
        }
    }
}

/** Thin drag strip with window buttons for login / non-chrome screens. */
@Composable
fun FrameWindowScope.IntegratedChromeBar(
    windowState: WindowState,
    onClose: () -> Unit,
) {
    WindowDraggableArea {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(Color(0xFF0A0C12)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            WindowControlButtons(windowState = windowState, onClose = onClose)
        }
    }
}

@Composable
private fun WindowChromeButton(
    onClick: () -> Unit,
    danger: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(46.dp, 40.dp)
            .background(
                when {
                    hovered && danger -> TitleBarCloseHover
                    hovered -> TitleBarHover
                    else -> Color.Transparent
                },
            )
            .hoverable(interaction)
            .clickable(indication = null, interactionSource = interaction, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
