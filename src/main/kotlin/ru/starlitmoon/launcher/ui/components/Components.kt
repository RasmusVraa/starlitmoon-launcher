package ru.starlitmoon.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.ui.theme.StarlitDimens

/** Solid, calm backdrop. No stars, no nebula, no motion. */
@Composable
fun StarlitBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StarlitColors.Background),
        content = content,
    )
}

@Composable
fun StarlitCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(StarlitDimens.Radius))
            .border(1.dp, StarlitColors.Border, RoundedCornerShape(StarlitDimens.Radius)),
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
) {
    val shape = RoundedCornerShape(StarlitDimens.Radius)
    val active = enabled && !loading
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(if (active) StarlitColors.Gold else StarlitColors.Gold.copy(alpha = 0.35f))
            .clickable(
                enabled = active,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = StarlitColors.OnGold,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                letterSpacing = 0.4.sp,
                color = if (active) StarlitColors.OnGold else StarlitColors.OnGold.copy(alpha = 0.6f),
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
) {
    val shape = RoundedCornerShape(StarlitDimens.Radius)
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(StarlitColors.Surface)
            .border(1.dp, StarlitColors.Border, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            letterSpacing = 0.2.sp,
            color = if (enabled) StarlitColors.Text else StarlitColors.TextMuted,
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
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(50))
            .background(if (checked) StarlitColors.GoldMuted else StarlitColors.Surface)
            .border(
                1.dp,
                if (checked) StarlitColors.Gold.copy(alpha = 0.5f) else StarlitColors.Border,
                RoundedCornerShape(50),
            )
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .clip(CircleShape)
                .background(if (checked) StarlitColors.Gold else StarlitColors.TextDim),
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
    val icon = remember {
        runCatching {
            val stream = object {}.javaClass.getResourceAsStream("/icon.png")
                ?: error("missing /icon.png")
            org.jetbrains.skia.Image.makeFromEncoded(stream.readBytes()).toComposeImageBitmap()
        }.getOrNull()
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(StarlitDimens.RadiusSm))
            .background(StarlitColors.Surface)
            .border(1.dp, StarlitColors.Border, RoundedCornerShape(StarlitDimens.RadiusSm)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = "StarlitMoon",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
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

/**
 * Loads an avatar image from [url] on a background thread and renders it.
 * Falls back to a flat placeholder box with the first letter of [fallbackName] on failure.
 */
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
