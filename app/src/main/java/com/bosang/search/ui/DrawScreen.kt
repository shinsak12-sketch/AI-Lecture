package com.bosang.search.ui

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bosang.search.data.Photos
import kotlin.math.hypot
import kotlin.math.min

private enum class Tool { PEN, MARKER, ERASER }

private class Line(val points: List<Offset>, val color: Color, val width: Float, val marker: Boolean)

private val PenColors = listOf(Color(0xFF1B2B6B), Color(0xFFFF3B4E), Color(0xFF3360FF), Color(0xFFFFC400), Color.White)

/**
 * 손으로 그리기. backgroundRef 가 있으면 그 사진 위에, 없으면 흰 종이에.
 * S펜을 한 번 쓰면 그 뒤로 손가락 터치는 무시 (손바닥 닿는 것 방지).
 */
@Composable
fun DrawScreen(backgroundRef: String?, onCancel: () -> Unit, onDone: (Bitmap) -> Unit) {
    val c = B.c
    val ctx = LocalContext.current
    val density = LocalDensity.current
    var bg by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(backgroundRef != null) }
    LaunchedEffect(backgroundRef) {
        if (backgroundRef != null) {
            bg = Photos.loadFull(ctx, backgroundRef)
            loading = false
        }
    }
    val lines = remember { mutableStateListOf<Line>() }
    val redo = remember { mutableStateListOf<Line>() }
    val current = remember { mutableStateListOf<Offset>() }
    var tool by remember { mutableStateOf(Tool.PEN) }
    var color by remember { mutableStateOf(if (backgroundRef != null) PenColors[1] else PenColors[0]) }
    var widthLevel by remember { mutableStateOf(1) }
    var stylusSeen by remember { mutableStateOf(false) }
    // 그림이 놓인 자리 (저장할 때만 씀, 화면 갱신과 무관)
    val canvasRect = remember { arrayOf(Rect.Zero) }
    val dark = backgroundRef != null

    fun strokeWidthPx(): Float = with(density) { listOf(2.5.dp, 5.dp, 9.dp)[widthLevel].toPx() } * (if (tool == Tool.MARKER) 3f else 1f)

    Box(
        Modifier
            .fillMaxSize()
            .background(if (dark) Color(0xFF07090F) else c.bg),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    "취소",
                    style = ts(15.5f, W7),
                    color = if (dark) Color.White.copy(alpha = 0.75f) else c.ink2,
                    modifier = Modifier.press(onClick = onCancel).padding(6.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(if (dark) "사진에 표시" else "그리기", style = ts(15.5f, W8), color = if (dark) Color.White else c.ink)
                Spacer(Modifier.weight(1f))
                GradientButton("완료", height = 38.dp, radius = 13.dp) {
                    onDone(render(bg, canvasRect[0], lines.toList(), Color.White))
                }
            }

            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = if (dark) 0.dp else 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(if (dark) 0.dp else 22.dp))
                    .background(if (dark) Color.Transparent else Color.White),
            ) {
                val w = constraints.maxWidth.toFloat()
                val h = constraints.maxHeight.toFloat()
                val b = bg
                canvasRect[0] = if (b != null) {
                    val s = min(w / b.width, h / b.height)
                    val dw = b.width * s
                    val dh = b.height * s
                    Rect((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2)
                } else {
                    Rect(0f, 0f, w, h)
                }
                if (b != null) {
                    Image(b.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                } else if (loading) {
                    CircularProgressIndicator(color = c.brand, modifier = Modifier.align(Alignment.Center).size(26.dp))
                } else if (!dark) {
                    // 점 종이
                    Canvas(Modifier.fillMaxSize()) {
                        val step = 16.dp.toPx()
                        var y = step
                        while (y < size.height) {
                            var x = step
                            while (x < size.width) {
                                drawCircle(Color(0x1A0C1222), 1.1.dp.toPx(), Offset(x, y))
                                x += step
                            }
                            y += step
                        }
                    }
                }
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(tool, color, widthLevel) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val isPen = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                                if (isPen) stylusSeen = true else if (stylusSeen) return@awaitEachGesture
                                val erasing = tool == Tool.ERASER || down.type == PointerType.Eraser
                                val radius = 18.dp.toPx()
                                fun eraseAt(p: Offset) {
                                    lines.removeAll { l -> l.points.any { hypot(it.x - p.x, it.y - p.y) < radius } }
                                }
                                if (erasing) eraseAt(down.position) else {
                                    current.clear()
                                    current.add(down.position)
                                }
                                down.consume()
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    if (erasing) {
                                        eraseAt(ch.position)
                                    } else {
                                        ch.historical.forEach { current.add(it.position) }
                                        current.add(ch.position)
                                    }
                                    ch.consume()
                                }
                                if (!erasing && current.isNotEmpty()) {
                                    lines.add(Line(current.toList(), color, strokeWidthPx(), tool == Tool.MARKER))
                                    redo.clear()
                                    current.clear()
                                }
                            }
                        },
                ) {
                    lines.forEach { l -> drawStroke(l) }
                    if (current.isNotEmpty()) {
                        drawStroke(Line(current.toList(), color, strokeWidthPx(), tool == Tool.MARKER))
                    }
                }
            }

            // 도구
            val shape = RoundedCornerShape(24.dp)
            Column(
                Modifier
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .fillMaxWidth()
                    .depth(shape, Depth.FLOAT)
                    .clip(shape)
                    .background(if (dark) Color(0xEB1E222E) else c.glass)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    ToolBtn(Ic.pen, "펜", tool == Tool.PEN, dark) { tool = Tool.PEN }
                    ToolBtn(Ic.highlighter, "형광펜", tool == Tool.MARKER, dark) { tool = Tool.MARKER }
                    ToolBtn(Ic.eraser, "지우개", tool == Tool.ERASER, dark) { tool = Tool.ERASER }
                    ToolBtn(Ic.undo, "되돌리기", false, dark) { if (lines.isNotEmpty()) redo.add(lines.removeAt(lines.lastIndex)) }
                    ToolBtn(Ic.redo, "다시", false, dark) { if (redo.isNotEmpty()) lines.add(redo.removeAt(redo.lastIndex)) }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp, end = 4.dp),
                ) {
                    PenColors.forEach { col ->
                        val on = col == color
                        Box(
                            Modifier
                                .padding(end = 9.dp)
                                .press(scale = 0.9f) {
                                    color = col
                                    if (tool == Tool.ERASER) tool = Tool.PEN
                                }
                                .size(24.dp)
                                .then(if (on) Modifier.border(2.dp, c.brand, CircleShape).padding(4.dp) else Modifier)
                                .clip(CircleShape)
                                .background(col)
                                .border(1.dp, Color(0x220C1222), CircleShape),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    listOf(0, 1, 2).forEach { lv ->
                        val on = lv == widthLevel
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .press(scale = 0.9f) { widthLevel = lv }
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (on) c.brandTint else Color.Transparent),
                        ) {
                            Box(
                                Modifier
                                    .size(listOf(5.dp, 9.dp, 14.dp)[lv])
                                    .clip(CircleShape)
                                    .background(if (on) c.brand else if (dark) Color.White.copy(alpha = 0.6f) else c.ink3),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolBtn(icon: ImageVector, desc: String, on: Boolean, dark: Boolean, onClick: () -> Unit) {
    val c = B.c
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .press(scale = 0.9f, onClick = onClick)
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) c.brandTint else Color.Transparent),
    ) {
        Icon(icon, desc, tint = if (on) c.brand else if (dark) Color.White.copy(alpha = 0.75f) else c.ink2, modifier = Modifier.size(22.dp))
    }
}

private fun smooth(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points[0].x, points[0].y)
    if (points.size == 1) {
        lineTo(points[0].x + 0.1f, points[0].y + 0.1f)
        return@apply
    }
    for (i in 1 until points.size) {
        val p = points[i - 1]
        val q = points[i]
        quadraticBezierTo(p.x, p.y, (p.x + q.x) / 2, (p.y + q.y) / 2)
    }
    lineTo(points.last().x, points.last().y)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(l: Line) {
    drawPath(
        smooth(l.points),
        color = if (l.marker) l.color.copy(alpha = 0.38f) else l.color,
        style = Stroke(width = l.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** 화면에 그린 선을 실제 그림 크기로 옮겨 한 장으로 합침 */
private fun render(bg: Bitmap?, rect: Rect, lines: List<Line>, paper: Color): Bitmap {
    val w = (bg?.width ?: rect.width.toInt()).coerceAtLeast(1)
    val h = (bg?.height ?: rect.height.toInt()).coerceAtLeast(1)
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    if (bg != null) canvas.drawBitmap(bg, 0f, 0f, null) else canvas.drawColor(paper.toArgb())
    val scale = if (rect.width > 0) w / rect.width else 1f
    lines.forEach { l ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = l.width * scale
            color = (if (l.marker) l.color.copy(alpha = 0.38f) else l.color).toArgb()
        }
        val path = android.graphics.Path()
        val pts = l.points.map { Offset((it.x - rect.left) * scale, (it.y - rect.top) * scale) }
        path.moveTo(pts[0].x, pts[0].y)
        if (pts.size == 1) path.lineTo(pts[0].x + 0.1f, pts[0].y + 0.1f)
        for (i in 1 until pts.size) {
            val p = pts[i - 1]
            val q = pts[i]
            path.quadTo(p.x, p.y, (p.x + q.x) / 2, (p.y + q.y) / 2)
        }
        path.lineTo(pts.last().x, pts.last().y)
        canvas.drawPath(path, paint)
    }
    return out
}
