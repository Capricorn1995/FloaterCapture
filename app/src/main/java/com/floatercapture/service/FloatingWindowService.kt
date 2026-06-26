package com.floatercapture.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.floatercapture.R
import com.floatercapture.data.model.MediaType
import com.floatercapture.data.repository.MediaRepository
import com.floatercapture.util.NotificationHelper
import com.floatercapture.util.PermissionHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 前台服务，管理悬浮窗的显示与交互。
 * 悬浮窗包含一个可拖拽的 FAB 按钮和可展开的控制面板。
 */
class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isPanelExpanded = false
    private var isInitialPositionSet = false

    // 拖拽相关变量
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mediaRepository = MediaRepository()

    // 统计计数
    private var imageCount = 0
    private var videoCount = 0
    private var documentCount = 0
    private var otherCount = 0

    /**
     * 监听 ACTION_MEDIA_FOUND 广播，收到后更新控制面板中的统计数据
     */
    private val mediaFoundReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Companion.ACTION_MEDIA_FOUND) {
                val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE) ?: return
                updateCounts(mediaType)
                refreshControlPanelStats()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // 使用LocalBroadcastManager注册，与MediaCaptureService保持一致
        LocalBroadcastManager.getInstance(this).registerReceiver(
            mediaFoundReceiver,
            IntentFilter(Companion.ACTION_MEDIA_FOUND)
        )
        // 启动时读取已有数据初始化计数
        initCountsFromRepository()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 必须在 onStartCommand 中尽快调用 startForeground，
        // 否则 startForegroundService 会触发系统崩溃
        startForegroundService()

        when (intent?.action) {
            ACTION_SHOW -> showFloatingWindow()
            ACTION_HIDE -> hideFloatingWindow()
            ACTION_TOGGLE -> toggleFloatingWindow()
            else -> {
                // 默认行为：显示悬浮窗
                showFloatingWindow()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mediaFoundReceiver)
        } catch (e: Exception) {
            // 接收器可能未注册
        }
        serviceScope.cancel()
        removeFloatingWindow()
        super.onDestroy()
    }

    // ========================
    // 前台服务
    // ========================

    private fun startForegroundService() {
        try {
            val notification = NotificationHelper.createServiceNotification(this)
            startForeground(NOTIFICATION_ID_SERVICE, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ========================
    // 悬浮窗管理
    // ========================

    private fun showFloatingWindow() {
        if (floatingView != null) return

        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !PermissionHelper.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show()
            _isRunning.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        try {
            floatingView = View.inflate(this, R.layout.floating_window, null)
            setupFloatingView()
            setupDragSupport()
            setupControlPanel()

            layoutParams = createLayoutParams()
            windowManager.addView(floatingView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "悬浮窗启动失败: ${e.message}", Toast.LENGTH_LONG).show()
            _isRunning.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun hideFloatingWindow() {
        removeFloatingWindow()
        _isRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun toggleFloatingWindow() {
        if (floatingView != null) {
            hideFloatingWindow()
        } else {
            showFloatingWindow()
        }
    }

    private fun removeFloatingWindow() {
        try {
            if (floatingView != null) {
                windowManager.removeView(floatingView)
                floatingView = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        layoutParams = null
        isPanelExpanded = false
    }

    /**
     * 创建 WindowManager.LayoutParams。
     * Android O+ 使用 TYPE_APPLICATION_OVERLAY，否则使用 TYPE_PHONE。
     */
    private fun createLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 初始位置：屏幕右侧中间
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            x = metrics.widthPixels - 200
            y = metrics.heightPixels / 3
        }
    }

    // ========================
    // 悬浮窗视图设置
    // ========================

    private fun setupFloatingView() {
        val fab = floatingView?.findViewById<FloatingActionButton>(R.id.fab_capture) ?: return

        fab.setOnClickListener {
            if (!isDragging) {
                toggleControlPanel()
            }
            isDragging = false
        }
    }

    /**
     * 实现 FAB 按钮的拖拽功能。
     * 使用 OnTouchListener 跟踪手指移动，计算位移并更新 LayoutParams。
     */
    private fun setupDragSupport() {
        val fab = floatingView?.findViewById<FloatingActionButton>(R.id.fab_capture) ?: return

        fab.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 记录初始位置和触摸点
                    initialX = layoutParams?.x ?: 0
                    initialY = layoutParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    // 移动超过阈值才认为是拖拽
                    if (kotlin.math.abs(deltaX) > DRAG_THRESHOLD || kotlin.math.abs(deltaY) > DRAG_THRESHOLD) {
                        isDragging = true
                    }

                    if (isDragging) {
                        layoutParams?.let { params ->
                            params.x = initialX + deltaX
                            params.y = initialY + deltaY
                            // 边界约束
                            params.x = params.x.coerceAtLeast(0)
                            params.y = params.y.coerceAtLeast(0)
                            try {
                                windowManager.updateViewLayout(floatingView, params)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 手指抬起，结束拖拽
                    true
                }
                else -> false
            }
        }
    }

    // ========================
    // 控制面板
    // ========================

    private fun setupControlPanel() {
        val cardPanel = floatingView?.findViewById<View>(R.id.card_control_panel) ?: return

        // 下载全部按钮
        cardPanel.findViewById<View>(R.id.btn_download_all)?.setOnClickListener {
            onDownloadAllClicked()
        }

        // 清空按钮 -> 等同于查看列表
        cardPanel.findViewById<View>(R.id.btn_clear)?.setOnClickListener {
            openMainActivity()
        }

        // 收起面板按钮
        cardPanel.findViewById<View>(R.id.btn_collapse)?.setOnClickListener {
            collapseControlPanel()
        }

        // 设置按钮
        cardPanel.findViewById<View>(R.id.btn_settings)?.setOnClickListener {
            openMainActivity()
        }
    }

    private fun toggleControlPanel() {
        val cardPanel = floatingView?.findViewById<View>(R.id.card_control_panel) ?: return

        if (isPanelExpanded) {
            collapseControlPanel()
        } else {
            expandControlPanel()
        }
    }

    private fun expandControlPanel() {
        val cardPanel = floatingView?.findViewById<View>(R.id.card_control_panel) ?: return
        cardPanel.visibility = View.VISIBLE
        isPanelExpanded = true
        refreshControlPanelStats()
        // 展开时更新布局以容纳面板
        updateFloatingViewSize(true)
    }

    private fun collapseControlPanel() {
        val cardPanel = floatingView?.findViewById<View>(R.id.card_control_panel) ?: return
        cardPanel.visibility = View.GONE
        isPanelExpanded = false
        updateFloatingViewSize(false)
    }

    /**
     * 展开/收起时更新悬浮窗布局参数
     */
    private fun updateFloatingViewSize(expanded: Boolean) {
        layoutParams?.let { params ->
            params.width = if (expanded) {
                WindowManager.LayoutParams.WRAP_CONTENT
            } else {
                WindowManager.LayoutParams.WRAP_CONTENT
            }
            try {
                windowManager.updateViewLayout(floatingView, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ========================
    // 控制面板统计更新
    // ========================

    /**
     * 更新控制面板中的媒体统计数据
     */
    private fun refreshControlPanelStats() {
        val cardPanel = floatingView?.findViewById<View>(R.id.card_control_panel) ?: return

        val totalCount = imageCount + videoCount + documentCount + otherCount

        // 更新总数文本
        val tvMediaCount = cardPanel.findViewById<android.widget.TextView>(R.id.tv_media_count)
        tvMediaCount?.text = if (totalCount > 0) {
            "已发现 $totalCount 个媒体文件"
        } else {
            getString(R.string.no_media_detected)
        }

        // 更新分类计数
        cardPanel.findViewById<android.widget.TextView>(R.id.tv_image_count)?.text = imageCount.toString()
        cardPanel.findViewById<android.widget.TextView>(R.id.tv_video_count)?.text = videoCount.toString()
        cardPanel.findViewById<android.widget.TextView>(R.id.tv_audio_count)?.text = documentCount.toString()
        cardPanel.findViewById<android.widget.TextView>(R.id.tv_other_count)?.text = otherCount.toString()

        // 更新下载全部按钮状态
        cardPanel.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_download_all)?.isEnabled =
            totalCount > 0
    }

    /**
     * 根据接收到的广播更新对应类型的计数
     */
    private fun updateCounts(mediaType: String) {
        when (mediaType) {
            "IMAGE" -> imageCount++
            "VIDEO" -> videoCount++
            "DOCUMENT" -> documentCount++
            "OTHER" -> otherCount++
        }
    }

    /**
     * 从数据库读取已有数据初始化计数
     */
    private fun initCountsFromRepository() {
        serviceScope.launch {
            try {
                mediaRepository.getAll().collect { items ->
                    imageCount = items.count { it.type == MediaType.IMAGE }
                    videoCount = items.count { it.type == MediaType.VIDEO }
                    documentCount = items.count { it.type == MediaType.DOCUMENT }
                    otherCount = items.count { it.type == MediaType.OTHER }
                    if (isPanelExpanded) {
                        refreshControlPanelStats()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ========================
    // 按钮点击处理
    // ========================

    /**
     * 下载全部：启动 DownloadService 下载所有媒体项
     */
    private fun onDownloadAllClicked() {
        serviceScope.launch {
            try {
                // 获取所有未下载的媒体项
                val allItems = mediaRepository.getAll().first()
                val pendingItems = allItems.filter { !it.isDownloaded }
                if (pendingItems.isEmpty()) {
                    Toast.makeText(this@FloatingWindowService, "没有可下载的媒体文件", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                DownloadService.startDownload(this@FloatingWindowService, pendingItems)
                Toast.makeText(
                    this@FloatingWindowService,
                    "开始下载 ${pendingItems.size} 个文件",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@FloatingWindowService, "启动下载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 打开主界面
     */
    private fun openMainActivity() {
        try {
            // 通过packageName启动主Activity
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ========================
    // Companion
    // ========================

    companion object {
        const val ACTION_SHOW = "com.floatercapture.action.SHOW_FLOATING"
        const val ACTION_HIDE = "com.floatercapture.action.HIDE_FLOATING"
        const val ACTION_TOGGLE = "com.floatercapture.action.TOGGLE_FLOATING"

        const val ACTION_MEDIA_FOUND = "com.floatercapture.ACTION_MEDIA_FOUND"
        const val EXTRA_MEDIA_TYPE = "extra_media_type"
        const val EXTRA_MEDIA_ID = "extra_media_id"

        const val NOTIFICATION_ID_SERVICE = 1001

        private const val DRAG_THRESHOLD = 10

        /** 使用 StateFlow 跟踪服务运行状态 */
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        /**
         * 启动悬浮窗服务
         */
        fun startService(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_SHOW
            }
            ContextCompat.startForegroundService(context, intent)
            _isRunning.value = true
        }

        /**
         * 停止悬浮窗服务
         */
        fun stopService(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_HIDE
            }
            context.startService(intent)
            _isRunning.value = false
        }
    }
}
