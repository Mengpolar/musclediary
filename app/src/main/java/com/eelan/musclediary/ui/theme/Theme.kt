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

@Composable
fun MuscleDiaryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
