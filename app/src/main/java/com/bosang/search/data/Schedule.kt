package com.bosang.search.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

enum class ApptKind(val label: String) {
    MEET("면담"),
    SITE("현장 확인"),
    CALL("전화 약속"),
    DOCS("서류"),
    VISIT("방문"),
    ETC("기타"),
}

/** 통화하고 잡은 약속 · 일정 */
data class Appointment(
    val id: String,
    val caseNo: String?,
    /** 약속한 사람 */
    val number: String?,
    val kind: ApptKind,
    val at: Long,
    val place: String = "",
    val memo: String = "",
    /** 몇 분 전에 알릴지 (0 = 정시) */
    val remind: List<Int> = listOf(60),
    val done: Boolean = false,
    val createdAt: Long,
    /** 이 약속을 잡은 통화 시각 */
    val callTime: Long? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        caseNo?.let { put("case", it) }
        number?.let { put("n", it) }
        put("kind", kind.name)
        put("at", at)
        put("place", place)
        put("memo", memo)
        put("remind", JSONArray(remind))
        put("done", done)
        put("created", createdAt)
        callTime?.let { put("call", it) }
    }

    companion object {
        val REMIND_OPTIONS = listOf(0, 10, 30, 60, 180, 1440)

        fun fromJson(o: JSONObject): Appointment? = runCatching {
            val r = o.optJSONArray("remind")
            Appointment(
                id = o.getString("id"),
                caseNo = o.optString("case").ifEmpty { null },
                number = o.optString("n").ifEmpty { null },
                kind = runCatching { ApptKind.valueOf(o.getString("kind")) }.getOrDefault(ApptKind.ETC),
                at = o.getLong("at"),
                place = o.optString("place"),
                memo = o.optString("memo"),
                remind = if (r == null) listOf(60) else (0 until r.length()).map { r.getInt(it) },
                done = o.optBoolean("done"),
                createdAt = o.optLong("created"),
                callTime = if (o.has("call")) o.getLong("call") else null,
            )
        }.getOrNull()
    }
}

/** 공업사에 들어간 차량: 입고일 · 출고 예정 · 출고 */
data class Repair(
    val id: String,
    val caseNo: String,
    val shop: String,
    val shopNumber: String? = null,
    val carNo: String = "",
    val inDate: LocalDate,
    /** 출고 예정일 (없으면 입고일 + limitDays) */
    val expectedOut: LocalDate? = null,
    val limitDays: Int = 7,
    val outDate: LocalDate? = null,
    val memo: String = "",
    val createdAt: Long,
) {
    val open: Boolean get() = outDate == null

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("case", caseNo)
        put("shop", shop)
        shopNumber?.let { put("tel", it) }
        put("car", carNo)
        put("in", inDate.toEpochDay())
        expectedOut?.let { put("due", it.toEpochDay()) }
        put("limit", limitDays)
        outDate?.let { put("out", it.toEpochDay()) }
        put("memo", memo)
        put("created", createdAt)
    }

    companion object {
        val LIMIT_OPTIONS = listOf(3, 5, 7, 10, 14, 21)

        fun fromJson(o: JSONObject): Repair? = runCatching {
            Repair(
                id = o.getString("id"),
                caseNo = o.getString("case"),
                shop = o.optString("shop"),
                shopNumber = o.optString("tel").ifEmpty { null },
                carNo = o.optString("car"),
                inDate = LocalDate.ofEpochDay(o.getLong("in")),
                expectedOut = if (o.has("due")) LocalDate.ofEpochDay(o.getLong("due")) else null,
                limitDays = o.optInt("limit", 7),
                outDate = if (o.has("out")) LocalDate.ofEpochDay(o.getLong("out")) else null,
                memo = o.optString("memo"),
                createdAt = o.optLong("created"),
            )
        }.getOrNull()
    }
}
