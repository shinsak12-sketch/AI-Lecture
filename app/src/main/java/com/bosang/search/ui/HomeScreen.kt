package com.bosang.search.ui

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.bosang.search.core.PhoneNumbers
import com.bosang.search.data.CaseSummary
import com.bosang.search.data.PhoneData
import com.bosang.search.data.Player
import com.bosang.search.data.RecordingIndex
import com.bosang.search.data.Store
import com.bosang.search.data.Summaries
import com.bosang.search.data.TimelineItem
import java.time.LocalDate

@Composable
fun HomeScreen(
    store: Store,
    data: PhoneData,
    player: Player,
    resumeTick: Int,
    onOpenCase: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val c = B.c
    StatusBarIcons(lightContent = true)
    val ver = store.version.intValue
    var query by rememberSaveable { mutableStateOf("") }
    var byRecent by rememberSaveable { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    val cases = remember(ver) { store.caseNos() }
    var summaries by remember { mutableStateOf<Map<String, CaseSummary>?>(null) }
    LaunchedEffect(ver, resumeTick, refresh) {
        // 문자 · 통화로 먼저 보여주고, 통화녹음은 다 찾은 뒤에 채운다
        val base = Summaries.base(cases, data, store)
        summaries = Summaries.summarize(base, RecordingIndex.peek())
        summaries = Summaries.summarize(base, RecordingIndex.get(data, store))
    }
    val sums = summaries
    val sorted = if (byRecent && sums != null) cases.sortedByDescending { sums[it]?.lastTime ?: 0L } else cases
    val featured = store.recentKeys()
        .firstOrNull { it.startsWith("c:") && it.removePrefix("c:") in cases }
        ?.removePrefix("c:")
        ?: sorted.firstOrNull()
    val today = LocalDate.now()
    val todayCount = sums?.values?.count { s -> s.lastTime?.let { Fmt.dayKey(it) == today } == true }
    val searching = query.isNotBlank()

    LazyColumn(
        contentPadding = PaddingValues(bottom = 140.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "hero") {
            val hero = @Composable {
                HomeHero(
                    query = query,
                    onQuery = { query = it },
                    caseCount = cases.size,
                    todayCount = todayCount,
                    tall = !searching && featured != null,
                    onRescan = {
                        RecordingIndex.invalidate()
                        refresh++
                    },
                    onSettings = onSettings,
                )
            }
            // 검색칸이 다시 만들어지면 한글 조합이 끊기므로, 검색 중에도 같은 자리에 둔다
            val deck = !searching && featured != null
            Overlap(
                overlap = if (deck) 56.dp else 0.dp,
                top = hero,
                bottom = {
                    if (deck && featured != null) {
                        FeaturedDeck(
                            caseNo = featured,
                            store = store,
                            summary = sums?.get(featured),
                            player = player,
                            onOpen = { onOpenCase(featured) },
                        )
                    } else {
                        Spacer(Modifier.height(0.dp))
                    }
                },
            )
        }

        if (searching) {
            searchResults(query, store, sums, onOpenCase, onOpenPerson)
        } else if (cases.isEmpty()) {
            item(key = "empty") {
                EmptyCard(
                    icon = Ic.link,
                    title = "아직 등록한 사건이 없어요",
                    body = "아래 가운데 [+]를 눌러 통화내역이나 연락처에서\n번호를 고르고 사고번호에 연결해 보세요.",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp),
                )
            }
        } else {
            item(key = "all-head") {
                SectionHeader("전체 사건", cases.size, modifier = Modifier.padding(top = 10.dp)) {
                    HeaderAction(if (byRecent) "최근 연락순" else "사고번호순", icon = Ic.sort) { byRecent = !byRecent }
                }
            }
            items(sorted, key = { "case-$it" }) { caseNo ->
                CaseCard(
                    caseNo = caseNo,
                    store = store,
                    summary = sums?.get(caseNo),
                    loading = sums?.get(caseNo)?.recCount == null,
                    onOpen = { onOpenCase(caseNo) },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun HomeHero(
    query: String,
    onQuery: (String) -> Unit,
    caseCount: Int,
    todayCount: Int?,
    tall: Boolean,
    onRescan: () -> Unit,
    onSettings: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Hero(bottomPadding = if (tall) 84.dp else 22.dp) {
        HeroTopBar(
            left = {
                LogoTile()
                Spacer(Modifier.width(9.dp))
                Text("보상검색기", style = ts(15f, W8, tracking = -0.01f), color = Color.White)
            },
            right = {
                Box {
                    GlassCircle(Ic.more, "더보기") { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("통화녹음 다시 찾기", style = ts(15f, W6)) },
                            leadingIcon = { Icon(Ic.refresh, null, Modifier.size(18.dp)) },
                            onClick = {
                                menu = false
                                onRescan()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("설정", style = ts(15f, W6)) },
                            leadingIcon = { Icon(Ic.gear, null, Modifier.size(18.dp)) },
                            onClick = {
                                menu = false
                                onSettings()
                            },
                        )
                    }
                }
            },
        )
        Text(Fmt.todayHeader(), style = ts(13f, W6), color = Color.White.copy(alpha = 0.62f), modifier = Modifier.padding(top = 10.dp))
        val em = SpanStyle(color = Color(0xFFA3BBFF))
        val headline = buildAnnotatedString {
            when {
                caseCount == 0 -> {
                    append("사고번호와 번호를\n")
                    withStyle(em) { append("연결") }
                    append("해 보세요")
                }
                todayCount == null -> {
                    append("사건 ")
                    withStyle(em) { append("${caseCount}개") }
                    append("의\n연락을 모으는 중이에요")
                }
                todayCount > 0 -> {
                    append("오늘 ")
                    withStyle(em) { append("${todayCount}개 사건") }
                    append("에서\n연락이 있었어요")
                }
                else -> {
                    append("오늘은 아직\n")
                    withStyle(em) { append("새 연락") }
                    append("이 없어요")
                }
            }
        }
        Text(
            headline,
            style = ts(25f, W8, tracking = -0.03f, lineHeight = 1.3f),
            color = Color.White,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
        )
        HeroSearchField(query, onQuery, "사고번호, 번호, 이름, 초성")
    }
}

/** 이어서 보기: 맨 위 카드 + 뒤로 겹친 카드 두 장 */
@Composable
private fun FeaturedDeck(
    caseNo: String,
    store: Store,
    summary: CaseSummary?,
    player: Player,
    onOpen: () -> Unit,
) {
    val c = B.c
    val links = store.linksForCase(caseNo)
    val people = links.map { Who(it.number, store.nameOf(it.number)) }
    Box(
        Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
            .fillMaxWidth(),
    ) {
        Ghost(Modifier.align(Alignment.BottomCenter), dy = 24, scale = 0.86f, alpha = 0.5f)
        Ghost(Modifier.align(Alignment.BottomCenter), dy = 12, scale = 0.93f, alpha = 0.85f)
        BCard(
            level = Depth.LIFT,
            modifier = Modifier
                .fillMaxWidth()
                .press(scale = 0.98f, onClick = onOpen),
        ) {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("이어서 보기", style = ts(11.5f, W8, tracking = 0.08f), color = c.brand)
                    Spacer(Modifier.weight(1f))
                    summary?.lastTime?.let { Text(Fmt.ago(it), style = ts(12.5f, W7, num = true), color = c.ink3) }
                }
                Text(caseNo, style = ts(27f, W8, tracking = -0.02f, num = true), color = c.ink, modifier = Modifier.padding(top = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    AvatarStack(people, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        links.joinToString(" · ") { store.displayName(it.number) },
                        style = ts(14f, W6),
                        color = c.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    Modifier
                        .padding(top = 16.dp, bottom = 14.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(c.line),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val last = summary?.last
                    when (last) {
                        is TimelineItem.Rec -> {
                            val f = last.rec.file
                            val name = store.displayName(last.number)
                            val role = links.firstOrNull { it.number == last.number }?.role
                            PlayButton(player.currentKey == f.cacheKey && player.isPlaying, 40.dp) {
                                player.toggle(f.cacheKey, f.uri, listOfNotNull(name, role).joinToString(" · "), Fmt.ago(last.timeMillis))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(withJosa(name, "과", "와") + " 통화 녹음", style = ts(14f, W7), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    Fmt.ago(last.timeMillis) + if (f.durationMs > 0) " · " + Fmt.durationKo(f.durationMs) else "",
                                    style = ts(12.5f, W4, num = true),
                                    color = c.ink2,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        is TimelineItem.Sms -> {
                            IconTile(Ic.msg, c.brand, c.brandTint, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    store.displayName(last.number) + if (last.sms.incoming) " 문자" else "에게 보낸 문자",
                                    style = ts(14f, W7),
                                    color = c.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    last.sms.body.replace('\n', ' '),
                                    style = ts(12.5f, W4),
                                    color = c.ink2,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        else -> {
                            IconTile(Ic.docSearch, c.ink3, c.chip, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (summary?.recCount == null) "기록을 찾는 중" else "아직 찾은 문자·녹음이 없어요",
                                style = ts(14f, W6),
                                color = c.ink2,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (summary != null) {
                        Spacer(Modifier.width(8.dp))
                        RecPill(summary.recCount)
                        Spacer(Modifier.width(6.dp))
                        MsgPill(summary.smsCount)
                    }
                }
            }
        }
    }
}

@Composable
private fun Ghost(modifier: Modifier, dy: Int, scale: Float, alpha: Float) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .offset(y = dy.dp)
            .graphicsLayer {
                scaleX = scale
                this.alpha = alpha
            }
            .fillMaxWidth()
            .height(60.dp)
            .depth(shape)
            .clip(shape)
            .background(B.c.card),
    )
}

@Composable
fun IconTile(icon: ImageVector, fg: Color, bg: Color, size: androidx.compose.ui.unit.Dp, radius: androidx.compose.ui.unit.Dp = size * 0.33f) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(bg),
    ) { Icon(icon, null, tint = fg, modifier = Modifier.size(size * 0.5f)) }
}

@Composable
fun CaseCard(
    caseNo: String,
    store: Store,
    summary: CaseSummary?,
    loading: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = B.c
    val links = store.linksForCase(caseNo)
    if (links.isEmpty()) return
    val people = links.map { Who(it.number, store.nameOf(it.number)) }
    BCard(
        modifier
            .fillMaxWidth()
            .press(scale = 0.98f, onClick = onOpen),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(caseNo, style = ts(18f, W8, tracking = -0.01f, num = true), color = c.ink, modifier = Modifier.weight(1f))
                AvatarStack(people, 28.dp)
            }
            Text(
                links.joinToString(" · ") { store.displayName(it.number) + " " + it.role },
                style = ts(13.5f, W4),
                color = c.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.chip)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                val last = summary?.last
                val (icon, fg, bg) = when (last) {
                    is TimelineItem.Rec -> Triple(Ic.wave, c.rec, c.recTint)
                    is TimelineItem.Sms -> Triple(Ic.msg, c.brand, c.brandTint)
                    else -> Triple(Ic.clock, c.ink3, c.chip2)
                }
                IconTile(icon, fg, bg, 24.dp, 8.dp)
                Spacer(Modifier.width(8.dp))
                val text = when (last) {
                    is TimelineItem.Rec -> withJosa(store.displayName(last.number), "과", "와") + " 통화 녹음" +
                        if (last.rec.file.durationMs > 0) " · " + Fmt.durationKo(last.rec.file.durationMs) else ""
                    is TimelineItem.Sms -> store.displayName(last.number) + " \"" + last.sms.body.replace('\n', ' ').trim() + "\""
                    else -> if (loading) "기록을 찾는 중" else "아직 찾은 문자·녹음이 없어요"
                }
                Text(
                    text,
                    style = ts(13f, W6),
                    color = if (last !is TimelineItem.Rec && last !is TimelineItem.Sms) c.ink3 else c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                summary?.lastTime?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(Fmt.short(it), style = ts(12f, W7, num = true), color = c.ink3)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.searchResults(
    query: String,
    store: Store,
    sums: Map<String, CaseSummary>?,
    onOpenCase: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
) {
    val cases = store.searchCases(query)
    val people = store.searchPeople(query)
    val digits = PhoneNumbers.normalize(query)
    val unregistered = query.count { it.isDigit() } >= 9 &&
        PhoneNumbers.looksLikeNumber(query) &&
        store.registeredNumbers().none { it == digits }

    if (cases.isNotEmpty()) {
        item(key = "rc-head") { SectionHeader("사건", cases.size, modifier = Modifier.padding(top = 6.dp)) }
        items(cases, key = { "rc-$it" }) { caseNo ->
            CaseCard(
                caseNo = caseNo,
                store = store,
                summary = sums?.get(caseNo),
                loading = sums?.get(caseNo)?.recCount == null,
                onOpen = { onOpenCase(caseNo) },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }
    }
    if (people.isNotEmpty()) {
        item(key = "rp-head") { SectionHeader("사람", people.size, modifier = Modifier.padding(top = 6.dp)) }
        itemsIndexed(people, key = { _, n -> "rp-$n" }) { i, n ->
            CardSegment(
                first = i == 0,
                last = i == people.lastIndex,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                PersonLine(store, n) { onOpenPerson(n) }
            }
        }
    }
    if (unregistered) {
        item(key = "ru-head") { SectionHeader("등록 안 된 번호", modifier = Modifier.padding(top = 6.dp)) }
        item(key = "ru") {
            CardSegment(first = true, last = true, modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .press(scale = 0.98f) { onOpenPerson(digits) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                ) {
                    Avatar(null, digits, 42.dp)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(PhoneNumbers.format(digits), style = ts(15.5f, W7, num = true), color = B.c.ink)
                        Text("이 번호의 문자 · 통화녹음 바로 보기", style = ts(13f, W4), color = B.c.ink2)
                    }
                    Icon(Ic.chevron, null, tint = B.c.ink3, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
    if (cases.isEmpty() && people.isEmpty() && !unregistered) {
        item(key = "r-none") {
            EmptyCard(
                icon = Ic.search,
                title = "찾는 결과가 없어요",
                body = "사고번호 일부(12345), 전화번호 뒷자리,\n이름이나 초성(ㅎㄱㄷ)으로 찾을 수 있어요.",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp),
            )
        }
    }
}

/** 사람 한 줄: 아바타 · 이름 · 번호 · 연결된 사건 */
@Composable
fun PersonLine(store: Store, number: String, onClick: () -> Unit) {
    val c = B.c
    val links = store.linksForNumber(number)
    val name = store.nameOf(number)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .press(scale = 0.98f, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Avatar(name, number, 42.dp)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(name ?: PhoneNumbers.format(number), style = ts(15.5f, W7), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (name != null) PhoneNumbers.format(number) else "저장 안 된 번호",
                style = ts(13f, W4, num = true),
                color = c.ink2,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            links.take(2).forEach { GrayTag("${it.caseNo} ${it.role}") }
            if (links.size > 2) Text("외 ${links.size - 2}건", style = ts(11.5f, W7), color = c.ink3)
        }
    }
}
