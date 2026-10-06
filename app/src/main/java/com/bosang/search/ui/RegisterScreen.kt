package com.bosang.search.ui

import android.provider.CallLog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bosang.search.core.CaseNumber
import com.bosang.search.core.PhoneNumbers
import com.bosang.search.data.PhoneData
import com.bosang.search.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

private data class CallRow(
    val number: String,
    val name: String?,
    val lastTime: Long,
    val lastType: Int,
    val count: Int,
)

private fun callTypeLabel(type: Int): String = when (type) {
    CallLog.Calls.INCOMING_TYPE -> "수신"
    CallLog.Calls.OUTGOING_TYPE -> "발신"
    CallLog.Calls.MISSED_TYPE -> "부재중"
    CallLog.Calls.REJECTED_TYPE -> "거절"
    else -> "통화"
}

@Composable
fun RegisterScreen(
    store: Store,
    data: PhoneData,
    prefillCase: String?,
    prefillNumbers: List<String>,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    var rows by remember { mutableStateOf<List<CallRow>?>(null) }
    val selected = remember { mutableStateListOf<String>().apply { addAll(prefillNumbers) } }
    val names = remember { mutableStateMapOf<String, String>() }
    var step by remember { mutableStateOf(if (prefillNumbers.isNotEmpty()) 2 else 1) }

    LaunchedEffect(Unit) {
        rows = withContext(Dispatchers.IO) {
            data.calls(500)
                .filter { it.number.isNotEmpty() }
                .groupBy { it.number } // 최신순이라 첫 줄이 마지막 통화
                .map { (n, list) -> CallRow(n, list.first().name, list.first().timeMillis, list.first().type, list.size) }
        }
    }

    // 고른 번호의 표시 이름: 통화기록 → 저장된 이름 → 연락처 순
    LaunchedEffect(step) {
        if (step != 2) return@LaunchedEffect
        selected.toList().forEach { n ->
            if (names[n] != null) return@forEach
            val fromCalls = rows?.firstOrNull { it.number == n }?.name
            names[n] = fromCalls ?: store.nameOf(n) ?: withContext(Dispatchers.IO) { data.contactName(n) } ?: ""
        }
    }

    if (step == 1) {
        PickStep(
            rows = rows,
            store = store,
            selected = selected,
            onBack = onBack,
            onNext = { step = 2 },
        )
    } else {
        CaseStep(
            store = store,
            prefillCase = prefillCase,
            selected = selected,
            names = names,
            onBack = { if (prefillNumbers.isNotEmpty()) onBack() else step = 1 },
            onSaved = onSaved,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickStep(
    rows: List<CallRow>?,
    store: Store,
    selected: MutableList<String>,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("통화내역에서 번호 고르기") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = onNext,
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                ) { Text(if (selected.isEmpty()) "번호를 골라주세요 (여러 명 가능)" else "다음 · ${selected.size}명 선택") }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("이름이나 번호로 좁히기") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (rows == null) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("통화내역 불러오는 중…")
                }
                return@Column
            }
            val digits = filter.filter { it.isDigit() }
            val shown = rows.filter { r ->
                filter.isBlank() ||
                    (digits.isNotEmpty() && r.number.contains(digits)) ||
                    (r.name?.contains(filter.trim()) == true)
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(shown, key = { it.number }) { r ->
                    val checked = r.number in selected
                    val caseCount = store.linksForNumber(r.number).size
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (checked) selected.remove(r.number) else selected.add(r.number) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { if (it) selected.add(r.number) else selected.remove(r.number) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                r.name ?: PhoneNumbers.format(r.number),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                (if (r.name != null) PhoneNumbers.format(r.number) + " · " else "") +
                                    "${callTypeLabel(r.lastType)} ${Fmt.dateTime(r.lastTime)}" +
                                    if (r.count > 1) " · ${r.count}회" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (caseCount > 0) {
                            Tag(
                                "사건 ${caseCount}건",
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.onSecondaryContainer,
                                Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CaseStep(
    store: Store,
    prefillCase: String?,
    selected: List<String>,
    names: Map<String, String>,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    var year by remember { mutableStateOf(prefillCase?.substringBefore('-') ?: CaseNumber.year2(LocalDate.now().year)) }
    var serial by remember { mutableStateOf(prefillCase?.substringAfter('-') ?: "") }
    val roles = remember { mutableStateMapOf<String, String>() }
    val caseNo = CaseNumber.of(year, serial)
    val valid = CaseNumber.isValid(caseNo)

    // 이미 있는 사건이면, 이미 정해둔 관계를 채워줌
    LaunchedEffect(caseNo) {
        if (!valid) return@LaunchedEffect
        store.linksForCase(caseNo).forEach { link ->
            if (link.number in selected && roles[link.number].isNullOrEmpty()) roles[link.number] = link.role
        }
    }
    val suggestions = if (prefillCase == null && serial.isNotEmpty() && !valid) {
        store.caseNos().filter { CaseNumber.digits(it).contains(serial) }.take(6)
    } else emptyList()
    val allRolesReady = selected.all { roleReady(roles[it].orEmpty()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("사고번호 · 관계") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = {
                        store.upsert(caseNo, selected.map { n -> Triple(n, roles[n].orEmpty(), names[n]?.ifBlank { null }) })
                        store.touchRecent("c:$caseNo")
                        onSaved(caseNo)
                    },
                    enabled = valid && allRolesReady,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(16.dp),
                ) {
                    Text(
                        when {
                            !valid -> "사고번호를 입력해주세요"
                            !allRolesReady -> "관계를 골라주세요"
                            else -> "$caseNo 에 저장"
                        },
                    )
                }
            }
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(pad)
                .fillMaxSize(),
        ) {
            item {
                Text("사고번호", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("연도") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(88.dp),
                    )
                    Text(" - ", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = serial,
                        onValueChange = { serial = it.filter { c -> c.isDigit() }.take(8) },
                        label = { Text("일련번호 8자리") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { Text("${serial.length} / 8") },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (valid && store.linksForCase(caseNo).isNotEmpty()) {
                    Text(
                        "이미 있는 사건이에요. 고른 사람이 이 사건에 추가됩니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            if (suggestions.isNotEmpty()) {
                item {
                    Text("이미 있는 사건", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow {
                        suggestions.forEach { c ->
                            AssistChip(
                                onClick = {
                                    year = c.substringBefore('-')
                                    serial = c.substringAfter('-')
                                },
                                label = { Text(c) },
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                    }
                }
            }
            items(selected, key = { it }) { n ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp)) {
                        val name = names[n].orEmpty()
                        Text(
                            name.ifBlank { PhoneNumbers.format(n) },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (name.isNotBlank()) {
                            Text(
                                PhoneNumbers.format(n),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        RolePicker(role = roles[n].orEmpty(), onRole = { roles[n] = it }, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}
