package com.shenliao.deepchat.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** 一套配色。字段名和原来的 AppColors 保持一致，方便调用点不改。 */
data class AppPalette(
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val border: Color,
    val primary: Color,
    val primaryDim: Color,
    val accent: Color,
    val textMain: Color,
    val textDim: Color,
    val textFaint: Color,
    val codeBg: Color,
    val codeText: Color,
    val codeAccent: Color,
    val danger: Color,
    val bubbleUser: Color,
    val errorBg: Color,
    val errorText: Color,
)

/** 夜色 + 电光蓝 */
val DarkPalette = AppPalette(
    bg = Color(0xFF070A11),
    surface = Color(0xFF10151F),
    surfaceHigh = Color(0xFF171F2D),
    border = Color(0xFF232C3D),
    primary = Color(0xFF5B8DFF),
    primaryDim = Color(0xFF20304F),
    accent = Color(0xFF8B7CFF),
    textMain = Color(0xFFEDF2FA),
    textDim = Color(0xFF93A1B6),
    textFaint = Color(0xFF63718A),
    codeBg = Color(0xFF0B1220),
    codeText = Color(0xFFD7E3F6),
    codeAccent = Color(0xFF9FD3FF),
    danger = Color(0xFFFF6B6B),
    bubbleUser = Color(0xFF22375E),
    errorBg = Color(0xFF2A1519),
    errorText = Color(0xFFFFB4B4),
)

/** 白天：米白底 + 同一支蓝 */
val LightPalette = AppPalette(
    bg = Color(0xFFF6F7FA),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFF0F3F8),
    border = Color(0xFFE1E6EF),
    primary = Color(0xFF2F6BE0),
    primaryDim = Color(0xFFE7EFFF),
    accent = Color(0xFF6F5BE8),
    textMain = Color(0xFF151A23),
    textDim = Color(0xFF5A6478),
    textFaint = Color(0xFF8B94A6),
    codeBg = Color(0xFFF2F5FA),
    codeText = Color(0xFF24303F),
    codeAccent = Color(0xFF1F5FBF),
    danger = Color(0xFFC83636),
    bubbleUser = Color(0xFFDCE8FF),
    errorBg = Color(0xFFFDECEC),
    errorText = Color(0xFF8E2020),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

/**
 * 全局配色入口。属性都带 @Composable getter，所以原来的 `AppColors.Bg` 写法不用改。
 */
object AppColors {
    val Bg: Color @Composable get() = LocalPalette.current.bg
    val Surface: Color @Composable get() = LocalPalette.current.surface
    val SurfaceHigh: Color @Composable get() = LocalPalette.current.surfaceHigh
    val Border: Color @Composable get() = LocalPalette.current.border
    val Primary: Color @Composable get() = LocalPalette.current.primary
    val PrimaryDim: Color @Composable get() = LocalPalette.current.primaryDim
    val Accent: Color @Composable get() = LocalPalette.current.accent
    val TextMain: Color @Composable get() = LocalPalette.current.textMain
    val TextDim: Color @Composable get() = LocalPalette.current.textDim
    val TextFaint: Color @Composable get() = LocalPalette.current.textFaint
    val CodeBg: Color @Composable get() = LocalPalette.current.codeBg
    val CodeText: Color @Composable get() = LocalPalette.current.codeText
    val CodeAccent: Color @Composable get() = LocalPalette.current.codeAccent
    val Danger: Color @Composable get() = LocalPalette.current.danger
    val BubbleUser: Color @Composable get() = LocalPalette.current.bubbleUser
    val ErrorBg: Color @Composable get() = LocalPalette.current.errorBg
    val ErrorText: Color @Composable get() = LocalPalette.current.errorText
}

private val DarkScheme = darkColorScheme(
    primary = DarkPalette.primary,
    onPrimary = Color(0xFF061223),
    primaryContainer = DarkPalette.bubbleUser,
    onPrimaryContainer = DarkPalette.textMain,
    secondary = DarkPalette.accent,
    onSecondary = Color(0xFF0B0A18),
    secondaryContainer = Color(0xFF241F44),
    onSecondaryContainer = DarkPalette.textMain,
    tertiary = Color(0xFF4FD1C5),
    background = DarkPalette.bg,
    onBackground = DarkPalette.textMain,
    surface = DarkPalette.surface,
    onSurface = DarkPalette.textMain,
    surfaceVariant = DarkPalette.surfaceHigh,
    onSurfaceVariant = DarkPalette.textDim,
    outline = DarkPalette.border,
    outlineVariant = DarkPalette.border,
    error = DarkPalette.danger,
    onError = Color(0xFF1A0505),
)

private val LightScheme = lightColorScheme(
    primary = LightPalette.primary,
    onPrimary = Color.White,
    primaryContainer = LightPalette.bubbleUser,
    onPrimaryContainer = LightPalette.textMain,
    secondary = LightPalette.accent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FF),
    onSecondaryContainer = LightPalette.textMain,
    tertiary = Color(0xFF12897F),
    background = LightPalette.bg,
    onBackground = LightPalette.textMain,
    surface = LightPalette.surface,
    onSurface = LightPalette.textMain,
    surfaceVariant = LightPalette.surfaceHigh,
    onSurfaceVariant = LightPalette.textDim,
    outline = LightPalette.border,
    outlineVariant = LightPalette.border,
    error = LightPalette.danger,
    onError = Color.White,
)

private val DeepTypography = Typography(
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

const val THEME_SYSTEM = "system"
const val THEME_LIGHT = "light"
const val THEME_DARK = "dark"

/** mode: system / light / dark */
@Composable
fun DeepChatTheme(mode: String = THEME_SYSTEM, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        THEME_LIGHT -> false
        THEME_DARK -> true
        else -> systemDark
    }
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) DarkScheme else LightScheme

    val view = LocalView.current
    val context = LocalContext.current
    SideEffect {
        val window = (context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = DeepTypography, content = content)
    }
}
