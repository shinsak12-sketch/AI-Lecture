package com.bosang.search.call

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.Settings
import android.telephony.TelephonyManager
import com.bosang.search.core.PhoneNumbers
import com.bosang.search.data.PhoneData
import com.bosang.search.data.Store

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
    private var offhook = false

    fun enabled(ctx: Context): Boolean = Store.get(ctx).callAssist() && Settings.canDrawOverlays(ctx)

    fun onState(ctx: Context, state: String?, rawNumber: String?) {
        if (!enabled(ctx)) return
        val n = rawNumber?.let { PhoneNumbers.normalize(it) }?.takeIf { it.length >= 3 }
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (n != null) ringing = n
                CallOverlay.hideAfter(ctx)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                offhook = true
                CallOverlay.hideAfter(ctx)
                val who = n ?: ringing
                if (who != null && who != active) {
                    active = who
                    CallOverlay.showBubble(ctx, who)
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                CallOverlay.hideBubble(ctx)
                val wasCall = offhook
                val known = active
                offhook = false
                active = null
                ringing = null
                // 통화기록에 이번 통화가 적힐 때까지 잠깐 기다림
                if (wasCall) main.postDelayed({ afterCall(ctx, known) }, 1500)
            }
        }
    }

    private fun afterCall(ctx: Context, known: String?) {
        val recent = PhoneData(ctx).calls(3)
        val call = (known?.let { k -> recent.firstOrNull { it.number == k } } ?: recent.firstOrNull())
            ?.takeIf { System.currentTimeMillis() - (it.timeMillis + it.durationSec * 1000) < 10 * 60_000 }
            ?: return
        if (call.number.isEmpty()) return
        if (call.type == CallLog.Calls.MISSED_TYPE || call.type == CallLog.Calls.REJECTED_TYPE) return
        if (call.durationSec <= 0) return
        CallOverlay.showAfter(ctx, call)
    }
}
