package com.bosang.search.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 기록 안 검색어.
 * - 날짜: 오늘 · 어제 · 그제 · 이번주 · 지난주 · 이번달 · 지난달 · 10/3 · 10.3 · 10월 3일 · 10월 · 3일 · 2026.10.3 · 10/1~10/5
 * - 시각: 14:30 · 14시 · 오후 3시 (오전/오후 없이 1~11시면 오전·오후 둘 다)
 * - 나머지 낱말: 내용 · 이름 · 관계 · 번호 · 특이사항에 모두 들어 있어야 함 (초성도 됨)
 * 날짜를 여럿 쓰면 그중 하나에 맞으면 된다.
 */
class RecordQuery private constructor(
    val raw: String,
    private val dates: List<(LocalDate) -> Boolean>,
    private val times: List<Pair<Int, Int?>>,
    val terms: List<String>,
    /** 알아들은 날짜 · 시각 (화면에 보여줌) */
    val labels: List<String>,
) {
    val isEmpty: Boolean get() = dates.isEmpty() && times.isEmpty() && terms.isEmpty()

    fun matches(timeMillis: Long, text: String, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (isEmpty) return true
        if (dates.isNotEmpty() || times.isNotEmpty()) {
            val t = Instant.ofEpochMilli(timeMillis).atZone(zone)
            if (dates.isNotEmpty() && dates.none { it(t.toLocalDate()) }) return false
            if (times.isNotEmpty() && times.none { (h, m) -> t.hour == h && (m == null || t.minute == m) }) return false
        }
        return terms.all { termIn(text, it) }
    }

    /** 날짜 · 시각 없이 글자만 (특이사항처럼 시각이 애매한 것) */
    fun matchesText(text: String): Boolean = terms.all { termIn(text, it) }

    companion object {
        private const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"

        fun termIn(text: String, term: String): Boolean {
            if (text.contains(term, ignoreCase = true)) return true
            val compactText = text.replace(" ", "")
            if (compactText.contains(term, ignoreCase = true)) return true
            if (term.all { it in CHO }) return Hangul.initials(compactText).contains(term)
            // 번호: 하이픈이 있든 없든
            val digits = term.filter { it.isDigit() }
            if (digits.length >= 3 && term.all { it.isDigit() || it == '-' }) {
                return text.split(' ', '\n').any { w -> w.filter { it.isDigit() }.contains(digits) }
            }
            return false
        }

        private fun md(m: Int, d: Int): ((LocalDate) -> Boolean)? =
            if (m in 1..12 && d in 1..31) ({ x: LocalDate -> x.monthValue == m && x.dayOfMonth == d }) else null

        private fun year(y: Int) = if (y < 100) 2000 + y else y

        fun parse(input: String, today: LocalDate = LocalDate.now()): RecordQuery {
            var s = " " + input.trim() + " "
            val dates = ArrayList<(LocalDate) -> Boolean>()
            val times = ArrayList<Pair<Int, Int?>>()
            val labels = ArrayList<String>()

            fun take(regex: Regex, f: (MatchResult) -> Boolean) {
                s = regex.replace(s) { m -> if (f(m)) " " else m.value }
            }

            // 기간: 10/1~10/5, 10.1-10.5
            take(Regex("(?<![\\d.])(\\d{1,2})[/.](\\d{1,2})\\s*[~-]\\s*(\\d{1,2})[/.](\\d{1,2})(?![\\d.])")) { m ->
                val (m1, d1, m2, d2) = m.destructured
                val a = runCatching { LocalDate.of(today.year, m1.toInt(), d1.toInt()) }.getOrNull()
                var b = runCatching { LocalDate.of(today.year, m2.toInt(), d2.toInt()) }.getOrNull()
                if (a == null || b == null) return@take false
                if (b.isBefore(a)) b = b.plusYears(1)
                val (from, to) = if (a.isAfter(today)) a.minusYears(1) to b.minusYears(1) else a to b
                dates.add { x -> !x.isBefore(from) && !x.isAfter(to) }
                labels.add("${m1.toInt()}/${d1.toInt()}~${m2.toInt()}/${d2.toInt()}")
                true
            }
            // 연-월-일: 2026.10.3, 26-10-3, 2026년 10월 3일
            take(Regex("(?<!\\d)(\\d{2}|\\d{4})(?:[./-]|년\\s*)(\\d{1,2})(?:[./-]|월\\s*)(\\d{1,2})일?(?![\\d.])")) { m ->
                val (y, mo, d) = m.destructured
                val date = runCatching { LocalDate.of(year(y.toInt()), mo.toInt(), d.toInt()) }.getOrNull() ?: return@take false
                dates.add { it == date }
                labels.add("${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일")
                true
            }
            // 월 일: 10월 3일, 10/3, 10.3
            take(Regex("(?<![\\d.])(\\d{1,2})월\\s*(\\d{1,2})일?(?![\\d])|(?<![\\d.])(\\d{1,2})[/.](\\d{1,2})(?![\\d.:])")) { m ->
                val mo = (m.groups[1] ?: m.groups[3])!!.value.toInt()
                val d = (m.groups[2] ?: m.groups[4])!!.value.toInt()
                val f = md(mo, d) ?: return@take false
                dates.add(f)
                labels.add("${mo}월 ${d}일")
                true
            }
            // 연 월: 2026년 10월
            take(Regex("(?<!\\d)(\\d{4})년\\s*(\\d{1,2})월(?!\\s*\\d)")) { m ->
                val (y, mo) = m.destructured
                val yy = y.toInt()
                val mm = mo.toInt()
                if (mm !in 1..12) return@take false
                dates.add { it.year == yy && it.monthValue == mm }
                labels.add("${yy}년 ${mm}월")
                true
            }
            // 월: 10월
            take(Regex("(?<![\\d.])(\\d{1,2})월(?!\\s*\\d)")) { m ->
                val mm = m.groupValues[1].toInt()
                if (mm !in 1..12) return@take false
                dates.add { it.monthValue == mm }
                labels.add("${mm}월")
                true
            }
            // 일: 3일
            take(Regex("(?<![\\d.])(\\d{1,2})일(?![가-힣])")) { m ->
                val dd = m.groupValues[1].toInt()
                if (dd !in 1..31) return@take false
                dates.add { it.dayOfMonth == dd }
                labels.add("${dd}일")
                true
            }
            // 오늘 · 어제 · 그제 · 이번주 · 지난주 · 이번달 · 지난달
            val monday = today.with(DayOfWeek.MONDAY)
            val words = listOf(
                "오늘" to { x: LocalDate -> x == today },
                "어제" to { x: LocalDate -> x == today.minusDays(1) },
                "그저께" to { x: LocalDate -> x == today.minusDays(2) },
                "그제" to { x: LocalDate -> x == today.minusDays(2) },
                "이번주" to { x: LocalDate -> !x.isBefore(monday) && !x.isAfter(today) },
                "지난주" to { x: LocalDate -> !x.isBefore(monday.minusWeeks(1)) && x.isBefore(monday) },
                "이번달" to { x: LocalDate -> x.year == today.year && x.monthValue == today.monthValue },
                "지난달" to { x: LocalDate -> today.minusMonths(1).let { p -> x.year == p.year && x.monthValue == p.monthValue } },
            )
            words.forEach { (w, f) ->
                take(Regex("(?<![가-힣])" + w.replace("주", "\\s?주").replace("달", "\\s?달") + "(?![가-힣])")) {
                    dates.add(f)
                    labels.add(w)
                    true
                }
            }

            fun addHour(h: Int, min: Int?, ampm: String?) {
                when {
                    ampm == "오후" && h in 1..11 -> times.add(h + 12 to min)
                    ampm == "오전" && h == 12 -> times.add(0 to min)
                    ampm == null && h in 1..11 -> {
                        times.add(h to min)
                        times.add(h + 12 to min)
                    }
                    else -> times.add(h to min)
                }
            }
            // 시각: 14:30, 오후 3시 20분, 3시
            take(Regex("(오전|오후)?\\s*(?<!\\d)(\\d{1,2}):(\\d{2})(?!\\d)")) { m ->
                val h = m.groupValues[2].toInt()
                val mi = m.groupValues[3].toInt()
                if (h > 23 || mi > 59) return@take false
                addHour(h, mi, m.groupValues[1].ifEmpty { null })
                labels.add(m.value.trim())
                true
            }
            take(Regex("(오전|오후)?\\s*(?<!\\d)(\\d{1,2})시(?:\\s*(\\d{1,2})분)?(?![가-힣])")) { m ->
                val h = m.groupValues[2].toInt()
                if (h > 24) return@take false
                addHour(h % 24, m.groupValues[3].ifEmpty { null }?.toInt(), m.groupValues[1].ifEmpty { null })
                labels.add(m.value.trim())
                true
            }

            val terms = s.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
            return RecordQuery(input, dates, times, terms, labels)
        }
    }
}
