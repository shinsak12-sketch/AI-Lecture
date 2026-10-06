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
    private val issues = mutableListOf<Issue>()
    private val photos = mutableListOf<CasePhoto>()
    private val appts = mutableListOf<Appointment>()
    private val repairs = mutableListOf<Repair>()
    private var callAssistOn = false
    private var showNoticesOn = false
    private val quietNumbers = mutableSetOf<String>()

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

    // ---------- 특이사항 ----------
    /** 사건 화면: 그 사건 사람들 번호의 특이사항 + 그 사건에서 직접 쓴 것, 최신순 */
    @Synchronized fun issuesForCase(caseNo: String): List<Issue> {
        val numbers = links.filter { it.caseNo == caseNo }.map { it.number }.toSet()
        return issues.filter { it.caseNo == caseNo || it.subject in numbers }.sortedByDescending { it.source?.timeMillis ?: it.createdAt }
    }

    /** 사람 화면: 그 번호의 특이사항 (그 사람이 주장한 것 포함), 최신순 */
    @Synchronized fun issuesForNumber(number: String): List<Issue> =
        issues.filter { it.subject == number || it.claimant == number }.sortedByDescending { it.source?.timeMillis ?: it.createdAt }

    @Synchronized fun issue(id: String): Issue? = issues.firstOrNull { it.id == id }

    fun saveIssue(issue: Issue) {
        synchronized(this) {
            val i = issues.indexOfFirst { it.id == issue.id }
            if (i >= 0) issues[i] = issue else issues.add(issue)
        }
        changed()
    }

    fun deleteIssue(id: String) {
        synchronized(this) { issues.removeAll { it.id == id } }
        changed()
    }

    // ---------- 사진 ----------
    @Synchronized fun photosForCase(caseNo: String): List<CasePhoto> =
        photos.filter { it.caseNo == caseNo }.sortedByDescending { it.takenAt }

    @Synchronized fun photo(id: String): CasePhoto? = photos.firstOrNull { it.id == id }

    @Synchronized fun hasPhoto(id: String): Boolean = photos.any { it.id == id }

    fun addPhotos(list: List<CasePhoto>) {
        if (list.isEmpty()) return
        synchronized(this) {
            list.forEach { p -> if (photos.none { it.id == p.id }) photos.add(p) }
        }
        changed()
    }

    fun updatePhoto(p: CasePhoto) {
        synchronized(this) {
            val i = photos.indexOfFirst { it.id == p.id }
            if (i >= 0) photos[i] = p
        }
        changed()
    }

    fun removePhoto(id: String) {
        synchronized(this) { photos.removeAll { it.id == id } }
        changed()
    }

    // ---------- 일정 ----------
    /** 사건 화면: 그 사건에서 잡은 약속 + 그 사건 사람들과의 약속, 시간순 */
    @Synchronized fun apptsForCase(caseNo: String): List<Appointment> {
        val numbers = links.filter { it.caseNo == caseNo }.map { it.number }.toSet()
        return appts.filter { it.caseNo == caseNo || (it.caseNo == null && it.number in numbers) }.sortedBy { it.at }
    }

    @Synchronized fun apptsForNumber(number: String): List<Appointment> = appts.filter { it.number == number }.sortedBy { it.at }

    /** 다가오는 약속 (안 끝난 것, from 이후) */
    @Synchronized fun upcomingAppts(from: Long): List<Appointment> = appts.filter { !it.done && it.at >= from }.sortedBy { it.at }

    @Synchronized fun allAppts(): List<Appointment> = appts.toList()

    @Synchronized fun appt(id: String): Appointment? = appts.firstOrNull { it.id == id }

    fun saveAppt(a: Appointment) {
        synchronized(this) {
            val i = appts.indexOfFirst { it.id == a.id }
            if (i >= 0) appts[i] = a else appts.add(a)
        }
        changed()
    }

    fun deleteAppt(id: String) {
        synchronized(this) { appts.removeAll { it.id == id } }
        changed()
    }

    // ---------- 입고 · 출고 ----------
    @Synchronized fun repairsForCase(caseNo: String): List<Repair> =
        repairs.filter { it.caseNo == caseNo }.sortedWith(compareBy<Repair> { !it.open }.thenByDescending { it.inDate })

    /** 아직 출고 안 된 차량 */
    @Synchronized fun openRepairs(): List<Repair> = repairs.filter { it.open }.sortedBy { it.inDate }

    @Synchronized fun repair(id: String): Repair? = repairs.firstOrNull { it.id == id }

    fun saveRepair(r: Repair) {
        synchronized(this) {
            val i = repairs.indexOfFirst { it.id == r.id }
            if (i >= 0) repairs[i] = r else repairs.add(r)
        }
        changed()
    }

    fun deleteRepair(id: String) {
        synchronized(this) { repairs.removeAll { it.id == id } }
        changed()
    }

    // ---------- 설정 ----------
    /** 캐치콜 · 매너콜 같은 통화 알림 문자도 보일지 (기본: 숨김) */
    @Synchronized fun showNotices(): Boolean = showNoticesOn

    fun setShowNotices(on: Boolean) {
        synchronized(this) { showNoticesOn = on }
        changed()
    }

    @Synchronized fun callAssist(): Boolean = callAssistOn

    fun setCallAssist(on: Boolean) {
        synchronized(this) { callAssistOn = on }
        changed()
    }

    /** 통화 끝나도 묻지 않을 번호 (가족 등) */
    @Synchronized fun isQuiet(number: String): Boolean = number in quietNumbers

    fun setQuiet(number: String, quiet: Boolean) {
        synchronized(this) { if (quiet) quietNumbers.add(number) else quietNumbers.remove(number) }
        changed()
    }

    @Synchronized fun quietCount(): Int = quietNumbers.size

    fun clearQuiet() {
        synchronized(this) { quietNumbers.clear() }
        changed()
    }

    // ---------- 변경 (화면에서 호출) ----------
    /** entries: (번호, 관계, 이름) */
    /** 사건 없이 이름만 기억 (특이사항 관련자) */
    fun rememberName(number: String, name: String) {
        if (name.isBlank()) return
        synchronized(this) { names[number] = name }
        changed()
    }

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
            // 번호 기록에 붙은 특이사항은 남기고, 사건에서만 쓴 것만 지움
            issues.removeAll { it.caseNo == caseNo && it.subject == null }
            issues.replaceAll { if (it.caseNo == caseNo) it.copy(caseNo = null) else it }
            photos.removeAll { it.caseNo == caseNo }
            repairs.removeAll { it.caseNo == caseNo }
            appts.replaceAll { if (it.caseNo == caseNo) it.copy(caseNo = null) else it }
            recent.remove("c:$caseNo")
        }
        changed()
    }

    /** 앱이 저장한 것 전부 지우기 (폰의 문자·녹음 원본은 그대로) */
    fun clearAll() {
        synchronized(this) {
            links.clear(); names.clear(); recCache.clear(); recent.clear(); issues.clear(); photos.clear()
            appts.clear(); repairs.clear()
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
        appContext?.let { com.bosang.search.widget.SearchWidget.refresh(it) }
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
        appContext?.let {
            com.bosang.search.widget.SearchWidget.refresh(it)
            com.bosang.search.remind.Reminders.sync(it)
        }
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
        root.put("issues", JSONArray().apply { issues.forEach { put(it.toJson()) } })
        root.put("photos", JSONArray().apply { photos.forEach { put(it.toJson()) } })
        root.put("appts", JSONArray().apply { appts.forEach { put(it.toJson()) } })
        root.put("repairs", JSONArray().apply { repairs.forEach { put(it.toJson()) } })
        root.put("prefs", JSONObject().put("callAssist", callAssistOn).put("notices", showNoticesOn).put("quiet", JSONArray(quietNumbers.toList())))
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
            root.optJSONArray("issues")?.let { a ->
                for (i in 0 until a.length()) Issue.fromJson(a.getJSONObject(i))?.let { issues.add(it) }
            }
            root.optJSONArray("photos")?.let { a ->
                for (i in 0 until a.length()) runCatching { CasePhoto.fromJson(a.getJSONObject(i)) }.getOrNull()?.let { photos.add(it) }
            }
            root.optJSONArray("appts")?.let { a ->
                for (i in 0 until a.length()) Appointment.fromJson(a.getJSONObject(i))?.let { appts.add(it) }
            }
            root.optJSONArray("repairs")?.let { a ->
                for (i in 0 until a.length()) Repair.fromJson(a.getJSONObject(i))?.let { repairs.add(it) }
            }
            callAssistOn = root.optJSONObject("prefs")?.optBoolean("callAssist") ?: false
            showNoticesOn = root.optJSONObject("prefs")?.optBoolean("notices") ?: false
            root.optJSONObject("prefs")?.optJSONArray("quiet")?.let { a -> for (i in 0 until a.length()) quietNumbers.add(a.getString(i)) }
        } catch (e: Exception) {
            // 파일이 깨졌으면 백업해 두고 빈 상태로 시작
            file.renameTo(File(file.parentFile, "store.broken.${System.currentTimeMillis()}.json"))
            links.clear(); names.clear(); recCache.clear(); recent.clear(); issues.clear(); photos.clear()
            appts.clear(); repairs.clear()
        }
    }

    companion object {
        @Volatile private var instance: Store? = null
        @Volatile private var appContext: Context? = null

        fun get(context: Context): Store =
            instance ?: synchronized(this) {
                instance ?: Store(File(context.applicationContext.filesDir, "store.json")).also {
                    appContext = context.applicationContext
                    it.load()
                    instance = it
                }
            }
    }
}
