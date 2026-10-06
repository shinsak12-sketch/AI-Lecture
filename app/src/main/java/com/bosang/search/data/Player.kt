package com.bosang.search.data

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 녹음 재생기. 한 번에 하나만 재생한다. */
class Player(context: Context) {
    private val ctx = context.applicationContext
    private var mp: MediaPlayer? = null

    var currentKey by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    /** 열지 못한 녹음 (그 카드에만 안내를 띄우려고) */
    var errorKey by mutableStateOf<String?>(null)
        private set

    fun toggle(key: String, uri: Uri) {
        val p = mp
        if (currentKey == key && p != null) {
            if (p.isPlaying) {
                p.pause()
                isPlaying = false
            } else {
                p.start()
                isPlaying = true
            }
            return
        }
        release()
        try {
            val np = MediaPlayer()
            np.setDataSource(ctx, uri)
            np.setOnCompletionListener {
                isPlaying = false
                positionMs = 0L
            }
            np.prepare()
            np.start()
            mp = np
            currentKey = key
            durationMs = np.duration.toLong()
            positionMs = 0L
            isPlaying = true
            errorKey = null
        } catch (e: Exception) {
            release()
            errorKey = key
        }
    }

    fun seekTo(ms: Long) {
        mp?.seekTo(ms.toInt())
        positionMs = ms
    }

    fun tick() {
        mp?.let { if (it.isPlaying) positionMs = it.currentPosition.toLong() }
    }

    fun release() {
        mp?.runCatching { stop() }
        mp?.release()
        mp = null
        currentKey = null
        isPlaying = false
        positionMs = 0L
        durationMs = 0L
    }
}
