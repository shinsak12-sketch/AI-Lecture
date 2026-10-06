package com.bosang.search.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.draw.clip
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
    val assistOn = remember(ver) { store.callAssist() }
    val phoneOk = remember(resumeTick, ver) { Perms.granted(ctx, Manifest.permission.READ_PHONE_STATE) }
    val overlayOk = remember(resumeTick, ver) { android.provider.Settings.canDrawOverlays(ctx) }
    val quiet = remember(ver) { store.quietCount() }
    fun openOverlaySettings() {
        runCatching {
            ctx.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + ctx.packageName),
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
    val phonePerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok && !android.provider.Settings.canDrawOverlays(ctx)) openOverlaySettings()
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

        Group("통화 연동")
        BCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            SettingRow(
                icon = Ic.phone, fg = c.brand, bg = c.brandTint,
                title = "통화 중 · 통화 후 도우미",
                sub = "사건 번호와 통화하면 화면 위에 사건 정보, 끝나면 특이사항 남기기",
                trailing = { Toggle(assistOn) },
                onClick = {
                    val on = !assistOn
                    store.setCallAssist(on)
                    if (on) {
                        if (!Perms.granted(ctx, Manifest.permission.READ_PHONE_STATE)) phonePerm.launch(Manifest.permission.READ_PHONE_STATE)
                        else if (!android.provider.Settings.canDrawOverlays(ctx)) openOverlaySettings()
                    }
                },
            )
            if (assistOn) {
                Line()
                SettingRow(
                    icon = Ic.clock, fg = if (phoneOk) c.ok else c.warn, bg = if (phoneOk) c.okTint else c.warnTint,
                    title = "전화 상태 읽기",
                    trailing = { if (phoneOk) SmallTag("허용됨", c.ok, c.okTint, Ic.check) else SmallTag("허용 필요", c.warn, c.warnTint) },
                    onClick = if (phoneOk) null else ({ phonePerm.launch(Manifest.permission.READ_PHONE_STATE) }),
                )
                Line()
                SettingRow(
                    icon = Ic.image, fg = if (overlayOk) c.ok else c.warn, bg = if (overlayOk) c.okTint else c.warnTint,
                    title = "다른 앱 위에 표시",
                    sub = if (overlayOk) null else "설정에서 보상검색기를 켜 주세요",
                    trailing = { if (overlayOk) SmallTag("허용됨", c.ok, c.okTint, Ic.check) else SmallTag("허용 필요", c.warn, c.warnTint) },
                    onClick = if (overlayOk) null else ({ openOverlaySettings() }),
                )
                Line()
                val last = remember(resumeTick, ver) { com.bosang.search.call.CallWatcher.lastEvent(ctx) }
                val err = remember(resumeTick, ver) { ctx.getSharedPreferences("call", android.content.Context.MODE_PRIVATE).getString("err", null) }
                SettingRow(
                    icon = Ic.info, fg = c.ink2, bg = c.chip,
                    title = "마지막 통화 감지",
                    sub = (last ?: "아직 감지한 통화가 없어요") + (err?.let { "\n창 오류: $it" } ?: ""),
                )
                Line()
                SettingRow(
                    icon = Ic.image, fg = c.brand, bg = c.brandTint,
                    title = "통화 끝난 뒤 창 미리 보기",
                    sub = "가장 최근 통화로 창을 띄워 봐요",
                    onClick = {
                        if (!com.bosang.search.call.CallWatcher.preview(ctx)) {
                            android.widget.Toast.makeText(ctx, if (!overlayOk) "다른 앱 위에 표시를 먼저 허용해 주세요" else "최근 통화가 없어요", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                if (quiet > 0) {
                    Line()
                    SettingRow(
                        icon = Ic.x, fg = c.ink2, bg = c.chip,
                        title = "다시 묻지 않는 번호 ${quiet}개",
                        sub = "눌러서 모두 다시 묻기",
                        onClick = { store.clearQuiet() },
                    )
                }
            }
        }
        Text(
            "녹음은 삼성 전화 앱이 그대로 해요. 이 앱은 통화 화면 위에 정보만 띄워요.",
            style = ts(12f, W6),
            color = c.ink3,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp),
        )

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
        if (onClick != null && trailing == null) {
            Spacer(Modifier.width(6.dp))
            Icon(Ic.chevron, null, tint = c.ink3, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun Toggle(on: Boolean) {
    val c = B.c
    Box(
        Modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (on) c.brand else c.chip2)
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color.White),
        )
    }
}
