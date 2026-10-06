package com.bosang.search.call

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.bosang.search.MainActivity
import com.bosang.search.R
import com.bosang.search.data.Store

/**
 * 통화 도우미: 켜 두면 백그라운드에서 계속 살아 있으면서 통화 시작·끝을 받는다.
 * (삼성은 쉬는 앱의 통화 알림을 막을 수 있어서, 알림창에 늘 떠 있는 방식으로 붙잡아 둠)
 */
class CallAssistService : Service() {
    private var receiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        startInForeground()
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
                @Suppress("DEPRECATION")
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                val pending = goAsync()
                CallWatcher.onState(applicationContext, intent.getStringExtra(TelephonyManager.EXTRA_STATE), number) {
                    runCatching { pending.finish() }
                }
            }
        }
        ContextCompat.registerReceiver(this, r, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        receiver = r
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Store.get(this).callAssist()) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        receiver?.let { runCatching { unregisterReceiver(it) } }
        receiver = null
        super.onDestroy()
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "통화 도우미", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "통화 중 사건 정보, 통화 후 특이사항 창을 띄우려고 켜 둡니다"
                    setShowBadge(false)
                },
            )
        }
        val open = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java).putExtra("nav", "settings"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("통화 도우미 켜짐")
            .setContentText("사건 번호와 통화하면 정보를 띄우고, 끝나면 특이사항을 물어봐요")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(open)
            .build()
        ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    companion object {
        private const val CHANNEL = "call_assist"

        @Volatile var running = false
            private set

        /** 설정대로 켜거나 끔 (앱을 열 때 · 부팅할 때 · 켜고 끌 때) */
        fun sync(ctx: Context) {
            val app = ctx.applicationContext
            val want = Store.get(app).callAssist() &&
                ContextCompat.checkSelfPermission(app, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            val i = Intent(app, CallAssistService::class.java)
            runCatching {
                if (want) ContextCompat.startForegroundService(app, i) else app.stopService(i)
            }
        }
    }
}

/** 폰을 켜면 통화 도우미 다시 시작 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            CallAssistService.sync(context)
        }
    }
}
