package com.bosang.search.core

import java.time.LocalDateTime

/**
 * 갤럭시 통화녹음 파일 이름을 해석한 결과.
 * - 저장 안 된 번호: "통화 녹음 01011112222_261002_174946" → label = 번호
 * - 저장된 연락처: "통화 녹음 홍길동_261002_174946"     → label = 연락처 이름
 */
data class ParsedRecording(
    val label: String,
    val isNumber: Boolean,
    val time: LocalDateTime,
) {
    val number: String get() = if (isNumber) PhoneNumbers.normalize(label) else ""
}

object RecordingName {
    private val prefix = Regex("^통화\\s*녹음\\s*")
    // 이름에 '_'가 있어도 되도록 뒤에서부터 날짜_시간을 떼어낸다. "(1)" 같은 중복 꼬리도 허용
    private val tail = Regex("^(.+?)_(\\d{6})_(\\d{6})(?:\\s*\\(\\d+\\))?$")

    fun parse(displayName: String): ParsedRecording? {
        val base = stripExtension(displayName.trim())
        val p = prefix.find(base) ?: return null
        val rest = base.substring(p.range.last + 1).trim()
        val m = tail.matchEntire(rest) ?: return null
        val label = m.groupValues[1].trim()
        if (label.isEmpty()) return null
        val d = m.groupValues[2]
        val t = m.groupValues[3]
        val time = try {
            LocalDateTime.of(
                2000 + d.substring(0, 2).toInt(), d.substring(2, 4).toInt(), d.substring(4, 6).toInt(),
                t.substring(0, 2).toInt(), t.substring(2, 4).toInt(), t.substring(4, 6).toInt(),
            )
        } catch (e: Exception) {
            return null
        }
        return ParsedRecording(label, PhoneNumbers.looksLikeNumber(label), time)
    }

    private fun stripExtension(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0) return name
        val ext = name.substring(dot + 1)
        val isExt = ext.length in 2..4 && ext.all { it.isLetterOrDigit() } && !ext.all { it.isDigit() }
        return if (isExt) name.substring(0, dot) else name
    }
}
