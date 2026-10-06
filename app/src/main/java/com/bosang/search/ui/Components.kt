@file:OptIn(ExperimentalFoundationApi::class)

package com.bosang.search.ui

import android.app.Activity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.bosang.search.core.MatchMethod
import com.bosang.search.data.Player
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

// ───────────────────────── 날짜·시간 ─────────────────────────

object Fmt {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val fTime = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)
    private val fMd = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
    private val fMdE = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
    private val fYmdE = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)
    private val fShort = DateTimeFormatter.ofPattern("M. d.", Locale.KOREAN)
    private val fShortY = DateTimeFormatter.ofPattern("yy. M. d.", Locale.KOREAN)
    private val fToday = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)

    private fun local(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone)
    fun dayKey(ms: Long): LocalDate = local(ms).toLocalDate()

    fun time(ms: Long): String = local(ms).format(fTime)

    /** 홈 맨 위: 10월 6일 월요일 */
    fun todayHeader(): String = LocalDate.now().format(fToday)

    /** 타임라인 날짜 줄: 오늘 · 10월 6일 / 어제 · 10월 5일 / 9월 28일 (일) */
    fun dayLabel(ms: Long): String {
        val d = dayKey(ms)
        val today = LocalDate.now()
        return when {
            d == today -> "오늘 · " + d.format(fMd)
            d == today.minusDays(1) -> "어제 · " + d.format(fMd)
            d.year == today.year -> d.format(fMdE)
            else -> d.format(fYmdE)
        }
    }

    /** 목록 오른쪽 작은 글씨: 14:32 / 어제 / 10. 2. */
    fun short(ms: Long): String {
        val d = dayKey(ms)
        val today = LocalDate.now()
        return when {
            d == today -> time(ms)
            d == today.minusDays(1) -> "어제"
            d.year == today.year -> d.format(fShort)
            else -> d.format(fShortY)
        }
    }

    /** 방금 / 12분 전 / 오늘 14:32 / 어제 18:20 / 10. 2. */
    fun ago(ms: Long): String {
        val diff = System.currentTimeMillis() - ms
        val d = dayKey(ms)
        val today = LocalDate.now()
        return when {
            diff in 0L until 60_000L -> "방금"
            diff in 0L until 3_600_000L -> "${diff / 60_000}분 전"
            d == today -> "오늘 " + time(ms)
            d == today.minusDays(1) -> "어제 " + time(ms)
            else -> short(ms)
        }
    }

    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }

    fun durationKo(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "${s}초"
            s % 60 == 0L -> "${s / 60}분"
            else -> "${s / 60}분 ${s % 60}초"
        }
    }
}

/** 한국어 조사: 홍길동과 / 김영희와 */
fun withJosa(name: String, batchim: String, plain: String): String {
    val last = name.lastOrNull() ?: return name
    val has = if (last in '가'..'힣') (last - '가') % 28 != 0 else last.isDigit() && last in "013678"
    return name + if (has) batchim else plain
}

// ───────────────────────── 누르는 느낌 ─────────────────────────

/** 눌렀을 때 살짝 작아지는 클릭 (물결 효과 대신) */
@Composable
fun Modifier.press(
    enabled: Boolean = true,
    scale: Float = 0.97f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(
        targetValue = if (pressed && enabled) scale else 1f,
        animationSpec = spring(stiffness = 700f),
        label = "press",
    )
    return this
        .graphicsLayer {
            scaleX = s
            scaleY = s
        }
        .combinedClickable(
            interactionSource = src,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

// ───────────────────────── 그림자 ─────────────────────────

enum class Depth { CARD, LIFT, FLOAT }

@Composable
fun Modifier.depth(shape: Shape, level: Depth = Depth.CARD): Modifier {
    val c = B.c
    val (elev, spot) = when (level) {
        Depth.CARD -> 8.dp to 0.42f
        Depth.LIFT -> 18.dp to 0.75f
        Depth.FLOAT -> 22.dp to 0.95f
    }
    return shadow(
        elevation = elev,
        shape = shape,
        clip = false,
        ambientColor = c.shadow.copy(alpha = if (c.dark) 0.6f else 0.35f),
        spotColor = c.shadow.copy(alpha = if (c.dark) 1f else spot),
    )
}

/** 파란 버튼의 은은한 빛 */
@Composable
fun Modifier.glow(shape: Shape, elevation: Dp = 12.dp): Modifier {
    val c = B.c
    return shadow(elevation, shape, clip = false, ambientColor = c.brand, spotColor = c.brand)
}

// ───────────────────────── 카드 ─────────────────────────

@Composable
fun BCard(
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    level: Depth = Depth.CARD,
    color: Color = B.c.card,
    brush: Brush? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .depth(shape, level)
            .clip(shape)
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(color)),
        content = content,
    )
}

/**
 * 긴 목록을 한 장의 카드처럼: 줄마다 따로 그리되, 위아래 이음새에 그림자가 비치지 않게
 * 첫 줄은 위쪽, 마지막 줄은 아래쪽 그림자만 보이게 자른다.
 */
@Composable
fun CardSegment(
    first: Boolean,
    last: Boolean,
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = if (first) radius else 0.dp,
        topEnd = if (first) radius else 0.dp,
        bottomStart = if (last) radius else 0.dp,
        bottomEnd = if (last) radius else 0.dp,
    )
    Box(
        modifier.drawWithContentClip(extendTop = first, extendBottom = last),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .depth(shape)
                .clip(shape)
                .background(B.c.card)
                .padding(
                    start = 6.dp,
                    end = 6.dp,
                    top = if (first) 6.dp else 0.dp,
                    bottom = if (last) 6.dp else 0.dp,
                ),
            content = content,
        )
    }
}

private fun Modifier.drawWithContentClip(extendTop: Boolean, extendBottom: Boolean): Modifier =
    this.then(
        Modifier.drawWithCache {
            val pad = 80.dp.toPx()
            onDrawWithContent {
                val top = if (extendTop) -pad else 0f
                val bottom = if (extendBottom) size.height + pad else size.height
                drawContext.canvas.save()
                drawContext.canvas.clipRect(-pad, top, size.width + pad, bottom)
                drawContent()
                drawContext.canvas.restore()
            }
        },
    )

/** 위 내용 아래쪽에 다음 카드를 overlap 만큼 겹쳐 올린다 (헤더 위로 뜬 카드) */
@Composable
fun Overlap(overlap: Dp, modifier: Modifier = Modifier, top: @Composable () -> Unit, bottom: @Composable () -> Unit) {
    Layout(contents = listOf(top, bottom), modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val tp = measurables[0].map { it.measure(loose) }
        val bp = measurables[1].map { it.measure(loose) }
        val th = tp.maxOfOrNull { it.height } ?: 0
        val bh = bp.maxOfOrNull { it.height } ?: 0
        val ov = overlap.roundToPx()
        val h = max(th, th + bh - ov)
        layout(constraints.maxWidth, h) {
            tp.forEach { it.place(0, 0) }
            bp.forEach { it.place(0, (th - ov).coerceAtLeast(0)) }
        }
    }
}

// ───────────────────────── 어두운 머리 ─────────────────────────

private val HeroDots = Color.White

fun Modifier.heroBackground(): Modifier = drawWithCache {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val base = Brush.linearGradient(
        listOf(Color(0xFF0A1330), Color(0xFF132459)),
        start = Offset(w * 0.62f, 0f),
        end = Offset(w * 0.38f, h),
    )
    val glow = Brush.radialGradient(
        listOf(Color(0x73628AFF), Color(0x00628AFF)),
        center = Offset(w, 0f),
        radius = max(w, h) * 0.78f,
    )
    val teal = Brush.radialGradient(
        listOf(Color(0x2928C8AA), Color(0x0028C8AA)),
        center = Offset(0f, h),
        radius = w * 0.7f,
    )
    val step = 14.dp.toPx()
    val dot = 2.2.dp.toPx()
    val fadeTo = h * 0.75f
    val rows = ArrayList<Pair<Float, List<Offset>>>()
    var y = step / 2
    while (y < fadeTo) {
        val pts = ArrayList<Offset>()
        var x = step / 2
        while (x < w) {
            pts.add(Offset(x, y))
            x += step
        }
        rows.add((0.09f * (1f - y / fadeTo)) to pts)
        y += step
    }
    onDrawBehind {
        drawRect(base)
        drawRect(glow)
        drawRect(teal)
        rows.forEach { (a, pts) ->
            drawPoints(pts, PointMode.Points, HeroDots.copy(alpha = a), strokeWidth = dot, cap = StrokeCap.Round)
        }
    }
}

/** 시안의 어두운 남색 머리. 상태표시줄 아래부터 내용 */
@Composable
fun Hero(
    modifier: Modifier = Modifier,
    radius: Dp = 30.dp,
    bottomPadding: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = radius, bottomEnd = radius))
            .heroBackground()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = bottomPadding),
        content = content,
    )
}

@Composable
fun HeroTopBar(
    left: @Composable RowScope.() -> Unit,
    right: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        left()
        Spacer(Modifier.weight(1f))
        right()
    }
}

/** 어두운 머리 위의 반투명 동그란 버튼 */
@Composable
fun GlassCircle(icon: ImageVector, desc: String, size: Dp = 38.dp, iconSize: Dp = 19.dp, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .press(scale = 0.92f, onClick = onClick)
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape),
    ) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun LogoTile(size: Dp = 30.dp, radius: Dp = 9.dp, iconSize: Dp = 17.dp) {
    val shape = RoundedCornerShape(radius)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .glow(shape, 8.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF7A9CFF), Color(0xFF2F55E6)))),
    ) {
        Icon(Ic.docSearch, null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

/** 어두운 머리 안의 검색칸 */
@Composable
fun HeroSearchField(value: String, onValue: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            .padding(horizontal = 16.dp),
    ) {
        Icon(Ic.search, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = ts(15.5f, W4), color = Color.White.copy(alpha = 0.6f), maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                textStyle = ts(15.5f, W6).copy(color = Color.White),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .press(scale = 0.9f) { onValue("") }
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
            ) { Icon(Ic.x, "지우기", tint = Color.White, modifier = Modifier.size(12.dp)) }
        }
    }
}

/** 밝은 화면의 검색칸 */
@Composable
fun LightSearchField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val c = B.c
    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .depth(shape)
            .clip(shape)
            .background(c.card)
            .padding(horizontal = 14.dp),
    ) {
        Icon(Ic.search, null, tint = c.ink3, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(9.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = ts(15f), color = c.ink3, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                textStyle = ts(15f, W6).copy(color = c.ink),
                cursorBrush = SolidColor(c.brand),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .press(scale = 0.9f) { onValue("") }
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(c.chip2),
            ) { Icon(Ic.x, "지우기", tint = c.ink2, modifier = Modifier.size(12.dp)) }
        } else if (hint != null) {
            Text(hint, style = ts(12f, W7), color = c.ink3)
        }
    }
}

// ───────────────────────── 아바타 ─────────────────────────

private val AvatarGradients = listOf(
    Color(0xFF6E8DFF) to Color(0xFF3150D6),
    Color(0xFFFF9B7B) to Color(0xFFE1514A),
    Color(0xFF47D3A6) to Color(0xFF11977A),
    Color(0xFFB392FF) to Color(0xFF6F49DB),
    Color(0xFFFFC861) to Color(0xFFE0912A),
)
private val GrayGradient = Color(0xFFC7CCD8) to Color(0xFF9AA2B4)

fun avatarColors(key: String, saved: Boolean): Pair<Color, Color> =
    if (!saved) GrayGradient else AvatarGradients[Math.floorMod(key.hashCode(), AvatarGradients.size)]

/** 이름 첫 글자 */
fun initialOf(name: String): String =
    name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"

/**
 * name 이 null 이면 저장 안 된 번호 → 회색 + 전화기.
 * ring 을 주면 바깥에 그 색 테두리를 둘러 겹쳐 쌓을 때 쓴다.
 */
@Composable
fun Avatar(
    name: String?,
    key: String,
    size: Dp,
    modifier: Modifier = Modifier,
    ring: Color? = null,
    ringWidth: Dp = 2.5.dp,
    checked: Boolean = false,
) {
    val c = B.c
    val (a, b) = avatarColors(key, name != null)
    Box(modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .then(if (ring != null) Modifier.background(ring, CircleShape).padding(ringWidth) else Modifier)
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(a, b))),
        ) {
            if (name == null) {
                Icon(Ic.phone, null, tint = Color.White, modifier = Modifier.size(size * 0.44f))
            } else {
                Text(
                    initialOf(name),
                    style = TextStyle(fontFamily = Pretendard, fontWeight = W8, fontSize = (size.value * 0.37f).sp),
                    color = Color.White,
                )
            }
        }
        if (checked) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 3.dp)
                    .size(20.dp)
                    .background(c.card, CircleShape)
                    .padding(2.5.dp)
                    .background(c.brand, CircleShape),
            ) { Icon(Ic.check, null, tint = Color.White, modifier = Modifier.size(10.dp)) }
        }
    }
}

data class Who(val number: String, val name: String?)

@Composable
fun AvatarStack(people: List<Who>, size: Dp, ring: Color = B.c.card, max: Int = 3) {
    Row(horizontalArrangement = Arrangement.spacedBy(-(size.value * 0.3f).dp)) {
        people.take(max).forEach { Avatar(it.name, it.number, size, ring = ring) }
        if (people.size > max) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .background(ring, CircleShape)
                    .padding(2.5.dp)
                    .size(size)
                    .clip(CircleShape)
                    .background(B.c.chip2),
            ) {
                Text("+${people.size - max}", style = ts(size.value * 0.34f, W8, tracking = 0f), color = B.c.ink2)
            }
        }
    }
}

// ───────────────────────── 작은 부품 ─────────────────────────

@Composable
fun CountPill(icon: ImageVector, count: Int?, fg: Color, bg: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(26.dp)
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 10.dp),
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        if (count != null) {
            Text("$count", style = ts(12.5f, W8, num = true), color = fg)
        } else {
            CircularProgressIndicator(color = fg, strokeWidth = 1.5.dp, modifier = Modifier.size(10.dp))
        }
    }
}

@Composable
fun RecPill(count: Int?) = CountPill(Ic.wave, count, B.c.rec, B.c.recTint)

@Composable
fun MsgPill(count: Int) = CountPill(Ic.msg, count, B.c.brand, B.c.brandTint)

@Composable
fun SmallTag(text: String, fg: Color, bg: Color, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = ts(11.5f, W8, num = true), color = fg, maxLines = 1)
    }
}

@Composable
fun RoleTag(role: String) = SmallTag(role, B.c.brand, B.c.brandTint)

@Composable
fun MethodTag(method: MatchMethod) {
    val c = B.c
    when (method) {
        MatchMethod.CALL_LOG -> SmallTag("통화기록 일치", c.ok, c.okTint, Ic.check)
        MatchMethod.FILE_NUMBER -> SmallTag("번호 일치", c.ok, c.okTint, Ic.check)
        MatchMethod.CONTACT_NAME -> SmallTag("연락처 이름 일치", c.ink2, c.chip)
        MatchMethod.AMBIGUOUS -> SmallTag("확인 필요", c.warn, c.warnTint)
    }
}

/** 회색 작은 꼬리표 (연결된 사건 번호 등) */
@Composable
fun GrayTag(text: String) {
    Text(
        text,
        style = ts(11.5f, W8, num = true),
        color = B.c.ink2,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(B.c.chip)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 54.dp,
    radius: Dp = 17.dp,
    onClick: () -> Unit,
) {
    val c = B.c
    val shape = RoundedCornerShape(radius)
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .press(enabled = enabled, onClick = onClick)
            .height(height)
            .then(if (enabled) Modifier.glow(shape) else Modifier)
            .clip(shape)
            .then(
                if (enabled) Modifier.background(Brush.linearGradient(listOf(c.brand2, c.brand)))
                else Modifier.background(c.chip2),
            )
            .padding(horizontal = 18.dp),
    ) {
        val fg = if (enabled) Color.White else c.ink3
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = ts(16f, W8), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 흰 바탕의 보조 버튼 */
@Composable
fun SoftButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, danger: Boolean = false, onClick: () -> Unit) {
    val c = B.c
    val fg = if (danger) c.rec else c.ink
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .press(onClick = onClick)
            .height(48.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(c.chip)
            .padding(horizontal = 16.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = ts(15f, W7), color = fg)
    }
}

@Composable
fun SectionHeader(
    title: String,
    count: Int? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val c = B.c
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 12.dp),
    ) {
        Text(title, style = ts(20f, W8, tracking = -0.025f), color = c.ink)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                "$count",
                style = ts(12f, W8, num = true),
                color = c.ink2,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(c.chip2)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

/** 섹션 머리 오른쪽의 회색 글자 버튼 */
@Composable
fun HeaderAction(text: String, icon: ImageVector? = null, trailing: ImageVector? = null, onClick: () -> Unit) {
    val c = B.c
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .press(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = c.ink2, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(text, style = ts(13.5f, W7), color = c.ink2)
        if (trailing != null) {
            Spacer(Modifier.width(2.dp))
            Icon(trailing, null, tint = c.ink2, modifier = Modifier.size(15.dp))
        }
    }
}

data class Seg(val label: String, val icon: ImageVector, val badge: Int = 0)

@Composable
fun SegmentedControl(options: List<Seg>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = B.c
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(c.chip2)
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            val shape = RoundedCornerShape(12.dp)
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .then(if (on) Modifier.depth(shape) else Modifier)
                    .clip(shape)
                    .background(if (on) c.card else Color.Transparent)
                    .press(scale = 0.98f) { onSelect(i) },
            ) {
                Icon(o.icon, null, tint = if (on) c.ink else c.ink2, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(o.label, style = ts(14.5f, W7), color = if (on) c.ink else c.ink2)
                if (o.badge > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${o.badge}",
                        style = ts(11.5f, W8, num = true),
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.brand)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun FilterPill(text: String, on: Boolean, onClick: () -> Unit) {
    val c = B.c
    val shape = CircleShape
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .press(scale = 0.95f, onClick = onClick)
            .height(32.dp)
            .clip(shape)
            .then(if (on) Modifier.background(c.ink) else Modifier.border(1.dp, c.chip2, shape))
            .padding(horizontal = 13.dp),
    ) {
        Text(text, style = ts(13f, W7, num = true), color = if (on) c.card else c.ink2)
    }
}

/** 둥근 파란 재생 버튼 */
@Composable
fun PlayButton(playing: Boolean, size: Dp, soft: Boolean = false, onClick: () -> Unit) {
    val c = B.c
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .press(scale = 0.9f, onClick = onClick)
            .size(size)
            .then(if (soft) Modifier else Modifier.glow(CircleShape, 10.dp))
            .clip(CircleShape)
            .then(
                if (soft) Modifier.background(c.brandTint)
                else Modifier.background(Brush.linearGradient(listOf(c.brand2, c.brand))),
            ),
    ) {
        Icon(
            if (playing) Ic.pause else Ic.play,
            if (playing) "일시정지" else "재생",
            tint = if (soft) c.brand else Color.White,
            modifier = Modifier
                .size(size * 0.42f)
                .offset(x = if (playing) 0.dp else size * 0.03f),
        )
    }
}

private val WaveShape = intArrayOf(6, 10, 15, 9, 18, 13, 7, 11, 19, 14, 8, 16, 12, 20, 10, 7, 13, 17, 9, 15, 11, 6, 14, 18, 10, 8, 16, 12, 7, 13, 19, 11, 9, 15, 10)

/** 녹음 막대 모양. progress 가 0..1 이면 그만큼 파랗게, onSeek 이 있으면 눌러서 이동 */
@Composable
fun Waveform(seed: Int, progress: Float, modifier: Modifier = Modifier, onSeek: ((Float) -> Unit)? = null) {
    val c = B.c
    val played = Brush.verticalGradient(listOf(c.brand2, c.brand))
    val rest = c.chip2
    val gestures = if (onSeek != null) {
        Modifier
            .pointerInput(onSeek) { detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(onSeek) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onSeek((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
    } else Modifier
    Canvas(
        modifier
            .fillMaxWidth()
            .height(24.dp)
            .then(gestures),
    ) {
        val bar = 2.6.dp.toPx()
        val gap = 2.dp.toPx()
        val n = ((size.width + gap) / (bar + gap)).toInt().coerceAtLeast(1)
        val unit = size.height / 22f
        val playedBars = (n * progress).toInt()
        val off = Math.floorMod(seed, WaveShape.size)
        for (i in 0 until n) {
            val h = WaveShape[(i + off) % WaveShape.size] * unit
            val x = i * (bar + gap)
            val top = (size.height - h) / 2
            if (i < playedBars) {
                drawRoundRect(played, Offset(x, top), Size(bar, h), CornerRadius(bar / 2))
            } else {
                drawRoundRect(rest, Offset(x, top), Size(bar, h), CornerRadius(bar / 2))
            }
        }
    }
}

@Composable
fun Loading(text: String, modifier: Modifier = Modifier) {
    val c = B.c
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
    ) {
        CircularProgressIndicator(color = c.brand, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = ts(14f, W6), color = c.ink2)
    }
}

/** 비어 있을 때 보여주는 카드 */
@Composable
fun EmptyCard(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val c = B.c
    BCard(modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 26.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(c.brandTint),
            ) { Icon(icon, null, tint = c.brand, modifier = Modifier.size(23.dp)) }
            Spacer(Modifier.height(14.dp))
            Text(title, style = ts(16.5f, W8), color = c.ink)
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = ts(13.5f, W4, lineHeight = 1.55f).copy(textAlign = TextAlign.Center),
                color = c.ink2,
            )
            if (action != null) {
                Spacer(Modifier.height(16.dp))
                action()
            }
        }
    }
}

/** 동그란 회색 닫기 버튼 */
@Composable
fun CloseCircle(size: Dp = 34.dp, onClick: () -> Unit) {
    val c = B.c
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .press(scale = 0.9f, onClick = onClick)
            .size(size)
            .clip(CircleShape)
            .background(c.chip),
    ) { Icon(Ic.x, "닫기", tint = c.ink2, modifier = Modifier.size(15.dp)) }
}

/** 상태표시줄 아이콘 색: 어두운 머리 위면 흰색 */
@Composable
fun StatusBarIcons(lightContent: Boolean) {
    val view = LocalView.current
    val dark = B.c.dark
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        val ctl = WindowCompat.getInsetsController(activity.window, view)
        ctl.isAppearanceLightStatusBars = !lightContent && !dark
        ctl.isAppearanceLightNavigationBars = !dark
    }
}

// ───────────────────────── 하단 떠 있는 것들 ─────────────────────────

enum class Tab { CASES, SETTINGS }

@Composable
fun FloatingTabBar(selected: Tab, onTab: (Tab) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val c = B.c
    val barShape = RoundedCornerShape(32.dp)
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 14.dp)
            .height(84.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(64.dp)
                .depth(barShape, Depth.FLOAT)
                .clip(barShape)
                .background(c.glass)
                .border(1.dp, c.glassLine, barShape),
        ) {
            TabItem("사건", Ic.folder, selected == Tab.CASES, Modifier.weight(1f)) { onTab(Tab.CASES) }
            Spacer(Modifier.width(90.dp))
            TabItem("설정", Ic.gear, selected == Tab.SETTINGS, Modifier.weight(1f)) { onTab(Tab.SETTINGS) }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .press(scale = 0.92f, onClick = onAdd)
                .size(74.dp)
                .background(c.bg, CircleShape)
                .padding(6.dp)
                .shadow(16.dp, CircleShape, ambientColor = Color(0xFF2E4BFF), spotColor = Color(0xFF2E4BFF))
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF7194FF), Color(0xFF2E4BFF)))),
        ) {
            Icon(Ic.plus, "사건 등록", tint = Color.White, modifier = Modifier.size(27.dp))
        }
    }
}

@Composable
private fun TabItem(label: String, icon: ImageVector, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = B.c
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxHeight()
            .press(scale = 0.94f, onClick = onClick)
            .padding(top = 10.dp),
    ) {
        Icon(icon, label, tint = if (on) c.ink else c.ink3, modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, style = ts(11f, W7), color = if (on) c.ink else c.ink3)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (on) c.brand else Color.Transparent),
        )
    }
}

/** 재생 중인 녹음: 화면 아래 떠 있는 작은 재생기 */
@Composable
fun MiniPlayer(player: Player, modifier: Modifier = Modifier) {
    val c = B.c
    if (player.currentKey == null) return
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
            .height(68.dp)
            .depth(shape, Depth.FLOAT)
            .clip(shape)
            .background(c.glass)
            .border(1.dp, c.glassLine, shape),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
        ) {
            PlayButton(player.isPlaying, 42.dp) { player.playPause() }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(player.title, style = ts(14.5f, W8), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Fmt.duration(player.positionMs)} / ${Fmt.duration(player.durationMs)}" +
                        if (player.subtitle.isNotEmpty()) " · ${player.subtitle}" else "",
                    style = ts(12f, W6, num = true),
                    color = c.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            CloseCircle { player.release() }
        }
        val p = if (player.durationMs > 0) (player.positionMs.toFloat() / player.durationMs).coerceIn(0f, 1f) else 0f
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(3.dp)
                .background(c.chip2)
                .drawBehind {
                    drawRect(
                        Brush.horizontalGradient(listOf(c.brand2, c.brand)),
                        size = Size(size.width * p, size.height),
                    )
                },
        )
    }
}

/** 화면 아래 떠 있는 바 (선택 바 등)의 틀 */
@Composable
fun FloatingBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val c = B.c
    val shape = RoundedCornerShape(26.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
            .depth(shape, Depth.FLOAT)
            .clip(shape)
            .background(c.glass)
            .border(1.dp, c.glassLine, shape)
            .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        content = content,
    )
}
