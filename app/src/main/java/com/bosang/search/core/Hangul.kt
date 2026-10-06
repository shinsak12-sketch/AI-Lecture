package com.bosang.search.core

/** 이름 검색: 그냥 포함 + 초성 검색 ("ㅎㄱㄷ" → 홍길동) */
object Hangul {
    private const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"

    fun initials(s: String): String = buildString {
        s.forEach { c -> append(if (c in '가'..'힣') CHO[(c - '가') / 588] else c) }
    }

    /** 연락처 색인 순서 */
    val INDEX = listOf("ㄱ", "ㄴ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅅ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ", "A", "#")

    private val PLAIN = mapOf('ㄲ' to 'ㄱ', 'ㄸ' to 'ㄷ', 'ㅃ' to 'ㅂ', 'ㅆ' to 'ㅅ', 'ㅉ' to 'ㅈ')

    /** 연락처 묶음 글자: 쌍자음은 홑자음으로, 영문은 A, 나머지는 # */
    fun indexOf(name: String): String {
        val c = name.trim().firstOrNull() ?: return "#"
        return when {
            c in '가'..'힣' -> CHO[(c - '가') / 588].let { PLAIN[it] ?: it }.toString()
            c in CHO -> (PLAIN[c] ?: c).toString()
            c in 'a'..'z' || c in 'A'..'Z' -> "A"
            else -> "#"
        }
    }

    fun matches(name: String, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        if (name.contains(q, ignoreCase = true)) return true
        val compact = q.replace(" ", "")
        if (compact.isNotEmpty() && compact.all { it in CHO }) {
            return initials(name).replace(" ", "").contains(compact)
        }
        return false
    }
}
