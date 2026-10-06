package com.bosang.search.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bosang.search.core.PhoneNumbers
import com.bosang.search.data.CaseLink
import com.bosang.search.data.PhoneData
import com.bosang.search.data.Player
import com.bosang.search.data.RecordingIndex
import com.bosang.search.data.Store
import com.bosang.search.data.TimelineItem

@OptIn(ExperimentalMaterial3Api::class)
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
    val ver = store.version.intValue
    val links = remember(ver, caseNo) { store.linksForCase(caseNo) }
    LaunchedEffect(caseNo) { store.touchRecent("c:$caseNo") }
    if (links.isEmpty()) {
        // 마지막 사람을 빼거나 사건을 지우면 돌아감
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val numbers = links.map { it.number }.toSet()
    var items by remember(caseNo) { mutableStateOf<List<TimelineItem>?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(numbers, resumeTick, refresh) {
        items = RecordingIndex.timeline(numbers, data, store)
    }

    var kind by remember { mutableStateOf(KindFilter.ALL) }
    var who by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<CaseLink?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val roleOf = links.associate { it.number to it.role }
    val label: (String) -> String? = { n -> "${store.displayName(n)}(${roleOf[n] ?: ""})" }
    val shown = items?.filterKind(kind)?.filter { who == null || it.number == who }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(caseNo, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
                actions = {
                    IconButton(onClick = {
                        RecordingIndex.invalidate()
                        refresh++
                    }) { Icon(Icons.Filled.Refresh, "다시 찾기") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "사건 삭제") }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(pad)
                .fillMaxSize(),
        ) {
            item { SectionTitle("관련된 사람 ${links.size}명") }
            items(links, key = { "p-" + it.number }) { link ->
                RowCard(onClick = { onOpenPerson(link.number) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoleTag(link.role)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(store.displayName(link.number), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                PhoneNumbers.format(link.number),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { editing = link }) { Icon(Icons.Filled.Edit, "관계 수정") }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onAddPeople, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("통화내역에서 사람 추가")
                }
            }
            item { SectionTitle("문자 · 통화녹음") }
            item { KindFilterRow(items.orEmpty(), kind) { kind = it } }
            if (links.size > 1) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        FilterChip(selected = who == null, onClick = { who = null }, label = { Text("모든 사람") })
                        links.forEach { l ->
                            FilterChip(
                                selected = who == l.number,
                                onClick = { who = l.number },
                                label = { Text("${store.displayName(l.number)}(${l.role})") },
                            )
                        }
                    }
                }
            }
            timeline(shown, label, player)
        }
    }

    editing?.let { link ->
        var role by remember(link) { mutableStateOf(link.role) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(store.displayName(link.number)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("$caseNo 에서의 관계", style = MaterialTheme.typography.labelLarge)
                    RolePicker(role = role, onRole = { role = it }, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = {
                TextButton(
                    enabled = roleReady(role),
                    onClick = {
                        store.setRole(caseNo, link.number, role)
                        editing = null
                    },
                ) { Text("저장") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        store.removeLink(caseNo, link.number)
                        editing = null
                    }) { Text("사건에서 빼기", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editing = null }) { Text("취소") }
                }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("$caseNo 삭제") },
            text = { Text("이 사건의 연결 정보만 지워요. 폰에 있는 문자와 통화녹음 원본은 그대로 남습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    store.deleteCase(caseNo)
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}
