package com.bosang.search.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.bosang.search.MainActivity
import com.bosang.search.ui.B
import com.bosang.search.ui.BCard
import com.bosang.search.ui.BosangTheme
import com.bosang.search.ui.Depth
import com.bosang.search.ui.GradientButton
import com.bosang.search.ui.Ic
import com.bosang.search.ui.Perms
import com.bosang.search.ui.W6
import com.bosang.search.ui.W7
import com.bosang.search.ui.press
import com.bosang.search.ui.ts
import kotlinx.coroutines.delay

/**
 * 위젯 검색칸을 누르면 뜨는 입력창. 글자만 넣고 [검색]을 누르면 닫히고,
 * 결과는 위젯 목록에 바로 나온다.
 */
class QuickSearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!Perms.allGranted(this)) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        setContent {
            BosangTheme {
                QueryInput(
                    initial = SearchWidget.query(this),
                    onSearch = { q ->
                        SearchWidget.setQuery(this, q)
                        finish()
                    },
                    onClose = { finish() },
                )
            }
        }
    }
}

@Composable
private fun QueryInput(initial: String, onSearch: (String) -> Unit, onClose: () -> Unit) {
    val c = B.c
    val ctx = LocalContext.current
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66060914))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        BCard(
            radius = 24.dp,
            level = Depth.FLOAT,
            color = c.bg,
            modifier = Modifier
                .statusBarsPadding()
                .padding(12.dp)
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.card)
                        .padding(horizontal = 14.dp),
                ) {
                    Icon(Ic.search, null, tint = c.ink3, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (value.text.isEmpty()) Text("사고번호, 번호, 이름, 초성", style = ts(16f), color = c.ink3, maxLines = 1)
                        BasicTextField(
                            value = value,
                            onValueChange = { value = it },
                            singleLine = true,
                            textStyle = ts(16f, W6).copy(color = c.ink),
                            cursorBrush = SolidColor(c.brand),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearch(value.text) }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    if (value.text.isNotEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.press(scale = 0.9f) { value = TextFieldValue("") }.size(24.dp).clip(CircleShape).background(c.chip2),
                        ) { Icon(Ic.x, "지우기", tint = c.ink2, modifier = Modifier.size(12.dp)) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        "결과는 위젯에 나와요",
                        style = ts(12.5f, W6),
                        color = c.ink2,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                    Text(
                        "앱 열기",
                        style = ts(13f, W7),
                        color = c.ink2,
                        modifier = Modifier
                            .press(scale = 0.94f) {
                                runCatching {
                                    ctx.startActivity(
                                        Intent(ctx, MainActivity::class.java).putExtra("nav", "search")
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                                    )
                                }
                                onClose()
                            }
                            .padding(8.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    GradientButton("검색", icon = Ic.search, height = 44.dp, radius = 14.dp) { onSearch(value.text) }
                }
            }
        }
    }
}
