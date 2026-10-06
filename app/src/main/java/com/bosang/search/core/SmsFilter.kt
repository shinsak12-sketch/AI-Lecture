package com.bosang.search.core

/**
 * 통신사가 보내는 "부재중 전화 알림" 문자 (캐치콜 · 매너콜 · 콜키퍼 · 통화가능 알림).
 * 상대 번호로 들어와서 그 사람 문자에 섞이지만 내용이 없으므로 숨긴다.
 */
object SmsFilter {
    private val tags = listOf("캐치콜", "매너콜", "콜키퍼", "통화가능알림", "통화가능 알림", "콜알리미", "부재중알리미", "착신전환")

    // 예: "10/06 14:20 전화하셨습니다", "010-1234-5678님이 14:20에 전화를 거셨습니다", "부재중 전화 1건"
    private val noticeRegex = Regex(
        "(\\d{1,2}[/.월]\\s?\\d{1,2}일?\\s*)?\\d{1,2}:\\d{2}.{0,20}(전화(를)?\\s?(하셨|거셨|걸어오셨|주셨)|부재중)" +
            "|(님(이|께서)\\s?.{0,20}전화(를)?\\s?(하셨|거셨|걸어오셨|주셨))" +
            "|부재중\\s?전화\\s?\\d+\\s?(건|통)" +
            "|지금\\s?통화(가)?\\s?가능(합니다|해요)",
    )

    fun isCallNotice(body: String): Boolean {
        val b = body.trim()
        if (b.isEmpty()) return false
        val compact = b.replace(" ", "")
        if (tags.any { compact.contains(it.replace(" ", "")) }) return true
        // 사람이 쓴 긴 문자는 건드리지 않음
        return b.length <= 120 && noticeRegex.containsMatchIn(b)
    }
}
