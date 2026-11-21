package com.example.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DotMatrixFontFamily
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily

@Composable
fun NothingDotMatrixText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = 20.sp,
    letterSpacing: TextUnit = 1.4.sp,
    maxLines: Int = 1
) {
    Text(
        text = text,
        color = color,
        fontFamily = DotMatrixFontFamily,
        fontSize = fontSize,
        letterSpacing = letterSpacing,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun NothingDotPatternStrip(
    modifier: Modifier = Modifier,
    dotColor: Color = NothingTheme.colors.dotMatrixColor,
    dotRadiusDp: Dp = 1.3.dp,
    spacingDp: Dp = 7.dp
) {
    Canvas(modifier = modifier) {
        val spacing = spacingDp.toPx().coerceAtLeast(4f)
        val radius = dotRadiusDp.toPx().coerceAtLeast(1f)
        var y = radius
        while (y < size.height) {
            var x = radius
            while (x < size.width) {
                drawCircle(color = dotColor, radius = radius, center = Offset(x, y))
                x += spacing
            }
            y += spacing
        }
    }
}

@Composable
fun NothingHairlineCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(14.dp),
    containerColor: Color = NothingTheme.colors.surface,
    borderColor: Color = NothingTheme.colors.border,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = modifier.fillMaxWidth()
    val border = BorderStroke(1.dp, borderColor)
    if (onClick != null) {
        val view = LocalView.current
        Surface(
            onClick = {
                TaskzHaptics.tap(view)
                onClick()
            },
            shape = shape,
            color = containerColor,
            border = border,
            modifier = cardModifier
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(
            shape = shape,
            color = containerColor,
            border = border,
            modifier = cardModifier
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun NothingDivider(
    modifier: Modifier = Modifier,
    color: Color = NothingTheme.colors.borderSubtle
) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = color)
}

@Composable
fun NothingStatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun NothingTagChip(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = NothingTheme.colors.surfaceElevated,
    contentColor: Color = NothingTheme.colors.textPrimary,
    borderColor: Color = NothingTheme.colors.border,
    mono: Boolean = true
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = if (mono) TechMonoFontFamily else MaterialTheme.typography.labelMedium.fontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun NothingSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    useDotMatrix: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = if (useDotMatrix) {
                MaterialTheme.typography.labelMedium.copy(
                    fontFamily = DotMatrixFontFamily,
                    letterSpacing = 1.4.sp,
                    fontSize = 12.sp
                )
            } else {
                MaterialTheme.typography.labelSmall.copy(
                    fontFamily = TechMonoFontFamily,
                    letterSpacing = 1.1.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            color = NothingTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!trailingText.isNullOrBlank() && onTrailingClick != null) {
            TextButton(
                onClick = onTrailingClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.heightIn(min = 36.dp)
            ) {
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = TechMonoFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun NothingStatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    statusDotColor: Color? = null,
    onClick: (() -> Unit)? = null
) {
    NothingHairlineCard(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NothingDotMatrixText(
                text = value,
                fontSize = 24.sp,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (statusDotColor != null) {
                NothingStatusDot(color = statusDotColor, size = 7.dp)
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = TechMonoFontFamily,
                fontSize = 10.sp,
                letterSpacing = 0.7.sp
            ),
            color = NothingTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun NothingSegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "segmented_tab"
) {
    if (options.isEmpty()) return
    val clampedIndex = selectedIndex.coerceIn(0, options.lastIndex)
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = NothingTheme.colors.surfaceElevated,
        border = BorderStroke(1.dp, NothingTheme.colors.border),
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp)
        ) {
            val segmentWidth = maxWidth / options.size
            val indicatorOffset by animateDpAsState(
                targetValue = segmentWidth * clampedIndex,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "nothing_segmented_offset"
            )
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(NothingTheme.colors.accentPrimary)
            )
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                options.forEachIndexed { idx, label ->
                    val selected = idx == clampedIndex
                    val textColor by animateColorAsState(
                        targetValue = if (selected) NothingTheme.colors.onAccentPrimary else NothingTheme.colors.textSecondary,
                        label = "nothing_segmented_text_$idx"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { onSelect(idx) }
                            .testTag("${tagPrefix}_$idx"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = textColor,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NothingSearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = NothingTheme.colors.surface,
        border = BorderStroke(1.dp, NothingTheme.colors.border),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = NothingTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NothingTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
                )
            }
            if (value.isNotEmpty()) {
                IconButton(
                    onClick = { onValueChange("") },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = NothingTheme.colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NothingEmptyState(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    NothingHairlineCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NothingTheme.colors.textSecondary,
                    modifier = Modifier.size(26.dp)
                )
            }
            NothingDotMatrixText(
                text = title.uppercase(),
                fontSize = 15.sp,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NothingTheme.colors.textSecondary
                )
            }
        }
    }
}

data class NothingNavDestination(
    val id: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun NothingFloatingNavCapsule(
    leftItems: List<NothingNavDestination>,
    rightItems: List<NothingNavDestination>,
    selectedId: String,
    isActionSheetOpen: Boolean,
    onSelect: (NothingNavDestination) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val plusRotation by animateFloatAsState(
        targetValue = if (isActionSheetOpen) 45f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "nav_plus_rotation"
    )
    val plusInteraction = remember { MutableInteractionSource() }
    val plusPressed by plusInteraction.collectIsPressedAsState()
    val plusScale by animateFloatAsState(
        targetValue = if (plusPressed) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "nav_plus_scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = NothingTheme.colors.capsuleNavBg,
            border = BorderStroke(1.dp, NothingTheme.colors.capsuleNavBorder),
            shadowElevation = 8.dp,
            modifier = Modifier
                .widthIn(max = 540.dp)
                .fillMaxWidth()
        ) {
            val view = LocalView.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                leftItems.forEach { dest ->
                    CapsuleNavSlot(
                        dest = dest,
                        selected = selectedId == dest.id,
                        onClick = {
                            TaskzHaptics.tap(view)
                            onSelect(dest)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Central Create (+) Circle Button
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(48.dp)
                        .scale(plusScale)
                        .clip(CircleShape)
                        .background(NothingTheme.colors.accentPrimary)
                        .border(1.dp, NothingTheme.colors.border, CircleShape)
                        .clickable(
                            interactionSource = plusInteraction,
                            indication = null,
                            onClick = {
                                TaskzHaptics.tap(view)
                                onCreateClick()
                            }
                        )
                        .testTag("nav_fab_create_center"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Quick Actions",
                        tint = NothingTheme.colors.onAccentPrimary,
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(plusRotation)
                    )
                }

                rightItems.forEach { dest ->
                    CapsuleNavSlot(
                        dest = dest,
                        selected = selectedId == dest.id,
                        onClick = {
                            TaskzHaptics.tap(view)
                            onSelect(dest)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CapsuleNavSlot(
    dest: NothingNavDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) NothingTheme.colors.capsuleNavActivePill else Color.Transparent,
        label = "nav_slot_bg_${dest.id}"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) NothingTheme.colors.capsuleNavActiveContent else NothingTheme.colors.capsuleNavInactiveContent,
        label = "nav_slot_fg_${dest.id}"
    )

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .testTag(dest.testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                contentDescription = dest.label,
                tint = contentColor,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = dest.label,
                color = contentColor,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

data class NothingQuickAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val testTag: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NothingCreateActionSheet(
    actions: List<NothingQuickAction>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NothingTheme.colors.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NothingSectionHeader(title = "QUICK ACTIONS", useDotMatrix = true)
            Spacer(modifier = Modifier.height(2.dp))
            actions.forEach { action ->
                NothingHairlineCard(
                    onClick = {
                        onDismiss()
                        action.onClick()
                    },
                    shape = RoundedCornerShape(12.dp),
                    containerColor = NothingTheme.colors.surfaceElevated,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    modifier = Modifier.testTag(action.testTag)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(NothingTheme.colors.surface)
                                .border(1.dp, NothingTheme.colors.border, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.title,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = action.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = action.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = NothingTheme.colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
