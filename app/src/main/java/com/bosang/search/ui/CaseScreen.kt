package com.bosang.search.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.mutableStateListOf
import com.bosang.search.data.PhotoTags
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.bosang.search.data.Records
import com.bosang.search.core.CallEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.CircularProgressIndicator
import com.bosang.search.data.TimelineItem
import com.bosang.search.core.RecordQuery
import com.bosang.search.data.IssueSource
import com.bosang.search.data.CasePhoto
import com.bosang.search.data.Photos
import com.bosang.search.data.recordKey
import com.bosang.search.data.toSource
import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString

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
    onIssue: (issueId: String?, source: IssueSource?) -> Unit,
    onAlbum: () -> Unit,
    onPhoto: (ref: String, photoId: String?) -> Unit,
    onAppt: (apptId: String?, number: String?, callTime: Long?) -> Unit,
    onRepair: (repairId: String?) -> Unit,
    onCamera: () -> Unit,
    onReport: (photoIds: List<String>) -> Unit,
) {
    val c = B.c
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    StatusBarIcons(lightContent = true)
    val ver = store.version.intValue
    val links = remember(ver, caseNo) { store.linksForCase(caseNo) }
    LaunchedEffect(caseNo) { store.touchRecent("c:$caseNo") }
    val exists = remember(ver, caseNo) { store.caseExists(caseNo) }
    if (links.isEmpty() && !exists) {
        // 사건을 지우면 돌아감 (사람 없이 사진만 있는 사건은 그대로 보임)
        LaunchedEffect(Unit) { onBack() }
        Box(
            Modifier
                .fillMaxSize()
                .background(c.bg),
        )
        return
    }

    val numbers = links.map { it.number }.toSet()
    var sms by remember(caseNo) { mutableStateOf<List<TimelineItem.Sms>?>(null) }
    var calls by remember(caseNo) { mutableStateOf<List<CallEntry>?>(null) }
    var recs by remember(caseNo) { mutableStateOf<List<TimelineItem.Rec>?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(numbers, resumeTick, refresh) {
        // 문자 · 통화는 바로, 통화녹음은 뒤에서 (이전 결과가 있으면 먼저 보여줌)
        launch { sms = Records.sms(numbers, data, !store.showNotices()) }
        launch { calls = Records.calls(numbers, data) }
        RecordingIndex.peek()?.let { recs = Records.recs(numbers, it) }
        val index = RecordingIndex.get(data, store)
        recs = withContext(Dispatchers.Default) { Records.recs(numbers, index) }
    }

    val allIssues = remember(ver, caseNo) { store.issuesForCase(caseNo) }
    val allPhotos = remember(ver, caseNo) { store.photosForCase(caseNo) }
    val issueIdx = remember(allIssues) { issueIndex(allIssues) }
    var onlyIssues by rememberSaveable { mutableStateOf(false) }
    val nameOf: (String) -> String = { store.displayName(it) }
    // 문자로 받은 사진은 자동으로 사건 사진에 보관
    LaunchedEffect(sms) {
        sms?.let { list -> Photos.syncMms(ctx, store, list.map { it.sms }, caseNo) }
    }
    var photoAdd by remember { mutableStateOf(false) }
    var recordMenu by remember { mutableStateOf<TimelineItem?>(null) }
    // 사진 탭: 보는 방식 · 여러 장 고르기 · 차량번호
    var photoView by rememberSaveable { mutableStateOf(PhotoView.VEHICLE) }
    val pickedPhotos = remember { mutableStateListOf<String>() }
    var carEdit by remember { mutableStateOf<String?>(null) }
    var tagPick by remember { mutableStateOf<String?>(null) }
    val imagePerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    val albumPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onAlbum() }

    // 다른 화면(카메라 등)에 갔다 와도 보던 분류 그대로
    var kind by rememberSaveable { mutableStateOf(UiMemory.caseKind[caseNo] ?: Kind.CALL) }
    LaunchedEffect(kind) { UiMemory.caseKind[caseNo] = kind }
    var who by remember { mutableStateOf<String?>(null) }
    var options by remember { mutableStateOf<CaseLink?>(null) }
    var editing by remember { mutableStateOf<CaseLink?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }

    val roleOf = links.associate { it.number to it.role }
    val label: (String) -> WhoInfo? = { n -> WhoInfo(store.displayName(n), roleOf[n]) }
    // 기록 안 검색 (내용 · 날짜 · 시각 · 이름)
    var search by rememberSaveable { mutableStateOf("") }
    val rq = remember(search) { RecordQuery.parse(search) }
    fun <T : TimelineItem> List<T>.narrow(): List<T> {
        val byWho = if (who == null) this else filter { it.number == who }
        if (rq.isEmpty) return byWho
        return byWho.filter { rq.matches(it.timeMillis, recordSearchText(it, label(it.number), attachedIssues(it, issueIdx), nameOf)) }
    }
    val callItems = remember(calls, recs) { calls?.let { Records.callItems(it, recs) } }
    val fCalls = remember(callItems, who, rq, issueIdx) { callItems?.narrow() }
    val fSms = remember(sms, who, rq, issueIdx) { sms?.narrow() }
    val fRecs = remember(recs, who, rq, issueIdx) { recs?.narrow() }
    val issues = remember(allIssues, rq) {
        if (rq.isEmpty) allIssues
        else allIssues.filter { rq.matches(it.source?.timeMillis ?: it.createdAt, issueSearchText(it, nameOf)) }
    }
    val photos = remember(allPhotos, rq) {
        if (rq.isEmpty) allPhotos
        else allPhotos.filter { p ->
            val text = listOfNotNull(
                "사진",
                p.kind,
                p.from?.let { nameOf(it) },
                when (p.source) { "mms" -> "문자 사진"; "camera" -> "촬영"; else -> "앨범" },
            ).joinToString(" ")
            rq.matches(p.takenAt, text)
        }
    }
    val shownAll: List<TimelineItem>? = when (kind) {
        Kind.SMS -> fSms
        Kind.REC -> fRecs
        Kind.CALL -> fCalls
        Kind.PHOTO -> null
    }
    val withIssues = shownAll?.count { attachedIssues(it, issueIdx).isNotEmpty() } ?: 0
    val shown = if (onlyIssues) shownAll?.filter { attachedIssues(it, issueIdx).isNotEmpty() } else shownAll
    val counts = mapOf(
        Kind.CALL to fCalls?.size,
        Kind.SMS to fSms?.size,
        Kind.REC to fRecs?.size,
        Kind.PHOTO to photos.size,
    )
    val found = if (rq.isEmpty) null else listOfNotNull(fCalls?.size, fSms?.size, fRecs?.size).sum() + photos.size + issues.size
    val lastContact = listOfNotNull(
        sms?.firstOrNull()?.timeMillis,
        recs?.firstOrNull()?.timeMillis,
        calls?.maxOfOrNull { it.timeMillis },
    ).maxOrNull()

    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,        contentPadding = PaddingValues(bottom = 120.dp),
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
                                GlassPill("사진+", Ic.image) { photoAdd = true }
                                Spacer(Modifier.width(8.dp))
                                Box {
                                    GlassCircle(Ic.more, "더보기") { moreMenu = true }
                                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                        MenuItem("사람 추가", Ic.userPlus) {
                                            moreMenu = false
                                            onAddPeople()
                                        }
                                        MenuItem("약속 추가", Ic.calendar) {
                                            moreMenu = false
                                            onAppt(null, null, null)
                                        }
                                        MenuItem("입고 등록", Ic.car) {
                                            moreMenu = false
                                            onRepair(null)
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
                            Stat(Ic.phone, c.ink2, c.chip, calls?.size, "통화", Modifier.weight(1f)) { kind = Kind.CALL }
                            StatDivider()
                            Stat(Ic.msg, c.brand, c.brandTint, sms?.size, "문자", Modifier.weight(1f)) { kind = Kind.SMS }
                            StatDivider()
                            Stat(Ic.wave, c.rec, c.recTint, recs?.size, "녹취", Modifier.weight(1f)) { kind = Kind.REC }
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

        item(key = "search") {
            RecordSearchBar(
                value = search,
                onValue = { search = it },
                query = rq,
                found = found,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
        }

        if (rq.isEmpty) {
            planSection(
                store = store,
                appts = store.apptsForCase(caseNo),
                repairs = store.repairsForCase(caseNo),
                onAppt = { a -> onAppt(a?.id, null, null) },
                onRepair = { r -> onRepair(r?.id) },
            )
        }

        issuesSection(
            issues = issues,
            nameOf = nameOf,
            onOpen = { onIssue(it.id, null) },
            onAdd = { onIssue(null, null) },
        )

        item(key = "tl-head") {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp)) {
                KindTabs(
                    selected = kind,
                    counts = counts,
                    onSelect = { kind = it },
                )
                if (kind != Kind.PHOTO) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
                        FilterPill("전체", !onlyIssues) { onlyIssues = false }
                        FilterPill("특이사항 있는 것만 $withIssues", onlyIssues) { onlyIssues = true }
                    }
                }
            }
        }
        if (kind == Kind.PHOTO) {
            photoSection(
                photos,
                nameOf,
                PhotoTabState(
                    view = photoView,
                    onView = { photoView = it },
                    selected = pickedPhotos.toSet(),
                    onSelect = { p -> if (p.id in pickedPhotos) pickedPhotos.remove(p.id) else pickedPhotos.add(p.id) },
                    carNo = { v -> store.carNo(caseNo, v) },
                    onCarNo = { carEdit = it },
                    onCamera = onCamera,
                    onAlbum = { if (Perms.granted(ctx, imagePerm)) onAlbum() else albumPerm.launch(imagePerm) },
                    onReport = { onReport(emptyList()) },
                ),
            ) { onPhoto(Photos.shown(it), it.id) }
        } else {
            timeline(
                items = shown,
                player = player,
                emptyText = when {
                    !rq.isEmpty -> "'${search.trim()}'에 맞는 ${kind.label} 기록이 없어요." + elsewhere(counts, kind)
                    onlyIssues -> "특이사항이 붙은 ${kind.label} 기록이 없어요."
                    who != null -> "이 사람과의 ${kind.label} 기록이 없어요. 위에서 [전체]를 눌러 보세요."
                    kind == Kind.REC -> "이 번호들의 통화녹음을 폰에서 찾지 못했어요."
                    else -> "이 번호들과의 ${kind.label} 기록이 없어요."
                },
                who = label,
                loadingText = if (kind == Kind.REC) "통화녹음을 찾는 중 (파일이 많으면 조금 걸려요)" else "불러오는 중",
                actions = TimelineActions(
                    onMore = { recordMenu = it },
                    attached = { attachedIssues(it, issueIdx) },
                    nameOf = nameOf,
                    onIssue = { onIssue(it.id, null) },
                    highlight = rq.terms,
                    onImage = { u ->
                        val saved = store.photo("mms_${u.lastPathSegment}_$caseNo")
                        if (saved != null) onPhoto(Photos.shown(saved), saved.id) else onPhoto(u.toString(), null)
                    },
                ),
            )
        }
    }
    ScrollTopButton(listState, bottom = if (player.currentKey != null) 96.dp else 20.dp, modifier = Modifier.align(Alignment.BottomCenter))
    // 사진 여러 장 골랐을 때
    if (kind == Kind.PHOTO && pickedPhotos.isNotEmpty()) {
        BCard(
            level = Depth.FLOAT,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp).fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text("${pickedPhotos.size}장", style = ts(15f, W8, num = true), color = c.ink, modifier = Modifier.padding(end = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    FilterPill("차량", false) { tagPick = "vehicle" }
                    FilterPill("단계", false) { tagPick = "stage" }
                    FilterPill("사진대지", false) {
                        onReport(pickedPhotos.toList())
                        pickedPhotos.clear()
                    }
                }
                Text("해제", style = ts(14f, W8), color = c.ink2, modifier = Modifier.press(scale = 0.94f) { pickedPhotos.clear() }.padding(8.dp))
            }
        }
    }
    }
    BackHandler(enabled = pickedPhotos.isNotEmpty()) { pickedPhotos.clear() }

    tagPick?.let { which ->
        val list = if (which == "vehicle") PhotoTags.VEHICLES else PhotoTags.STAGES
        OptionsDialog(
            title = if (which == "vehicle") "어느 차량 사진인가요" else "어느 단계 사진인가요",
            subtitle = "${pickedPhotos.size}장",
            items = list.map { (k, label) ->
                Option(if (which == "vehicle") listOfNotNull(label, store.carNo(caseNo, k)).joinToString(" ") else label, if (which == "vehicle") Ic.car else Ic.clock) {
                    val ids = pickedPhotos.toSet()
                    store.updatePhotos(allPhotos.filter { it.id in ids }.map { if (which == "vehicle") it.copy(vehicle = k) else it.copy(stage = k) })
                    tagPick = null
                    pickedPhotos.clear()
                }
            },
            onDismiss = { tagPick = null },
        )
    }
    carEdit?.let { v ->
        CarNoDialog(
            title = (PhotoTags.vehicleLabel(v) ?: "차량") + " 차량번호",
            initial = store.carNo(caseNo, v).orEmpty(),
            onDismiss = { carEdit = null },
        ) {
            store.setCarNo(caseNo, v, it)
            carEdit = null
        }
    }

    if (photoAdd) {
        PhotoAddDialog(
            caseNo = caseNo,
            onCamera = {
                photoAdd = false
                onCamera()
            },
            onAlbum = {
                photoAdd = false
                if (Perms.granted(ctx, imagePerm)) onAlbum() else albumPerm.launch(imagePerm)
            },
            onDismiss = { photoAdd = false },
        )
    }

    recordMenu?.let { item ->
        val who = store.displayName(item.number)
        val title = when (item) {
            is TimelineItem.Rec -> "$who · 통화 녹음"
            is TimelineItem.Sms -> "$who · " + if (item.sms.incoming) "받은 문자" else "보낸 문자"
            is TimelineItem.Call -> "$who · 통화"
        }
        val sub = Fmt.dayLabel(item.timeMillis).substringBefore(" ·") + " " + Fmt.time(item.timeMillis) + when (item) {
            is TimelineItem.Rec -> item.rec.file.durationMs.takeIf { it > 0 }?.let { " · " + Fmt.durationKo(it) } ?: ""
            is TimelineItem.Call -> item.call.durationSec.takeIf { it > 0 }?.let { " · " + Fmt.durationKo(it * 1000) } ?: ""
            else -> ""
        }
        val info = "$caseNo · $title · $sub" + if (item is TimelineItem.Rec) " · " + item.rec.file.displayName else ""
        OptionsDialog(
            title = title,
            subtitle = sub,
            onDismiss = { recordMenu = null },
            items = buildList {
                add(Option("특이사항 남기기", Ic.pen) {
                    recordMenu = null
                    onIssue(null, item.toSource())
                })
                add(Option("약속 추가", Ic.calendar) {
                    recordMenu = null
                    onAppt(null, item.number, item.timeMillis)
                })
                when (item) {
                    is TimelineItem.Sms -> add(Option("문자 보내기", Ic.msg) {
                        recordMenu = null
                        sendSms(ctx, item.number)
                    })
                    else -> add(Option("전화 걸기", Ic.phone) {
                        recordMenu = null
                        dial(ctx, item.number)
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
                    clipboard.setText(AnnotatedString(if (item is TimelineItem.Sms) item.sms.body else info))
                    Toast.makeText(ctx, "복사했어요", Toast.LENGTH_SHORT).show()
                })
            },
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
        if (value != null) {
            Text(value.toString(), style = ts(21f, W8, tracking = -0.02f, num = true), color = c.ink)
        } else {
            Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.ink3, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            }
        }
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

/** 어두운 머리 위의 반투명 글자 버튼 */
@Composable
fun GlassPill(text: String, icon: ImageVector, onClick: () -> Unit) {
    val shape = RoundedCornerShape(19.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .press(scale = 0.94f, onClick = onClick)
            .height(38.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), shape)
            .padding(start = 11.dp, end = 13.dp),
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = ts(13.5f, W8), color = Color.White)
    }
}

/** 지금 분류엔 없지만 다른 분류에 있으면 알려줌 */
internal fun elsewhere(counts: Map<Kind, Int?>, now: Kind): String {
    val other = counts.filter { (k, n) -> k != now && (n ?: 0) > 0 }.map { (k, n) -> "${k.label} ${n}건" }
    return if (other.isEmpty()) "" else "\n" + other.joinToString(" · ") + "에 있어요."
}
