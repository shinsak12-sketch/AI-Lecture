package com.bosang.search.ui

import android.net.Uri
import android.provider.CallLog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import com.bosang.search.data.Issue
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bosang.search.core.MatchMethod
import com.bosang.search.data.Player
import com.bosang.search.data.TimelineItem

/** 문자 · 통화녹음 · 통화를 나눠서 본다. 문자가 기본 (가장 빨리 뜸) */
enum class Kind(val label: String) { SMS("문자"), REC("녹음"), CALL("통화"), PHOTO("사진") }

/** 종류 탭: 개수, 찾는 중이면 작은 원 */
@Composable
fun KindTabs(
    selected: Kind,
    counts: Map<Kind, Int?>,
    onSelect: (Kind) -> Unit,
    modifier: Modifier = Modifier,
    kinds: List<Kind> = Kind.entries,
) {
    val c = B.c
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(c.chip2)
            .padding(4.dp),
    ) {
        kinds.forEach { k ->
            val on = k == selected
            val shape = RoundedCornerShape(12.dp)
            val (icon, tint) = when (k) {
                Kind.SMS -> Ic.msg to c.brand
                Kind.REC -> Ic.wave to c.rec
                Kind.CALL -> Ic.phone to c.ink2
                Kind.PHOTO -> Ic.image to c.ok
            }
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .then(if (on) Modifier.depth(shape) else Modifier)
                    .clip(shape)
                    .background(if (on) c.card else Color.Transparent)
                    .press(scale = 0.97f) { onSelect(k) },
            ) {
                Icon(icon, null, tint = if (on) tint else c.ink3, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text(k.label, style = ts(14f, W7), color = if (on) c.ink else c.ink2, maxLines = 1)
                Spacer(Modifier.width(5.dp))
                val n = counts[k]
                if (n == null) {
                    CircularProgressIndicator(color = c.ink3, strokeWidth = 1.5.dp, modifier = Modifier.size(11.dp))
                } else {
                    Text("$n", style = ts(12.5f, W8, num = true), color = if (on) tint else c.ink3)
                }
            }
        }
    }
}

/** 타임라인 카드의 ⋮ · 붙은 특이사항 · 문자 사진 누르기 */
class TimelineActions(
    val onMore: ((TimelineItem) -> Unit)? = null,
    val attached: (TimelineItem) -> List<Issue> = { emptyList() },
    val nameOf: (String) -> String = { it },
    val onIssue: (Issue) -> Unit = {},
    val onImage: (Uri) -> Unit = {},
)

/** 타임라인 카드에 붙는 사람 정보 */
data class WhoInfo(val name: String, val role: String?)

private val RailStart = 18.dp
private val RailWidth = 22.dp
private val RailX = 6.dp

/** 세로 선 + 날짜 묶음 + 카드. who 가 null 을 주면 사람 이름 없이 (사람 상세) */
fun LazyListScope.timeline(
    items: List<TimelineItem>?,
    player: Player,
    emptyText: String,
    who: ((String) -> WhoInfo?)? = null,
    loadingText: String = "폰에서 찾는 중",
    actions: TimelineActions = TimelineActions(),
) {
    if (items == null) {
        item(key = "tl-loading") { Loading(loadingText) }
        return
    }
    if (items.isEmpty()) {
        item(key = "tl-empty") {
            EmptyCard(
                icon = Ic.docSearch,
                title = "찾은 기록이 없어요",
                body = emptyText,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        return
    }
    val groups = items.groupBy { Fmt.dayKey(it.timeMillis) }.entries.toList()
    groups.forEachIndexed { gi, (day, dayItems) ->
        item(key = "tl-d-$day") {
            DayRow(Fmt.dayLabel(dayItems.first().timeMillis), first = gi == 0)
        }
        dayItems.forEachIndexed { i, ti ->
            val isLast = gi == groups.lastIndex && i == dayItems.lastIndex
            item(key = ti.key()) {
                val w = who?.invoke(ti.number)
                val more = actions.onMore?.let { f -> { f(ti) } }
                ItemRow(ti, last = isLast) {
                    when (ti) {
                        is TimelineItem.Rec -> RecCard(ti, w, player, showWho = who != null, onMore = more)
                        is TimelineItem.Sms -> SmsCard(ti, w, showWho = who != null, onMore = more, onImage = actions.onImage)
                        is TimelineItem.Call -> CallCard(ti, w, player, showWho = who != null, onMore = more)
                    }
                    val rec = when (ti) {
                        is TimelineItem.Rec -> ti.rec
                        is TimelineItem.Call -> ti.rec
                        else -> null
                    }
                    actions.attached(ti).forEach { iss ->
                        AttachedIssue(
                            iss,
                            actions.nameOf,
                            onOpen = { actions.onIssue(iss) },
                            onPlayAt = rec?.let { r ->
                                { at: Long ->
                                    player.toggle(
                                        r.file.cacheKey, r.file.uri,
                                        listOfNotNull(w?.name ?: "통화 녹음", w?.role).joinToString(" · "),
                                        Fmt.time(r.file.timeMillis), ti.number, r.file.timeMillis, at,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun TimelineItem.key(): String = when (this) {
    is TimelineItem.Sms -> "s-${sms.id}"
    is TimelineItem.Rec -> "r-${rec.file.id}-$number"
    is TimelineItem.Call -> "k-${call.timeMillis}-$number"
}

@Composable
private fun DayRow(text: String, first: Boolean) {
    val c = B.c
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = RailStart, end = 16.dp)
            .drawBehind {
                val x = RailX.toPx()
                val cy = size.height / 2
                val w = 2.dp.toPx()
                drawLine(c.chip2, Offset(x, if (first) cy else 0f), Offset(x, size.height), strokeWidth = w)
                drawCircle(c.bg, 6.dp.toPx(), Offset(x, cy))
                drawCircle(c.ink3, 4.75.dp.toPx(), Offset(x, cy), style = Stroke(2.5.dp.toPx()))
            }
            .padding(start = RailWidth, top = 8.dp, bottom = 10.dp),
    ) {
        Text(text, style = ts(12.5f, W8, tracking = 0.02f), color = c.ink2)
    }
}

@Composable
private fun ItemRow(item: TimelineItem, last: Boolean, content: @Composable () -> Unit) {
    val c = B.c
    val (node, tint) = when (item) {
        is TimelineItem.Rec -> c.rec to c.recTint
        is TimelineItem.Sms -> c.brand to c.brandTint
        is TimelineItem.Call -> if (item.rec != null) c.rec to c.recTint else c.ink3 to c.chip2
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = RailStart, end = 16.dp)
            .drawBehind {
                val x = RailX.toPx()
                val w = 2.dp.toPx()
                if (last) {
                    drawLine(
                        Brush.verticalGradient(listOf(c.chip2, Color.Transparent), startY = 0f, endY = size.height),
                        Offset(x, 0f),
                        Offset(x, size.height),
                        strokeWidth = w,
                    )
                } else {
                    drawLine(c.chip2, Offset(x, 0f), Offset(x, size.height), strokeWidth = w)
                }
                val cy = 24.dp.toPx()
                drawCircle(c.bg, 7.5.dp.toPx(), Offset(x, cy))
                drawCircle(tint, 7.dp.toPx(), Offset(x, cy))
                drawCircle(node, 4.dp.toPx(), Offset(x, cy))
            }
            .padding(start = RailWidth, bottom = 10.dp),
    ) { content() }
}

@Composable
private fun RecCard(item: TimelineItem.Rec, who: WhoInfo?, player: Player, showWho: Boolean, onMore: (() -> Unit)?) {
    val c = B.c
    val f = item.rec.file
    val match = item.rec.match
    val key = f.cacheKey
    val isCurrent = player.currentKey == key
    val playing = isCurrent && player.isPlaying
    val dur = if (isCurrent && player.durationMs > 0) player.durationMs else f.durationMs
    val progress = if (isCurrent && dur > 0) (player.positionMs.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val name = who?.name
    val title = if (showWho && name != null) name else "통화 녹음"
    val playerTitle = listOfNotNull(name ?: "통화 녹음", who?.role).joinToString(" · ")

    BCard(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            PlayButton(playing, 42.dp, soft = !isCurrent && match.method == MatchMethod.AMBIGUOUS) {
                player.toggle(
                    key, f.uri, playerTitle,
                    Fmt.dayLabel(item.timeMillis).substringBefore(" ·") + " " + Fmt.time(item.timeMillis),
                    item.number, f.timeMillis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.weight(1f)) {
                        Text(
                            title,
                            style = ts(14.5f, W8),
                            color = c.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        val sub = if (showWho) who?.role else Fmt.time(item.timeMillis)
                        if (sub != null) {
                            Spacer(Modifier.width(5.dp))
                            Text(sub, style = ts(12f, W6, num = true), color = c.ink2, maxLines = 1)
                        }
                    }
                    if (dur > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isCurrent) "${Fmt.duration(player.positionMs)} / ${Fmt.duration(dur)}" else Fmt.duration(dur),
                            style = ts(12.5f, W7, num = true),
                            color = if (isCurrent) c.brand else c.ink2,
                        )
                    }
                    if (onMore != null) MoreBtn(onMore = onMore)
                }
                Waveform(
                    seed = (f.id % 1000).toInt(),
                    progress = progress,
                    modifier = Modifier.padding(vertical = 6.dp),
                    onSeek = if (isCurrent && dur > 0) { p -> player.seekTo((p * dur).toLong()) } else null,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showWho) {
                        Text(Fmt.time(item.timeMillis), style = ts(12f, W6, num = true), color = c.ink2)
                        Spacer(Modifier.width(6.dp))
                    }
                    MethodTag(match.method)
                    if (match.method == MatchMethod.AMBIGUOUS) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "같은 이름 연락처 ${match.numbers.size}개",
                            style = ts(12f, W6),
                            color = c.ink2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (player.errorKey == key) {
            Text(
                "녹음 파일을 열 수 없어요. 지워졌거나 옮겨졌을 수 있어요.",
                style = ts(12.5f, W7),
                color = c.rec,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun SmsCard(item: TimelineItem.Sms, who: WhoInfo?, showWho: Boolean, onMore: (() -> Unit)?, onImage: (Uri) -> Unit) {
    val c = B.c
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val incoming = item.sms.incoming
    var expanded by remember { mutableStateOf(false) }
    val fg = if (incoming) c.ink else Color.White
    val meta = if (incoming) c.ink2 else Color.White.copy(alpha = 0.6f)
    val dirColor = if (incoming) c.brand else Color.White.copy(alpha = 0.78f)
    val brush = if (incoming) null else Brush.linearGradient(listOf(Color(0xFF1C2B63), Color(0xFF0F1A40)))

    BCard(
        modifier = Modifier
            .fillMaxWidth()
            .press(
                scale = 0.985f,
                onLongClick = {
                    clipboard.setText(AnnotatedString(item.sms.body))
                    Toast.makeText(ctx, "문자 내용을 복사했어요", Toast.LENGTH_SHORT).show()
                },
            ) { expanded = !expanded },
        brush = brush,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (incoming) Ic.incoming else Ic.outgoing, null, tint = dirColor, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text(if (incoming) "받은 문자" else "보낸 문자", style = ts(12f, W7), color = dirColor)
                Spacer(Modifier.width(6.dp))
                val parts = buildList {
                    if (showWho && who != null) {
                        if (incoming) {
                            add(who.name)
                            who.role?.let { add(it) }
                        } else {
                            add(who.name + "에게")
                        }
                    }
                    add(Fmt.time(item.timeMillis))
                }
                Text(
                    parts.joinToString(" · "),
                    style = ts(12f, W7, num = true),
                    color = meta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (onMore != null) MoreBtn(tint = meta, onMore = onMore)
            }
            if (item.sms.body.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    item.sms.body,
                    style = ts(14.5f, W4, lineHeight = 1.5f),
                    color = fg,
                    maxLines = if (expanded) Int.MAX_VALUE else 7,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.sms.images.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 9.dp).horizontalScroll(rememberScrollState()),
                ) {
                    item.sms.images.forEach { u ->
                        Thumb(
                            u.toString(),
                            Modifier.size(width = 96.dp, height = 72.dp).clip(RoundedCornerShape(12.dp)).press(scale = 0.95f) { onImage(u) },
                            px = 300,
                        )
                    }
                }
                if (incoming) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 7.dp)) {
                        Icon(Ic.check, null, tint = c.ok, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("사진 ${item.sms.images.size}장 사건에 자동 보관", style = ts(11.5f, W7), color = c.ok)
                    }
                }
            }
        }
    }
}

@Composable
private fun CallCard(item: TimelineItem.Call, who: WhoInfo?, player: Player, showWho: Boolean, onMore: (() -> Unit)?) {
    val c = B.c
    val call = item.call
    val missed = call.type == CallLog.Calls.MISSED_TYPE || call.type == CallLog.Calls.REJECTED_TYPE
    val incoming = call.type == CallLog.Calls.INCOMING_TYPE
    val kind = when {
        call.type == CallLog.Calls.REJECTED_TYPE -> "거절"
        missed -> "부재중"
        incoming -> "받은 전화"
        else -> "건 전화"
    }
    val (fg, bg) = when {
        missed -> c.rec to c.recTint
        incoming -> c.brand to c.brandTint
        else -> c.ok to c.okTint
    }
    BCard(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            IconTile(if (missed) Ic.missed else if (incoming) Ic.incoming else Ic.outgoing, fg, bg, 36.dp, 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val title = if (showWho && who != null) listOfNotNull(who.name, who.role).joinToString(" · ") else kind
                Text(title, style = ts(14.5f, W8), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = buildList {
                    if (showWho) add(kind)
                    add(Fmt.time(call.timeMillis))
                    if (!missed && call.durationSec > 0) add(Fmt.durationKo(call.durationSec * 1000))
                }
                Text(
                    sub.joinToString(" · "),
                    style = ts(12.5f, W6, num = true),
                    color = if (missed && showWho) c.rec else c.ink2,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            val rec = item.rec
            if (rec != null) {
                val key = rec.file.cacheKey
                val playing = player.currentKey == key && player.isPlaying
                Spacer(Modifier.width(8.dp))
                SmallTag("녹취", c.rec, c.recTint)
                Spacer(Modifier.width(8.dp))
                PlayButton(playing, 36.dp) {
                    val t = listOfNotNull(who?.name ?: "통화 녹음", who?.role).joinToString(" · ")
                    player.toggle(key, rec.file.uri, t, Fmt.dayLabel(call.timeMillis).substringBefore(" ·") + " " + Fmt.time(call.timeMillis), call.number, rec.file.timeMillis)
                }
            }
            if (onMore != null) {
                Spacer(Modifier.width(4.dp))
                MoreBtn(onMore = onMore)
            }
        }
    }
}

@Composable
private fun MoreBtn(tint: Color = B.c.ink3, onMore: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(start = 4.dp)
            .size(28.dp)
            .clip(RoundedCornerShape(9.dp))
            .press(scale = 0.9f, onClick = onMore),
    ) { Icon(Ic.moreV, "더보기", tint = tint, modifier = Modifier.size(17.dp)) }
}
