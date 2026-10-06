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
import com.bosang.search.R
import com.bosang.search.core.CallEntry
import com.bosang.search.core.PhoneNumbers
import com.bosang.search.data.PhoneData
import com.bosang.search.data.Store

/**
 * 통화가 끝나면 화면 위에 뜨는 작은 창 (안드로이드 보통 View 로 그림).
 * "사건에 연결할까요?" / "특이사항이 있었나요?" → [예]면 PostCallActivity 에서 사고번호 · 관계 · 특이사항.
 */
object CallOverlay {
    private val main = Handler(Looper.getMainLooper())
    private var after: View? = null
    private val hideAfterTask = Runnable { closeAfterNow() }

    /** 메인 스레드면 바로, 아니면 메인으로 넘겨서. (예전엔 늘 미뤄서, 새 창을 띄운 직후 "이전 창 닫기"가 새 창을 닫아버렸음) */
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private fun closeAfterNow() {
        main.removeCallbacks(hideAfterTask)
        val v = after
        after = null
        v?.let { remove(it.context, it) }
    }

    /** 테스트 · 진단용 */
    fun afterShowing(): Boolean = after != null
    internal fun afterView(): View? = after

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

        val root = FrameLayout(ctx).apply { setPadding(ctx.dp(10f), 0, ctx.dp(10f), 0) }
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(BG, ctx.dp(28f).toFloat())
            elevation = ctx.dp(18f).toFloat()
            setPadding(ctx.dp(16f), ctx.dp(16f), ctx.dp(16f), ctx.dp(14f))
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

        // 이미 연결된 사건
        if (links.isNotEmpty()) {
            val list = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, ctx.dp(12f), 0, 0)
            }
            links.take(3).forEach { l ->
                list.addView(
                    text(ctx, "${l.caseNo}   ${l.role}", 14.5f, 2, INK).apply {
                        background = rounded(Color.WHITE, ctx.dp(14f).toFloat())
                        setPadding(ctx.dp(12f), ctx.dp(10f), ctx.dp(12f), ctx.dp(10f))
                    },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ctx.dp(6f) },
                )
            }
            card.addView(list)
        }

        // 묻기
        card.addView(
            text(ctx, if (links.isEmpty()) "이 통화를 사건에 연결할까요?" else "특이사항이나 약속이 있었나요?", 16f, 2, INK).apply {
                setPadding(ctx.dp(4f), ctx.dp(if (links.isEmpty()) 16f else 8f), 0, ctx.dp(4f))
            },
        )
        card.addView(
            text(
                ctx,
                if (links.isEmpty()) "예를 누르면 사고번호 · 관계 · 특이사항 · 약속을 정해요" else "예를 누르면 특이사항 구분이나 약속을 골라 바로 적어요",
                12.5f,
                1,
                INK2,
            ).apply { setPadding(ctx.dp(4f), 0, 0, ctx.dp(14f)) },
        )
        val foot = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        foot.addView(button(ctx, "아니오", false) { hideAfter(ctx) }, weighted(ctx, right = 4f))
        foot.addView(
            button(ctx, "예", true) {
                hideAfter(ctx)
                runCatching { ctx.startActivity(PostCallActivity.intent(ctx, call.number, name, call.timeMillis, call.durationSec * 1000, call.type)) }
            },
            weighted(ctx, left = 4f),
        )
        card.addView(foot)
        if (links.isEmpty()) {
            card.addView(
                text(ctx, "이 번호는 다시 묻지 않기", 12.5f, 1, INK3).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, ctx.dp(12f), 0, ctx.dp(2f))
                    setOnClickListener {
                        store.setQuiet(call.number, true)
                        hideAfter(ctx)
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        root.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return root
    }

    // ───────────── 부품 ─────────────

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

    private fun duration(sec: Long): String = when {
        sec < 60 -> "${sec}초"
        sec % 60 == 0L -> "${sec / 60}분"
        else -> "${sec / 60}분 ${sec % 60}초"
    }
}
