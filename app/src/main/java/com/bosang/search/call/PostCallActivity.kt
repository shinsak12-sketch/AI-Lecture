package com.bosang.search.call

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.CallLog
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bosang.search.MainActivity
import com.bosang.search.core.CaseNumber
import com.bosang.search.core.PhoneNumbers
import com.bosang.search.data.IssueKind
import com.bosang.search.data.Store
import com.bosang.search.ui.Avatar
import com.bosang.search.ui.B
import com.bosang.search.ui.BCard
import com.bosang.search.ui.BosangTheme
import com.bosang.search.ui.CloseCircle
import com.bosang.search.ui.Depth
import com.bosang.search.ui.Fmt
import com.bosang.search.ui.GradientButton
import com.bosang.search.ui.Ic
import com.bosang.search.ui.OtpField
import com.bosang.search.ui.RoleRow
import com.bosang.search.ui.SheetLabel
import com.bosang.search.ui.SoftButton
import com.bosang.search.ui.W4
import com.bosang.search.ui.W7
import com.bosang.search.ui.W8
import com.bosang.search.ui.YearChip
import com.bosang.search.ui.kindColors
import com.bosang.search.ui.kindIcon
import com.bosang.search.ui.press
import com.bosang.search.ui.roleReady
import com.bosang.search.ui.ts
import java.time.LocalDate

/**
 * 통화 끝난 뒤 창에서 [예]를 누르면 뜨는 작은 창.
 * 사고번호 · 관계를 정해 사건에 연결하고, 특이사항이 있었으면 구분을 골라 바로 적으러 간다.
 */
class PostCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val number = intent.getStringExtra("number")
        if (number.isNullOrEmpty()) {
            finish()
            return
        }
        val call = PostCall(
            number = number,
            name = intent.getStringExtra("name"),
            timeMillis = intent.getLongExtra("time", 0L),
            lengthMs = intent.getLongExtra("len", 0L),
            type = intent.getIntExtra("type", CallLog.Calls.INCOMING_TYPE),
        )
        setContent {
            BosangTheme {
                PostCallSheet(Store.get(this), call, onClose = { finish() }, onDone = { caseNo, kind -> done(call, caseNo, kind) })
            }
        }
    }

    private fun done(call: PostCall, caseNo: String, kind: IssueKind?) {
        if (kind != null) {
            runCatching {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .putExtra("nav", "issue")
                        .putExtra("case", caseNo)
                        .putExtra("number", call.number)
                        .putExtra("time", call.timeMillis)
                        .putExtra("len", call.lengthMs)
                        .putExtra("kind", kind.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            }
        } else {
            Toast.makeText(this, "$caseNo 사건에 연결했어요", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object {
        fun intent(ctx: Context, number: String, name: String?, timeMillis: Long, lengthMs: Long, type: Int): Intent =
            Intent(ctx, PostCallActivity::class.java)
                .putExtra("number", number)
                .putExtra("name", name)
                .putExtra("time", timeMillis)
                .putExtra("len", lengthMs)
                .putExtra("type", type)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private class PostCall(val number: String, val name: String?, val timeMillis: Long, val lengthMs: Long, val type: Int)

@Composable
private fun PostCallSheet(store: Store, call: PostCall, onClose: () -> Unit, onDone: (String, IssueKind?) -> Unit) {
    val c = B.c
    val thisYear = LocalDate.now().year
    val links = remember { store.linksForNumber(call.number) }
    val first = links.firstOrNull()
    var year by remember { mutableStateOf(first?.caseNo?.substringBefore('-') ?: CaseNumber.year2(thisYear)) }
    var serial by remember { mutableStateOf(first?.caseNo?.substringAfter('-') ?: "") }
    var role by remember { mutableStateOf(first?.role ?: "") }
    var kind by remember { mutableStateOf<IssueKind?>(null) }
    val caseNo = CaseNumber.of(year, serial)
    val valid = CaseNumber.isValid(caseNo)
    val name = store.nameOf(call.number) ?: call.name?.takeIf { it.isNotBlank() }
    val focus = remember { FocusRequester() }

    // 고를 만한 사건: 이 번호의 사건 → (입력 중이면) 맞는 사건 → 최근 본 사건
    val recentCases = remember {
        val all = store.caseNos().toSet()
        store.recentKeys().filter { it.startsWith("c:") }.map { it.removePrefix("c:") }.filter { it in all }
    }
    val picks = (
        links.map { it.caseNo } +
            (if (serial.isNotEmpty() && !valid) store.caseNos().filter { CaseNumber.digits(it).contains(serial) } else recentCases)
        ).distinct().take(6)

    fun pick(s: String) {
        year = s.substringBefore('-')
        serial = s.substringAfter('-')
        store.linksForCase(s).firstOrNull { it.number == call.number }?.let { role = it.role }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66060914))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        BCard(
            radius = 28.dp,
            level = Depth.FLOAT,
            color = c.bg,
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(10.dp)
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(Modifier.heightIn(max = 640.dp)) {
                // 누구와 몇 분
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 18.dp, end = 14.dp, top = 16.dp, bottom = 4.dp),
                ) {
                    Avatar(name, call.number, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            name ?: PhoneNumbers.format(call.number),
                            style = ts(17f, W8, num = name == null),
                            color = c.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(
                                if (call.type == CallLog.Calls.INCOMING_TYPE) "받은 전화" else "건 전화",
                                call.timeMillis.takeIf { it > 0 }?.let { Fmt.time(it) },
                                call.lengthMs.takeIf { it > 0 }?.let { Fmt.durationKo(it) },
                            ).joinToString(" · "),
                            style = ts(12.5f, W4, num = true),
                            color = c.ink2,
                        )
                    }
                    CloseCircle(onClick = onClose)
                }

                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SheetLabel("사고번호") { YearChip(year, thisYear) { year = it } }
                    OtpField(year = year, serial = serial, onSerial = { serial = it.filter { ch -> ch.isDigit() }.take(8) }, focus = focus)
                    if (picks.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                        ) {
                            Text(if (links.isNotEmpty()) "연결된 사건" else "최근 사건", style = ts(12.5f, W7), color = c.ink2)
                            picks.forEach { s ->
                                val on = s == caseNo
                                Text(
                                    s,
                                    style = ts(13f, W8, num = true),
                                    color = if (on) Color.White else c.brand,
                                    modifier = Modifier
                                        .press(scale = 0.95f) { pick(s) }
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (on) c.brand else c.brandTint)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                    if (valid) {
                        val others = store.linksForCase(caseNo).filter { it.number != call.number }
                        if (others.isNotEmpty()) {
                            Text(
                                "이미 있는 사건 · " + others.joinToString(", ") { store.displayName(it.number) + " " + it.role },
                                style = ts(12.5f, W4),
                                color = c.ink2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp),
                            )
                        }
                    }

                    SheetLabel("관계")
                    BCard(
                        Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                    ) {
                        RoleRow(number = call.number, name = name, source = null, role = role, onRole = { role = it })
                    }

                    SheetLabel("특이사항 있었나요? (선택)")
                    Column(
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        IssueKind.entries.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                row.forEach { k -> KindChoice(k, kind == k, Modifier.weight(1f)) { kind = if (kind == k) null else k } }
                            }
                        }
                    }
                    Text(
                        if (kind == null) "고르지 않으면 연결만 해요" else "연결한 뒤 ${kind?.label} 특이사항을 적는 화면으로 가요",
                        style = ts(12.5f, W4),
                        color = c.ink3,
                        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 6.dp),
                    )
                }

                val ready = valid && roleReady(role)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
                ) {
                    SoftButton("취소", Modifier.width(92.dp).height(54.dp), onClick = onClose)
                    GradientButton(
                        text = when {
                            !valid -> "사고번호 8자리"
                            !roleReady(role) -> "관계를 골라 주세요"
                            kind != null -> "연결하고 특이사항 쓰기"
                            else -> "연결하기"
                        },
                        icon = if (ready) Ic.link else null,
                        enabled = ready,
                        modifier = Modifier.weight(1f),
                    ) {
                        store.upsert(caseNo, listOf(Triple(call.number, role.trim(), name)))
                        store.touchRecent("c:$caseNo")
                        onDone(caseNo, kind)
                    }
                }
            }
        }
    }
}

@Composable
private fun KindChoice(k: IssueKind, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = B.c
    val (fg, bg) = kindColors(k)
    val shape = RoundedCornerShape(14.dp)
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .press(scale = 0.95f, onClick = onClick)
            .height(46.dp)
            .clip(shape)
            .background(if (on) bg else c.card)
            .then(if (on) Modifier.border(1.5.dp, fg, shape) else Modifier),
    ) {
        Icon(kindIcon(k), null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (k == IssueKind.DAMAGE) "파손" else k.label, style = ts(14f, W7), color = if (on) fg else c.ink)
    }
}
