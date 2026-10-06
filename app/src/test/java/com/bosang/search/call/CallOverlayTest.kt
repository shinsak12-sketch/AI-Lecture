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

    @Test fun 사건없는번호는_통화중_말풍선없음() {
        Store.get(ctx).setCallAssist(true)
        CallWatcher.onState(ctx, TelephonyManager.EXTRA_STATE_OFFHOOK, "01099990000") {}
        idle()
        assertFalse(CallOverlay.bubbleShowing())
        CallWatcher.onState(ctx, TelephonyManager.EXTRA_STATE_IDLE, null) {}
        idle()
    }
}
