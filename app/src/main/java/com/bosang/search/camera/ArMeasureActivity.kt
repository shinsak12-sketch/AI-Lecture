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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
 * AR 측정: 높이(바닥에서) · 길이(점과 점).
 * 가운데 조준점을 맞추고 [점 찍기], [사진 저장]하면 선 · 숫자가 함께 찍힌 사진이 사건에 들어간다.
 */
class ArMeasureActivity : ComponentActivity() {
    private var session: Session? = null
    private var installRequested = false
    private lateinit var surface: GLSurfaceView
    private lateinit var overlay: MeasureOverlay
    private lateinit var renderer: ArRenderer

    private var mode by mutableStateOf(MeasureMode.HEIGHT)
    private var plate by mutableStateOf(false)
    private var message by mutableStateOf<String?>("폰을 천천히 좌우로 움직여 주변을 인식시켜 주세요")
    private var error by mutableStateOf<String?>(null)
    private var floorFound by mutableStateOf(false)
    private var pointCount by mutableIntStateOf(0)
    private var saved by mutableIntStateOf(0)

    private val caseNo by lazy { intent.getStringExtra("case").orEmpty() }
    private val vehicle by lazy { intent.getStringExtra("vehicle") }
    private val stage by lazy { intent.getStringExtra("stage") }
    private val place by lazy { intent.getStringExtra("place") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        overlay = MeasureOverlay(this)
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

    private fun onSnapshot(s: ArSnapshot) {
        overlay.snap = s
        floorFound = s.floorY != null
        pointCount = s.points.size
        message = when {
            !s.tracking -> when (s.reason) {
                TrackingFailureReason.INSUFFICIENT_LIGHT -> "너무 어두워요. 밝은 곳에서 해 주세요"
                TrackingFailureReason.EXCESSIVE_MOTION -> "천천히 움직여 주세요"
                TrackingFailureReason.INSUFFICIENT_FEATURES -> "무늬가 있는 곳(바닥 · 바퀴 · 범퍼 틈)을 비춰 주세요"
                else -> "폰을 천천히 좌우로 움직여 주변을 인식시켜 주세요"
            }
            mode == MeasureMode.HEIGHT && s.floorY == null -> "먼저 차 옆 바닥을 비추며 천천히 움직여 주세요"
            mode == MeasureMode.HEIGHT -> "조준점을 손상 위 · 아래 끝에 맞추고 [점 찍기]"
            plate -> "번호판 왼쪽 끝 → 오른쪽 끝에 [점 찍기] (규격 52cm와 비교)"
            s.points.isEmpty() -> "조준점을 시작점에 맞추고 [점 찍기]"
            else -> "끝점(또는 다음 꺾이는 곳)에서 [점 찍기]"
        }
    }

    private fun save(glBitmap: Bitmap) {
        val summary = overlay.summary()
        lifecycleScope.launch {
            val bmp = glBitmap.copy(Bitmap.Config.ARGB_8888, true)
            overlay.draw(Canvas(bmp))
            val now = System.currentTimeMillis()
            val store = Store.get(this@ArMeasureActivity)
            val ref = withContext(Dispatchers.IO) {
                PhotoStamp.draw(
                    this@ArMeasureActivity,
                    bmp,
                    listOfNotNull(
                        summary ?: "AR 측정",
                        "AR 측정 · 참고값  |  " + PhotoStamp.dateText(now),
                        listOfNotNull(
                            caseNo.ifEmpty { null },
                            PhotoTags.vehicleLabel(vehicle)?.let { v -> vehicle?.let { store.carNo(caseNo, it) }?.let { "$v $it" } ?: v },
                            PhotoTags.stageLabel(stage),
                        ).joinToString("  ·  ").ifEmpty { null },
                        place,
                    ),
                )
                Photos.saveBitmap(this@ArMeasureActivity, bmp, "ar")
            }
            if (caseNo.isNotEmpty()) {
                store.addPhotos(
                    listOf(
                        CasePhoto(
                            id = Photos.newId(), caseNo = caseNo, uri = ref, source = "camera", kind = "측정",
                            takenAt = now, addedAt = now, vehicle = vehicle, stage = stage,
                            measure = summary, place = place,
                        ),
                    ),
                )
            }
            saved++
            Toast.makeText(this@ArMeasureActivity, if (summary != null) "저장했어요 · $summary" else "저장했어요", Toast.LENGTH_SHORT).show()
        }
    }

    @androidx.compose.runtime.Composable
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
                            listOfNotNull(caseNo.ifEmpty { null }, PhotoTags.vehicleLabel(vehicle), PhotoTags.stageLabel(stage)).joinToString(" · "),
                            style = ts(14.5f, W8, num = true),
                            color = Color.White,
                        )
                    }
                    if (floorFound) Pill("바닥 인식됨", true) {}
                    if (saved > 0) {
                        Spacer(Modifier.width(6.dp))
                        Pill("${saved}장 저장", false) {}
                    }
                }
                (error ?: message)?.let {
                    Text(it, style = ts(13.5f, W7), color = if (error != null) Color(0xFFFF8A8A) else Color.White, modifier = Modifier.padding(top = 8.dp))
                }
            }

            // 아래: 모드 · 버튼
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).navigationBarsPadding().padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("높이", mode == MeasureMode.HEIGHT) { setMode(MeasureMode.HEIGHT, false) }
                    Pill("길이", mode == MeasureMode.LENGTH && !plate) { setMode(MeasureMode.LENGTH, false) }
                    Pill("번호판으로 정확도 확인", plate) { setMode(MeasureMode.LENGTH, true) }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 14.dp),
                ) {
                    Pill("되돌리기", false) { renderer.actions.add(ArRenderer.Action.Undo) }
                    Pill("지우기", false) { renderer.actions.add(ArRenderer.Action.Clear) }
                    GradientButton("점 찍기", height = 52.dp, radius = 26.dp) { renderer.actions.add(ArRenderer.Action.Add) }
                    Pill(if (pointCount > 0) "사진 저장" else "그냥 저장", pointCount > 0) {
                        renderer.actions.add(ArRenderer.Action.Capture)
                    }
                }
                Text(
                    "측정값은 AR로 잰 참고값이에요. 반짝이는 차체보다 바닥 · 틈 · 손상 부위를 겨누면 잘 잡혀요.",
                    style = ts(11.5f, W6),
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }

    private fun setMode(m: MeasureMode, plateCheck: Boolean) {
        if (mode != m || plate != plateCheck) renderer.actions.add(ArRenderer.Action.Clear)
        mode = m
        plate = plateCheck
        overlay.mode = m
        overlay.plateCheck = plateCheck
    }

    @androidx.compose.runtime.Composable
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

    private val camPerm = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
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
