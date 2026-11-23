package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GlassStrokeBright
import com.example.ui.theme.GlassStrokeSubtle
import com.example.ui.theme.GlassStrokeViolet
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LuminousPurple
import com.example.ui.theme.MidnightDeep
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.MidnightViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun LiquidGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bg_orb_drift")
    val drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(MidnightDeep, MidnightNavy, MidnightViolet, MidnightDeep),
                    start = Offset.Zero,
                    end = Offset(1200f, 2200f)
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ElectricCyan.copy(alpha = 0.22f),
                        ElectricCyan.copy(alpha = 0f)
                    ),
                    center = Offset(w * (0.18f + 0.08f * drift), h * (0.14f + 0.04f * drift)),
                    radius = w * 0.72f
                ),
                center = Offset(w * (0.18f + 0.08f * drift), h * (0.14f + 0.04f * drift)),
                radius = w * 0.72f
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        LuminousPurple.copy(alpha = 0.26f),
                        LuminousPurple.copy(alpha = 0f)
                    ),
                    center = Offset(w * (0.86f - 0.10f * drift), h * (0.42f + 0.06f * drift)),
                    radius = w * 0.80f
                ),
                center = Offset(w * (0.86f - 0.10f * drift), h * (0.42f + 0.06f * drift)),
                radius = w * 0.80f
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        IndigoAccent.copy(alpha = 0.20f),
                        IndigoAccent.copy(alpha = 0f)
                    ),
                    center = Offset(w * 0.35f, h * (0.88f - 0.05f * drift)),
                    radius = w * 0.75f
                ),
                center = Offset(w * 0.35f, h * (0.88f - 0.05f * drift)),
                radius = w * 0.75f
            )
        }
        content()
    }
}

@Composable
fun LiquidGlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 24,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            GlassStrokeBright,
            GlassStrokeViolet,
            GlassStrokeSubtle
        )
    )
    val surfaceBrush = Brush.linearGradient(
        colors = listOf(
            GlassSurfaceElevated.copy(alpha = 0.68f),
            GlassSurface.copy(alpha = 0.54f)
        )
    )

    Column(
        modifier = modifier
            .clip(shape)
            .background(surfaceBrush)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun GradientPrimaryButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    testTag: String,
    modifier: Modifier = Modifier,
    gradientColors: List<Color> = listOf(ElectricCyan, IndigoAccent, LuminousPurple)
) {
    val shape = RoundedCornerShape(16.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = TextPrimary,
            disabledContainerColor = GlassSurfaceElevated.copy(alpha = 0.4f),
            disabledContentColor = TextMuted
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(50.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = if (enabled) {
                        Brush.horizontalGradient(gradientColors)
                    } else {
                        Brush.horizontalGradient(
                            listOf(
                                GlassSurfaceElevated.copy(alpha = 0.5f),
                                GlassSurfaceElevated.copy(alpha = 0.5f)
                            )
                        )
                    },
                    shape = shape
                )
                .border(
                    width = 1.dp,
                    color = if (enabled) Color.White.copy(alpha = 0.30f) else GlassStrokeSubtle,
                    shape = shape
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun GlassActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    accentColor: Color,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = if (enabled) accentColor.copy(alpha = 0.16f) else GlassSurface.copy(alpha = 0.35f),
        border = BorderStroke(
            1.dp,
            if (enabled) accentColor.copy(alpha = 0.55f) else GlassStrokeSubtle
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(50.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) accentColor else TextMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) TextPrimary else TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MetricGlassTile(
    label: String,
    value: String,
    accentColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(GlassSurfaceElevated.copy(alpha = 0.55f))
            .border(1.dp, accentColor.copy(alpha = 0.28f), shape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold
            ),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DurationStepperRow(
    label: String,
    valueText: String,
    presets: List<Pair<String, Int>>,
    currentValue: Int,
    enabled: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onPresetSelected: (Int) -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = TextSecondary
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onDecrement,
                    enabled = enabled,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GlassSurfaceElevated.copy(alpha = 0.65f))
                        .border(1.dp, GlassStrokeSubtle, CircleShape)
                        .testTag("${testTagPrefix}_dec")
                        .semantics { contentDescription = "Decrease $label" }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = null,
                        tint = if (enabled) TextPrimary else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MidnightDeep.copy(alpha = 0.65f))
                        .border(1.dp, GlassStrokeViolet, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = valueText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = ElectricCyan
                    )
                }

                IconButton(
                    onClick = onIncrement,
                    enabled = enabled,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GlassSurfaceElevated.copy(alpha = 0.65f))
                        .border(1.dp, GlassStrokeSubtle, CircleShape)
                        .testTag("${testTagPrefix}_inc")
                        .semantics { contentDescription = "Increase $label" }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = if (enabled) TextPrimary else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            presets.forEach { (chipLabel, chipVal) ->
                val selected = currentValue == chipVal
                val chipShape = RoundedCornerShape(10.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(chipShape)
                        .background(
                            if (selected) ElectricCyan.copy(alpha = 0.22f)
                            else MidnightDeep.copy(alpha = 0.45f)
                        )
                        .border(
                            1.dp,
                            if (selected) ElectricCyan.copy(alpha = 0.65f) else GlassStrokeSubtle,
                            chipShape
                        )
                        .clickable(enabled = enabled) { onPresetSelected(chipVal) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = chipLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) ElectricCyan else TextMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
