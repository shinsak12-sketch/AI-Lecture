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
 * 통화 상태를 따라가며 통화가 끝나면 "사건에 연결 / 특이사항" 창을 띄운다.
 * (통화 중 창은 기기마다 막혀서 빼고, 통화 중에는 번호만 기억해 둔다)
 *
 * 통화 끝은 두 길로 안다: ① 통화 상태(알림·직접 구독) ② 통화기록에 새 줄이 생김.
 * 어느 쪽이 먼저 오든 같은 통화로는 창을 한 번만 띄운다.
 */
object CallWatcher {
    private val main = Handler(Looper.getMainLooper())
    private var ringing: String? = null
    private var active: String? = null

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("call", Context.MODE_PRIVATE)
    private fun offhookAt(ctx: Context): Long = prefs(ctx).getLong("offAt", 0L)

    fun enabled(ctx: Context): Boolean = Store.get(ctx).callAssist() && Settings.canDrawOverlays(ctx)

    // ───────────── 감지 기록 (설정 화면에서 보여줌) ─────────────

    fun log(ctx: Context, what: String) {
        val t = SimpleDateFormat("M/d HH:mm:ss", Locale.KOREAN).format(Date())
        val p = prefs(ctx)
        val old = p.getString("log", "").orEmpty().lines().filter { it.isNotBlank() }
        p.edit().putString("log", (listOf("$t  $what") + old).take(12).joinToString("\n")).putString("last", "$t $what").apply()
    }

    fun events(ctx: Context): List<String> = prefs(ctx).getString("log", "").orEmpty().lines().filter { it.isNotBlank() }

    fun lastEvent(ctx: Context): String? = prefs(ctx).getString("last", null)

    // ───────────── 앱에서 [전화]로 건 번호 (내가 건 전화는 통화 중 번호가 안 올 수 있어서) ─────────────

    fun rememberDial(ctx: Context, number: String) {
        prefs(ctx).edit().putString("dial", PhoneNumbers.normalize(number)).putLong("dialAt", System.currentTimeMillis()).apply()
    }

    private fun dialHint(ctx: Context): String? {
        val p = prefs(ctx)
        val at = p.getLong("dialAt", 0L)
        return if (System.currentTimeMillis() - at < 2 * 60_000) p.getString("dial", null)?.takeIf { it.isNotEmpty() } else null
    }

    // ───────────── 통화 상태 ─────────────

    fun onState(ctx: Context, state: String?, rawNumber: String?, via: String = "알림", done: () -> Unit = {}) {
        val n = rawNumber?.let { PhoneNumbers.normalize(it) }?.takeIf { it.length >= 3 }
        log(ctx, "$via · " + (stateLabel(state) ?: "알 수 없음") + if (n != null) " · 번호 받음" else "")
        if (!enabled(ctx)) {
            log(ctx, "도우미 꺼짐 또는 '다른 앱 위에 표시' 꺼짐")
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
                CallOverlay.hideAfter(ctx)
                val who = n ?: ringing ?: dialHint(ctx)
                if (who != null) prefs(ctx).edit().putString("who", who).apply()
                if (who != null) active = who
                done()
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
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
            log(ctx, "통화 끝 · 통화기록을 아직 못 찾음 (통화기록 쪽에서 다시 확인)")
            done()
            return
        }
        main.postDelayed({
            val call = findCall(ctx, known, since)
            if (call == null) {
                tryAfter(ctx, known, since, waits.drop(1), done)
            } else {
                ended(ctx, call, "통화 상태")
                main.postDelayed(done, 1500)
            }
        }, waits.first())
    }

    private fun findCall(ctx: Context, known: String?, since: Long): CallEntry? {
        val recent = PhoneData(ctx).calls(5).filter { it.number.isNotEmpty() && it.timeMillis >= since }
        return known?.let { k -> recent.firstOrNull { it.number == k } } ?: recent.firstOrNull()
    }

    // ───────────── 통화기록에 새 줄이 생김 ─────────────

    private var pendingCtx: Context? = null
    private val logCheck = Runnable {
        val ctx = pendingCtx ?: return@Runnable
        if (!enabled(ctx)) return@Runnable
        val latest = PhoneData(ctx).calls(3).firstOrNull { it.number.isNotEmpty() } ?: return@Runnable
        val endedAt = latest.timeMillis + latest.durationSec * 1000
        if (System.currentTimeMillis() - endedAt < 3 * 60_000) ended(ctx, latest, "통화기록")
    }

    fun onCallLogChanged(ctx: Context) {
        pendingCtx = ctx.applicationContext
        main.removeCallbacks(logCheck)
        main.postDelayed(logCheck, 800)
    }

    /** 끝난 통화 하나 → 창. 같은 통화는 한 번만 */
    fun ended(ctx: Context, call: CallEntry, via: String): Boolean {
        val p = prefs(ctx)
        if (p.getLong("shownAt", 0L) == call.timeMillis) return false
        p.edit().putLong("shownAt", call.timeMillis).apply()
        if (call.durationSec <= 0 || call.type == CallLog.Calls.MISSED_TYPE || call.type == CallLog.Calls.REJECTED_TYPE) {
            log(ctx, "$via · 부재중이거나 연결 안 된 통화라 건너뜀")
            return false
        }
        CallOverlay.showAfter(ctx, call)
        log(ctx, "$via · 통화 끝 창 · " + Store.get(ctx).displayName(call.number))
        return true
    }

    /** 설정의 [창 미리 보기]: 가장 최근 통화로 통화 끝난 뒤 창을 띄움 */
    fun preview(ctx: Context): Boolean {
        if (!Settings.canDrawOverlays(ctx)) return false
        val call = PhoneData(ctx).calls(10).firstOrNull { it.number.isNotEmpty() && it.durationSec > 0 } ?: return false
        CallOverlay.showAfter(ctx, call, force = true)
        log(ctx, "미리 보기 · " + Store.get(ctx).displayName(call.number))
        return true
    }

    fun stateLabel(s: String?) = when (s) {
        TelephonyManager.EXTRA_STATE_RINGING -> "전화 옴"
        TelephonyManager.EXTRA_STATE_OFFHOOK -> "통화 중"
        TelephonyManager.EXTRA_STATE_IDLE -> "통화 끝"
        else -> null
    }
}
