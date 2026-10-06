package com.bosang.search.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 공업사 입고 차량: 며칠째인지, 출고가 늦어졌는지 */
object RepairRule {
    /** 입고 후 며칠째 (입고한 날 = 1일째) */
    fun dayCount(inDate: LocalDate, today: LocalDate): Int =
        (ChronoUnit.DAYS.between(inDate, today) + 1).toInt().coerceAtLeast(1)

    /** 출고 예정일. 정해둔 날이 없으면 입고일 + 기준 일수 */
    fun dueDate(inDate: LocalDate, expectedOut: LocalDate?, limitDays: Int): LocalDate =
        expectedOut ?: inDate.plusDays((limitDays - 1).coerceAtLeast(0).toLong())

    /** 예정일을 며칠 넘겼는지 (안 넘겼으면 0) */
    fun overdueDays(inDate: LocalDate, expectedOut: LocalDate?, limitDays: Int, today: LocalDate): Int =
        ChronoUnit.DAYS.between(dueDate(inDate, expectedOut, limitDays), today).toInt().coerceAtLeast(0)

    /** 예정일까지 남은 날 (지났으면 음수) */
    fun daysLeft(inDate: LocalDate, expectedOut: LocalDate?, limitDays: Int, today: LocalDate): Int =
        ChronoUnit.DAYS.between(today, dueDate(inDate, expectedOut, limitDays)).toInt()

    /** 짧은 상태: "입고 5일째 · 출고 D-2" / "입고 12일째 · 예정 3일 지남" / "출고 완료 (8일)" */
    fun label(inDate: LocalDate, expectedOut: LocalDate?, limitDays: Int, outDate: LocalDate?, today: LocalDate): String {
        if (outDate != null) return "출고 완료 (${dayCount(inDate, outDate)}일)"
        val day = "입고 ${dayCount(inDate, today)}일째"
        val left = daysLeft(inDate, expectedOut, limitDays, today)
        return when {
            left > 0 -> "$day · 출고 D-$left"
            left == 0 -> "$day · 오늘 출고 예정"
            else -> "$day · 예정 ${-left}일 지남"
        }
    }
}

/** 약속 알림 시각 */
object ReminderRule {
    /** 약속 시각과 "몇 분 전" 목록 → 아직 안 지난 알림 시각들 (분 전, 시각) */
    fun times(at: Long, minutesBefore: List<Int>, now: Long): List<Pair<Int, Long>> =
        minutesBefore.distinct().map { it to at - it * 60_000L }.filter { it.second > now }.sortedBy { it.second }

    fun label(minutes: Int): String = when {
        minutes == 0 -> "정시"
        minutes % 1440 == 0 -> "${minutes / 1440}일 전"
        minutes % 60 == 0 -> "${minutes / 60}시간 전"
        else -> "${minutes}분 전"
    }
}
