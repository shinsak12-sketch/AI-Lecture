package com.bosang.search.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.bosang.search.R
import com.bosang.search.core.PhoneNumbers
import com.bosang.search.data.Store

/**
 * 바탕화면 위젯: 위는 검색칸(누르면 앱 검색이 키보드와 함께 열림), 아래는 최근 본 사건·사람.
 * 위젯 안에서는 글자를 칠 수 없어서 검색은 앱에서 한다.
 */
class SearchWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, views(context, id)) }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.w_list)
    }

    companion object {
        /** 저장된 내용이 바뀌면 위젯 목록도 다시 */
        fun refresh(ctx: Context) {
            runCatching {
                val m = AppWidgetManager.getInstance(ctx)
                val ids = m.getAppWidgetIds(ComponentName(ctx, SearchWidget::class.java))
                if (ids.isNotEmpty()) m.notifyAppWidgetViewDataChanged(ids, R.id.w_list)
            }
        }

        private fun views(ctx: Context, id: Int): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_search)
            // 검색칸: 앱을 열지 않고 홈 화면 위에 작은 검색창
            val search = PendingIntent.getActivity(
                ctx,
                2,
                Intent(ctx, QuickSearchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            v.setOnClickPendingIntent(R.id.w_search, search)

            val svc = Intent(ctx, WidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            @Suppress("DEPRECATION")
            v.setRemoteAdapter(R.id.w_list, svc)
            v.setEmptyView(R.id.w_list, R.id.w_empty)
            // 줄마다 할 일(열기 · 전화 · 문자)은 아래 줄에서 채워서 WidgetRouter 로
            val template = PendingIntent.getActivity(
                ctx,
                3,
                Intent(ctx, WidgetRouter::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            v.setPendingIntentTemplate(R.id.w_list, template)
            return v
        }
    }
}

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)
}

private class Row(
    val title: String,
    val sub: String,
    val badge: String,
    val isCase: Boolean,
    val target: String,
    /** 전화·문자 버튼을 붙일 번호 */
    val phone: String?,
)

private class Factory(private val ctx: Context) : RemoteViewsService.RemoteViewsFactory {
    private var rows: List<Row> = emptyList()

    override fun onCreate() {}
    override fun onDestroy() {}

    override fun onDataSetChanged() {
        val store = Store.get(ctx)
        val out = ArrayList<Row>()
        val cases = store.caseNos().toSet()
        val seen = HashSet<String>()
        fun addCase(c: String) {
            if (!seen.add("c:$c")) return
            val links = store.linksForCase(c)
            if (links.isEmpty()) return
            // 사건 줄: 가장 최근 특이사항이 있으면 그것을, 없으면 사람들
            val latest = store.issuesForCase(c).firstOrNull()
            out.add(
                Row(
                    title = c,
                    sub = latest?.let { it.kind.label + " · " + it.summary { n -> store.displayName(n) } }
                        ?: links.joinToString(" · ") { store.displayName(it.number) + " " + it.role },
                    badge = store.nameOf(links.first().number)?.trim()?.firstOrNull()?.toString() ?: "#",
                    isCase = true,
                    target = c,
                    phone = links.singleOrNull()?.number,
                ),
            )
        }
        fun addPerson(n: String) {
            if (!seen.add("p:$n")) return
            val name = store.nameOf(n)
            val links = store.linksForNumber(n)
            out.add(
                Row(
                    title = name ?: PhoneNumbers.format(n),
                    sub = (if (name != null) PhoneNumbers.format(n) else "저장 안 된 번호") +
                        (if (links.isNotEmpty()) " · " + links.joinToString(", ") { it.caseNo } else ""),
                    badge = name?.trim()?.firstOrNull()?.toString() ?: "#",
                    isCase = false,
                    target = n,
                    phone = n,
                ),
            )
        }
        store.recentKeys().forEach { k ->
            when {
                k.startsWith("c:") && k.removePrefix("c:") in cases -> addCase(k.removePrefix("c:"))
                k.startsWith("p:") -> addPerson(k.removePrefix("p:"))
            }
        }
        store.caseNos().forEach { if (out.size < 12) addCase(it) }
        rows = out.take(12)
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val r = rows.getOrNull(position) ?: return RemoteViews(ctx.packageName, R.layout.widget_row)
        return RemoteViews(ctx.packageName, R.layout.widget_row).apply {
            setTextViewText(R.id.r_title, r.title)
            setTextViewText(R.id.r_sub, r.sub)
            setTextViewText(R.id.r_badge, r.badge)
            setTextViewText(R.id.r_tag, if (r.isCase) "사건" else "사람")
            setInt(R.id.r_badge, "setBackgroundResource", if (r.isCase) R.drawable.widget_badge_case else R.drawable.widget_badge_person)
            val fill = Intent().apply {
                if (r.isCase) putExtra("nav", "case").putExtra("case", r.target)
                else putExtra("nav", "person").putExtra("number", r.target)
            }
            setOnClickFillInIntent(R.id.r_root, fill)
            val phone = r.phone
            val vis = if (phone != null) android.view.View.VISIBLE else android.view.View.GONE
            setViewVisibility(R.id.r_call, vis)
            setViewVisibility(R.id.r_sms, vis)
            setViewVisibility(R.id.r_tag, if (phone != null) android.view.View.GONE else android.view.View.VISIBLE)
            if (phone != null) {
                setOnClickFillInIntent(R.id.r_call, Intent().putExtra("act", "dial").putExtra("number", phone))
                setOnClickFillInIntent(R.id.r_sms, Intent().putExtra("act", "sms").putExtra("number", phone))
            }
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
}

/** 위젯에서 누른 것을 받아 전화 · 문자 · 앱 화면으로 넘기고 바로 닫힘 */
class WidgetRouter : android.app.Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val i = intent
        val number = i.getStringExtra("number")
        val next = when (i.getStringExtra("act")) {
            "dial" -> number?.let { Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it")) }
            "sms" -> number?.let { Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$it")) }
            else -> Intent(this, com.bosang.search.MainActivity::class.java).apply {
                putExtras(i)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
        if (next != null) runCatching { startActivity(next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        finish()
    }
}
