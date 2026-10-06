package com.bosang.search.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bosang.search.data.Player
import com.bosang.search.data.TimelineItem
import kotlinx.coroutines.delay

enum class KindFilter(val label: String) { ALL("전체"), REC("통화녹음"), SMS("문자") }

fun List<TimelineItem>.filterKind(k: KindFilter): List<TimelineItem> = when (k) {
    KindFilter.ALL -> this
    KindFilter.REC -> filterIsInstance<TimelineItem.Rec>()
    KindFilter.SMS -> filterIsInstance<TimelineItem.Sms>()
}

@Composable
fun KindFilterRow(all: List<TimelineItem>, selected: KindFilter, onSelect: (KindFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KindFilter.entries.forEach { k ->
            FilterChip(
                selected = selected == k,
                onClick = { onSelect(k) },
                label = { Text("${k.label} ${all.filterKind(k).size}") },
            )
        }
    }
}

/** 로딩·빈 상태·날짜별 묶음까지 포함한 타임라인 */
fun LazyListScope.timeline(
    items: List<TimelineItem>?,
    whoLabel: (number: String) -> String?,
    player: Player,
) {
    if (items == null) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 24.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("폰에서 문자·통화녹음을 찾는 중…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    if (items.isEmpty()) {
        item {
            Text(
                "찾은 문자·통화녹음이 없어요.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }
        return
    }
    val groups = items.groupBy { Fmt.dayKey(it.timeMillis) }
    groups.forEach { (_, dayItems) ->
        item(key = "d-" + Fmt.dayKey(dayItems.first().timeMillis)) {
            Text(
                Fmt.day(dayItems.first().timeMillis),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
            )
        }
        items(dayItems, key = { it.key() }) { item ->
            when (item) {
                is TimelineItem.Sms -> SmsCard(item, whoLabel(item.number))
                is TimelineItem.Rec -> RecCard(item, whoLabel(item.number), player)
            }
        }
    }
}

private fun TimelineItem.key(): String = when (this) {
    is TimelineItem.Sms -> "s-${sms.id}"
    is TimelineItem.Rec -> "r-${rec.file.id}-$number"
}

@Composable
private fun SmsCard(item: TimelineItem.Sms, who: String?) {
    val cs = MaterialTheme.colorScheme
    val incoming = item.sms.incoming
    Card(
        colors = CardDefaults.cardColors(containerColor = if (incoming) cs.surfaceContainer else cs.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (incoming) "📩 받은 문자" else "📤 보낸 문자",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (who != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(who, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Text(Fmt.time(item.timeMillis), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.size(6.dp))
            Text(item.sms.body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun RecCard(item: TimelineItem.Rec, who: String?, player: Player) {
    val cs = MaterialTheme.colorScheme
    val f = item.rec.file
    val key = f.cacheKey
    val isCurrent = player.currentKey == key
    if (isCurrent && player.isPlaying) {
        LaunchedEffect(key, player.isPlaying) {
            while (true) {
                player.tick()
                delay(250)
            }
        }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { player.toggle(key, f.uri) }) {
                    if (isCurrent && player.isPlaying) {
                        Text("❚❚", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "재생")
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎙 통화녹음", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        if (who != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(who, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                        }
                    }
                    Text(
                        "${Fmt.time(item.timeMillis)} · ${Fmt.duration(f.durationMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
                MethodTag(item.rec.match.method)
            }
            if (isCurrent) {
                val dur = player.durationMs.coerceAtLeast(1L)
                Slider(
                    value = player.positionMs.toFloat().coerceIn(0f, dur.toFloat()),
                    onValueChange = { player.seekTo(it.toLong()) },
                    valueRange = 0f..dur.toFloat(),
                )
                Text(
                    "${Fmt.duration(player.positionMs)} / ${Fmt.duration(player.durationMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                )
            }
            if (player.errorKey == key) {
                Text(
                    "녹음 파일을 열 수 없어요. 파일이 지워졌거나 옮겨졌을 수 있어요.",
                    color = cs.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
