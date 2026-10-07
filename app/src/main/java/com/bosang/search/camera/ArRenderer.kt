package com.bosang.search.camera

import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.google.ar.core.Anchor
import com.google.ar.core.Coordinates2d
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** 한 프레임에서 화면(오버레이)으로 넘기는 것 */
class ArSnapshot(
    val tracking: Boolean,
    val reason: TrackingFailureReason,
    /** 투영 × 뷰 (열 우선 4×4) */
    val viewProj: FloatArray,
    val width: Int,
    val height: Int,
    /** 찍은 점들 (월드 좌표) */
    val points: List<FloatArray>,
    /** 바닥 높이 (월드 y), 아직 모르면 null */
    val floorY: Float?,
    /** 화면 가운데가 닿는 곳 */
    val center: FloatArray?,
    /** 가운데가 닿은 곳이 어떤 면인지 */
    val centerKind: String?,
)

/** ARCore 카메라 화면 그리기 + 점 찍기 · 바닥 찾기 (GL 스레드) */
class ArRenderer(
    private val sessionOf: () -> Session?,
    private val rotationOf: () -> Int,
    private val onFrame: (ArSnapshot) -> Unit,
    private val onCapture: (Bitmap) -> Unit,
) : GLSurfaceView.Renderer {
    sealed interface Action {
        data object Add : Action
        data object Undo : Action
        data object Clear : Action
        data object Capture : Action
    }

    val actions = ConcurrentLinkedQueue<Action>()
    private val anchors = ArrayList<Anchor>()
    private var floorY: Float? = null
    private var width = 1
    private var height = 1
    private var geometryChanged = true
    private var textureSession: Session? = null

    // 카메라 배경
    private var textureId = -1
    private var program = 0
    private var aPos = 0
    private var aTex = 0
    private val quad: FloatBuffer = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val tex: FloatBuffer = floatBuffer(FloatArray(8))

    private val proj = FloatArray(16)
    private val view = FloatArray(16)

    private fun floatBuffer(a: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(a); position(0) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        textureId = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        program = link(
            """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() { gl_Position = a_Position; v_TexCoord = a_TexCoord; }
            """.trimIndent(),
            """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES sTexture;
            void main() { gl_FragColor = texture2D(sTexture, v_TexCoord); }
            """.trimIndent(),
        )
        aPos = GLES20.glGetAttribLocation(program, "a_Position")
        aTex = GLES20.glGetAttribLocation(program, "a_TexCoord")
        textureSession = null
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
        width = w
        height = h
        geometryChanged = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val session = sessionOf() ?: return
        if (textureSession !== session) {
            session.setCameraTextureName(textureId)
            textureSession = session
            geometryChanged = true
        }
        if (geometryChanged) {
            session.setDisplayGeometry(rotationOf(), width, height)
            geometryChanged = false
        }
        val frame = try { session.update() } catch (e: Exception) { return }
        drawBackground(frame)

        val camera = frame.camera
        val tracking = camera.trackingState == TrackingState.TRACKING
        // 바닥: 위를 보는 수평면 중 가장 낮은 것
        session.getAllTrackables(Plane::class.java)
            .filter { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null && it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
            .minOfOrNull { it.centerPose.ty() }
            ?.let { y -> floorY = floorY?.let { minOf(it, y) } ?: y }

        var centerHit: HitResult? = null
        var centerKind: String? = null
        if (tracking) {
            val hits = runCatching { frame.hitTest(width / 2f, height / 2f) }.getOrDefault(emptyList())
            centerHit = hits.firstOrNull { h ->
                when (val t = h.trackable) {
                    is DepthPoint -> true
                    is Plane -> t.isPoseInPolygon(h.hitPose)
                    is Point -> true
                    else -> false
                }
            }
            centerKind = when (centerHit?.trackable) {
                is DepthPoint -> "표면"
                is Plane -> "면"
                is Point -> "특징점"
                else -> null
            }
        }

        // 화면에서 누른 것 처리
        while (true) {
            val a = actions.poll() ?: break
            when (a) {
                Action.Add -> centerHit?.let { h -> runCatching { anchors.add(h.createAnchor()) } }
                Action.Undo -> anchors.removeLastOrNull()?.detach()
                Action.Clear -> {
                    anchors.forEach { it.detach() }
                    anchors.clear()
                }
                Action.Capture -> onCapture(readPixels())
            }
        }

        camera.getProjectionMatrix(proj, 0, 0.05f, 100f)
        camera.getViewMatrix(view, 0)
        val vp = FloatArray(16)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        val points = anchors.filter { it.trackingState == TrackingState.TRACKING }.map { a ->
            val p = a.pose
            floatArrayOf(p.tx(), p.ty(), p.tz())
        }
        val center = centerHit?.hitPose?.let { floatArrayOf(it.tx(), it.ty(), it.tz()) }
        onFrame(ArSnapshot(tracking, camera.trackingFailureReason, vp, width, height, points, floorY, center, centerKind))
    }

    private fun drawBackground(frame: Frame) {
        if (frame.hasDisplayGeometryChanged()) {
            quad.position(0)
            tex.position(0)
            frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quad, Coordinates2d.TEXTURE_NORMALIZED, tex)
        }
        if (frame.timestamp == 0L) return
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUseProgram(program)
        quad.position(0)
        tex.position(0)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, tex)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
        GLES20.glDepthMask(true)
    }

    /** 지금 화면(카메라 배경)을 그림으로 */
    private fun readPixels(): Bitmap {
        val buf = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        buf.position(0)
        val raw = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        raw.copyPixelsFromBuffer(buf)
        // GL 은 아래에서부터라 뒤집음
        val flip = android.graphics.Matrix().apply { preScale(1f, -1f) }
        return Bitmap.createBitmap(raw, 0, 0, width, height, flip, false)
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        return s
    }

    private fun link(vs: String, fs: String): Int {
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        return p
    }
}
