package com.bosang.search.remind

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bosang.search.data.Appointment
import com.bosang.search.data.ApptKind
import com.bosang.search.data.Repair
import com.bosang.search.data.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/** 약속 · 입고 알림이 실제로 알람에 걸리고 알림으로 뜨는지 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemindersTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun notifications() = shadowOf(ctx.getSystemService(NotificationManager::class.java)).allNotifications

    @Test fun 약속을_저장하면_알람이_걸리고_알림이_뜬다() {
        shadowOf(ctx as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val store = Store.get(ctx)
        store.upsert("26-00011111", listOf(Triple("01012121212", "피해자", "홍길동")))
        val at = System.currentTimeMillis() + 3 * 3_600_000L
        store.saveAppt(Appointment("a1", "26-00011111", "01012121212", ApptKind.MEET, at, place = "○○공업사", remind = listOf(60, 0), createdAt = 0))

        val alarms = shadowOf(ctx.getSystemService(AlarmManager::class.java)).scheduledAlarms
        val times = alarms.map { it.triggerAtTime }
        assertTrue(times.contains(at - 3_600_000L))
        assertTrue(times.contains(at))

        Reminders.showAppt(ctx, "a1", 60)
        val n = notifications().last()
        assertTrue(n.extras.getString("android.title").orEmpty().startsWith("면담 1시간 전"))
        assertTrue(n.extras.getCharSequence("android.text").toString().contains("홍길동 피해자"))
    }

    @Test fun 끝낸_약속은_알림_안함() {
        val store = Store.get(ctx)
        val at = System.currentTimeMillis() + 3_600_000L
        store.saveAppt(Appointment("a2", null, null, ApptKind.CALL, at, remind = listOf(30), done = true, createdAt = 0))
        val before = notifications().size
        Reminders.showAppt(ctx, "a2", 30)
        assertEquals(before, notifications().size)
    }

    @Test fun 출고가_늦으면_아침에_알린다() {
        shadowOf(ctx as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val store = Store.get(ctx)
        val today = LocalDate.now()
        store.saveRepair(Repair("r1", "26-00022222", "제일자동차공업", carNo = "12가3456", inDate = today.minusDays(9), limitDays = 7, createdAt = 0))
        store.saveRepair(Repair("r2", "26-00022222", "여유공업사", inDate = today, limitDays = 7, createdAt = 0))
        Reminders.showRepairs(ctx)
        val titles = notifications().map { it.extras.getString("android.title").orEmpty() }
        assertTrue(titles.any { it == "출고 지연 3일 · 제일자동차공업" })
        assertTrue(titles.none { it.contains("여유공업사") })
        // 다음 날 아침 확인도 걸려 있음
        val alarms = shadowOf(ctx.getSystemService(AlarmManager::class.java)).scheduledAlarms
        assertTrue(alarms.isNotEmpty())
    }
}
