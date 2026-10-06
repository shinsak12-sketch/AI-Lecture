package com.bosang.search.ui

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bosang.search.data.IndexedRecording
import com.bosang.search.data.PhoneData
import com.bosang.search.data.RecordingIndex
import com.bosang.search.data.Store
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(store: Store, data: PhoneData, resumeTick: Int) {
    val c = B.c
    val ctx = LocalContext.current
    StatusBarIcons(lightContent = false)
    val ver = store.version.intValue
    val scope = rememberCoroutineScope()
    var index by remember { mutableStateOf<List<IndexedRecording>?>(null) }
    var scan by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(resumeTick, scan) {
        index = RecordingIndex.get(data, store)
    }
    val caseCount = remember(ver) { store.caseNos().size }
    val peopleCount = remember(ver) { store.registeredNumbers().size }
    val perms = remember(resumeTick) {
        listOf(
            Triple("통화기록", Ic.clock, Manifest.permission.READ_CALL_LOG),
            Triple("문자", Ic.msg, Manifest.permission.READ_SMS),
            Triple("연락처", Ic.user, Manifest.permission.READ_CONTACTS),
            Triple("오디오", Ic.wave, Perms.audio),
        ).map { (label, icon, p) -> Triple(label, icon, Perms.granted(ctx, p)) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(bottom = 140.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .height(56.dp),
        ) {
            Text("설정", style = ts(30f, W8, tracking = -0.035f), color = c.ink)
        }

        Group("통화녹음")
        BCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            val list = index
            SettingRow(
                icon = Ic.wave, fg = c.rec, bg = c.recTint,
                title = "찾은 통화녹음",
                value = list?.size?.let { "${it}개" } ?: "찾는 중",
            )
            Line()
            val ambiguous = list?.count { it.match.method == com.bosang.search.core.MatchMethod.AMBIGUOUS }
            SettingRow(
                icon = Ic.info, fg = c.warn, bg = c.warnTint,
                title = "확인 필요 (같은 이름)",
                value = ambiguous?.let { "${it}개" } ?: "–",
            )
            Line()
            SettingRow(
                icon = Ic.refresh, fg = c.brand, bg = c.brandTint,
                title = "통화녹음 다시 찾기",
                sub = "새로 생긴 녹음 파일을 바로 찾고 싶을 때",
                onClick = {
                    index = null
                    RecordingIndex.invalidate()
                    scan++
                },
            )
        }

        Group("권한")
        BCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            perms.forEachIndexed { i, (label, icon, ok) ->
                if (i > 0) Line()
                SettingRow(
                    icon = icon,
                    fg = if (ok) c.ok else c.warn,
                    bg = if (ok) c.okTint else c.warnTint,
                    title = label,
                    trailing = {
                        if (ok) SmallTag("허용됨", c.ok, c.okTint, Ic.check) else SmallTag("꺼짐", c.warn, c.warnTint)
                    },
                )
            }
            Line()
            SettingRow(
                icon = Ic.gear, fg = c.ink2, bg = c.chip,
                title = "앱 정보 열기",
                sub = "권한 바꾸기 · 제한된 설정 허용",
                onClick = { Perms.openAppSettings(ctx) },
            )
        }

        Group("저장된 내용")
        BCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            SettingRow(
                icon = Ic.folder, fg = c.brand, bg = c.brandTint,
                title = "사건 ${caseCount}개 · 사람 ${peopleCount}명",
                sub = "사고번호와 번호의 연결만 이 폰 안에 저장돼요",
            )
            Line()
            SettingRow(
                icon = Ic.trash, fg = c.rec, bg = c.recTint,
                title = "모든 연결 지우기",
                titleColor = c.rec,
                onClick = { confirmClear = true },
            )
        }

        Group("이 앱은")
        BCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            SettingRow(
                icon = Ic.shield, fg = c.ok, bg = c.okTint,
                title = "인터넷 권한 없음",
                sub = "읽은 문자·녹음은 폰 밖으로 나가지 않아요",
            )
            Line()
            SettingRow(
                icon = Ic.docSearch, fg = c.ink2, bg = c.chip,
                title = "보상검색기",
                value = "버전 " + versionName(ctx),
                sub = "글꼴: Pretendard (SIL Open Font License)",
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = c.card,
            shape = RoundedCornerShape(26.dp),
            title = { Text("모든 연결을 지울까요?", style = ts(19f, W8), color = c.ink) },
            text = {
                Text(
                    "등록한 사건 ${caseCount}개와 이름 · 관계 정보가 지워져요. 폰에 있는 문자와 통화녹음 원본은 그대로 남습니다.",
                    style = ts(14.5f, W4, lineHeight = 1.5f),
                    color = c.ink2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        store.clearAll()
                        RecordingIndex.invalidate()
                        scan++
                    }
                }) { Text("지우기", style = ts(15f, W8), color = c.rec) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("취소", style = ts(15f, W7), color = c.ink2) }
            },
        )
    }
}

private fun versionName(ctx: android.content.Context): String =
    runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "-"

@Composable
private fun Group(text: String) {
    Text(
        text,
        style = ts(12.5f, W8, tracking = 0.04f),
        color = B.c.ink2,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 9.dp),
    )
}

@Composable
private fun Line() {
    Box(
        Modifier
            .padding(start = 66.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(B.c.line),
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    fg: Color,
    bg: Color,
    title: String,
    sub: String? = null,
    value: String? = null,
    titleColor: Color = B.c.ink,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = B.c
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.press(scale = 0.98f, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        IconTile(icon, fg, bg, 36.dp, 12.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = ts(15f, W7), color = titleColor)
            if (sub != null) Text(sub, style = ts(12.5f, W4), color = c.ink2, modifier = Modifier.padding(top = 2.dp))
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, style = ts(14f, W7, num = true), color = c.ink2)
        }
        trailing?.invoke()
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))
            Icon(Ic.chevron, null, tint = c.ink3, modifier = Modifier.size(15.dp))
        }
    }
}
