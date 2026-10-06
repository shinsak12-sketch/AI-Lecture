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
    /** 아래 떠 있는 재생 바에 보여줄 글 */
    var title by mutableStateOf("")
        private set
    var subtitle by mutableStateOf("")
        private set
    /** 재생 중인 녹음의 상대 번호와 녹음 시각 (특이사항 "이 지점에"용) */
    var number by mutableStateOf<String?>(null)
        private set
    var recTime by mutableLongStateOf(0L)
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

    fun toggle(
        key: String,
        uri: Uri,
        title: String,
        subtitle: String = "",
        number: String? = null,
        recTime: Long = 0L,
        startAt: Long = 0L,
    ) {
        if (currentKey == key && mp != null) {
            if (startAt > 0) {
                seekTo(startAt)
                if (!isPlaying) playPause()
            } else {
                playPause()
            }
            return
        }
        release()
        try {
            val np = MediaPlayer()
            np.setDataSource(ctx, uri)
            np.setOnCompletionListener {
                isPlaying = false
                positionMs = durationMs
            }
            np.prepare()
            if (startAt > 0) np.seekTo(startAt.toInt())
            np.start()
            mp = np
            currentKey = key
            this.title = title
            this.subtitle = subtitle
            this.number = number
            this.recTime = recTime
            durationMs = np.duration.toLong()
            positionMs = startAt
            isPlaying = true
            errorKey = null
        } catch (e: Exception) {
            release()
            errorKey = key
        }
    }

    /** 재생 / 일시정지. 끝까지 들은 뒤라면 처음부터 */
    fun playPause() {
        val p = mp ?: return
        if (p.isPlaying) {
            p.pause()
            isPlaying = false
        } else {
            if (positionMs >= durationMs - 300) {
                p.seekTo(0)
                positionMs = 0L
            }
            p.start()
            isPlaying = true
        }
    }

    fun seekTo(ms: Long) {
        val p = mp ?: return
        val t = ms.coerceIn(0L, durationMs)
        p.seekTo(t.toInt())
        positionMs = t
    }

    fun tick() {
        mp?.let { if (it.isPlaying) positionMs = it.currentPosition.toLong() }
    }

    fun release() {
        mp?.runCatching { stop() }
        mp?.release()
        mp = null
        currentKey = null
        number = null
        isPlaying = false
        positionMs = 0L
        durationMs = 0L
    }
}
