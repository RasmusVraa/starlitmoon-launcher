package ru.starlitmoon.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.starlitmoon.launcher.ui.theme.StarlitColors
import ru.starlitmoon.launcher.ui.theme.StarlitDimens

@Composable
fun StarlitBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                Brush.verticalGradient(
                    listOf(
                        StarlitColors.BgDeep,
                        Color(0xFF12182A),
                        StarlitColors.BgDeep,
                    ),
                ),
            ),
    ) {
        content()
    }
}

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
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(StarlitDimens.Radius),
        colors = ButtonDefaults.buttonColors(
            containerColor = StarlitColors.Accent,
            contentColor = StarlitColors.BgDeep,
            disabledContainerColor = StarlitColors.Accent.copy(alpha = 0.4f),
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = StarlitColors.BgDeep,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text, fontWeight = FontWeight.Bold)
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
        Text(text)
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
            .padding(bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                color = if (isError) StarlitColors.Offline else StarlitColors.Online,
                style = MaterialTheme.typography.bodyMedium,
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
        Text(label, color = StarlitColors.TextMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (online != null) {
                StatusDot(online, modifier = Modifier.padding(end = 8.dp))
            }
            Text(value, color = StarlitColors.Text, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, color = StarlitColors.Text)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
