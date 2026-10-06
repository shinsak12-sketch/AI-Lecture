package com.bosang.search.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

/**
 * 통화 시작·끝 알림 (앱이 꺼져 있을 때의 예비). 통화 도우미가 켜져 있으면 그쪽이 받으므로 건너뜀.
 */
class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        if (CallAssistService.running) return
        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        val pending = goAsync()
        CallWatcher.onState(context.applicationContext, intent.getStringExtra(TelephonyManager.EXTRA_STATE), number) {
            runCatching { pending.finish() }
        }
    }
}
