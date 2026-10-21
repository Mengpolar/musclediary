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
import com.eelan.musclediary.L
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
                    Text(L("数值是怎么算出来的", "How the numbers are calculated"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextHi)
                    Spacer(Modifier.height(4.dp))
                    Text(L("你的数据：", "Your data: ") + "${L("体重 ", "wt ")}${w}kg · ${L("身高 ", "ht ")}${h}cm · ${L("年龄 ", "age ")}${profile.age} · ${if (profile.male) L("男", "male") else L("女", "female")} · ${if (cut) L("减脂期", "cut phase") else L("增肌期", "bulk phase")}",
                        fontSize = 13.sp, color = TextLo)
                    Spacer(Modifier.height(12.dp))

                    FormulaRow(L("基础代谢 BMR", "BMR"),
                        L("Mifflin-St Jeor：10×体重 + 6.25×身高 − 5×年龄 ", "Mifflin-St Jeor: 10xWt + 6.25xHt - 5xAge ") + "${if (profile.male) L("+ 5（男）", "+ 5 (male)") else L("− 161（女）", "- 161 (female)")}",
                        "10×${w} + 6.25×$h − 5×${profile.age} ${if (profile.male) "+ 5" else "− 161"} = ${fmt0(t.bmr)} kcal")
                    FormulaRow(L("当日运动消耗", "Exercise burn"),
                        L("Σ MET × 体重 × 时长（按每条锻炼记录计算后求和）", "Sum of MET x weight x duration for each exercise entry"),
                        "= ${fmt0(exerciseKcal)} kcal")
                    FormulaRow(L("活动系数", "Activity factor"),
                        L("1.2（久坐基础）+ 运动消耗 ÷ BMR，最高 1.6", "1.2 (sedentary base) + exercise burn / BMR, capped at 1.6"),
                        "1.2 + ${fmt1(exerciseKcal)} ÷ ${fmt0(t.bmr)} = ${fmt2(t.activityFactor)}")
                    FormulaRow(
                        L("热量目标", "Calorie target"),
                        if (cut) L("BMR × 活动系数 − 减脂赤字（设置中可调，当前 ", "BMR x factor - cut deficit (adjustable in Settings, currently ") + "${fmt0(-t.surplus)})"
                        else L("BMR × 活动系数 + 增肌盈余（设置中可调，当前 ", "BMR x factor + bulk surplus (adjustable in Settings, currently ") + "${fmt0(t.surplus)})",
                        "${fmt0(t.bmr)} × ${fmt2(t.activityFactor)} ${if (cut) "−" else "+"} ${fmt0(kotlin.math.abs(t.surplus))} = ${fmt0(t.tdee)} kcal")

                    val pPer = if (cut) "1.5" else "2.0"
                    val cPer = if (cut) "2.0" else "5.0"
                    val fPer = if (cut) "0.8" else "1.0"
                    val baseKcal = t.baseProtein * 4 + t.baseCarb * 4 + t.baseFat * 9
                    FormulaRow(L("三维基础目标", "Base macro targets"),
                        "$pPer g/kg " + L("蛋白", "protein") + " + $cPer g/kg " + L("碳水", "carbs") + " + $fPer g/kg " + L("脂肪", "fat") + L("，合计 ", ", total ") + "${fmt0(baseKcal)} kcal",
                        L("蛋白 ", "P ") + "${fmt0(t.baseProtein)}g · " + L("碳水 ", "C ") + "${fmt0(t.baseCarb)}g · " + L("脂肪 ", "F ") + "${fmt0(t.baseFat)}g")
                    FormulaRow(L("热量等比分摊", "Calorie-proportional scaling"),
                        L("热量目标 ÷ 三维基础热量 = 缩放系数（进度条上的「热量所需」刻度）", "Calorie target / base macro calories = scale factor"),
                        "${fmt0(t.tdee)} ÷ ${fmt0(baseKcal)} = ×${fmt2(t.scale)}")
                    FormulaRow(L("蛋白质目标", "Protein target"),
                        "$pPer g/kg × " + L("体重", "wt") + " × " + L("缩放系数", "scale"),
                        "$pPer × ${w} × ${fmt2(t.scale)} = ${fmt0(t.protein)} g")
                    FormulaRow(L("碳水目标", "Carb target"),
                        "$cPer g/kg × " + L("体重", "wt") + " × " + L("缩放系数", "scale"),
                        "$cPer × ${w} × ${fmt2(t.scale)} = ${fmt0(t.carb)} g")
                    FormulaRow(L("脂肪目标", "Fat target"),
                        "$fPer g/kg × " + L("体重", "wt") + " × " + L("缩放系数", "scale") + L("（减脂期已含 0.8g/kg 保底）", " (cut phase includes 0.8g/kg floor)"),
                        "$fPer × ${w} × ${fmt2(t.scale)} = ${fmt0(t.fat)} g")

                    Spacer(Modifier.height(16.dp))
                    Text(L("今日完成情况", "Today's progress"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextHi)
                    Spacer(Modifier.height(8.dp))
                    FormulaRow(L("摄入热量", "Calories eaten"), "", "${fmt0(intakeKcal)} / ${fmt0(t.tdee)} kcal (${pct(intakeKcal, t.tdee)})")
                    FormulaRow(L("摄入蛋白质", "Protein eaten"), "", "${fmt1(intakeProtein)} / ${fmt0(t.protein)} g (${pct(intakeProtein, t.protein)})")
                    FormulaRow(L("摄入碳水", "Carbs eaten"), "", "${fmt1(intakeCarb)} / ${fmt0(t.carb)} g (${pct(intakeCarb, t.carb)})")
                    FormulaRow(L("摄入脂肪", "Fat eaten"), "", "${fmt1(intakeFat)} / ${fmt0(t.fat)} g (${pct(intakeFat, t.fat)})")
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
