package com.bosang.search.data

import com.bosang.search.core.CallEntry
import com.bosang.search.core.MatchMethod
import com.bosang.search.core.RecordingMatch
import com.bosang.search.core.RecordingMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class IndexedRecording(val file: RecordingFile, val match: RecordingMatch)

sealed interface TimelineItem {
    val timeMillis: Long
    /** 이 항목이 누구(번호) 것인지 */
    val number: String

    data class Sms(val sms: SmsItem) : TimelineItem {
        override val timeMillis get() = sms.timeMillis
        override val number get() = sms.number
    }

    data class Rec(val rec: IndexedRecording, override val number: String) : TimelineItem {
        override val timeMillis get() = rec.file.timeMillis
    }

    /** 통화기록 한 줄. 그 통화의 녹음을 찾았으면 rec 에 */
    data class Call(val call: CallEntry, val rec: IndexedRecording? = null) : TimelineItem {
        override val timeMillis get() = call.timeMillis
        override val number get() = call.number
    }
}

/**
 * 폰 안 통화녹음 전체를 번호와 짝지어 둔 목록.
 * 한 번 짝지어진 녹음은 Store에 기억해서, 나중에 통화기록이 지워져도 유지된다.
 * 다시 찾는 동안에도 이전 결과(peek)는 그대로 보여줄 수 있다.
 */
object RecordingIndex {
    private val lock = Mutex()
    @Volatile private var cache: List<IndexedRecording>? = null
    @Volatile private var stale = true

    /** 다음 get() 때 새로 찾게 표시 (이전 결과는 남겨 둠) */
    fun invalidate() {
        stale = true
    }

    /** 지금 가지고 있는 결과 (없으면 null). 기다리지 않음 */
    fun peek(): List<IndexedRecording>? = cache

    suspend fun get(data: PhoneData, store: Store): List<IndexedRecording> = lock.withLock {
        val now = cache
        if (now != null && !stale) return@withLock now
        stale = false
        build(data, store).also { cache = it }
    }

    private suspend fun build(data: PhoneData, store: Store): List<IndexedRecording> = withContext(Dispatchers.IO) {
        val files = data.recordings()
        // 오래된 순으로 정렬해 두고, 녹음마다 그 시각 근처 통화만 이분 탐색으로 꺼낸다
        val calls = data.calls(5000).sortedBy { it.timeMillis }
        val times = LongArray(calls.size) { calls[it].timeMillis }
        val names by lazy { data.contactNumbersByName() }
        var changed = false
        val out = files.mapNotNull { f ->
            val cached = store.cachedMatch(f.cacheKey)
            val m = cached ?: run {
                val from = lowerBound(times, f.timeMillis - RecordingMatcher.AFTER_MS)
                val to = lowerBound(times, f.timeMillis + RecordingMatcher.BEFORE_MS + 1)
                val near = if (from < to) calls.subList(from, to) else emptyList()
                RecordingMatcher.match(f.parsed, f.timeMillis, near) { name -> names[name].orEmpty() }
            }
            // "확인 필요"는 연락처가 정리되면 바뀔 수 있으니 기억하지 않는다
            if (m != null && cached == null && m.method != MatchMethod.AMBIGUOUS) {
                store.cacheMatch(f.cacheKey, m)
                changed = true
            }
            m?.let { IndexedRecording(f, it) }
        }
        if (changed) store.persist()
        out
    }

    /** times 에서 value 이상이 처음 나오는 자리 */
    private fun lowerBound(times: LongArray, value: Long): Int {
        var lo = 0
        var hi = times.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (times[mid] < value) lo = mid + 1 else hi = mid
        }
        return lo
    }
}

/** 문자 · 통화 · 녹음을 따로 불러온다 (문자와 통화는 빠르고, 녹음은 오래 걸릴 수 있음) */
object Records {
    suspend fun sms(numbers: Set<String>, data: PhoneData): List<TimelineItem.Sms> = withContext(Dispatchers.IO) {
        numbers.flatMap { data.sms(it) }.sortedByDescending { it.timeMillis }.map { TimelineItem.Sms(it) }
    }

    suspend fun calls(numbers: Set<String>, data: PhoneData): List<CallEntry> = withContext(Dispatchers.IO) {
        data.calls(5000).filter { it.number in numbers }
    }

    fun recs(numbers: Set<String>, index: List<IndexedRecording>): List<TimelineItem.Rec> =
        index.mapNotNull { r ->
            r.match.numbers.firstOrNull { it in numbers }?.let { TimelineItem.Rec(r, it) }
        }.sortedByDescending { it.timeMillis }

    /** 통화마다 그 시각의 녹음을 붙인다 */
    fun callItems(calls: List<CallEntry>, recs: List<TimelineItem.Rec>?): List<TimelineItem.Call> {
        val byNumber = recs.orEmpty().groupBy { it.number }
        return calls.map { call ->
            val rec = byNumber[call.number]
                ?.filter {
                    it.timeMillis >= call.timeMillis - RecordingMatcher.BEFORE_MS &&
                        it.timeMillis <= call.timeMillis + RecordingMatcher.AFTER_MS
                }
                ?.minByOrNull { kotlin.math.abs(it.timeMillis - call.timeMillis) }
            TimelineItem.Call(call, rec?.rec)
        }.sortedByDescending { it.timeMillis }
    }
}
