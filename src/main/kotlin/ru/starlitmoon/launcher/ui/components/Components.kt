package ru.starlitmoon.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.starlitmoon.launcher.ui.theme.PlayerRanks
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.ui.theme.StarlitDimens
import ru.starlitmoon.launcher.ui.theme.starlitAnimateColor
import ru.starlitmoon.launcher.ui.theme.starlitAnimateDp
import ru.starlitmoon.launcher.ui.theme.starlitAnimateFloat
import ru.starlitmoon.launcher.update.UpdateInfo
import ru.starlitmoon.launcher.viewmodel.LauncherTab
import ru.starlitmoon.launcher.viewmodel.LauncherViewModel

@Composable
fun rememberBrandLogo(): ImageBitmap? = remember {
    runCatching {
        val stream = object {}.javaClass.getResourceAsStream("/icon.png")
            ?: object {}.javaClass.getResourceAsStream("/brand-logo.png")
            ?: error("missing icon")
        org.jetbrains.skia.Image.makeFromEncoded(stream.readBytes()).toComposeImageBitmap()
    }.getOrNull()
}

@Composable
fun StarlitBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StarlitColors.Background),
        content = content,
    )
}

/** Full-bleed screenshot background with dark cinematic overlay. */
@Composable
fun HeroBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val hero = remember {
        runCatching {
            val stream = object {}.javaClass.getResourceAsStream("/hero-bg.png")
                ?: error("missing /hero-bg.png")
            org.jetbrains.skia.Image.makeFromEncoded(stream.readBytes()).toComposeImageBitmap()
        }.getOrNull()
    }
    Box(modifier = modifier.fillMaxSize()) {
        if (hero != null) {
            Image(
                bitmap = hero,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.55f,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xB305060A),
                            Color(0x9907090F),
                            Color(0xE607090F),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xAA07090F),
                            Color.Transparent,
                            Color(0x8807090F),
                        ),
                    ),
                ),
        )
        content()
    }
}

@Composable
fun StarlitCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    val borderColor = if (selected) StarlitColors.Gold else StarlitColors.Border
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(StarlitDimens.Radius))
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(StarlitDimens.Radius)),
        color = StarlitColors.Surface,
        shadowElevation = 0.dp,
        content = content,
    )
}

@Composable
fun StarlitPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    danger: Boolean = false,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(if (compact) StarlitDimens.RadiusSm else StarlitDimens.Radius)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = enabled && !loading
    val base = when {
        danger -> StarlitColors.Offline
        else -> StarlitColors.Gold
    }
    val targetBg = when {
        !active -> base.copy(alpha = 0.32f)
        pressed -> if (danger) Color(0xFFC44D58) else StarlitColors.GoldDim
        hovered -> if (danger) Color(0xFFF07A84) else Color(0xFFE0B85C)
        else -> base
    }
    val bg = starlitAnimateColor(targetBg, label = "primaryBg")
    val fg = when {
        danger -> StarlitColors.Text
        active -> StarlitColors.OnGold
        else -> StarlitColors.OnGold.copy(alpha = 0.55f)
    }
    val scale = starlitAnimateFloat(
        target = when {
            !active -> 1f
            pressed -> 0.97f
            hovered -> 1.02f
            else -> 1f
        },
        durationMs = 140,
        label = "primaryScale",
    )
    val height = if (compact) 40.dp else 50.dp
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = if (compact) 96.dp else 120.dp)
            .height(height)
            .scale(scale)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(bg.copy(alpha = if (active) 1f else 0.9f), bg.copy(alpha = 0.88f)),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = if (active) 0.28f else 0.08f), Color.Transparent),
                ),
                shape = shape,
            )
            .clickable(
                enabled = active,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(if (compact) 18.dp else 22.dp),
                color = fg,
                strokeWidth = 2.dp,
            )
        } else {
            val size = if (compact) 13.sp else 15.sp
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold,
                fontSize = size,
                lineHeight = size,
                letterSpacing = 0.sp,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                color = fg,
                modifier = Modifier.padding(horizontal = if (compact) 12.dp else 16.dp),
            )
        }
    }
}

@Composable
fun StarlitSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(if (compact) StarlitDimens.RadiusSm else StarlitDimens.Radius)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val targetBorder = when {
        !enabled -> StarlitColors.Border
        pressed -> StarlitColors.Gold
        hovered -> StarlitColors.Gold.copy(alpha = 0.85f)
        else -> StarlitColors.BorderStrong
    }
    val targetBg = when {
        !enabled -> StarlitColors.Surface.copy(alpha = 0.55f)
        pressed -> StarlitColors.GoldMuted
        hovered -> StarlitColors.SurfaceElevated
        else -> StarlitColors.SurfaceHover
    }
    val borderColor = starlitAnimateColor(targetBorder, label = "secondaryBorder")
    val bg = starlitAnimateColor(targetBg, label = "secondaryBg")
    val scale = starlitAnimateFloat(
        target = when {
            !enabled -> 1f
            pressed -> 0.97f
            hovered -> 1.015f
            else -> 1f
        },
        durationMs = 140,
        label = "secondaryScale",
    )
    val height = if (compact) 40.dp else 50.dp
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = if (compact) 96.dp else 120.dp)
            .height(height)
            .scale(scale)
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val size = if (compact) 13.sp else 15.sp
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            fontSize = size,
            lineHeight = size,
            letterSpacing = 0.sp,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            color = if (enabled) StarlitColors.Text else StarlitColors.TextMuted,
            modifier = Modifier.padding(horizontal = if (compact) 12.dp else 16.dp),
        )
    }
}

@Composable
fun StarlitIconButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val shape = RoundedCornerShape(StarlitDimens.RadiusSm)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val targetBorder = when {
        !enabled -> StarlitColors.Border
        danger && (hovered || pressed) -> StarlitColors.Offline
        hovered || pressed -> StarlitColors.Gold
        else -> StarlitColors.BorderStrong
    }
    val targetBg = when {
        !enabled -> StarlitColors.Surface.copy(alpha = 0.5f)
        pressed -> if (danger) StarlitColors.Offline.copy(alpha = 0.22f) else StarlitColors.GoldMuted
        hovered -> StarlitColors.SurfaceElevated
        else -> StarlitColors.SurfaceHover
    }
    val border = starlitAnimateColor(targetBorder, label = "iconBorder")
    val bg = starlitAnimateColor(targetBg, label = "iconBg")
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = when {
                !enabled -> StarlitColors.TextMuted
                danger && hovered -> StarlitColors.Offline
                else -> StarlitColors.Text
            },
        )
    }
}

@Composable
fun StarlitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    readOnly: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        readOnly = readOnly,
        visualTransformation = if (isPassword) {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
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
fun StatusDot(online: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (online) StarlitColors.Online else StarlitColors.Offline),
    )
}

@Composable
fun MessageBanner(message: String, isError: Boolean, onDismiss: () -> Unit) {
    StarlitCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                color = if (isError) StarlitColors.Offline else StarlitColors.Online,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            StarlitSecondaryButton(text = "OK", onClick = onDismiss, modifier = Modifier.width(72.dp))
        }
    }
}

@Composable
fun StatRow(label: String, value: String, online: Boolean? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = StarlitColors.TextMuted, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (online != null) {
                StatusDot(online, modifier = Modifier.padding(end = 8.dp))
            }
            Text(value, color = StarlitColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = StarlitColors.Text,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = StarlitColors.TextMuted, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
fun StarlitProgressBar(
    progress: Float?,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = StarlitColors.TextMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            if (progress != null) {
                Text(
                    text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                    color = StarlitColors.Gold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(StarlitColors.Surface),
        ) {
            if (progress != null) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(50))
                        .background(StarlitColors.Gold),
                )
            }
        }
    }
}

@Composable
fun StarlitToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackBg = starlitAnimateColor(
        if (checked) StarlitColors.GoldMuted else StarlitColors.Surface,
        label = "toggleTrack",
    )
    val trackBorder = starlitAnimateColor(
        if (checked) StarlitColors.Gold.copy(alpha = 0.5f) else StarlitColors.Border,
        label = "toggleBorder",
    )
    val thumbColor = starlitAnimateColor(
        if (checked) StarlitColors.Gold else StarlitColors.TextDim,
        label = "toggleThumb",
    )
    val thumbOffset = starlitAnimateDp(
        if (checked) 18.dp else 0.dp,
        durationMs = 180,
        label = "toggleOffset",
    )
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(50))
            .background(trackBg)
            .border(1.dp, trackBorder, RoundedCornerShape(50))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(3.dp),
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(18.dp)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(StarlitDimens.RadiusSm))
                    .background(StarlitColors.Surface)
                    .border(1.dp, StarlitColors.Border, RoundedCornerShape(StarlitDimens.RadiusSm))
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Spacer(Modifier.size(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = StarlitColors.Text,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = StarlitColors.TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val logo = rememberBrandLogo()
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(StarlitDimens.RadiusSm))
            .background(StarlitColors.Surface)
            .border(1.dp, StarlitColors.Border, RoundedCornerShape(StarlitDimens.RadiusSm)),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            Image(
                bitmap = logo,
                contentDescription = "StarlitMoon",
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                "SM",
                color = StarlitColors.Gold,
                fontSize = (size.value * 0.32f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun BrandWordmark(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Starlit",
            color = StarlitColors.Text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
        )
        Text(
            "Moon",
            color = StarlitColors.Gold,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
        )
    }
}

@Composable
fun SidebarNav(vm: LauncherViewModel) {
    Column(
        modifier = Modifier
            .width(StarlitDimens.SidebarWidth)
            .fillMaxHeight()
            .background(Color(0xFF0A0C12))
            .border(width = 1.dp, color = StarlitColors.Border),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(18.dp))

        SidebarIcon(
            icon = Icons.Default.Home,
            selected = vm.currentTab == LauncherTab.Home,
            contentDescription = "Главная",
            onClick = { vm.currentTab = LauncherTab.Home },
        )
        Spacer(Modifier.height(10.dp))
        SidebarIcon(
            icon = Icons.Default.Apps,
            selected = vm.currentTab == LauncherTab.Builds,
            contentDescription = "Сборки",
            onClick = { vm.currentTab = LauncherTab.Builds },
        )
        Spacer(Modifier.height(10.dp))
        SidebarIcon(
            icon = Icons.Default.Person,
            selected = vm.currentTab == LauncherTab.Cabinet,
            contentDescription = "Кабинет",
            onClick = { vm.currentTab = LauncherTab.Cabinet },
        )
        Spacer(Modifier.height(10.dp))
        SidebarIcon(
            icon = Icons.Default.Checkroom,
            selected = vm.currentTab == LauncherTab.Skins,
            contentDescription = "Скины",
            onClick = { vm.currentTab = LauncherTab.Skins },
        )
        if (vm.isAdmin) {
            Spacer(Modifier.height(10.dp))
            SidebarIcon(
                icon = Icons.Default.Security,
                selected = vm.currentTab == LauncherTab.Admin,
                contentDescription = "Админ",
                onClick = { vm.currentTab = LauncherTab.Admin },
            )
        }

        Spacer(Modifier.weight(1f))
        SidebarIcon(
            icon = Icons.Default.Settings,
            selected = vm.currentTab == LauncherTab.Settings,
            contentDescription = "Настройки",
            onClick = { vm.currentTab = LauncherTab.Settings },
        )
        Spacer(Modifier.height(10.dp))
        SidebarIcon(
            icon = Icons.AutoMirrored.Filled.Logout,
            selected = false,
            contentDescription = "Выйти",
            onClick = {
                val ok = javax.swing.JOptionPane.showConfirmDialog(
                    null,
                    "Выйти из аккаунта?",
                    "Выход",
                    javax.swing.JOptionPane.YES_NO_OPTION,
                ) == javax.swing.JOptionPane.YES_OPTION
                if (ok) vm.logout()
            },
            tintOverride = StarlitColors.TextMuted,
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SidebarIcon(
    icon: ImageVector,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    tintOverride: Color? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val selectedAlpha = starlitAnimateFloat(if (selected) 1f else 0f, durationMs = 200, label = "sidebarSel")
    val hoverAlpha = starlitAnimateFloat(
        if (!selected && hovered) 1f else 0f,
        durationMs = 140,
        label = "sidebarHover",
    )
    val iconTint = starlitAnimateColor(
        tintOverride
            ?: when {
                selected -> StarlitColors.Gold
                hovered -> StarlitColors.Text
                else -> StarlitColors.TextMuted
            },
        label = "sidebarTint",
    )
    val scale = starlitAnimateFloat(
        when {
            selected -> 1.04f
            hovered -> 1.06f
            else -> 1f
        },
        durationMs = 160,
        label = "sidebarScale",
    )
    Box(
        modifier = Modifier
            .size(44.dp)
            .scale(scale)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        StarlitColors.Gold.copy(alpha = 0.35f * selectedAlpha),
                        StarlitColors.Purple.copy(alpha = 0.45f * selectedAlpha),
                    ),
                ),
            )
            .background(StarlitColors.SurfaceHover.copy(alpha = 0.55f * hoverAlpha))
            .border(1.dp, StarlitColors.Gold.copy(alpha = 0.55f * selectedAlpha), shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun TopStatusBar(
    vm: LauncherViewModel,
    windowControls: (@Composable () -> Unit)? = null,
    dragModifier: Modifier = Modifier,
) {
    val ranks = PlayerRanks.normalize(
        vm.meData?.cabinet?.player?.ranks.orEmpty(),
    )
    val primaryRank = ranks.firstOrNull()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(dragModifier)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(end = if (windowControls != null) 0.dp else 20.dp),
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    vm.userName.ifBlank { "Игрок" },
                    color = StarlitColors.Text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                if (primaryRank != null) {
                    Text(
                        primaryRank.labelRu,
                        color = primaryRank.foreground,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(
                        "Игрок",
                        color = StarlitColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            if (vm.activeSkinPath != null) {
                LocalSkinFace(
                    skinPath = vm.activeSkinPath,
                    fallbackName = vm.userName,
                    size = 36.dp,
                    revision = vm.avatarRevision,
                )
            } else {
                NetworkAvatar(
                    url = vm.avatarUrl(size = 64) + "&r=${vm.avatarRevision}",
                    fallbackName = vm.userName,
                    size = 36.dp,
                )
            }
            windowControls?.invoke()
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    online: Boolean,
    emphasize: Boolean = false,
) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(StarlitColors.Surface)
            .border(1.dp, StarlitColors.Border, RoundedCornerShape(50))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(online)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = if (emphasize) StarlitColors.Text else StarlitColors.TextMuted,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
@Composable
fun UpdateOverlay(
    update: UpdateInfo,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    applying: Boolean = false,
    progressLabel: String? = null,
    progressFraction: Float? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StarlitColors.OverlayScrim)
            .clickable(
                enabled = !applying,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        StarlitCard(
            modifier = Modifier
                .width(420.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (applying) "Обновление лаунчера" else "Доступно обновление",
                    color = StarlitColors.Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Text(
                    "Версия ${update.latestVersion} (сейчас ${update.currentVersion})",
                    color = StarlitColors.TextMuted,
                    fontSize = 14.sp,
                )
                if (!applying && update.releaseNotes.isNotBlank()) {
                    Text(
                        update.releaseNotes.lines().take(6).joinToString("\n"),
                        color = StarlitColors.Text,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
                if (applying) {
                    StarlitProgressBar(
                        progress = progressFraction,
                        label = progressLabel ?: "Загрузка…",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StarlitPrimaryButton(
                        text = if (applying) "Обновление…" else "Обновить",
                        onClick = onDownload,
                        modifier = Modifier.width(160.dp),
                        loading = applying,
                        enabled = !applying,
                    )
                    StarlitSecondaryButton(
                        text = "Позже",
                        onClick = onDismiss,
                        modifier = Modifier.width(120.dp),
                        enabled = !applying,
                    )
                }
            }
        }
    }
}

@Composable
fun NetworkAvatar(
    url: String,
    fallbackName: String,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember(url) { mutableStateOf(true) }

    LaunchedEffect(url) {
        loading = true
        bitmap = null
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = java.net.URI(url).toURL().openStream().use { it.readBytes() }
                org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
            }.getOrNull()
        }
        bitmap = loaded
        loading = false
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(StarlitDimens.RadiusSm))
            .background(StarlitColors.Surface)
            .border(1.dp, StarlitColors.Border, RoundedCornerShape(StarlitDimens.RadiusSm)),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        when {
            bmp != null -> Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            loading -> CircularProgressIndicator(
                modifier = Modifier.size(size / 4),
                color = StarlitColors.Gold,
                strokeWidth = 2.dp,
            )
            else -> Text(
                fallbackName.trim().take(1).uppercase().ifBlank { "?" },
                color = StarlitColors.Gold,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.32f).sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
