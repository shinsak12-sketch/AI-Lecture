package com.bosang.search.camera

import com.bosang.search.core.MeasureMath
import kotlin.math.abs
import kotlin.math.sqrt

/** AR 화면에 남기는 것: 점이 몇 개 있어야 끝나는지 */
enum class Tool(val label: String, val points: Int) {
    LENGTH("길이", 2),
    HEIGHT("높이", 1),
    CIRCLE("원", 2),
    RECT("네모", 2),
    STICKER("스티커", 1),
    PLATE("번호판", 2),
}

/** 수리 배지 */
object Stickers {
    val ALL = listOf("교환", "판금", "도장", "수리", "탈착", "부품", "확인 필요")

    /** 배지 색 (ARGB) */
    fun color(label: String?): Int = when (label) {
        "교환" -> 0xFFE5484D.toInt()
        "판금" -> 0xFFF08C00.toInt()
        "도장" -> 0xFF3360FF.toInt()
        "수리" -> 0xFF12A594.toInt()
        "탈착" -> 0xFF7B5CF0.toInt()
        "부품" -> 0xFF0FA3B1.toInt()
        else -> 0xFF5A6276.toInt()
    }
}

/** 화면으로 넘기는 표시 하나 */
class MarkSnap(
    val tool: Tool,
    val label: String?,
    val points: List<FloatArray>,
    /** 첫 점이 닿은 면의 두 방향 (원 그리기용), 모르면 null */
    val axisX: FloatArray?,
    val axisZ: FloatArray?,
) {
    val complete: Boolean get() = points.size >= tool.points
}

object MarkText {
    /** 네모: 가로(수평 거리) · 세로(높이 차) cm */
    fun rectSize(a: FloatArray, b: FloatArray): Pair<Int, Int> {
        val dx = (b[0] - a[0]).toDouble()
        val dz = (b[2] - a[2]).toDouble()
        val w = MeasureMath.cm(sqrt(dx * dx + dz * dz))
        val h = MeasureMath.cm(abs((b[1] - a[1]).toDouble()))
        return w to h
    }

    /** 표시 하나의 글자 (화면 · 저장 공통) */
    fun label(m: MarkSnap, baseY: Float?): String? {
        if (!m.complete) return null
        val p = m.points
        return when (m.tool) {
            Tool.LENGTH -> "${MeasureMath.cm(MeasureMath.distance(p[0], p[1]))}cm"
            Tool.HEIGHT -> baseY?.let { "${MeasureMath.cm(MeasureMath.height(p[0], it))}cm" }
            Tool.CIRCLE -> "지름 ${MeasureMath.cm(MeasureMath.distance(p[0], p[1]) * 2)}cm"
            Tool.RECT -> rectSize(p[0], p[1]).let { (w, h) -> "$w × ${h}cm" }
            Tool.STICKER -> m.label
            Tool.PLATE -> {
                val cm = MeasureMath.distance(p[0], p[1]) * 100
                val (err, grade) = MeasureMath.plateCheck(cm)
                "번호판 ${String.format("%.1f", cm)}cm · 오차 ${String.format("%+.1f", err)} · $grade"
            }
        }
    }

    /** 사진에 기록할 요약: "길이 23cm · 높이 42~61cm · 교환 · 판금" */
    fun summary(marks: List<MarkSnap>, baseY: Float?): String? {
        val done = marks.filter { it.complete }
        val parts = ArrayList<String>()
        done.filter { it.tool == Tool.LENGTH }.forEach { parts.add("길이 " + label(it, baseY)) }
        if (baseY != null) {
            MeasureMath.heightLabel(done.filter { it.tool == Tool.HEIGHT }.map { MeasureMath.height(it.points[0], baseY) })?.let { parts.add(it) }
        }
        done.filter { it.tool == Tool.CIRCLE || it.tool == Tool.RECT || it.tool == Tool.PLATE }.forEach { m -> label(m, baseY)?.let { parts.add(it) } }
        done.filter { it.tool == Tool.STICKER }.mapNotNull { it.label }.distinct().forEach { parts.add(it) }
        return parts.joinToString(" · ").ifEmpty { null }
    }
}
