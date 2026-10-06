package com.bosang.search.call

import android.content.Context
import android.os.Looper
import android.provider.CallLog
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import com.bosang.search.core.CallEntry
import com.bosang.search.data.Store
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/** 통화 창이 실제로 화면(WindowManager)에 붙어 있는지, 바로 닫히지 않는지 확인 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallOverlayTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val number = "01011112222"

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Before fun setUp() {
        ShadowSettings.setCanDrawOverlays(true)
    }

    @After fun tearDown() {
        CallOverlay.hideAfter(ctx)
        CallOverlay.hideBubble(ctx)
        idle()
    }

    @Test fun 통화끝난뒤창은_띄운뒤에도_남아있다() {
        val call = CallEntry(number, "홍길동", System.currentTimeMillis() - 60_000, 125, CallLog.Calls.INCOMING_TYPE)
        CallOverlay.showAfter(ctx, call, force = true)
        idle()
        assertTrue(CallOverlay.afterShowing())
        CallOverlay.hideAfter(ctx)
        idle()
        assertFalse(CallOverlay.afterShowing())
    }

    @Test fun 사건번호와_통화하면_말풍선_끝나면_닫힘() {
        val store = Store.get(ctx)
        store.upsert("26-00012345", listOf(Triple(number, "피보험자", "홍길동")))
        store.setCallAssist(true)
        CallWatcher.onState(ctx, TelephonyManager.EXTRA_STATE_RINGING, number) {}
        CallWatcher.onState(ctx, TelephonyManager.EXTRA_STATE_OFFHOOK, number) {}
        idle()
        assertTrue(CallOverlay.bubbleShowing())
        CallWatcher.onState(ctx, TelephonyManager.EXTRA_STATE_IDLE, null) {}
        idle()
        assertFalse(CallOverlay.bubbleShowing())
    }

    @Test fun 사건없는번호도_통화중_작은동그라미() {
        Store.get(ctx).setCallAssist(true)
        CallWatcher.onState(ctx, TelephonyManager.EXTRA_STATE_OFFHOOK, "01099990000") {}
        idle()
        assertTrue(CallOverlay.bubbleShowing())
        CallWatcher.onState(ctx, TelephonyManager.EXTRA_STATE_IDLE, null) {}
        idle()
        assertFalse(CallOverlay.bubbleShowing())
    }

    @Test fun 내가건전화_번호가_안와도_앱에서_건번호로_말풍선() {
        val store = Store.get(ctx)
        store.upsert("26-00077777", listOf(Triple("01033334444", "피해자", "박철수")))
        store.setCallAssist(true)
        CallWatcher.rememberDial(ctx, "010-3333-4444")
        CallWatcher.onState(ctx, TelephonyManager.EXTRA_STATE_OFFHOOK, null, "직접")
        idle()
        assertTrue(CallOverlay.bubbleShowing())
        CallWatcher.onState(ctx, TelephonyManager.EXTRA_STATE_IDLE, null, "직접")
        idle()
    }

    @Test fun 같은통화는_끝난뒤창_한번만() {
        Store.get(ctx).setCallAssist(true)
        val call = CallEntry("01077778888", null, System.currentTimeMillis() - 30_000, 20, CallLog.Calls.OUTGOING_TYPE)
        assertTrue(CallWatcher.ended(ctx, call, "통화기록"))
        idle()
        assertTrue(CallOverlay.afterShowing())
        CallOverlay.hideAfter(ctx)
        idle()
        assertFalse(CallWatcher.ended(ctx, call, "통화 상태"))
        idle()
        assertFalse(CallOverlay.afterShowing())
    }
}
