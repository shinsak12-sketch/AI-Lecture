package com.bosang.search.core

/** 전화번호 표기를 하나로 통일하고(숫자만), 보기 좋게 다시 꾸민다. */
object PhoneNumbers {

    /** "010-1111-2222", "+82 10 1111 2222", "0082..." → "01011112222" */
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var d = raw.filter { it.isDigit() }
        if (d.startsWith("0082")) {
            d = d.substring(4).let { if (it.startsWith("0")) it else "0$it" }
        } else if (d.startsWith("82") && d.length >= 10) {
            d = d.substring(2).let { if (it.startsWith("0")) it else "0$it" }
        }
        return d
    }

    /** 녹음 파일 이름의 앞부분이 번호인지(연락처 이름이 아닌지) */
    fun looksLikeNumber(s: String): Boolean {
        val t = s.trim()
        if (t.isEmpty()) return false
        if (!t.all { it.isDigit() || it in "+-() " }) return false
        return t.count { it.isDigit() } >= 7
    }

    fun format(raw: String?): String {
        val n = normalize(raw)
        if (n.isEmpty()) return raw.orEmpty()
        return when {
            n.startsWith("02") && n.length == 9 -> "02-${n.substring(2, 5)}-${n.substring(5)}"
            n.startsWith("02") && n.length == 10 -> "02-${n.substring(2, 6)}-${n.substring(6)}"
            n.startsWith("0") && n.length == 11 -> "${n.substring(0, 3)}-${n.substring(3, 7)}-${n.substring(7)}"
            n.startsWith("0") && n.length == 10 -> "${n.substring(0, 3)}-${n.substring(3, 6)}-${n.substring(6)}"
            n.startsWith("0") && n.length == 12 -> "${n.substring(0, 4)}-${n.substring(4, 8)}-${n.substring(8)}"
            n.length == 8 && n[0] == '1' -> "${n.substring(0, 4)}-${n.substring(4)}"
            else -> n
        }
    }
}
