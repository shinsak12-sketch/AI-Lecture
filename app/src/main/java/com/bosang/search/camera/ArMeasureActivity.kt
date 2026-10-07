package com.bosang.search.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bosang.search.data.CasePhoto
import com.bosang.search.data.Gallery
import com.bosang.search.data.PhotoTags
import com.bosang.search.data.Photos
import com.bosang.search.data.Store
import com.bosang.search.ui.BosangTheme
import com.bosang.search.ui.GlassCircle
import com.bosang.search.ui.GradientButton
import com.bosang.search.ui.Ic
import com.bosang.search.ui.W6
import com.bosang.search.ui.W7
import com.bosang.search.ui.W8
import com.bosang.search.ui.press
import com.bosang.search.ui.ts
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AR 측정: 길이 · 높이(직접 찍은 바닥 기준) · 원 · 네모 · 수리 배지.
 * 점 두 개로 하나가 끝나고, 다시 찍으면 새 표시. [저장]하면 점선 · 숫자 · 배지가 함께 찍힌 사진이 사건(과 갤러리)에 들어간다.
 */
class ArMeasureActivity : ComponentActivity() {
    private var session: Session? = null
    private var installRequested = false
    private lateinit var surface: GLSurfaceView
    private lateinit var overlay: MeasureOverlay
    private lateinit var renderer: ArRenderer

    private var tool by mutableStateOf(Tool.LENGTH)
    private var sticker by mutableStateOf(Stickers.ALL.first())
    private var message by mutableStateOf<String?>("폰을 천천히 좌우로 움직여 주변을 인식시켜 주세요")
    private var error by mutableStateOf<String?>(null)
    private var hasBase by mutableStateOf(false)
    private var markCount by mutableIntStateOf(0)
    private var pending by mutableStateOf(false)
    private var selected by mutableStateOf<Int?>(null)
    private var selectedText by mutableStateOf<String?>(null)
    private var saved by mutableIntStateOf(0)
    private var lastMissed = 0

    private val caseNo by lazy { intent.getStringExtra("case").orEmpty() }
    private val vehicle by lazy { intent.getStringExtra("vehicle") }
    private val stage by lazy { intent.getStringExtra("stage") }
    private val place by lazy { intent.getStringExtra("place") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        overlay = MeasureOverlay(this) { idx ->
            selected = idx
            overlay.selected = idx
        }
        renderer = ArRenderer(
            sessionOf = { session },
            rotationOf = { rotation() },
            onFrame = { s -> overlay.post { onSnapshot(s) } },
            onCapture = { bmp -> overlay.post { save(bmp) } },
        )
        surface = GLSurfaceView(this).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            setWillNotDraw(false)
        }
        setContent { BosangTheme { Screen() } }
    }

    @Suppress("DEPRECATION")
    private fun rotation(): Int = if (Build.VERSION.SDK_INT >= 30) display?.rotation ?: 0 else windowManager.defaultDisplay.rotation

    private fun send(a: ArRenderer.Action) {
        renderer.actions.add(a)
    }

    private fun onSnapshot(s: ArSnapshot) {
        overlay.snap = s
        hasBase = s.base != null
        markCount = s.marks.size
        val last = s.marks.lastOrNull()
        pending = last != null && !last.complete
        val sel = selected
        if (sel != null && sel >= s.marks.size) {
            selected = null
            overlay.selected = null
        }
        selectedText = selected?.let { i -> s.marks.getOrNull(i)?.let { m -> m.tool.label + (MarkText.label(m, s.base?.get(1))?.let { " · $it" } ?: "") } }
        if (s.missed != lastMissed) {
            lastMissed = s.missed
            Toast.makeText(this, "표면을 못 찾았어요. 바닥 · 틈 · 손상 부위처럼 무늬가 있는 곳을 겨눠 주세요", Toast.LENGTH_SHORT).show()
        }
        message = when {
            !s.tracking -> when (s.reason) {
                TrackingFailureReason.INSUFFICIENT_LIGHT -> "너무 어두워요. 밝은 곳에서 해 주세요"
                TrackingFailureReason.EXCESSIVE_MOTION -> "천천히 움직여 주세요"
                TrackingFailureReason.INSUFFICIENT_FEATURES -> "무늬가 있는 곳(바닥 · 바퀴 · 범퍼 틈)을 비춰 주세요"
                else -> "폰을 천천히 좌우로 움직여 주변을 인식시켜 주세요"
            }
            tool == Tool.HEIGHT && s.base == null -> "① 바닥(타이어가 땅에 닿는 곳)에 조준점을 맞추고 [바닥 찍기]"
            tool == Tool.HEIGHT -> "② 높이를 잴 곳(손상 위 · 아래 끝)마다 [점 찍기]"
            tool == Tool.STICKER -> "배지를 고르고, 붙일 곳에 조준점을 맞춰 [붙이기]"
            tool == Tool.PLATE -> if (pending) "번호판 오른쪽 끝에서 [점 찍기]" else "번호판 왼쪽 끝에서 [점 찍기] (규격 52cm와 비교)"
            tool == Tool.CIRCLE -> if (pending) "가장자리에서 [점 찍기]" else "원의 가운데에서 [점 찍기]"
            tool == Tool.RECT -> if (pending) "맞은편 모서리에서 [점 찍기]" else "한쪽 모서리에서 [점 찍기]"
            pending -> "끝점에서 [점 찍기]"
            else -> "시작점에서 [점 찍기]"
        }
    }

    private fun save(glBitmap: Bitmap) {
        val summary = overlay.summary()
        lifecycleScope.launch {
            val bmp = glBitmap.copy(Bitmap.Config.ARGB_8888, true)
            overlay.capturing = true
            overlay.draw(Canvas(bmp))
            overlay.capturing = false
            overlay.invalidate()
            val now = System.currentTimeMillis()
            val ctx = this@ArMeasureActivity
            val store = Store.get(ctx)
            val vText = PhotoTags.vehicleLabel(vehicle)?.let { v -> vehicle?.let { store.carNo(caseNo, it) }?.let { "$v $it" } ?: v }
            val (ref, gallery) = withContext(Dispatchers.IO) {
                PhotoStamp.draw(
                    ctx,
                    bmp,
                    listOfNotNull(
                        PhotoStamp.dateText(now),
                        "AR 측정 · 참고값" + (summary?.let { "  |  $it" } ?: ""),
                        listOfNotNull(caseNo.ifEmpty { null }, vText, PhotoTags.stageLabel(stage)).joinToString("  ·  ").ifEmpty { null },
                        place,
                    ),
                )
                val r = Photos.saveBitmap(ctx, bmp, "ar")
                val g = if (store.galleryOn()) Photos.file(ctx, r)?.let { Gallery.save(ctx, it, caseNo, now) } else null
                r to g
            }
            store.addPhotos(
                listOf(
                    CasePhoto(
                        id = Photos.newId(), caseNo = caseNo, uri = ref, source = "camera", kind = "측정",
                        takenAt = now, addedAt = now, vehicle = vehicle, stage = stage,
                        measure = summary, place = place, gallery = gallery,
                    ),
                ),
            )
            saved++
            Toast.makeText(ctx, if (summary != null) "저장했어요 · $summary" else "저장했어요", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pickTool(t: Tool) {
        if (t != tool) send(ArRenderer.Action.DropPending)
        tool = t
        overlay.tool = t
    }

    @Composable
    private fun Screen() {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { surface }, modifier = Modifier.fillMaxSize())
            AndroidView(factory = { overlay }, modifier = Modifier.fillMaxSize())

            // 위: 닫기 · 안내
            Column(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassCircle(Ic.x, "닫기") { finish() }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("AR 측정 · 참고값", style = ts(12f, W7), color = Color.White.copy(alpha = 0.6f))
                        Text(
                            listOfNotNull(caseNo.ifEmpty { "사고번호 미정" }, PhotoTags.vehicleLabel(vehicle), PhotoTags.stageLabel(stage)).joinToString(" · "),
                            style = ts(14.5f, W8, num = true),
                            color = Color.White,
                        )
                    }
                    if (saved > 0) Pill("${saved}장 저장", false) {}
                }
                (error ?: message)?.let {
                    Text(it, style = ts(13.5f, W7), color = if (error != null) Color(0xFFFF8A8A) else Color.White, modifier = Modifier.padding(top = 8.dp))
                }
            }

            // 아래: 고른 표시 · 도구 · 버튼
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).navigationBarsPadding().padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val sel = selected
                if (sel != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp).fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text("고름: " + (selectedText ?: ""), style = ts(13.5f, W8), color = Color.White, modifier = Modifier.weight(1f))
                        Pill("이것만 지우기", true) {
                            send(ArRenderer.Action.Delete(sel))
                            selected = null
                            overlay.selected = null
                        }
                        Spacer(Modifier.width(6.dp))
                        Pill("취소", false) {
                            selected = null
                            overlay.selected = null
                        }
                    }
                }
                if (tool == Tool.STICKER) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 10.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                    ) {
                        Stickers.ALL.forEach { label ->
                            val on = sticker == label
                            Text(
                                label,
                                style = ts(13.5f, W8),
                                color = Color.White,
                                modifier = Modifier
                                    .press(scale = 0.94f) { sticker = label }
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (on) Color(Stickers.color(label)) else Color.White.copy(alpha = 0.16f))
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                ) {
                    Tool.entries.forEach { t -> Pill(if (t == Tool.PLATE) "번호판 확인" else t.label, tool == t) { pickTool(t) } }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Pill("되돌리기", false) { send(ArRenderer.Action.Undo) }
                    Pill("모두 지우기", false) {
                        send(ArRenderer.Action.Clear)
                        selected = null
                        overlay.selected = null
                    }
                    when {
                        tool == Tool.HEIGHT && !hasBase -> GradientButton("바닥 찍기", height = 52.dp, radius = 26.dp) { send(ArRenderer.Action.SetBase) }
                        tool == Tool.STICKER -> GradientButton("붙이기", height = 52.dp, radius = 26.dp) { send(ArRenderer.Action.Tap(Tool.STICKER, sticker)) }
                        else -> GradientButton("점 찍기", height = 52.dp, radius = 26.dp) { send(ArRenderer.Action.Tap(tool, null)) }
                    }
                    Pill("저장", markCount > 0) {
                        selected = null
                        overlay.selected = null
                        send(ArRenderer.Action.DropPending)
                        send(ArRenderer.Action.Capture)
                    }
                }
                if (tool == Tool.HEIGHT && hasBase) {
                    Text(
                        "바닥 다시 찍기",
                        style = ts(12.5f, W8),
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 8.dp).press(scale = 0.94f) { send(ArRenderer.Action.ClearBase) }.padding(6.dp),
                    )
                }
                Text(
                    "표시를 누르면 그것만 지울 수 있어요 · 측정값은 참고값",
                    style = ts(11.5f, W6),
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }

    @Composable
    private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
        Text(
            text,
            style = ts(13f, W8),
            color = if (on) Color.Black else Color.White,
            maxLines = 1,
            modifier = Modifier
                .press(scale = 0.94f, onClick = onClick)
                .clip(RoundedCornerShape(16.dp))
                .background(if (on) Color(0xFFFFD43B) else Color.White.copy(alpha = 0.18f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }

    private val camPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) error = "카메라 권한이 필요해요"
    }

    override fun onResume() {
        super.onResume()
        if (session == null) {
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        return
                    }
                    else -> {}
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    camPerm.launch(Manifest.permission.CAMERA)
                    return
                }
                session = Session(this).also { s ->
                    val cfg = Config(s)
                    cfg.focusMode = Config.FocusMode.AUTO
                    cfg.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    cfg.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    if (s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) cfg.depthMode = Config.DepthMode.AUTOMATIC
                    s.configure(cfg)
                }
            } catch (e: UnavailableUserDeclinedInstallationException) {
                error = "AR 기능(Google Play AR 서비스)을 설치해야 측정할 수 있어요"
                return
            } catch (e: UnavailableDeviceNotCompatibleException) {
                error = "이 폰은 AR 측정을 지원하지 않아요"
                return
            } catch (e: Exception) {
                error = "AR을 시작하지 못했어요 (${e.javaClass.simpleName})"
                return
            }
        }
        try {
            session?.resume()
        } catch (e: Exception) {
            error = "카메라를 열 수 없어요. 다른 앱이 카메라를 쓰고 있는지 확인해 주세요"
            session = null
            return
        }
        surface.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (::surface.isInitialized) surface.onPause()
        session?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        session?.close()
        session = null
    }
}
