package com.eelan.musclediary.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 夜间模式配色
val Ink0 = Color(0xFF101216) // 背景
val Ink1 = Color(0xFF171A20) // 卡片
val Ink2 = Color(0xFF23262D) // 次级面
val Ink3 = Color(0xFF3A3F47) // 分隔/描边
val TextHi = Color(0xFFE8EAED)
val TextLo = Color(0xFF9AA0A6)
val Accent = Color(0xFFFF7A45) // 主色（热量环）
val Protein = Color(0xFF4FC3F7) // 蛋白质
val CarbC = Color(0xFFFFD54F)   // 碳水
val FatC = Color(0xFFB39DDB)    // 脂肪
val Good = Color(0xFF66BB6A)
val Warn = Color(0xFFFFB74D)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1A1208),
    secondary = Protein,
    onSecondary = Color(0xFF08131A),
    tertiary = CarbC,
    background = Ink0,
    onBackground = TextHi,
    surface = Ink1,
    onSurface = TextHi,
    surfaceVariant = Ink2,
    onSurfaceVariant = TextLo,
    outline = Ink3,
    error = Color(0xFFEF5350),
)

private val Base = androidx.compose.material3.Typography()

/** 全局数字等宽（tnum）：数值变化时宽度稳定，对齐感更强 */
private fun withTabular(style: androidx.compose.ui.text.TextStyle) =
    style.copy(fontFeatureSettings = "tnum")

private val AppTypography = androidx.compose.material3.Typography(
    displayLarge = withTabular(Base.displayLarge),
    displayMedium = withTabular(Base.displayMedium),
    displaySmall = withTabular(Base.displaySmall),
    headlineLarge = withTabular(Base.headlineLarge),
    headlineMedium = withTabular(Base.headlineMedium),
    headlineSmall = withTabular(Base.headlineSmall),
    titleLarge = withTabular(Base.titleLarge),
    titleMedium = withTabular(Base.titleMedium),
    titleSmall = withTabular(Base.titleSmall),
    bodyLarge = withTabular(Base.bodyLarge),
    bodyMedium = withTabular(Base.bodyMedium),
    bodySmall = withTabular(Base.bodySmall),
    labelLarge = withTabular(Base.labelLarge),
    labelMedium = withTabular(Base.labelMedium),
    labelSmall = withTabular(Base.labelSmall),
)

@Composable
fun MuscleDiaryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
}
