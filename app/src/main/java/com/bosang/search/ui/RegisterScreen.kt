package com.bosang.search.ui

import android.provider.CallLog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.bosang.search.core.CaseNumber
import com.bosang.search.core.Hangul
import com.bosang.search.core.PhoneNumbers
import com.bosang.search.data.ContactEntry
import com.bosang.search.data.PhoneData
import com.bosang.search.data.Roles
import com.bosang.search.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private data class CallRow(
    val number: String,
    val name: String?,
    val lastTime: Long,
    val lastType: Int,
    val count: Int,
)

internal enum class Source(val label: String) { CALLS("통화내역"), CONTACTS("연락처") }

private enum class CallFilter { ALL, NO_CASE, MISSED }

/** 목록 한 줄의 종류 */
private sealed interface Entry {
    val key: String

    data object Top : Entry {
        override val key = "top"
    }

    data class Day(val text: String) : Entry {
        override val key = "day-$text"
    }

    data class Letter(val letter: String) : Entry {
        override val key = "letter-$letter"
    }

    data class Call(val row: CallRow, val first: Boolean, val last: Boolean) : Entry {
        override val key = "c-" + row.number
    }

    data class Person(val contact: ContactEntry, val first: Boolean, val last: Boolean) : Entry {
        override val key = "p-" + contact.name + "|" + contact.number
    }

    data class Message(val text: String, val loading: Boolean) : Entry {
        override val key = "msg"
    }
}

private fun isMissed(type: Int) = type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE

@Composable
fun RegisterScreen(
    store: Store,
    data: PhoneData,
    prefillCase: String?,
    prefillNumbers: List<String>,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    StatusBarIcons(lightContent = false)
    val c = B.c
    var rows by remember { mutableStateOf<List<CallRow>?>(null) }
    var contacts by remember { mutableStateOf<List<ContactEntry>?>(null) }
    var source by remember { mutableStateOf(Source.CALLS) }
    var filter by remember { mutableStateOf(CallFilter.ALL) }
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>().apply { addAll(prefillNumbers) } }
    val names = remember { mutableStateMapOf<String, String>() }
    val sourceOf = remember { mutableStateMapOf<String, Source>() }
    var sheet by remember { mutableStateOf(prefillNumbers.isNotEmpty()) }

    LaunchedEffect(Unit) {
        rows = withContext(Dispatchers.IO) {
            data.calls(500)
                .filter { it.number.isNotEmpty() }
                .groupBy { it.number } // 최신순이라 첫 줄이 마지막 통화
                .map { (n, list) -> CallRow(n, list.first().name, list.first().timeMillis, list.first().type, list.size) }
        }
    }
    LaunchedEffect(source) {
        if (source == Source.CONTACTS && contacts == null) {
            contacts = withContext(Dispatchers.IO) { data.contacts() }
        }
    }
    // 고른 번호의 표시 이름: 고를 때 본 이름 → 통화기록 → 저장된 이름 → 연락처
    LaunchedEffect(sheet) {
        if (!sheet) return@LaunchedEffect
        selected.toList().forEach { n ->
            if (!names[n].isNullOrBlank()) return@forEach
            val fromCalls = rows?.firstOrNull { it.number == n }?.name
            names[n] = fromCalls ?: store.nameOf(n) ?: withContext(Dispatchers.IO) { data.contactName(n) } ?: ""
        }
    }

    fun toggle(number: String, name: String?, src: Source) {
        if (number in selected) {
            selected.remove(number)
        } else {
            selected.add(number)
            if (!name.isNullOrBlank()) names[number] = name
            sourceOf[number] = src
        }
    }

    fun closeSheet() {
        if (prefillNumbers.isNotEmpty()) onBack() else sheet = false
    }
    BackHandler(enabled = sheet) { closeSheet() }

    // ---------- 목록 만들기 ----------
    val digits = query.filter { it.isDigit() }
    val linkedNumbers = remember(store.version.intValue) { store.registeredNumbers().toSet() }
    val entries: List<Entry>
    val letterIndex = HashMap<String, Int>()
    run {
        val out = ArrayList<Entry>()
        out.add(Entry.Top)
        if (source == Source.CALLS) {
            val all = rows
            if (all == null) {
                out.add(Entry.Message("통화내역 불러오는 중", true))
            } else {
                val shown = all.filter { r ->
                    (query.isBlank() || (digits.isNotEmpty() && r.number.contains(digits)) || (r.name != null && Hangul.matches(r.name, query))) &&
                        when (filter) {
                            CallFilter.ALL -> true
                            CallFilter.NO_CASE -> r.number !in linkedNumbers
                            CallFilter.MISSED -> isMissed(r.lastType)
                        }
                }
                if (shown.isEmpty()) out.add(Entry.Message(if (all.isEmpty()) "통화내역이 없어요" else "조건에 맞는 번호가 없어요", false))
                shown.groupBy { Fmt.dayKey(it.lastTime) }.forEach { (day, list) ->
                    out.add(Entry.Day(dayTitle(day)))
                    list.forEachIndexed { i, r -> out.add(Entry.Call(r, i == 0, i == list.lastIndex)) }
                }
            }
        } else {
            val all = contacts
            if (all == null) {
                out.add(Entry.Message("연락처 불러오는 중", true))
            } else {
                val shown = all.filter { p ->
                    query.isBlank() || (digits.isNotEmpty() && p.number.contains(digits)) || Hangul.matches(p.name, query)
                }
                if (shown.isEmpty()) out.add(Entry.Message(if (all.isEmpty()) "번호가 저장된 연락처가 없어요" else "찾는 연락처가 없어요", false))
                val groups = shown.groupBy { Hangul.indexOf(it.name) }
                Hangul.INDEX.forEach { letter ->
                    val list = groups[letter] ?: return@forEach
                    letterIndex[letter] = out.size
                    out.add(Entry.Letter(letter))
                    list.forEachIndexed { i, p -> out.add(Entry.Person(p, i == 0, i == list.lastIndex)) }
                }
            }
        }
        entries = out
    }
    val noCaseCount = rows?.count { it.number !in linkedNumbers } ?: 0
    val fromCalls = selected.count { sourceOf[it] != Source.CONTACTS }
    val fromContacts = selected.count { sourceOf[it] == Source.CONTACTS }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showIndex = source == Source.CONTACTS && letterIndex.isNotEmpty()

    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 130.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(entries, key = { it.key }, contentType = { it::class }) { e ->
                val endPad = if (showIndex) 30.dp else 16.dp
                when (e) {
                    Entry.Top -> PickHeader(
                        source = source,
                        onSource = {
                            source = it
                            scope.launch { listState.scrollToItem(0) }
                        },
                        callBadge = fromCalls,
                        contactBadge = fromContacts,
                        query = query,
                        onQuery = { query = it },
                        filter = filter,
                        onFilter = { filter = it },
                        noCaseCount = noCaseCount,
                        onCancel = onBack,
                    )
                    is Entry.Day -> Text(
                        e.text,
                        style = ts(12.5f, W8, tracking = 0.02f),
                        color = c.ink2,
                        modifier = Modifier.padding(start = 22.dp, top = 16.dp, bottom = 8.dp),
                    )
                    is Entry.Letter -> Box(Modifier.padding(start = 20.dp, top = 14.dp, bottom = 8.dp)) {
                        val shape = RoundedCornerShape(9.dp)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .widthIn(min = 26.dp)
                                .height(26.dp)
                                .depth(shape)
                                .clip(shape)
                                .background(c.card)
                                .padding(horizontal = 6.dp),
                        ) { Text(e.letter, style = ts(13f, W8), color = c.ink) }
                    }
                    is Entry.Call -> CardSegment(e.first, e.last, Modifier.padding(start = 16.dp, end = endPad)) {
                        CallLine(e.row, e.row.number in selected, store) {
                            toggle(e.row.number, e.row.name, Source.CALLS)
                        }
                    }
                    is Entry.Person -> CardSegment(e.first, e.last, Modifier.padding(start = 16.dp, end = endPad)) {
                        ContactLine(e.contact, e.contact.number in selected, store) {
                            toggle(e.contact.number, e.contact.name, Source.CONTACTS)
                        }
                    }
                    is Entry.Message -> if (e.loading) {
                        Loading(e.text)
                    } else {
                        Text(
                            e.text,
                            style = ts(14f, W6),
                            color = c.ink2,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 28.dp),
                        )
                    }
                }
            }
        }

        ScrollTopButton(
            listState,
            bottom = if (!sheet && selected.isNotEmpty()) 96.dp else 20.dp,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (showIndex) {
            val current by remember(letterIndex) {
                derivedStateOf {
                    val first = listState.firstVisibleItemIndex
                    letterIndex.entries.filter { it.value <= first }.maxByOrNull { it.value }?.key
                }
            }
            IndexBar(
                letters = Hangul.INDEX,
                present = letterIndex.keys,
                current = current,
                onPick = { letter ->
                    val target = Hangul.INDEX.drop(Hangul.INDEX.indexOf(letter)).firstNotNullOfOrNull { letterIndex[it] }
                        ?: letterIndex.values.maxOrNull()
                    if (target != null) scope.launch { listState.scrollToItem(target) }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
            )
        }

        AnimatedVisibility(
            visible = !sheet && selected.isNotEmpty(),
            enter = slideInVertically(tween(260)) { it } + fadeIn(),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            FloatingBar {
                AvatarStack(selected.map { Who(it, names[it]?.ifBlank { null }) }, 34.dp, ring = c.glass.compositeOver(c.bg))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${selected.size}명 선택", style = ts(15f, W8), color = c.ink)
                    Text(
                        when {
                            fromContacts == 0 -> "통화내역에서 ${fromCalls}명"
                            fromCalls == 0 -> "연락처에서 ${fromContacts}명"
                            else -> "통화내역 $fromCalls · 연락처 $fromContacts"
                        },
                        style = ts(12f, W4, num = true),
                        color = c.ink2,
                        maxLines = 1,
                    )
                }
                GradientButton("연결", icon = Ic.link, height = 50.dp, radius = 16.dp) { sheet = true }
            }
        }

        AnimatedVisibility(visible = sheet, enter = fadeIn(tween(220)), exit = fadeOut(tween(180))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x73080C1A))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { closeSheet() },
                    ),
            )
        }
        AnimatedVisibility(
            visible = sheet,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(200)),
        ) {
            LinkSheet(
                store = store,
                prefillCase = prefillCase,
                selected = selected,
                names = names,
                sourceOf = sourceOf,
                onClose = { closeSheet() },
                onSaved = onSaved,
            )
        }
    }
}

private fun dayTitle(day: LocalDate): String {
    val today = LocalDate.now()
    return when {
        day == today -> "오늘"
        day == today.minusDays(1) -> "어제"
        else -> Fmt.dayLabel(day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
    }
}

@Composable
private fun PickHeader(
    source: Source,
    onSource: (Source) -> Unit,
    callBadge: Int,
    contactBadge: Int,
    query: String,
    onQuery: (String) -> Unit,
    filter: CallFilter,
    onFilter: (CallFilter) -> Unit,
    noCaseCount: Int,
    onCancel: () -> Unit,
) {
    val c = B.c
    Column(
        Modifier
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("번호 고르기", style = ts(30f, W8, tracking = -0.035f), color = c.ink, modifier = Modifier.weight(1f))
            Text(
                "취소",
                style = ts(15f, W7),
                color = c.brand,
                modifier = Modifier
                    .press(scale = 0.94f, onClick = onCancel)
                    .padding(8.dp),
            )
        }
        SegmentedControl(
            options = listOf(
                Seg("통화내역", Ic.clock, callBadge),
                Seg("연락처", Ic.book, contactBadge),
            ),
            selected = source.ordinal,
            onSelect = { onSource(Source.entries[it]) },
            modifier = Modifier.padding(top = 12.dp),
        )
        LightSearchField(
            value = query,
            onValue = onQuery,
            placeholder = if (source == Source.CALLS) "이름, 번호" else "이름, 번호, 초성",
            hint = if (source == Source.CONTACTS) "예: ㅎㄱㄷ" else null,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (source == Source.CALLS) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .padding(top = 12.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                FilterPill("전체", filter == CallFilter.ALL) { onFilter(CallFilter.ALL) }
                FilterPill("사건 없는 번호 $noCaseCount", filter == CallFilter.NO_CASE) { onFilter(CallFilter.NO_CASE) }
                FilterPill("부재중", filter == CallFilter.MISSED) { onFilter(CallFilter.MISSED) }
            }
        }
    }
}

/** 사건 연결 꼬리표: 하나면 사고번호, 여럿이면 "사건 N" */
@Composable
private fun LinkedTag(store: Store, number: String) {
    val links = store.linksForNumber(number)
    when {
        links.size == 1 -> GrayTag(links.first().caseNo)
        links.size > 1 -> GrayTag("사건 ${links.size}")
    }
}

@Composable
private fun CallLine(r: CallRow, checked: Boolean, store: Store, onToggle: () -> Unit) {
    val c = B.c
    val missed = isMissed(r.lastType)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) c.brandTint else Color.Transparent)
            .press(scale = 0.985f, onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Avatar(r.name, r.number, 46.dp, checked = checked)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                r.name ?: PhoneNumbers.format(r.number),
                style = ts(15.5f, W7, num = r.name == null),
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                if (missed) {
                    Text(if (r.lastType == CallLog.Calls.REJECTED_TYPE) "거절" else "부재중", style = ts(13f, W7), color = c.rec)
                    if (r.count > 1) Text(" · ${r.count}회", style = ts(13f, W4, num = true), color = c.ink2)
                } else {
                    val incoming = r.lastType == CallLog.Calls.INCOMING_TYPE
                    Icon(if (incoming) Ic.incoming else Ic.outgoing, null, tint = c.ink2, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    val parts = listOfNotNull(
                        if (incoming) "받은 전화" else "건 전화",
                        if (r.count > 1) "${r.count}회" else null,
                        if (r.name == null) "저장 안 됨" else null,
                    )
                    Text(parts.joinToString(" · "), style = ts(13f, W4, num = true), color = c.ink2, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(Fmt.time(r.lastTime), style = ts(12.5f, W6, num = true), color = c.ink3)
            LinkedTag(store, r.number)
        }
    }
}

@Composable
private fun ContactLine(p: ContactEntry, checked: Boolean, store: Store, onToggle: () -> Unit) {
    val c = B.c
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) c.brandTint else Color.Transparent)
            .press(scale = 0.985f, onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Avatar(p.name, p.number, 42.dp, checked = checked)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = ts(15.5f, W7), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                PhoneNumbers.format(p.number) + (p.label?.let { " · $it" } ?: ""),
                style = ts(13f, W4, num = true),
                color = c.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        LinkedTag(store, p.number)
    }
}

/** 오른쪽 ㄱㄴㄷ 색인. 누르거나 끌어서 이동 */
@Composable
private fun IndexBar(
    letters: List<String>,
    present: Set<String>,
    current: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = B.c
    var last by remember { mutableStateOf<String?>(null) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .width(22.dp)
            .pointerInput(letters) {
                fun pick(y: Float) {
                    val i = (y / size.height * letters.size).toInt().coerceIn(0, letters.lastIndex)
                    val l = letters[i]
                    if (l != last) {
                        last = l
                        onPick(l)
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    pick(down.position.y)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        ch.consume()
                        pick(ch.position.y)
                    }
                    last = null
                }
            }
            .padding(vertical = 4.dp),
    ) {
        letters.forEach { l ->
            val on = l == current
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(18.dp, 17.dp)
                    .then(if (on) Modifier.glow(RoundedCornerShape(6.dp), 6.dp) else Modifier)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (on) c.brand else Color.Transparent),
            ) {
                Text(
                    l,
                    style = ts(10.5f, W8, tracking = 0f),
                    color = when {
                        on -> Color.White
                        l in present -> c.ink2
                        else -> c.ink3.copy(alpha = 0.5f)
                    },
                )
            }
        }
    }
}

// ───────────────────────── 사건에 연결 (아래에서 올라오는 시트) ─────────────────────────

/** 받침 따라: 피보험자로 / 병원으로 / 지인으로 */
private fun withRo(word: String): String {
    val last = word.lastOrNull() ?: return word
    if (last !in '가'..'힣') return word + "로"
    val jong = (last - '가') % 28
    return word + if (jong == 0 || jong == 8) "로" else "으로"
}

@Composable
private fun LinkSheet(
    store: Store,
    prefillCase: String?,
    selected: List<String>,
    names: Map<String, String>,
    sourceOf: Map<String, Source>,
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val c = B.c
    val thisYear = LocalDate.now().year
    var year by remember { mutableStateOf(prefillCase?.substringBefore('-') ?: CaseNumber.year2(thisYear)) }
    var serial by remember { mutableStateOf(prefillCase?.substringAfter('-') ?: "") }
    val roles = remember { mutableStateMapOf<String, String>() }
    val caseNo = CaseNumber.of(year, serial)
    val valid = CaseNumber.isValid(caseNo)
    val existing = remember(caseNo, store.version.intValue) { if (valid) store.linksForCase(caseNo) else emptyList() }

    // 이미 있는 사건이면 정해둔 관계를 채워줌
    LaunchedEffect(caseNo) {
        existing.forEach { link ->
            if (link.number in selected && roles[link.number].isNullOrEmpty()) roles[link.number] = link.role
        }
    }
    val suggestions = if (serial.isNotEmpty() && !valid) {
        store.caseNos().filter { CaseNumber.digits(it).contains(serial) }.take(6)
    } else emptyList()
    val allRolesReady = selected.isNotEmpty() && selected.all { roleReady(roles[it].orEmpty()) }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (prefillCase == null) {
            delay(350)
            runCatching { focus.requestFocus() }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 18.dp)
            .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .background(c.bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .imePadding(),
    ) {
        Box(
            Modifier
                .padding(top = 8.dp)
                .align(Alignment.CenterHorizontally)
                .size(40.dp, 5.dp)
                .clip(CircleShape)
                .background(c.chip2),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
        ) {
            Text("사건에 연결", style = ts(22f, W8, tracking = -0.03f), color = c.ink, modifier = Modifier.weight(1f))
            CloseCircle(onClick = onClose)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            SheetLabel("사고번호") {
                YearChip(year, thisYear) { year = it }
            }
            OtpField(
                year = year,
                serial = serial,
                onSerial = { serial = it.filter { ch -> ch.isDigit() }.take(8) },
                focus = focus,
            )
            if (suggestions.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    Text("이미 있는 사건", style = ts(12.5f, W7), color = c.ink2)
                    suggestions.forEach { s ->
                        Text(
                            s,
                            style = ts(13f, W8, num = true),
                            color = c.brand,
                            modifier = Modifier
                                .press(scale = 0.95f) {
                                    year = s.substringBefore('-')
                                    serial = s.substringAfter('-')
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.brandTint)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            if (existing.isNotEmpty()) {
                ExistNotice(store, existing.map { it.number to it.role }, selected)
            }

            SheetLabel("관계")
            BCard(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
            ) {
                selected.forEachIndexed { i, n ->
                    if (i > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(c.line),
                        )
                    }
                    RoleRow(
                        number = n,
                        name = names[n]?.ifBlank { null },
                        source = sourceOf[n],
                        role = roles[n].orEmpty(),
                        onRole = { roles[n] = it },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        GradientButton(
            text = when {
                !valid -> "사고번호 8자리를 입력해 주세요"
                !allRolesReady -> "관계를 골라 주세요"
                else -> "연결하기"
            },
            icon = if (valid && allRolesReady) Ic.link else null,
            enabled = valid && allRolesReady,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        ) {
            store.upsert(caseNo, selected.map { n -> Triple(n, roles[n].orEmpty().trim(), names[n]?.ifBlank { null }) })
            store.touchRecent("c:$caseNo")
            onSaved(caseNo)
        }
    }
}

@Composable
internal fun SheetLabel(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 9.dp),
    ) {
        Text(text, style = ts(12.5f, W8, tracking = 0.04f), color = B.c.ink2, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
internal fun YearChip(year2: String, thisYear: Int, onYear: (String) -> Unit) {
    val c = B.c
    var open by remember { mutableStateOf(false) }
    val full = 2000 + (year2.toIntOrNull() ?: (thisYear % 100))
    Box {
        val shape = RoundedCornerShape(10.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .press(scale = 0.95f) { open = true }
                .depth(shape)
                .clip(shape)
                .background(c.card)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text("${full}년", style = ts(13f, W8, tracking = 0f, num = true), color = c.ink)
            Spacer(Modifier.width(4.dp))
            Icon(Ic.down, null, tint = c.ink, modifier = Modifier.size(13.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (thisYear downTo thisYear - 5).forEach { y ->
                DropdownMenuItem(
                    text = { Text("${y}년", style = ts(15f, if (y == full) W8 else W6, num = true)) },
                    onClick = {
                        onYear(CaseNumber.year2(y))
                        open = false
                    },
                )
            }
        }
    }
}

/** 칸마다 한 자리씩 보이는 사고번호 입력 */
@Composable
internal fun OtpField(year: String, serial: String, onSerial: (String) -> Unit, focus: FocusRequester) {
    val c = B.c
    var focused by remember { mutableStateOf(false) }
    BCard(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
    ) {
        BasicTextField(
            value = serial,
            onValueChange = onSerial,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            cursorBrush = SolidColor(Color.Transparent),
            textStyle = ts(1f).copy(color = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(Modifier.size(0.dp)) { inner() }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                ) {
                    Text("$year –", style = ts(15f, W8, num = true), color = c.ink3, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    for (i in 0 until 8) {
                        if (i == 4) Spacer(Modifier.width(8.dp))
                        val ch = serial.getOrNull(i)
                        val cur = focused && (i == serial.length || (serial.length == 8 && i == 7))
                        val shape = RoundedCornerShape(11.dp)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.5.dp)
                                .height(48.dp)
                                .then(if (cur) Modifier.glow(shape, 8.dp) else Modifier)
                                .clip(shape)
                                .background(if (cur) c.card else c.chip)
                                .then(if (cur) Modifier.border(2.dp, c.brand, shape) else Modifier),
                        ) {
                            Text(ch?.toString() ?: "", style = ts(24f, W8, num = true), color = c.ink)
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun ExistNotice(store: Store, existing: List<Pair<String, String>>, selected: List<String>) {
    val c = B.c
    val overlap = existing.filter { it.first in selected }
    val newCount = selected.count { n -> existing.none { it.first == n } }
    val body = if (overlap.isNotEmpty()) {
        val (n, role) = overlap.first()
        val who = withJosa(store.displayName(n), "은", "는")
        val more = if (overlap.size > 1) " 외 ${overlap.size - 1}명" else ""
        "$who$more 이미 ${withRo(role)} 연결돼 있어요. " +
            if (newCount > 0) "새로 고른 ${newCount}명만 추가됩니다." else "관계만 바꿀 수 있어요."
    } else {
        "지금 ${existing.size}명이 연결돼 있어요. 고른 ${newCount}명이 함께 연결됩니다."
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.brandTint)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        AvatarStack(
            existing.map { Who(it.first, store.nameOf(it.first)) },
            28.dp,
            ring = c.brandTint.compositeOver(c.bg),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("이미 있는 사건이에요", style = ts(13.5f, W8), color = c.brand)
            Text(body, style = ts(13f, W4, lineHeight = 1.45f), color = c.ink)
        }
    }
}

@Composable
internal fun RoleRow(number: String, name: String?, source: Source?, role: String, onRole: (String) -> Unit) {
    val c = B.c
    val customMode = role == Roles.CUSTOM || (role.isNotEmpty() && role !in Roles.all)
    Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 15.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name, number, 34.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name ?: PhoneNumbers.format(number),
                    style = ts(15.5f, W8, num = name == null),
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (name != null) PhoneNumbers.format(number) else "저장 안 됨",
                    style = ts(12.5f, W4, num = true),
                    color = c.ink2,
                )
            }
            if (source != null) {
                Text(
                    source.label,
                    style = ts(11f, W8),
                    color = c.ink3,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.chip)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            (Roles.all + Roles.CUSTOM).forEach { r ->
                val on = if (r == Roles.CUSTOM) customMode else role == r
                RoleChip(r, on) { onRole(r) }
            }
        }
        if (customMode) {
            Spacer(Modifier.height(10.dp))
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.chip)
                    .padding(horizontal = 14.dp),
            ) {
                val text = if (role == Roles.CUSTOM) "" else role
                if (text.isEmpty()) Text("예: 동승자, 담당 설계사", style = ts(14.5f), color = c.ink3)
                BasicTextField(
                    value = text,
                    onValueChange = { onRole(it.take(12).ifBlank { Roles.CUSTOM }) },
                    singleLine = true,
                    textStyle = ts(14.5f, W7).copy(color = c.ink),
                    cursorBrush = SolidColor(c.brand),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun RoleChip(text: String, on: Boolean, onClick: () -> Unit) {
    val c = B.c
    val shape = RoundedCornerShape(12.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .press(scale = 0.94f, onClick = onClick)
            .height(36.dp)
            .then(if (on) Modifier.glow(shape, 8.dp) else Modifier)
            .clip(shape)
            .then(
                if (on) Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(c.brand2, c.brand)))
                else Modifier.background(c.chip),
            )
            .padding(horizontal = 14.dp),
    ) {
        Text(text, style = ts(14f, W7), color = if (on) Color.White else c.ink2, maxLines = 1)
    }
}

/** 관계가 실제로 정해졌는지 ("직접 입력"만 눌러두고 비워둔 상태는 아님) */
fun roleReady(role: String): Boolean = role.isNotBlank() && role != Roles.CUSTOM
