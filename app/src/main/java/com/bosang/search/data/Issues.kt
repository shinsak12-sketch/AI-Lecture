package com.bosang.search.data

import com.bosang.search.core.Money
import org.json.JSONArray
import org.json.JSONObject

/** 특이사항 구분 */
enum class IssueKind(val label: String) {
    FAULT("과실"),
    AMOUNT("금액"),
    DAMAGE("파손부위"),
    STATEMENT("진술"),
    SITE("현장"),
    ETC("기타"),
}

/** 특이사항이 붙은 기록 */
data class IssueSource(
    /** "rec" · "sms" · "call" */
    val type: String,
    val number: String,
    val timeMillis: Long,
    /** 기록을 다시 찾을 열쇠 (녹음: 파일 열쇠, 문자: 문자 id, 통화: 시각) */
    val key: String,
    /** 녹음의 어느 지점인지 (ms) */
    val offsetMs: Long? = null,
    /** 통화 길이(초) 또는 녹음 길이(ms) 표시용 */
    val lengthMs: Long? = null,
) {
    /** 타임라인 카드와 짝을 맞추는 값 */
    val recordKey: String get() = recordKey(type, key)

    companion object {
        fun recordKey(type: String, key: String) = "$type:$key"
    }
}

data class Issue(
    val id: String,
    val caseNo: String,
    val kind: IssueKind,
    val createdAt: Long,
    /** 주장한 사람 (번호) */
    val claimant: String? = null,
    val source: IssueSource? = null,
    val text: String = "",
    // 과실: 우리 쪽 과실 (0~100)
    val accidentType: String? = null,
    val baseOurs: Int? = null,
    val claimOurs: Int? = null,
    // 금액
    val amountKind: String? = null,
    val claimAmount: Long? = null,
    val ourAmount: Long? = null,
    // 파손부위
    val parts: List<String> = emptyList(),
    val claimNote: String? = null,
    /** 그린 그림 (앱 안 파일 이름) */
    val drawing: String? = null,
    /** 붙인 사진 (CasePhoto.id) */
    val photos: List<String> = emptyList(),
) {
    /** 한 줄 요약. nameOf 로 번호 → 이름 */
    fun summary(nameOf: (String) -> String): String {
        val who = claimant?.let(nameOf)
        return when (kind) {
            IssueKind.FAULT -> {
                val claim = claimOurs?.let { "$it : ${100 - it} 주장" }
                val base = baseOurs?.let { "기준 $it : ${100 - it}" }
                listOfNotNull(listOfNotNull(who, claim).joinToString(" ").ifBlank { null }, base)
                    .joinToString(" · ")
                    .ifBlank { firstLine() ?: "과실 특이사항" }
            }
            IssueKind.AMOUNT -> {
                val amt = claimAmount?.let { Money.short(it) + " 요구" }
                listOfNotNull(who, amountKind, amt).joinToString(" ").ifBlank { firstLine() ?: "금액 특이사항" }
            }
            IssueKind.DAMAGE -> {
                val p = parts.joinToString("·").ifBlank { null }
                listOfNotNull(who, p, claimNote).joinToString(" ").ifBlank { firstLine() ?: "파손 특이사항" }
            }
            else -> listOfNotNull(who, firstLine()).joinToString(" · ").ifBlank { kind.label + " 특이사항" }
        }
    }

    fun firstLine(): String? = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("c", caseNo)
        put("k", kind.name)
        put("t", createdAt)
        claimant?.let { put("who", it) }
        source?.let { s ->
            put(
                "src",
                JSONObject().put("type", s.type).put("n", s.number).put("t", s.timeMillis).put("key", s.key).apply {
                    s.offsetMs?.let { put("off", it) }
                    s.lengthMs?.let { put("len", it) }
                },
            )
        }
        put("text", text)
        accidentType?.let { put("at", it) }
        baseOurs?.let { put("base", it) }
        claimOurs?.let { put("claim", it) }
        amountKind?.let { put("ak", it) }
        claimAmount?.let { put("ca", it) }
        ourAmount?.let { put("oa", it) }
        if (parts.isNotEmpty()) put("parts", JSONArray(parts))
        claimNote?.let { put("cn", it) }
        drawing?.let { put("draw", it) }
        if (photos.isNotEmpty()) put("photos", JSONArray(photos))
    }

    companion object {
        fun fromJson(o: JSONObject): Issue? {
            val kind = runCatching { IssueKind.valueOf(o.getString("k")) }.getOrNull() ?: return null
            val src = o.optJSONObject("src")?.let { s ->
                IssueSource(
                    type = s.getString("type"),
                    number = s.getString("n"),
                    timeMillis = s.getLong("t"),
                    key = s.getString("key"),
                    offsetMs = if (s.has("off")) s.getLong("off") else null,
                    lengthMs = if (s.has("len")) s.getLong("len") else null,
                )
            }
            fun strings(name: String): List<String> =
                o.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            return Issue(
                id = o.getString("id"),
                caseNo = o.getString("c"),
                kind = kind,
                createdAt = o.optLong("t"),
                claimant = o.optString("who").ifEmpty { null },
                source = src,
                text = o.optString("text"),
                accidentType = o.optString("at").ifEmpty { null },
                baseOurs = if (o.has("base")) o.getInt("base") else null,
                claimOurs = if (o.has("claim")) o.getInt("claim") else null,
                amountKind = o.optString("ak").ifEmpty { null },
                claimAmount = if (o.has("ca")) o.getLong("ca") else null,
                ourAmount = if (o.has("oa")) o.getLong("oa") else null,
                parts = strings("parts"),
                claimNote = o.optString("cn").ifEmpty { null },
                drawing = o.optString("draw").ifEmpty { null },
                photos = strings("photos"),
            )
        }

        val ACCIDENT_TYPES = listOf("진로변경", "추돌", "교차로 직진", "교차로 좌회전", "신호위반", "중앙선 침범", "주차장", "후진", "보행자", "기타")
        val AMOUNT_KINDS = listOf("추정수리비 (현금)", "수리비", "렌트비", "휴차료", "기타")
        val PARTS = listOf(
            "앞범퍼", "뒷범퍼", "본넷", "트렁크", "운전석 앞문", "운전석 뒷문", "조수석 앞문", "조수석 뒷문",
            "앞휀더", "뒤휀더", "헤드램프", "리어램프", "사이드미러", "휠", "기타",
        )
        val DAMAGE_CLAIMS = listOf("추가 파손 주장", "교환 요구", "기존 손상 의심", "수리 범위 다툼")
    }
}

/** 사건 사진 */
data class CasePhoto(
    val id: String,
    val caseNo: String,
    /** content:// 또는 앱 안 파일 (file 이름만) */
    val uri: String,
    /** "camera" · "album" · "mms" */
    val source: String,
    val kind: String? = null,
    val takenAt: Long,
    val addedAt: Long,
    /** 문자로 받은 경우 보낸 번호 */
    val from: String? = null,
    /** 그 위에 그린 그림을 합친 파일 (앱 안 파일 이름) */
    val marked: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("c", caseNo); put("u", uri); put("s", source)
        kind?.let { put("k", it) }
        put("t", takenAt); put("a", addedAt)
        from?.let { put("f", it) }
        marked?.let { put("m", it) }
    }

    companion object {
        val KINDS = listOf("파손", "현장", "서류", "기타")

        fun fromJson(o: JSONObject) = CasePhoto(
            id = o.getString("id"),
            caseNo = o.getString("c"),
            uri = o.getString("u"),
            source = o.getString("s"),
            kind = o.optString("k").ifEmpty { null },
            takenAt = o.optLong("t"),
            addedAt = o.optLong("a"),
            from = o.optString("f").ifEmpty { null },
            marked = o.optString("m").ifEmpty { null },
        )
    }
}
