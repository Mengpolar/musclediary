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
    /** 命令流只解析一次，绘制 / 命中 / 屏幕变换共用 */
    internal val cmds: List<Pair<Char, FloatArray>> = tokens(d)
    val path: Path = buildPath(cmds)
    private val poly: List<Offset> = flatten(cmds)

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

    private fun flatten(cmds: List<Pair<Char, FloatArray>>): List<Offset> {
        val pts = mutableListOf<Offset>()
        var cx = 0f; var cy = 0f
        cmds.forEach { (cmd, nums) ->
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
    }
}

/** path 数据只含 M/C/Z 绝对坐标：切出命令与参数流（解析/命中/变换共用） */
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

private fun buildPath(cmds: List<Pair<Char, FloatArray>>): Path {
    val p = Path()
    cmds.forEach { (cmd, nums) ->
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

/** 模型→屏幕的统一变换：scale + translate（正/背面通过视口 x 偏移切半） */
class SvgView(val unit: Float, val ox: Float, val oy: Float) {
    fun px(x: Float) = ox + x * unit
    fun py(y: Float) = oy + y * unit
}

/**
 * 视口计算：运行时从该侧全部 path 的实际 bounds 求 Union（外扩 6 单位留白），
 * 再等比缩放居中到画布。以数据真实边界为准，数学上保证内容恒在画布内——
 * 无论数据怎么改、屏幕多窄，两具人形各自完整居中，绝不会越界相碰或被裁切。
 * （每侧 shape 集合固定，结果缓存按 side 记忆，只在首帧计算一次。）
 */
private val viewCache = HashMap<com.eelan.musclediary.domain.Side, androidx.compose.ui.geometry.Rect>()

fun svgView(
    canvasW: Float, canvasH: Float,
    side: com.eelan.musclediary.domain.Side,
    shapes: List<SvgShape>,
): SvgView {
    val content = synchronized(viewCache) { viewCache[side] } ?: run {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        shapes.forEach { s ->
            val bb = s.path.getBounds()
            l = minOf(l, bb.left); t = minOf(t, bb.top)
            r = maxOf(r, bb.right); b = maxOf(b, bb.bottom)
        }
        val rect = androidx.compose.ui.geometry.Rect(l - 6f, t - 6f, r + 6f, b + 6f)
        synchronized(viewCache) { viewCache[side] = rect }
        rect
    }
    val unit = minOf(canvasW / content.width, canvasH / content.height)
    val ox = (canvasW - content.width * unit) / 2f - content.left * unit
    val oy = (canvasH - content.height * unit) / 2f - content.top * unit
    return SvgView(unit, ox, oy)
}

// —— 绘制辅助 ——

fun DrawScope.drawSvg(shape: SvgShape, v: SvgView, color: Color, isBack: Boolean = false) {
    drawPath(shape.transformed(v, isBack), color, style = Fill)
}

fun DrawScope.drawSvgOutline(shape: SvgShape, v: SvgView, color: Color, width: Float, isBack: Boolean = false) {
    drawPath(shape.transformed(v, isBack), color, style = Stroke(width))
}

/**
 * 屏幕坐标变换缓存：正/背面各一份，按视口（unit/ox/oy 三者）整体失效。
 * 同一次绘制 pass 内 view 固定，缓存一次填充后，选中肌群呼吸重绘等后续帧
 * 直接复用已变换 path，不再逐帧重走命令流。
 * 变换不依赖 Matrix（scale/translate 组合顺序在不同版本语义有差异），
 * 直接重走 path 命令流逐点计算屏幕坐标：px = ox + x·unit，所见即所得。
 */
private class TransformCache {
    private var unit = Float.NaN
    private var ox = Float.NaN
    private var oy = Float.NaN
    private val paths = HashMap<SvgShape, Path>()

    fun matches(v: SvgView) = unit == v.unit && ox == v.ox && oy == v.oy

    fun reset(v: SvgView) {
        unit = v.unit; ox = v.ox; oy = v.oy
        paths.clear()
    }

    operator fun get(shape: SvgShape): Path? = paths[shape]
    fun put(shape: SvgShape, path: Path) { paths[shape] = path }
}

private val frontTransform = TransformCache()
private val backTransform = TransformCache()

private fun SvgShape.transformed(v: SvgView, isBack: Boolean): Path {
    val cache = if (isBack) backTransform else frontTransform
    if (!cache.matches(v)) cache.reset(v)
    cache[this]?.let { return it }
    val out = Path()
    cmds.forEach { (cmd, nums) ->
        when (cmd) {
            'M' -> out.moveTo(v.px(nums[0]), v.py(nums[1]))
            'C' -> {
                var i = 0
                while (i + 5 < nums.size) {
                    out.cubicTo(
                        v.px(nums[i]), v.py(nums[i + 1]),
                        v.px(nums[i + 2]), v.py(nums[i + 3]),
                        v.px(nums[i + 4]), v.py(nums[i + 5]),
                    )
                    i += 6
                }
            }
            'Z' -> out.close()
        }
    }
    cache.put(this, out)
    return out
}
