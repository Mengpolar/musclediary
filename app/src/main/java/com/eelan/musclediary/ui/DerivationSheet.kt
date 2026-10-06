package com.eelan.musclediary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eelan.musclediary.data.Profile
import com.eelan.musclediary.domain.Calc
import com.eelan.musclediary.ui.theme.*

/** 点击目标数值后弹出的计算推导面板：把真实数据代入公式逐行展示 */
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
            val w = fmt1(profile.weightKg); val h = fmt1(profile.heightCm)
            val cut = profile.mode == 1
            ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Ink1) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text("数值是怎么算出来的", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextHi)
                    Spacer(Modifier.height(4.dp))
                    Text("你的数据：体重 ${w}kg · 身高 ${h}cm · 年龄 ${profile.age} · ${if (profile.male) "男" else "女"} · ${if (cut) "减脂期" else "增肌期"}",
                        fontSize = 13.sp, color = TextLo)
                    Spacer(Modifier.height(12.dp))

                    FormulaRow("基础代谢 BMR",
                        "Mifflin-St Jeor：10×体重 + 6.25×身高 − 5×年龄 ${if (profile.male) "+ 5（男）" else "− 161（女）"}",
                        "10×${w} + 6.25×$h − 5×${profile.age} ${if (profile.male) "+ 5" else "− 161"} = ${fmt0(t.bmr)} kcal")
                    FormulaRow("当日运动消耗",
                        "Σ MET × 体重 × 时长（按每条锻炼记录计算后求和）",
                        "= ${fmt0(exerciseKcal)} kcal")
                    FormulaRow("活动系数",
                        "1.2（久坐基础）+ 运动消耗 ÷ BMR，最高 1.6",
                        "1.2 + ${fmt1(exerciseKcal)} ÷ ${fmt0(t.bmr)} = ${fmt2(t.activityFactor)}")
                    FormulaRow(
                        "热量目标",
                        if (cut) "BMR × 活动系数 − 减脂赤字（设置中可调，当前 ${fmt0(-t.surplus)}）"
                        else "BMR × 活动系数 + 增肌盈余（设置中可调，当前 ${fmt0(t.surplus)}）",
                        "${fmt0(t.bmr)} × ${fmt2(t.activityFactor)} ${if (cut) "−" else "+"} ${fmt0(kotlin.math.abs(t.surplus))} = ${fmt0(t.tdee)} kcal")

                    val pPer = if (cut) "1.5" else "2.0"
                    val cPer = if (cut) "2.0" else "5.0"
                    val fPer = if (cut) "0.8" else "1.0"
                    val baseKcal = t.baseProtein * 4 + t.baseCarb * 4 + t.baseFat * 9
                    FormulaRow("三维基础目标",
                        "$pPer g/kg 蛋白 + $cPer g/kg 碳水 + $fPer g/kg 脂肪，合计 ${fmt0(baseKcal)} kcal",
                        "蛋白 ${fmt0(t.baseProtein)}g · 碳水 ${fmt0(t.baseCarb)}g · 脂肪 ${fmt0(t.baseFat)}g")
                    FormulaRow("热量等比分摊",
                        "热量目标 ÷ 三维基础热量 = 缩放系数（进度条上的「热量所需」刻度）",
                        "${fmt0(t.tdee)} ÷ ${fmt0(baseKcal)} = ×${fmt2(t.scale)}")
                    FormulaRow("蛋白质目标",
                        "$pPer g/kg × 体重 × 缩放系数",
                        "$pPer × ${w} × ${fmt2(t.scale)} = ${fmt0(t.protein)} g")
                    FormulaRow("碳水目标",
                        "$cPer g/kg × 体重 × 缩放系数",
                        "$cPer × ${w} × ${fmt2(t.scale)} = ${fmt0(t.carb)} g")
                    FormulaRow("脂肪目标",
                        "$fPer g/kg × 体重 × 缩放系数（减脂期已含 0.8g/kg 保底）",
                        "$fPer × ${w} × ${fmt2(t.scale)} = ${fmt0(t.fat)} g")

                    Spacer(Modifier.height(16.dp))
                    Text("今日完成情况", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextHi)
                    Spacer(Modifier.height(8.dp))
                    FormulaRow("摄入热量", "", "${fmt0(intakeKcal)} / ${fmt0(t.tdee)} kcal（${pct(intakeKcal, t.tdee)}）")
                    FormulaRow("摄入蛋白质", "", "${fmt1(intakeProtein)} / ${fmt0(t.protein)} g（${pct(intakeProtein, t.protein)}）")
                    FormulaRow("摄入碳水", "", "${fmt1(intakeCarb)} / ${fmt0(t.carb)} g（${pct(intakeCarb, t.carb)}）")
                    FormulaRow("摄入脂肪", "", "${fmt1(intakeFat)} / ${fmt0(t.fat)} g（${pct(intakeFat, t.fat)}）")
                }
            }
}

private fun pct(v: Double, target: Double): String {
    if (target <= 0) return "0%"
    return "${(v / target * 100).toInt()}%"
}

@Composable
private fun FormulaRow(title: String, formula: String, result: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Ink2)
            .padding(12.dp)
    ) {
        Row {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Accent)
        }
        if (formula.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(formula, fontSize = 12.sp, color = TextLo)
        }
        Spacer(Modifier.height(4.dp))
        Text(result, fontSize = 14.sp, color = TextHi, fontFamily = FontFamily.Monospace)
    }
}

private fun fmt2(v: Double): String = String.format("%.2f", v)
