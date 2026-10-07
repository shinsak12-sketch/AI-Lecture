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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.bosang.search.core.CallEntry
import com.bosang.search.core.PhoneNumbers
import androidx.compose.foundation.horizontalScroll
import com.bosang.search.data.CaseSummary
import com.bosang.search.data.DirEntry
import com.bosang.search.core.Hangul
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.bosang.search.data.PhoneData
import com.bosang.search.data.Photos
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
    onAppt: (String) -> Unit,
    onCamera: () -> Unit,
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
    // 위젯 검색칸으로 들어오면 바로 입력
    val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(ExternalNav.focusSearch.value) {
        if (ExternalNav.focusSearch.value) {
            ExternalNav.focusSearch.value = false
            kotlinx.coroutines.delay(350)
            runCatching { searchFocus.requestFocus() }
            keyboard?.show()
        }
    }
    // 사건이 없는 사람도 찾을 수 있게: 연락처 + 통화내역 (검색을 시작할 때 읽음)
    var directory by remember { mutableStateOf<List<DirEntry>?>(null) }
    LaunchedEffect(searching, resumeTick) {
        if (searching) directory = withContext(Dispatchers.IO) { data.directory() }
    }

    // 첫 화면: 사건별 목록 / 통화기록 (고른 것을 기억)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val homePrefs = remember { ctx.getSharedPreferences("home", android.content.Context.MODE_PRIVATE) }
    var mode by rememberSaveable { mutableStateOf(if (homePrefs.getString("mode", "case") == "calls") HomeMode.CALLS else HomeMode.CASES) }
    fun switchMode(m: HomeMode) {
        mode = m
        homePrefs.edit().putString("mode", if (m == HomeMode.CALLS) "calls" else "case").apply()
    }
    var callFilter by rememberSaveable { mutableStateOf(CallLogFilter.ALL) }
    var callLog by remember { mutableStateOf<List<CallEntry>?>(null) }
    LaunchedEffect(mode, resumeTick) {
        if (mode == HomeMode.CALLS) callLog = withContext(Dispatchers.IO) { data.calls(400).filter { it.number.isNotEmpty() } }
    }

    // 사고번호 없이 찍은 사진 → 사고번호 넣기
    var assignAsk by remember { mutableStateOf(false) }
    if (assignAsk) {
        CaseNoInputDialog(store, onDismiss = { assignAsk = false }) { cn ->
            assignAsk = false
            assignUnassigned(ctx, store, cn)
            UiMemory.caseKind[cn] = Kind.PHOTO
            onOpenCase(cn)
        }
    }

    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
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
                    tall = !searching && mode == HomeMode.CASES && featured != null,
                    onRescan = {
                        RecordingIndex.invalidate()
                        refresh++
                    },
                    onSettings = onSettings,
                    onCamera = onCamera,
                    focus = searchFocus,
                )
            }
            // 검색칸이 다시 만들어지면 한글 조합이 끊기므로, 검색 중에도 같은 자리에 둔다
            val deck = !searching && mode == HomeMode.CASES && featured != null
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

        if (!searching) {
            item(key = "mode") {
                SegmentedControl(
                    options = listOf(Seg("사건별", Ic.folder), Seg("통화기록", Ic.clock)),
                    selected = mode.ordinal,
                    onSelect = { switchMode(HomeMode.entries[it]) },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = if (mode == HomeMode.CASES && featured != null) 4.dp else 16.dp),
                )
            }
            unassignedRow(store, onAssign = { assignAsk = true })
        }
        if (searching) {
            searchResults(query, store, sums, directory, onOpenCase, onOpenPerson)
        } else if (mode == HomeMode.CALLS) {
            callLogList(callLog, callFilter, { callFilter = it }, store, onOpenPerson)
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
            homePlanSection(store, onAppt = { onAppt(it.id) }, onCase = onOpenCase)
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
    ScrollTopButton(listState, bottom = if (player.currentKey != null) 190.dp else 108.dp, modifier = Modifier.align(Alignment.BottomCenter))
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
    onCamera: () -> Unit,
    focus: androidx.compose.ui.focus.FocusRequester,
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
                GlassCircle(Ic.camera, "보상 카메라", onClick = onCamera)
                Spacer(Modifier.width(8.dp))
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
        HeroSearchField(query, onQuery, "사고번호, 번호, 이름, 초성", focus = focus)
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
    if (links.isEmpty() && !store.caseExists(caseNo)) return
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
                if (links.isEmpty()) "연결된 사람 없음 · 사진 ${store.photosForCase(caseNo).size}장"
                else links.joinToString(" · ") { store.displayName(it.number) + " " + it.role },
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
    directory: List<DirEntry>?,
    onOpenCase: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
) {
    val cases = store.searchCases(query)
    val people = store.searchPeople(query)
    val registered = store.registeredNumbers().toSet()
    val typed = query.filter { it.isDigit() }
    val text = query.trim()
    // 사건이 없는 번호: 연락처 · 통화내역에서 번호 일부, 이름, 초성으로
    val others = directory.orEmpty().filter { e ->
        e.number !in registered &&
            ((typed.length >= 3 && e.number.contains(typed)) ||
                (text.isNotEmpty() && e.name?.let { Hangul.matches(it, text) } == true))
    }.take(50)
    val digits = PhoneNumbers.normalize(query)
    val unregistered = query.count { it.isDigit() } >= 9 &&
        PhoneNumbers.looksLikeNumber(query) &&
        digits !in registered &&
        others.none { it.number == digits }

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
    if (others.isNotEmpty()) {
        item(key = "ro-head") { SectionHeader("연락처 · 통화내역", others.size, modifier = Modifier.padding(top = 6.dp)) }
        itemsIndexed(others, key = { _, e -> "ro-${e.number}" }) { i, e ->
            CardSegment(
                first = i == 0,
                last = i == others.lastIndex,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                DirLine(e) { onOpenPerson(e.number) }
            }
        }
    }
    if (directory == null) {
        item(key = "ro-loading") { Loading("연락처 · 통화내역에서 찾는 중") }
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
    if (cases.isEmpty() && people.isEmpty() && others.isEmpty() && !unregistered && directory != null) {
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

private enum class HomeMode { CASES, CALLS }

private enum class CallLogFilter(val label: String) { ALL("전체"), LINKED("사건 있는 번호"), NO_CASE("사건 없는 번호"), MISSED("부재중") }

private fun isMissedCall(type: Int) =
    type == android.provider.CallLog.Calls.MISSED_TYPE || type == android.provider.CallLog.Calls.REJECTED_TYPE

/** 같은 날 같은 번호와 연달아 한 통화는 한 줄로 (폰 기본 통화기록처럼) */
private class CallGroup(val last: CallEntry, val count: Int)

private fun groupCalls(calls: List<CallEntry>): List<CallGroup> {
    val out = ArrayList<CallGroup>()
    var head: CallEntry? = null
    var n = 0
    for (c in calls) {
        val h = head
        if (h != null && h.number == c.number && Fmt.dayKey(h.timeMillis) == Fmt.dayKey(c.timeMillis) &&
            isMissedCall(h.type) == isMissedCall(c.type)
        ) {
            n++
        } else {
            if (h != null) out.add(CallGroup(h, n))
            head = c
            n = 1
        }
    }
    head?.let { out.add(CallGroup(it, n)) }
    return out
}

/** 홈 통화기록: 날짜별로 묶고, 번호를 누르면 그 번호의 기록 화면 */
private fun androidx.compose.foundation.lazy.LazyListScope.callLogList(
    calls: List<CallEntry>?,
    filter: CallLogFilter,
    onFilter: (CallLogFilter) -> Unit,
    store: Store,
    onOpenPerson: (String) -> Unit,
) {
    item(key = "cl-filter") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        ) {
            CallLogFilter.entries.forEach { f -> FilterPill(f.label, f == filter) { onFilter(f) } }
        }
    }
    if (calls == null) {
        item(key = "cl-loading") { Loading("통화기록을 불러오는 중") }
        return
    }
    val shown = calls.filter { c ->
        when (filter) {
            CallLogFilter.ALL -> true
            CallLogFilter.LINKED -> store.linksForNumber(c.number).isNotEmpty()
            CallLogFilter.NO_CASE -> store.linksForNumber(c.number).isEmpty()
            CallLogFilter.MISSED -> isMissedCall(c.type)
        }
    }
    if (shown.isEmpty()) {
        item(key = "cl-empty") {
            EmptyCard(
                icon = Ic.clock,
                title = if (filter == CallLogFilter.ALL) "통화기록이 없어요" else "해당하는 통화가 없어요",
                body = if (filter == CallLogFilter.ALL) "통화기록 권한이 꺼져 있으면 설정에서 켜 주세요." else "다른 분류를 골라 보세요.",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp),
            )
        }
        return
    }
    groupCalls(shown).groupBy { Fmt.dayKey(it.last.timeMillis) }.forEach { (day, groups) ->
        item(key = "cl-d-$day") {
            Text(
                Fmt.dayLabel(groups.first().last.timeMillis),
                style = ts(13f, W7),
                color = B.c.ink2,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 8.dp),
            )
        }
        itemsIndexed(groups, key = { _, g -> "cl-${g.last.number}-${g.last.timeMillis}" }) { i, g ->
            CardSegment(first = i == 0, last = i == groups.lastIndex, modifier = Modifier.padding(horizontal = 16.dp)) {
                CallLogLine(store, g) { onOpenPerson(g.last.number) }
            }
        }
    }
}

@Composable
private fun CallLogLine(store: Store, g: CallGroup, onClick: () -> Unit) {
    val c = B.c
    val call = g.last
    val name = store.nameOf(call.number) ?: call.name?.takeIf { it.isNotBlank() }
    val links = store.linksForNumber(call.number)
    val missed = isMissedCall(call.type)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .press(scale = 0.98f, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Avatar(name, call.number, 42.dp)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name ?: PhoneNumbers.format(call.number),
                    style = ts(15.5f, W7, num = name == null),
                    color = if (missed) c.rec else c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (g.count > 1) {
                    Text(" (${g.count})", style = ts(14f, W6, num = true), color = c.ink3)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                if (missed) {
                    Icon(Ic.missed, null, tint = c.rec, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (call.type == android.provider.CallLog.Calls.REJECTED_TYPE) "거절" else "부재중", style = ts(13f, W6), color = c.rec)
                } else {
                    val incoming = call.type == android.provider.CallLog.Calls.INCOMING_TYPE
                    Icon(if (incoming) Ic.incoming else Ic.outgoing, null, tint = c.ink2, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        listOfNotNull(
                            if (incoming) "받은 전화" else "건 전화",
                            call.durationSec.takeIf { it > 0 }?.let { Fmt.durationKo(it * 1000) },
                        ).joinToString(" · "),
                        style = ts(13f, W4, num = true),
                        color = c.ink2,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(Fmt.time(call.timeMillis), style = ts(12.5f, W6, num = true), color = c.ink3)
            when {
                links.size == 1 -> GrayTag("${links.first().caseNo} ${links.first().role}")
                links.size > 1 -> GrayTag("사건 ${links.size}")
            }
        }
    }
}

/** 사건이 없는 번호 한 줄 */
@Composable
private fun DirLine(e: DirEntry, onClick: () -> Unit) {
    val c = B.c
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .press(scale = 0.98f, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Avatar(e.name, e.number, 42.dp)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.name ?: PhoneNumbers.format(e.number),
                style = ts(15.5f, W7, num = e.name == null),
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (e.name != null) PhoneNumbers.format(e.number) + (e.label?.let { " · $it" } ?: "") else "저장 안 된 번호",
                style = ts(13f, W4, num = true),
                color = c.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            GrayTag("사건 없음")
            e.lastCall?.let { Text("통화 " + Fmt.short(it), style = ts(11.5f, W7, num = true), color = c.ink3) }
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

/** 사고번호 없이 찍은 사진이 있으면 맨 위에 */
private fun androidx.compose.foundation.lazy.LazyListScope.unassignedRow(store: Store, onAssign: () -> Unit) {
    val list = store.unassignedPhotos()
    if (list.isEmpty()) return
    item(key = "unassigned") {
        val c = B.c
        BCard(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth().press(scale = 0.98f, onClick = onAssign),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                Thumb(Photos.shown(list.first()), Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)), px = 200)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("사고번호 없는 사진 ${list.size}장", style = ts(15f, W8), color = c.ink)
                    Text("눌러서 사고번호를 넣으면 사건이 만들어져요", style = ts(12.5f, W4), color = c.ink2)
                }
                SmallTag("넣기", c.brand, c.brandTint)
            }
        }
    }
}
