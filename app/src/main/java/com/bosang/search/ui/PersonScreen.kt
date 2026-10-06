package com.bosang.search.ui

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.bosang.search.data.PhoneData
import com.bosang.search.data.Player
import com.bosang.search.data.RecordingIndex
import com.bosang.search.data.Store
import com.bosang.search.data.TimelineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(
    store: Store,
    data: PhoneData,
    player: Player,
    number: String,
    resumeTick: Int,
    onBack: () -> Unit,
    onOpenCase: (String) -> Unit,
    onRegister: () -> Unit,
) {
    val ver = store.version.intValue
    val links = remember(ver, number) { store.linksForNumber(number) }
    var contactName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(number) {
        store.touchRecent("p:$number")
        contactName = withContext(Dispatchers.IO) { data.contactName(number) }
    }
    val title = store.nameOf(number) ?: contactName ?: PhoneNumbers.format(number)

    var items by remember(number) { mutableStateOf<List<TimelineItem>?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(number, resumeTick, refresh) {
        items = RecordingIndex.timeline(setOf(number), data, store)
    }
    var kind by remember { mutableStateOf(KindFilter.ALL) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, fontWeight = FontWeight.Bold)
                        if (title != PhoneNumbers.format(number)) {
                            Text(
                                PhoneNumbers.format(number),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
                actions = {
                    IconButton(onClick = {
                        RecordingIndex.invalidate()
                        refresh++
                    }) { Icon(Icons.Filled.Refresh, "다시 찾기") }
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
            if (links.isNotEmpty()) {
                item { SectionTitle("연결된 사건 ${links.size}건") }
                items(links, key = { "c-" + it.caseNo }) { link ->
                    RowCard(onClick = { onOpenCase(link.caseNo) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(link.caseNo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            RoleTag(link.role)
                        }
                    }
                }
            } else {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("아직 사건에 연결되지 않은 번호예요.")
                            Button(onClick = onRegister, modifier = Modifier.fillMaxWidth()) { Text("이 번호를 사건에 등록") }
                        }
                    }
                }
            }
            item { SectionTitle("문자 · 통화녹음 (사건 구분 없이 전부)") }
            item { KindFilterRow(items.orEmpty(), kind) { kind = it } }
            timeline(items?.filterKind(kind), { null }, player)
        }
    }
}
