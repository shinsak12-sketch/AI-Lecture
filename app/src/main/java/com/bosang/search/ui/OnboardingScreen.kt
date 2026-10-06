package com.bosang.search.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private data class PermRow(val perm: String, val title: String, val desc: String, val icon: ImageVector, val kind: Int)

@Composable
fun OnboardingScreen(resumeTick: Int, onChecked: () -> Unit) {
    val c = B.c
    val ctx = LocalContext.current
    StatusBarIcons(lightContent = true)
    var asked by remember { mutableStateOf(false) }
    // 설정에서 돌아오면 다시 확인
    var tick by remember { mutableStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        tick++
        onChecked()
    }
    val rows = listOf(
        PermRow(Perms.audio, "오디오", "통화녹음 파일을 찾아서 재생", Ic.wave, 0),
        PermRow(Manifest.permission.READ_SMS, "문자", "그 번호와 주고받은 문자", Ic.msg, 1),
        PermRow(Manifest.permission.READ_CALL_LOG, "통화기록", "번호 고르기 · 녹음과 시간 맞추기", Ic.clock, 2),
        PermRow(Manifest.permission.READ_CONTACTS, "연락처", "이름으로 저장된 녹음의 번호 찾기", Ic.user, 3),
    )
    // resumeTick, tick 이 바뀌면 다시 읽음
    val grantedNow = remember(resumeTick, tick) { rows.associate { it.perm to Perms.granted(ctx, it.perm) } }
    val anyMissing = grantedNow.values.any { !it }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Overlap(
                overlap = 38.dp,
                top = {
                    Hero(radius = 36.dp, bottomPadding = 66.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Spacer(Modifier.height(18.dp))
                            Orbit()
                            Spacer(Modifier.height(6.dp))
                            Text("폰 안에서만 찾아요", style = ts(25f, W8, tracking = -0.03f), color = Color.White)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "사고번호 하나로 문자와 통화녹음을 모아 보여드려요",
                                style = ts(14f, W4).copy(textAlign = TextAlign.Center),
                                color = Color.White.copy(alpha = 0.66f),
                            )
                        }
                    }
                },
                bottom = {
                    BCard(
                        level = Depth.LIFT,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(6.dp)) {
                            rows.forEachIndexed { i, r ->
                                if (i > 0) {
                                    Box(
                                        Modifier
                                            .padding(horizontal = 10.dp)
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(c.line),
                                    )
                                }
                                PermissionRow(r, grantedNow[r.perm] == true)
                            }
                        }
                    }
                },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            ) {
                Icon(Ic.shield, null, tint = c.ok, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text("인터넷 권한이 없는 앱이라 밖으로 나가지 않아요", style = ts(12.5f, W7), color = c.ink2)
            }

            if (asked && anyMissing) {
                BCard(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 18.dp)
                        .fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Ic.info, null, tint = c.warn, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("아직 허용되지 않은 권한이 있어요", style = ts(15f, W8), color = c.ink)
                        }
                        Spacer(Modifier.height(10.dp))
                        listOf(
                            "아래 [앱 정보 열기]를 누르고",
                            "오른쪽 위 점 세 개 → '제한된 설정 허용'",
                            "권한에서 통화기록 · 문자 · 연락처 · 음악 및 오디오 허용",
                        ).forEachIndexed { i, s ->
                            Row(Modifier.padding(vertical = 4.dp)) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(c.chip),
                                ) { Text("${i + 1}", style = ts(11.5f, W8), color = c.ink2) }
                                Spacer(Modifier.width(10.dp))
                                Text(s, style = ts(13.5f, W4, lineHeight = 1.5f), color = c.ink2)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        SoftButton("앱 정보 열기", modifier = Modifier.fillMaxWidth()) { Perms.openAppSettings(ctx) }
                    }
                }
            }
            Spacer(Modifier.height(130.dp))
        }

        GradientButton(
            text = if (asked && anyMissing) "다시 허용하기" else "권한 허용하고 시작",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 22.dp)
                .fillMaxWidth(),
        ) { launcher.launch(Perms.required) }
    }
}

@Composable
private fun PermissionRow(r: PermRow, granted: Boolean) {
    val c = B.c
    val (fg, bg) = when (r.kind) {
        0 -> c.rec to c.recTint
        1 -> c.brand to c.brandTint
        2 -> c.ok to c.okTint
        else -> c.warn to c.warnTint
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 11.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(bg),
        ) { Icon(r.icon, null, tint = fg, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, style = ts(15f, W8), color = c.ink)
            Text(r.desc, style = ts(12.5f, W4), color = c.ink2)
        }
        if (granted) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(c.okTint),
            ) { Icon(Ic.check, "허용됨", tint = c.ok, modifier = Modifier.size(11.dp)) }
        }
    }
}

/** 로고 둘레의 궤도와 떠 있는 꼬리표 */
@Composable
private fun Orbit() {
    val t = rememberInfiniteTransition(label = "orbit")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Box(Modifier.size(260.dp, 230.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val line = Color.White.copy(alpha = 0.12f)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x59628AFF), Color(0x00628AFF)),
                    center = center,
                    radius = 60.dp.toPx(),
                ),
                radius = 60.dp.toPx(),
                center = center,
            )
            drawCircle(line, 60.dp.toPx(), center, style = Stroke(1.dp.toPx()))
            drawCircle(line, 95.dp.toPx(), center, style = Stroke(1.dp.toPx()))
            drawCircle(
                Color.White.copy(alpha = 0.08f),
                128.dp.toPx(),
                center,
                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
            )
        }
        Box(Modifier.align(Alignment.Center)) { LogoTile(size = 78.dp, radius = 24.dp, iconSize = 40.dp) }
        Orb("통화녹음", Ic.wave, Modifier.align(Alignment.TopStart), 0.dp, 34.dp, phase, 0f)
        Orb("문자", Ic.msg, Modifier.align(Alignment.TopEnd), 4.dp, 58.dp, phase, 1.6f)
        Orb("통화기록", Ic.clock, Modifier.align(Alignment.BottomStart), 14.dp, (-26).dp, phase, 3.1f)
        Orb("연락처", Ic.user, Modifier.align(Alignment.BottomEnd), (-8).dp, (-4).dp, phase, 4.5f)
    }
}

@Composable
private fun Orb(text: String, icon: ImageVector, modifier: Modifier, x: Dp, y: Dp, phase: Float, shift: Float) {
    val shape = RoundedCornerShape(17.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .offset(x, y)
            .graphicsLayer { translationY = kotlin.math.sin(phase + shift) * 3.dp.toPx() }
            .height(34.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            .padding(horizontal = 12.dp),
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = ts(12.5f, W7), color = Color.White)
    }
}
