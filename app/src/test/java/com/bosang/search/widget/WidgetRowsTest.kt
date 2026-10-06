package com.bosang.search.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bosang.search.data.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 위젯 목록: 검색어가 있으면 결과, 없으면 최근 본 것 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetRowsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun 검색어로_사건과_사람을_찾는다() {
        val store = Store.get(ctx)
        store.upsert("26-00054321", listOf(Triple("01055556666", "피해자", "박철수")))
        val byCase = WidgetRows.build(ctx, "54321")
        assertTrue(byCase.any { it.isCase && it.target == "26-00054321" })
        val byInitials = WidgetRows.build(ctx, "ㅂㅊㅅ")
        assertTrue(byInitials.any { !it.isCase && it.target == "01055556666" && it.phone == "01055556666" })
    }

    @Test fun 검색어를_저장하면_위젯이_그걸로_그린다() {
        SearchWidget.setQuery(ctx, " 홍길 ")
        assertEquals("홍길", SearchWidget.query(ctx))
        SearchWidget.setQuery(ctx, "")
        assertEquals("", SearchWidget.query(ctx))
    }
}
