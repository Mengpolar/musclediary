package com.eelan.musclediary.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.eelan.musclediary.domain.Muscle

/**
 * 人体肌肉图素材层：SVG path 数据（M/L/C/Q/Z 绝对坐标）+ 解析 + 绘制 + 命中判定。
 * 模型坐标 120×240（y 向下）。左右对称的形状只在数据里定义左半，[ArtShape.mirrored]
 * 为 true 时自动补画/判右半（x → 120-x）。肌肉命中的多边形采样与绘制 path 同源，保证所见即所点。
 */

// —— 造型：曲线化人体（圆肩、鼓胸、收腰、外凸小腿肚，全部贝塞尔弧）——

private const val HEAD_D = "M60,5 C66.5,5 70,10.5 70,17 C70,24 65.5,30 60,30 C54.5,30 50,24 50,17 C50,10.5 53.5,5 60,5 Z"
private const val NECK_D = "M54.5,27 C55,31 52,33.5 45,35 L75,35 C68,33.5 65,31 65.5,27 C63.5,29 56.5,29 54.5,27 Z"
private const val TORSO_D = "M46,35 C38,37 33,40.5 32,46 C31,54 34.5,62 35.5,70 C36.5,80 38.5,88 42.5,96 C45.5,102 49,105.5 52,107.5 L68,107.5 C71,105.5 74.5,102 77.5,96 C81.5,88 83.5,80 84.5,70 C85.5,62 89,54 88,46 C87,40.5 82,37 74,35 C70.5,44 66,46.5 60,46.5 C54,46.5 49.5,44 46,35 Z"
private const val ARM_D = "M33.5,39.5 C29.5,42.5 27.5,48 26.5,54 C25.5,62 23.5,72 21.5,82 C19.8,91 18,99 16.2,103.5 C17,107.5 19.5,109.5 22.5,109 C25.5,108.5 26.8,105.5 27.8,101 C29.5,91 31.5,81 33.5,71 C35.2,63 36.3,55 37.2,49.5 C38,44.5 36,40.5 33.5,39.5 Z"
private const val LEG_D = "M45.5,106 C41.5,114 39.5,124 39.8,134 C40,144 42,152 44,158 C45,166 45.2,172 44.2,178 C43.2,186 41.4,194 40.4,200 C39.5,206 38.6,210 38.6,213.5 C40.5,215.5 44.5,215.5 46.4,213.5 C47.3,207.5 48.3,199.5 49.3,191.5 C50.3,183.5 51.3,175.5 52.3,167.5 C53.2,159.5 54,151.5 54.8,143.5 L65.2,143.5 C66,151.5 66.8,159.5 67.7,167.5 C68.7,175.5 69.7,183.5 70.7,191.5 C71.7,199.5 72.7,207.5 73.6,213.5 C75.5,215.5 79.5,215.5 81.4,213.5 C81.4,210 80.5,206 79.6,200 C78.6,194 76.8,186 75.8,178 C74.8,172 75,166 76,158 C78,152 80,144 80.2,134 C80.5,124 78.5,114 74.5,106 C66,112.5 54,112.5 45.5,106 Z"

// —— 正面肌群（左半）——
private const val CHEST_D = "M45.5,37 C52,35.8 58.2,37.5 59,44.5 L59,51.5 C59,54.8 55.8,56.8 51.8,55.8 C46.8,54.6 43.6,50.5 43,45 C42.6,41.2 43.2,37.9 45.5,37 Z"
private const val SHOULDER_D = "M35,37.5 C31.5,39.2 30,43.5 31.2,47.5 C32.2,50.8 35.5,52.2 38.5,51 C41.5,49.8 43.2,46.5 42.8,43 C42.4,39.8 39.5,37.2 36.8,37.2 C36.2,37.2 35.6,37.3 35,37.5 Z"
private const val BICEPS_D = "M31.5,54.5 C28.8,58.5 27.3,64.5 27.4,70.5 C27.5,75.5 28.8,79.5 30.8,81.3 C32.8,83 35.6,81.8 36.4,78.8 C37.4,73.8 37.7,67.8 37.1,62.2 C36.5,58 34.2,53.6 31.5,54.5 Z"
private const val FOREARM_D = "M27.2,80 C25.4,86 23.6,93 21.8,99 C20.2,103 19,106 18.2,108.2 C20.2,110.4 23.2,110.4 25.2,108.4 C27,104.4 29,97.4 30,90.4 C30.7,86.2 29.4,79.3 27.2,80 Z"
private const val ABS_D = "M52,55.5 C56.5,54.3 63.5,54.3 68,55.5 C69.2,61.5 69.2,67.5 68,73.5 C67,78.5 64,81.8 60,81.8 C56,81.8 53,78.5 52,73.5 C50.8,67.5 50.8,61.5 52,55.5 Z"
private const val ABS_LINES_D = "M60,55.5 L60,81 M53,63.5 L67,63.5 M52.5,71 L67.5,71"
private const val QUADS_D = "M46,109.5 C50,112 55.5,113.2 59,112.2 L58.2,140 C55.2,142 50,141 47.2,138 C44.2,130 44.2,117.5 46,109.5 Z"
private const val CALF_D = "M45.2,150.5 C48,152.6 53,153.4 55,151.4 C55.6,158.4 54,166.4 52,172.4 C51,176.4 49.2,179.4 47.2,179.2 C45.2,178.6 44.2,172.6 44.2,164.6 C44.2,159.4 44.6,154.5 45.2,150.5 Z"

// —— 背面肌群（左半）——
private const val TRAPS_D = "M45.5,36 C52,39.5 56.5,44 58.5,50.5 L51.5,52.5 C48.5,47.5 46.5,42 45.5,36 Z"
private const val LATS_D = "M43,41.5 C48.5,45.5 52.5,51.5 53.2,59.5 C53.6,65.5 52.2,71.5 50.2,75.5 C47,71.5 44.2,63.5 43.2,55.5 C42.7,49.5 42.3,44.5 43,41.5 Z"
private const val TRICEPS_D = BICEPS_D
private const val LOWER_BACK_D = "M52.5,79.5 C55.5,78.7 64.5,78.7 67.5,79.5 C66.7,83.5 64.7,86.7 62.7,88 L57.3,88 C55.3,86.7 53.3,83.5 52.5,79.5 Z"
private const val GLUTES_D = "M45.8,94 C51,91.8 57,93.8 59,99.8 C59.2,104.8 56,107.8 52,107.8 C48,107.8 45,104.8 45,99.8 C45,97.6 45.2,95.4 45.8,94 Z"
private const val HAMSTRINGS_D = QUADS_D
private const val CALF_BACK_D = CALF_D

// —— 形状注册表：绘制顺序即 z 序（底图 → 肌群 → 装饰线）——

/** 底图（头/颈/躯干/臂/腿） */
val artBody: List<ArtShape> = listOf(
    ArtShape(null, HEAD_D, mirrored = false),
    ArtShape(null, NECK_D, mirrored = false),
    ArtShape(null, TORSO_D, mirrored = false),
    ArtShape(null, ARM_D, mirrored = true),
    ArtShape(null, LEG_D, mirrored = false),
)

/** 正面肌群 */
val artFront: List<ArtShape> = listOf(
    ArtShape(Muscle.CHEST, CHEST_D),
    ArtShape(Muscle.SHOULDERS, SHOULDER_D),
    ArtShape(Muscle.BICEPS, BICEPS_D),
    ArtShape(Muscle.FOREARMS, FOREARM_D),
    ArtShape(Muscle.ABS, ABS_D, mirrored = false),
    ArtShape(Muscle.QUADS, QUADS_D),
    ArtShape(Muscle.CALVES, CALF_D),
)

/** 正面装饰线（腹肌分割，不参与命中） */
val artFrontDeco: List<ArtShape> = listOf(
    ArtShape(null, ABS_LINES_D, mirrored = false),
)

/** 背面肌群 */
val artBack: List<ArtShape> = listOf(
    ArtShape(Muscle.TRAPS, TRAPS_D, mirrored = false),
    ArtShape(Muscle.LATS, LATS_D),
    ArtShape(Muscle.TRICEPS, TRICEPS_D),
    ArtShape(Muscle.LOWER_BACK, LOWER_BACK_D, mirrored = false),
    ArtShape(Muscle.GLUTES, GLUTES_D),
    ArtShape(Muscle.HAMSTRINGS, HAMSTRINGS_D),
    ArtShape(Muscle.CALVES, CALF_BACK_D),
)

/** 一条 path 的离散化多边形（贝塞尔按 8 段线性化），命中判定与绘制同源 */
private class Poly(d: String) {
    val points: List<Offset> = run {
        val pts = mutableListOf<Offset>()
        var cx = 0f; var cy = 0f; var sx = 0f; var sy = 0f
        Regex("([MLCQZ])([^MLCQZ]*)").findAll(d.replace(",", " ")).forEach { m ->
            val nums = Regex("-?\\d*\\.?\\d+").findAll(m.groupValues[2]).map { it.value.toFloat() }.toList()
            when (m.groupValues[1]) {
                "M" -> { cx = nums[0]; cy = nums[1]; sx = cx; sy = cy; pts.add(Offset(cx, cy)) }
                "L" -> { var i = 0; while (i + 1 < nums.size) { cx = nums[i]; cy = nums[i + 1]; pts.add(Offset(cx, cy)); i += 2 } }
                "C" -> { var i = 0; while (i + 5 < nums.size) {
                    val x0 = cx; val y0 = cy; val x1 = nums[i]; val y1 = nums[i + 1]
                    val x2 = nums[i + 2]; val y2 = nums[i + 3]; val x3 = nums[i + 4]; val y3 = nums[i + 5]
                    for (s in 1..8) { val t = s / 8f; val u = 1 - t
                        pts.add(Offset(u*u*u*x0 + 3*u*u*t*x1 + 3*u*t*t*x2 + t*t*t*x3,
                                       u*u*u*y0 + 3*u*u*t*y1 + 3*u*t*t*y2 + t*t*t*y3)) }
                    cx = x3; cy = y3; i += 6 } }
                "Q" -> { var i = 0; while (i + 3 < nums.size) {
                    val x0 = cx; val y0 = cy; val x1 = nums[i]; val y1 = nums[i + 1]
                    val x2 = nums[i + 2]; val y2 = nums[i + 3]
                    for (s in 1..8) { val t = s / 8f; val u = 1 - t
                        pts.add(Offset(u*u*x0 + 2*u*t*x1 + t*t*x2, u*u*y0 + 2*u*t*y1 + t*t*y2)) }
                    cx = x2; cy = y2; i += 4 } }
                "Z" -> { if (sx != cx || sy != cy) pts.add(Offset(sx, sy)) }
            }
        }
        pts
    }

    /** 射线法命中 */
    fun hit(mx: Float, my: Float): Boolean {
        var inside = false
        var j = points.size - 1
        for (i in points.indices) {
            val x1 = points[i].x; val y1 = points[i].y
            val x2 = points[j].x; val y2 = points[j].y
            if ((y1 > my) != (y2 > my) && mx < (x2 - x1) * (my - y1) / (y2 - y1) + x1) inside = !inside
            j = i
        }
        return inside
    }
}

/** 可绘制 + 可命中的形状；mirrored=true 时右半自动镜像（x→120-x） */
class ArtShape(
    val muscle: Muscle?,          // null = 底图/装饰
    private val d: String,
    val mirrored: Boolean = true,
) {
    private val poly = Poly(d)

    fun contains(p: Offset): Boolean {
        val left = poly.hit(p.x, p.y)
        return if (mirrored) left || poly.hit(120f - p.x, p.y) else left
    }

    /** 绘制到屏幕坐标（unit = 模型→屏幕缩放，ox/oy = 居中偏移） */
    fun draw(
        scope: DrawScope,
        unit: Float, ox: Float, oy: Float,
        color: Color? = null, brush: Brush? = null,
        outline: Boolean = false, strokeWidth: Float = 2f,
    ) {
        val style = if (outline) Stroke(strokeWidth) else Fill
        if (brush != null) {
            scope.drawPath(screenPath(unit, ox, oy, false), brush, style = style)
            if (mirrored) scope.drawPath(screenPath(unit, ox, oy, true), brush, style = style)
        } else if (color != null) {
            scope.drawPath(screenPath(unit, ox, oy, false), color, style = style)
            if (mirrored) scope.drawPath(screenPath(unit, ox, oy, true), color, style = style)
        }
    }

    /** 模型坐标 → 屏幕坐标 path（每次绘制现算；贝塞尔控制点同步缩放） */
    private fun screenPath(unit: Float, ox: Float, oy: Float, mir: Boolean): Path {
        val out = Path()
        var cx = 0f; var cy = 0f
        Regex("([MLCQZ])([^MLCQZ]*)").findAll(d.replace(",", " ")).forEach { m ->
            val nums = Regex("-?\\d*\\.?\\d+").findAll(m.groupValues[2]).map { it.value.toFloat() }.toList()
            fun px(v: Float) = ox + (if (mir) 120f - v else v) * unit
            fun py(v: Float) = oy + v * unit
            when (m.groupValues[1]) {
                "M" -> { cx = nums[0]; cy = nums[1]; out.moveTo(px(cx), py(cy)) }
                "L" -> { var i = 0; while (i + 1 < nums.size) { cx = nums[i]; cy = nums[i + 1]; out.lineTo(px(cx), py(cy)); i += 2 } }
                "C" -> { var i = 0; while (i + 5 < nums.size) {
                    val x1 = nums[i]; val y1 = nums[i + 1]; val x2 = nums[i + 2]; val y2 = nums[i + 3]
                    val x3 = nums[i + 4]; val y3 = nums[i + 5]
                    out.cubicTo(px(x1), py(y1), px(x2), py(y2), px(x3), py(y3))
                    cx = x3; cy = y3; i += 6 } }
                "Q" -> { var i = 0; while (i + 3 < nums.size) {
                    val x1 = nums[i]; val y1 = nums[i + 1]; val x2 = nums[i + 2]; val y2 = nums[i + 3]
                    out.quadraticBezierTo(px(x1), py(y1), px(x2), py(y2))
                    cx = x2; cy = y2; i += 4 } }
                "Z" -> out.close()
            }
        }
        return out
    }
}
