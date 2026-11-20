package com.xiaogou.serverseyes.core.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class AppColorTokens(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val border: Color,
    val borderStrong: Color,
    val actionPrimary: Color,
    val onActionPrimary: Color,
    val actionSecondary: Color,
    val focus: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val error: Color,
    val errorSoft: Color,
    val info: Color,
    val infoSoft: Color,
    val navBackground: Color
)

/**
 * 方案 A · Ops Precision 语义化色板（浅色/深色独立校准，满足 WCAG AA 对比度）
 */
val LightAppColors = AppColorTokens(
    isDark = false,
    background = Color(0xFFF3F6FA),
    surface = Color(0xFFFFFFFF),
    surfaceSubtle = Color(0xFFE9EEF5),
    textPrimary = Color(0xFF111827),
    textSecondary = Color(0xFF4B5565),
    textDisabled = Color(0xFF8C96A6),
    border = Color(0xFFDCE3ED),
    borderStrong = Color(0xFFB8C4D4),
    actionPrimary = Color(0xFF2563EB),
    onActionPrimary = Color(0xFFFFFFFF),
    actionSecondary = Color(0xFFE9EEF5),
    focus = Color(0xFF1D4ED8),
    success = Color(0xFF047857),
    successSoft = Color(0x1F059669),
    warning = Color(0xFFB45309),
    warningSoft = Color(0x1FD97706),
    error = Color(0xFFB91C1C),
    errorSoft = Color(0x1FDC2626),
    info = Color(0xFF1D4ED8),
    infoSoft = Color(0x1F2563EB),
    navBackground = Color(0xFFFFFFFF)
)

val DarkAppColors = AppColorTokens(
    isDark = true,
    background = Color(0xFF0D131A),
    surface = Color(0xFF151D27),
    surfaceSubtle = Color(0xFF1D2734),
    textPrimary = Color(0xFFEEF2F7),
    textSecondary = Color(0xFF9AA8BC),
    textDisabled = Color(0xFF637082),
    border = Color(0xFF263344),
    borderStrong = Color(0xFF3A4C63),
    actionPrimary = Color(0xFF5B93FF),
    onActionPrimary = Color(0xFF091326),
    actionSecondary = Color(0xFF1D2734),
    focus = Color(0xFF93B8FF),
    success = Color(0xFF34D399),
    successSoft = Color(0x2634D399),
    warning = Color(0xFFFBBF24),
    warningSoft = Color(0x26FBBF24),
    error = Color(0xFFF87171),
    errorSoft = Color(0x29F87171),
    info = Color(0xFF60A5FA),
    infoSoft = Color(0x2660A5FA),
    navBackground = Color(0xFF121922)
)
