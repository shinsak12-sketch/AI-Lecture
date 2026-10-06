package com.bosang.search

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import com.bosang.search.ui.App
import com.bosang.search.ui.BosangTheme

class MainActivity : ComponentActivity() {
    /** 앱으로 돌아올 때마다 바뀜 → 새 녹음·문자·권한 상태를 다시 읽는 신호 */
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BosangTheme {
                App(resumeTick = resumeTick.intValue)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue = resumeTick.intValue + 1
    }
}
