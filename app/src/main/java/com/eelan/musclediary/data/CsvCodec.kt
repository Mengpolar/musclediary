package com.eelan.musclediary.data

/**
 * CSV 编解码纯函数：备份导出转义 / 导入解析 / 公式注入防护。
 * 抽离为无 Android 依赖的工具对象，导入导出这套风险最高的逻辑可做 JVM 单元测试。
 */
object CsvCodec {

    /** 字段转义：含逗号/引号/换行时加引号，引号翻倍 */
    fun escape(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n'))
            "\"" + s.replace("\"", "\"\"") + "\""
        else s

    /** 防公式注入：名称以 ' = + - @ 开头时加前导单引号（Excel 打开 CSV 不会当公式执行），导入侧对称还原 */
    fun safe(s: String): String =
        if (s.isNotEmpty() && s.first() in "'=+-@") "'$s" else s

    fun unSafe(s: String): String =
        if (s.length > 1 && s[0] == '\'' && s[1] in "'=+-@") s.substring(1) else s

    /** 拆分一行 CSV（支持带引号字段：引号内逗号不分割、双引号转义为单引号） */
    fun parseLine(line: String): List<String> {
        if (!line.contains('"')) return line.split(',')
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuote = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuote && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                c == '"' -> inQuote = !inQuote
                c == ',' && !inQuote -> { out.add(cur.toString()); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out.add(cur.toString())
        return out
    }
}
