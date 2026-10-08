package com.eelan.musclediary.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.eelan.musclediary.MainActivity
import com.eelan.musclediary.R
import com.eelan.musclediary.data.AppDatabase

/**
 * 饮水分时段提醒（可扩展更多提醒类型）：
 * 按当前饮水目标把一天分成几个时段，每个时段结束时检查该时段「应喝 vs 实喝」，
 * 落后则发本地通知。仅在已授予通知权限且开关打开时生效。
 */
object WaterReminder {

    const val CHANNEL_ID = "water_reminder"
    const val ACTION_CHECK = "com.eelan.musclediary.WATER_CHECK"

    /** 分时段计划：各时段结束时刻（小时）与其占全天的权重 */
    val SLOTS = listOf(
        Slot(11, 0.30), // 上午（起床~11点）应喝 30%
        Slot(15, 0.30), // 午后（11~15点）应喝 30%
        Slot(19, 0.25), // 傍晚（15~19点）应喝 25%
        Slot(22, 0.15), // 晚间（19~22点）应喝 15%
    )

    data class Slot(val endHour: Int, val ratio: Double)

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "喝水提醒", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "按时段检查饮水量，落后时提醒" }
            ctx.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(ch)
        }
    }

    fun hasPermission(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        }

    fun notifyBehind(ctx: Context, slotLabel: String, shouldMl: Int, actualMl: Int) {
        if (!hasPermission(ctx)) return
        ensureChannel(ctx)
        val intent = Intent(ctx, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = "$slotLabel 应喝约 ${shouldMl}ml，目前 ${actualMl}ml，喝杯水吧"
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("该喝水了")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(1001, n) }
    }

    /** 计算当前应处的时段与达标情况；返回非 null 表示需要提醒 */
    suspend fun checkNow(ctx: Context) {
        val db = AppDatabase.get(ctx)
        val profile = db.dao().getProfile() ?: return
        if (profile.waterReminder == 0) return
        val now = java.util.Calendar.getInstance()
        val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val slot = SLOTS.lastOrNull { hour >= it.endHour } ?: return // 时段未结束不打扰
        val targetMl = profile.weightKg * 35.0
        val shouldMl = (targetMl * slot.ratio).toInt()
        val today = java.time.LocalDate.now().toString()
        val actual = db.dao().allWaterEntries()
            .firstOrNull { it.date == today }?.ml?.toInt() ?: 0
        if (actual < shouldMl) {
            val label = when (slot.endHour) {
                11 -> "上午时段（起床~11点）"
                15 -> "午后时段（11~15点）"
                19 -> "傍晚时段（15~19点）"
                else -> "晚间时段（19~22点）"
            }
            notifyBehind(ctx, label, shouldMl, actual)
        }
    }

    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_CHECK) return
            kotlinx.coroutines.runBlocking { checkNow(context) }
            scheduleNext(context)
        }
    }

    private const val ALARM_REQUEST_CODE = 2001

    /** 注册下一个时段的检查（幂等，覆盖式） */
    fun scheduleNext(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, ALARM_REQUEST_CODE,
            Intent(ACTION_CHECK).setPackage(ctx.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val now = java.util.Calendar.getInstance()
        val next = SLOTS.map { s ->
            val c = now.clone() as java.util.Calendar
            c.set(java.util.Calendar.HOUR_OF_DAY, s.endHour)
            c.set(java.util.Calendar.MINUTE, 10)
            c.set(java.util.Calendar.SECOND, 0)
            if (c.timeInMillis <= now.timeInMillis) c.add(java.util.Calendar.DAY_OF_YEAR, 1)
            c
        }.minByOrNull { it.timeInMillis } ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                (next.timeInMillis - System.currentTimeMillis())
                    .plus(System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()), pi)
        } else {
            am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
        }
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        am.cancel(PendingIntent.getBroadcast(
            ctx, ALARM_REQUEST_CODE,
            Intent(ACTION_CHECK).setPackage(ctx.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ))
    }
}
