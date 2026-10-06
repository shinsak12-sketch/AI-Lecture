package com.bosang.search.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import com.bosang.search.core.CaseNumber
import com.bosang.search.core.Hangul
import com.bosang.search.core.MatchMethod
import com.bosang.search.core.PhoneNumbers
import com.bosang.search.core.RecordingMatch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 사고번호와 사람(번호)의 연결. 관계는 사건마다 따로 */
data class CaseLink(
    val caseNo: String,
    val number: String,
    val role: String,
    val createdAt: Long,
)

object Roles {
    val all = listOf("피보험자", "계약자", "피해자", "배우자", "부", "모", "자녀", "형제", "지인", "정비소", "병원")
    const val CUSTOM = "직접 입력"
}

/**
 * 앱이 직접 저장하는 것은 이것뿐: 사건-번호 연결, 표시용 이름, 녹음 연결 기억, 최근 본 항목.
 * 문자·녹음 원본은 복사하지 않는다. 폰 안(앱 전용 폴더)에만 저장된다.
 */
class Store private constructor(private val file: File) {

    /** 화면 갱신용 신호 */
    val version = mutableIntStateOf(0)

    private val links = mutableListOf<CaseLink>()
    private val names = mutableMapOf<String, String>()
    private val recCache = mutableMapOf<String, RecordingMatch>()
    private val recent = mutableListOf<String>()

    // ---------- 조회 ----------
    @Synchronized fun caseNos(): List<String> = links.map { it.caseNo }.distinct().sortedDescending()

    @Synchronized fun linksForCase(caseNo: String): List<CaseLink> =
        links.filter { it.caseNo == caseNo }.sortedBy { it.createdAt }

    @Synchronized fun linksForNumber(number: String): List<CaseLink> =
        links.filter { it.number == number }.sortedByDescending { it.caseNo }

    @Synchronized fun registeredNumbers(): List<String> = links.map { it.number }.distinct()

    @Synchronized fun nameOf(number: String): String? = names[number]

    @Synchronized fun recentKeys(): List<String> = recent.toList()

    fun displayName(number: String): String = nameOf(number) ?: PhoneNumbers.format(number)

    fun searchCases(query: String): List<String> = caseNos().filter { CaseNumber.matches(it, query) }

    fun searchPeople(query: String): List<String> {
        val digits = query.filter { it.isDigit() }
        val text = query.trim()
        return registeredNumbers().filter { n ->
            (digits.length >= 3 && n.contains(digits)) || (text.isNotEmpty() && nameOf(n)?.let { Hangul.matches(it, text) } == true)
        }
    }

    // ---------- 변경 (화면에서 호출) ----------
    /** entries: (번호, 관계, 이름) */
    fun upsert(caseNo: String, entries: List<Triple<String, String, String?>>) {
        synchronized(this) {
            val now = System.currentTimeMillis()
            entries.forEachIndexed { i, (number, role, name) ->
                val idx = links.indexOfFirst { it.caseNo == caseNo && it.number == number }
                if (idx >= 0) links[idx] = links[idx].copy(role = role)
                else links.add(CaseLink(caseNo, number, role, now + i))
                if (!name.isNullOrBlank()) names[number] = name
            }
        }
        changed()
    }

    fun setRole(caseNo: String, number: String, role: String) {
        synchronized(this) {
            val idx = links.indexOfFirst { it.caseNo == caseNo && it.number == number }
            if (idx >= 0) links[idx] = links[idx].copy(role = role)
        }
        changed()
    }

    fun removeLink(caseNo: String, number: String) {
        synchronized(this) { links.removeAll { it.caseNo == caseNo && it.number == number } }
        changed()
    }

    fun deleteCase(caseNo: String) {
        synchronized(this) {
            links.removeAll { it.caseNo == caseNo }
            recent.remove("c:$caseNo")
        }
        changed()
    }

    /** 앱이 저장한 것 전부 지우기 (폰의 문자·녹음 원본은 그대로) */
    fun clearAll() {
        synchronized(this) {
            links.clear(); names.clear(); recCache.clear(); recent.clear()
        }
        changed()
    }

    fun touchRecent(key: String) {
        synchronized(this) {
            recent.remove(key)
            recent.add(0, key)
            while (recent.size > 12) recent.removeAt(recent.lastIndex)
            save()
        }
    }

    // ---------- 녹음 연결 기억 (백그라운드에서 호출) ----------
    @Synchronized fun cachedMatch(key: String): RecordingMatch? = recCache[key]

    @Synchronized fun cacheMatch(key: String, match: RecordingMatch) {
        recCache[key] = match
    }

    @Synchronized fun persist() = save()

    // ---------- 파일 ----------
    private fun changed() {
        synchronized(this) { save() }
        version.intValue = version.intValue + 1
    }

    private fun save() {
        val root = JSONObject()
        root.put("links", JSONArray().apply {
            links.forEach {
                put(JSONObject().put("c", it.caseNo).put("n", it.number).put("r", it.role).put("t", it.createdAt))
            }
        })
        root.put("names", JSONObject().apply { names.forEach { (k, v) -> put(k, v) } })
        root.put("rec", JSONObject().apply {
            recCache.forEach { (k, m) ->
                put(k, JSONObject().put("n", JSONArray(m.numbers)).put("m", m.method.name))
            }
        })
        root.put("recent", JSONArray(recent))
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(root.toString())
            tmp.delete()
        }
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText())
            root.optJSONArray("links")?.let { a ->
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    links.add(CaseLink(o.getString("c"), o.getString("n"), o.getString("r"), o.optLong("t")))
                }
            }
            root.optJSONObject("names")?.let { o -> o.keys().forEach { k -> names[k] = o.getString(k) } }
            root.optJSONObject("rec")?.let { o ->
                o.keys().forEach { k ->
                    val m = o.getJSONObject(k)
                    val arr = m.getJSONArray("n")
                    val nums = (0 until arr.length()).map { arr.getString(it) }
                    val method = runCatching { MatchMethod.valueOf(m.getString("m")) }.getOrNull()
                    if (method != null) recCache[k] = RecordingMatch(nums, method)
                }
            }
            root.optJSONArray("recent")?.let { a -> for (i in 0 until a.length()) recent.add(a.getString(i)) }
        } catch (e: Exception) {
            // 파일이 깨졌으면 백업해 두고 빈 상태로 시작
            file.renameTo(File(file.parentFile, "store.broken.${System.currentTimeMillis()}.json"))
            links.clear(); names.clear(); recCache.clear(); recent.clear()
        }
    }

    companion object {
        @Volatile private var instance: Store? = null

        fun get(context: Context): Store =
            instance ?: synchronized(this) {
                instance ?: Store(File(context.applicationContext.filesDir, "store.json")).also {
                    it.load()
                    instance = it
                }
            }
    }
}
