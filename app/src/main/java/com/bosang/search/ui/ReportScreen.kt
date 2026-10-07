@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.bosang.search.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bosang.search.data.CasePhoto
import com.bosang.search.data.PhotoReport
import com.bosang.search.data.PhotoTags
import com.bosang.search.data.Photos
import com.bosang.search.data.Store
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** 사진대지 만들기: 사진 고르기 · 배치 · 미리 보기 · 보내기 */
@Composable
fun ReportScreen(store: Store, caseNo: String, preselect: List<String>, onBack: () -> Unit) {
    val c = B.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    StatusBarIcons(lightContent = false)
    val ver = store.version.intValue
    val all = remember(ver, caseNo) { PhotoReport.order(store.photosForCase(caseNo)) }
    val selected = remember { mutableStateListOf<String>().apply { addAll(preselect.ifEmpty { all.map { it.id } }) } }
    var title by remember { mutableStateOf("차량 사진 자료") }
    var layout by remember { mutableStateOf(PhotoReport.Layout.TWO) }
    var showTime by remember { mutableStateOf(true) }
    var showPlace by remember { mutableStateOf(all.any { !it.place.isNullOrBlank() }) }
    var showMeasure by remember { mutableStateOf(true) }
    var author by remember { mutableStateOf(store.author()) }
    var file by remember { mutableStateOf<File?>(null) }
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var building by remember { mutableStateOf(false) }
    var captionFor by remember { mutableStateOf<CasePhoto?>(null) }

    val chosen = all.filter { it.id in selected }
    val opts = PhotoReport.Options(title.ifBlank { "차량 사진 자료" }, layout, showTime, showPlace, showMeasure, author)
    // 바꿀 때마다 잠깐 뒤 미리 보기 다시
    LaunchedEffect(chosen.map { it.id to it.caption }, opts) {
        if (chosen.isEmpty()) {
            pages = emptyList()
            file = null
            return@LaunchedEffect
        }
        delay(450)
        building = true
        val f = PhotoReport.build(ctx, store, caseNo, chosen, opts)
        file = f
        pages = PhotoReport.render(f, 1000)
        building = false
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp)) {
                CircleBtn(Ic.back, "뒤로", onClick = onBack)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(caseNo, style = ts(12f, W8, num = true), color = c.ink3)
                    Text("사진대지", style = ts(18f, W8), color = c.ink)
                }
                GradientButton("보내기", icon = Ic.share, height = 40.dp, radius = 13.dp, enabled = file != null && !building) {
                    if (author != store.author()) store.setAuthor(author)
                    file?.let { PhotoReport.share(ctx, it) }
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
                FieldLabel("제목")
                ReportField(title, { title = it }, "차량 사진 자료")
                FieldLabel("한 쪽에")
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                    PhotoReport.Layout.entries.forEach { l -> SelectChip(l.label, layout == l, big = true) { layout = l } }
                }
                FieldLabel("사진 아래 표시")
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    SelectChip("촬영 일시", showTime, big = true) { showTime = !showTime }
                    SelectChip("촬영 위치", showPlace, big = true) { showPlace = !showPlace }
                    SelectChip("측정값", showMeasure, big = true) { showMeasure = !showMeasure }
                }
                FieldLabel("작성자", hint = "다음에도 기억해요")
                ReportField(author, { author = it }, "예: ○○손해보험 보상팀 홍길동")

                FieldLabel("사진 ${selected.size}/${all.size}장", hint = "길게 누르면 설명 쓰기")
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
                    SelectChip("전체", selected.size == all.size, big = false) {
                        selected.clear()
                        selected.addAll(all.map { it.id })
                    }
                    PhotoTags.VEHICLES.forEach { (k, label) ->
                        if (all.any { it.vehicle == k }) {
                            SelectChip(label + "만", false) {
                                selected.clear()
                                selected.addAll(all.filter { it.vehicle == k }.map { it.id })
                            }
                        }
                    }
                    SelectChip("비우기", false) { selected.clear() }
                }
                all.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 5.dp)) {
                        row.forEach { p ->
                            val on = p.id in selected
                            val n = chosen.indexOfFirst { it.id == p.id }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .press(scale = 0.95f, onLongClick = { captionFor = p }) { if (on) selected.remove(p.id) else selected.add(p.id) }
                                    .then(if (on) Modifier.border(2.5.dp, c.brand, RoundedCornerShape(10.dp)) else Modifier),
                            ) {
                                Thumb(Photos.clean(p), Modifier.fillMaxSize(), px = 240)
                                if (!on) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.55f)))
                                if (on && n >= 0) {
                                    Text(
                                        PhotoReport.number(n),
                                        style = ts(11f, W8),
                                        color = Color.White,
                                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp).clip(CircleShape).background(c.brand).padding(horizontal = 5.dp),
                                    )
                                }
                                if (!p.caption.isNullOrBlank()) {
                                    Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(16.dp).clip(RoundedCornerShape(5.dp)).background(Color(0x990C1222)), contentAlignment = Alignment.Center) {
                                        Icon(Ic.pen, null, tint = Color.White, modifier = Modifier.size(9.dp))
                                    }
                                }
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                FieldLabel("미리 보기", hint = if (building) "만드는 중" else if (pages.isNotEmpty()) "${pages.size}쪽" else null)
                if (chosen.isEmpty()) {
                    Text("사진을 골라 주세요", style = ts(13.5f, W6), color = c.ink3, modifier = Modifier.padding(horizontal = 22.dp))
                } else if (pages.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = c.brand, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                }
                pages.forEach { bmp ->
                    BCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), radius = 6.dp, level = Depth.LIFT) {
                        Image(
                            bmp.asImageBitmap(),
                            null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().aspectRatio(bmp.width / bmp.height.toFloat()),
                        )
                    }
                }
            }
        }
    }

    captionFor?.let { p ->
        var v by remember(p.id) { mutableStateOf(p.caption.orEmpty()) }
        AlertDialog(
            onDismissRequest = { captionFor = null },
            containerColor = c.card,
            shape = RoundedCornerShape(26.dp),
            title = { Text("사진 설명", style = ts(19f, W8), color = c.ink) },
            text = {
                Column {
                    Thumb(Photos.clean(p), Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)), px = 600)
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.chip).padding(12.dp)) {
                        if (v.isEmpty()) Text("예: 운전석 앞문 찌그러짐", style = ts(15f), color = c.ink3)
                        BasicTextField(v, { v = it.take(40) }, singleLine = true, textStyle = ts(15f, W7).copy(color = c.ink), cursorBrush = SolidColor(c.brand), modifier = Modifier.fillMaxWidth())
                    }
                    Text("비워 두면 차량 · 단계가 제목으로 들어가요", style = ts(12f, W6), color = c.ink3, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    store.updatePhoto(p.copy(caption = v.trim().ifEmpty { null }))
                    captionFor = null
                }) { Text("저장", style = ts(15f, W8), color = c.brand) }
            },
            dismissButton = { TextButton(onClick = { captionFor = null }) { Text("취소", style = ts(15f, W7), color = c.ink2) } },
        )
    }
}

@Composable
private fun ReportField(value: String, onValue: (String) -> Unit, placeholder: String) {
    val c = B.c
    BCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            if (value.isEmpty()) Text(placeholder, style = ts(15f), color = c.ink3)
            BasicTextField(value, onValue, singleLine = true, textStyle = ts(15f, W6).copy(color = c.ink), cursorBrush = SolidColor(c.brand), modifier = Modifier.fillMaxWidth())
        }
    }
}
