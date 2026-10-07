package com.bosang.search.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.ExifInterface
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.bosang.search.R
import com.bosang.search.data.Photos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

/** 사진에 날짜 · 사건 · 위치 글씨를 넣은 사본 만들기 (원본은 그대로) */
object PhotoStamp {
    private val fmt = SimpleDateFormat("yyyy.MM.dd  HH:mm", Locale.KOREA)

    fun dateText(ms: Long): String = fmt.format(Date(ms))

    private fun font(ctx: Context, res: Int): Typeface = runCatching { ResourcesCompat.getFont(ctx, res) }.getOrNull() ?: Typeface.DEFAULT_BOLD

    /** 파일을 읽어 방향을 바로잡고, 긴 변이 maxPx 이하가 되게 */
    fun decodeUpright(file: File, maxPx: Int = 3264): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample; inMutable = true }) ?: return null
        val deg = when (runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrNull()) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (deg == 0f) return bmp
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)
    }

    /**
     * 아래쪽에 옅은 그림자 띠를 깔고 오른쪽 아래에 글씨를 쓴다.
     * lines[0] 은 크게 (날짜), 나머지는 작게.
     */
    fun draw(ctx: Context, bmp: Bitmap, lines: List<String>) {
        if (lines.isEmpty()) return
        val c = Canvas(bmp)
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val unit = min(w, h)
        val big = unit * 0.046f
        val small = unit * 0.027f
        val pad = unit * 0.035f
        val gap = small * 0.5f
        val textH = big + (lines.size - 1) * (small + gap)
        val bandTop = h - textH - pad * 3.2f
        c.drawRect(
            0f, bandTop, w, h,
            Paint().apply { shader = LinearGradient(0f, bandTop, 0f, h, Color.TRANSPARENT, Color.argb(150, 0, 0, 0), Shader.TileMode.CLAMP) },
        )
        val bold = font(ctx, R.font.pretendard_bold)
        val semi = font(ctx, R.font.pretendard_semibold)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.RIGHT
            setShadowLayer(unit * 0.006f, 0f, unit * 0.002f, Color.argb(160, 0, 0, 0))
        }
        var y = h - pad - (lines.size - 1) * (small + gap)
        p.typeface = bold
        p.textSize = big
        c.drawText(lines[0], w - pad, y, p)
        p.typeface = semi
        p.textSize = small
        p.color = Color.argb(235, 255, 255, 255)
        lines.drop(1).forEach { line ->
            y += small + gap
            c.drawText(line, w - pad, y, p)
        }
    }

    /** 원본 파일로 워터마크 사본을 만들고 그 값을 돌려줌 */
    fun make(ctx: Context, src: File, lines: List<String>): String? = runCatching {
        val bmp = decodeUpright(src) ?: return null
        val out = if (bmp.isMutable) bmp else bmp.copy(Bitmap.Config.ARGB_8888, true)
        draw(ctx, out, lines)
        Photos.saveBitmap(ctx, out, "stamp")
    }.getOrNull()
}

/** 촬영 위치 → 짧은 주소 (선택 기능) */
object Place {
    fun granted(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context): String? = withContext(Dispatchers.IO) {
        if (!granted(ctx)) return@withContext null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return@withContext null
        val providers = lm.getProviders(true)
        var best: Location? = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if ((best == null || System.currentTimeMillis() - best.time > 2 * 60_000) && Build.VERSION.SDK_INT >= 30) {
            val provider = when {
                LocationManager.FUSED_PROVIDER in providers -> LocationManager.FUSED_PROVIDER
                LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
                else -> providers.firstOrNull()
            }
            if (provider != null) {
                val fresh = withTimeoutOrNull(6_000) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        val signal = CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        runCatching {
                            lm.getCurrentLocation(provider, signal, ctx.mainExecutor) { loc -> if (cont.isActive) cont.resume(loc) }
                        }.onFailure { if (cont.isActive) cont.resume(null) }
                    }
                }
                if (fresh != null) best = fresh
            }
        }
        val loc = best ?: return@withContext null
        address(ctx, loc) ?: String.format(Locale.US, "%.5f, %.5f", loc.latitude, loc.longitude)
    }

    @Suppress("DEPRECATION")
    private fun address(ctx: Context, loc: Location): String? = runCatching {
        if (!Geocoder.isPresent()) return null
        val a = Geocoder(ctx, Locale.KOREA).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull() ?: return null
        a.getAddressLine(0)?.removePrefix("대한민국")?.trim()
    }.getOrNull()
}
