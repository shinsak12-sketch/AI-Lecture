package com.bosang.search.core

/** 사고번호: "26-00000000" (연도 2자리 - 일련번호 8자리) */
object CaseNumber {
    private val full = Regex("^\\d{2}-\\d{8}$")

    fun isValid(s: String): Boolean = full.matches(s)

    fun of(year2: String, serial: String): String = "$year2-$serial"

    fun year2(year: Int): String = (year % 100).toString().padStart(2, '0')

    fun digits(s: String): String = s.filter { it.isDigit() }

    /** 검색어의 숫자가 사고번호 숫자 안에 들어 있으면 일치 ("26-0001", "12345" 모두 가능) */
    fun matches(caseNo: String, query: String): Boolean {
        val q = digits(query)
        return q.isNotEmpty() && digits(caseNo).contains(q)
    }
}
