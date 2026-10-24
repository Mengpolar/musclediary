package com.eelan.musclediary.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** CSV 编解码：备份导入导出的转义/解析/公式注入防护往返 */
class CsvCodecTest {

    // —— escape ——

    @Test fun escape_plainFieldStaysBare() {
        assertEquals("鸡胸肉", CsvCodec.escape("鸡胸肉"))
    }

    @Test fun escape_commaGetsQuoted() {
        assertEquals("\"a,b\"", CsvCodec.escape("a,b"))
    }

    @Test fun escape_quoteDoublesInsideQuotes() {
        assertEquals("\"say \"\"hi\"\"\"", CsvCodec.escape("say \"hi\""))
    }

    @Test fun escape_newlineGetsQuoted() {
        assertEquals("\"a\nb\"", CsvCodec.escape("a\nb"))
    }

    // —— parseLine ——

    @Test fun parseLine_simple() {
        assertEquals(listOf("a", "b", "c"), CsvCodec.parseLine("a,b,c"))
    }

    @Test fun parseLine_quotedCommaDoesNotSplit() {
        assertEquals(listOf("a,b", "c"), CsvCodec.parseLine("\"a,b\",c"))
    }

    @Test fun parseLine_doubledQuoteUnescapes() {
        assertEquals(listOf("say \"hi\"", "x"), CsvCodec.parseLine("\"say \"\"hi\"\"\",x"))
    }

    @Test fun parseLine_trailingEmptyField() {
        assertEquals(listOf("a", ""), CsvCodec.parseLine("a,"))
    }

    @Test fun roundTrip_nameWithCommaAndQuotes() {
        val name = "自制,能量\"棒\"(试做)"
        val line = CsvCodec.escape(name)
        assertEquals(name, CsvCodec.parseLine("x,$line,y")[1])
    }

    @Test fun roundTrip_musclesJson() {
        // xentry 尾列的 musclesJson 含引号与逗号，是转义压力最大的字段
        val json = "{\"chest\":12.5,\"abs,core\":8}"
        val line = "xentry,2026-10-24,${CsvCodec.escape(json)},20,0,REPS,7,1.0,${CsvCodec.escape(json)}"
        val p = CsvCodec.parseLine(line)
        assertEquals(json, p[2])
        assertEquals(json, p[8])
    }

    // —— 公式注入防护 ——

    @Test fun safe_prefixesFormulaChars() {
        assertEquals("'=SUM(A1)", CsvCodec.safe("=SUM(A1)"))
        assertEquals("'+1", CsvCodec.safe("+1"))
        assertEquals("'-cmd", CsvCodec.safe("-cmd"))
        assertEquals("'@x", CsvCodec.safe("@x"))
        assertEquals("''", CsvCodec.safe("'"))
    }

    @Test fun safe_leavesNormalNames() {
        assertEquals("牛奶", CsvCodec.safe("牛奶"))
        assertEquals("Oatmeal", CsvCodec.safe("Oatmeal"))
    }

    @Test fun unSafe_restoresPrefixed() {
        assertEquals("=SUM(A1)", CsvCodec.unSafe("'=SUM(A1)"))
        assertEquals("-cmd", CsvCodec.unSafe("'-cmd"))
    }

    @Test fun unSafe_keepsNormalAndApostropheNames() {
        assertEquals("牛奶", CsvCodec.unSafe("牛奶"))
        // 天然以单引号开头但后面不是公式字符：不还原，避免误伤
        assertEquals("'s fine", CsvCodec.unSafe("'s fine"))
    }

    @Test fun roundTrip_formulaInjectionSafe() {
        for (name in listOf("=cmd", "+1", "-2", "@f", "'=x", "正常名")) {
            assertEquals(name, CsvCodec.unSafe(CsvCodec.safe(name)))
        }
    }
}
