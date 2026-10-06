package com.bosang.search.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bosang.search.core.PhoneNumbers
import com.bosang.search.data.CaseLink
import com.bosang.search.data.CaseSummary
import com.bosang.search.data.PhoneData
import com.bosang.search.data.Player
import com.bosang.search.data.RecordingIndex
import com.bosang.search.data.Store
import com.bosang.search.data.Summaries
import com.bosang.search.data.Records
import com.bosang.search.core.CallEntry
import kotlinx.coroutines.launch
import com.bosang.search.data.TimelineItem
import com.bosang.search.data.IssueSource
import com.bosang.search.data.Photos
import com.bosang.search.data.toSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PersonScreen(
    store: Store,
    data: PhoneData,
    player: Player,
    number: String,
    resumeTick: Int,
    onBack: () -> Unit,
    onOpenCase: (String) -> Unit,
    onRegister: () -> Unit,
    onIssue: (caseNo: String, source: IssueSource) -> Unit,
) {
    val c = B.c
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    StatusBarIcons(lightContent = true)
    val ver = store.version.intValue
    val links = remember(ver, number) { store.linksForNumber(number) }
    var contactName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(number) {
        store.touchRecent("p:$number")
        contactName = withContext(Dispatchers.IO) { data.contactName(number) }
    }
    val name = store.nameOf(number) ?: contactName
    val formatted = PhoneNumbers.format(number)

    val numbers = remember(number) { setOf(number) }
    var sms by remember(number) { mutableStateOf<List<TimelineItem.Sms>?>(null) }
    var calls by remember(number) { mutableStateOf<List<CallEntry>?>(null) }
    var recs by remember(number) { mutableStateOf<List<TimelineItem.Rec>?>(null) }
    var summaries by remember { mutableStateOf<Map<String, CaseSummary>?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(number, resumeTick, refresh) {
        // 문자 · 통화는 바로, 통화녹음은 뒤에서
        launch { sms = Records.sms(numbers, data) }
        launch { calls = Records.calls(numbers, data) }
        RecordingIndex.peek()?.let { recs = Records.recs(numbers, it) }
        val index = RecordingIndex.get(data, store)
        recs = withContext(Dispatchers.Default) { Records.recs(numbers, index) }
    }
    LaunchedEffect(links, resumeTick, refresh) {
        val base = Summaries.base(links.map { it.caseNo }, data, store)
        summaries = Summaries.summarize(base, RecordingIndex.peek())
        summaries = Summaries.summarize(base, RecordingIndex.get(data, store))
    }
    var kind by remember { mutableStateOf(Kind.SMS) }
    val callItems = remember(calls, recs) { calls?.let { Records.callItems(it, recs) } }
    val shown: List<TimelineItem>? = when (kind) {
        Kind.SMS -> sms
        Kind.REC -> recs
        Kind.CALL -> callItems
        Kind.PHOTO -> null
    }
    var recordMenu by remember { mutableStateOf<TimelineItem?>(null) }
    var pickCaseFor by remember { mutableStateOf<TimelineItem?>(null) }
    var moreMenu by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 120.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        item(key = "hero") {
            Overlap(
                overlap = 64.dp,
                top = {
                    Hero(bottomPadding = 92.dp) {
                        HeroTopBar(
                            left = { GlassCircle(Ic.back, "뒤로", onClick = onBack) },
                            right = {
                                Box {
                                    GlassCircle(Ic.more, "더보기") { moreMenu = true }
                                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                        MenuItem("번호 복사", Ic.copy) {
                                            moreMenu = false
                                            clipboard.setText(AnnotatedString(formatted))
                                            Toast.makeText(ctx, "번호를 복사했어요", Toast.LENGTH_SHORT).show()
                                        }
                                        MenuItem("통화녹음 다시 찾기", Ic.refresh) {
                                            moreMenu = false
                                            RecordingIndex.invalidate()
                                            refresh++
                                        }
                                    }
                                }
                            },
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            BigAvatar(name, number)
                            Text(
                                name ?: formatted,
                                style = ts(27f, W8, tracking = -0.03f, num = name == null),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 14.dp),
                            )
                            Text(
                                if (name != null) formatted else "저장 안 된 번호",
                                style = ts(14.5f, W6, num = true),
                                color = Color.White.copy(alpha = 0.66f),
                                modifier = Modifier.padding(top = 3.dp),
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(22.dp),
                                modifier = Modifier.padding(top = 20.dp),
                            ) {
                                QuickAction("전화", Ic.phone) {
                                    runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
                                }
                                QuickAction("문자", Ic.msg) {
                                    runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))) }
                                }
                                QuickAction("사건 연결", Ic.link, onClick = onRegister)
                            }
                        }
                    }
                },
                bottom = {
                    if (links.isEmpty()) {
                        BCard(
                            level = Depth.LIFT,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .press(scale = 0.98f, onClick = onRegister),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                                IconTile(Ic.link, c.brand, c.brandTint, 42.dp, 14.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("아직 연결된 사건이 없어요", style = ts(15.5f, W8), color = c.ink)
                                    Text("눌러서 사고번호에 연결하기", style = ts(13f, W4), color = c.ink2)
                                }
                                androidx.compose.material3.Icon(Ic.chevron, null, tint = c.ink3, modifier = Modifier.size(16.dp))
                            }
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        ) {
                            items(links, key = { it.caseNo }) { link ->
                                PassCard(link, store, summaries?.get(link.caseNo)) { onOpenCase(link.caseNo) }
                            }
                        }
                    }
                },
            )
        }

        item(key = "tl-head") {
            SectionHeader(
                "전체 기록",
                modifier = Modifier.padding(top = if (links.isEmpty()) 8.dp else 0.dp),
            ) {
                Text("사건 구분 없이", style = ts(13.5f, W7), color = c.ink2)
            }
        }
        item(key = "tl-tabs") {
            KindTabs(
                selected = kind,
                counts = mapOf(Kind.SMS to sms?.size, Kind.REC to recs?.size, Kind.CALL to callItems?.size),
                kinds = listOf(Kind.SMS, Kind.REC, Kind.CALL),
                onSelect = { kind = it },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            )
        }
        timeline(
            items = shown,
            player = player,
            emptyText = if (kind == Kind.REC) "이 번호의 통화녹음을 폰에서 찾지 못했어요." else "이 번호와의 ${kind.label} 기록이 없어요.",
            who = null,
            actions = TimelineActions(onMore = { recordMenu = it }),
            loadingText = if (kind == Kind.REC) "통화녹음을 찾는 중 (파일이 많으면 조금 걸려요)" else "불러오는 중",
        )
    }

    recordMenu?.let { item ->
        val title = (name ?: formatted) + " · " + when (item) {
            is TimelineItem.Rec -> "통화 녹음"
            is TimelineItem.Sms -> if (item.sms.incoming) "받은 문자" else "보낸 문자"
            is TimelineItem.Call -> "통화"
        }
        val sub = Fmt.dayLabel(item.timeMillis).substringBefore(" ·") + " " + Fmt.time(item.timeMillis)
        OptionsDialog(
            title = title,
            subtitle = sub,
            onDismiss = { recordMenu = null },
            items = buildList {
                if (links.isNotEmpty()) {
                    add(Option("특이사항 남기기", Ic.pen) {
                        recordMenu = null
                        if (links.size == 1) onIssue(links.first().caseNo, item.toSource()) else pickCaseFor = item
                    })
                }
                when (item) {
                    is TimelineItem.Rec -> add(Option("공유", Ic.share) {
                        recordMenu = null
                        Photos.share(ctx, item.rec.file.uri, item.rec.file.displayName, "audio/*")
                    })
                    is TimelineItem.Sms -> add(Option("공유", Ic.share) {
                        recordMenu = null
                        Photos.shareText(ctx, item.sms.body)
                    })
                    else -> {}
                }
                add(Option(if (item is TimelineItem.Sms) "내용 복사" else "정보 복사", Ic.copy) {
                    recordMenu = null
                    clipboard.setText(AnnotatedString(if (item is TimelineItem.Sms) item.sms.body else "$title · $sub"))
                    Toast.makeText(ctx, "복사했어요", Toast.LENGTH_SHORT).show()
                })
            },
        )
    }

    pickCaseFor?.let { item ->
        OptionsDialog(
            title = "어느 사건의 특이사항인가요?",
            subtitle = null,
            onDismiss = { pickCaseFor = null },
            items = links.map { l ->
                Option("${l.caseNo} · ${l.role}", Ic.folder) {
                    pickCaseFor = null
                    onIssue(l.caseNo, item.toSource())
                }
            },
        )
    }
}

@Composable
private fun BigAvatar(name: String?, number: String) {
    val (a, b) = avatarColors(number, name != null)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(top = 4.dp)
            .size(98.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f)),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(88.dp)
                .shadow(16.dp, CircleShape, ambientColor = Color(0xFF1E3CC8), spotColor = Color(0xFF1E3CC8))
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(a, b))),
        ) {
            if (name == null) {
                androidx.compose.material3.Icon(Ic.phone, null, tint = Color.White, modifier = Modifier.size(38.dp))
            } else {
                Text(initialOf(name), style = ts(34f, W8, tracking = 0f), color = Color.White)
            }
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassCircle(icon, label, size = 48.dp, iconSize = 20.dp, onClick = onClick)
        Spacer(Modifier.height(6.dp))
        Text(label, style = ts(12f, W7), color = Color.White.copy(alpha = 0.82f))
    }
}

/** 지갑 속 카드처럼 생긴 연결된 사건 */
@Composable
private fun PassCard(link: CaseLink, store: Store, summary: CaseSummary?, onClick: () -> Unit) {
    val c = B.c
    val others = store.linksForCase(link.caseNo).filter { it.number != link.number }
    val alt = Math.floorMod(link.caseNo.hashCode(), 2) == 1
    val bar = if (alt) listOf(Color(0xFFFF9B7B), Color(0xFFE1514A)) else listOf(c.brand2, c.brand)
    val (fg, bg) = if (alt) c.rec to c.recTint else c.brand to c.brandTint
    BCard(
        level = Depth.LIFT,
        modifier = Modifier
            .width(236.dp)
            .press(scale = 0.97f, onClick = onClick),
    ) {
        Column(
            Modifier
                .drawBehind {
                    drawRect(Brush.horizontalGradient(bar), size = Size(size.width, 4.dp.toPx()))
                }
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallTag(link.role, fg, bg)
                Spacer(Modifier.weight(1f))
                summary?.lastTime?.let { Text(Fmt.short(it), style = ts(12f, W7, num = true), color = c.ink3) }
            }
            Text(
                link.caseNo,
                style = ts(20f, W8, tracking = -0.015f, num = true),
                color = c.ink,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                if (others.isEmpty()) "단독" else withJosa(others.joinToString(" · ") { store.displayName(it.number) }, "과", "와") + " 함께",
                style = ts(12.5f, W4),
                color = c.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 12.dp)) {
                if (summary != null) {
                    RecPill(summary.recCount)
                    MsgPill(summary.smsCount)
                } else {
                    Box(
                        Modifier
                            .height(26.dp)
                            .width(90.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(c.chip),
                    )
                }
            }
        }
    }
}
