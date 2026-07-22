package ru.starlitmoon.launcher.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.starlitmoon.launcher.ui.theme.StarlitAccentGradient
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.ui.theme.StarlitDimens
import kotlin.random.Random

@Composable
fun StarlitBackground(content: @Composable BoxScope.() -> Unit) {
    val stars = remember {
        List(72) {
            Star(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                r = Random.nextFloat() * 1.4f + 0.3f,
                phase = Random.nextFloat() * 6.28f,
                speed = Random.nextFloat() * 1.2f + 0.5f,
            )
        }
    }
    val transition = rememberInfiniteTransition(label = "stars")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Restart),
        label = "starPhase",
    )
    val moonPulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Reverse),
        label = "moonPulse",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        StarlitColors.Void,
                        StarlitColors.BgDeep,
                        Color(0xFF0E1428),
                        StarlitColors.Void,
                    ),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            stars.forEach { star ->
                val alpha = 0.2f + 0.35f * kotlin.math.sin(t * star.speed + star.phase).toFloat().let {
                    (it + 1f) / 2f
                }
                drawCircle(
                    color = Color(0xFFE8ECF8).copy(alpha = alpha),
                    radius = star.r,
                    center = Offset(star.x * size.width, star.y * size.height),
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 60.dp, y = (-40).dp)
                .size(340.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            StarlitColors.Violet.copy(alpha = 0.14f * moonPulse),
                            Color.Transparent,
                        ),
                    ),
                    CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-80).dp, y = 60.dp)
                .size(380.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            StarlitColors.Gold.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                    CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 20.dp, y = 80.dp)
                .size(260.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            StarlitColors.VioletSoft,
                            Color.Transparent,
                        ),
                    ),
                    CircleShape,
                ),
        )

        MoonDecoration(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 32.dp, end = 48.dp)
                .size(72.dp),
            pulse = moonPulse,
        )

        content()
    }
}

@Composable
private fun MoonDecoration(modifier: Modifier = Modifier, pulse: Float) {
    val moonShadow = Color(0xFFD8D0C4)
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f * 0.76f * pulse
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = StarlitColors.Violet.copy(alpha = 0.12f), radius = r * 1.4f, center = c)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(StarlitColors.Moon, moonShadow),
                center = c,
                radius = r,
            ),
            radius = r,
            center = c,
        )
        drawCircle(
            color = StarlitColors.Void.copy(alpha = 0.15f),
            radius = r,
            center = Offset(c.x + r * 0.26f, c.y - r * 0.06f),
        )
        drawCircle(Color(0xFFC8C0B0).copy(alpha = 0.35f), r * 0.1f, Offset(c.x - r * 0.22f, c.y - r * 0.12f))
        drawCircle(Color(0xFFC8C0B0).copy(alpha = 0.28f), r * 0.07f, Offset(c.x + r * 0.08f, c.y + r * 0.2f))
        drawCircle(
            color = StarlitColors.Gold.copy(alpha = 0.18f),
            radius = r * 1.12f,
            center = c,
            style = Stroke(width = 1f),
        )
    }
}

private data class Star(val x: Float, val y: Float, val r: Float, val phase: Float, val speed: Float)

@Composable
fun StarlitCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(StarlitDimens.Radius))
            .border(1.dp, StarlitColors.Stroke, RoundedCornerShape(StarlitDimens.Radius)),
        color = StarlitColors.Glass,
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
            .height(52.dp)
            .clip(shape)
            .background(
                if (active) {
                    StarlitAccentGradient
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            StarlitColors.Gold.copy(alpha = 0.35f),
                            StarlitColors.GoldDeep.copy(alpha = 0.35f),
                        ),
                    )
                },
            )
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
                modifier = Modifier.size(22.dp),
                color = StarlitColors.Void,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = text,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                letterSpacing = 0.8.sp,
                color = if (active) StarlitColors.Void else StarlitColors.Void.copy(alpha = 0.55f),
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
            .height(52.dp)
            .clip(shape)
            .background(StarlitColors.Glass)
            .border(1.dp, StarlitColors.Stroke, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            letterSpacing = 0.4.sp,
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
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (isPassword) {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        shape = RoundedCornerShape(StarlitDimens.Radius),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = StarlitColors.Text,
            unfocusedTextColor = StarlitColors.Text,
            focusedBorderColor = StarlitColors.Violet,
            unfocusedBorderColor = StarlitColors.Stroke,
            focusedLabelColor = StarlitColors.Gold,
            unfocusedLabelColor = StarlitColors.TextMuted,
            cursorColor = StarlitColors.Gold,
            focusedContainerColor = StarlitColors.Glass,
            unfocusedContainerColor = StarlitColors.Glass.copy(alpha = 0.6f),
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
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
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
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(StarlitColors.BgElevated),
        ) {
            if (progress != null) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                listOf(StarlitColors.Gold, StarlitColors.GoldDeep, StarlitColors.Violet),
                            ),
                        ),
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
            .size(width = 50.dp, height = 28.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (checked) StarlitColors.GoldSoft else StarlitColors.BgElevated,
            )
            .border(
                1.dp,
                if (checked) StarlitColors.Gold.copy(alpha = 0.55f) else StarlitColors.Stroke,
                RoundedCornerShape(50),
            )
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .clip(CircleShape)
                .background(
                    if (checked) StarlitColors.Gold else StarlitColors.TextDim,
                ),
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
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(StarlitColors.VioletSoft)
                    .border(1.dp, StarlitColors.StrokeSoft, RoundedCornerShape(12.dp))
                    .padding(9.dp),
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
                fontWeight = FontWeight.SemiBold,
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
fun BrandMark(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(StarlitAccentGradient),
            contentAlignment = Alignment.Center,
        ) {
            Text("★", color = StarlitColors.Void, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Starlit",
            color = StarlitColors.Text,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            letterSpacing = 0.5.sp,
        )
        Text(
            "Moon",
            color = StarlitColors.Gold,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
        )
    }
}
