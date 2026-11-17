package com.shenliao.deepchat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 夜色 + 电光蓝。整套界面只走深色。 */
object AppColors {
    val Bg = Color(0xFF070A11)
    val Surface = Color(0xFF10151F)
    val SurfaceHigh = Color(0xFF171F2D)
    val Border = Color(0xFF232C3D)
    val Primary = Color(0xFF5B8DFF)
    val PrimaryDim = Color(0xFF20304F)
    val Accent = Color(0xFF8B7CFF)
    val TextMain = Color(0xFFEDF2FA)
    val TextDim = Color(0xFF93A1B6)
    val TextFaint = Color(0xFF63718A)
    val CodeBg = Color(0xFF0B1220)
    val Danger = Color(0xFFFF6B6B)
    val BubbleUser = Color(0xFF22375E)
}

private val DeepColors = darkColorScheme(
    primary = AppColors.Primary,
    onPrimary = Color(0xFF061223),
    primaryContainer = AppColors.BubbleUser,
    onPrimaryContainer = AppColors.TextMain,
    secondary = AppColors.Accent,
    onSecondary = Color(0xFF0B0A18),
    secondaryContainer = Color(0xFF241F44),
    onSecondaryContainer = AppColors.TextMain,
    tertiary = Color(0xFF4FD1C5),
    background = AppColors.Bg,
    onBackground = AppColors.TextMain,
    surface = AppColors.Surface,
    onSurface = AppColors.TextMain,
    surfaceVariant = AppColors.SurfaceHigh,
    onSurfaceVariant = AppColors.TextDim,
    outline = AppColors.Border,
    outlineVariant = AppColors.Border,
    error = AppColors.Danger,
    onError = Color(0xFF1A0505),
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

@Composable
fun DeepChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DeepColors,
        typography = DeepTypography,
        content = content,
    )
}
