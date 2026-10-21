package com.eelan.musclediary

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 应用内语言切换：0 中文 / 1 English。
 * 选择持久化在 Profile.lang；AppViewModel 加载档案与更新档案时同步到本对象。
 * Compose 内通过 [L] 取词——读取 lang 状态，切换后自动重组刷新。
 */
object Lang {
    var lang by androidx.compose.runtime.mutableStateOf(0)
        private set
    val isEn: Boolean get() = lang == 1
    fun set(l: Int) { lang = l }
}

/** 双语取词：按当前语言返回对应文案（在 Composable 中调用，切换语言自动刷新） */
fun L(zh: String, en: String): String = if (Lang.isEn) en else zh

/** 英文月份三字母缩写（日期展示用） */
val MON3 = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** 「X月Y日」按语言格式化 */
fun dMD(month: Int, day: Int): String =
    if (Lang.isEn) "${MON3[month - 1]} $day" else "${month}月${day}日"

/** 「Y年M月」按语言格式化（月份分组头） */
fun dYM(year: Int, month: Int): String =
    if (Lang.isEn) "${MON3[month - 1]} $year" else "${year}年${month}月"

/** 食物标签显示名：库里存的是中文键，展示时按语言映射；未知标签原样返回 */
fun tagLabel(tag: String): String = when (tag) {
    "高质量蛋白" -> L("高质量蛋白", "Protein")
    "优质碳水" -> L("优质碳水", "Carbs")
    "健康脂肪" -> L("健康脂肪", "Fats")
    "果蔬纤维" -> L("果蔬纤维", "Veggies")
    "加工食品" -> L("加工食品", "Processed")
    else -> tag
}
