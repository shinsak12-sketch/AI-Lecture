package com.bosang.search.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bosang.search.data.PhoneData
import com.bosang.search.data.Player
import com.bosang.search.data.RecordingIndex
import com.bosang.search.data.Store
import com.bosang.search.data.IssueKind
import com.bosang.search.data.IssueSource
import kotlinx.coroutines.delay

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Register(val caseNo: String? = null, val numbers: List<String> = emptyList()) : Screen
    data class Case(val caseNo: String) : Screen
    data class Person(val number: String) : Screen
    data class IssueEdit(
        val caseNo: String,
        val issueId: String? = null,
        val source: IssueSource? = null,
        val kind: IssueKind? = null,
    ) : Screen
    data class Album(val caseNo: String) : Screen
    data class Photo(val ref: String, val photoId: String?) : Screen
}

/** 앱 밖(통화 끝난 뒤 창)에서 열어 달라고 한 화면 */
object ExternalNav {
    val pending = mutableStateOf<Screen?>(null)
}

object Perms {
    val audio: String = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    val required: Array<String> = arrayOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_CONTACTS,
        audio,
    )

    fun granted(ctx: Context, p: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun allGranted(ctx: Context): Boolean = required.all { granted(ctx, it) }

    fun openAppSettings(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
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
        OnboardingScreen(resumeTick = resumeTick, onChecked = { granted = Perms.allGranted(ctx) })
        return
    }

    val store = remember { Store.get(ctx) }
    val data = remember { PhoneData(ctx) }
    val player = remember { Player(ctx) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(player.isPlaying) {
        while (player.isPlaying) {
            player.tick()
            delay(200)
        }
    }

    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    var markPick by remember { mutableStateOf<IssueSource?>(null) }
    fun push(s: Screen) {
        if (s is Screen.Register) player.release()
        stack = stack + s
    }
    fun pop() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }
    fun replaceTop(s: Screen) {
        stack = stack.dropLast(1) + s
    }
    fun tab(t: Tab) {
        stack = listOf(if (t == Tab.CASES) Screen.Home else Screen.Settings)
    }
    BackHandler(enabled = stack.size > 1) { pop() }
    LaunchedEffect(ExternalNav.pending.value) {
        val next = ExternalNav.pending.value ?: return@LaunchedEffect
        ExternalNav.pending.value = null
        push(next)
    }
    BackHandler(enabled = stack.size == 1 && stack.first() == Screen.Settings) { tab(Tab.CASES) }

    val top = stack.last()
    val tabs = top == Screen.Home || top == Screen.Settings

    Box(
        Modifier
            .fillMaxSize()
            .background(B.c.bg),
    ) {
        AnimatedContent(
            targetState = stack,
            contentKey = { it.last() },
            transitionSpec = {
                val from = initialState.size
                val to = targetState.size
                when {
                    to > from ->
                        (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 10 } + fadeOut(tween(200)))
                    to < from ->
                        (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 10 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 4 } + fadeOut(tween(200)))
                    else -> fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                }
            },
            label = "nav",
        ) { st ->
            when (val s = st.last()) {
                Screen.Home -> HomeScreen(
                    store = store,
                    data = data,
                    player = player,
                    resumeTick = resumeTick,
                    onOpenCase = { push(Screen.Case(it)) },
                    onOpenPerson = { push(Screen.Person(it)) },
                    onSettings = { tab(Tab.SETTINGS) },
                )
                Screen.Settings -> SettingsScreen(store = store, data = data, resumeTick = resumeTick)
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
                    onIssue = { id, src -> push(Screen.IssueEdit(s.caseNo, issueId = id, source = src)) },
                    onAlbum = { push(Screen.Album(s.caseNo)) },
                    onPhoto = { ref, id -> push(Screen.Photo(ref, id)) },
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
                    onIssue = { caseNo, src -> push(Screen.IssueEdit(caseNo, source = src)) },
                    onPhoto = { ref -> push(Screen.Photo(ref, null)) },
                )
                is Screen.IssueEdit -> IssueEditorScreen(
                    store = store,
                    data = data,
                    player = player,
                    caseNo = s.caseNo,
                    issueId = s.issueId,
                    initialKind = s.kind,
                    source = s.source,
                    onBack = { pop() },
                )
                is Screen.Album -> AlbumClassifyScreen(store = store, data = data, caseNo = s.caseNo, onBack = { pop() })
                is Screen.Photo -> PhotoViewScreen(store = store, ref = s.ref, photoId = s.photoId, onBack = { pop() })
            }
        }

        val showPlayer = top == Screen.Home || top == Screen.Settings || top is Screen.Case || top is Screen.Person
        // 녹음을 듣다가 "이 지점에" 특이사항
        store.version.intValue
        val markCases = player.number?.let { store.linksForNumber(it) }.orEmpty()
        val onMark: (() -> Unit)? = if (markCases.isEmpty() || player.currentKey == null) null else {
            {
                val src = IssueSource(
                    "rec", player.number.orEmpty(), player.recTime, player.currentKey.orEmpty(),
                    player.positionMs, player.durationMs,
                )
                if (player.isPlaying) player.playPause()
                val here = (top as? Screen.Case)?.caseNo?.takeIf { cn -> markCases.any { it.caseNo == cn } }
                when {
                    here != null -> push(Screen.IssueEdit(here, source = src))
                    markCases.size == 1 -> push(Screen.IssueEdit(markCases.first().caseNo, source = src))
                    else -> markPick = src
                }
            }
        }
        if (showPlayer) {
            MiniPlayer(
                player = player,
                onMark = onMark,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (tabs) 92.dp else 0.dp),
            )
        }
        if (tabs) {
            FloatingTabBar(
                selected = if (top == Screen.Settings) Tab.SETTINGS else Tab.CASES,
                onTab = { tab(it) },
                onAdd = { push(Screen.Register()) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
    markPick?.let { src ->
        OptionsDialog(
            title = "어느 사건의 특이사항인가요?",
            subtitle = Fmt.duration(src.offsetMs ?: 0) + " 지점",
            onDismiss = { markPick = null },
            items = store.linksForNumber(src.number).map { l ->
                Option("${l.caseNo} · ${l.role}", Ic.folder) {
                    markPick = null
                    stack = stack + Screen.IssueEdit(l.caseNo, source = src)
                }
            },
        )
    }
}
