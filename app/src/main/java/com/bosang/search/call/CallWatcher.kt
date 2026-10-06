package com.bosang.search.call

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.Settings
import android.telephony.TelephonyManager
import com.bosang.search.core.CallEntry
import com.bosang.search.core.PhoneNumbers
import com.bosang.search.data.PhoneData
import com.bosang.search.data.Store
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 통화 상태를 따라가며
 * - 사건에 연결된 번호와 통화가 시작되면 화면 위에 사건 정보를 띄우고
 * - 통화가 끝나면 "특이사항 남기기" 창을 띄운다.
 * 같은 알림이 두 번씩 오기도 해서 겹치지 않게 막는다.
 */
object CallWatcher {
    private val main = Handler(Looper.getMainLooper())
    private var ringing: String? = null
    private var active: String? = null

    // 통화 중에 앱이 꺼져도 통화가 끝났을 때 알 수 있게 파일에 적어 둠
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("call", Context.MODE_PRIVATE)
    private fun offhookAt(ctx: Context): Long = prefs(ctx).getLong("offAt", 0L)

    fun enabled(ctx: Context): Boolean = Store.get(ctx).callAssist() && Settings.canDrawOverlays(ctx)

    /** 설정 화면에 보여줄 마지막 감지 기록 */
    fun lastEvent(ctx: Context): String? =
        ctx.getSharedPreferences("call", Context.MODE_PRIVATE).getString("last", null)

    private fun note(ctx: Context, what: String) {
        val t = SimpleDateFormat("M/d HH:mm:ss", Locale.KOREAN).format(Date())
        ctx.getSharedPreferences("call", Context.MODE_PRIVATE).edit().putString("last", "$t $what").apply()
    }

    fun onState(ctx: Context, state: String?, rawNumber: String?, done: () -> Unit) {
        val n = rawNumber?.let { PhoneNumbers.normalize(it) }?.takeIf { it.length >= 3 }
        note(ctx, (stateLabel(state) ?: "알 수 없음") + if (n != null) " · 번호 받음" else "")
        if (!enabled(ctx)) {
            done()
            return
        }
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (n != null) ringing = n
                CallOverlay.hideAfter(ctx)
                done()
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (offhookAt(ctx) == 0L) prefs(ctx).edit().putLong("offAt", System.currentTimeMillis()).apply()
                if (n != null) prefs(ctx).edit().putString("who", n).apply()
                CallOverlay.hideAfter(ctx)
                val who = n ?: ringing
                if (who != null && who != active) {
                    active = who
                    CallOverlay.showBubble(ctx, who)
                }
                done()
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                CallOverlay.hideBubble(ctx)
                val at = offhookAt(ctx)
                if (at == 0L) {
                    ringing = null
                    done()
                    return
                }
                val known = active ?: prefs(ctx).getString("who", null)
                val since = at - 90_000
                prefs(ctx).edit().remove("offAt").remove("who").apply()
                active = null
                ringing = null
                // 통화기록에 이번 통화가 적힐 때까지 몇 번 다시 봄
                tryAfter(ctx, known, since, listOf(1200L, 2500L, 4000L), done)
            }
            else -> done()
        }
    }

    private fun tryAfter(ctx: Context, known: String?, since: Long, waits: List<Long>, done: () -> Unit) {
        if (waits.isEmpty()) {
            note(ctx, "통화 끝 · 통화기록을 못 찾음")
            done()
            return
        }
        main.postDelayed({
            val call = findCall(ctx, known, since)
            if (call == null) {
                tryAfter(ctx, known, since, waits.drop(1), done)
            } else {
                if (call.durationSec > 0 && call.type != CallLog.Calls.MISSED_TYPE && call.type != CallLog.Calls.REJECTED_TYPE) {
                    CallOverlay.showAfter(ctx, call)
                    note(ctx, "통화 끝 · 창 띄움")
                }
                // 창을 띄우고 잠깐 더 붙잡아 둠
                main.postDelayed(done, 1500)
            }
        }, waits.first())
    }

    private fun findCall(ctx: Context, known: String?, since: Long): CallEntry? {
        val recent = PhoneData(ctx).calls(5).filter { it.number.isNotEmpty() && it.timeMillis >= since }
        return known?.let { k -> recent.firstOrNull { it.number == k } } ?: recent.firstOrNull()
    }

    /** 설정의 [창 미리 보기]: 가장 최근 통화로 통화 끝난 뒤 창을 띄움 */
    fun preview(ctx: Context): Boolean {
        if (!Settings.canDrawOverlays(ctx)) return false
        val call = PhoneData(ctx).calls(10).firstOrNull { it.number.isNotEmpty() && it.durationSec > 0 } ?: return false
        CallOverlay.showAfter(ctx, call, force = true)
        return true
    }

    private fun stateLabel(s: String?) = when (s) {
        TelephonyManager.EXTRA_STATE_RINGING -> "전화 옴"
        TelephonyManager.EXTRA_STATE_OFFHOOK -> "통화 중"
        TelephonyManager.EXTRA_STATE_IDLE -> "통화 끝"
        else -> null
    }
}
