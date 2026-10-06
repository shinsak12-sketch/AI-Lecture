package com.bosang.search.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

/** 통화 시작·끝 알림을 받는다 (기본 전화 앱은 삼성 그대로) */
class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        // 통화 끝난 뒤 통화기록을 기다리는 동안 앱이 멈추지 않게 붙잡아 둠
        val pending = goAsync()
        CallWatcher.onState(context.applicationContext, intent.getStringExtra(TelephonyManager.EXTRA_STATE), number) {
            runCatching { pending.finish() }
        }
    }
}
