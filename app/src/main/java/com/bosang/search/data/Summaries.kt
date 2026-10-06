package com.bosang.search.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 사건 카드에 보여줄 숫자와 마지막 연락 */
data class CaseSummary(
    /** null 이면 아직 통화녹음을 찾는 중 */
    val recCount: Int?,
    val smsCount: Int,
    val callCount: Int,
    /** 가장 최근 문자·녹음 */
    val last: TimelineItem?,
    /** 가장 최근 통화 (녹음이 없는 통화 포함) */
    val lastCallTime: Long?,
) {
    val lastTime: Long? get() = listOfNotNull(last?.timeMillis, lastCallTime).maxOrNull()
}

/** 녹음 없이 빨리 구할 수 있는 부분 (문자 · 통화) */
class CaseBase(
    val numbers: Set<String>,
    val smsCount: Int,
    val lastSms: SmsItem?,
    val callCount: Int,
    val lastCallTime: Long?,
)

object Summaries {
    /** 문자 · 통화만으로 먼저. 번호별 문자는 한 번만 읽는다 */
    suspend fun base(caseNos: List<String>, data: PhoneData, store: Store): Map<String, CaseBase> {
        if (caseNos.isEmpty()) return emptyMap()
        return withContext(Dispatchers.IO) {
            val calls = data.calls(5000).groupBy { it.number }
            val smsCache = HashMap<String, List<SmsItem>>()
            caseNos.associateWith { caseNo ->
                val numbers = store.linksForCase(caseNo).map { it.number }.toSet()
                val sms = numbers.flatMap { n -> smsCache.getOrPut(n) { data.sms(n) + data.mms(n) } }
                val caseCalls = numbers.flatMap { calls[it].orEmpty() }
                CaseBase(
                    numbers = numbers,
                    smsCount = sms.size,
                    lastSms = sms.maxByOrNull { it.timeMillis },
                    callCount = caseCalls.size,
                    lastCallTime = caseCalls.maxOfOrNull { it.timeMillis },
                )
            }
        }
    }

    /** index 가 null 이면 녹음은 "찾는 중"으로 */
    fun summarize(base: Map<String, CaseBase>, index: List<IndexedRecording>?): Map<String, CaseSummary> =
        base.mapValues { (_, b) ->
            val recs = index?.let { Records.recs(b.numbers, it) }
            val lastRec = recs?.firstOrNull()
            val lastSms = b.lastSms?.let { TimelineItem.Sms(it) }
            CaseSummary(
                recCount = recs?.size,
                smsCount = b.smsCount,
                callCount = b.callCount,
                last = listOfNotNull<TimelineItem>(lastRec, lastSms).maxByOrNull { it.timeMillis },
                lastCallTime = b.lastCallTime,
            )
        }
}
