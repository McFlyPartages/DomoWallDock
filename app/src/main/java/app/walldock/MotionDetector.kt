package app.walldock

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Range
import android.util.Size
import android.view.Display
import android.view.Surface
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/**
 * Détection de mouvement avec la caméra frontale (comme Fully Kiosk) : de petites images
 * (environ 320 × 240), quelques fois par seconde, comparées à la précédente sur une grille
 * de 32 × 24 cases. Les images ne sont jamais enregistrées.
 *
 * Caméra partagée ([highRes]) : la même session fournit de plus grandes images, encodées en
 * JPEG seulement pendant qu'on les demande ([requestJpeg]) — photo ou flux pour Home Assistant
 * et Gladys (voir [CameraServer]). Android ne laisse en général qu'une appli à la fois sur la
 * caméra : on ne l'ouvre donc qu'une fois pour les deux usages.
 *
 * onMotion(true) au premier mouvement, onMotion(false) après [HOLD_MS] sans mouvement.
 */
class MotionDetector(
    private val context: Context,
    private val onMotion: (Boolean) -> Unit
) {

    private val main = Handler(Looper.getMainLooper())
    private var thread: HandlerThread? = null
    private var worker: Handler? = null
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null

    @Volatile
    var running = false
        private set

    /** 0 faible, 1 moyenne, 2 élevée. */
    @Volatile
    var sensitivity = 1

    /** Dernière part de l'image qui a bougé (0–100 %), pour le diagnostic. */
    @Volatile
    var lastChangePercent = 0f
        private set

    /** Dernière erreur (caméra occupée, absente…), null si tout va bien. */
    @Volatile
    var lastError: String? = null
        private set

    /** Images plus grandes pour la caméra partagée ; changer ce réglage rouvre la caméra. */
    @Volatile
    var highRes = false
        set(value) {
            if (field == value) return
            field = value
            if (running) main.post { reopen() }
        }

    /** Rotation imposée (0, 90, 180, 270) ou -1 pour la calculer selon l'orientation de l'écran. */
    @Volatile
    var rotationOverride = -1

    private var sensorOrientation = 0
    private val jpegLock = Object()
    /** Jusqu'à quand (elapsedRealtime) encoder des JPEG : quelqu'un regarde. */
    @Volatile
    private var jpegUntil = 0L
    @Volatile
    private var latestJpeg: ByteArray? = null
    @Volatile
    private var latestJpegAt = 0L

    private var previous: FloatArray? = null
    /** Heure (elapsedRealtime) de la dernière image analysée : prouve que la caméra tourne vraiment. */
    @Volatile
    var lastFrameAt = 0L
        private set
    private var lastMotionAt = 0L
    @Volatile
    private var motion = false
    private var hits = 0

    private val release = Runnable {
        if (motion && SystemClock.elapsedRealtime() - lastMotionAt >= HOLD_MS) {
            motion = false
            onMotion(false)
        }
    }

    private val retry = Runnable { if (running) open() }

    private fun reopen() {
        if (!running) return
        main.removeCallbacks(retry)
        val handler = worker ?: return
        handler.post {
            closeCamera()
            if (running) open()
        }
    }

    /** Demande des images JPEG pendant [holdMs] (photo ou flux en cours). */
    fun requestJpeg(holdMs: Long) {
        val until = SystemClock.elapsedRealtime() + holdMs
        if (until > jpegUntil) jpegUntil = until
    }

    /**
     * Attend une image JPEG plus récente que [newerThan] (elapsedRealtime), au plus [timeoutMs].
     * Renvoie l'image et son heure, ou null si la caméra ne fournit rien.
     */
    fun awaitJpeg(newerThan: Long, timeoutMs: Long): Pair<ByteArray, Long>? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        synchronized(jpegLock) {
            while (true) {
                val jpeg = latestJpeg
                if (jpeg != null && latestJpegAt > newerThan) return jpeg to latestJpegAt
                val left = deadline - SystemClock.elapsedRealtime()
                if (left <= 0 || !running) return null
                jpegLock.wait(left)
            }
        }
    }

    fun start() {
        if (running) return
        if (context.checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            lastError = tr(R.string.motion_no_permission)
            return
        }
        running = true
        val t = HandlerThread("walldock-motion").also { it.start() }
        thread = t
        worker = Handler(t.looper)
        open()
    }

    fun stop() {
        running = false
        synchronized(jpegLock) { jpegLock.notifyAll() }
        main.removeCallbacks(retry)
        main.removeCallbacks(release)
        closeCamera()
        thread?.quitSafely()
        thread = null
        worker = null
        previous = null
        if (motion) {
            motion = false
            onMotion(false)
        }
    }

    @SuppressLint("MissingPermission")
    private fun open() {
        val handler = worker ?: return
        val manager = context.getSystemService(CameraManager::class.java) ?: return
        try {
            val id = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            } ?: manager.cameraIdList.firstOrNull()
            if (id == null) {
                lastError = tr(R.string.motion_no_camera)
                return
            }
            val characteristics = manager.getCameraCharacteristics(id)
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.YUV_420_888) ?: emptyArray()
            val small = sizes.filter { it.width >= 240 && it.height >= 180 }.minByOrNull { it.width * it.height }
                ?: sizes.minByOrNull { it.width * it.height } ?: Size(320, 240)
            // Caméra partagée : la plus grande image jusqu'à 1280 × 720 (assez pour voir, léger à encoder)
            val size = if (highRes) {
                sizes.filter { it.width * it.height <= SHARE_MAX_PIXELS }.maxByOrNull { it.width * it.height } ?: small
            } else small
            val fpsRange = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                ?.minByOrNull { it.upper }
            val imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2)
            imageReader.setOnImageAvailableListener({ onImage(it) }, handler)
            reader = imageReader
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    if (!running) {
                        device.close()
                        return
                    }
                    camera = device
                    startSession(device, imageReader, fpsRange, handler)
                }

                override fun onDisconnected(device: CameraDevice) {
                    // Une autre appli (visio…) a pris la caméra : on réessaiera plus tard
                    lastError = tr(R.string.motion_camera_busy)
                    closeCamera()
                    scheduleRetry()
                }

                override fun onError(device: CameraDevice, error: Int) {
                    lastError = tr(R.string.motion_camera_error, error)
                    closeCamera()
                    scheduleRetry()
                }
            }, handler)
        } catch (e: Exception) {
            lastError = tr(R.string.motion_camera_error_text, e.javaClass.simpleName)
            closeCamera()
            scheduleRetry()
        }
    }

    @Suppress("DEPRECATION")
    private fun startSession(device: CameraDevice, imageReader: ImageReader, fps: Range<Int>?, handler: Handler) {
        try {
            device.createCaptureSession(listOf(imageReader.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    session = s
                    try {
                        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        request.addTarget(imageReader.surface)
                        if (fps != null) request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fps)
                        s.setRepeatingRequest(request.build(), null, handler)
                        lastError = null
                    } catch (e: Exception) {
                        lastError = tr(R.string.motion_camera_error_text, e.javaClass.simpleName)
                        closeCamera()
                        scheduleRetry()
                    }
                }

                override fun onConfigureFailed(s: CameraCaptureSession) {
                    lastError = tr(R.string.motion_camera_error_text, "session")
                    closeCamera()
                    scheduleRetry()
                }
            }, handler)
        } catch (e: Exception) {
            lastError = tr(R.string.motion_camera_error_text, e.javaClass.simpleName)
            closeCamera()
            scheduleRetry()
        }
    }

    private fun onImage(imageReader: ImageReader) {
        val image = try {
            imageReader.acquireLatestImage()
        } catch (e: Exception) {
            null
        } ?: return
        try {
            val now = SystemClock.elapsedRealtime()
            if (highRes && now < jpegUntil && now - latestJpegAt >= JPEG_INTERVAL_MS) encodeJpeg(image, now)
            if (now - lastFrameAt < FRAME_INTERVAL_MS) return
            lastFrameAt = now
            val plane = image.planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val width = image.width
            val height = image.height
            // Luminosité moyenne de chaque case (un pixel sur deux suffit)
            val cells = FloatArray(GRID_W * GRID_H)
            val counts = IntArray(GRID_W * GRID_H)
            var y = 0
            while (y < height) {
                val cy = y * GRID_H / height
                var x = 0
                while (x < width) {
                    val cx = x * GRID_W / width
                    val index = y * rowStride + x * pixelStride
                    if (index < buffer.limit()) {
                        cells[cy * GRID_W + cx] += (buffer.get(index).toInt() and 0xFF).toFloat()
                        counts[cy * GRID_W + cx]++
                    }
                    x += 2
                }
                y += 2
            }
            for (i in cells.indices) if (counts[i] > 0) cells[i] /= counts[i]
            analyse(cells, now)
        } finally {
            image.close()
        }
    }

    private fun encodeJpeg(image: Image, now: Long) {
        val jpeg = try {
            val width = image.width
            val height = image.height
            val out = ByteArrayOutputStream(width * height / 4)
            YuvImage(toNv21(image), ImageFormat.NV21, width, height, null)
                .compressToJpeg(Rect(0, 0, width, height), JPEG_QUALITY, out)
            val rotation = imageRotation()
            if (rotation == 0) out.toByteArray() else rotate(out.toByteArray(), rotation)
        } catch (e: Exception) {
            null
        } ?: return
        synchronized(jpegLock) {
            latestJpeg = jpeg
            latestJpegAt = now
            jpegLock.notifyAll()
        }
    }

    /** YUV_420_888 (trois plans, pas et écarts variables selon la tablette) → NV21, que YuvImage sait encoder. */
    private fun toNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val out = ByteArray(width * height * 3 / 2)
        val y = image.planes[0]
        val yBuffer = y.buffer
        var pos = 0
        for (row in 0 until height) {
            val start = row * y.rowStride
            if (y.pixelStride == 1) {
                yBuffer.position(start)
                yBuffer.get(out, pos, width)
                pos += width
            } else {
                for (col in 0 until width) out[pos++] = yBuffer.get(start + col * y.pixelStride)
            }
        }
        val u = image.planes[1]
        val v = image.planes[2]
        val uBuffer = u.buffer
        val vBuffer = v.buffer
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val uIndex = row * u.rowStride + col * u.pixelStride
                val vIndex = row * v.rowStride + col * v.pixelStride
                out[pos++] = if (vIndex < vBuffer.limit()) vBuffer.get(vIndex) else 0
                out[pos++] = if (uIndex < uBuffer.limit()) uBuffer.get(uIndex) else 0
            }
        }
        return out
    }

    /**
     * Rotation à appliquer pour que l'image soit droite. Caméra frontale (image non retournée) :
     * orientation du capteur + rotation de l'écran.
     */
    private fun imageRotation(): Int {
        val forced = rotationOverride
        if (forced in listOf(0, 90, 180, 270)) return forced
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        val degrees = when (display?.rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return (sensorOrientation + degrees) % 360
    }

    private fun rotate(jpeg: ByteArray, degrees: Int): ByteArray {
        val source = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return jpeg
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        val out = ByteArrayOutputStream(jpeg.size)
        rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (rotated !== source) rotated.recycle()
        source.recycle()
        return out.toByteArray()
    }

    private fun analyse(cells: FloatArray, now: Long) {
        val before = previous
        previous = cells
        if (before == null) return
        // On retire le changement global de luminosité (exposition automatique, lumière allumée)
        var meanDelta = 0f
        for (i in cells.indices) meanDelta += cells[i] - before[i]
        meanDelta /= cells.size
        var changed = 0
        for (i in cells.indices) {
            if (abs(cells[i] - before[i] - meanDelta) > CELL_THRESHOLD) changed++
        }
        val percent = changed * 100f / cells.size
        lastChangePercent = percent
        val limit = THRESHOLDS[sensitivity.coerceIn(0, 2)]
        // Deux images de suite au-dessus du seuil : évite les déclenchements sur un reflet
        hits = if (percent >= limit) hits + 1 else 0
        if (hits >= 2) {
            lastMotionAt = now
            if (!motion) {
                motion = true
                main.post { onMotion(true) }
            }
            main.removeCallbacks(release)
            main.postDelayed(release, HOLD_MS)
        }
    }

    private fun scheduleRetry() {
        if (!running) return
        main.removeCallbacks(retry)
        main.postDelayed(retry, RETRY_MS)
    }

    private fun closeCamera() {
        try {
            session?.close()
        } catch (e: Exception) {
            // déjà fermée
        }
        session = null
        try {
            camera?.close()
        } catch (e: Exception) {
            // déjà fermée
        }
        camera = null
        try {
            reader?.close()
        } catch (e: Exception) {
            // déjà fermé
        }
        reader = null
        previous = null
    }

    companion object {
        private const val GRID_W = 32
        private const val GRID_H = 24
        private const val FRAME_INTERVAL_MS = 250L
        private const val CELL_THRESHOLD = 14f
        /** Part de l'image qui doit bouger (%) selon la sensibilité : faible, moyenne, élevée. */
        private val THRESHOLDS = floatArrayOf(6f, 3f, 1.2f)
        /** Le capteur « Mouvement » reste allumé 15 s après le dernier mouvement. */
        const val HOLD_MS = 15_000L
        private const val RETRY_MS = 60_000L
        /** Une image JPEG toutes les 150 ms au plus (environ 6 images par seconde). */
        private const val JPEG_INTERVAL_MS = 150L
        private const val JPEG_QUALITY = 70
        private const val SHARE_MAX_PIXELS = 1280 * 720
    }
}
