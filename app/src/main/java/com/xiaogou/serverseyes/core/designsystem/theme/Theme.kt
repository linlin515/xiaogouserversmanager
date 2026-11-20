package com.xiaogou.serverseyes.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.xiaogou.serverseyes.core.designsystem.tokens.AppColorTokens
import com.xiaogou.serverseyes.core.designsystem.tokens.AppTypographyTokens
import com.xiaogou.serverseyes.core.designsystem.tokens.DarkAppColors
import com.xiaogou.serverseyes.core.designsystem.tokens.LightAppColors
import com.xiaogou.serverseyes.core.model.ThemePreference

val LocalAppColors = staticCompositionLocalOf { LightAppColors }
val LocalAppTypography = staticCompositionLocalOf { AppTypographyTokens() }

object AppTheme {
    val colors: AppColorTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current

    val typography: AppTypographyTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTypography.current
}

@Composable
fun ServersEyesTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDark = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val tokens = if (useDark) DarkAppColors else LightAppColors
    val typo = AppTypographyTokens()

    val materialScheme = if (useDark) {
        darkColorScheme(
            primary = tokens.actionPrimary,
            onPrimary = tokens.onActionPrimary,
            secondary = tokens.info,
            background = tokens.background,
            onBackground = tokens.textPrimary,
            surface = tokens.surface,
            onSurface = tokens.textPrimary,
            surfaceVariant = tokens.surfaceSubtle,
            onSurfaceVariant = tokens.textSecondary,
            outline = tokens.border,
            error = tokens.error
        )
    } else {
        lightColorScheme(
            primary = tokens.actionPrimary,
            onPrimary = tokens.onActionPrimary,
            secondary = tokens.info,
            background = tokens.background,
            onBackground = tokens.textPrimary,
            surface = tokens.surface,
            onSurface = tokens.textPrimary,
            surfaceVariant = tokens.surfaceSubtle,
            onSurfaceVariant = tokens.textSecondary,
            outline = tokens.border,
            error = tokens.error
        )
    }

    val materialTypo = Typography(
        displaySmall = typo.display,
        headlineMedium = typo.headline,
        titleLarge = typo.titleLarge,
        titleMedium = typo.titleMedium,
        titleSmall = typo.titleSmall,
        bodyLarge = typo.bodyLarge,
        bodyMedium = typo.bodyMedium,
        bodySmall = typo.bodySmall,
        labelLarge = typo.labelLarge,
        labelSmall = typo.labelSmall
    )

    CompositionLocalProvider(
        LocalAppColors provides tokens,
        LocalAppTypography provides typo
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = materialTypo,
            content = content
        )
    }
}
