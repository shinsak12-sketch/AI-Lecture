package com.bosang.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bosang.search.core.PhoneNumbers
import com.bosang.search.data.CaseLink
import com.bosang.search.data.PhoneData
import com.bosang.search.data.Player
import com.bosang.search.data.RecordingIndex
import com.bosang.search.data.Roles
import com.bosang.search.data.Store
import com.bosang.search.data.Summaries
import com.bosang.search.data.TimelineItem

@Composable
fun CaseScreen(
    store: Store,
    data: PhoneData,
    player: Player,
    caseNo: String,
    resumeTick: Int,
    onBack: () -> Unit,
    onOpenPerson: (String) -> Unit,
    onAddPeople: () -> Unit,
) {
    val c = B.c
    StatusBarIcons(lightContent = true)
    val ver = store.version.intValue
    val links = remember(ver, caseNo) { store.linksForCase(caseNo) }
    LaunchedEffect(caseNo) { store.touchRecent("c:$caseNo") }
    if (links.isEmpty()) {
        // 마지막 사람을 빼거나 사건을 지우면 돌아감
        LaunchedEffect(Unit) { onBack() }
        Box(
            Modifier
                .fillMaxSize()
                .background(c.bg),
        )
        return
    }

    val numbers = links.map { it.number }.toSet()
    var items by remember(caseNo) { mutableStateOf<List<TimelineItem>?>(null) }
    var calls by remember(caseNo) { mutableStateOf<Pair<Int, Long?>?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(numbers, resumeTick, refresh) {
        items = RecordingIndex.timeline(numbers, data, store)
        calls = Summaries.calls(numbers, data)
    }

    var kind by remember { mutableStateOf(KindFilter.ALL) }
    var who by remember { mutableStateOf<String?>(null) }
    var options by remember { mutableStateOf<CaseLink?>(null) }
    var editing by remember { mutableStateOf<CaseLink?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var kindMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }

    val roleOf = links.associate { it.number to it.role }
    val label: (String) -> WhoInfo? = { n -> WhoInfo(store.displayName(n), roleOf[n]) }
    val shown = items?.filterKind(kind)?.filter { who == null || it.number == who }
    val all = items.orEmpty()
    val lastContact = listOfNotNull(all.firstOrNull()?.timeMillis, calls?.second).maxOrNull()

    LazyColumn(
        contentPadding = PaddingValues(bottom = 120.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        item(key = "hero") {
            Overlap(
                overlap = 40.dp,
                top = {
                    Hero(bottomPadding = 64.dp) {
                        HeroTopBar(
                            left = { GlassCircle(Ic.back, "뒤로", onClick = onBack) },
                            right = {
                                Box {
                                    GlassCircle(Ic.more, "더보기") { moreMenu = true }
                                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                        MenuItem("사람 추가", Ic.userPlus) {
                                            moreMenu = false
                                            onAddPeople()
                                        }
                                        MenuItem("통화녹음 다시 찾기", Ic.refresh) {
                                            moreMenu = false
                                            RecordingIndex.invalidate()
                                            refresh++
                                        }
                                        MenuItem("사건 삭제", Ic.trash, danger = true) {
                                            moreMenu = false
                                            confirmDelete = true
                                        }
                                    }
                                }
                            },
                        )
                        Text(
                            "사고번호",
                            style = ts(12f, W8, tracking = 0.1f),
                            color = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            caseNo,
                            style = ts(34f, W8, tracking = -0.025f, num = true),
                            color = Color.White,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                            if (lastContact != null) {
                                Box(
                                    Modifier
                                        .size(13.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x404BE3AC)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4BE3AC)),
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(Fmt.ago(lastContact) + " 연락", style = ts(13f, W6, num = true), color = Color.White.copy(alpha = 0.72f))
                                Box(
                                    Modifier
                                        .padding(horizontal = 8.dp)
                                        .size(3.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.4f)),
                                )
                            }
                            Text("${links.size}명 연결", style = ts(13f, W6), color = Color.White.copy(alpha = 0.72f))
                        }
                    }
                },
                bottom = {
                    BCard(
                        level = Depth.LIFT,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(vertical = 14.dp, horizontal = 6.dp)) {
                            val recs = items?.count { it is TimelineItem.Rec }
                            val sms = items?.count { it is TimelineItem.Sms }
                            Stat(Ic.wave, c.rec, c.recTint, recs, "통화녹음", Modifier.weight(1f)) { kind = KindFilter.REC }
                            StatDivider()
                            Stat(Ic.msg, c.brand, c.brandTint, sms, "문자", Modifier.weight(1f)) { kind = KindFilter.SMS }
                            StatDivider()
                            Stat(Ic.phone, c.ink2, c.chip, calls?.first, "통화", Modifier.weight(1f)) { kind = KindFilter.ALL }
                        }
                    }
                },
            )
        }

        item(key = "people") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp),
            ) {
                item(key = "all") {
                    PersonChip(on = who == null, onClick = { who = null }) {
                        Text("전체", style = ts(13.5f, W8), color = if (who == null) c.card else c.ink, modifier = Modifier.padding(horizontal = 11.dp))
                    }
                }
                items(links, key = { it.number }) { link ->
                    val on = who == link.number
                    PersonChip(
                        on = on,
                        onClick = { who = if (on) null else link.number },
                        onLongClick = { options = link },
                    ) {
                        Avatar(store.nameOf(link.number), link.number, 34.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.padding(end = 9.dp)) {
                            Text(
                                store.displayName(link.number),
                                style = ts(13.5f, W8, num = store.nameOf(link.number) == null),
                                color = if (on) c.card else c.ink,
                                maxLines = 1,
                            )
                            Text(link.role, style = ts(11f, W6), color = if (on) c.card.copy(alpha = 0.7f) else c.ink2, maxLines = 1)
                        }
                    }
                }
                item(key = "add") {
                    val shape = RoundedCornerShape(22.dp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .press(scale = 0.95f, onClick = onAddPeople)
                            .height(44.dp)
                            .clip(shape)
                            .border(1.5.dp, c.chip2, shape)
                            .padding(horizontal = 14.dp),
                    ) {
                        Icon(Ic.plusThin, null, tint = c.ink2, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("사람 추가", style = ts(13.5f, W7), color = c.ink2)
                    }
                }
            }
        }
        item(key = "hint") {
            Text(
                "사람을 누르면 그 사람 기록만, 길게 누르면 상세 · 관계 수정",
                style = ts(12f, W6),
                color = c.ink3,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        item(key = "tl-head") {
            SectionHeader("타임라인", items?.filterKind(kind)?.filter { who == null || it.number == who }?.size, modifier = Modifier.padding(top = 0.dp)) {
                Box {
                    HeaderAction(kind.label, trailing = Ic.down) { kindMenu = true }
                    DropdownMenu(expanded = kindMenu, onDismissRequest = { kindMenu = false }) {
                        KindFilter.entries.forEach { k ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${k.label} ${all.filterKind(k).filter { who == null || it.number == who }.size}",
                                        style = ts(15f, if (k == kind) W8 else W6, num = true),
                                    )
                                },
                                onClick = {
                                    kind = k
                                    kindMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }
        timeline(
            items = shown,
            player = player,
            emptyText = if (who != null || kind != KindFilter.ALL) "조건에 맞는 기록이 없어요. 위에서 [전체]를 눌러 보세요." else "이 번호들과 주고받은 문자나 통화녹음을 폰에서 찾지 못했어요.",
            who = label,
        )
    }

    options?.let { link ->
        OptionsDialog(
            title = store.displayName(link.number),
            subtitle = "${PhoneNumbers.format(link.number)} · ${link.role}",
            onDismiss = { options = null },
            items = listOf(
                Option("사람 상세 보기", Ic.user) {
                    options = null
                    onOpenPerson(link.number)
                },
                Option("관계 바꾸기", Ic.edit) {
                    options = null
                    editing = link
                },
                Option("이 사건에서 빼기", Ic.trash, danger = true) {
                    options = null
                    if (who == link.number) who = null
                    store.removeLink(caseNo, link.number)
                },
            ),
        )
    }

    editing?.let { link ->
        RoleDialog(
            title = store.displayName(link.number),
            caseNo = caseNo,
            initial = link.role,
            onDismiss = { editing = null },
            onSave = { role ->
                store.setRole(caseNo, link.number, role)
                editing = null
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.card,
            shape = RoundedCornerShape(26.dp),
            title = { Text("$caseNo 삭제", style = ts(19f, W8, num = true), color = c.ink) },
            text = {
                Text(
                    "이 사건의 연결 정보만 지워요. 폰에 있는 문자와 통화녹음 원본은 그대로 남습니다.",
                    style = ts(14.5f, W4, lineHeight = 1.5f),
                    color = c.ink2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    store.deleteCase(caseNo)
                }) { Text("삭제", style = ts(15f, W8), color = c.rec) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("취소", style = ts(15f, W7), color = c.ink2) }
            },
        )
    }
}

@Composable
private fun Stat(icon: ImageVector, fg: Color, bg: Color, value: Int?, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = B.c
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.press(scale = 0.95f, onClick = onClick),
    ) {
        IconTile(icon, fg, bg, 30.dp, 10.dp)
        Spacer(Modifier.height(6.dp))
        Text(value?.toString() ?: "–", style = ts(21f, W8, tracking = -0.02f, num = true), color = c.ink)
        Text(label, style = ts(12f, W6), color = c.ink2)
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .padding(vertical = 8.dp)
            .width(1.dp)
            .height(64.dp)
            .background(B.c.line),
    )
}

@Composable
private fun PersonChip(
    on: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val c = B.c
    val shape = RoundedCornerShape(22.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .press(scale = 0.95f, onLongClick = onLongClick, onClick = onClick)
            .height(44.dp)
            .depth(shape)
            .clip(shape)
            .background(if (on) c.ink else c.card)
            .padding(start = 5.dp, end = 5.dp),
        content = content,
    )
}

@Composable
fun MenuItem(text: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    val c = B.c
    DropdownMenuItem(
        text = { Text(text, style = ts(15f, W6), color = if (danger) c.rec else c.ink) },
        leadingIcon = { Icon(icon, null, tint = if (danger) c.rec else c.ink2, modifier = Modifier.size(18.dp)) },
        onClick = onClick,
    )
}

data class Option(val text: String, val icon: ImageVector, val danger: Boolean = false, val onClick: () -> Unit)

/** 길게 눌렀을 때 뜨는 선택 창 */
@Composable
fun OptionsDialog(title: String, subtitle: String?, items: List<Option>, onDismiss: () -> Unit) {
    val c = B.c
    Dialog(onDismissRequest = onDismiss) {
        BCard(radius = 26.dp, level = Depth.FLOAT, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 10.dp)) {
                Text(title, style = ts(19f, W8), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = ts(13f, W6, num = true), color = c.ink2, modifier = Modifier.padding(top = 3.dp))
            }
            Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 10.dp)) {
                items.forEach { o ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .press(scale = 0.98f, onClick = o.onClick)
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                    ) {
                        IconTile(o.icon, if (o.danger) c.rec else c.brand, if (o.danger) c.recTint else c.brandTint, 34.dp, 11.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(o.text, style = ts(15.5f, W7), color = if (o.danger) c.rec else c.ink)
                    }
                }
            }
        }
    }
}

/** 관계 바꾸기 창 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoleDialog(title: String, caseNo: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val c = B.c
    var role by remember { mutableStateOf(initial) }
    val customMode = role == Roles.CUSTOM || (role.isNotEmpty() && role !in Roles.all)
    Dialog(onDismissRequest = onDismiss) {
        BCard(radius = 26.dp, level = Depth.FLOAT, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp)) {
                Text(title, style = ts(19f, W8), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$caseNo 에서의 관계", style = ts(13f, W6, num = true), color = c.ink2, modifier = Modifier.padding(top = 3.dp))
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    (Roles.all + Roles.CUSTOM).forEach { r ->
                        RoleChip(r, if (r == Roles.CUSTOM) customMode else role == r) { role = r }
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
                            onValueChange = { role = it.take(12).ifBlank { Roles.CUSTOM } },
                            singleLine = true,
                            textStyle = ts(14.5f, W7).copy(color = c.ink),
                            cursorBrush = SolidColor(c.brand),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row {
                    SoftButton("취소", modifier = Modifier.weight(1f), onClick = onDismiss)
                    Spacer(Modifier.width(10.dp))
                    GradientButton("저장", modifier = Modifier.weight(1f), enabled = roleReady(role), height = 48.dp, radius = 15.dp) {
                        onSave(role.trim())
                    }
                }
            }
        }
    }
}
