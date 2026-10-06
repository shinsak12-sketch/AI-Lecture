@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.bosang.search.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bosang.search.core.PhoneNumbers
import com.bosang.search.core.ReminderRule
import com.bosang.search.core.RepairRule
import com.bosang.search.data.Appointment
import com.bosang.search.data.ApptKind
import com.bosang.search.data.Photos
import com.bosang.search.data.Repair
import com.bosang.search.data.Store
import com.bosang.search.remind.Reminders
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// ───────────────────────── 날짜 · 시각 글자 ─────────────────────────

private val fDate = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
private val fTime = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

internal fun dateLabel(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
    today -> "오늘 " + d.format(fDate)
    today.plusDays(1) -> "내일 " + d.format(fDate)
    today.plusDays(2) -> "모레 " + d.format(fDate)
    today.minusDays(1) -> "어제 " + d.format(fDate)
    else -> d.format(fDate)
}

internal fun timeLabel(t: LocalTime): String = t.format(fTime)

private fun dDay(d: LocalDate, today: LocalDate = LocalDate.now()): String {
    val n = ChronoUnit.DAYS.between(today, d)
    return when {
        n == 0L -> "오늘"
        n > 0 -> "D-$n"
        else -> "${-n}일 지남"
    }
}

private fun zone() = ZoneId.systemDefault()

// ───────────────────────── 약속 쓰기 ─────────────────────────

@Composable
fun ApptEditScreen(
    store: Store,
    apptId: String?,
    caseNo: String?,
    number: String?,
    callTime: Long?,
    onBack: () -> Unit,
) {
    val c = B.c
    val ctx = LocalContext.current
    StatusBarIcons(lightContent = false)
    val existing = remember(apptId) { apptId?.let { store.appt(it) } }
    val start = existing?.let { Instant.ofEpochMilli(it.at).atZone(zone()) }
    var kind by remember { mutableStateOf(existing?.kind ?: ApptKind.MEET) }
    var date by remember { mutableStateOf(start?.toLocalDate() ?: LocalDate.now().plusDays(1)) }
    var time by remember { mutableStateOf(start?.toLocalTime()?.withSecond(0)?.withNano(0) ?: LocalTime.of(10, 0)) }
    var who by remember { mutableStateOf(existing?.number ?: number) }
    var place by remember { mutableStateOf(existing?.place.orEmpty()) }
    var memo by remember { mutableStateOf(existing?.memo.orEmpty()) }
    val remind = remember { mutableStateListOf<Int>().apply { addAll(existing?.remind ?: listOf(60)) } }
    var done by remember { mutableStateOf(existing?.done ?: false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // 사건: 보고 있던 사건, 아니면 그 사람의 사건이 하나면 그것
    val caseOptions = remember(who) {
        (listOfNotNull(existing?.caseNo, caseNo) + who?.let { n -> store.linksForNumber(n).map { it.caseNo } }.orEmpty()).distinct()
    }
    var case by remember { mutableStateOf(existing?.caseNo ?: caseNo ?: caseOptions.singleOrNull()) }
    val people = remember(case, number) {
        val base = case?.let { store.linksForCase(it).map { l -> l.number to l.role } }.orEmpty()
        val extra = listOfNotNull(existing?.number, number).filter { n -> base.none { it.first == n } }.map { it to "상대" }
        extra + base
    }
    val at = date.atTime(time).atZone(zone()).toInstant().toEpochMilli()

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun save() {
        if (Build.VERSION.SDK_INT >= 33 && !Perms.granted(ctx, Manifest.permission.POST_NOTIFICATIONS)) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        store.saveAppt(
            Appointment(
                id = existing?.id ?: Photos.newId(),
                caseNo = case,
                number = who,
                kind = kind,
                at = at,
                place = place.trim(),
                memo = memo.trim(),
                remind = remind.sortedDescending(),
                done = done,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                callTime = existing?.callTime ?: callTime,
            ),
        )
        onBack()
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            EditorBar(
                heading = case ?: who?.let { store.displayName(it) } ?: "",
                title = if (existing == null) "약속 추가" else "약속",
                icon = Ic.calendar,
                onBack = onBack,
                onDelete = if (existing != null) ({ confirmDelete = true }) else null,
                onSave = { save() },
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
                // 한눈에: 날짜 · 시각
                BCard(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                        IconTile(Ic.calendar, c.brand, c.brandTint, 44.dp, 14.dp)
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(dateLabel(date), style = ts(17f, W8), color = c.ink)
                            Text(timeLabel(time) + " · " + kind.label, style = ts(14f, W6, num = true), color = c.ink2, modifier = Modifier.padding(top = 2.dp))
                        }
                        SmallTag(dDay(date), if (date.isBefore(LocalDate.now())) c.rec else c.brand, if (date.isBefore(LocalDate.now())) c.recTint else c.brandTint)
                    }
                }

                FieldLabel("무슨 약속")
                ChipFlow { ApptKind.entries.forEach { k -> SelectChip(k.label, kind == k, big = true) { kind = k } } }

                FieldLabel("날짜")
                ChipRow {
                    val today = LocalDate.now()
                    listOf("오늘" to today, "내일" to today.plusDays(1), "모레" to today.plusDays(2), "다음 주" to today.plusWeeks(1)).forEach { (l, d) ->
                        SelectChip(l, date == d, big = true) { date = d }
                    }
                    SelectChip(if (date.isAfter(LocalDate.now().plusDays(2)) || date.isBefore(LocalDate.now())) date.format(fDate) else "날짜 고르기", false, big = true) { pickDate = true }
                }

                FieldLabel("시각")
                ChipRow {
                    listOf(9, 10, 11, 13, 14, 15, 16, 17).forEach { h ->
                        val t = LocalTime.of(h, 0)
                        SelectChip(if (h < 12) "오전 $h" else "오후 ${if (h == 12) 12 else h - 12}", time == t, big = true) { time = t }
                    }
                    SelectChip(if (time.minute != 0 || time.hour !in listOf(9, 10, 11, 13, 14, 15, 16, 17)) timeLabel(time) else "직접", false, big = true) { pickTime = true }
                }

                if (people.isNotEmpty()) {
                    FieldLabel("누구와")
                    ChipRow {
                        people.forEach { (n, role) ->
                            val on = who == n
                            PersonPick(store.nameOf(n), n, store.displayName(n), role, on) { who = if (on) null else n }
                        }
                    }
                }
                if (caseOptions.size > 1) {
                    FieldLabel("사건")
                    ChipRow { caseOptions.forEach { cn -> SelectChip(cn, case == cn, big = true) { case = cn } } }
                }

                FieldLabel("장소")
                PlainField(place, { place = it }, "예: ○○공업사, 피해자 자택, 사무실")
                FieldLabel("메모")
                PlainField(memo, { memo = it }, "예: 견적서 원본 받기, 합의서 지참", singleLine = false)

                FieldLabel("알림", hint = "여러 개 고를 수 있어요")
                ChipFlow {
                    Appointment.REMIND_OPTIONS.forEach { m ->
                        val on = m in remind
                        SelectChip(ReminderRule.label(m), on, big = true) { if (on) remind.remove(m) else remind.add(m) }
                    }
                }
                if (remind.isNotEmpty() && !Reminders.exactAllowed(ctx)) {
                    Box(Modifier.padding(horizontal = 16.dp).press(scale = 0.98f) {
                        if (Build.VERSION.SDK_INT >= 31) {
                            runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + ctx.packageName))) }
                        }
                    }) { DiffNote("정확한 시각 알림이 꺼져 있어 몇 분 늦을 수 있어요. 눌러서 허용") }
                }
                if (existing != null) {
                    FieldLabel("상태")
                    ChipRow {
                        SelectChip("예정", !done, big = true) { done = false }
                        SelectChip("끝남", done, big = true) { done = true }
                    }
                }
            }
        }
    }

    if (pickDate) DatePick(date, onPick = { date = it; pickDate = false }, onDismiss = { pickDate = false })
    if (pickTime) TimePick(time, onPick = { time = it; pickTime = false }, onDismiss = { pickTime = false })
    if (confirmDelete && existing != null) {
        ConfirmDelete("이 약속을 지울까요?", onDismiss = { confirmDelete = false }) {
            store.deleteAppt(existing.id)
            onBack()
        }
    }
}

// ───────────────────────── 입고 등록 ─────────────────────────

@Composable
fun RepairEditScreen(store: Store, repairId: String?, caseNo: String, onBack: () -> Unit) {
    val c = B.c
    val ctx = LocalContext.current
    StatusBarIcons(lightContent = false)
    val existing = remember(repairId) { repairId?.let { store.repair(it) } }
    val shops = remember(caseNo) { store.linksForCase(caseNo).filter { it.role.contains("정비") || it.role.contains("공업") } }
    var shop by remember { mutableStateOf(existing?.shop ?: shops.firstOrNull()?.let { store.nameOf(it.number) }.orEmpty()) }
    var shopNumber by remember { mutableStateOf(existing?.shopNumber ?: shops.firstOrNull()?.number.orEmpty()) }
    var carNo by remember { mutableStateOf(existing?.carNo.orEmpty()) }
    var inDate by remember { mutableStateOf(existing?.inDate ?: LocalDate.now()) }
    var expectedOut by remember { mutableStateOf(existing?.expectedOut) }
    var limitDays by remember { mutableStateOf(existing?.limitDays ?: 7) }
    var outDate by remember { mutableStateOf(existing?.outDate) }
    var memo by remember { mutableStateOf(existing?.memo.orEmpty()) }
    var pick by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun save() {
        if (Build.VERSION.SDK_INT >= 33 && !Perms.granted(ctx, Manifest.permission.POST_NOTIFICATIONS)) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        store.saveRepair(
            Repair(
                id = existing?.id ?: Photos.newId(),
                caseNo = caseNo,
                shop = shop.trim().ifEmpty { "공업사" },
                shopNumber = PhoneNumbers.normalize(shopNumber).ifEmpty { null },
                carNo = carNo.trim(),
                inDate = inDate,
                expectedOut = expectedOut,
                limitDays = limitDays,
                outDate = outDate,
                memo = memo.trim(),
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            ),
        )
        onBack()
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            EditorBar(
                heading = caseNo,
                title = if (existing == null) "입고 등록" else "입고 차량",
                icon = Ic.car,
                onBack = onBack,
                onDelete = if (existing != null) ({ confirmDelete = true }) else null,
                onSave = { save() },
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
                val overdue = outDate == null && RepairRule.daysLeft(inDate, expectedOut, limitDays, today) < 0
                BCard(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                        IconTile(Ic.car, if (overdue) c.rec else c.ok, if (overdue) c.recTint else c.okTint, 44.dp, 14.dp)
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(RepairRule.label(inDate, expectedOut, limitDays, outDate, today), style = ts(16f, W8, num = true), color = if (overdue) c.rec else c.ink)
                            Text(
                                "출고 예정 " + dateLabel(RepairRule.dueDate(inDate, expectedOut, limitDays)),
                                style = ts(13.5f, W6, num = true),
                                color = c.ink2,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }

                FieldLabel("공업사")
                if (shops.isNotEmpty()) {
                    ChipRow {
                        shops.forEach { l ->
                            val name = store.nameOf(l.number) ?: PhoneNumbers.format(l.number)
                            PersonPick(store.nameOf(l.number), l.number, name, l.role, shopNumber == l.number) {
                                shop = name
                                shopNumber = l.number
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                PlainField(shop, { shop = it }, "공업사 이름")
                Spacer(Modifier.height(8.dp))
                PlainField(shopNumber, { shopNumber = it }, "공업사 전화 (선택)", keyboard = KeyboardType.Phone)
                FieldLabel("차량번호")
                PlainField(carNo, { carNo = it }, "예: 12가3456")

                FieldLabel("입고일")
                ChipRow {
                    listOf("오늘" to today, "어제" to today.minusDays(1), "그제" to today.minusDays(2)).forEach { (l, d) ->
                        SelectChip(l, inDate == d, big = true) { inDate = d }
                    }
                    SelectChip(if (inDate.isBefore(today.minusDays(2))) inDate.format(fDate) else "날짜 고르기", false, big = true) { pick = "in" }
                }

                FieldLabel("출고 예정", hint = "지나면 매일 아침 알려요")
                ChipRow {
                    Repair.LIMIT_OPTIONS.forEach { d ->
                        SelectChip("${d}일", expectedOut == null && limitDays == d, big = true) {
                            expectedOut = null
                            limitDays = d
                        }
                    }
                    SelectChip(expectedOut?.format(fDate) ?: "날짜로 정하기", expectedOut != null, big = true) { pick = "due" }
                }

                FieldLabel("메모")
                PlainField(memo, { memo = it }, "예: 부품 대기, 렌트 연장 확인", singleLine = false)

                if (existing != null) {
                    FieldLabel("출고")
                    ChipRow {
                        SelectChip("아직", outDate == null, big = true) { outDate = null }
                        SelectChip("오늘 출고", outDate == today, big = true) { outDate = today }
                        SelectChip(outDate?.takeIf { it != today }?.format(fDate) ?: "출고일 고르기", outDate != null && outDate != today, big = true) { pick = "out" }
                    }
                }
            }
        }
    }

    pick?.let { which ->
        val init = when (which) {
            "in" -> inDate
            "due" -> expectedOut ?: RepairRule.dueDate(inDate, null, limitDays)
            else -> outDate ?: today
        }
        DatePick(init, onPick = { d ->
            when (which) {
                "in" -> inDate = d
                "due" -> expectedOut = d
                else -> outDate = d
            }
            pick = null
        }, onDismiss = { pick = null })
    }
    if (confirmDelete && existing != null) {
        ConfirmDelete("이 입고 기록을 지울까요?", onDismiss = { confirmDelete = false }) {
            store.deleteRepair(existing.id)
            onBack()
        }
    }
}

// ───────────────────────── 목록 (사건 · 사람 · 홈) ─────────────────────────

/** 사건 · 사람 화면의 "약속 · 입고" 묶음 */
fun LazyListScope.planSection(
    store: Store,
    appts: List<Appointment>,
    repairs: List<Repair>,
    onAppt: (Appointment?) -> Unit,
    onRepair: ((Repair?) -> Unit)?,
) {
    val now = System.currentTimeMillis()
    // 끝나지 않은 약속 + 하루 안에 지난 약속, 안 끝난 입고 + 최근 출고 1건
    val shownAppts = appts.filter { !it.done && it.at > now - 24 * 3_600_000L }
    val shownRepairs = repairs.filter { it.open } + repairs.filter { !it.open }.take(1)
    item(key = "plan-head") {
        SectionHeader("약속 · 입고", (shownAppts.size + shownRepairs.count { it.open }).takeIf { it > 0 }) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                HeaderAction("약속", icon = Ic.plus) { onAppt(null) }
                if (onRepair != null) HeaderAction("입고", icon = Ic.plus) { onRepair(null) }
            }
        }
    }
    if (shownAppts.isEmpty() && shownRepairs.isEmpty()) {
        item(key = "plan-empty") {
            Text(
                if (onRepair != null) "통화로 잡은 약속, 공업사 입고일을 적어 두면 때맞춰 알려줘요" else "통화로 잡은 약속을 적어 두면 때맞춰 알려줘요",
                style = ts(13f, W6),
                color = B.c.ink3,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
        }
        return
    }
    val rows: List<Any> = shownRepairs + shownAppts
    itemsIndexed(rows, key = { _, r -> if (r is Repair) "pr-${r.id}" else "pa-${(r as Appointment).id}" }) { i, r ->
        CardSegment(first = i == 0, last = i == rows.lastIndex, modifier = Modifier.padding(horizontal = 16.dp)) {
            when (r) {
                is Repair -> RepairLine(r) { onRepair?.invoke(r) }
                is Appointment -> ApptLine(store, r, showCase = false) { onAppt(r) }
            }
        }
    }
}

/** 홈: 다가오는 약속 (7일) + 출고 예정 · 지연 차량 */
fun LazyListScope.homePlanSection(
    store: Store,
    onAppt: (Appointment) -> Unit,
    onCase: (String) -> Unit,
) {
    val now = System.currentTimeMillis()
    val appts = store.upcomingAppts(now - 3_600_000L).filter { it.at < now + 7 * 86_400_000L }.take(6)
    val today = LocalDate.now()
    val repairs = store.openRepairs().sortedBy { RepairRule.daysLeft(it.inDate, it.expectedOut, it.limitDays, today) }.take(6)
    if (appts.isEmpty() && repairs.isEmpty()) return
    item(key = "home-plan-head") { SectionHeader("다가오는 약속 · 입고", appts.size + repairs.size, modifier = Modifier.padding(top = 6.dp)) }
    val rows: List<Any> = appts + repairs
    itemsIndexed(rows, key = { _, r -> if (r is Repair) "hr-${r.id}" else "ha-${(r as Appointment).id}" }) { i, r ->
        CardSegment(first = i == 0, last = i == rows.lastIndex, modifier = Modifier.padding(horizontal = 16.dp)) {
            when (r) {
                is Repair -> RepairLine(r, showCase = true) { onCase(r.caseNo) }
                is Appointment -> ApptLine(store, r, showCase = true) { onAppt(r) }
            }
        }
    }
}

@Composable
private fun ApptLine(store: Store, a: Appointment, showCase: Boolean, onClick: () -> Unit) {
    val c = B.c
    val t = Instant.ofEpochMilli(a.at).atZone(zone())
    val past = a.at < System.currentTimeMillis()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().press(scale = 0.98f, onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        // 날짜 칸
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(if (past) c.chip else c.brandTint).padding(top = 5.dp),
        ) {
            Text("${t.monthValue}월", style = ts(10.5f, W7, num = true), color = if (past) c.ink3 else c.brand)
            Text("${t.dayOfMonth}", style = ts(17f, W8, num = true), color = if (past) c.ink3 else c.brand)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                a.kind.label + " · " + timeLabel(t.toLocalTime()),
                style = ts(15f, W8, num = true),
                color = c.ink,
                maxLines = 1,
            )
            val sub = listOfNotNull(
                a.number?.let { store.displayName(it) },
                if (showCase) a.caseNo else null,
                a.place.ifBlank { null },
            ).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = ts(13f, W4, num = true), color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        val day = t.toLocalDate()
        SmallTag(if (past) "지남" else dDay(day), if (past) c.ink3 else if (day == LocalDate.now()) c.rec else c.brand, if (past) c.chip else if (day == LocalDate.now()) c.recTint else c.brandTint)
    }
}

@Composable
private fun RepairLine(r: Repair, showCase: Boolean = false, onClick: () -> Unit) {
    val c = B.c
    val today = LocalDate.now()
    val left = RepairRule.daysLeft(r.inDate, r.expectedOut, r.limitDays, today)
    val (fg, bg) = when {
        !r.open -> c.ink3 to c.chip
        left < 0 -> c.rec to c.recTint
        left <= 1 -> c.warn to c.warnTint
        else -> c.ok to c.okTint
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().press(scale = 0.98f, onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        IconTile(Ic.car, fg, bg, 46.dp, 14.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(r.shop, r.carNo.ifBlank { null }).joinToString(" · "),
                style = ts(15f, W8, num = true),
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(if (showCase) r.caseNo else null, RepairRule.label(r.inDate, r.expectedOut, r.limitDays, r.outDate, today)).joinToString(" · "),
                style = ts(13f, W6, num = true),
                color = if (r.open && left < 0) c.rec else c.ink2,
                maxLines = 1,
            )
        }
    }
}

// ───────────────────────── 부품 ─────────────────────────

@Composable
private fun EditorBar(heading: String, title: String, icon: ImageVector, onBack: () -> Unit, onDelete: (() -> Unit)?, onSave: () -> Unit) {
    val c = B.c
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp),
    ) {
        CircleBtn(Ic.back, "뒤로", onClick = onBack)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(heading, style = ts(12f, W8, tracking = 0.04f, num = true), color = c.ink3, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Icon(icon, null, tint = c.brand, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(title, style = ts(17f, W8), color = c.ink)
            }
        }
        if (onDelete != null) {
            CircleBtn(Ic.trash, "삭제", onClick = onDelete)
            Spacer(Modifier.width(8.dp))
        }
        GradientButton("저장", height = 40.dp, radius = 13.dp, onClick = onSave)
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
    ) { content() }
}

@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) { content() }
}

@Composable
private fun PlainField(value: String, onValue: (String) -> Unit, placeholder: String, singleLine: Boolean = true, keyboard: KeyboardType = KeyboardType.Text) {
    val c = B.c
    BCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 13.dp).then(if (singleLine) Modifier else Modifier.heightIn(min = 64.dp))) {
            if (value.isEmpty()) Text(placeholder, style = ts(15f, W4), color = c.ink3)
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = singleLine,
                textStyle = ts(15f, W6, lineHeight = 1.5f).copy(color = c.ink),
                cursorBrush = SolidColor(c.brand),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DatePick(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val c = B.c
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text("확인", style = ts(15f, W8), color = c.brand) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", style = ts(15f, W7), color = c.ink2) } },
    ) { DatePicker(state = state, showModeToggle = false) }
}

@Composable
private fun TimePick(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val c = B.c
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        shape = RoundedCornerShape(26.dp),
        title = { Text("시각", style = ts(19f, W8), color = c.ink) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("확인", style = ts(15f, W8), color = c.brand) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", style = ts(15f, W7), color = c.ink2) } },
    )
}

@Composable
private fun ConfirmDelete(title: String, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val c = B.c
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        shape = RoundedCornerShape(26.dp),
        title = { Text(title, style = ts(19f, W8), color = c.ink) },
        confirmButton = { TextButton(onClick = onDelete) { Text("지우기", style = ts(15f, W8), color = c.rec) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", style = ts(15f, W7), color = c.ink2) } },
    )
}
