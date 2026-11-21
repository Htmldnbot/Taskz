package com.example.ui.common

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily

object TaskzHaptics {
    fun tap(view: View) {
        runCatching {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    fun confirm(view: View) {
        runCatching {
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
            view.performHapticFeedback(code)
        }
    }

    fun warning(view: View) {
        runCatching {
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
            view.performHapticFeedback(code)
        }
    }
}

fun isSystemReducedMotionEnabled(context: Context): Boolean {
    return runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }.getOrDefault(false)
}

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { isSystemReducedMotionEnabled(context) }
}

/**
 * Lightweight Nothing OS + restrained monochrome radial ambient backdrop.
 * Zero continuous infinite frame animations; safe on low-end devices.
 */
@Composable
fun TaskzAmbientBackground(
    modifier: Modifier = Modifier,
    showDotCluster: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = NothingTheme.colors
    val dotColor = colors.dotMatrixColor
    val topHaloColor = if (colors.isDark) Color(0xFFFFFFFF) else Color(0xFF101012)
    val topHaloAlpha = if (colors.isDark) 0.06f else 0.045f
    val bottomHaloAlpha = if (colors.isDark) 0.035f else 0.03f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .drawBehind {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@drawBehind

                // Subtle top-right radial highlight
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            topHaloColor.copy(alpha = topHaloAlpha),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.88f, h * 0.08f),
                        radius = w * 0.65f
                    ),
                    radius = w * 0.65f,
                    center = Offset(w * 0.88f, h * 0.08f)
                )

                // Subtle bottom-left depth balance
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            topHaloColor.copy(alpha = bottomHaloAlpha),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.12f, h * 0.86f),
                        radius = w * 0.55f
                    ),
                    radius = w * 0.55f,
                    center = Offset(w * 0.12f, h * 0.86f)
                )

                // Restrained corner dot matrix (5x6 dots)
                if (showDotCluster) {
                    val step = 10.dp.toPx()
                    val dotRadius = 1.25.dp.toPx()
                    val startX = w - (6 * step) - 14.dp.toPx()
                    val startY = 24.dp.toPx()
                    for (row in 0 until 6) {
                        for (col in 0 until 5) {
                            val fade = 1f - ((row + col) / 14f)
                            drawCircle(
                                color = dotColor.copy(alpha = (0.55f * fade).coerceIn(0.15f, 0.55f)),
                                radius = dotRadius,
                                center = Offset(startX + col * step, startY + row * step)
                            )
                        }
                    }
                }
            },
        content = content
    )
}

/**
 * Restrained translucent glass card with crisp 1dp border and top highlight edge.
 * Maintains strong contrast in both Dark and Light modes without heavy blur overhead.
 */
@Composable
fun TaskzGlassSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    elevated: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = NothingTheme.colors
    val view = LocalView.current
    val fillColor = when {
        elevated -> colors.glassSurfaceElevated
        else -> colors.glassSurface
    }
    val borderBrush = Brush.verticalGradient(
        colors = listOf(
            colors.glassHighlightEdge,
            colors.border
        )
    )
    val border = BorderStroke(1.dp, borderBrush)

    if (onClick != null) {
        Surface(
            onClick = {
                TaskzHaptics.tap(view)
                onClick()
            },
            shape = shape,
            color = fillColor,
            border = border,
            modifier = modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(
            shape = shape,
            color = fillColor,
            border = border,
            modifier = modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun TaskzSettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingBadge: String? = null,
    trailingBadgeColor: Color = NothingTheme.colors.accentPrimary,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    testTag: String = ""
) {
    val colors = NothingTheme.colors
    val view = LocalView.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                TaskzHaptics.tap(view)
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!trailingBadge.isNullOrBlank()) {
            NothingTagChip(
                text = trailingBadge,
                containerColor = trailingBadgeColor.copy(alpha = 0.14f),
                contentColor = trailingBadgeColor,
                borderColor = trailingBadgeColor.copy(alpha = 0.4f),
                modifier = Modifier.padding(end = 6.dp)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun TaskzStatusPill(
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    NothingTagChip(
        text = label.uppercase(),
        containerColor = accentColor.copy(alpha = 0.13f),
        contentColor = accentColor,
        borderColor = accentColor.copy(alpha = 0.4f),
        modifier = modifier
    )
}
