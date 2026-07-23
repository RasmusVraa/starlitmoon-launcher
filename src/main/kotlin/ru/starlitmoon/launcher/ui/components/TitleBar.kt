package ru.starlitmoon.launcher.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import org.jetbrains.skia.Image
import ru.starlitmoon.launcher.LauncherVersion
import ru.starlitmoon.launcher.ui.theme.StarlitColors

/** Custom title bar integrated into the dark launcher chrome (no system light bar). */
private val TitleBarBg = Color(0xFF0A0C12)
private val TitleBarHover = Color(0x22FFFFFF)
private val TitleBarCloseHover = Color(0xFFE81123)

@Composable
fun FrameWindowScope.StarlitTitleBar(
    windowState: WindowState,
    onClose: () -> Unit,
) {
    val iconPainter = remember {
        runCatching {
            val bytes = object {}.javaClass.getResourceAsStream("/icon.png")?.readBytes() ?: return@remember null
            BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
        }.getOrNull()
    }
    WindowDraggableArea {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(TitleBarBg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(12.dp))
            if (iconPainter != null) {
                Image(
                    painter = iconPainter,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                "StarlitMoon Launcher v${LauncherVersion.CURRENT}",
                color = StarlitColors.TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            TitleBarButton(onClick = { window.isMinimized = true }) {
                Icon(Icons.Default.Remove, null, tint = StarlitColors.TextMuted, modifier = Modifier.size(16.dp))
            }
            TitleBarButton(
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
            TitleBarButton(onClick = onClose, danger = true) {
                Icon(Icons.Default.Close, null, tint = StarlitColors.TextMuted, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun TitleBarButton(
    onClick: () -> Unit,
    danger: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(46.dp, 36.dp)
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
