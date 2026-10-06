package com.bosang.search.call

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.bosang.search.MainActivity
import com.bosang.search.R
import com.bosang.search.core.CallEntry
import com.bosang.search.core.PhoneNumbers
import com.bosang.search.data.IssueKind
import com.bosang.search.data.PhoneData
import com.bosang.search.data.Store

/**
 * 통화 화면 위에 뜨는 창 두 가지 (안드로이드 보통 View 로 그림).
 * - 통화 중: 사건 정보 말풍선 (접으면 동그라미)
 * - 통화 후: 특이사항 남기기 / 사건에 연결
 */
object CallOverlay {
    private val main = Handler(Looper.getMainLooper())
    private var bubble: View? = null
    private var after: View? = null
    private val hideAfterTask = Runnable { closeAfterNow() }

    /** 메인 스레드면 바로, 아니면 메인으로 넘겨서. (예전엔 늘 미뤄서, 새 창을 띄운 직후 "이전 창 닫기"가 새 창을 닫아버렸음) */
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private fun closeBubbleNow() {
        val v = bubble
        bubble = null
        v?.let { remove(it.context, it) }
    }

    private fun closeAfterNow() {
        main.removeCallbacks(hideAfterTask)
        val v = after
        after = null
        v?.let { remove(it.context, it) }
    }

    /** 테스트 · 진단용 */
    fun bubbleShowing(): Boolean = bubble != null
    fun afterShowing(): Boolean = after != null

    // 색 (시안 v3)
    private const val INK = 0xFF0C1222.toInt()
    private const val INK2 = 0xFF5A6276.toInt()
    private const val INK3 = 0xFF9AA1B1.toInt()
    private const val CHIP = 0xFFF1F3F8.toInt()
    private const val BG = 0xFFECEEF4.toInt()
    private const val BRAND = 0xFF3360FF.toInt()
    private const val BRAND2 = 0xFF6A8CFF.toInt()
    private const val BRAND_TINT = 0x1A3360FF
    private const val REC = 0xFFFF4B5C.toInt()

    private fun kindColor(k: IssueKind): Int = when (k) {
        IssueKind.FAULT -> BRAND
        IssueKind.AMOUNT -> 0xFF14AE7A.toInt()
        IssueKind.DAMAGE -> REC
        IssueKind.STATEMENT -> 0xFF7B5CF0.toInt()
        IssueKind.SITE -> 0xFF0FA3B1.toInt()
        IssueKind.ETC -> INK2
    }

    // ───────────── 통화 중 ─────────────

    fun showBubble(ctx: Context, number: String) {
        onMain {
            val store = Store.get(ctx)
            closeBubbleNow()
            if (store.linksForNumber(number).isEmpty() && store.issuesForNumber(number).isEmpty()) {
                // 사건 없는 번호: 방해되지 않게 작은 동그라미만 (누르면 [사건에 연결])
                collapse(ctx, store, number)
                return@onMain
            }
            val v = bubbleCard(ctx, store, number)
            if (add(ctx, v, Gravity.TOP, ctx.dp(200f))) bubble = v
        }
    }

    fun hideBubble(ctx: Context) {
        onMain { closeBubbleNow() }
    }

    private fun bubbleCard(ctx: Context, store: Store, number: String): View {
        val links = store.linksForNumber(number)
        val name = store.displayName(number)
        val issues = relatedIssues(store, number).take(3)

        val root = FrameLayout(ctx).apply { setPadding(ctx.dp(12f), 0, ctx.dp(12f), 0) }
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, ctx.dp(22f).toFloat())
            elevation = ctx.dp(16f).toFloat()
            clipToOutline = true
        }
        // 머리
        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(12f), ctx.dp(11f), ctx.dp(10f), ctx.dp(11f))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF0A1330.toInt(), 0xFF132459.toInt()))
        }
        head.addView(logo(ctx, 30f))
        val titles = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(10f), 0, ctx.dp(8f), 0)
        }
        titles.addView(text(ctx, when (links.size) { 0 -> name; 1 -> links[0].caseNo; else -> "사건 ${links.size}개" }, 15f, 2, Color.WHITE))
        titles.addView(
            text(ctx, if (links.isEmpty()) "사건 없음 · 특이사항 ${issues.size}" else "$name · " + links.joinToString(", ") { it.role }, 12f, 1, 0xA6FFFFFF.toInt()),
        )
        head.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(iconButton(ctx, R.drawable.ic_close, 0x24FFFFFF) { collapse(ctx, store, number) })
        card.addView(head)

        // 특이사항
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(14f), ctx.dp(6f), ctx.dp(14f), ctx.dp(4f))
        }
        if (issues.isEmpty()) {
            body.addView(
                text(ctx, if (links.isEmpty()) "사건에 연결되지 않은 번호예요" else "아직 남긴 특이사항이 없어요", 13.5f, 1, INK2)
                    .apply { setPadding(0, ctx.dp(10f), 0, ctx.dp(10f)) },
            )
        } else {
            issues.forEach { iss ->
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.TOP
                    setPadding(0, ctx.dp(9f), 0, ctx.dp(9f))
                }
                val col = kindColor(iss.kind)
                row.addView(
                    text(ctx, iss.kind.label, 11.5f, 2, col).apply {
                        background = rounded((col and 0x00FFFFFF) or 0x1F000000, ctx.dp(7f).toFloat())
                        setPadding(ctx.dp(7f), ctx.dp(3f), ctx.dp(7f), ctx.dp(3f))
                    },
                )
                val tx = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(ctx.dp(9f), 0, 0, 0)
                }
                tx.addView(text(ctx, iss.summary { store.displayName(it) }, 14f, 2, INK, lines = 2))
                iss.firstLine()?.let { tx.addView(text(ctx, it, 12f, 1, INK2, lines = 1)) }
                row.addView(tx, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                body.addView(row)
            }
        }
        card.addView(body)

        val foot = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(ctx.dp(12f), ctx.dp(4f), ctx.dp(12f), ctx.dp(12f))
        }
        foot.addView(button(ctx, "접기", false) { collapse(ctx, store, number) }, weighted(ctx, right = 4f))
        foot.addView(
            button(ctx, when (links.size) { 0 -> "사건에 연결"; 1 -> "사건 보기"; else -> "기록 보기" }, true) {
                val i = Intent(ctx, MainActivity::class.java)
                when (links.size) {
                    0 -> i.putExtra("nav", "register").putExtra("number", number)
                    1 -> i.putExtra("nav", "case").putExtra("case", links[0].caseNo)
                    else -> i.putExtra("nav", "person").putExtra("number", number)
                }
                open(ctx, i)
            },
            weighted(ctx, left = 4f),
        )
        card.addView(foot)
        root.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return root
    }

    /** 접으면 오른쪽에 작은 동그라미, 누르면 다시 펼침 */
    private fun collapse(ctx: Context, store: Store, number: String) {
        closeBubbleNow()
        val count = relatedIssues(store, number).size
        val root = FrameLayout(ctx).apply { setPadding(ctx.dp(6f), ctx.dp(6f), ctx.dp(6f), ctx.dp(6f)) }
        val dot = FrameLayout(ctx).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF7A9CFF.toInt(), 0xFF2F55E6.toInt())).apply { shape = GradientDrawable.OVAL }
            elevation = ctx.dp(10f).toFloat()
            setOnClickListener {
                closeBubbleNow()
                val v = bubbleCard(ctx, store, number)
                if (add(ctx, v, Gravity.TOP, ctx.dp(200f))) bubble = v
            }
        }
        dot.addView(
            ImageView(ctx).apply { setImageResource(R.drawable.ic_launcher_foreground) },
            FrameLayout.LayoutParams(ctx.dp(52f), ctx.dp(52f), Gravity.CENTER),
        )
        root.addView(dot, FrameLayout.LayoutParams(ctx.dp(50f), ctx.dp(50f)))
        if (count > 0) {
            root.addView(
                text(ctx, "$count", 11f, 2, Color.WHITE).apply {
                    gravity = Gravity.CENTER
                    background = rounded(REC, ctx.dp(9f).toFloat())
                    setPadding(ctx.dp(5f), 0, ctx.dp(5f), 0)
                    elevation = ctx.dp(11f).toFloat()
                },
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ctx.dp(18f), Gravity.TOP or Gravity.END),
            )
        }
        if (add(ctx, root, Gravity.TOP or Gravity.END, ctx.dp(170f), width = ViewGroup.LayoutParams.WRAP_CONTENT)) bubble = root
    }

    // ───────────── 통화 후 ─────────────

    fun showAfter(ctx: Context, call: CallEntry, force: Boolean = false) {
        onMain {
            val store = Store.get(ctx)
            val links = store.linksForNumber(call.number)
            if (!force && links.isEmpty() && store.isQuiet(call.number)) return@onMain
            closeAfterNow()
            val v = afterCard(ctx, store, call)
            if (add(ctx, v, Gravity.BOTTOM, ctx.dp(16f))) {
                after = v
                main.postDelayed(hideAfterTask, 90_000)
            }
        }
    }

    fun hideAfter(ctx: Context) {
        onMain { closeAfterNow() }
    }

    private fun afterCard(ctx: Context, store: Store, call: CallEntry): View {
        val links = store.linksForNumber(call.number)
        val name = store.nameOf(call.number) ?: call.name?.takeIf { it.isNotBlank() } ?: PhoneData(ctx).contactName(call.number)
        val shown = name ?: PhoneNumbers.format(call.number)
        var chosen = links.firstOrNull()?.caseNo

        val root = FrameLayout(ctx).apply { setPadding(ctx.dp(10f), 0, ctx.dp(10f), 0) }
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(BG, ctx.dp(28f).toFloat())
            elevation = ctx.dp(18f).toFloat()
            setPadding(ctx.dp(16f), ctx.dp(16f), ctx.dp(16f), ctx.dp(16f))
        }
        // 누구와 몇 분
        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(avatar(ctx, name, call.number))
        val tcol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(12f), 0, ctx.dp(6f), 0)
        }
        val josa = shown.lastOrNull()?.let { ch -> if (ch in '가'..'힣' && (ch - '가') % 28 != 0) "과" else "와" } ?: "와"
        tcol.addView(text(ctx, "$shown$josa ${duration(call.durationSec)} 통화", 17f, 2, INK, lines = 1))
        tcol.addView(text(ctx, "방금 · " + if (call.type == CallLog.Calls.INCOMING_TYPE) "받은 전화" else "건 전화", 12.5f, 1, INK2))
        top.addView(tcol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(iconButton(ctx, R.drawable.ic_close, 0x1F0C1222, tint = INK2) { hideAfter(ctx) })
        card.addView(top)

        if (links.isNotEmpty()) {
            // 지금 연결된 사건 (여럿이면 골라서, 특이사항은 번호에 붙고 사건은 열 화면만 정함)
            val chips = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, ctx.dp(12f), 0, 0)
            }
            val chipViews = ArrayList<TextView>()
            links.forEach { l ->
                val chip = text(ctx, "${l.caseNo}   ${l.role}", 14.5f, 2, INK).apply {
                    setPadding(ctx.dp(12f), ctx.dp(10f), ctx.dp(12f), ctx.dp(10f))
                }
                chipViews.add(chip)
                fun paint() = chipViews.forEachIndexed { i, cv ->
                    val on = links[i].caseNo == chosen
                    cv.background = rounded(Color.WHITE, ctx.dp(14f).toFloat(), stroke = if (on && links.size > 1) BRAND else 0)
                }
                chip.setOnClickListener {
                    chosen = l.caseNo
                    paint()
                }
                chips.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ctx.dp(6f) })
                paint()
            }
            card.addView(chips)
        } else {
            card.addView(text(ctx, "사건에 연결되지 않은 번호예요", 13f, 1, INK2).apply { setPadding(ctx.dp(4f), ctx.dp(10f), 0, 0) })
        }
        card.addView(text(ctx, "특이사항이 있었나요?", 12.5f, 2, INK2).apply { setPadding(ctx.dp(4f), ctx.dp(10f), 0, ctx.dp(8f)) })
        IssueKind.entries.chunked(3).forEach { rowKinds ->
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            rowKinds.forEachIndexed { i, k ->
                val b = text(ctx, if (k == IssueKind.DAMAGE) "파손" else k.label, 14f, 2, INK).apply {
                    gravity = Gravity.CENTER
                    background = rounded(Color.WHITE, ctx.dp(15f).toFloat())
                    setPadding(0, ctx.dp(13f), 0, ctx.dp(13f))
                    setCompoundDrawablesRelativeWithIntrinsicBounds(dotDrawable(ctx, kindColor(k)), null, null, null)
                    compoundDrawablePadding = ctx.dp(6f)
                    setOnClickListener {
                        hideAfter(ctx)
                        val intent = Intent(ctx, MainActivity::class.java)
                            .putExtra("nav", "issue")
                            .putExtra("kind", k.name)
                            .putExtra("number", call.number)
                            .putExtra("time", call.timeMillis)
                            .putExtra("len", call.durationSec * 1000)
                        chosen?.let { intent.putExtra("case", it) }
                        open(ctx, intent)
                    }
                }
                row.addView(b, weighted(ctx, left = if (i > 0) 4f else 0f, right = if (i < rowKinds.lastIndex) 4f else 0f))
            }
            card.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ctx.dp(8f) })
        }
        val foot = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, ctx.dp(4f), 0, 0)
        }
        if (links.isNotEmpty()) {
            foot.addView(button(ctx, "특이사항 없음", false) { hideAfter(ctx) }, weighted(ctx, right = 4f))
            foot.addView(button(ctx, "나중에", false) { hideAfter(ctx) }, weighted(ctx, left = 4f))
        } else {
            foot.addView(
                button(ctx, "다시 묻지 않기", false) {
                    store.setQuiet(call.number, true)
                    hideAfter(ctx)
                },
                weighted(ctx, right = 4f),
            )
            foot.addView(
                button(ctx, "사건에 연결", true) {
                    hideAfter(ctx)
                    open(ctx, Intent(ctx, MainActivity::class.java).putExtra("nav", "register").putExtra("number", call.number))
                },
                weighted(ctx, left = 4f),
            )
        }
        card.addView(foot)
        root.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return root
    }

    /** 이 번호와 같은 사건 사람들까지의 특이사항, 최신순 */
    private fun relatedIssues(store: Store, number: String) =
        (store.issuesForNumber(number) + store.linksForNumber(number).flatMap { store.issuesForCase(it.caseNo) })
            .distinctBy { it.id }
            .sortedByDescending { it.source?.timeMillis ?: it.createdAt }

    // ───────────── 부품 ─────────────

    private fun open(ctx: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { ctx.startActivity(intent) }
        hideBubble(ctx)
    }

    private fun add(ctx: Context, v: View, gravity: Int, y: Int, width: Int = ViewGroup.LayoutParams.MATCH_PARENT): Boolean {
        val p = WindowManager.LayoutParams(
            width,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = gravity or if (width == ViewGroup.LayoutParams.MATCH_PARENT) Gravity.CENTER_HORIZONTAL else 0
            this.y = y
        }
        return runCatching { ctx.getSystemService(WindowManager::class.java).addView(v, p) }.onFailure {
            ctx.getSharedPreferences("call", Context.MODE_PRIVATE).edit().putString("err", it.javaClass.simpleName + ": " + it.message).apply()
        }.isSuccess
    }

    private fun remove(ctx: Context, v: View) {
        runCatching { ctx.getSystemService(WindowManager::class.java).removeView(v) }
    }

    private fun Context.dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Float, stroke: Int = 0): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (stroke != 0) setStroke((radius / 7).toInt().coerceAtLeast(3), stroke)
    }

    private fun font(ctx: Context, weight: Int): Typeface? = runCatching {
        ResourcesCompat.getFont(
            ctx,
            when (weight) {
                2 -> R.font.pretendard_extrabold
                1 -> R.font.pretendard_semibold
                else -> R.font.pretendard_regular
            },
        )
    }.getOrNull()

    private fun text(ctx: Context, s: String, sp: Float, weight: Int, color: Int, lines: Int = 0): TextView = TextView(ctx).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        typeface = font(ctx, weight)
        includeFontPadding = false
        if (lines > 0) {
            maxLines = lines
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    private fun button(ctx: Context, label: String, primary: Boolean, onClick: () -> Unit): TextView =
        text(ctx, label, 14.5f, 2, if (primary) Color.WHITE else INK2).apply {
            gravity = Gravity.CENTER
            background = if (primary) {
                GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(BRAND2, BRAND)).apply { cornerRadius = ctx.dp(14f).toFloat() }
            } else {
                rounded(CHIP, ctx.dp(14f).toFloat())
            }
            setPadding(0, ctx.dp(13f), 0, ctx.dp(13f))
            setOnClickListener { onClick() }
        }

    private fun weighted(ctx: Context, left: Float = 0f, right: Float = 0f) =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = ctx.dp(left)
            rightMargin = ctx.dp(right)
        }

    private fun iconButton(ctx: Context, res: Int, bg: Int, tint: Int = Color.WHITE, onClick: () -> Unit): View =
        FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bg)
            }
            addView(
                ImageView(ctx).apply {
                    setImageResource(res)
                    imageTintList = ColorStateList.valueOf(tint)
                },
                FrameLayout.LayoutParams(ctx.dp(14f), ctx.dp(14f), Gravity.CENTER),
            )
            layoutParams = LinearLayout.LayoutParams(ctx.dp(30f), ctx.dp(30f))
            setOnClickListener { onClick() }
        }

    private fun logo(ctx: Context, size: Float): View = FrameLayout(ctx).apply {
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF7A9CFF.toInt(), 0xFF2F55E6.toInt())).apply {
            cornerRadius = ctx.dp(size * 0.3f).toFloat()
        }
        addView(ImageView(ctx).apply { setImageResource(R.drawable.ic_launcher_foreground) }, FrameLayout.LayoutParams(ctx.dp(size * 1.1f), ctx.dp(size * 1.1f), Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(ctx.dp(size), ctx.dp(size))
    }

    private val AVATAR = listOf(
        0xFF6E8DFF.toInt() to 0xFF3150D6.toInt(),
        0xFFFF9B7B.toInt() to 0xFFE1514A.toInt(),
        0xFF47D3A6.toInt() to 0xFF11977A.toInt(),
        0xFFB392FF.toInt() to 0xFF6F49DB.toInt(),
        0xFFFFC861.toInt() to 0xFFE0912A.toInt(),
    )

    private fun avatar(ctx: Context, name: String?, number: String): View {
        val (a, b) = if (name == null) 0xFFC7CCD8.toInt() to 0xFF9AA2B4.toInt() else AVATAR[Math.floorMod(number.hashCode(), AVATAR.size)]
        val initial = name?.trim()?.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "#"
        return text(ctx, initial, 18f, 2, Color.WHITE).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(a, b)).apply { shape = GradientDrawable.OVAL }
            layoutParams = LinearLayout.LayoutParams(ctx.dp(46f), ctx.dp(46f))
        }
    }

    private fun dotDrawable(ctx: Context, color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setSize(ctx.dp(8f), ctx.dp(8f))
    }

    private fun duration(sec: Long): String = when {
        sec < 60 -> "${sec}초"
        sec % 60 == 0L -> "${sec / 60}분"
        else -> "${sec / 60}분 ${sec % 60}초"
    }
}
