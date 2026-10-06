package com.bosang.search

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import com.bosang.search.data.IssueKind
import com.bosang.search.data.IssueSource
import com.bosang.search.ui.App
import com.bosang.search.ui.BosangTheme
import com.bosang.search.ui.ExternalNav
import com.bosang.search.ui.Screen

class MainActivity : ComponentActivity() {
    /** 앱으로 돌아올 때마다 바뀜 → 새 녹음·문자·권한 상태를 다시 읽는 신호 */
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            BosangTheme {
                App(resumeTick = resumeTick.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        com.bosang.search.call.CallAssistService.sync(this)
        com.bosang.search.remind.Reminders.sync(this)
        resumeTick.intValue = resumeTick.intValue + 1
    }

    /** 통화 끝난 뒤 창에서 누른 것 → 해당 화면 */
    private fun handle(intent: Intent?) {
        val i = intent ?: return
        val case = i.getStringExtra("case")
        val number = i.getStringExtra("number")
        // 통화 끝난 뒤 "약속도 잡았어요" → 약속 쓰기를 아래에 깔고 특이사항부터
        ExternalNav.under = if (i.getBooleanExtra("appt", false) && i.getStringExtra("nav") == "issue") {
            Screen.ApptEdit(caseNo = case, number = number, callTime = i.getLongExtra("time", 0L).takeIf { it > 0 })
        } else null
        ExternalNav.pending.value = when (i.getStringExtra("nav")) {
            "appt" -> Screen.ApptEdit(
                apptId = i.getStringExtra("id"),
                caseNo = case,
                number = number,
                callTime = i.getLongExtra("time", 0L).takeIf { it > 0 },
            )
            "issue" -> if (number != null) {
                val time = i.getLongExtra("time", 0L)
                Screen.IssueEdit(
                    caseNo = case,
                    number = number,
                    source = IssueSource("call", number, time, time.toString(), null, i.getLongExtra("len", 0L)),
                    kind = runCatching { IssueKind.valueOf(i.getStringExtra("kind").orEmpty()) }.getOrNull(),
                )
            } else null
            "register" -> number?.let { Screen.Register(numbers = listOf(it)) }
            "case" -> case?.let { Screen.Case(it) }
            "person" -> number?.let { Screen.Person(it) }
            "settings" -> Screen.Settings
            "search" -> {
                ExternalNav.focusSearch.value = true
                Screen.Home
            }
            else -> null
        }
    }
}
