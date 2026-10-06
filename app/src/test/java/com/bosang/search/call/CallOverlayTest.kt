package com.bosang.search.call

import android.content.Context
import android.os.Looper
import android.provider.CallLog
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import com.bosang.search.core.CallEntry
import com.bosang.search.data.Store
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test fun 통화중에는_아무창도_안뜬다() {
        val store = Store.get(ctx)
        store.upsert("26-00012345", listOf(Triple(number, "피보험자", "홍길동")))
        store.setCallAssist(true)
        CallWatcher.onState(ctx, TelephonyManager.EXTRA_STATE_RINGING, number) {}
        CallWatcher.onState(ctx, TelephonyManager.EXTRA_STATE_OFFHOOK, number) {}
        idle()
        assertFalse(CallOverlay.afterShowing())
        CallWatcher.onState(ctx, TelephonyManager.EXTRA_STATE_IDLE, null) {}
        idle()
    }

    @Test fun 끝난뒤창에서_예를_누르면_연결창() {
        val call = CallEntry("01055556666", "김영희", System.currentTimeMillis() - 60_000, 95, CallLog.Calls.INCOMING_TYPE)
        CallOverlay.showAfter(ctx, call, force = true)
        idle()
        val root = CallOverlay.afterView()
        assertNotNull(root)
        assertNotNull(findText(root!!, "이 통화를 사건에 연결할까요?"))
        findText(root, "예")!!.performClick()
        idle()
        assertFalse(CallOverlay.afterShowing())
        val next = shadowOf(ctx as android.app.Application).nextStartedActivity
        assertEquals(PostCallActivity::class.java.name, next.component?.className)
        assertEquals("01055556666", next.getStringExtra("number"))
        assertEquals(95_000L, next.getLongExtra("len", 0L))
    }

    @Test fun 끝난뒤창에서_아니오는_그냥_닫힘() {
        val call = CallEntry("01055557777", null, System.currentTimeMillis() - 60_000, 30, CallLog.Calls.OUTGOING_TYPE)
        CallOverlay.showAfter(ctx, call, force = true)
        idle()
        findText(CallOverlay.afterView()!!, "아니오")!!.performClick()
        idle()
        assertFalse(CallOverlay.afterShowing())
        assertNull(shadowOf(ctx as android.app.Application).nextStartedActivity)
    }

    @Test fun 사건있는번호는_특이사항을_묻는다() {
        Store.get(ctx).upsert("26-00088888", listOf(Triple("01044445555", "피해자", "이순신")))
        val call = CallEntry("01044445555", null, System.currentTimeMillis() - 60_000, 61, CallLog.Calls.INCOMING_TYPE)
        CallOverlay.showAfter(ctx, call, force = true)
        idle()
        val root = CallOverlay.afterView()!!
        assertNotNull(findText(root, "특이사항이 있었나요?"))
        assertNull(findText(root, "이 통화를 사건에 연결할까요?"))
    }

    private fun findText(v: android.view.View, text: String): android.view.View? {
        if (v is android.widget.TextView && v.text.toString() == text) return v
        if (v is android.view.ViewGroup) {
            for (i in 0 until v.childCount) findText(v.getChildAt(i), text)?.let { return it }
        }
        return null
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
