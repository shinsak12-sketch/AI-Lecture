package com.bosang.search.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File

/**
 * 폰 갤러리에도 사본을 넣는다: 사진/보상검색기/사고번호 폴더.
 * 앱이 넣은 사진이라 따로 권한 없이 넣고 · 옮기고 · 지울 수 있다.
 */
object Gallery {
    private const val ROOT = "Pictures/보상검색기"

    fun folder(caseNo: String): String = "$ROOT/" + caseNo.ifEmpty { "사고번호 미정" }

    /** 파일을 갤러리에 넣고 주소를 돌려줌 (실패하면 null) */
    fun save(ctx: Context, file: File, caseNo: String, takenAt: Long): String? = runCatching {
        val cr = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, folder(caseNo))
            put(MediaStore.Images.Media.DATE_TAKEN, takenAt)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        cr.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        cr.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        uri.toString()
    }.getOrNull()

    /** 사고번호를 정하면 갤러리 폴더도 옮김 */
    fun move(ctx: Context, uri: String?, caseNo: String) {
        if (uri == null) return
        runCatching {
            ctx.contentResolver.update(Uri.parse(uri), ContentValues().apply { put(MediaStore.Images.Media.RELATIVE_PATH, folder(caseNo)) }, null, null)
        }
    }

    fun delete(ctx: Context, uri: String?) {
        if (uri == null) return
        runCatching { ctx.contentResolver.delete(Uri.parse(uri), null, null) }
    }
}
