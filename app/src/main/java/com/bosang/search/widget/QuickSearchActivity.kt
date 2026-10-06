package com.bosang.search.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bosang.search.MainActivity
import com.bosang.search.core.Hangul
import com.bosang.search.core.PhoneNumbers
import com.bosang.search.data.DirEntry
import com.bosang.search.data.PhoneData
import com.bosang.search.data.Store
import com.bosang.search.ui.Avatar
import com.bosang.search.ui.B
import com.bosang.search.ui.BCard
import com.bosang.search.ui.BosangTheme
import com.bosang.search.ui.Depth
import com.bosang.search.ui.Ic
import com.bosang.search.ui.Perms
import com.bosang.search.ui.W4
import com.bosang.search.ui.W6
import com.bosang.search.ui.W8
import com.bosang.search.ui.dial
import com.bosang.search.ui.press
import com.bosang.search.ui.sendSms
import com.bosang.search.ui.ts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 위젯 검색칸을 누르면 홈 화면 위에 뜨는 작은 검색창. 결과에서 바로 전화 · 문자 · 열기 */
class QuickSearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!Perms.allGranted(this)) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        setContent {
            BosangTheme {
                QuickSearch(
                    onClose = { finish() },
                    onDone = { finish() },
                )
            }
        }
    }
}

private class QItem(
    val key: String,
    val title: String,
    val sub: String,
    val name: String?,
    val number: String?,
    val isCase: Boolean,
    val open: (Context) -> Intent,
)

private fun caseItem(store: Store, c: String): QItem? {
    val links = store.linksForCase(c)
    if (links.isEmpty()) return null
    val latest = store.issuesForCase(c).firstOrNull()
    return QItem(
        key = "c:$c",
        title = c,
        sub = latest?.let { it.kind.label + " · " + it.summary { n -> store.displayName(n) } }
            ?: links.joinToString(" · ") { store.displayName(it.number) + " " + it.role },
        name = store.nameOf(links.first().number) ?: "#",
        number = links.singleOrNull()?.number,
        isCase = true,
        open = { ctx -> Intent(ctx, MainActivity::class.java).putExtra("nav", "case").putExtra("case", c) },
    )
}

private fun personItem(store: Store, n: String, name: String?, label: String?): QItem {
    val links = store.linksForNumber(n)
    val shown = name ?: store.nameOf(n)
    return QItem(
        key = "p:$n",
        title = shown ?: PhoneNumbers.format(n),
        sub = listOfNotNull(
            if (shown != null) PhoneNumbers.format(n) else "저장 안 된 번호",
            label,
            if (links.isNotEmpty()) links.joinToString(", ") { it.caseNo } else "사건 없음",
        ).joinToString(" · "),
        name = shown,
        number = n,
        isCase = false,
        open = { ctx -> Intent(ctx, MainActivity::class.java).putExtra("nav", "person").putExtra("number", n) },
    )
}

@Composable
private fun QuickSearch(onClose: () -> Unit, onDone: () -> Unit) {
    val c = B.c
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val data = remember { PhoneData(ctx) }
    var query by remember { mutableStateOf("") }
    var dir by remember { mutableStateOf<List<DirEntry>?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focus.requestFocus() }
        keyboard?.show()
        dir = withContext(Dispatchers.IO) { data.directory() }
    }

    fun go(intent: Intent) {
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)) }
        onDone()
    }

    val items: List<QItem> = run {
        val q = query.trim()
        if (q.isEmpty()) {
            val cases = store.caseNos().toSet()
            store.recentKeys().mapNotNull { k ->
                when {
                    k.startsWith("c:") && k.removePrefix("c:") in cases -> caseItem(store, k.removePrefix("c:"))
                    k.startsWith("p:") -> personItem(store, k.removePrefix("p:"), null, null)
                    else -> null
                }
            }
        } else {
            val digits = q.filter { it.isDigit() }
            val out = ArrayList<QItem>()
            store.searchCases(q).mapNotNullTo(out) { caseItem(store, it) }
            val registered = store.searchPeople(q)
            registered.mapTo(out) { personItem(store, it, null, null) }
            dir.orEmpty().filter { e ->
                e.number !in registered &&
                    ((digits.length >= 3 && e.number.contains(digits)) || (e.name?.let { Hangul.matches(it, q) } == true))
            }.take(40).mapTo(out) { personItem(store, it.number, it.name, it.label) }
            if (out.isEmpty() && digits.length >= 9 && PhoneNumbers.looksLikeNumber(q)) {
                out.add(personItem(store, PhoneNumbers.normalize(q), null, null))
            }
            out.distinctBy { it.key }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99060914))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        Column(
            Modifier
                .statusBarsPadding()
                .imePadding()
                .padding(12.dp)
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            BCard(radius = 24.dp, level = Depth.FLOAT, color = c.bg, modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.card)
                        .padding(horizontal = 14.dp),
                ) {
                    Icon(Ic.search, null, tint = c.ink3, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("사고번호, 번호, 이름, 초성", style = ts(15.5f), color = c.ink3, maxLines = 1)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = ts(15.5f, W6).copy(color = c.ink),
                            cursorBrush = SolidColor(c.brand),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    Text(
                        "앱 열기",
                        style = ts(13f, W8),
                        color = c.brand,
                        modifier = Modifier.press(scale = 0.94f) { go(Intent(ctx, MainActivity::class.java)) }.padding(6.dp),
                    )
                }
                Text(
                    if (query.isBlank()) "최근 본 사건 · 사람" else "결과 ${items.size}",
                    style = ts(12f, W8),
                    color = c.ink2,
                    modifier = Modifier.padding(start = 18.dp, bottom = 6.dp),
                )
                if (items.isEmpty()) {
                    Text(
                        if (query.isBlank()) "아직 본 사건이 없어요" else if (dir == null) "찾는 중" else "찾는 결과가 없어요",
                        style = ts(14f, W6),
                        color = c.ink2,
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 20.dp),
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 460.dp).padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                        items(items, key = { it.key }) { q ->
                            QRow(
                                q,
                                onOpen = { go(q.open(ctx)) },
                                onCall = q.number?.let { n -> { dial(ctx, n); onDone() } },
                                onSms = q.number?.let { n -> { sendSms(ctx, n); onDone() } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QRow(item: QItem, onOpen: () -> Unit, onCall: (() -> Unit)?, onSms: (() -> Unit)?) {
    val c = B.c
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .press(scale = 0.98f, onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        if (item.isCase) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(c.brandTint),
            ) { Icon(Ic.folder, null, tint = c.brand, modifier = Modifier.size(19.dp)) }
        } else {
            Avatar(item.name, item.number.orEmpty(), 38.dp)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = ts(15f, W8, num = true), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.sub, style = ts(12.5f, W4, num = true), color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onCall != null) RoundAction(Ic.phone, "전화", onCall)
        if (onSms != null) RoundAction(Ic.msg, "문자", onSms)
    }
}

@Composable
private fun RoundAction(icon: ImageVector, desc: String, onClick: () -> Unit) {
    val c = B.c
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(start = 6.dp)
            .press(scale = 0.9f, onClick = onClick)
            .size(36.dp)
            .clip(CircleShape)
            .background(c.brandTint),
    ) { Icon(icon, desc, tint = c.brand, modifier = Modifier.size(17.dp)) }
}
