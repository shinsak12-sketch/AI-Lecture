package com.bosang.search.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 사건 카드에 보여줄 숫자와 마지막 연락 */
data class CaseSummary(
    val recCount: Int,
    val smsCount: Int,
    val callCount: Int,
    /** 가장 최근 문자·녹음 */
    val last: TimelineItem?,
    /** 가장 최근 통화 (녹음이 없는 통화 포함) */
    val lastCallTime: Long?,
) {
    val lastTime: Long? get() = listOfNotNull(last?.timeMillis, lastCallTime).maxOrNull()

    companion object {
        val EMPTY = CaseSummary(0, 0, 0, null, null)
    }
}

object Summaries {
    /** 여러 사건을 한 번에. 번호별 문자는 한 번만 읽는다 */
    suspend fun forCases(caseNos: List<String>, data: PhoneData, store: Store): Map<String, CaseSummary> {
        if (caseNos.isEmpty()) return emptyMap()
        val index = RecordingIndex.get(data, store)
        return withContext(Dispatchers.IO) {
            val calls = data.calls(5000).groupBy { it.number }
            val smsCache = HashMap<String, List<SmsItem>>()
            caseNos.associateWith { caseNo ->
                val numbers = store.linksForCase(caseNo).map { it.number }.toSet()
                val recs = index.mapNotNull { r ->
                    r.match.numbers.firstOrNull { it in numbers }?.let { TimelineItem.Rec(r, it) }
                }
                val sms = numbers.flatMap { n -> smsCache.getOrPut(n) { data.sms(n) } }
                val caseCalls = numbers.flatMap { calls[it].orEmpty() }
                val lastRec = recs.maxByOrNull { it.timeMillis }
                val lastSms = sms.maxByOrNull { it.timeMillis }?.let { TimelineItem.Sms(it) }
                val last = listOfNotNull<TimelineItem>(lastRec, lastSms).maxByOrNull { it.timeMillis }
                CaseSummary(
                    recCount = recs.size,
                    smsCount = sms.size,
                    callCount = caseCalls.size,
                    last = last,
                    lastCallTime = caseCalls.maxOfOrNull { it.timeMillis },
                )
            }
        }
    }

    /** 한 사람(번호들)의 통화 횟수와 마지막 통화 시각 */
    suspend fun calls(numbers: Set<String>, data: PhoneData): Pair<Int, Long?> = withContext(Dispatchers.IO) {
        val list = data.calls(5000).filter { it.number in numbers }
        list.size to list.maxOfOrNull { it.timeMillis }
    }
}
