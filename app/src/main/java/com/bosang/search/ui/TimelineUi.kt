package com.bosang.search.ui

import android.widget.Toast
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

enum class KindFilter(val label: String) { ALL("전체"), REC("통화녹음"), SMS("문자") }

fun List<TimelineItem>.filterKind(k: KindFilter): List<TimelineItem> = when (k) {
    KindFilter.ALL -> this
    KindFilter.REC -> filterIsInstance<TimelineItem.Rec>()
    KindFilter.SMS -> filterIsInstance<TimelineItem.Sms>()
}

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
) {
    if (items == null) {
        item(key = "tl-loading") { Loading("폰에서 문자·통화녹음을 찾는 중") }
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
                ItemRow(isRec = ti is TimelineItem.Rec, last = isLast) {
                    when (ti) {
                        is TimelineItem.Rec -> RecCard(ti, who?.invoke(ti.number), player, showWho = who != null)
                        is TimelineItem.Sms -> SmsCard(ti, who?.invoke(ti.number), showWho = who != null)
                    }
                }
            }
        }
    }
}

private fun TimelineItem.key(): String = when (this) {
    is TimelineItem.Sms -> "s-${sms.id}"
    is TimelineItem.Rec -> "r-${rec.file.id}-$number"
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
private fun ItemRow(isRec: Boolean, last: Boolean, content: @Composable () -> Unit) {
    val c = B.c
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
                val node = if (isRec) c.rec else c.brand
                val tint = if (isRec) c.recTint else c.brandTint
                drawCircle(c.bg, 7.5.dp.toPx(), Offset(x, cy))
                drawCircle(tint, 7.dp.toPx(), Offset(x, cy))
                drawCircle(node, 4.dp.toPx(), Offset(x, cy))
            }
            .padding(start = RailWidth, bottom = 10.dp),
    ) { content() }
}

@Composable
private fun RecCard(item: TimelineItem.Rec, who: WhoInfo?, player: Player, showWho: Boolean) {
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
                player.toggle(key, f.uri, playerTitle, Fmt.dayLabel(item.timeMillis).substringBefore(" ·") + " " + Fmt.time(item.timeMillis))
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
private fun SmsCard(item: TimelineItem.Sms, who: WhoInfo?, showWho: Boolean) {
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
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                item.sms.body,
                style = ts(14.5f, W4, lineHeight = 1.5f),
                color = fg,
                maxLines = if (expanded) Int.MAX_VALUE else 7,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
