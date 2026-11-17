package com.shenliao.deepchat.ui.math

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.shenliao.deepchat.R

/** 内置的两款数学字体，都是 OFL 许可，可随 App 分发 */
object MathFonts {

    /** STIX Two Math：期刊排版常用，笔画较粗，深色背景上更清楚（默认） */
    val STIX: FontFamily = FontFamily(Font(R.font.stix_two_math))

    /** Latin Modern Math：LaTeX 教科书那套（Computer Modern） */
    val LATIN_MODERN: FontFamily = FontFamily(Font(R.font.latin_modern_math))

    const val KEY_STIX = "stix"
    const val KEY_LATIN_MODERN = "lm"

    fun of(key: String): FontFamily = when (key) {
        KEY_LATIN_MODERN -> LATIN_MODERN
        else -> STIX
    }
}

/** 当前使用的数学字体，由 AppRoot 根据设置提供 */
val LocalMathFont = compositionLocalOf { MathFonts.STIX }
