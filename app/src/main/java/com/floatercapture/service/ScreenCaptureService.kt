package com.floatercapture.service

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Surface
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.floatercapture.FloaterApp
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 屏幕截图服务。
 * 使用 MediaProjection API 捕获屏幕内容并保存为图片文件。
 * 截图操作在后台 HandlerThread 中执行，避免阻塞主线程。
 */
class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    override fun onCreate() {
        super.onCreate()
        // 创建后台线程处理截图
        backgroundThread = HandlerThread("ScreenCaptureThread").apply {
            start()
        }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CAPTURE -> {
                captureScreen()
            }
            ACTION_INIT_PROJECTION -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }
                if (resultCode == Activity.RESULT_OK && data != null) {
                    handlePermissionResult(resultCode, data)
                }
            }
            else -> {
                // 默认：启动前台服务
                startForegroundService()
            }
        }
        return START_NOT_STICKY
    }

    // ========================
    // 前台服务
    // ========================

    private fun startForegroundService() {
        val notification = com.floatercapture.util.NotificationHelper.createServiceNotification(this)
        startForeground(NOTIFICATION_ID_SERVICE, notification)
    }

    // ========================
    // 屏幕尺寸获取
    // ========================

    /**
     * 获取屏幕尺寸和密度信息
     */
    private fun initScreenMetrics() {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            DisplayMetrics().also {
                it.widthPixels = bounds.width()
                it.heightPixels = bounds.height()
                it.density = windowMetrics.density
            }
        } else {
            @Suppress("DEPRECATION")
            DisplayMetrics().also {
                windowManager.defaultDisplay.getRealMetrics(it)
            }
        }
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    // ========================
    // 截图核心逻辑
    // ========================

    /**
     * 执行屏幕截图：
     * 1. 获取屏幕尺寸
     * 2. 创建 ImageReader 和 VirtualDisplay
     * 3. 捕获 Image 并转换为 Bitmap
     * 4. 保存到 Pictures/FloaterCapture 目录
     * 5. 释放资源
     */
    private fun captureScreen() {
        val projection = mediaProjection
        if (projection == null) {
            android.util.Log.w(TAG, "MediaProjection not initialized")
            return
        }

        initScreenMetrics()

        backgroundHandler?.post {
            try {
                // 创建 ImageReader 用于接收屏幕截图
                imageReader = ImageReader.newInstance(
                    screenWidth,
                    screenHeight,
                    PixelFormat.RGBA_8888,
                    MAX_IMAGES
                )

                // 创建 VirtualDisplay
                virtualDisplay = projection.createVirtualDisplay(
                    TAG,
                    screenWidth,
                    screenHeight,
                    screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader!!.surface,
                    null,
                    backgroundHandler
                )

                // 等待一帧画面渲染
                Thread.sleep(CAPTURE_DELAY_MS)

                // 从 ImageReader 获取最新图像
                val image = imageReader?.acquireLatestImage()
                if (image != null) {
                    try {
                        val bitmap = imageToBitmap(image)
                        if (bitmap != null) {
                            saveBitmapToFile(bitmap)
                            bitmap.recycle()
                        }
                    } finally {
                        image.close()
                    }
                } else {
                    android.util.Log.w(TAG, "Failed to acquire image from ImageReader")
                }

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Capture failed", e)
            } finally {
                releaseResources()
            }
        }
    }

    /**
     * 将 Image 对象转换为 Bitmap。
     */
    private fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            if (rowPadding > 0) {
                // 裁剪掉填充部分
                Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to convert image to bitmap", e)
            null
        }
    }

    /**
     * 将 Bitmap 保存到 Pictures/FloaterCapture 目录。
     */
    private fun saveBitmapToFile(bitmap: Bitmap) {
        try {
            val saveDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "FloaterCapture"
            )
            if (!saveDir.exists()) {
                saveDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.getDefault())
                .format(Date())
            val fileName = "screenshot_$timestamp.png"
            val file = File(saveDir, fileName)

            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, QUALITY, outputStream)
            }

            android.util.Log.d(TAG, "Screenshot saved: ${file.absolutePath}")

            // 扫描文件到媒体库
            android.media.MediaScannerConnection.scanFile(
                this,
                arrayOf(file.absolutePath),
                arrayOf("image/png")
            ) { path, uri ->
                if (uri != null) {
                    android.util.Log.d(TAG, "Screenshot scanned: $path -> $uri")
                }
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to save screenshot", e)
        }
    }

    /**
     * 释放 MediaProjection、VirtualDisplay 和 ImageReader 资源。
     */
    private fun releaseResources() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to release VirtualDisplay", e)
        }

        try {
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to close ImageReader", e)
        }
    }

    override fun onDestroy() {
        // 释放 MediaProjection
        try {
            mediaProjection?.stop()
            mediaProjection = null
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to stop MediaProjection", e)
        }

        releaseResources()

        // 停止后台线程
        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null

        super.onDestroy()
    }

    // ========================
    // Companion
    // ========================

    companion object {
        private const val TAG = "ScreenCaptureService"

        const val ACTION_CAPTURE = "com.floatercapture.action.CAPTURE_SCREEN"
        const val ACTION_INIT_PROJECTION = "com.floatercapture.action.INIT_PROJECTION"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"

        const val NOTIFICATION_ID_SERVICE = 1001
        const val REQUEST_CODE_MEDIA_PROJECTION = 999

        private const val MAX_IMAGES = 2
        private const val CAPTURE_DELAY_MS = 100L
        private const val QUALITY = 95

        /** 缓存的 MediaProjection 授权数据（跨 Activity 生命周期） */
        private var cachedResultCode: Int = 0
        private var cachedData: Intent? = null

        /**
         * 启动屏幕截图权限请求。
         * 在 Activity 中调用此方法，会启动系统授权对话框。
         */
        fun requestPermission(activity: Activity, requestCode: Int = REQUEST_CODE_MEDIA_PROJECTION) {
            val mediaProjectionManager = activity.getSystemService(
                Context.MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager
            activity.startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(),
                requestCode
            )
        }

        /**
         * 处理权限请求结果，初始化 MediaProjection 并启动服务。
         * 在 Activity.onActivityResult 中调用此方法。
         */
        fun handlePermissionResult(context: Context, resultCode: Int, data: Intent) {
            if (resultCode == Activity.RESULT_OK) {
                // 缓存授权数据
                cachedResultCode = resultCode
                cachedData = data

                // 启动截图服务并传入授权数据
                val intent = Intent(context, ScreenCaptureService::class.java).apply {
                    action = ACTION_INIT_PROJECTION
                    putExtra(EXTRA_RESULT_CODE, resultCode)
                    putExtra(EXTRA_DATA, data)
                }
                ContextCompat.startForegroundService(context, intent)
            }
        }

        /**
         * 触发截图。
         * 在已初始化的服务中调用。
         */
        fun captureScreen(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_CAPTURE
            }
            context.startService(intent)
        }
    }

    /**
     * 处理 MediaProjection 权限结果，在服务内部初始化 MediaProjection 实例。
     */
    fun handlePermissionResult(resultCode: Int, data: Intent) {
        try {
            val mediaProjectionManager = getSystemService(
                Context.MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

            // 使用缓存的 Intent 或传入的 Intent 获取 MediaProjection
            val intent = data
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, intent)

            // 注册 MediaProjection 停止回调
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    android.util.Log.d(TAG, "MediaProjection stopped")
                    mediaProjection = null
                    releaseResources()
                }
            }, backgroundHandler)

            android.util.Log.d(TAG, "MediaProjection initialized successfully")

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to initialize MediaProjection", e)
        }
    }
}
