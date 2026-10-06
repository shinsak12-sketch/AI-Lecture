package com.bosang.search.core

/** 금액 표기 */
object Money {
    /** 1240000 → "1,240,000" */
    fun comma(n: Long): String = "%,d".format(n)

    /** 요약용: 만 원 단위로 떨어지면 "120만", 아니면 "1,234,567원" */
    fun short(n: Long): String = when {
        n >= 10_000 && n % 10_000 == 0L -> "${comma(n / 10_000)}만"
        else -> comma(n) + "원"
    }

    /** 입력칸 글자에서 숫자만 (최대 12자리) */
    fun parse(s: String): Long? = s.filter { it.isDigit() }.take(12).toLongOrNull()
}
