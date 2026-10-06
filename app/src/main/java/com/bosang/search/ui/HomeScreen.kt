package com.bosang.search.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bosang.search.core.PhoneNumbers
import com.bosang.search.data.Store

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    store: Store,
    onOpenCase: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onRegister: () -> Unit,
) {
    store.version.intValue // 등록·수정되면 다시 그림
    var query by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("보상검색기", fontWeight = FontWeight.Bold)
                    Text(
                        "사고번호 · 전화번호 · 이름으로 찾기",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onRegister,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("사건 등록") },
            )
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .padding(pad)
                .fillMaxSize(),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "지우기") }
                        }
                    },
                    placeholder = { Text("26-00012345 · 010-1234-5678 · 홍길동") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (query.isBlank()) {
                val recent = store.recentKeys()
                if (recent.isNotEmpty()) {
                    item { SectionTitle("최근 본 항목") }
                    items(recent, key = { "recent-$it" }) { key ->
                        when {
                            key.startsWith("c:") -> CaseRow(store, key.removePrefix("c:"), onOpenCase)
                            key.startsWith("p:") -> PersonRow(store, key.removePrefix("p:"), onOpenPerson)
                        }
                    }
                }
                val cases = store.caseNos()
                item { SectionTitle("등록된 사건 ${cases.size}건") }
                if (cases.isEmpty()) {
                    item {
                        Text(
                            "아직 등록된 사건이 없어요.\n아래 [사건 등록]을 눌러 통화내역이나 연락처에서 번호를 골라보세요.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                items(cases, key = { "case-$it" }) { CaseRow(store, it, onOpenCase) }
            } else {
                val cases = store.searchCases(query)
                val people = store.searchPeople(query)
                val digits = PhoneNumbers.normalize(query)
                val unregistered = query.count { it.isDigit() } >= 9 &&
                    PhoneNumbers.looksLikeNumber(query) &&
                    store.registeredNumbers().none { it == digits }

                if (cases.isNotEmpty()) {
                    item { SectionTitle("사건 ${cases.size}") }
                    items(cases, key = { "rc-$it" }) { CaseRow(store, it, onOpenCase) }
                }
                if (people.isNotEmpty()) {
                    item { SectionTitle("사람 ${people.size}") }
                    items(people, key = { "rp-$it" }) { PersonRow(store, it, onOpenPerson) }
                }
                if (unregistered) {
                    item { SectionTitle("등록 안 된 번호") }
                    item {
                        RowCard(onClick = { onOpenPerson(digits) }) {
                            Text(PhoneNumbers.format(digits), fontWeight = FontWeight.Bold)
                            Text(
                                "이 번호의 문자·통화녹음 바로 보기",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (cases.isEmpty() && people.isEmpty() && !unregistered) {
                    item {
                        Text(
                            "찾는 결과가 없어요.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
fun RowCard(onClick: () -> Unit, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
private fun CaseRow(store: Store, caseNo: String, onOpen: (String) -> Unit) {
    val links = store.linksForCase(caseNo)
    if (links.isEmpty()) return
    RowCard(onClick = { onOpen(caseNo) }) {
        Text(caseNo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            links.joinToString(" · ") { "${store.displayName(it.number)}(${it.role})" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PersonRow(store: Store, number: String, onOpen: (String) -> Unit) {
    val links = store.linksForNumber(number)
    RowCard(onClick = { onOpen(number) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(store.displayName(number), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                PhoneNumbers.format(number),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (links.isNotEmpty()) {
            Text(
                links.joinToString(" · ") { "${it.caseNo} ${it.role}" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
