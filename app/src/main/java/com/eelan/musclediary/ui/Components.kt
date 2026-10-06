package com.eelan.musclediary.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eelan.musclediary.ui.theme.*

/** 环形进度，点击可弹推导面板 */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    size: Dp,
    stroke: Dp = 10.dp,
    centerLabel: String,
    centerValue: String,
    subValue: String,
    onClick: () -> Unit,
) {
    val track = Ink3
    Box(
        modifier = Modifier
            .size(size)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            drawArc(
                color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                style = Stroke(s, cap = StrokeCap.Round),
            )
            drawArc(
                color = color, startAngle = -90f,
                sweepAngle = (progress.coerceIn(0f, 1f) * 360f), useCenter = false,
                style = Stroke(s, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerLabel, fontSize = 11.sp, color = TextLo)
            Text(centerValue, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextHi)
            Text(subValue, fontSize = 10.sp, color = TextLo)
        }
    }
}

/** 宏量营养横条进度，点击可弹推导面板 */
@Composable
fun MacroBar(label: String, value: Double, target: Double, color: Color, onClick: () -> Unit) {
    val pct = if (target > 0) (value / target).toFloat().coerceIn(0f, 1f) else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, fontSize = 12.sp, color = TextLo)
            Spacer(Modifier.weight(1f))
            Text(
                "${fmt1(value)} / ${fmt1(target)} g",
                fontSize = 12.sp, color = TextHi, fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Ink2)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(pct)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink1)
            .padding(16.dp),
        content = content,
    )
}

fun fmt1(v: Double): String = if (v >= 100) v.toInt().toString() else String.format("%.1f", v)
fun fmt0(v: Double): String = v.toInt().toString()

/** 数字输入对话框 */
@Composable
fun NumberDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text(title, color = TextHi) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text(label, color = TextLo) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = TextHi, unfocusedTextColor = TextHi,
                    focusedContainerColor = Ink1, unfocusedContainerColor = Ink1,
                    focusedIndicatorColor = Accent, unfocusedIndicatorColor = Ink3,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                text.toDoubleOrNull()?.let { if (it > 0) onConfirm(it) }
            }) { Text("确定", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextLo) } },
    )
}

/** 通用列表项 */
@Composable
fun EntryRow(
    title: String,
    detail: String,
    trailing: String,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Ink2)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = TextHi, fontWeight = FontWeight.Medium)
            Text(detail, fontSize = 12.sp, color = TextLo)
        }
        Text(trailing, fontSize = 14.sp, color = Accent, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End)
        Spacer(Modifier.width(8.dp))
        Text("删", fontSize = 12.sp, color = TextLo,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Ink3)
                .clickable(onClick = onDelete)
                .padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

/** 页面标题 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier.padding(vertical = 8.dp),
        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextHi,
    )
}
