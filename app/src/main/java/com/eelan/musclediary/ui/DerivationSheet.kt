package com.eelan.musclediary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eelan.musclediary.L
import com.eelan.musclediary.data.Profile
import com.eelan.musclediary.domain.Calc
import com.eelan.musclediary.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 点击目标数值后弹出的计算推导面板：
 * 将真实数据代入公式，按「身体档案 → 热量目标 4 步推导 → 三大营养素分摊矩阵 → 今日完成度」结构化展示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DerivationSheet(
    profile: Profile,
    exerciseKcal: Double,
    intakeKcal: Double,
    intakeProtein: Double,
    intakeCarb: Double,
    intakeFat: Double,
    onDismiss: () -> Unit,
) {
    val t = Calc.targets(profile, exerciseKcal)
    val w = fmt1(profile.weightKg)
    val h = fmt1(profile.heightCm)
    val cut = profile.mode == 1
    // 当减脂赤字为负数时按减法展示；若因导入异常为正数，则如实展示符号
    val surplusAbs = fmt0(abs(t.surplus))
    val surplusOp = if (t.surplus < 0) "−" else "+"
    val rawBonus = if (t.bmr > 0) exerciseKcal / t.bmr else 0.0
    val bonusCapped = rawBonus > 0.4 + 1e-6

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Ink1) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            // ── 顶部标题 + 阶段徽章 ──
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        L("数值是怎么算出来的", "How the numbers are calculated"),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextHi,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        L("每一项每日目标都由你的真实身体数据实时推导", "Every daily target is derived live from your body stats"),
                        fontSize = 12.sp,
                        color = TextLo,
                    )
                }
                PhaseBadge(
                    text = if (cut) L("减脂期", "CUT") else L("增肌期", "BULK"),
                    color = if (cut) Protein else Accent,
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .width(36.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Accent),
            )
            Spacer(Modifier.height(14.dp))

            // ── 身体参数胶囊栏 ──
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ProfileStatPill(Modifier.weight(1f), L("性别", "Sex"), if (profile.male) L("男", "Male") else L("女", "Female"))
                ProfileStatPill(Modifier.weight(1f), L("年龄", "Age"), "${profile.age}")
                ProfileStatPill(Modifier.weight(1f), L("身高", "Height"), "${h}cm")
                ProfileStatPill(Modifier.weight(1f), L("体重", "Weight"), "${w}kg")
            }

            Spacer(Modifier.height(18.dp))

            // ══════════════════════════════════════════════════════
            // Part 1：每日热量目标 4 步推导链
            // ══════════════════════════════════════════════════════
            DerivationSectionHeader(
                index = "01",
                title = L("热量目标推导", "Calorie Target Derivation"),
                badgeText = "${fmt0(t.tdee)} kcal",
                badgeColor = Accent,
            )
            Spacer(Modifier.height(8.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Ink2)
                    .border(0.75.dp, Ink3.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StepFormulaItem(
                    step = "1",
                    title = L("基础代谢 BMR", "Basal Metabolic Rate (BMR)"),
                    valueText = "${fmt0(t.bmr)} kcal",
                    valueColor = TextHi,
                    rule = L("Mifflin-St Jeor 公式：10×体重 + 6.25×身高 − 5×年龄 ", "Mifflin-St Jeor: 10×wt + 6.25×ht − 5×age ") +
                            if (profile.male) L("+ 5（男）", "+ 5 (male)") else L("− 161（女）", "− 161 (female)"),
                    math = "10×$w + 6.25×$h − 5×${profile.age} ${if (profile.male) "+ 5" else "− 161"} = ${fmt0(t.bmr)}",
                )
                StepDivider()
                StepFormulaItem(
                    step = "2",
                    title = L("当日运动消耗", "Today's Exercise Burn"),
                    valueText = "${fmt1(exerciseKcal)} kcal",
                    valueColor = Warn,
                    rule = L("按每条锻炼记录的 MET × 体重(kg) × 时长(h) 累加", "Sum of MET × weight(kg) × duration(h) across today's workouts"),
                    math = L("今日累计消耗 = ", "Total burn today = ") + "${fmt1(exerciseKcal)} kcal",
                )
                StepDivider()
                StepFormulaItem(
                    step = "3",
                    title = L("动态活动系数", "Dynamic Activity Factor"),
                    valueText = "×${fmt3(t.activityFactor)}",
                    valueColor = Protein,
                    rule = L("1.2（久坐基础）+ 运动消耗 ÷ BMR（增量上限 +0.4，即最高 1.6）", "1.2 (sedentary) + burn ÷ BMR (bonus capped at +0.4, max 1.6)"),
                    math = if (bonusCapped) {
                        "1.2 + min(0.4, ${fmt1(exerciseKcal)} ÷ ${fmt0(t.bmr)}) = 1.2 + 0.400 = 1.600"
                    } else {
                        "1.2 + ${fmt1(exerciseKcal)} ÷ ${fmt0(t.bmr)} = 1.2 + ${fmt3(t.bonus)} = ${fmt3(t.activityFactor)}"
                    },
                )
                StepDivider()
                StepFormulaItem(
                    step = "4",
                    title = L("最终热量目标", "Final Calorie Target"),
                    valueText = "${fmt0(t.tdee)} kcal",
                    valueColor = Accent,
                    rule = if (cut) {
                        L("BMR × 活动系数 − 减脂赤字（可在设置中调整，当前 −", "BMR × factor − cut deficit (in Settings, now −") + "$surplusAbs kcal)"
                    } else {
                        L("BMR × 活动系数 + 增肌盈余（可在设置中调整，当前 +", "BMR × factor + bulk surplus (in Settings, now +") + "$surplusAbs kcal)"
                    },
                    math = "${fmt0(t.bmr)} × ${fmt3(t.activityFactor)} $surplusOp $surplusAbs = ${fmt0(t.tdee)} kcal",
                )
            }

            Spacer(Modifier.height(18.dp))

            // ══════════════════════════════════════════════════════
            // Part 2：三大营养素等比分摊
            // ══════════════════════════════════════════════════════
            val pPer = if (cut) "1.5" else "2.0"
            val cPer = if (cut) "2.0" else "5.0"
            val fPer = if (cut) "0.8" else "1.0"
            val baseKcal = t.baseProtein * 4 + t.baseCarb * 4 + t.baseFat * 9

            DerivationSectionHeader(
                index = "02",
                title = L("三大营养素等比分摊", "Macro Proportional Scaling"),
                badgeText = "×${fmt3(t.scale)}",
                badgeColor = Protein,
            )
            Spacer(Modifier.height(8.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Ink2)
                    .border(0.75.dp, Ink3.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = TextLo)) {
                            append(L("先按每公斤体重算出", "First calculate per-kg ") )
                        }
                        withStyle(SpanStyle(color = TextHi, fontWeight = FontWeight.SemiBold)) {
                            append(L("「基础目标」", "Base Targets"))
                        }
                        withStyle(SpanStyle(color = TextLo)) {
                            append(L("（4/4/9 kcal/g 合计 ", " (4/4/9 kcal/g = "))
                        }
                        withStyle(SpanStyle(color = TextHi, fontWeight = FontWeight.SemiBold)) {
                            append("${fmt0(baseKcal)} kcal")
                        }
                        withStyle(SpanStyle(color = TextLo)) {
                            append(L("），再按热量目标等比缩放：", "), then scale to Calorie Target:"))
                        }
                    },
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )

                // 缩放系数算式条
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Ink1)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        L("缩放系数", "Scale factor"),
                        fontSize = 11.5.sp,
                        color = TextLo,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${fmt0(t.tdee)} ÷ ${fmt0(baseKcal)} = ×${fmt3(t.scale)}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = Accent,
                    )
                }

                // 营养素三行对比矩阵（每公斤定额 -> 基础克数 -> ×系数 -> 最终目标克数，与今日页完全一致用 fmt1）
                MacroFormulaCard(
                    name = L("蛋白质", "Protein"),
                    color = Protein,
                    kcalPerGram = "4 kcal/g",
                    perKgFormula = "${pPer}g/kg × ${w}kg",
                    baseGrams = fmt1(t.baseProtein),
                    scaleStr = fmt3(t.scale),
                    finalGrams = fmt1(t.protein),
                )
                MacroFormulaCard(
                    name = L("碳水", "Carbs"),
                    color = CarbC,
                    kcalPerGram = "4 kcal/g",
                    perKgFormula = "${cPer}g/kg × ${w}kg",
                    baseGrams = fmt1(t.baseCarb),
                    scaleStr = fmt3(t.scale),
                    finalGrams = fmt1(t.carb),
                )
                MacroFormulaCard(
                    name = L("脂肪", "Fat"),
                    color = FatC,
                    kcalPerGram = "9 kcal/g",
                    perKgFormula = "${fPer}g/kg × ${w}kg",
                    baseGrams = fmt1(t.baseFat),
                    scaleStr = fmt3(t.scale),
                    finalGrams = fmt1(t.fat),
                )
            }

            Spacer(Modifier.height(18.dp))

            // ══════════════════════════════════════════════════════
            // Part 3：今日摄入完成度（四维可视化进度）
            // ══════════════════════════════════════════════════════
            DerivationSectionHeader(
                index = "03",
                title = L("今日完成情况", "Today's Progress"),
                badgeText = pct(intakeKcal, t.tdee),
                badgeColor = if (intakeKcal >= t.tdee * 0.9) Good else Accent,
            )
            Spacer(Modifier.height(8.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Ink2)
                    .border(0.75.dp, Ink3.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ProgressCompareRow(
                    label = L("摄入热量", "Calories"),
                    current = fmt0(intakeKcal),
                    target = fmt0(t.tdee),
                    unit = "kcal",
                    ratio = if (t.tdee > 0) (intakeKcal / t.tdee).toFloat() else 0f,
                    color = Accent,
                )
                ProgressCompareRow(
                    label = L("蛋白质", "Protein"),
                    current = fmt1(intakeProtein),
                    target = fmt1(t.protein),
                    unit = "g",
                    ratio = if (t.protein > 0) (intakeProtein / t.protein).toFloat() else 0f,
                    color = Protein,
                )
                ProgressCompareRow(
                    label = L("碳水", "Carbs"),
                    current = fmt1(intakeCarb),
                    target = fmt1(t.carb),
                    unit = "g",
                    ratio = if (t.carb > 0) (intakeCarb / t.carb).toFloat() else 0f,
                    color = CarbC,
                )
                ProgressCompareRow(
                    label = L("脂肪", "Fat"),
                    current = fmt1(intakeFat),
                    target = fmt1(t.fat),
                    unit = "g",
                    ratio = if (t.fat > 0) (intakeFat / t.fat).toFloat() else 0f,
                    color = FatC,
                )
            }
        }
    }
}

private fun pct(v: Double, target: Double): String {
    if (target <= 0) return "0%"
    return "${(v / target * 100).roundToInt()}%"
}

/** 保留 3 位小数用于活动系数与缩放系数推导展示，消除四舍五入带来的验算误差 */
private fun fmt3(v: Double): String = String.format(java.util.Locale.US, "%.3f", v)

@Composable
private fun ProfileStatPill(modifier: Modifier = Modifier, label: String, value: String) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Ink2)
            .border(0.5.dp, Ink3.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .padding(vertical = 7.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 10.sp, color = TextLo, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextHi, maxLines = 1)
    }
}

@Composable
private fun DerivationSectionHeader(
    index: String,
    title: String,
    badgeText: String,
    badgeColor: Color,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(badgeColor.copy(alpha = 0.16f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(index, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = badgeColor, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextHi, modifier = Modifier.weight(1f))
        Text(
            badgeText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = badgeColor,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun StepDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.75.dp)
            .background(Ink3.copy(alpha = 0.45f)),
    )
}

@Composable
private fun StepFormulaItem(
    step: String,
    title: String,
    valueText: String,
    valueColor: Color,
    rule: String,
    math: String,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(valueColor.copy(alpha = 0.14f))
                    .border(1.dp, valueColor.copy(alpha = 0.65f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = step,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = valueColor,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeight = 11.sp,
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                    ),
                )
            }
            Spacer(Modifier.width(7.dp))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextHi, modifier = Modifier.weight(1f))
            Text(valueText, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = valueColor, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(4.dp))
        Text(rule, fontSize = 11.5.sp, color = TextLo, lineHeight = 16.sp)
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(Ink1)
                .padding(horizontal = 9.dp, vertical = 5.dp),
        ) {
            Text(math, fontSize = 12.sp, color = TextHi, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun MacroFormulaCard(
    name: String,
    color: Color,
    kcalPerGram: String,
    perKgFormula: String,
    baseGrams: String,
    scaleStr: String,
    finalGrams: String,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Ink1)
            .border(0.75.dp, color.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
            .padding(horizontal = 11.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(Modifier.width(6.dp))
            Text(kcalPerGram, fontSize = 10.5.sp, color = TextLo)
            Spacer(Modifier.weight(1f))
            Text(
                "$finalGrams g",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(3.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                L("基础 ", "Base ") + "$perKgFormula = ${baseGrams}g",
                fontSize = 11.sp,
                color = TextLo,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${baseGrams}g × $scaleStr = ${finalGrams}g",
                fontSize = 11.sp,
                color = TextHi,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun ProgressCompareRow(
    label: String,
    current: String,
    target: String,
    unit: String,
    ratio: Float,
    color: Color,
) {
    val pctInt = (ratio * 100).roundToInt().coerceAtLeast(0)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.5.sp, color = TextHi, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(
                "$current / $target $unit",
                fontSize = 12.sp,
                color = TextHi,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "$pctInt%",
                fontSize = 11.5.sp,
                color = color,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Ink1),
        ) {
            val clamped = ratio.coerceIn(0f, 1f)
            if (clamped > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(clamped.coerceAtLeast(0.03f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(99.dp))
                        .background(color),
                )
            }
        }
    }
}
