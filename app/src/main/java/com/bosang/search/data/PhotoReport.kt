package com.bosang.search.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.bosang.search.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min

/**
 * 사진대지 (고객 전송용 PDF).
 * 깨끗한 원본 사진 + 아래 설명(차량 · 단계 · 촬영 일시 · 위치 · 측정값), 쪽 번호.
 * 내부 메모(특이사항)는 넣지 않는다.
 */
object PhotoReport {
    enum class Layout(val label: String, val cols: Int, val rows: Int) {
        ONE("1장 크게", 1, 1),
        TWO("2장", 1, 2),
        FOUR("4장", 2, 2),
    }

    data class Options(
        val title: String = "차량 사진 자료",
        val layout: Layout = Layout.TWO,
        val showTime: Boolean = true,
        val showPlace: Boolean = false,
        val showMeasure: Boolean = true,
        val author: String = "",
    )

    // A4 (pt)
    private const val W = 595f
    private const val H = 842f
    private const val M = 40f
    private const val GAP = 16f
    private const val CAPTION = 50f
    private const val HEADER = 168f
    private const val FOOTER = 34f

    private val INK = Color.rgb(12, 18, 34)
    private val INK2 = Color.rgb(90, 98, 118)
    private val INK3 = Color.rgb(154, 161, 177)
    private val LINE = Color.rgb(228, 231, 238)
    private val SOFT = Color.rgb(243, 245, 249)
    private val BRAND = Color.rgb(51, 96, 255)

    private val dateFmt = SimpleDateFormat("yyyy. M. d.", Locale.KOREA)
    private val timeFmt = SimpleDateFormat("yyyy. M. d.  HH:mm", Locale.KOREA)
    private val fileFmt = SimpleDateFormat("yyyyMMdd", Locale.KOREA)

    private val CIRCLED = "①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳"

    fun number(i: Int): String = if (i < CIRCLED.length) CIRCLED[i].toString() else "${i + 1}."

    /** 차량 → 단계 → 시간 순 */
    fun order(photos: List<CasePhoto>): List<CasePhoto> {
        val vIdx = PhotoTags.VEHICLES.map { it.first }
        val sIdx = PhotoTags.STAGES.map { it.first }
        return photos.sortedWith(
            compareBy<CasePhoto>({ vIdx.indexOf(it.vehicle).let { i -> if (i < 0) 99 else i } }, { sIdx.indexOf(it.stage).let { i -> if (i < 0) 99 else i } }, { it.takenAt }),
        )
    }

    fun perPage(layout: Layout) = layout.cols * layout.rows

    fun pageCount(n: Int, layout: Layout) = maxOf(1, ceil(n / perPage(layout).toDouble()).toInt())

    private fun font(ctx: Context, res: Int) = runCatching { ResourcesCompat.getFont(ctx, res) }.getOrNull() ?: Typeface.DEFAULT

    /** PDF 를 만들어 파일을 돌려줌 */
    suspend fun build(ctx: Context, store: Store, caseNo: String, photos: List<CasePhoto>, o: Options): File = withContext(Dispatchers.IO) {
        val list = order(photos)
        val bold = font(ctx, R.font.pretendard_bold)
        val extra = font(ctx, R.font.pretendard_extrabold)
        val semi = font(ctx, R.font.pretendard_semibold)
        val reg = font(ctx, R.font.pretendard_regular)
        val doc = PdfDocument()
        val pages = pageCount(list.size, o.layout)
        val per = perPage(o.layout)
        val hasMeasure = o.showMeasure && list.any { !it.measure.isNullOrBlank() }

        for (pi in 0 until pages) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pi + 1).create())
            val c = page.canvas
            var top = M
            if (pi == 0) {
                drawHeader(c, store, caseNo, list, o, extra, bold, semi, reg)
                top = M + HEADER
            } else {
                // 이어지는 쪽: 얇은 머리줄
                text(c, o.title, M, M + 4f, 10f, INK2, semi)
                text(c, "사고번호 $caseNo", W - M, M + 4f, 10f, INK3, semi, Paint.Align.RIGHT)
                c.drawRect(M, M + 12f, W - M, M + 12.6f, fill(LINE))
                top = M + 28f
            }
            val bottom = H - M - FOOTER
            val cols = o.layout.cols
            val rows = o.layout.rows
            val slotW = (W - 2 * M - (cols - 1) * GAP) / cols
            val slotH = (bottom - top - (rows - 1) * GAP) / rows
            val imgH = slotH - CAPTION
            for (k in 0 until per) {
                val idx = pi * per + k
                val p = list.getOrNull(idx) ?: break
                val col = k % cols
                val row = k / cols
                val x = M + col * (slotW + GAP)
                val y = top + row * (slotH + GAP)
                drawPhoto(ctx, c, p, RectF(x, y, x + slotW, y + imgH))
                badge(c, number(idx), x + 10f, y + 10f, bold)
                drawCaption(c, store, caseNo, p, o, x, y + imgH + 10f, slotW, bold, reg, semi)
            }
            // 아래
            c.drawRect(M, H - M - 18f, W - M, H - M - 17.4f, fill(LINE))
            val foot = if (hasMeasure) "측정값은 휴대폰 AR로 잰 참고값입니다." else o.title
            text(c, foot, M, H - M - 4f, 8.5f, INK3, reg)
            text(c, "${pi + 1} / $pages", W - M, H - M - 4f, 8.5f, INK2, semi, Paint.Align.RIGHT)
            doc.finishPage(page)
        }
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.filter { it.name.endsWith(".pdf") }?.forEach { it.delete() }
        val f = File(dir, "사진대지_${caseNo}_${fileFmt.format(Date())}.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        f
    }

    private fun drawHeader(c: Canvas, store: Store, caseNo: String, list: List<CasePhoto>, o: Options, extra: Typeface, bold: Typeface, semi: Typeface, reg: Typeface) {
        // 맨 위 색 띠
        c.drawRect(0f, 0f, W, 6f, fill(BRAND))
        text(c, "PHOTO REPORT", M, M + 8f, 9f, BRAND, bold, letter = 0.18f)
        text(c, o.title, M, M + 38f, 24f, INK, extra)
        text(c, "작성일 " + dateFmt.format(Date()), W - M, M + 38f, 10f, INK2, semi, Paint.Align.RIGHT)

        // 정보 상자
        val boxTop = M + 56f
        val box = RectF(M, boxTop, W - M, boxTop + 92f)
        c.drawRoundRect(box, 12f, 12f, fill(SOFT))
        val vehicles = list.mapNotNull { it.vehicle }.distinct()
            .sortedBy { v -> PhotoTags.VEHICLES.indexOfFirst { it.first == v } }
            .map { v -> listOfNotNull(PhotoTags.vehicleLabel(v), store.carNo(caseNo, v)).joinToString(" ") }
        val stages = list.mapNotNull { it.stage }.distinct()
            .sortedBy { s -> PhotoTags.STAGES.indexOfFirst { it.first == s } }
            .mapNotNull { PhotoTags.stageLabel(it) }
        val first = list.minOfOrNull { it.takenAt }
        val last = list.maxOfOrNull { it.takenAt }
        val period = if (first == null) "-" else if (dateFmt.format(Date(first)) == dateFmt.format(Date(last ?: first))) dateFmt.format(Date(first)) else dateFmt.format(Date(first)) + " ~ " + dateFmt.format(Date(last ?: first))
        val cells = listOf(
            "사고번호" to caseNo,
            "사진" to "${list.size}장",
            "차량" to vehicles.joinToString(", ").ifEmpty { "-" },
            "촬영 기간" to period,
            "구분" to stages.joinToString(" · ").ifEmpty { "-" },
            "작성자" to o.author.ifBlank { "-" },
        )
        val colW = (box.width() - 36f) / 2
        cells.forEachIndexed { i, (k, v) ->
            val col = i % 2
            val row = i / 2
            val x = box.left + 18f + col * colW
            val y = box.top + 26f + row * 25f
            text(c, k, x, y, 9f, INK3, semi)
            text(c, ellipsize(v, colW - 70f, 10.5f, bold), x + 62f, y, 10.5f, INK, bold)
        }
    }

    private fun drawPhoto(ctx: Context, c: Canvas, p: CasePhoto, r: RectF) {
        val clip = Path().apply { addRoundRect(r, 10f, 10f, Path.Direction.CW) }
        c.save()
        c.clipPath(clip)
        c.drawRect(r, fill(SOFT))
        val bmp = decode(ctx, Photos.clean(p), (r.width() * 2.4f).toInt())
        if (bmp != null) {
            // 자르지 않고 칸 안에 맞춤 (증거 사진이라 잘리면 안 됨)
            val s = min(r.width() / bmp.width, r.height() / bmp.height)
            val w = bmp.width * s
            val h = bmp.height * s
            val dst = RectF(r.centerX() - w / 2, r.centerY() - h / 2, r.centerX() + w / 2, r.centerY() + h / 2)
            c.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            bmp.recycle()
        }
        c.restore()
        c.drawRoundRect(r, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.6f; color = LINE })
    }

    private fun drawCaption(c: Canvas, store: Store, caseNo: String, p: CasePhoto, o: Options, x: Float, y: Float, w: Float, bold: Typeface, reg: Typeface, semi: Typeface) {
        val tags = listOfNotNull(
            PhotoTags.vehicleLabel(p.vehicle)?.let { v -> p.vehicle?.let { store.carNo(caseNo, it) }?.let { "$v $it" } ?: v },
            PhotoTags.stageLabel(p.stage),
            p.kind,
        ).joinToString(" · ")
        val title = p.caption?.takeIf { it.isNotBlank() } ?: tags.ifEmpty { "사진" }
        text(c, ellipsize(title, w, 11f, bold), x, y + 8f, 11f, INK, bold)
        val sub = listOfNotNull(
            if (p.caption.isNullOrBlank()) null else tags.ifEmpty { null },
            if (o.showTime) timeFmt.format(Date(p.takenAt)) else null,
            if (o.showPlace) p.place else null,
        ).joinToString("  ·  ")
        if (sub.isNotEmpty()) text(c, ellipsize(sub, w, 9f, reg), x, y + 23f, 9f, INK2, reg)
        if (o.showMeasure && !p.measure.isNullOrBlank()) text(c, ellipsize(p.measure, w, 9.5f, semi), x, y + 37f, 9.5f, BRAND, semi)
    }

    private fun badge(c: Canvas, s: String, x: Float, y: Float, bold: Typeface) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(215, 12, 18, 34) }
        c.drawCircle(x + 10f, y + 10f, 10f, p)
        text(c, s, x + 10f, y + 14f, 11f, Color.WHITE, bold, Paint.Align.CENTER)
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, tf: Typeface, align: Paint.Align = Paint.Align.LEFT, letter: Float = 0f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = tf
            textAlign = align
            letterSpacing = letter
        }
        c.drawText(s, x, y, p)
    }

    private fun ellipsize(s: String, maxW: Float, size: Float, tf: Typeface): String {
        val p = Paint().apply { textSize = size; typeface = tf }
        if (p.measureText(s) <= maxW) return s
        var t = s
        while (t.isNotEmpty() && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    private fun decode(ctx: Context, ref: String, px: Int): Bitmap? = runCatching {
        val uri = Photos.resolve(ctx, ref)
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) sample *= 2
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val rot = runCatching {
            cr.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: 0
        val deg = when (rot) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (deg == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)
    }.getOrNull()

    /** 미리 보기용: 각 쪽을 그림으로 */
    suspend fun render(file: File, widthPx: Int): List<Bitmap> = withContext(Dispatchers.IO) {
        val out = ArrayList<Bitmap>()
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { r ->
                    for (i in 0 until r.pageCount) {
                        r.openPage(i).use { page ->
                            val h = (widthPx * page.height / page.width.toFloat()).toInt()
                            val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            out.add(bmp)
                        }
                    }
                }
            }
        }
        out
    }

    fun share(ctx: Context, file: File) {
        runCatching {
            val uri: Uri = FileProvider.getUriForFile(ctx, Photos.authority(ctx), file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, "사진대지 보내기").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
