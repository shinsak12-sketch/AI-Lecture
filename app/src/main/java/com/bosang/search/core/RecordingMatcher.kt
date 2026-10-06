package com.bosang.search.core

/** 통화기록 한 줄. number는 PhoneNumbers.normalize 된 값 */
data class CallEntry(
    val number: String,
    val name: String?,
    val timeMillis: Long,
    val durationSec: Long,
    val type: Int,
)

enum class MatchMethod {
    /** 통화기록의 통화 시각과 녹음 시각이 맞음 (가장 정확) */
    CALL_LOG,
    /** 파일 이름에 번호가 그대로 들어 있음 */
    FILE_NUMBER,
    /** 파일 이름의 연락처명으로 찾음 */
    CONTACT_NAME,
    /** 같은 이름이 여러 개라 사람이 확인해야 함 */
    AMBIGUOUS,
}

data class RecordingMatch(val numbers: List<String>, val method: MatchMethod)

object RecordingMatcher {
    /** 녹음은 통화가 연결된 뒤 시작되므로, 통화기록 시각보다 조금 늦게 찍힌다 */
    const val BEFORE_MS = 30_000L
    const val AFTER_MS = 150_000L

    fun match(
        rec: ParsedRecording,
        recMillis: Long,
        calls: List<CallEntry>,
        numbersForName: (String) -> List<String>,
    ): RecordingMatch? {
        val inWindow = calls.filter {
            it.number.isNotEmpty() &&
                recMillis >= it.timeMillis - BEFORE_MS &&
                recMillis <= it.timeMillis + AFTER_MS
        }

        // 1) 파일 이름에 번호가 있으면 그 번호. 통화기록으로 확인되면 더 확실
        if (rec.isNumber) {
            val n = rec.number
            if (n.isEmpty()) return null
            val confirmed = inWindow.any { it.number == n }
            return RecordingMatch(listOf(n), if (confirmed) MatchMethod.CALL_LOG else MatchMethod.FILE_NUMBER)
        }

        val label = rec.label
        // 2) 그 시각 통화기록에 같은 이름으로 남아 있는 번호
        val byName = inWindow.filter { it.name?.trim() == label }.map { it.number }.distinct()
        if (byName.size == 1) return RecordingMatch(byName, MatchMethod.CALL_LOG)

        val contactNums = numbersForName(label).map { PhoneNumbers.normalize(it) }.filter { it.isNotEmpty() }.distinct()
        val windowNums = inWindow.map { it.number }.distinct()

        // 3) 그 시각에 통화한 번호 중 연락처 이름과 맞는 번호
        val both = windowNums.filter { it in contactNums }
        if (both.size == 1) return RecordingMatch(both, MatchMethod.CALL_LOG)

        // 4) 연락처가 지워지거나 이름이 바뀌었어도, 그 시각 통화가 하나뿐이면 그 번호
        if (contactNums.isEmpty() && windowNums.size == 1) return RecordingMatch(windowNums, MatchMethod.CALL_LOG)

        // 5) 연락처 이름으로만 찾기
        if (contactNums.size == 1) return RecordingMatch(contactNums, MatchMethod.CONTACT_NAME)
        if (contactNums.size > 1) return RecordingMatch(contactNums, MatchMethod.AMBIGUOUS)
        if (windowNums.size > 1) return RecordingMatch(windowNums, MatchMethod.AMBIGUOUS)
        return null
    }
}
