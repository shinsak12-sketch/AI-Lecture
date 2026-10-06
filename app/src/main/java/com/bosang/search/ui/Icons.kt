package com.bosang.search.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** 시안과 같은 선 아이콘 (24 격자, 선 굵기 1.9) */
object Ic {
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}A$r $r 0 1 0 ${cx + r} ${cy}A$r $r 0 1 0 ${cx - r} ${cy}Z"

    private fun line(name: String, vararg paths: String, width: Float = 1.9f, dots: List<String> = emptyList()): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            dots.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
        }.build()

    private fun solid(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
        }.build()

    val search by lazy { line("search", circle(11f, 11f, 7f), "M20.5 20.5L16 16") }
    val plus by lazy { line("plus", "M12 5v14M5 12h14", width = 2.4f) }
    val plusThin by lazy { line("plusThin", "M12 5v14M5 12h14", width = 2f) }
    val back by lazy { line("back", "M15 5l-7 7 7 7", width = 2.1f) }
    val down by lazy { line("down", "M6 9l6 6 6-6", width = 2.1f) }
    val folder by lazy {
        line("folder", "M3 7.5A2.5 2.5 0 0 1 5.5 5h3.6a2 2 0 0 1 1.5.7l1.2 1.3h6.7A2.5 2.5 0 0 1 21 9.5v8a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 17.5z")
    }
    val gear by lazy {
        line(
            "gear",
            circle(12f, 12f, 3f),
            "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
        )
    }
    val wave by lazy { line("wave", "M4 10v4M8 6.5v11M12 3.5v17M16 7.5v9M20 10.5v3", width = 2.1f) }
    val msg by lazy { line("msg", "M20 15.5a2 2 0 0 1-2 2H8l-4 3.5V5.5a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2z") }
    val phone by lazy { line("phone", "M5.5 3.5h3l1.6 4.2-2 1.4a12 12 0 0 0 6.8 6.8l1.4-2 4.2 1.6v3a2 2 0 0 1-2.2 2A17 17 0 0 1 3.5 5.7a2 2 0 0 1 2-2.2z") }
    val clock by lazy { line("clock", circle(12f, 12f, 9f), "M12 7.5V12l3 2") }
    val user by lazy { line("user", circle(12f, 8f, 4f), "M4 20.5a8 8 0 0 1 16 0") }
    val userPlus by lazy { line("userPlus", circle(10f, 8f, 4f), "M2.5 20.5a7.5 7.5 0 0 1 13.5-4.5M19 13v6M16 16h6") }
    val book by lazy {
        line("book", "M6 3.5h11a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-13a2 2 0 0 1 2-2z", circle(11.5f, 10f, 2.6f), "M7.5 16.5a4.3 4.3 0 0 1 8 0M19 7h2M19 12h2")
    }
    val incoming by lazy { line("in", "M17 7L7 17M7 9v8h8", width = 2.3f) }
    val outgoing by lazy { line("out", "M7 17L17 7M9 7h8v8", width = 2.3f) }
    val missed by lazy { line("missed", "M4 7l6 6 4-4 6 6M20 10v5h-5", width = 2.1f) }
    val check by lazy { line("check", "M5 12.5l4.5 4.5L19 7.5", width = 3.4f) }
    val checkThin by lazy { line("checkThin", "M5 12.5l4.5 4.5L19 7.5", width = 2.6f) }
    val more by lazy {
        line("more", dots = listOf(circle(5.5f, 12f, 1.6f), circle(12f, 12f, 1.6f), circle(18.5f, 12f, 1.6f)))
    }
    val link by lazy { line("link", "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1", "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1", width = 2.1f) }
    val x by lazy { line("x", "M6.5 6.5l11 11M17.5 6.5l-11 11", width = 2.2f) }
    val sort by lazy { line("sort", "M7 4v16M3.5 16.5L7 20l3.5-3.5M17 20V4M13.5 7.5L17 4l3.5 3.5") }
    val shield by lazy { line("shield", "M12 3L4.5 6v5.5c0 4.6 3.2 8.4 7.5 9.5 4.3-1.1 7.5-4.9 7.5-9.5V6z", "M9 12l2 2 4-4", width = 2.1f) }
    val docSearch by lazy {
        line("docSearch", "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h4", "M14 3v4a1 1 0 0 0 1 1h4", "M19 8v2", circle(16.5f, 16.5f, 3f), "M21 21l-2.3-2.3")
    }
    val edit by lazy { line("edit", "M4 20h4L18.6 9.4a2.1 2.1 0 0 0-3-3L5 17v3", "M13.5 8.5l2 2") }
    val trash by lazy { line("trash", "M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V4.5h6V7") }
    val refresh by lazy { line("refresh", "M20 4.5v5h-5", "M19.4 9.5A8 8 0 1 0 20 13.5") }
    val copy by lazy { line("copy", "M9 9h9.5a1.5 1.5 0 0 1 1.5 1.5V20a1.5 1.5 0 0 1-1.5 1.5H9A1.5 1.5 0 0 1 7.5 20v-9.5A1.5 1.5 0 0 1 9 9z", "M16.5 9V5.5A1.5 1.5 0 0 0 15 4H5.5A1.5 1.5 0 0 0 4 5.5V15a1.5 1.5 0 0 0 1.5 1.5H7.5") }
    val info by lazy { line("info", circle(12f, 12f, 9f), "M12 11v5.5M12 7.6v.2", width = 2f) }
    val chevron by lazy { line("chevron", "M9 5l7 7-7 7", width = 2.1f) }

    val play by lazy { solid("play", "M7 4.6v14.8a1 1 0 0 0 1.5.86l12.3-7.4a1 1 0 0 0 0-1.72L8.5 3.74A1 1 0 0 0 7 4.6z") }
    val pause by lazy {
        solid(
            "pause",
            "M6.9 4h1.8a1.4 1.4 0 0 1 1.4 1.4v13.2a1.4 1.4 0 0 1-1.4 1.4H6.9a1.4 1.4 0 0 1-1.4-1.4V5.4A1.4 1.4 0 0 1 6.9 4z",
            "M15.3 4h1.8a1.4 1.4 0 0 1 1.4 1.4v13.2a1.4 1.4 0 0 1-1.4 1.4h-1.8a1.4 1.4 0 0 1-1.4-1.4V5.4A1.4 1.4 0 0 1 15.3 4z",
        )
    }
}
