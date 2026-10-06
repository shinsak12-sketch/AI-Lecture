package com.bosang.search.data

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
}

/**
 * 폰 안 통화녹음 전체를 번호와 짝지어 둔 목록.
 * 한 번 짝지어진 녹음은 Store에 기억해서, 나중에 통화기록이 지워져도 유지된다.
 */
object RecordingIndex {
    private val lock = Mutex()
    private var cache: List<IndexedRecording>? = null

    fun invalidate() {
        cache = null
    }

    suspend fun get(data: PhoneData, store: Store): List<IndexedRecording> = lock.withLock {
        cache ?: build(data, store).also { cache = it }
    }

    private suspend fun build(data: PhoneData, store: Store): List<IndexedRecording> = withContext(Dispatchers.IO) {
        val files = data.recordings()
        val calls = data.calls(5000)
        val byName = HashMap<String, List<String>>()
        val out = files.mapNotNull { f ->
            val cached = store.cachedMatch(f.cacheKey)
            val m = cached ?: RecordingMatcher.match(f.parsed, f.timeMillis, calls) { name ->
                byName.getOrPut(name) { data.numbersForName(name) }
            }
            // "확인 필요"는 연락처가 정리되면 바뀔 수 있으니 기억하지 않는다
            if (m != null && cached == null && m.method != MatchMethod.AMBIGUOUS) store.cacheMatch(f.cacheKey, m)
            m?.let { IndexedRecording(f, it) }
        }
        store.persist()
        out
    }

    /** 여러 번호의 문자 + 녹음을 최신순으로 한 줄에 */
    suspend fun timeline(numbers: Set<String>, data: PhoneData, store: Store): List<TimelineItem> {
        val index = get(data, store)
        return withContext(Dispatchers.IO) {
            val recs = index.mapNotNull { r ->
                val who = r.match.numbers.firstOrNull { it in numbers } ?: return@mapNotNull null
                TimelineItem.Rec(r, who)
            }
            val sms = numbers.flatMap { data.sms(it) }.map { TimelineItem.Sms(it) }
            (recs + sms).sortedByDescending { it.timeMillis }
        }
    }
}
