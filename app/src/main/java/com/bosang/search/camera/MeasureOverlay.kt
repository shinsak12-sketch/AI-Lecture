package com.bosang.search.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.bosang.search.R
import com.bosang.search.core.MeasureMath

enum class MeasureMode { HEIGHT, LENGTH }

/** AR 화면 위에 점 · 선 · 숫자를 그린다 (사진 저장 때 그대로 함께 찍힘) */
class MeasureOverlay(context: Context) : View(context) {
    var snap: ArSnapshot? = null
        set(v) {
            field = v
            invalidate()
        }
    var mode: MeasureMode = MeasureMode.HEIGHT
        set(v) {
            field = v
            invalidate()
        }
    var plateCheck = false
        set(v) {
            field = v
            invalidate()
        }

    private val d = resources.displayMetrics.density
    private val bold: Typeface = runCatching { ResourcesCompat.getFont(context, R.font.pretendard_bold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    private val accent = Color.rgb(255, 212, 59)
    private val good = Color.rgb(75, 227, 172)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        strokeWidth = 3f * d
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(2f * d, 0f, 0f, Color.argb(120, 0, 0, 0))
    }
    private val dash = Paint(line).apply {
        color = Color.WHITE
        strokeWidth = 2f * d
        pathEffect = DashPathEffect(floatArrayOf(8f * d, 6f * d), 0f)
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val dotRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
    }
    private val labelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(215, 12, 18, 34) }
    private val labelText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f * d
        typeface = bold
        textAlign = Paint.Align.CENTER
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    /** 월드 좌표 → 화면 (뒤쪽이면 null) */
    private fun project(s: ArSnapshot, p: FloatArray): FloatArray? {
        val m = s.viewProj
        val x = m[0] * p[0] + m[4] * p[1] + m[8] * p[2] + m[12]
        val y = m[1] * p[0] + m[5] * p[1] + m[9] * p[2] + m[13]
        val w = m[3] * p[0] + m[7] * p[1] + m[11] * p[2] + m[15]
        if (w <= 0.0001f) return null
        val sx = (x / w + 1f) / 2f * width
        val sy = (1f - y / w) / 2f * height
        return floatArrayOf(sx, sy)
    }

    private fun label(c: Canvas, text: String, x: Float, y: Float, color: Int = Color.WHITE) {
        labelText.color = color
        val w = labelText.measureText(text) + 16f * d
        val h = 24f * d
        val r = RectF(x - w / 2, y - h / 2, x + w / 2, y + h / 2)
        c.drawRoundRect(r, 8f * d, 8f * d, labelBg)
        c.drawText(text, x, y + labelText.textSize * 0.36f, labelText)
    }

    private fun point(c: Canvas, at: FloatArray, n: Int) {
        c.drawCircle(at[0], at[1], 7f * d, dot)
        c.drawCircle(at[0], at[1], 7f * d, dotRing)
        labelText.color = Color.BLACK
        labelText.textSize = 9.5f * d
        c.drawText("$n", at[0], at[1] + 3.4f * d, labelText)
        labelText.textSize = 14f * d
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = snap ?: return
        val cx = width / 2f
        val cy = height / 2f

        when (mode) {
            MeasureMode.HEIGHT -> {
                val floor = s.floorY
                s.points.forEachIndexed { i, p ->
                    val at = project(s, p) ?: return@forEachIndexed
                    if (floor != null) {
                        project(s, floatArrayOf(p[0], floor, p[2]))?.let { base ->
                            canvas.drawLine(at[0], at[1], base[0], base[1], dash)
                            canvas.drawCircle(base[0], base[1], 4f * d, dotRing)
                        }
                    }
                    point(canvas, at, i + 1)
                    if (floor != null) label(canvas, "${MeasureMath.cm(MeasureMath.height(p, floor))}cm", at[0] + 44f * d, at[1])
                }
            }
            MeasureMode.LENGTH -> {
                val pts = s.points.map { project(s, it) }
                s.points.zipWithNext().forEachIndexed { i, (a, b) ->
                    val pa = pts[i] ?: return@forEachIndexed
                    val pb = pts[i + 1] ?: return@forEachIndexed
                    canvas.drawLine(pa[0], pa[1], pb[0], pb[1], line)
                    label(canvas, "${MeasureMath.cm(MeasureMath.distance(a, b))}cm", (pa[0] + pb[0]) / 2, (pa[1] + pb[1]) / 2 - 18f * d)
                }
                pts.forEachIndexed { i, at -> if (at != null) point(canvas, at, i + 1) }
                // 마지막 점 → 가운데 (미리 보기)
                val last = s.points.lastOrNull()
                val center = s.center
                if (last != null && center != null) {
                    project(s, last)?.let { pl -> canvas.drawLine(pl[0], pl[1], cx, cy, dash) }
                }
            }
        }

        // 가운데 조준점
        val ok = s.tracking && s.center != null
        val ring = Paint(dotRing).apply {
            color = if (ok) good else Color.argb(170, 255, 255, 255)
            strokeWidth = 2.5f * d
        }
        canvas.drawCircle(cx, cy, 16f * d, ring)
        canvas.drawLine(cx - 8f * d, cy, cx + 8f * d, cy, ring)
        canvas.drawLine(cx, cy - 8f * d, cx, cy + 8f * d, ring)
        live(s)?.let { label(canvas, it, cx, cy + 40f * d, if (ok) good else Color.WHITE) }
    }

    /** 조준점 아래 실시간 글자 */
    private fun live(s: ArSnapshot): String? {
        if (!s.tracking) return null
        val c = s.center ?: return "표면을 찾는 중"
        return when (mode) {
            MeasureMode.HEIGHT -> s.floorY?.let { "바닥에서 ${MeasureMath.cm(MeasureMath.height(c, it))}cm" } ?: "바닥을 먼저 비춰 주세요"
            MeasureMode.LENGTH -> s.points.lastOrNull()?.let { "${MeasureMath.cm(MeasureMath.distance(it, c))}cm" }
        }
    }

    /** 저장할 측정 요약 */
    fun summary(): String? {
        val s = snap ?: return null
        return when (mode) {
            MeasureMode.HEIGHT -> s.floorY?.let { f -> MeasureMath.heightLabel(s.points.map { MeasureMath.height(it, f) }) }
            MeasureMode.LENGTH -> {
                if (s.points.size < 2) return null
                val total = MeasureMath.cm(MeasureMath.pathLength(s.points))
                if (plateCheck && s.points.size == 2) {
                    val (err, grade) = MeasureMath.plateCheck(MeasureMath.pathLength(s.points) * 100)
                    "번호판 확인 ${total}cm · 오차 ${String.format("%+.1f", err)}cm · $grade"
                } else if (s.points.size > 2) {
                    "길이 ${total}cm (${s.points.size - 1}구간)"
                } else {
                    "길이 ${total}cm"
                }
            }
        }
    }
}
