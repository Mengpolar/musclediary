package com.eelan.musclediary.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.eelan.musclediary.domain.Muscle

/**
 * 人体肌肉图绘制引擎：消费 [BodySvg] 的 path 数据（坐标空间 535x462，正背面同一坐标系）。
 * 命中判定与绘制同源——都是解析后的 [SvgShape]，所见即所点。
 * path 数据仅用 M/C/Z 绝对坐标命令，解析器按需实现（无需完整 SVG 栈）。
 */

/** 一条解析好的 path：绘制用缓存 Path，命中用离散多边形（贝塞尔 8 段线性化 + 射线法） */
class SvgShape(val d: String) {
    val path: Path = parse(d)
    private val poly: List<Offset> = flatten(d)

    fun contains(p: Offset): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val y1 = poly[i].y; val y2 = poly[j].y
            if ((y1 > p.y) != (y2 > p.y) &&
                p.x < (poly[j].x - poly[i].x) * (p.y - y1) / (y2 - y1) + poly[i].x
            ) inside = !inside
            j = i
        }
        return inside
    }

    private fun flatten(d: String): List<Offset> {
        val pts = mutableListOf<Offset>()
        var cx = 0f; var cy = 0f
        tokens(d).forEach { (cmd, nums) ->
            when (cmd) {
                'M' -> { cx = nums[0]; cy = nums[1]; pts.add(Offset(cx, cy)) }
                'C' -> {
                    var i = 0
                    while (i + 5 < nums.size) {
                        val x0 = cx; val y0 = cy
                        for (s in 1..8) {
                            val t = s / 8f; val u = 1 - t
                            pts.add(Offset(
                                u*u*u*x0 + 3*u*u*t*nums[i] + 3*u*t*t*nums[i+2] + t*t*t*nums[i+4],
                                u*u*u*y0 + 3*u*u*t*nums[i+1] + 3*u*t*t*nums[i+3] + t*t*t*nums[i+5]))
                        }
                        cx = nums[i + 4]; cy = nums[i + 5]; i += 6
                    }
                }
            }
        }
        return pts
    }

    companion object {
        private val cache = HashMap<String, SvgShape>()

        /** 数据量约 80 条且组合态固定：全局缓存避免重复解析 */
        fun of(d: String): SvgShape = cache.getOrPut(d) { SvgShape(d) }

        private fun tokens(d: String): List<Pair<Char, FloatArray>> {
            val out = mutableListOf<Pair<Char, FloatArray>>()
            val re = Regex("([MCZ])([^MCZ]*)")
            val num = Regex("-?\\d*\\.?\\d+")
            re.findAll(d).forEach { m ->
                out += m.groupValues[1][0] to num.findAll(m.groupValues[2].replace(",", " "))
                    .map { it.value.toFloat() }.toList().toFloatArray()
            }
            return out
        }

        private fun parse(d: String): Path {
            val p = Path()
            tokens(d).forEach { (cmd, nums) ->
                when (cmd) {
                    'M' -> p.moveTo(nums[0], nums[1])
                    'C' -> {
                        var i = 0
                        while (i + 5 < nums.size) {
                            p.cubicTo(nums[i], nums[i + 1], nums[i + 2], nums[i + 3], nums[i + 4], nums[i + 5])
                            i += 6
                        }
                    }
                    'Z' -> p.close()
                }
            }
            return p
        }
    }
}

/** 模型→屏幕的统一变换：scale + translate（正/背面通过视口 x 偏移切半） */
class SvgView(val unit: Float, val ox: Float, val oy: Float) {
    fun px(x: Float) = ox + x * unit
    fun py(y: Float) = oy + y * unit
}

/**
 * 视口计算：单一坐标系 535x462（正面人形在左半、背面在右半），各视图取自己那半。
 * front 视口 = x[0,267.5]，back 视口 = x[267.5,535]，等比缩放居中到给定画布。
 * back 视口的模型原点在 x=267.5，由 [SvgView] 的 ox 直接减去该偏移折算。
 */
fun svgView(canvasW: Float, canvasH: Float, side: com.eelan.musclediary.domain.Side): SvgView {
    val viewW = 535f / 2
    val viewH = 462f
    val unit = minOf(canvasW / viewW, canvasH / viewH)
    val shift = if (side == com.eelan.musclediary.domain.Side.BACK) viewW * unit else 0f
    val ox = (canvasW - viewW * unit) / 2f - shift
    val oy = (canvasH - viewH * unit) / 2f
    return SvgView(unit, ox, oy)
}

// —— 绘制辅助 ——

fun DrawScope.drawSvg(shape: SvgShape, v: SvgView, color: Color) {
    drawPath(shape.transformed(v), color, style = Fill)
}

fun DrawScope.drawSvgOutline(shape: SvgShape, v: SvgView, color: Color, width: Float) {
    drawPath(shape.transformed(v), color, style = Stroke(width))
}

/** 缓存变换后的 path（同一 shape 在同尺寸画布上重复绘制） */
private var tfCacheKey: Pair<SvgShape, Pair<Float, Float>>? = null
private var tfCachePath: Path? = null

private fun SvgShape.transformed(v: SvgView): Path {
    val key = this to (v.unit to v.ox)
    if (tfCacheKey == key && tfCachePath != null) return tfCachePath!!
    val m = androidx.compose.ui.graphics.Matrix().apply {
        scale(v.unit, v.unit, 1f)
        translate(v.ox, v.oy, 0f)
    }
    val out = Path()
    out.addPath(path)
    out.transform(m)
    tfCacheKey = key
    tfCachePath = out
    return out
}
