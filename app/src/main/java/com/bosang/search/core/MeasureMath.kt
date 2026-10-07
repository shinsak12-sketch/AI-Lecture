package com.bosang.search.core

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** AR 측정 계산 (미터 단위 좌표, y 가 위쪽) */
object MeasureMath {
    /** 우리나라 신형 번호판 가로 (cm) — 측정이 맞는지 확인용 */
    const val PLATE_CM = 52.0

    fun distance(a: FloatArray, b: FloatArray): Double {
        val dx = (a[0] - b[0]).toDouble()
        val dy = (a[1] - b[1]).toDouble()
        val dz = (a[2] - b[2]).toDouble()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /** 꺾은선 전체 길이 (m) */
    fun pathLength(points: List<FloatArray>): Double =
        points.zipWithNext().sumOf { (a, b) -> distance(a, b) }

    /** 바닥(floorY)에서 점까지 높이 (m), 바닥보다 아래면 0 */
    fun height(point: FloatArray, floorY: Float): Double = (point[1] - floorY).toDouble().coerceAtLeast(0.0)

    fun cm(m: Double): Int = (m * 100).roundToInt()

    /** 높이 글자: 한 점이면 "높이 52cm", 여러 점이면 "높이 42~61cm" */
    fun heightLabel(heightsM: List<Double>): String? {
        if (heightsM.isEmpty()) return null
        val cms = heightsM.map { cm(it) }
        val lo = cms.min()
        val hi = cms.max()
        return if (lo == hi) "높이 ${lo}cm" else "높이 $lo~${hi}cm"
    }

    /** 번호판으로 측정 정확도 확인: (오차 cm, 등급) */
    fun plateCheck(measuredCm: Double): Pair<Double, String> {
        val err = measuredCm - PLATE_CM
        val grade = when {
            abs(err) <= 1.5 -> "양호"
            abs(err) <= 3.0 -> "보통"
            else -> "부정확"
        }
        return err to grade
    }

    /** 두 높이 범위가 겹치는 구간 (cm), 없으면 null */
    fun overlap(a: IntRange, b: IntRange): IntRange? {
        val lo = maxOf(a.first, b.first)
        val hi = minOf(a.last, b.last)
        return if (lo <= hi) lo..hi else null
    }
}
