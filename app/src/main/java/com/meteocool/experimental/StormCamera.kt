package com.meteocool.experimental

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.core.content.ContextCompat
import kotlin.math.atan

/** Camera and orientation live only while their activity is visible. */
internal class StormCamera(
    private val view: TextureView,
    private val ready: (Boolean, Double) -> Unit,
) : TextureView.SurfaceTextureListener {
    private val manager = view.context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var surface: Surface? = null
    private var active = false
    private var opening = false
    private var generation = 0
    private var size: Size? = null
    private var orientation = 90
    private var sensorWidth = 0.0
    private var sensorHeight = 0.0
    private var focal = 0.0
    init { view.surfaceTextureListener = this }
    fun start() { active = true; if (view.isAvailable) open() }
    fun stop() {
        active = false; generation++; opening = false
        session?.close(); session = null
        camera?.close(); camera = null
        surface?.release(); surface = null
    }
    @SuppressLint("MissingPermission")
    private fun open() {
        if (!active || opening || camera != null) return
        if (ContextCompat.checkSelfPermission(view.context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { ready(false, 0.0); return }
        try {
            val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
            if (id == null) { ready(false, 0.0); return }
            val spec = manager.getCameraCharacteristics(id)
            orientation = spec.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            val sensor = spec.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            sensorWidth = sensor?.width?.toDouble() ?: 0.0; sensorHeight = sensor?.height?.toDouble() ?: 0.0
            focal = spec.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()?.toDouble() ?: 0.0
            val options = spec.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(SurfaceTexture::class.java)
            size = options?.filter { it.width <= 1920 && it.height <= 1920 }?.maxByOrNull { it.width * it.height } ?: options?.minByOrNull { it.width * it.height }
            val chosen = size ?: run { ready(false, 0.0); return }
            view.surfaceTexture?.setDefaultBufferSize(chosen.width, chosen.height)
            opening = true
            val requested = generation
            manager.openCamera(id, view.context.mainExecutor, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    opening = false
                    if (!active || requested != generation || !view.isAvailable) { device.close(); return }
                    camera = device
                    configure(device, requested)
                }
                override fun onDisconnected(device: CameraDevice) { device.close(); if (requested == generation) { stop(); ready(false, 0.0) } }
                override fun onError(device: CameraDevice, error: Int) { onDisconnected(device) }
            })
        } catch (_: Exception) { stop(); ready(false, 0.0) }
    }
    private fun configure(device: CameraDevice, requested: Int) {
        try {
            val texture = view.surfaceTexture ?: return
            surface = Surface(texture)
            val target = surface ?: return
            device.createCaptureSession(SessionConfiguration(SessionConfiguration.SESSION_REGULAR, listOf(OutputConfiguration(target)), view.context.mainExecutor, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(capture: CameraCaptureSession) {
                    if (!active || requested != generation || camera !== device) { capture.close(); return }
                    session = capture
                    try {
                        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(target)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
                        }.build()
                        capture.setSingleRepeatingRequest(request, view.context.mainExecutor, object : CameraCaptureSession.CaptureCallback() {})
                        transform()
                    } catch (_: Exception) { stop(); ready(false, 0.0) }
                }
                override fun onConfigureFailed(capture: CameraCaptureSession) { capture.close(); if (requested == generation) { stop(); ready(false, 0.0) } }
            }))
        } catch (_: Exception) { stop(); ready(false, 0.0) }
    }
    private fun transform() {
        val buffer = size ?: return
        val rotation = when (view.display?.rotation) { Surface.ROTATION_90 -> 90; Surface.ROTATION_180 -> 180; Surface.ROTATION_270 -> 270; else -> 0 }
        val swapped = (orientation - rotation + 360) % 180 == 90
        val bw = if (swapped) buffer.height.toFloat() else buffer.width.toFloat()
        val bh = if (swapped) buffer.width.toFloat() else buffer.height.toFloat()
        val w = view.width.toFloat(); val h = view.height.toFloat()
        if (w <= 0 || h <= 0) return
        val matrix = Matrix()
        val screen = RectF(0f, 0f, w, h)
        val rect = RectF(0f, 0f, bw, bh).apply { offset(w / 2 - centerX(), h / 2 - centerY()) }
        matrix.setRectToRect(screen, rect, Matrix.ScaleToFit.FILL)
        val scale = maxOf(w / bw, h / bh)
        matrix.postScale(scale, scale, w / 2, h / 2)
        matrix.postRotate(-rotation.toFloat(), w / 2, h / 2)
        view.setTransform(matrix)
        // Account for the stream's sensor crop and the preview's center crop.
        val sensorAspect = sensorWidth / sensorHeight
        val streamAspect = buffer.width.toDouble() / buffer.height
        val cropWidth = if (sensorAspect > streamAspect) sensorHeight * streamAspect else sensorWidth
        val cropHeight = if (sensorAspect > streamAspect) sensorHeight else sensorWidth / streamAspect
        val horizontalSensor = if (swapped) cropHeight else cropWidth
        val visibleFraction = (w / (bw * scale)).toDouble()
        val field = if (focal > 0 && horizontalSensor > 0) 2 * atan(horizontalSensor * visibleFraction / (2 * focal)) else 0.0
        ready(field.isFinite() && field > 0, field)
    }
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) { open() }
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) { transform() }
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { val resume = active; stop(); active = resume; return true }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
}

internal class StormOrientation(context: Context, private val changed: (FloatArray?, Boolean) -> Unit) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val matrix = FloatArray(9)
    private var lastFrame = 0L
    val available get() = sensor != null
    fun start() {
        if (sensor == null || !manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)) changed(null, false)
    }
    fun stop() { manager.unregisterListener(this) }
    override fun onSensorChanged(event: SensorEvent) {
        if (event.values.any { !it.isFinite() } || event.timestamp - lastFrame < 33_000_000L) return
        lastFrame = event.timestamp
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        changed(matrix, event.accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM)
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
