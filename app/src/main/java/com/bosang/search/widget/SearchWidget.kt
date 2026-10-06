package com.bosang.search.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.bosang.search.R
import com.bosang.search.core.Hangul
import com.bosang.search.core.PhoneNumbers
import com.bosang.search.data.PhoneData
import com.bosang.search.data.Store

/**
 * 바탕화면 위젯.
 * - 검색칸을 누르면 글자 넣는 작은 창이 뜨고, 넣은 검색어의 결과가 위젯 목록에 바로 나온다.
 *   (안드로이드 위젯 안에는 입력칸을 둘 수 없어서 입력만 잠깐 창으로)
 * - 검색어가 없으면 최근 본 사건 · 사람.
 * - 줄을 누르면 앱의 그 화면, 사람 줄의 [전화] [문자]는 앱 없이 바로.
 */
class SearchWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, views(context, id)) }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.w_list)
    }

    companion object {
        private fun prefs(ctx: Context) = ctx.getSharedPreferences("widget", Context.MODE_PRIVATE)

        fun query(ctx: Context): String = prefs(ctx).getString("q", "").orEmpty()

        /** 검색어를 바꾸고 위젯 전체(검색칸 글자 + 목록)를 다시 그림 */
        fun setQuery(ctx: Context, q: String) {
            prefs(ctx).edit().putString("q", q.trim()).apply()
            updateAll(ctx)
        }

        fun updateAll(ctx: Context) {
            runCatching {
                val m = AppWidgetManager.getInstance(ctx)
                val ids = m.getAppWidgetIds(ComponentName(ctx, SearchWidget::class.java))
                ids.forEach { id -> m.updateAppWidget(id, views(ctx, id)) }
                if (ids.isNotEmpty()) m.notifyAppWidgetViewDataChanged(ids, R.id.w_list)
            }
        }

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
            val q = query(ctx)
            v.setTextViewText(R.id.w_query, q.ifEmpty { ctx.getString(R.string.widget_hint) })
            v.setTextColor(R.id.w_query, ctx.getColor(if (q.isEmpty()) R.color.w_ink3 else R.color.w_ink))
            v.setViewVisibility(R.id.w_clear, if (q.isEmpty()) View.GONE else View.VISIBLE)
            v.setViewVisibility(R.id.w_icon, if (q.isEmpty()) View.VISIBLE else View.GONE)
            v.setTextViewText(R.id.w_label, if (q.isEmpty()) ctx.getString(R.string.widget_recent) else "'$q' 검색 결과")
            v.setTextViewText(R.id.w_empty, if (q.isEmpty()) ctx.getString(R.string.widget_empty) else "찾는 결과가 없어요")

            // 검색칸 → 글자 넣는 작은 창
            val search = PendingIntent.getActivity(
                ctx,
                2,
                Intent(ctx, QuickSearchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            v.setOnClickPendingIntent(R.id.w_search, search)
            // 지우기 → 최근 목록으로
            val clear = PendingIntent.getActivity(
                ctx,
                4,
                Intent(ctx, WidgetRouter::class.java).putExtra("act", "clear").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            v.setOnClickPendingIntent(R.id.w_clear, clear)

            val svc = Intent(ctx, WidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            @Suppress("DEPRECATION")
            v.setRemoteAdapter(R.id.w_list, svc)
            v.setEmptyView(R.id.w_list, R.id.w_empty)
            // 줄마다 할 일(열기 · 전화 · 문자)은 줄에서 채워서 WidgetRouter 로
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

/** 위젯 목록 한 줄 */
class WidgetRow(
    val title: String,
    val sub: String,
    val badge: String,
    val isCase: Boolean,
    val target: String,
    /** 전화·문자 버튼을 붙일 번호 */
    val phone: String?,
)

object WidgetRows {
    /** 검색어가 있으면 결과 (사건 · 등록한 사람 · 연락처/통화내역), 없으면 최근 본 것 */
    fun build(ctx: Context, query: String, limit: Int = 30): List<WidgetRow> {
        val store = Store.get(ctx)
        val out = ArrayList<WidgetRow>()
        val seen = HashSet<String>()
        fun addCase(c: String) {
            if (!seen.add("c:$c")) return
            val links = store.linksForCase(c)
            if (links.isEmpty()) return
            // 사건 줄: 가장 최근 특이사항이 있으면 그것을, 없으면 사람들
            val latest = store.issuesForCase(c).firstOrNull()
            out.add(
                WidgetRow(
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
        fun addPerson(n: String, contactName: String? = null, label: String? = null) {
            if (!seen.add("p:$n")) return
            val name = store.nameOf(n) ?: contactName
            val links = store.linksForNumber(n)
            out.add(
                WidgetRow(
                    title = name ?: PhoneNumbers.format(n),
                    sub = listOfNotNull(
                        if (name != null) PhoneNumbers.format(n) else "저장 안 된 번호",
                        label,
                        if (links.isNotEmpty()) links.joinToString(", ") { it.caseNo } else null,
                    ).joinToString(" · "),
                    badge = name?.trim()?.firstOrNull()?.toString() ?: "#",
                    isCase = false,
                    target = n,
                    phone = n,
                ),
            )
        }

        val q = query.trim()
        if (q.isEmpty()) {
            val cases = store.caseNos().toSet()
            store.recentKeys().forEach { k ->
                when {
                    k.startsWith("c:") && k.removePrefix("c:") in cases -> addCase(k.removePrefix("c:"))
                    k.startsWith("p:") -> addPerson(k.removePrefix("p:"))
                }
            }
            store.caseNos().forEach { if (out.size < 12) addCase(it) }
            return out.take(12)
        }
        store.searchCases(q).forEach { addCase(it) }
        store.searchPeople(q).forEach { addPerson(it) }
        val digits = q.filter { it.isDigit() }
        runCatching { PhoneData(ctx).directory() }.getOrDefault(emptyList())
            .filter { e -> (digits.length >= 3 && e.number.contains(digits)) || (e.name?.let { Hangul.matches(it, q) } == true) }
            .forEach { if (out.size < limit) addPerson(it.number, it.name, it.label) }
        if (out.isEmpty() && digits.length >= 9 && PhoneNumbers.looksLikeNumber(q)) addPerson(PhoneNumbers.normalize(q))
        return out.take(limit)
    }
}

private class Factory(private val ctx: Context) : RemoteViewsService.RemoteViewsFactory {
    private var rows: List<WidgetRow> = emptyList()

    override fun onCreate() {}
    override fun onDestroy() {}

    override fun onDataSetChanged() {
        rows = WidgetRows.build(ctx, SearchWidget.query(ctx))
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
            val vis = if (phone != null) View.VISIBLE else View.GONE
            setViewVisibility(R.id.r_call, vis)
            setViewVisibility(R.id.r_sms, vis)
            setViewVisibility(R.id.r_tag, if (phone != null) View.GONE else View.VISIBLE)
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

/** 위젯에서 누른 것을 받아 전화 · 문자 · 앱 화면으로 넘기거나 검색어를 지우고 바로 닫힘 */
class WidgetRouter : android.app.Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val i = intent
        val number = i.getStringExtra("number")
        val next = when (i.getStringExtra("act")) {
            "dial" -> number?.let { Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it")) }
            "sms" -> number?.let { Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$it")) }
            "clear" -> {
                SearchWidget.setQuery(this, "")
                null
            }
            else -> Intent(this, com.bosang.search.MainActivity::class.java).apply {
                putExtras(i)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
        if (next != null) runCatching { startActivity(next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        finish()
    }
}
