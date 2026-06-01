package moe.ouom.neriplayer.ui.screen.tab.settings.component

/*
 * NeriPlayer - A unified Android player for streaming music and videos from multiple online platforms.
 * Copyright (C) 2025-2025 NeriPlayer developers
 * https://github.com/cwuom/NeriPlayer
 *
 * This software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * File: moe.ouom.neriplayer.ui.screen.tab.settings.component/SettingsCommon
 * Updated: 2026/3/23
 */

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.ZoomInMap
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.util.HapticIconButton
import kotlin.math.abs

internal fun maskCookieValue(value: String): String {
    return when {
        value.length <= 4 -> "***"
        else -> "${value.take(2)}***${value.takeLast(2)}"
    }
}

private val SettingsItemShape = RoundedCornerShape(18.dp)

enum class ThemeMode {
    Auto,
    Light,
    Dark
}

fun resolveThemeMode(
    followSystemDark: Boolean,
    forceDark: Boolean
): ThemeMode {
    return when {
        followSystemDark -> ThemeMode.Auto
        forceDark -> ThemeMode.Dark
        else -> ThemeMode.Light
    }
}

internal fun ThemeMode.next(): ThemeMode {
    return when (this) {
        ThemeMode.Auto -> ThemeMode.Light
        ThemeMode.Light -> ThemeMode.Dark
        ThemeMode.Dark -> ThemeMode.Auto
    }
}

internal fun Modifier.settingsItemClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    return clip(SettingsItemShape).clickable(enabled = enabled, onClick = onClick)
}

/** 可复用的折叠区头部 */
@Composable
internal fun ExpandableHeader(
    icon: ImageVector,
    title: String,
    subtitleCollapsed: String,
    subtitleExpanded: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    arrowRotation: Float = 0f
) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(if (expanded) subtitleExpanded else subtitleCollapsed) },
        trailingContent = {
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = if (expanded) {
                    stringResource(R.string.action_collapse)
                } else {
                    stringResource(R.string.action_expand)
                },
                modifier = Modifier.rotate(arrowRotation.takeIf { it != 0f } ?: if (expanded) 180f else 0f),
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        modifier = Modifier.settingsItemClickable(onClick = onToggle),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
internal fun LazyAnimatedVisibility(
    visible: Boolean,
    enter: EnterTransition = fadeIn() + expandVertically(),
    exit: ExitTransition = fadeOut() + shrinkVertically(),
    content: @Composable () -> Unit
) {
    var restoredVisibleState by rememberSaveable { mutableStateOf(visible) }
    val visibilityState = remember {
        MutableTransitionState(restoredVisibleState)
    }

    LaunchedEffect(visible) {
        visibilityState.targetState = visible
        restoredVisibleState = visible
    }

    if (visibilityState.currentState || visibilityState.targetState) {
        androidx.compose.animation.AnimatedVisibility(
            visibleState = visibilityState,
            enter = enter,
            exit = exit
        ) {
            content()
        }
    }
}

/** 主题色预览行（当关闭系统动态取色时显示） */
@Composable
internal fun ThemeSeedListItem(seedColorHex: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.settingsItemClickable(onClick = onClick),
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.ColorLens,
                contentDescription = stringResource(R.string.settings_theme_color),
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        headlineContent = { Text(stringResource(R.string.settings_theme_color)) },
        supportingContent = { Text(stringResource(R.string.settings_theme_color_desc)) },
        trailingContent = {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(("#$seedColorHex").toColorInt()))
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

/** UI 缩放设置入口 */
@Composable
internal fun UiScaleListItem(currentScale: Float, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.settingsItemClickable(onClick = onClick),
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.ZoomInMap,
                contentDescription = stringResource(R.string.settings_ui_scale),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        headlineContent = { Text(stringResource(R.string.settings_ui_scale_dpi)) },
        supportingContent = {
            Text(stringResource(R.string.settings_ui_scale_current, "%.2f".format(currentScale)))
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
internal fun ThemeModeActionButton(
    isDarkTheme: Boolean,
    onToggleRequest: (Offset, Float) -> Unit
) {
    ThemeModeActionButton(
        themeMode = if (isDarkTheme) ThemeMode.Dark else ThemeMode.Light,
        isDarkTheme = isDarkTheme,
        onModeRequest = { _, originInWindow, startRadiusPx ->
            onToggleRequest(originInWindow, startRadiusPx)
        }
    )
}

@Composable
internal fun ThemeModeActionButton(
    themeMode: ThemeMode,
    isDarkTheme: Boolean,
    onModeRequest: (ThemeMode, Offset, Float) -> Unit
) {
    var centerInWindow by remember { mutableStateOf<Offset?>(null) }
    var revealStartRadiusPx by remember { mutableFloatStateOf(18f) }
    val nextThemeMode = themeMode.next()
    val contentDescription = when (nextThemeMode) {
        ThemeMode.Auto -> stringResource(R.string.settings_theme_toggle_auto)
        ThemeMode.Light -> stringResource(R.string.settings_theme_toggle_light)
        ThemeMode.Dark -> stringResource(R.string.settings_theme_toggle_dark)
    }
    val iconProgress by animateFloatAsState(
        targetValue = when (themeMode) {
            ThemeMode.Light -> 0f
            ThemeMode.Dark -> 1f
            ThemeMode.Auto -> 2f
        },
        animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing),
        label = "theme_toggle_icon_progress"
    )
    val containerColor by animateColorAsState(
        targetValue = when (themeMode) {
            ThemeMode.Auto -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            ThemeMode.Dark -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
            ThemeMode.Light -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        },
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "theme_toggle_container_color"
    )

    fun iconAlpha(target: Float): Float {
        return (1f - abs(iconProgress - target)).coerceIn(0f, 1f)
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(containerColor)
    ) {
        HapticIconButton(
            onClick = {
                centerInWindow?.let {
                    onModeRequest(nextThemeMode, it, revealStartRadiusPx)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    revealStartRadiusPx = maxOf(coordinates.size.width, coordinates.size.height) / 2f
                    centerInWindow = coordinates.positionInWindow() + Offset(
                        x = coordinates.size.width / 2f,
                        y = coordinates.size.height / 2f
                    )
                }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.LightMode,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            alpha = iconAlpha(0f)
                            val scale = 0.56f + alpha * 0.44f
                            scaleX = scale
                            scaleY = scale
                            rotationZ = -56f * (1f - alpha)
                        }
                )
                Icon(
                    imageVector = Icons.Outlined.DarkMode,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            alpha = iconAlpha(1f)
                            val scale = 0.56f + alpha * 0.44f
                            scaleX = scale
                            scaleY = scale
                            rotationZ = 56f * (1f - alpha)
                        }
                )
                Icon(
                    imageVector = Icons.Outlined.BrightnessAuto,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            alpha = iconAlpha(2f)
                            val scale = 0.56f + alpha * 0.44f
                            scaleX = scale
                            scaleY = scale
                            rotationZ = -56f * (1f - alpha)
                        }
                )
            }
        }
    }
}

/** 内嵌提示条 */
@Composable
internal fun InlineMessage(text: String, onClose: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            HapticIconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_close)
                )
            }
        }
    }
}
