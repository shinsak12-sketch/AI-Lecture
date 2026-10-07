package com.bosang.search.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import com.bosang.search.data.AlbumPhoto
import com.bosang.search.data.CasePhoto
import com.bosang.search.data.PhoneData
import com.bosang.search.data.Photos
import androidx.compose.foundation.border
import com.bosang.search.data.PhotoTags
import com.bosang.search.data.Records
import com.bosang.search.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** 사진 미리보기 (작게 읽어 기억해 둠) */
@Composable
fun Thumb(ref: String, modifier: Modifier = Modifier, px: Int = 360) {
    val ctx = LocalContext.current
    var bmp by remember(ref) { mutableStateOf(Photos.cached(ref, px)) }
    LaunchedEffect(ref) { if (bmp == null) bmp = Photos.load(ctx, ref, px) }
    Box(modifier.background(B.c.chip2)) {
        bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

// ───────────────────────── 사진+ ─────────────────────────

@Composable
fun PhotoAddDialog(caseNo: String, onCamera: () -> Unit, onAlbum: () -> Unit, onDismiss: () -> Unit) {
    val c = B.c
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        BCard(radius = 26.dp, level = Depth.FLOAT, color = c.bg, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("사진 추가", style = ts(20f, W8), color = c.ink, modifier = Modifier.weight(1f))
                    Text(caseNo, style = ts(13f, W8, num = true), color = c.ink3)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 14.dp)) {
                    AddTile(Ic.camera, c.brand, c.brandTint, "보상 카메라", "차량 · 단계가 붙어서 바로 이 사건에 들어가요", Modifier.weight(1f), onCamera)
                    AddTile(Ic.list, c.ok, c.okTint, "앨범에서 분류", "이 사건 통화 앞뒤 사진부터 보여드려요", Modifier.weight(1f), onAlbum)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp, start = 4.dp)) {
                    Icon(Ic.msg, null, tint = c.brand, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("문자로 받은 사진은 따로 할 것 없이 자동으로 들어와요", style = ts(12.5f, W6), color = c.ink2)
                }
                SoftButton("닫기", modifier = Modifier.fillMaxWidth().padding(top = 14.dp), onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun AddTile(icon: ImageVector, fg: Color, bg: Color, title: String, body: String, modifier: Modifier, onClick: () -> Unit) {
    val c = B.c
    BCard(modifier.press(scale = 0.96f, onClick = onClick), radius = 20.dp) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            IconTile(icon, fg, bg, 44.dp, 14.dp)
            Text(title, style = ts(15f, W8), color = c.ink, maxLines = 1, modifier = Modifier.padding(top = 10.dp))
            Text(body, style = ts(12.5f, W4, lineHeight = 1.45f), color = c.ink2, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ───────────────────────── 사진 탭 ─────────────────────────

enum class PhotoView(val label: String) { VEHICLE("차량별"), STAGE("단계별"), SOURCE("출처별") }

/** 사진 탭에서 쓰는 것들 (사건 화면이 상태를 가짐) */
class PhotoTabState(
    val view: PhotoView,
    val onView: (PhotoView) -> Unit,
    val selected: Set<String>,
    val onSelect: (CasePhoto) -> Unit,
    val carNo: (String) -> String?,
    val onCarNo: (String) -> Unit,
    val onCamera: () -> Unit,
    val onAlbum: () -> Unit,
    val onReport: () -> Unit,
)

fun LazyListScope.photoSection(
    photos: List<CasePhoto>,
    nameOf: (String) -> String,
    tab: PhotoTabState,
    onOpen: (CasePhoto) -> Unit,
) {
    item(key = "ph-actions") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            PhotoAction(Ic.camera, "보상 카메라", true, Modifier.weight(1f), tab.onCamera)
            PhotoAction(Ic.list, "앨범에서", false, Modifier.weight(1f), tab.onAlbum)
            PhotoAction(Ic.share, "사진대지", false, Modifier.weight(1f), tab.onReport)
        }
    }
    if (photos.isEmpty()) {
        item(key = "ph-empty") {
            EmptyCard(
                icon = Ic.image,
                title = "사진이 없어요",
                body = "보상 카메라로 찍으면 차량 · 단계가 붙어서 들어와요.\n문자로 받은 사진은 자동으로 들어와요.",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
        }
        return
    }
    item(key = "ph-views") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        ) {
            PhotoView.entries.forEach { v -> FilterPill(v.label, tab.view == v) { tab.onView(v) } }
            Spacer(Modifier.weight(1f))
            Text(if (tab.selected.isEmpty()) "길게 눌러 여러 장 고르기" else "${tab.selected.size}장 고름", style = ts(11.5f, W7), color = B.c.ink3)
        }
    }
    val groups: List<Triple<String, String, List<CasePhoto>>> = when (tab.view) {
        PhotoView.VEHICLE -> (PhotoTags.VEHICLES.map { it.first } + "").map { k ->
            val list = photos.filter { (it.vehicle ?: "") == k }
            Triple(k, if (k.isEmpty()) "차량 미분류" else listOfNotNull(PhotoTags.vehicleLabel(k), tab.carNo(k)).joinToString(" · "), list)
        }
        PhotoView.STAGE -> (PhotoTags.STAGES.map { it.first } + "").map { k ->
            Triple(k, if (k.isEmpty()) "단계 미분류" else PhotoTags.stageLabel(k) ?: k, photos.filter { (it.stage ?: "") == k })
        }
        PhotoView.SOURCE -> listOf("camera" to "촬영", "mms" to "문자로 받음", "album" to "앨범에서").map { (k, t) ->
            Triple(k, t, photos.filter { it.source == k })
        }
    }.filter { it.third.isNotEmpty() }
    groups.forEach { (key, title, list) ->
        item(key = "ph-h-${tab.view}-$key") {
            val c = B.c
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)) {
                Text(title, style = ts(13.5f, W8, num = true), color = c.ink)
                Spacer(Modifier.width(6.dp))
                Text("${list.size}장", style = ts(12f, W7, num = true), color = c.ink3)
                Spacer(Modifier.weight(1f))
                if (tab.view == PhotoView.VEHICLE && key.isNotEmpty()) {
                    HeaderAction(if (tab.carNo(key) == null) "차량번호" else "번호 수정", icon = Ic.edit) { tab.onCarNo(key) }
                }
            }
        }
        list.sortedByDescending { it.takenAt }.chunked(3).forEachIndexed { i, row ->
            item(key = "ph-${tab.view}-$key-$i") {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 5.dp)) {
                    row.forEach { p ->
                        PhotoCell(
                            p,
                            nameOf,
                            tab.view,
                            selected = p.id in tab.selected,
                            selecting = tab.selected.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            onLong = { tab.onSelect(p) },
                        ) { if (tab.selected.isNotEmpty()) tab.onSelect(p) else onOpen(p) }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PhotoAction(icon: ImageVector, text: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = B.c
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .press(scale = 0.96f, onClick = onClick)
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) c.brand else c.card),
    ) {
        Icon(icon, null, tint = if (primary) Color.White else c.ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = ts(13.5f, W8), color = if (primary) Color.White else c.ink, maxLines = 1)
    }
}

@Composable
private fun PhotoCell(
    p: CasePhoto,
    nameOf: (String) -> String,
    view: PhotoView,
    selected: Boolean,
    selecting: Boolean,
    modifier: Modifier,
    onLong: () -> Unit,
    onClick: () -> Unit,
) {
    val c = B.c
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .press(scale = 0.96f, onLongClick = onLong, onClick = onClick)
            .then(if (selected) Modifier.border(3.dp, c.brand, RoundedCornerShape(12.dp)) else Modifier),
    ) {
        Thumb(Photos.shown(p), Modifier.fillMaxSize())
        // 보이는 묶음이 아닌 쪽 꼬리표를 보여줌
        val label = listOfNotNull(
            if (view != PhotoView.STAGE) PhotoTags.stageLabel(p.stage) else PhotoTags.vehicleLabel(p.vehicle),
            p.kind,
        ).joinToString(" · ").ifEmpty { p.from?.let(nameOf) }
        if (!label.isNullOrEmpty()) {
            Text(
                label,
                style = ts(10.5f, W8),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x8C0C1222))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        if (!p.measure.isNullOrBlank()) {
            Text(
                p.measure,
                style = ts(9.5f, W8, num = true),
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xE6FFD43B)).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        if (selecting) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (selected) c.brand else Color(0x660C1222))
                    .border(1.5.dp, Color.White, CircleShape),
            ) { if (selected) Icon(Ic.check, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
        } else if (p.marked != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x990C1222)),
            ) { Icon(Ic.pen, null, tint = Color.White, modifier = Modifier.size(10.dp)) }
        }
    }
}

// ───────────────────────── 앨범에서 분류 ─────────────────────────

private class PhotoGroup(val photos: List<AlbumPhoto>, val reason: String?, val distance: Long, val kakao: Boolean) {
    val start get() = photos.first().takenAt
    val end get() = photos.last().takenAt
}

@Composable
fun AlbumClassifyScreen(store: Store, data: PhoneData, caseNo: String, onBack: () -> Unit) {
    val c = B.c
    val ctx = LocalContext.current
    StatusBarIcons(lightContent = false)
    var groups by remember { mutableStateOf<List<PhotoGroup>?>(null) }
    val selected = remember { mutableStateMapOf<Long, Boolean>() }
    val kinds = remember { mutableStateMapOf<Int, String>() }

    LaunchedEffect(caseNo) {
        val links = store.linksForCase(caseNo)
        val numbers = links.map { it.number }.toSet()
        val calls = Records.calls(numbers, data)
        val since = listOfNotNull(links.minOfOrNull { it.createdAt }, calls.minOfOrNull { it.timeMillis }).minOrNull()
            ?.minus(7L * 24 * 3600 * 1000)
            ?.coerceAtLeast(System.currentTimeMillis() - 180L * 24 * 3600 * 1000)
            ?: (System.currentTimeMillis() - 60L * 24 * 3600 * 1000)
        val taken = store.photosForCase(caseNo).map { it.uri }.toSet()
        val album = Photos.album(ctx, since).filter { it.uri.toString() !in taken }.sortedBy { it.takenAt }
        groups = withContext(Dispatchers.Default) {
            val out = ArrayList<List<AlbumPhoto>>()
            var cur = ArrayList<AlbumPhoto>()
            album.forEach { p ->
                if (cur.isNotEmpty() && p.takenAt - cur.last().takenAt > 10 * 60_000) {
                    out.add(cur)
                    cur = ArrayList()
                }
                cur.add(p)
            }
            if (cur.isNotEmpty()) out.add(cur)
            out.map { g ->
                val mid = g.first().takenAt
                val near = calls.minByOrNull { abs(it.timeMillis - mid) }
                val dist = near?.let { abs(it.timeMillis - mid) } ?: Long.MAX_VALUE
                val reason = if (near != null && dist <= 3 * 3600_000L) {
                    val mins = (dist / 60_000).coerceAtLeast(1)
                    val after = mid >= near.timeMillis
                    withJosa(store.displayName(near.number), "과", "와") + " 통화 ${mins}분 " + (if (after) "뒤" else "전") + "에 찍음"
                } else null
                val kakao = g.count { it.bucket?.contains("kakao", ignoreCase = true) == true } * 2 > g.size
                PhotoGroup(g, reason, dist, kakao)
            }.sortedWith(compareBy<PhotoGroup> { if (it.reason != null) 0 else 1 }.thenBy { if (it.reason != null) it.distance else -it.start })
        }
    }

    val count = selected.count { it.value }
    Box(Modifier.fillMaxSize().background(c.bg)) {
        LazyColumn(contentPadding = PaddingValues(bottom = 130.dp), modifier = Modifier.fillMaxSize()) {
            item(key = "top") {
                Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("앨범에서 분류", style = ts(26f, W8, tracking = -0.035f), color = c.ink, modifier = Modifier.weight(1f))
                        Text("취소", style = ts(15f, W7), color = c.brand, modifier = Modifier.press(onClick = onBack).padding(8.dp))
                    }
                    Text("$caseNo · 이 사건 통화와 가까운 사진부터", style = ts(13f, W6, num = true), color = c.ink2)
                }
            }
            val gs = groups
            if (gs == null) {
                item(key = "loading") { Loading("사진 정리하는 중") }
            } else if (gs.isEmpty()) {
                item(key = "none") {
                    EmptyCard(
                        icon = Ic.image,
                        title = "넣을 사진이 없어요",
                        body = "이 사건 무렵 찍거나 받은 사진을 찾지 못했어요.\n사진 접근을 '모두 허용'으로 바꾸면 더 보일 수 있어요.",
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                gs.forEachIndexed { gi, g ->
                    item(key = "g-$gi") {
                        GroupCard(g, gi, selected, kinds)
                    }
                }
            }
        }

        if (count > 0) {
            FloatingBar(Modifier.align(Alignment.BottomCenter)) {
                Column(Modifier.weight(1f)) {
                    Text("${count}장 선택", style = ts(15f, W8, num = true), color = c.ink)
                    Text("종류는 묶음마다 고를 수 있어요", style = ts(12f, W4), color = c.ink2)
                }
                GradientButton("사건에 넣기", icon = Ic.plusThin, height = 50.dp, radius = 16.dp) {
                    val now = System.currentTimeMillis()
                    val list = groups.orEmpty().flatMapIndexed { gi, g ->
                        g.photos.filter { selected[it.id] == true }.map { p ->
                            CasePhoto(
                                id = "alb_${p.id}_$caseNo",
                                caseNo = caseNo,
                                uri = p.uri.toString(),
                                source = "album",
                                kind = kinds[gi],
                                takenAt = p.takenAt,
                                addedAt = now,
                            )
                        }
                    }
                    store.addPhotos(list)
                    Toast.makeText(ctx, "${list.size}장을 넣었어요", Toast.LENGTH_SHORT).show()
                    onBack()
                }
            }
        }
    }
}

@Composable
private fun GroupCard(g: PhotoGroup, gi: Int, selected: MutableMap<Long, Boolean>, kinds: MutableMap<Int, String>) {
    val c = B.c
    val allOn = g.photos.all { selected[it.id] == true }
    BCard(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp).fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val title = Fmt.dayLabel(g.start).substringBefore(" ·") + " " + Fmt.time(g.start) +
                    (if (g.end - g.start >= 60_000) " ~ " + Fmt.time(g.end) else "") +
                    (if (g.kakao) " · 카톡" else "")
                Text(title, style = ts(14.5f, W8, num = true), color = c.ink, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                Text("${g.photos.size}장", style = ts(12f, W7, num = true), color = c.ink3)
                Spacer(Modifier.weight(1f))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .press(scale = 0.9f) { g.photos.forEach { selected[it.id] = !allOn } }
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (allOn) c.brand else c.chip2),
                ) { if (allOn) Icon(Ic.check, "모두 선택", tint = Color.White, modifier = Modifier.size(12.dp)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Icon(if (g.reason != null) Ic.phone else Ic.clock, null, tint = if (g.reason != null) c.brand else c.ink3, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(g.reason ?: "근처에 이 사건 통화 없음", style = ts(12f, W7), color = if (g.reason != null) c.brand else c.ink3)
            }
            g.photos.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    row.forEach { p ->
                        val on = selected[p.id] == true
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .press(scale = 0.95f) { selected[p.id] = !on },
                        ) {
                            Thumb(p.uri.toString(), Modifier.fillMaxSize(), px = 240)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (on) c.brand else Color(0x66FFFFFF)),
                            ) { if (on) Icon(Ic.check, null, tint = Color.White, modifier = Modifier.size(10.dp)) }
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (g.photos.any { selected[it.id] == true }) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                ) {
                    CasePhoto.KINDS.forEach { k ->
                        val on = kinds[gi] == k
                        Text(
                            k,
                            style = ts(12.5f, W7),
                            color = if (on) c.card else c.ink2,
                            modifier = Modifier
                                .press(scale = 0.94f) { if (on) kinds.remove(gi) else kinds[gi] = k }
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (on) c.ink else c.chip)
                                .padding(horizontal = 11.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────── 크게 보기 ─────────────────────────

/** photoId 가 있으면 사건 사진 (종류 · 그리기 · 빼기), 없으면 문자 사진 보기만 */
@Composable
fun PhotoViewScreen(store: Store, ref: String, photoId: String?, onBack: () -> Unit) {
    val c = B.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    StatusBarIcons(lightContent = true)
    val ver = store.version.intValue
    val photo = remember(ver, photoId) { photoId?.let { store.photo(it) } }
    val shownRef = photo?.let { Photos.shown(it) } ?: ref
    var bmp by remember(shownRef) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(shownRef) { mutableStateOf(false) }
    LaunchedEffect(shownRef) {
        bmp = Photos.loadFull(ctx, shownRef)
        failed = bmp == null
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var drawing by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color(0xFF07090F))) {
        val b = bmp
        if (b == null && failed) {
            Text(
                "사진을 열 수 없어요.\n문자가 지워졌거나 사진이 옮겨졌을 수 있어요.",
                style = ts(14f, W6, lineHeight = 1.5f).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        } else if (b == null) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center).size(26.dp))
        } else {
            Image(
                b.asImageBitmap(),
                null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            GlassCircle(Ic.back, "뒤로", onClick = onBack)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    when (photo?.source) {
                        "mms" -> "문자로 받은 사진"
                        "camera" -> "촬영한 사진"
                        "album" -> "앨범 사진"
                        else -> "문자 사진"
                    },
                    style = ts(15f, W8),
                    color = Color.White,
                )
                val t = photo?.takenAt
                if (t != null) Text(Fmt.dayLabel(t).substringBefore(" ·") + " " + Fmt.time(t), style = ts(12f, W6, num = true), color = Color.White.copy(alpha = 0.6f))
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xD91E222E))
                .padding(12.dp),
        ) {
            if (photo != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    CasePhoto.KINDS.forEach { k ->
                        val on = photo.kind == k
                        Text(
                            k,
                            style = ts(13f, W7),
                            color = if (on) Color(0xFF0C1222) else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier
                                .press(scale = 0.94f) { store.updatePhoto(photo.copy(kind = if (on) null else k)) }
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (on) Color.White else Color.White.copy(alpha = 0.12f))
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                if (photo != null) ViewAction(Ic.pen, "그리기") { drawing = true }
                ViewAction(Ic.share, "공유") { Photos.share(ctx, Photos.resolve(ctx, shownRef), "사진.jpg", "image/jpeg") }
                if (photo != null) ViewAction(Ic.trash, "사건에서 빼기") { confirmRemove = true }
            }
        }
        if (drawing && photo != null) {
            DrawScreen(
                backgroundRef = shownRef,
                onCancel = { drawing = false },
                onDone = { out ->
                    scope.launch {
                        val newRef = withContext(Dispatchers.IO) { Photos.saveBitmap(ctx, out, "mark") }
                        Photos.deleteFile(ctx, photo.marked)
                        store.updatePhoto(photo.copy(marked = newRef))
                        drawing = false
                    }
                },
            )
        }
    }
    BackHandler(enabled = drawing) { drawing = false }

    if (confirmRemove && photo != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            containerColor = c.card,
            shape = RoundedCornerShape(26.dp),
            title = { Text("사건에서 빼기", style = ts(19f, W8), color = c.ink) },
            text = {
                Text(
                    if (photo.source == "album") "이 사건 사진에서만 빠지고, 앨범 원본은 그대로예요." else "앱에 보관한 이 사진을 지워요.",
                    style = ts(14.5f, W4, lineHeight = 1.5f),
                    color = c.ink2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    Photos.deleteFile(ctx, photo.marked)
                    if (photo.source == "camera") Photos.deleteFile(ctx, photo.uri)
                    Photos.deleteFile(ctx, photo.stamped)
                    store.removePhoto(photo.id)
                    onBack()
                }) { Text("빼기", style = ts(15f, W8), color = c.rec) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("취소", style = ts(15f, W7), color = c.ink2) } },
        )
    }
}

@Composable
private fun ViewAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.press(scale = 0.92f, onClick = onClick).padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = ts(12f, W7), color = Color.White.copy(alpha = 0.8f))
    }
}
