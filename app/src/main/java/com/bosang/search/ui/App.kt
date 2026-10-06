package com.bosang.search.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bosang.search.data.PhoneData
import com.bosang.search.data.Player
import com.bosang.search.data.RecordingIndex
import com.bosang.search.data.Store

sealed interface Screen {
    data object Home : Screen
    data class Register(val caseNo: String? = null, val numbers: List<String> = emptyList()) : Screen
    data class Case(val caseNo: String) : Screen
    data class Person(val number: String) : Screen
}

object Perms {
    private val audio = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    val required: Array<String> = arrayOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_CONTACTS,
        audio,
    )

    fun allGranted(ctx: Context): Boolean =
        required.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

    fun missing(ctx: Context): List<String> =
        required.filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
}

@Composable
fun App(resumeTick: Int) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(Perms.allGranted(ctx)) }
    LaunchedEffect(resumeTick) {
        granted = Perms.allGranted(ctx)
        RecordingIndex.invalidate() // 돌아오면 새 통화녹음을 다시 찾음
    }
    if (!granted) {
        PermissionScreen(onChecked = { granted = Perms.allGranted(ctx) })
        return
    }

    val store = remember { Store.get(ctx) }
    val data = remember { PhoneData(ctx) }
    val player = remember { Player(ctx) }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    fun push(s: Screen) {
        player.release()
        stack = stack + s
    }
    fun pop() {
        player.release()
        if (stack.size > 1) stack = stack.dropLast(1)
    }
    fun replaceTop(s: Screen) {
        player.release()
        stack = stack.dropLast(1) + s
    }
    BackHandler(enabled = stack.size > 1) { pop() }

    when (val s = stack.last()) {
        Screen.Home -> HomeScreen(
            store = store,
            onOpenCase = { push(Screen.Case(it)) },
            onOpenPerson = { push(Screen.Person(it)) },
            onRegister = { push(Screen.Register()) },
        )
        is Screen.Register -> RegisterScreen(
            store = store,
            data = data,
            prefillCase = s.caseNo,
            prefillNumbers = s.numbers,
            onBack = { pop() },
            onSaved = { caseNo -> replaceTop(Screen.Case(caseNo)) },
        )
        is Screen.Case -> CaseScreen(
            store = store,
            data = data,
            player = player,
            caseNo = s.caseNo,
            resumeTick = resumeTick,
            onBack = { pop() },
            onOpenPerson = { push(Screen.Person(it)) },
            onAddPeople = { push(Screen.Register(caseNo = s.caseNo)) },
        )
        is Screen.Person -> PersonScreen(
            store = store,
            data = data,
            player = player,
            number = s.number,
            resumeTick = resumeTick,
            onBack = { pop() },
            onOpenCase = { push(Screen.Case(it)) },
            onRegister = { push(Screen.Register(numbers = listOf(s.number))) },
        )
    }
}

@Composable
private fun PermissionScreen(onChecked: () -> Unit) {
    val ctx = LocalContext.current
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        onChecked()
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text("보상검색기", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "사고번호와 전화번호만 연결하면, 그 번호의 문자와 통화녹음을 폰 안에서 찾아 보여드려요.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("필요한 권한", fontWeight = FontWeight.Bold)
                    Text("📞 통화기록 — 번호를 골라 등록하고, 녹음과 통화 시각을 맞춰보기")
                    Text("💬 문자 — 그 번호와 주고받은 문자 보여주기")
                    Text("👤 연락처 — 이름으로 저장된 녹음 파일의 번호 찾기")
                    Text("🎙 오디오 — 통화녹음 파일 찾아서 재생하기")
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    "🔒 이 앱은 인터넷 권한이 없어요. 읽은 내용은 폰 밖으로 나가지 않습니다.",
                    modifier = Modifier.padding(16.dp),
                )
            }
            Button(onClick = { launcher.launch(Perms.required) }, modifier = Modifier.fillMaxWidth()) {
                Text("권한 허용하기")
            }
            if (asked && Perms.missing(ctx).isNotEmpty()) {
                Spacer(Modifier.size(4.dp))
                Text("아직 허용되지 않은 권한이 있어요", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Text(
                    "허용 창이 안 뜨거나 '제한된 설정'이라고 나오면:\n" +
                        "1. 아래 버튼으로 앱 정보로 이동\n" +
                        "2. 오른쪽 위 ⋮ → '제한된 설정 허용'\n" +
                        "3. 권한 → 통화기록·문자·연락처·음악 및 오디오 허용",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("앱 정보 열기") }
            }
        }
    }
}
