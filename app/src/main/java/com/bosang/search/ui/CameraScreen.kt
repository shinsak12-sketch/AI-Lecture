package com.bosang.search.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.bosang.search.camera.ArMeasureActivity
import com.bosang.search.camera.Place
import com.bosang.search.camera.PhotoStamp
import com.bosang.search.data.CasePhoto
import com.bosang.search.data.PhotoTags
import com.bosang.search.data.Photos
import com.bosang.search.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** 마지막으로 고른 차량 · 단계 (카메라를 다시 열어도 이어서) */
internal object CameraMemory {
    val vehicle = HashMap<String, String>()
    val stage = HashMap<String, String>()
}

private enum class CamMode(val label: String) { PHOTO("사진"), DOC("문서"), MEASURE("측정") }

private suspend fun cameraProvider(ctx: Context): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
    val f = ProcessCameraProvider.getInstance(ctx)
    f.addListener({ runCatching { f.get() }.onSuccess { if (cont.isActive) cont.resume(it) } }, ContextCompat.getMainExecutor(ctx))
}

/** 찍으면 바로 사건 사진으로: 차량 · 단계 꼬리표, 날짜 워터마크(선택), 위치(선택) */
@Composable
fun CameraScreen(store: Store, caseNo: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    StatusBarIcons(lightContent = true)
    BackHandler(onBack = onBack)
    val ver = store.version.intValue

    var granted by remember { mutableStateOf(Perms.granted(ctx, Manifest.permission.CAMERA)) }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) camPerm.launch(Manifest.permission.CAMERA) }

    var vehicle by remember { mutableStateOf(CameraMemory.vehicle[caseNo] ?: "own") }
    var stage by remember { mutableStateOf(CameraMemory.stage[caseNo] ?: "site") }
    var mode by remember { mutableStateOf(CamMode.PHOTO) }
    var flash by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var editCar by remember { mutableStateOf<String?>(null) }
    val stampOn = remember(ver) { store.stamp() }
    val placeOn = remember(ver) { store.placeOn() }
    var place by remember { mutableStateOf<String?>(null) }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) store.setPlaceOn(true) else Toast.makeText(ctx, "위치 권한이 없어 위치는 넣지 않아요", Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(placeOn) {
        while (placeOn) {
            place = Place.current(ctx)
            delay(90_000)
        }
        place = null
    }

    // 이번에 찍은 사진
    val sessionStart = remember { System.currentTimeMillis() }
    val shot = remember(ver) { store.photosForCase(caseNo).filter { it.source == "camera" && it.addedAt >= sessionStart } }
    var busy by remember { mutableIntStateOf(0) }
    var flashAnim by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoom by remember { mutableStateOf(1f) }
    var zoomRange by remember { mutableStateOf(1f..1f) }
    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        val provider = cameraProvider(ctx)
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        runCatching {
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            camera?.cameraInfo?.zoomState?.value?.let { z -> zoomRange = z.minZoomRatio..z.maxZoomRatio; zoom = z.zoomRatio }
        }.onFailure { Toast.makeText(ctx, "카메라를 열 수 없어요", Toast.LENGTH_SHORT).show() }
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { ProcessCameraProvider.getInstance(ctx).get().unbindAll() } }
    }
    fun setZoom(z: Float) {
        val v = z.coerceIn(zoomRange.start, zoomRange.endInclusive)
        zoom = v
        camera?.cameraControl?.setZoomRatio(v)
    }

    fun vehicleText(key: String): String {
        val label = PhotoTags.vehicleLabel(key) ?: key
        return store.carNo(caseNo, key)?.let { "$label $it" } ?: label
    }

    fun capture() {
        val name = "cam_${System.currentTimeMillis()}.jpg"
        val file = File(Photos.dir(ctx), name)
        imageCapture.flashMode = flash
        busy++
        flashAnim = true
        val kindNow = if (mode == CamMode.DOC) "서류" else null
        val vNow = vehicle
        val sNow = stage
        val placeNow = place
        val stampNow = stampOn
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    scope.launch {
                        val now = System.currentTimeMillis()
                        val stamped = if (stampNow) withContext(Dispatchers.IO) {
                            PhotoStamp.make(
                                ctx,
                                file,
                                listOfNotNull(
                                    PhotoStamp.dateText(now),
                                    listOfNotNull(caseNo, vehicleText(vNow), PhotoTags.stageLabel(sNow)).joinToString("  ·  "),
                                    placeNow,
                                ),
                            )
                        } else null
                        store.addPhotos(
                            listOf(
                                CasePhoto(
                                    id = Photos.newId(), caseNo = caseNo, uri = Photos.ref(name), source = "camera",
                                    kind = kindNow, takenAt = now, addedAt = now,
                                    vehicle = vNow, stage = sNow, place = placeNow, stamped = stamped,
                                ),
                            ),
                        )
                        busy--
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    busy--
                    Toast.makeText(ctx, "찍지 못했어요", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
    LaunchedEffect(flashAnim) {
        if (flashAnim) {
            delay(120)
            flashAnim = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(camera) {
                        detectTransformGestures { _, _, z, _ -> if (z != 1f) setZoom(zoom * z) }
                    }
                    .pointerInput(camera) {
                        detectTapGestures { pos ->
                            val point = previewView.meteringPointFactory.createPoint(pos.x, pos.y)
                            camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                        }
                    },
            )
        } else {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("카메라 권한이 필요해요", style = ts(17f, W8), color = Color.White)
                Spacer(Modifier.height(14.dp))
                GradientButton("권한 허용", height = 46.dp, radius = 14.dp) { camPerm.launch(Manifest.permission.CAMERA) }
            }
        }
        if (flashAnim) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.35f)))

        // ── 위: 닫기 · 사건 · 워터마크 · 위치 · 플래시
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .statusBarsPadding()
                .padding(top = 6.dp, bottom = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                GlassCircle(Ic.x, "닫기", onClick = onBack)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("보상 카메라", style = ts(12f, W7), color = Color.White.copy(alpha = 0.6f))
                    Text(caseNo, style = ts(16f, W8, num = true), color = Color.White)
                }
                CamToggle(if (stampOn) "날짜 표시" else "날짜 없음", stampOn) { store.setStamp(!stampOn) }
                Spacer(Modifier.width(6.dp))
                CamToggle(if (placeOn) "위치" else "위치 끔", placeOn) {
                    if (placeOn) store.setPlaceOn(false)
                    else if (Place.granted(ctx)) store.setPlaceOn(true)
                    else locPerm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                Spacer(Modifier.width(6.dp))
                CamToggle(
                    when (flash) { ImageCapture.FLASH_MODE_ON -> "플래시 켬"; ImageCapture.FLASH_MODE_AUTO -> "플래시 자동"; else -> "플래시 끔" },
                    flash != ImageCapture.FLASH_MODE_OFF,
                ) {
                    flash = when (flash) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                }
            }
            // 차량
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            ) {
                PhotoTags.VEHICLES.forEach { (k, _) ->
                    CamChip(vehicleText(k), vehicle == k, onLong = { editCar = k }) {
                        vehicle = k
                        CameraMemory.vehicle[caseNo] = k
                    }
                }
                CamChip("차량번호", false, outline = true) { editCar = vehicle }
            }
            // 단계
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            ) {
                PhotoTags.STAGES.forEach { (k, label) ->
                    CamChip(label, stage == k) {
                        stage = k
                        CameraMemory.stage[caseNo] = k
                    }
                }
            }
            if (placeOn) {
                Text(
                    place ?: "위치 찾는 중",
                    style = ts(11.5f, W6),
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp),
                )
            }
        }

        // ── 아래: 줌 · 모드 · 셔터
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .navigationBarsPadding()
                .padding(top = 10.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOfNotNull(
                    zoomRange.start.takeIf { it < 0.99f },
                    1f,
                    2f.takeIf { zoomRange.endInclusive >= 2f },
                    5f.takeIf { zoomRange.endInclusive >= 5f },
                ).forEach { z ->
                    val on = kotlin.math.abs(zoom - z) < 0.05f
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = if (on) 0.7f else 0.4f))
                            .press(scale = 0.92f) { setZoom(z) },
                    ) {
                        Text(
                            if (z < 1f) String.format("%.1f", z) else "${z.toInt()}" + if (on) "×" else "",
                            style = ts(12f, W8, num = true),
                            color = if (on) Color(0xFFFFD43B) else Color.White,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(top = 12.dp)) {
                CamMode.entries.forEach { m ->
                    Text(
                        m.label,
                        style = ts(14f, W8),
                        color = if (mode == m) Color(0xFFFFD43B) else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.press(scale = 0.94f) {
                            if (m == CamMode.MEASURE) {
                                runCatching {
                                    ctx.startActivity(
                                        Intent(ctx, ArMeasureActivity::class.java)
                                            .putExtra("case", caseNo)
                                            .putExtra("vehicle", vehicle)
                                            .putExtra("stage", stage)
                                            .putExtra("place", place),
                                    )
                                }
                            } else {
                                mode = m
                            }
                        },
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, start = 24.dp, end = 24.dp),
            ) {
                // 이번에 찍은 사진
                Box(Modifier.weight(1f)) {
                    val last = shot.maxByOrNull { it.addedAt }
                    if (last != null) {
                        Box(Modifier.press(scale = 0.94f, onClick = onBack)) {
                            Thumb(Photos.shown(last), Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, Color.White, RoundedCornerShape(12.dp)), px = 200)
                            Text(
                                "${shot.size}",
                                style = ts(11f, W8, num = true),
                                color = Color.Black,
                                modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).clip(CircleShape).background(Color(0xFFFFD43B)).padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                // 셔터
                val pressed by animateFloatAsState(if (busy > 0) 0.9f else 1f, label = "shutter")
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(76.dp)
                        .scale(pressed)
                        .clip(CircleShape)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(7.dp)
                        .clip(CircleShape)
                        .background(if (mode == CamMode.DOC) Color(0xFF6A8CFF) else Color.White)
                        .press(scale = 0.9f) { if (granted && camera != null) capture() },
                ) {
                    if (mode == CamMode.DOC) Icon(Ic.list, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Text(
                        "완료",
                        style = ts(15f, W8),
                        color = Color.White,
                        modifier = Modifier.press(scale = 0.94f, onClick = onBack).padding(10.dp),
                    )
                }
            }
            Text(
                listOfNotNull(
                    vehicleText(vehicle),
                    PhotoTags.stageLabel(stage),
                    if (mode == CamMode.DOC) "서류" else null,
                ).joinToString(" · ") + "  로 저장돼요",
                style = ts(12f, W6),
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }

    editCar?.let { key ->
        CarNoDialog(
            title = (PhotoTags.vehicleLabel(key) ?: "차량") + " 차량번호",
            initial = store.carNo(caseNo, key).orEmpty(),
            onDismiss = { editCar = null },
        ) {
            store.setCarNo(caseNo, key, it)
            editCar = null
        }
    }
}

@Composable
private fun CamToggle(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = ts(11.5f, W8),
        color = if (on) Color.Black else Color.White,
        maxLines = 1,
        modifier = Modifier
            .press(scale = 0.94f, onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) Color(0xFFFFD43B) else Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 9.dp, vertical = 6.dp),
    )
}

@Composable
private fun CamChip(text: String, on: Boolean, outline: Boolean = false, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(15.dp)
    Text(
        text,
        style = ts(13f, W8, num = true),
        color = if (on) Color.Black else Color.White,
        maxLines = 1,
        modifier = Modifier
            .press(scale = 0.94f, onLongClick = onLong, onClick = onClick)
            .clip(shape)
            .then(if (outline) Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), shape) else Modifier)
            .background(if (on) Color.White else if (outline) Color.Transparent else Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
fun CarNoDialog(title: String, initial: String, placeholder: String = "예: 12가3456", maxLen: Int = 12, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val c = B.c
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        shape = RoundedCornerShape(26.dp),
        title = { Text(title, style = ts(19f, W8), color = c.ink) },
        text = {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.chip).padding(horizontal = 14.dp, vertical = 13.dp),
            ) {
                if (v.isEmpty()) Text(placeholder, style = ts(16f), color = c.ink3)
                BasicTextField(
                    value = v,
                    onValueChange = { v = it.take(maxLen) },
                    singleLine = true,
                    textStyle = ts(16f, W7, num = true).copy(color = c.ink),
                    cursorBrush = SolidColor(c.brand),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(v) }) { Text("저장", style = ts(15f, W8), color = c.brand) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", style = ts(15f, W7), color = c.ink2) } },
    )
}
