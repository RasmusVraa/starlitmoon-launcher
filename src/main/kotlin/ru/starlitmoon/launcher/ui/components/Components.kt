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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.ui.theme.StarlitDimens
import kotlin.random.Random

@Composable
fun StarlitBackground(content: @Composable BoxScope.() -> Unit) {
    val stars = remember {
        List(120) {
            Star(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                r = Random.nextFloat() * 1.8f + 0.4f,
                phase = Random.nextFloat() * 6.28f,
                speed = Random.nextFloat() * 1.5f + 0.6f,
            )
        }
    }
    val transition = rememberInfiniteTransition(label = "stars")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "starPhase",
    )
    val moonPulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse),
        label = "moonPulse",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF070B16),
                        StarlitColors.BgDeep,
                        Color(0xFF12142A),
                        Color(0xFF0A0E1A),
                    ),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            stars.forEach { star ->
                val alpha = 0.35f + 0.45f * kotlin.math.sin(t * star.speed + star.phase).toFloat().let {
                    (it + 1f) / 2f
                }
                drawCircle(
                    color = Color(0xFFE8ECF8).copy(alpha = alpha),
                    radius = star.r,
                    center = Offset(star.x * size.width, star.y * size.height),
                )
            }
        }

        // Soft purple/gold glows like site hero
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-20).dp)
                .size(280.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            StarlitColors.Purple.copy(alpha = 0.22f * moonPulse),
                            Color.Transparent,
                        ),
                    ),
                    CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-60).dp, y = 40.dp)
                .size(320.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            StarlitColors.Accent.copy(alpha = 0.12f),
                            Color.Transparent,
                        ),
                    ),
                    CircleShape,
                ),
        )

        MoonDecoration(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 28.dp, end = 36.dp)
                .size(88.dp),
            pulse = moonPulse,
        )

        content()
    }
}

@Composable
private fun MoonDecoration(modifier: Modifier = Modifier, pulse: Float) {
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f * 0.78f * pulse
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = StarlitColors.Purple.copy(alpha = 0.18f), radius = r * 1.35f, center = c)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(StarlitColors.Moon, StarlitColors.MoonShadow),
                center = c,
                radius = r,
            ),
            radius = r,
            center = c,
        )
        drawCircle(
            color = Color(0xFF0A0E1A).copy(alpha = 0.18f),
            radius = r,
            center = Offset(c.x + r * 0.28f, c.y - r * 0.08f),
        )
        // craters
        drawCircle(Color(0xFFC8C0B0).copy(alpha = 0.45f), r * 0.12f, Offset(c.x - r * 0.25f, c.y - r * 0.15f))
        drawCircle(Color(0xFFC8C0B0).copy(alpha = 0.35f), r * 0.08f, Offset(c.x + r * 0.1f, c.y + r * 0.22f))
        drawCircle(Color(0xFFC8C0B0).copy(alpha = 0.3f), r * 0.06f, Offset(c.x - r * 0.05f, c.y + r * 0.05f))
        drawCircle(
            color = StarlitColors.Accent.copy(alpha = 0.25f),
            radius = r * 1.15f,
            center = c,
            style = Stroke(width = 1.5f),
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
            .clip(RoundedCornerShape(StarlitDimens.Radius + 4.dp))
            .border(1.dp, StarlitColors.CardBorder, RoundedCornerShape(StarlitDimens.Radius + 4.dp)),
        color = StarlitColors.BgCard,
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
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(StarlitDimens.Radius),
        colors = ButtonDefaults.buttonColors(
            containerColor = StarlitColors.Accent,
            contentColor = StarlitColors.BgDeep,
            disabledContainerColor = StarlitColors.Accent.copy(alpha = 0.35f),
            disabledContentColor = StarlitColors.BgDeep.copy(alpha = 0.6f),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = StarlitColors.BgDeep,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
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
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(StarlitDimens.Radius),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = StarlitColors.Text),
        border = androidx.compose.foundation.BorderStroke(1.dp, StarlitColors.CardBorder),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
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
            focusedBorderColor = StarlitColors.Purple,
            unfocusedBorderColor = StarlitColors.CardBorder,
            focusedLabelColor = StarlitColors.Accent,
            unfocusedLabelColor = StarlitColors.TextMuted,
            cursorColor = StarlitColors.Accent,
            focusedContainerColor = Color(0x3312182A),
            unfocusedContainerColor = Color(0x2212182A),
        ),
    )
}

@Composable
fun StatusDot(online: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(10.dp)
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
                .padding(horizontal = 14.dp, vertical = 10.dp),
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
            StarlitSecondaryButton(text = "OK", onClick = onDismiss)
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
fun BrandMark(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "★",
            color = StarlitColors.Accent,
            fontSize = 22.sp,
        )
        Text(
            "Starlit",
            color = StarlitColors.Text,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
        )
        Text(
            "Moon",
            color = StarlitColors.Accent,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
    }
}
