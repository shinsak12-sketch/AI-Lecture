package com.bosang.search.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.bosang.search.R
import com.bosang.search.core.MeasureMath
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** AR 화면 위에 점선 · 원 · 네모 · 배지 · 숫자를 그린다 (사진 저장 때 함께 찍힘) */
@SuppressLint("ViewConstructor")
class MeasureOverlay(context: Context, private val onPick: (Int?) -> Unit) : View(context) {
    var snap: ArSnapshot? = null
        set(v) {
            field = v
            invalidate()
        }
    var tool: Tool = Tool.LENGTH
        set(v) {
            field = v
            invalidate()
        }
    /** 눌러서 고른 표시 (지우기용) */
    var selected: Int? = null
        set(v) {
            field = v
            invalidate()
        }
    /** 사진 저장 중: 조준점 · 미리 보기 선 · 선택 표시는 빼고 그림 */
    var capturing = false

    private val d = resources.displayMetrics.density
    private val bold: Typeface = runCatching { ResourcesCompat.getFont(context, R.font.pretendard_bold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    private val accent = Color.rgb(255, 212, 59)
    private val good = Color.rgb(75, 227, 172)
    private val brand = Color.rgb(51, 96, 255)

    private val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        strokeWidth = 3.2f * d
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        pathEffect = DashPathEffect(floatArrayOf(10f * d, 7f * d), 0f)
        setShadowLayer(2.5f * d, 0f, 0f, Color.argb(150, 0, 0, 0))
    }
    private val thinDash = Paint(dashed).apply {
        color = Color.WHITE
        strokeWidth = 2f * d
        pathEffect = DashPathEffect(floatArrayOf(6f * d, 6f * d), 0f)
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
    }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = bold
        textAlign = Paint.Align.CENTER
    }

    /** 글자 상자 위치 (눌러서 고르기용) */
    private val boxes = ArrayList<Pair<Int, RectF>>()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) {
                val hit = boxes.firstOrNull { (_, r) -> RectF(r).apply { inset(-12f * d, -12f * d) }.contains(e.x, e.y) }?.first
                onPick(if (hit == selected) null else hit)
            }
            boxes.isNotEmpty()
        }
    }

    /** 월드 좌표 → 화면 (뒤쪽이면 null) */
    private fun project(s: ArSnapshot, p: FloatArray): FloatArray? {
        val m = s.viewProj
        val x = m[0] * p[0] + m[4] * p[1] + m[8] * p[2] + m[12]
        val y = m[1] * p[0] + m[5] * p[1] + m[9] * p[2] + m[13]
        val w = m[3] * p[0] + m[7] * p[1] + m[11] * p[2] + m[15]
        if (w <= 0.0001f) return null
        return floatArrayOf((x / w + 1f) / 2f * width, (1f - y / w) / 2f * height)
    }

    private fun label(c: Canvas, s: String, x: Float, y: Float, bg: Int = Color.argb(220, 12, 18, 34), fg: Int = Color.WHITE, size: Float = 14f, index: Int? = null) {
        text.textSize = size * d
        text.color = fg
        val w = text.measureText(s) + 18f * d
        val h = (size + 11f) * d
        val r = RectF(x - w / 2, y - h / 2, x + w / 2, y + h / 2)
        pill.color = bg
        c.drawRoundRect(r, h / 2, h / 2, pill)
        if (index != null && index == selected && !capturing) {
            c.drawRoundRect(r, h / 2, h / 2, Paint(ring).apply { color = brand; strokeWidth = 3f * d })
        }
        c.drawText(s, x, y + text.textSize * 0.36f, text)
        if (index != null) boxes.add(index to r)
    }

    private fun point(c: Canvas, at: FloatArray, r: Float = 5.5f) {
        c.drawCircle(at[0], at[1], r * d, dot)
        c.drawCircle(at[0], at[1], r * d, ring)
    }

    private fun dashLine(c: Canvas, a: FloatArray, b: FloatArray, p: Paint = dashed) {
        c.drawLine(a[0], a[1], b[0], b[1], p)
    }

    private fun polygon(c: Canvas, pts: List<FloatArray?>, p: Paint = dashed) {
        if (pts.any { it == null }) return
        val path = Path()
        pts.forEachIndexed { i, q -> if (i == 0) path.moveTo(q!![0], q[1]) else path.lineTo(q!![0], q[1]) }
        path.close()
        c.drawPath(path, p)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        boxes.clear()
        val s = snap ?: return
        val cx = width / 2f
        val cy = height / 2f
        val baseY = s.base?.get(1)

        // 바닥 기준점
        s.base?.let { b ->
            project(s, b)?.let { at ->
                canvas.drawLine(at[0] - 22f * d, at[1], at[0] + 22f * d, at[1], Paint(thinDash).apply { pathEffect = null })
                point(canvas, at, 4.5f)
                label(canvas, "바닥 기준", at[0], at[1] + 22f * d, size = 11.5f)
            }
        }

        s.marks.forEachIndexed { i, m ->
            if (capturing && !m.complete) return@forEachIndexed
            val pts = m.points.map { project(s, it) }
            val a = pts.getOrNull(0) ?: return@forEachIndexed
            val text = MarkText.label(m, baseY)
            when (m.tool) {
                Tool.LENGTH, Tool.PLATE -> {
                    val b = pts.getOrNull(1)
                    point(canvas, a)
                    if (b != null) {
                        dashLine(canvas, a, b)
                        point(canvas, b)
                        if (text != null) label(canvas, text, (a[0] + b[0]) / 2, (a[1] + b[1]) / 2 - 20f * d, index = i)
                    } else if (!capturing && s.center != null) {
                        dashLine(canvas, a, floatArrayOf(cx, cy), thinDash)
                    }
                }
                Tool.HEIGHT -> {
                    val p = m.points[0]
                    if (baseY != null) {
                        project(s, floatArrayOf(p[0], baseY, p[2]))?.let { foot ->
                            dashLine(canvas, a, foot)
                            canvas.drawCircle(foot[0], foot[1], 3.5f * d, ring)
                        }
                    }
                    point(canvas, a)
                    if (text != null) label(canvas, text, a[0] + 46f * d, a[1], index = i)
                }
                Tool.CIRCLE -> {
                    point(canvas, a, 3.5f)
                    val edge = m.points.getOrNull(1)
                    val r3 = edge?.let { MeasureMath.distance(m.points[0], it).toFloat() }
                    if (r3 != null) {
                        val ax = m.axisX
                        val az = m.axisZ
                        if (ax != null && az != null) {
                            // 면 위의 진짜 원 (비스듬하면 타원으로 보임)
                            val c0 = m.points[0]
                            polygon(
                                canvas,
                                (0 until 48).map { k ->
                                    val t = (k / 48.0 * Math.PI * 2).toFloat()
                                    project(s, floatArrayOf(
                                        c0[0] + r3 * (cos(t) * ax[0] + sin(t) * az[0]),
                                        c0[1] + r3 * (cos(t) * ax[1] + sin(t) * az[1]),
                                        c0[2] + r3 * (cos(t) * ax[2] + sin(t) * az[2]),
                                    ))
                                },
                            )
                        } else {
                            pts.getOrNull(1)?.let { e -> canvas.drawCircle(a[0], a[1], hypot(e[0] - a[0], e[1] - a[1]), dashed) }
                        }
                        if (text != null) label(canvas, text, a[0], a[1] - 24f * d, index = i)
                    } else if (!capturing && s.center != null) {
                        canvas.drawCircle(a[0], a[1], hypot(cx - a[0], cy - a[1]), thinDash)
                    }
                }
                Tool.RECT -> {
                    point(canvas, a, 3.5f)
                    val bw = m.points.getOrNull(1)
                    if (bw != null) {
                        val aw = m.points[0]
                        val corners = listOf(aw, floatArrayOf(bw[0], aw[1], bw[2]), bw, floatArrayOf(aw[0], bw[1], aw[2])).map { project(s, it) }
                        polygon(canvas, corners)
                        val ys = corners.filterNotNull()
                        if (text != null && ys.isNotEmpty()) {
                            label(canvas, text, ys.map { it[0] }.average().toFloat(), ys.minOf { it[1] } - 20f * d, index = i)
                        }
                    } else if (!capturing && s.center != null) {
                        val r = RectF(minOf(a[0], cx), minOf(a[1], cy), maxOf(a[0], cx), maxOf(a[1], cy))
                        canvas.drawRect(r, thinDash)
                    }
                }
                Tool.STICKER -> {
                    // 배지: 점에서 살짝 위로
                    val col = Stickers.color(m.label)
                    canvas.drawCircle(a[0], a[1], 5f * d, Paint(dot).apply { color = col })
                    canvas.drawCircle(a[0], a[1], 5f * d, ring)
                    canvas.drawLine(a[0], a[1] - 5f * d, a[0], a[1] - 22f * d, Paint(ring).apply { strokeWidth = 2f * d })
                    label(canvas, m.label ?: "확인", a[0], a[1] - 36f * d, bg = col, size = 15f, index = i)
                }
            }
        }

        if (capturing) return
        // 가운데 조준점
        val ok = s.tracking && s.center != null
        val aim = Paint(ring).apply {
            color = if (ok) good else Color.argb(170, 255, 255, 255)
            strokeWidth = 2.5f * d
        }
        canvas.drawCircle(cx, cy, 16f * d, aim)
        canvas.drawLine(cx - 8f * d, cy, cx + 8f * d, cy, aim)
        canvas.drawLine(cx, cy - 8f * d, cx, cy + 8f * d, aim)
        live(s)?.let {
            text.textSize = 13f * d
            text.color = if (ok) good else Color.WHITE
            val w = text.measureText(it) + 18f * d
            pill.color = Color.argb(200, 12, 18, 34)
            val r = RectF(cx - w / 2, cy + 28f * d, cx + w / 2, cy + 52f * d)
            canvas.drawRoundRect(r, 12f * d, 12f * d, pill)
            canvas.drawText(it, cx, cy + 44.5f * d, text)
        }
    }

    /** 조준점 아래 실시간 글자 */
    private fun live(s: ArSnapshot): String? {
        if (!s.tracking) return null
        val c = s.center ?: return "표면을 찾는 중"
        val pending = s.marks.lastOrNull()?.takeIf { !it.complete && it.tool == tool }
        return when (tool) {
            Tool.HEIGHT -> s.base?.let { "바닥에서 ${MeasureMath.cm(MeasureMath.height(c, it[1]))}cm" } ?: "바닥(타이어 닿는 곳)을 겨누고 [바닥 찍기]"
            Tool.LENGTH, Tool.PLATE -> pending?.let { "${MeasureMath.cm(MeasureMath.distance(it.points[0], c))}cm" }
            Tool.CIRCLE -> pending?.let { "지름 ${MeasureMath.cm(MeasureMath.distance(it.points[0], c) * 2)}cm" }
            Tool.RECT -> pending?.let { MarkText.rectSize(it.points[0], c).let { (w, h) -> "$w × ${h}cm" } }
            Tool.STICKER -> null
        }
    }

    fun summary(): String? = snap?.let { MarkText.summary(it.marks, it.base?.get(1)) }
}
