package com.bosang.search.remind

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bosang.search.MainActivity
import com.bosang.search.R
import com.bosang.search.core.ReminderRule
import com.bosang.search.core.RepairRule
import com.bosang.search.data.Appointment
import com.bosang.search.data.Store
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 약속 알림과 매일 아침 입고 차량 확인을 안드로이드 알람으로 맞춰 둔다.
 * 저장된 내용이 바뀔 때 · 폰을 켤 때 · 앱을 열 때 다시 맞춘다.
 */
object Reminders {
    private const val CHANNEL = "reminders"
    private const val ACT_APPT = "com.bosang.search.APPT"
    private const val ACT_REPAIRS = "com.bosang.search.REPAIRS"
    /** 매일 입고 차량을 확인하는 시각 */
    val REPAIR_CHECK: LocalTime = LocalTime.of(9, 0)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("remind", Context.MODE_PRIVATE)

    private fun apptIntent(ctx: Context, id: String, minutes: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            0,
            Intent(ctx, ReminderReceiver::class.java)
                .setAction(ACT_APPT)
                .setData(Uri.parse("bosang://appt/$id/$minutes"))
                .putExtra("id", id)
                .putExtra("m", minutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun repairIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            1,
            Intent(ctx, ReminderReceiver::class.java).setAction(ACT_REPAIRS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun exactAllowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun set(ctx: Context, at: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        runCatching {
            if (exactAllowed(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** 지금 저장된 약속 · 입고 차량대로 알람을 다시 맞춤 */
    fun sync(ctx: Context) {
        val app = ctx.applicationContext
        val store = Store.get(app)
        val am = app.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val keys = HashSet<String>()
        store.allAppts().filter { !it.done }.forEach { a ->
            ReminderRule.times(a.at, a.remind, now).forEach { (m, t) ->
                set(app, t, apptIntent(app, a.id, m))
                keys.add("${a.id}/$m")
            }
        }
        // 없어진 알림은 취소
        val old = prefs(app).getStringSet("keys", emptySet()).orEmpty()
        (old - keys).forEach { k ->
            val id = k.substringBeforeLast('/')
            val m = k.substringAfterLast('/').toIntOrNull() ?: return@forEach
            runCatching { am.cancel(apptIntent(app, id, m)) }
        }
        prefs(app).edit().putStringSet("keys", keys).apply()

        if (store.openRepairs().isEmpty()) {
            runCatching { am.cancel(repairIntent(app)) }
        } else {
            set(app, nextRepairCheck(LocalDateTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), repairIntent(app))
        }
    }

    fun nextRepairCheck(now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(REPAIR_CHECK)
        return if (now.isBefore(today)) today else today.plusDays(1)
    }

    // ───────────── 알림 띄우기 ─────────────

    private fun channel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "약속 · 입고 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "약속 시간이 다가올 때, 공업사 출고가 늦어질 때 알려요"
                },
            )
        }
    }

    private fun open(ctx: Context, code: Int, caseNo: String?, number: String?): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        when {
            caseNo != null -> i.putExtra("nav", "case").putExtra("case", caseNo)
            number != null -> i.putExtra("nav", "person").putExtra("number", number)
        }
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun post(ctx: Context, id: Int, b: NotificationCompat.Builder) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        runCatching { nm.notify(id, b.build()) }
    }

    private val timeFmt = DateTimeFormatter.ofPattern("M월 d일 (E) a h:mm", Locale.KOREAN)

    fun apptText(store: Store, a: Appointment): String = listOfNotNull(
        a.number?.let { n ->
            val role = a.caseNo?.let { c -> store.linksForCase(c).firstOrNull { it.number == n }?.role }
            listOfNotNull(store.displayName(n), role).joinToString(" ")
        },
        a.caseNo,
        a.place.ifBlank { null },
        a.memo.lineSequence().firstOrNull { it.isNotBlank() },
    ).joinToString(" · ")

    fun showAppt(ctx: Context, id: String, minutes: Int) {
        val store = Store.get(ctx)
        val a = store.appt(id) ?: return
        if (a.done) return
        channel(ctx)
        val time = java.time.Instant.ofEpochMilli(a.at).atZone(ZoneId.systemDefault()).format(timeFmt)
        val title = if (minutes == 0) "지금 ${a.kind.label} 시간이에요" else "${a.kind.label} ${ReminderRule.label(minutes)} · $time"
        val code = a.id.hashCode()
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(apptText(store, a).ifBlank { time })
            .setStyle(NotificationCompat.BigTextStyle().bigText(apptText(store, a).ifBlank { time }))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open(ctx, code, a.caseNo, a.number))
        a.number?.let { n ->
            b.addAction(
                0,
                "전화",
                PendingIntent.getActivity(
                    ctx,
                    code + 1,
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        post(ctx, code, b)
    }

    /** 매일 아침: 오늘 출고 예정 · 출고 늦어진 차량 */
    fun showRepairs(ctx: Context) {
        val store = Store.get(ctx)
        val today = LocalDate.now()
        channel(ctx)
        store.openRepairs().forEach { r ->
            val left = RepairRule.daysLeft(r.inDate, r.expectedOut, r.limitDays, today)
            if (left > 0) return@forEach
            val title = if (left == 0) "오늘 출고 예정 · ${r.shop}" else "출고 지연 ${-left}일 · ${r.shop}"
            val text = listOf(
                r.caseNo,
                r.carNo.ifBlank { null },
                RepairRule.label(r.inDate, r.expectedOut, r.limitDays, null, today),
            ).filterNotNull().joinToString(" · ")
            val code = ("repair" + r.id).hashCode()
            val b = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(open(ctx, code, r.caseNo, null))
            r.shopNumber?.let { n ->
                b.addAction(
                    0,
                    "공업사 전화",
                    PendingIntent.getActivity(
                        ctx,
                        code + 1,
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
            post(ctx, code, b)
        }
    }

    internal fun handle(ctx: Context, intent: Intent) {
        when (intent.action) {
            ACT_APPT -> showAppt(ctx, intent.getStringExtra("id") ?: return, intent.getIntExtra("m", 0))
            ACT_REPAIRS -> {
                showRepairs(ctx)
                sync(ctx) // 다음 날 아침으로
            }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.handle(context.applicationContext, intent)
    }
}
