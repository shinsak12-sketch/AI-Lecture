package com.bosang.search.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** 앨범의 사진 한 장 */
data class AlbumPhoto(val id: Long, val uri: Uri, val takenAt: Long, val bucket: String?)

/**
 * 사건 사진: 직접 찍은 것 · 문자로 받은 것은 앱 안에 보관, 앨범 사진은 원본에 연결만.
 * 앱 안 파일은 "file:이름" 으로 적는다.
 */
object Photos {
    private const val FILE = "file:"
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun dir(ctx: Context): File = File(ctx.filesDir, "photos").apply { mkdirs() }

    fun authority(ctx: Context) = ctx.packageName + ".files"

    fun newId(): String = UUID.randomUUID().toString().take(12)

    /** 저장된 값 → 읽을 수 있는 주소 */
    fun resolve(ctx: Context, ref: String): Uri =
        if (ref.startsWith(FILE)) Uri.fromFile(File(dir(ctx), ref.removePrefix(FILE))) else Uri.parse(ref)

    /** 화면에 보여줄 것: 그림을 그렸으면 그린 것 */
    fun shown(p: CasePhoto): String = p.marked ?: p.uri

    /** 카메라로 찍을 빈 파일 (값, 카메라에 넘길 주소) */
    fun newCameraTarget(ctx: Context): Pair<String, Uri> {
        val name = "cam_${System.currentTimeMillis()}.jpg"
        val f = File(dir(ctx), name)
        return (FILE + name) to FileProvider.getUriForFile(ctx, authority(ctx), f)
    }

    fun exists(ctx: Context, ref: String): Boolean =
        if (ref.startsWith(FILE)) File(dir(ctx), ref.removePrefix(FILE)).let { it.exists() && it.length() > 0 } else true

    fun deleteFile(ctx: Context, ref: String?) {
        if (ref != null && ref.startsWith(FILE)) File(dir(ctx), ref.removePrefix(FILE)).delete()
    }

    /** 그림을 합친 사진을 앱 안에 저장하고 값을 돌려줌 */
    fun saveBitmap(ctx: Context, bmp: Bitmap, prefix: String): String {
        val name = "${prefix}_${System.currentTimeMillis()}.jpg"
        File(dir(ctx), name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return FILE + name
    }

    /**
     * 사건 번호들에게서 받은 MMS 사진을 앱 안에 복사해 사건 사진으로 넣는다.
     * 이미 넣은 것은 건너뜀. 새로 넣은 장수를 돌려줌.
     */
    suspend fun syncMms(ctx: Context, store: Store, sms: List<SmsItem>, caseNo: String): Int = withContext(Dispatchers.IO) {
        val added = ArrayList<CasePhoto>()
        sms.filter { it.incoming && it.images.isNotEmpty() }.forEach { m ->
            m.images.forEach { uri ->
                val partId = uri.lastPathSegment ?: return@forEach
                val id = "mms_${partId}_$caseNo"
                if (store.hasPhoto(id)) return@forEach
                val name = "mms_$partId.jpg"
                val f = File(dir(ctx), name)
                if (!f.exists()) {
                    val ok = runCatching {
                        ctx.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
                    }.isSuccess
                    if (!ok || f.length() == 0L) {
                        f.delete()
                        return@forEach
                    }
                }
                added.add(
                    CasePhoto(
                        id = id, caseNo = caseNo, uri = FILE + name, source = "mms",
                        takenAt = m.timeMillis, addedAt = System.currentTimeMillis(), from = m.number,
                    ),
                )
            }
        }
        store.addPhotos(added)
        added.size
    }

    /** 앨범 사진 (since 이후, 최신순) */
    suspend fun album(ctx: Context, since: Long, limit: Int = 800): List<AlbumPhoto> = withContext(Dispatchers.IO) {
        val out = ArrayList<AlbumPhoto>()
        val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        runCatching {
            ctx.contentResolver.query(
                base,
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                ),
                "${MediaStore.Images.Media.DATE_ADDED} >= ?",
                arrayOf((since / 1000).toString()),
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val id = c.getLong(0)
                    val taken = c.getLong(1).takeIf { it > 0 } ?: (c.getLong(2) * 1000)
                    out.add(AlbumPhoto(id, ContentUris.withAppendedId(base, id), taken, c.getString(3)))
                }
            }
        }
        out
    }

    fun cached(ref: String, px: Int): Bitmap? = cache.get("$ref@$px")

    /** 미리보기 그림 (작게 읽고 기억해 둠) */
    suspend fun load(ctx: Context, ref: String, px: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$ref@$px"
        cache.get(key)?.let { return@withContext it }
        val uri = resolve(ctx, ref)
        val bmp = runCatching {
            if (uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY) {
                ctx.contentResolver.loadThumbnail(uri, Size(px, px), null)
            } else {
                decodeSampled(ctx, uri, px)
            }
        }.getOrNull() ?: runCatching { decodeSampled(ctx, uri, px) }.getOrNull()
        bmp?.also { cache.put(key, it) }
    }

    /** 원본 크기에 가깝게 (그리기 · 크게 보기용) */
    suspend fun loadFull(ctx: Context, ref: String, maxPx: Int = 2048): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { decodeSampled(ctx, resolve(ctx, ref), maxPx) }.getOrNull()
    }

    private fun decodeSampled(ctx: Context, uri: Uri, px: Int): Bitmap? {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) sample *= 2
        val bmp = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = runCatching {
            cr.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull()
        val deg = when (rotation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (deg == 0f) return bmp
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)
    }

    /** 다른 앱으로 보내기: 사본을 만들어 공유 창을 띄움 */
    fun share(ctx: Context, source: Uri, name: String, mime: String) {
        runCatching {
            val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val f = File(dir, name.replace('/', '_'))
            ctx.contentResolver.openInputStream(source)?.use { input -> f.outputStream().use { input.copyTo(it) } }
            val uri = FileProvider.getUriForFile(ctx, authority(ctx), f)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, "공유").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun shareText(ctx: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { ctx.startActivity(Intent.createChooser(send, "공유").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
