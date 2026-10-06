package com.eelan.musclediary.domain

/** 13 个肌群：id 用于数据库与模板映射，view 决定画在正面图还是背面图 */
enum class Muscle(val id: String, val label: String, val views: Set<Side>) {
    CHEST("chest", "胸", setOf(Side.FRONT)),
    SHOULDERS("shoulders", "肩", setOf(Side.FRONT)),
    BICEPS("biceps", "肱二头", setOf(Side.FRONT)),
    FOREARMS("forearms", "前臂", setOf(Side.FRONT, Side.BACK)),
    ABS("abs", "腹", setOf(Side.FRONT)),
    QUADS("quads", "股四头", setOf(Side.FRONT)),
    CALVES("calves", "小腿", setOf(Side.FRONT, Side.BACK)),
    TRAPS("traps", "斜方肌", setOf(Side.BACK)),
    LATS("lats", "背阔肌", setOf(Side.BACK)),
    LOWER_BACK("lower_back", "下背", setOf(Side.BACK)),
    TRICEPS("triceps", "肱三头", setOf(Side.BACK)),
    GLUTES("glutes", "臀", setOf(Side.BACK)),
    HAMSTRINGS("hamstrings", "腘绳肌", setOf(Side.BACK)),
    ;

    companion object {
        val byId: Map<String, Muscle> = entries.associateBy { it.id }
        fun labelOf(id: String): String = byId[id]?.label ?: id
    }
}

enum class Side { FRONT, BACK }
