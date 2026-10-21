package com.eelan.musclediary.domain

/** 13 个肌群：id 用于数据库与模板映射，view 决定画在正面图还是背面图 */
enum class Muscle(val id: String, val label: String, val labelEn: String, val views: Set<Side>) {
    CHEST("chest", "胸", "Chest", setOf(Side.FRONT)),
    SHOULDERS("shoulders", "肩", "Shoulders", setOf(Side.FRONT)),
    BICEPS("biceps", "肱二头", "Biceps", setOf(Side.FRONT)),
    FOREARMS("forearms", "前臂", "Forearms", setOf(Side.FRONT, Side.BACK)),
    ABS("abs", "腹", "Abs", setOf(Side.FRONT)),
    QUADS("quads", "股四头", "Quads", setOf(Side.FRONT)),
    CALVES("calves", "小腿", "Calves", setOf(Side.FRONT, Side.BACK)),
    TRAPS("traps", "斜方肌", "Traps", setOf(Side.BACK)),
    LATS("lats", "背阔肌", "Lats", setOf(Side.BACK)),
    LOWER_BACK("lower_back", "下背", "Lower back", setOf(Side.BACK)),
    TRICEPS("triceps", "肱三头", "Triceps", setOf(Side.BACK)),
    GLUTES("glutes", "臀", "Glutes", setOf(Side.BACK)),
    HAMSTRINGS("hamstrings", "腘绳肌", "Hamstrings", setOf(Side.BACK)),
    ;

    companion object {
        val byId: Map<String, Muscle> = entries.associateBy { it.id }
        /** 按当前语言返回肌群名；未知 id 原样返回 */
        fun labelOf(id: String): String = byId[id]?.let { if (com.eelan.musclediary.Lang.isEn) it.labelEn else it.label } ?: id
    }
}

enum class Side { FRONT, BACK }
