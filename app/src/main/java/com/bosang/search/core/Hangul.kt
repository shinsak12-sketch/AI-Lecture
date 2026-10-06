package com.bosang.search.core

/** 이름 검색: 그냥 포함 + 초성 검색 ("ㅎㄱㄷ" → 홍길동) */
object Hangul {
    private const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"

    fun initials(s: String): String = buildString {
        s.forEach { c -> append(if (c in '가'..'힣') CHO[(c - '가') / 588] else c) }
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
