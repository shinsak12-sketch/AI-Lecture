@file:OptIn(ExperimentalLayoutApi::class)

package com.bosang.search.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bosang.search.core.Hangul
import com.bosang.search.core.Money
import com.bosang.search.core.PhoneNumbers
import com.bosang.search.data.DirEntry
import com.bosang.search.data.Issue
import com.bosang.search.data.IssueKind
import com.bosang.search.data.IssueSource
import com.bosang.search.data.PhoneData
import com.bosang.search.data.Photos
import com.bosang.search.data.Player
import com.bosang.search.data.RecordingIndex
import com.bosang.search.data.Roles
import com.bosang.search.data.Store
import com.bosang.search.data.TimelineItem
import com.bosang.search.data.recordKey
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.compositeOver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ───────────────────────── 구분별 색 · 아이콘 ─────────────────────────

fun kindIcon(k: IssueKind): ImageVector = when (k) {
    IssueKind.FAULT -> Ic.scale
    IssueKind.AMOUNT -> Ic.won
    IssueKind.DAMAGE -> Ic.car
    IssueKind.STATEMENT -> Ic.quote
    IssueKind.SITE -> Ic.pin
    IssueKind.ETC -> Ic.note
}

@Composable
fun kindColors(k: IssueKind): Pair<Color, Color> {
    val c = B.c
    return when (k) {
        IssueKind.FAULT -> c.brand to c.brandTint
        IssueKind.AMOUNT -> c.ok to c.okTint
        IssueKind.DAMAGE -> c.rec to c.recTint
        IssueKind.STATEMENT -> Color(0xFF7B5CF0) to Color(0x1F7B5CF0)
        IssueKind.SITE -> Color(0xFF0FA3B1) to Color(0x210FA3B1)
        IssueKind.ETC -> c.ink2 to c.chip
    }
}

@Composable
fun KindTag(k: IssueKind, suffix: String = "") {
    val (fg, bg) = kindColors(k)
    SmallTag(k.label + suffix, fg, bg, kindIcon(k))
}

/** 기록 출처 한 줄: 오늘 14:32 녹음 · 2:14 */
fun sourceLabel(s: IssueSource): String {
    val day = Fmt.dayLabel(s.timeMillis).substringBefore(" ·")
    val what = when (s.type) {
        "rec" -> "녹음"
        "sms" -> "문자"
        else -> "통화"
    }
    val at = s.offsetMs?.let { " · " + Fmt.duration(it) } ?: ""
    return "$day ${Fmt.time(s.timeMillis)} $what$at"
}

fun sourceIcon(s: IssueSource): ImageVector = when (s.type) {
    "rec" -> Ic.wave
    "sms" -> Ic.msg
    else -> Ic.phone
}

// ───────────────────────── 구분 고르기 ─────────────────────────

@Composable
fun KindPickerDialog(title: String, subtitle: String?, onPick: (IssueKind) -> Unit, onDismiss: () -> Unit) {
    val c = B.c
    Dialog(onDismissRequest = onDismiss) {
        BCard(radius = 26.dp, level = Depth.FLOAT, color = c.bg, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text(title, style = ts(19f, W8), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = ts(13f, W6, num = true), color = c.ink2, modifier = Modifier.padding(top = 3.dp))
                Text("특이사항 구분", style = ts(12.5f, W8, tracking = 0.03f), color = c.ink2, modifier = Modifier.padding(top = 16.dp, bottom = 9.dp))
                IssueKind.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        row.forEach { k ->
                            val (fg, bg) = kindColors(k)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .press(scale = 0.95f) { onPick(k) }
                                    .height(52.dp)
                                    .depth(RoundedCornerShape(15.dp))
                                    .clip(RoundedCornerShape(15.dp))
                                    .background(c.card)
                                    .padding(horizontal = 9.dp),
                            ) {
                                IconTile(kindIcon(k), fg, bg, 30.dp, 10.dp)
                                Spacer(Modifier.width(7.dp))
                                Text(if (k == IssueKind.DAMAGE) "파손" else k.label, style = ts(14f, W8), color = c.ink, maxLines = 1)
                            }
                        }
                    }
                }
                SoftButton("닫기", modifier = Modifier.fillMaxWidth().padding(top = 4.dp), onClick = onDismiss)
            }
        }
    }
}

// ───────────────────────── 사건 맨 위 요약 · 기록 밑에 붙은 것 ─────────────────────────

fun LazyListScope.issuesSection(
    issues: List<Issue>,
    nameOf: (String) -> String,
    onOpen: (Issue) -> Unit,
    onAdd: (() -> Unit)?,
    emptyHint: String = "통화·녹음·문자 카드의 ⋮ 에서 그 기록에 붙여 남길 수 있어요",
) {
    item(key = "iss-head") {
        SectionHeader("특이사항", issues.size.takeIf { it > 0 }, modifier = Modifier.padding(top = 4.dp)) {
            if (onAdd != null) HeaderAction("추가", icon = Ic.plusThin, onClick = onAdd)
        }
    }
    if (issues.isEmpty()) {
        item(key = "iss-empty") {
            val c = B.c
            BCard(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .then(if (onAdd != null) Modifier.press(scale = 0.98f, onClick = onAdd) else Modifier),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
                    IconTile(Ic.pen, c.brand, c.brandTint, 34.dp, 11.dp)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("아직 특이사항이 없어요", style = ts(14.5f, W8), color = c.ink)
                        Text(emptyHint, style = ts(12.5f, W4), color = c.ink2)
                    }
                }
            }
        }
    } else {
        items(issues, key = { "iss-" + it.id }) { iss ->
            IssueCard(iss, nameOf, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) { onOpen(iss) }
        }
    }
}

/** 특이사항 카드: 구분 색을 연하게 깔고 왼쪽에 색 띠 → 기록 카드와 한눈에 구분 */
@Composable
private fun Modifier.issueSurface(iss: Issue, radius: Dp): Modifier {
    val c = B.c
    val (fg, bg) = kindColors(iss.kind)
    val shape = RoundedCornerShape(radius)
    return this
        .depth(shape)
        .clip(shape)
        .background(bg.compositeOver(c.card))
        .drawBehind {
            drawRect(fg, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height))
        }
}

@Composable
private fun IssueCard(iss: Issue, nameOf: (String) -> String, modifier: Modifier, onClick: () -> Unit) {
    val c = B.c
    val (fg, _) = kindColors(iss.kind)
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            .press(scale = 0.98f, onClick = onClick)
            .issueSurface(iss, 18.dp)
            .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(11.dp)).background(c.card),
        ) { Icon(kindIcon(iss.kind), null, tint = fg, modifier = Modifier.size(17.dp)) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(iss.kind.label, style = ts(11.5f, W8), color = fg)
                Spacer(Modifier.width(6.dp))
                val src = iss.source
                Text(
                    src?.let { sourceLabel(it) } ?: ("직접 작성 · " + Fmt.short(iss.createdAt)),
                    style = ts(11.5f, W7, num = true),
                    color = c.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                iss.summary(nameOf),
                style = ts(14.5f, W8, num = true),
                color = c.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
            iss.firstLine()?.takeIf { it != iss.summary(nameOf) }?.let {
                Text(it, style = ts(12.5f, W4), color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** 타임라인 카드 바로 밑에 달리는 특이사항 */
@Composable
fun AttachedIssue(iss: Issue, nameOf: (String) -> String, onOpen: () -> Unit, onPlayAt: ((Long) -> Unit)?) {
    val c = B.c
    val (fg, _) = kindColors(iss.kind)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = 14.dp, top = 5.dp)
            .fillMaxWidth()
            .press(scale = 0.98f, onClick = onOpen)
            .issueSurface(iss, 16.dp)
            .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(c.card),
        ) { Icon(kindIcon(iss.kind), null, tint = fg, modifier = Modifier.size(14.dp)) }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                iss.kind.label + " · " + iss.summary(nameOf),
                style = ts(13.5f, W8, num = true),
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            iss.firstLine()?.let {
                Text(it, style = ts(12f, W6), color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val off = iss.source?.offsetMs
        if (off != null && onPlayAt != null) {
            Spacer(Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .press(scale = 0.92f) { onPlayAt(off) }
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.card)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(Ic.play, null, tint = c.rec, modifier = Modifier.size(10.dp))
                Spacer(Modifier.width(3.dp))
                Text(Fmt.duration(off), style = ts(12f, W8, num = true), color = c.rec)
            }
        }
    }
}

/** 기록 하나에 붙은 특이사항 (통화는 그 통화의 녹음에 붙은 것까지) */
fun attachedIssues(item: TimelineItem, index: Map<String, List<Issue>>): List<Issue> = when (item) {
    is TimelineItem.Call -> index[item.recordKey()].orEmpty() +
        (item.rec?.let { index[IssueSource.recordKey("rec", it.file.cacheKey)] }.orEmpty())
    else -> index[item.recordKey()].orEmpty()
}

fun issueIndex(issues: List<Issue>): Map<String, List<Issue>> =
    issues.mapNotNull { i -> i.source?.let { it.recordKey to i } }.groupBy({ it.first }, { it.second })

// ───────────────────────── 작성 화면 ─────────────────────────

private object Thousands : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val out = raw.toLongOrNull()?.let { Money.comma(it) } ?: raw
        val map = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                var d = 0
                out.forEachIndexed { i, ch ->
                    if (ch.isDigit()) {
                        d++
                        if (d == offset) return i + 1
                    }
                }
                return out.length
            }

            override fun transformedToOriginal(offset: Int): Int = out.take(offset.coerceIn(0, out.length)).count { it.isDigit() }
        }
        return TransformedText(AnnotatedString(out), map)
    }
}

@Composable
fun IssueEditorScreen(
    store: Store,
    data: PhoneData,
    player: Player,
    caseNo: String?,
    number: String?,
    issueId: String?,
    initialKind: IssueKind?,
    source: IssueSource?,
    onBack: () -> Unit,
) {
    val c = B.c
    val ctx = LocalContext.current
    StatusBarIcons(lightContent = false)
    val existing = remember(issueId) { issueId?.let { store.issue(it) } }
    var kind by remember { mutableStateOf(existing?.kind ?: initialKind) }
    val src = existing?.source ?: source
    // 기준 번호: 기록의 상대 (사건 화면에서 직접 쓰면 없을 수 있음)
    val subject = existing?.subject ?: src?.number ?: number
    var claimant by remember { mutableStateOf(existing?.claimant ?: subject) }
    var text by remember { mutableStateOf(existing?.text.orEmpty()) }
    var accidentType by remember { mutableStateOf(existing?.accidentType) }
    var baseOurs by remember { mutableStateOf(existing?.baseOurs) }
    var claimOurs by remember { mutableStateOf(existing?.claimOurs) }
    var amountKind by remember { mutableStateOf(existing?.amountKind) }
    var claimAmount by remember { mutableStateOf(existing?.claimAmount?.toString().orEmpty()) }
    var ourAmount by remember { mutableStateOf(existing?.ourAmount?.toString().orEmpty()) }
    val parts = remember { mutableStateListOf<String>().apply { addAll(existing?.parts.orEmpty()) } }
    var claimNote by remember { mutableStateOf(existing?.claimNote) }
    var drawingRef by remember { mutableStateOf(existing?.drawing) }
    val photoIds = remember { mutableStateListOf<String>().apply { addAll(existing?.photos.orEmpty()) } }

    var drawing by remember { mutableStateOf(false) }
    var pickPhotos by remember { mutableStateOf(false) }
    var addPerson by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ver = store.version.intValue
    // 고를 수 있는 사람: 보고 있던 사건 사람들, 없으면 이 번호와 같은 사건에 있는 사람들
    val people = remember(ver, caseNo, subject) {
        val base = if (caseNo != null) store.linksForCase(caseNo)
        else subject?.let { n -> store.linksForNumber(n).flatMap { store.linksForCase(it.caseNo) } }.orEmpty()
        val list = base.distinctBy { it.number }.map { it.number to it.role }
        if (subject != null && list.none { it.first == subject }) listOf(subject to "상대") + list else list
    }
    // 사람 추가 시 연결할 사건: 보고 있던 사건, 아니면 이 번호의 사건이 하나일 때 그 사건
    val linkCase = caseNo ?: subject?.let { n -> store.linksForNumber(n).map { it.caseNo }.distinct().singleOrNull() }
    val photoCases = caseNo?.let { listOf(it) } ?: subject?.let { n -> store.linksForNumber(n).map { it.caseNo }.distinct() }.orEmpty()
    val heading = caseNo ?: subject?.let { store.displayName(it) } ?: ""

    if (kind == null) {
        KindPickerDialog(
            title = "특이사항",
            subtitle = src?.let { sourceLabel(it) } ?: heading,
            onPick = { kind = it },
            onDismiss = onBack,
        )
        Box(Modifier.fillMaxSize().background(c.bg))
        return
    }
    val k = kind ?: return

    fun save() {
        val issue = Issue(
            id = existing?.id ?: Photos.newId(),
            caseNo = existing?.caseNo ?: caseNo,
            number = subject,
            kind = k,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            claimant = claimant,
            source = src,
            text = text.trim(),
            accidentType = accidentType.takeIf { k == IssueKind.FAULT },
            baseOurs = baseOurs.takeIf { k == IssueKind.FAULT },
            claimOurs = claimOurs.takeIf { k == IssueKind.FAULT },
            amountKind = amountKind.takeIf { k == IssueKind.AMOUNT },
            claimAmount = Money.parse(claimAmount).takeIf { k == IssueKind.AMOUNT },
            ourAmount = Money.parse(ourAmount).takeIf { k == IssueKind.AMOUNT },
            parts = if (k == IssueKind.DAMAGE) parts.toList() else emptyList(),
            claimNote = claimNote.takeIf { k == IssueKind.DAMAGE },
            drawing = drawingRef,
            photos = photoIds.toList(),
        )
        store.saveIssue(issue)
        onBack()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp),
            ) {
                CircleBtn(Ic.back, "뒤로", onClick = onBack)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(heading, style = ts(12f, W8, tracking = 0.04f, num = true), color = c.ink3, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Box(Modifier.press(scale = 0.95f) { if (existing == null) kind = null }) { KindTag(k, " 특이사항") }
                    }
                }
                if (existing != null) {
                    CircleBtn(Ic.trash, "삭제") { confirmDelete = true }
                    Spacer(Modifier.width(8.dp))
                }
                GradientButton("저장", height = 40.dp, radius = 13.dp, onClick = { save() })
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 40.dp),
            ) {
                if (src != null) SourceBar(src, store, player)

                FieldLabel(if (k == IssueKind.AMOUNT) "요구한 사람" else if (k == IssueKind.FAULT || k == IssueKind.DAMAGE) "주장한 사람" else "관련자")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                ) {
                    people.forEach { (n, role) ->
                        val on = claimant == n
                        PersonPick(store.nameOf(n), n, store.displayName(n), role, on) {
                            claimant = if (on) null else n
                        }
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .press(scale = 0.92f) { addPerson = true }
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, c.chip2, CircleShape),
                    ) { Icon(Ic.plusThin, "사람 추가", tint = c.ink2, modifier = Modifier.size(17.dp)) }
                }

                when (k) {
                    IssueKind.FAULT -> {
                        FieldLabel("과실")
                        BCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text("사고 유형", style = ts(13.5f, W8), color = c.ink)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .horizontalScroll(rememberScrollState()),
                                ) {
                                    Issue.ACCIDENT_TYPES.forEach { t ->
                                        SelectChip(t, accidentType == t) { accidentType = if (accidentType == t) null else t }
                                    }
                                }
                                Spacer(Modifier.height(14.dp))
                                RatioBar("기준", baseOurs, accent = false) { baseOurs = it }
                                Spacer(Modifier.height(8.dp))
                                RatioBar("주장", claimOurs, accent = true) { claimOurs = it }
                                Text(
                                    "막대를 누르거나 끌어서 우리 쪽 과실을 정해요 (10 단위)",
                                    style = ts(11.5f, W6),
                                    color = c.ink3,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                val b = baseOurs
                                val cl = claimOurs
                                if (b != null && cl != null) {
                                    DiffNote(
                                        when {
                                            cl < b -> "기준보다 우리 과실을 ${b - cl} 낮게 주장"
                                            cl > b -> "기준보다 우리 과실을 ${cl - b} 높게 주장"
                                            else -> "기준과 같은 비율"
                                        },
                                    )
                                }
                            }
                        }
                    }
                    IssueKind.AMOUNT -> {
                        FieldLabel("무슨 금액")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            Issue.AMOUNT_KINDS.forEach { a -> SelectChip(a, amountKind == a, big = true) { amountKind = if (amountKind == a) null else a } }
                        }
                        FieldLabel("금액")
                        BCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                MoneyField("요구", claimAmount, big = true) { claimAmount = it }
                                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(c.line))
                                MoneyField("우리 산정", ourAmount, big = false) { ourAmount = it }
                                val ca = Money.parse(claimAmount)
                                val oa = Money.parse(ourAmount)
                                if (ca != null && oa != null) {
                                    DiffNote(
                                        when {
                                            ca > oa -> "산정보다 ${Money.comma(ca - oa)}원 많이 요구"
                                            ca < oa -> "산정보다 ${Money.comma(oa - ca)}원 적게 요구"
                                            else -> "산정과 같은 금액"
                                        },
                                    )
                                }
                            }
                        }
                    }
                    IssueKind.DAMAGE -> {
                        FieldLabel("부위", "여러 개 가능")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            Issue.PARTS.forEach { p ->
                                val on = p in parts
                                SelectChip(p, on) { if (on) parts.remove(p) else parts.add(p) }
                            }
                        }
                        FieldLabel("주장 내용")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            Issue.DAMAGE_CLAIMS.forEach { d -> SelectChip(d, claimNote == d, big = true) { claimNote = if (claimNote == d) null else d } }
                        }
                    }
                    else -> {}
                }

                FieldLabel("근거 · 내용")
                BCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).heightIn(min = 72.dp)) {
                        if (text.isEmpty()) {
                            Text(
                                when (k) {
                                    IssueKind.FAULT -> "예: 상대가 깜빡이 없이 들어왔다고 주장, 블랙박스 보내주기로"
                                    IssueKind.AMOUNT -> "예: 정비소 견적 140만 받았다고 함"
                                    IssueKind.DAMAGE -> "예: 뒷문 찍힘도 이번 사고라고 주장"
                                    else -> "들은 내용, 확인할 것"
                                },
                                style = ts(14.5f, W4, lineHeight = 1.55f),
                                color = c.ink3,
                            )
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            textStyle = ts(14.5f, W4, lineHeight = 1.55f).copy(color = c.ink),
                            cursorBrush = SolidColor(c.brand),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                FieldLabel("첨부")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    drawingRef?.let { ref ->
                        AttachThumb(ref, onOpen = { drawing = true }, onRemove = {
                            Photos.deleteFile(ctx, ref)
                            drawingRef = null
                        })
                    }
                    photoIds.forEach { id ->
                        store.photo(id)?.let { p ->
                            AttachThumb(Photos.shown(p), onOpen = {}, onRemove = { photoIds.remove(id) })
                        }
                    }
                    OutlineAction(if (drawingRef == null) "그리기" else "다시 그리기", Ic.pen) { drawing = true }
                    OutlineAction("사진", Ic.image) { pickPhotos = true }
                }
            }
        }

        if (drawing) {
            DrawScreen(
                backgroundRef = drawingRef,
                onCancel = { drawing = false },
                onDone = { bmp ->
                    scope.launch {
                        val ref = withContext(Dispatchers.IO) { Photos.saveBitmap(ctx, bmp, "draw") }
                        drawingRef?.let { Photos.deleteFile(ctx, it) }
                        drawingRef = ref
                        drawing = false
                    }
                },
            )
        }
    }

    BackHandler(enabled = drawing) { drawing = false }

    if (pickPhotos) {
        PhotoPickDialog(
            store = store,
            cases = photoCases,
            selected = photoIds.toSet(),
            onDone = { ids ->
                photoIds.clear()
                photoIds.addAll(ids)
                pickPhotos = false
            },
            onDismiss = { pickPhotos = false },
        )
    }
    if (addPerson) {
        PersonPickDialog(
            store = store,
            data = data,
            caseNo = linkCase,
            onPicked = { n ->
                claimant = n
                addPerson = false
            },
            onDismiss = { addPerson = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.card,
            shape = RoundedCornerShape(26.dp),
            title = { Text("특이사항 삭제", style = ts(19f, W8), color = c.ink) },
            text = { Text("이 특이사항을 지울까요? 붙어 있던 통화·녹음·사진 원본은 그대로예요.", style = ts(14.5f, W4, lineHeight = 1.5f), color = c.ink2) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    existing?.let {
                        Photos.deleteFile(ctx, it.drawing)
                        store.deleteIssue(it.id)
                    }
                    onBack()
                }) { Text("삭제", style = ts(15f, W8), color = c.rec) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소", style = ts(15f, W7), color = c.ink2) } },
        )
    }
}

@Composable
private fun SourceBar(src: IssueSource, store: Store, player: Player) {
    val c = B.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val name = store.displayName(src.number)
    BCard(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
            .fillMaxWidth(),
        radius = 16.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (src.type == "rec") {
                val playing = player.currentKey == src.key && player.isPlaying
                PlayButton(playing, 36.dp) {
                    scope.launch {
                        val rec = RecordingIndex.peek()?.firstOrNull { it.file.cacheKey == src.key }
                            ?: RecordingIndex.get(PhoneData(ctx), store).firstOrNull { it.file.cacheKey == src.key }
                        if (rec != null) {
                            player.toggle(src.key, rec.file.uri, "$name · 통화 녹음", sourceLabel(src), src.number, rec.file.timeMillis, src.offsetMs ?: 0L)
                        }
                    }
                }
            } else {
                IconTile(sourceIcon(src), c.brand, c.brandTint, 36.dp, 12.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (src.type) {
                        "rec" -> "$name 통화 녹음"
                        "sms" -> "$name 문자"
                        else -> withJosa(name, "과", "와") + " 통화"
                    },
                    style = ts(13.5f, W8),
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val len = src.lengthMs?.takeIf { it > 0 }?.let { " · " + Fmt.durationKo(it) } ?: ""
                Text(Fmt.dayLabel(src.timeMillis).substringBefore(" ·") + " " + Fmt.time(src.timeMillis) + len, style = ts(12f, W6, num = true), color = c.ink2)
            }
            src.offsetMs?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(c.recTint)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Icon(Ic.wave, null, tint = c.rec, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(Fmt.duration(it), style = ts(12f, W8, num = true), color = c.rec)
                }
            }
        }
    }
}

@Composable
fun CircleBtn(icon: ImageVector, desc: String, size: Dp = 40.dp, onClick: () -> Unit) {
    val c = B.c
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .press(scale = 0.92f, onClick = onClick)
            .size(size)
            .depth(CircleShape)
            .clip(CircleShape)
            .background(c.card),
    ) { Icon(icon, desc, tint = c.ink, modifier = Modifier.size(19.dp)) }
}

@Composable
internal fun FieldLabel(text: String, hint: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 8.dp)) {
        Text(text, style = ts(12.5f, W8, tracking = 0.03f), color = B.c.ink2, modifier = Modifier.weight(1f))
        if (hint != null) Text(hint, style = ts(12f, W7), color = B.c.ink3)
    }
}

@Composable
internal fun PersonPick(name: String?, number: String, label: String, role: String, on: Boolean, onClick: () -> Unit) {
    val c = B.c
    val shape = RoundedCornerShape(20.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .press(scale = 0.95f, onClick = onClick)
            .height(40.dp)
            .then(if (on) Modifier else Modifier.depth(shape))
            .clip(shape)
            .background(if (on) c.brandTint else c.card)
            .then(if (on) Modifier.border(1.5.dp, c.brand, shape) else Modifier)
            .padding(start = 4.dp, end = 12.dp),
    ) {
        Avatar(name, number, 32.dp, checked = on)
        Spacer(Modifier.width(7.dp))
        Text(label, style = ts(13.5f, W8, num = name == null), color = c.ink, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        Text(role, style = ts(11f, W7), color = c.ink2, maxLines = 1)
    }
}

@Composable
internal fun SelectChip(text: String, on: Boolean, big: Boolean = false, onClick: () -> Unit) {
    val c = B.c
    val shape = RoundedCornerShape(11.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .press(scale = 0.95f, onClick = onClick)
            .height(if (big) 36.dp else 32.dp)
            .then(if (on) Modifier.glow(shape, 6.dp) else Modifier)
            .clip(shape)
            .then(if (on) Modifier.background(Brush.linearGradient(listOf(c.brand2, c.brand))) else Modifier.background(c.chip))
            .padding(horizontal = 12.dp),
    ) {
        Text(text, style = ts(if (big) 13.5f else 13f, W7), color = if (on) Color.White else c.ink2, maxLines = 1)
    }
}

/** 우리 과실 막대: 누르거나 끌어서 10 단위 */
@Composable
private fun RatioBar(label: String, value: Int?, accent: Boolean, onValue: (Int) -> Unit) {
    val c = B.c
    fun pick(x: Float, width: Int) = ((x / width).coerceIn(0f, 1f) * 10).roundToInt() * 10
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = ts(11.5f, W8), color = if (accent) c.warn else c.ink3, modifier = Modifier.width(32.dp))
        val shape = RoundedCornerShape(9.dp)
        Box(
            Modifier
                .weight(1f)
                .height(30.dp)
                .clip(shape)
                .then(if (accent && value != null) Modifier.border(2.dp, c.warn, shape) else Modifier)
                .background(c.chip)
                .pointerInput(Unit) { detectTapGestures { onValue(pick(it.x, size.width)) } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        onValue(pick(change.position.x, size.width))
                    }
                },
        ) {
            if (value == null) {
                Text("눌러서 정하기", style = ts(12.5f, W7), color = c.ink3, modifier = Modifier.align(Alignment.Center))
            } else {
                Row(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxWidth(value / 100f)
                            .fillMaxHeight()
                            .background(Brush.linearGradient(listOf(c.brand2, c.brand))),
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Brush.linearGradient(listOf(Color(0xFFFF9B7B), Color(0xFFE1514A)))),
                    )
                }
                Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("우리 $value", style = ts(12.5f, W8, num = true), color = Color.White)
                    Spacer(Modifier.weight(1f))
                    Text("${100 - value}", style = ts(12.5f, W8, num = true), color = Color.White)
                }
            }
        }
    }
}

@Composable
internal fun DiffNote(text: String) {
    val c = B.c
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.warnTint)
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Icon(Ic.info, null, tint = c.warn, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = ts(13f, W7, num = true), color = c.ink)
    }
}

@Composable
private fun MoneyField(label: String, value: String, big: Boolean, onValue: (String) -> Unit) {
    val c = B.c
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Text(label, style = ts(13.5f, W8), color = c.ink2, modifier = Modifier.width(76.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (value.isEmpty()) Text("0", style = ts(if (big) 26f else 20f, W8, num = true), color = c.chip2)
            BasicTextField(
                value = value,
                onValueChange = { onValue(it.filter { ch -> ch.isDigit() }.take(12)) },
                singleLine = true,
                visualTransformation = Thousands,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                textStyle = ts(if (big) 26f else 20f, W8, tracking = -0.025f, num = true).copy(
                    color = if (big) c.ink else c.ink2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                ),
                cursorBrush = SolidColor(c.brand),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text("원", style = ts(14f, W7), color = c.ink2, modifier = Modifier.padding(start = 3.dp))
    }
}

@Composable
private fun OutlineAction(text: String, icon: ImageVector, onClick: () -> Unit) {
    val c = B.c
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .press(scale = 0.95f, onClick = onClick)
            .height(40.dp)
            .clip(shape)
            .border(1.5.dp, c.chip2, shape)
            .padding(horizontal = 12.dp),
    ) {
        Icon(icon, null, tint = c.ink2, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = ts(13f, W7), color = c.ink2)
    }
}

@Composable
private fun AttachThumb(ref: String, onOpen: () -> Unit, onRemove: () -> Unit) {
    val c = B.c
    Box(Modifier.size(64.dp)) {
        Thumb(ref, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).press(scale = 0.95f, onClick = onOpen))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .press(scale = 0.9f, onClick = onRemove)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(0x990C1222)),
        ) { Icon(Ic.x, "빼기", tint = Color.White, modifier = Modifier.size(10.dp)) }
    }
}

// ───────────────────────── 사진 고르기 · 사람 추가 ─────────────────────────

@Composable
private fun PhotoPickDialog(store: Store, cases: List<String>, selected: Set<String>, onDone: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val c = B.c
    val list = remember { cases.flatMap { store.photosForCase(it) }.distinctBy { it.uri } }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(selected) } }
    Dialog(onDismissRequest = onDismiss) {
        BCard(radius = 26.dp, level = Depth.FLOAT, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("사건 사진에서 고르기", style = ts(18f, W8), color = c.ink)
                if (list.isEmpty()) {
                    Text(
                        "이 사건에 넣은 사진이 없어요.\n사건 화면 오른쪽 위 [사진+]로 먼저 넣어 주세요.",
                        style = ts(13.5f, W4, lineHeight = 1.5f),
                        color = c.ink2,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    Column(
                        Modifier
                            .padding(top = 12.dp)
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        list.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(bottom = 5.dp)) {
                                row.forEach { p ->
                                    val on = p.id in chosen
                                    Box(Modifier.weight(1f).height(86.dp)) {
                                        Thumb(
                                            Photos.shown(p),
                                            Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp)).press(scale = 0.96f) {
                                                if (on) chosen.remove(p.id) else chosen.add(p.id)
                                            },
                                        )
                                        if (on) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp).clip(CircleShape).background(c.brand),
                                            ) { Icon(Ic.check, null, tint = Color.White, modifier = Modifier.size(10.dp)) }
                                        }
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                Row(Modifier.padding(top = 10.dp)) {
                    SoftButton("취소", modifier = Modifier.weight(1f), onClick = onDismiss)
                    Spacer(Modifier.width(10.dp))
                    GradientButton("붙이기", modifier = Modifier.weight(1f), height = 48.dp, radius = 15.dp, enabled = list.isNotEmpty()) { onDone(chosen.toList()) }
                }
            }
        }
    }
}

/** 관련자 추가: 연락처·통화내역에서 고르고 관계를 정하면 사건에도 연결 */
@Composable
fun PersonPickDialog(store: Store, data: PhoneData, caseNo: String?, onPicked: (String) -> Unit, onDismiss: () -> Unit) {
    val c = B.c
    var dir by remember { mutableStateOf<List<DirEntry>?>(null) }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<DirEntry?>(null) }
    var role by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { dir = withContext(Dispatchers.IO) { data.directory() } }
    val inCase = remember { caseNo?.let { store.linksForCase(it).map { l -> l.number }.toSet() }.orEmpty() }
    Dialog(onDismissRequest = onDismiss) {
        BCard(radius = 26.dp, level = Depth.FLOAT, color = c.bg, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("관련자 추가", style = ts(18f, W8), color = c.ink)
                Text(
                    if (caseNo != null) "추가하면 $caseNo 사건에도 연결돼요" else "사건 없이 이 특이사항의 관련자로만 넣어요",
                    style = ts(12.5f, W6, num = true),
                    color = c.ink2,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                )
                val p = picked
                if (p == null) {
                    LightSearchField(query, { query = it }, "이름, 번호, 초성")
                    val typed = query.filter { it.isDigit() }
                    val shown = dir.orEmpty().filter { e ->
                        e.number !in inCase && (
                            query.isBlank() ||
                                (typed.length >= 3 && e.number.contains(typed)) ||
                                (e.name?.let { Hangul.matches(it, query.trim()) } == true)
                            )
                    }.take(60)
                    Box(Modifier.padding(top = 10.dp).height(320.dp)) {
                        if (dir == null) {
                            Loading("연락처 · 통화내역 불러오는 중")
                        } else {
                            LazyColumn {
                                if (shown.isEmpty() && typed.length >= 9) {
                                    item {
                                        DirPickRow(DirEntry(PhoneNumbers.normalize(typed), null, null, null)) { picked = it }
                                    }
                                }
                                items(shown, key = { it.number }) { e -> DirPickRow(e) { picked = it } }
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p.name, p.number, 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name ?: PhoneNumbers.format(p.number), style = ts(15.5f, W8, num = p.name == null), color = c.ink)
                            Text(PhoneNumbers.format(p.number), style = ts(12.5f, W4, num = true), color = c.ink2)
                        }
                        Text("다시 고르기", style = ts(13f, W7), color = c.brand, modifier = Modifier.press { picked = null }.padding(6.dp))
                    }
                    if (caseNo != null) {
                        Text("관계", style = ts(12.5f, W8), color = c.ink2, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Roles.all.forEach { r -> RoleChip(r, role == r) { role = r } }
                        }
                    }
                }
                Row(Modifier.padding(top = 14.dp)) {
                    SoftButton("취소", modifier = Modifier.weight(1f), onClick = onDismiss)
                    Spacer(Modifier.width(10.dp))
                    GradientButton(
                        "연결",
                        modifier = Modifier.weight(1f),
                        height = 48.dp,
                        radius = 15.dp,
                        enabled = p != null && (caseNo == null || roleReady(role)),
                    ) {
                        if (p != null) {
                            if (caseNo != null) store.upsert(caseNo, listOf(Triple(p.number, role, p.name)))
                            else p.name?.let { store.rememberName(p.number, it) }
                            onPicked(p.number)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirPickRow(e: DirEntry, onPick: (DirEntry) -> Unit) {
    val c = B.c
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .press(scale = 0.98f) { onPick(e) }
            .padding(horizontal = 6.dp, vertical = 7.dp),
    ) {
        Avatar(e.name, e.number, 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name ?: PhoneNumbers.format(e.number), style = ts(14.5f, W7, num = e.name == null), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (e.name != null) PhoneNumbers.format(e.number) + (e.label?.let { " · $it" } ?: "") else "저장 안 된 번호",
                style = ts(12.5f, W4, num = true),
                color = c.ink2,
                maxLines = 1,
            )
        }
        e.lastCall?.let { Text(Fmt.short(it), style = ts(11.5f, W7, num = true), color = c.ink3) }
    }
}
