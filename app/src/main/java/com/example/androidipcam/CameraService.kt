package com.example.androidipcam

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * CameraService - Single Source of Truth for Camera Management
 * 
 * This foreground service manages the camera resource and distributes frames
 * to both the app preview and all connected web clients.
 * 
 * Key responsibilities:
 * - Camera lifecycle management (start, stop, switch)
 * - Frame capture and JPEG compression
 * - Frame distribution to multiple consumers
 * - HTTP server hosting
 * - Settings persistence
 * - Watchdog monitoring and recovery
 * - Wake lock management for 24/7 operation
 */
class CameraService : LifecycleService() {

    companion object {
        private const val TAG = "CameraService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "camera_service_channel"
        private const val WATCHDOG_INTERVAL_MS = 5000L
        const val ACTION_START_SERVICE = "com.example.ipcam.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.example.ipcam.STOP_SERVICE"
        const val ACTION_SWITCH_CAMERA = "com.example.ipcam.SWITCH_CAMERA"
        const val ACTION_TOGGLE_FLASHLIGHT = "com.example.ipcam.TOGGLE_FLASHLIGHT"
    }

    // Service binding
    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): CameraService = this@CameraService
    }

    // Configuration
    private var config: StreamingConfig = StreamingConfig()

    // Camera components
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private var previewSurfaceProvider: Preview.SurfaceProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Frame management
    private var latestFrame: ByteArray? = null
    private val frameListeners = CopyOnWriteArrayList<FrameListener>()
    private val activeConnections = AtomicInteger(0)

    // HTTP Server
    private var webServer: IPCamWebServer? = null

    // Wake locks for 24/7 operation
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    // Network monitoring
    private var networkMonitor: NetworkMonitor? = null

    // Coroutine scope for service lifecycle
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watchdogJob: Job? = null

    // Status tracking
    private var isRunning = false
    private var lastFrameTime = 0L
    private var frameCount = 0L

    interface FrameListener {
        fun onFrameAvailable(frame: ByteArray)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        
        // Load configuration
        config = StreamingConfig.load(this)
        
        // Create notification channel
        createNotificationChannel()
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        when (intent?.action) {
            ACTION_START_SERVICE -> startStreaming()
            ACTION_STOP_SERVICE -> stopStreaming()
            ACTION_SWITCH_CAMERA -> switchCamera()
            ACTION_TOGGLE_FLASHLIGHT -> toggleFlashlight()
        }
        
        // START_STICKY ensures service restarts after system kill
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed - restarting service")
        
        // Restart service when app is swiped away
        val restartIntent = Intent(applicationContext, CameraService::class.java).apply {
            action = ACTION_START_SERVICE
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        
        stopStreaming()
        releaseResources()
        serviceScope.cancel()
    }

    /**
     * Start camera streaming and HTTP server
     */
    fun startStreaming() {
        if (isRunning) {
            Log.d(TAG, "Already streaming")
            return
        }
        
        Log.d(TAG, "Starting streaming")
        isRunning = true
        
        // Acquire wake locks
        acquireWakeLocks()
        
        // Start camera
        initializeCamera()
        
        // Start HTTP server
        startWebServer()
        
        // Start network monitoring
        startNetworkMonitoring()
        
        // Start watchdog
        startWatchdog()
        
        // Update notification
        updateNotification()
    }

    /**
     * Stop camera streaming and HTTP server
     */
    fun stopStreaming() {
        if (!isRunning) {
            return
        }
        
        Log.d(TAG, "Stopping streaming")
        isRunning = false
        
        // Stop watchdog
        watchdogJob?.cancel()
        
        // Stop network monitoring
        stopNetworkMonitoring()
        
        // Stop HTTP server
        webServer?.stop()
        webServer = null
        
        // Release camera
        releaseCamera()
        
        // Release wake locks
        releaseWakeLocks()
        
        // Update notification
        updateNotification()
    }

    /**
     * Switch between front and back camera
     */
    fun switchCamera() {
        Log.d(TAG, "Switching camera")
        
        config = config.copy(
            cameraType = if (config.cameraType == "back") "front" else "back"
        )
        StreamingConfig.save(this, config)
        
        if (isRunning) {
            // Run camera operations on main thread
            runOnMainThread {
                releaseCamera()
                initializeCamera()
            }
        }
    }
    
    /**
     * Set the Preview SurfaceProvider from MainActivity
     * Must be called from main thread
     */
    fun setPreviewSurfaceProvider(surfaceProvider: Preview.SurfaceProvider) {
        previewSurfaceProvider = surfaceProvider
        preview?.setSurfaceProvider(surfaceProvider)
    }

    /**
     * Toggle flashlight (back camera only)
     */
    fun toggleFlashlight() {
        if (config.cameraType != "back") {
            Log.d(TAG, "Flashlight only available for back camera")
            return
        }
        
        config = config.copy(flashlightEnabled = !config.flashlightEnabled)
        StreamingConfig.save(this, config)
        
        camera?.cameraControl?.enableTorch(config.flashlightEnabled)
        Log.d(TAG, "Flashlight: ${config.flashlightEnabled}")
    }

    /**
     * Initialize CameraX and start frame capture
     */
    private fun initializeCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        // Must run on main thread for CameraX bindToLifecycle()
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize camera", e)
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(this))
    }

    /**
     * Bind camera use cases (Preview + ImageAnalysis)
     */
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        
        // Unbind all use cases before rebinding
        cameraProvider.unbindAll()
        
        // Select camera
        val cameraSelector = if (config.cameraType == "front") {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        
        // Configure preview
        preview = Preview.Builder()
            .build()
            .also { 
                // Set surface provider if available (from MainActivity)
                previewSurfaceProvider?.let { provider ->
                    it.setSurfaceProvider(provider)
                }
            }
        
        // Configure image analysis
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { image ->
                    processFrame(image)
                }
            }
        
        try {
            // Bind use cases to lifecycle (include preview for MainActivity)
            camera = cameraProvider.bindToLifecycle(
                this as LifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
            
            // Enable flashlight if configured
            if (config.flashlightEnabled && config.cameraType == "back") {
                camera?.cameraControl?.enableTorch(true)
            }
            
            Log.d(TAG, "Camera bound successfully: ${config.cameraType}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
        }
    }

    /**
     * Process camera frame: convert YUV to JPEG and distribute to listeners
     */
    private fun processFrame(image: ImageProxy) {
        try {
            // Throttle frame rate to target FPS
            val currentTime = System.currentTimeMillis()
            val targetInterval = 1000L / config.targetFps
            
            if (currentTime - lastFrameTime < targetInterval) {
                image.close()
                return
            }
            
            lastFrameTime = currentTime
            frameCount++
            
            // Convert YUV to JPEG
            val jpegData = yuvToJpeg(image, config.jpegQuality)
            
            if (jpegData != null) {
                latestFrame = jpegData
                
                // Notify all listeners
                frameListeners.forEach { listener ->
                    try {
                        listener.onFrameAvailable(jpegData)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error notifying frame listener", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
        } finally {
            image.close()
        }
    }

    /**
     * Convert YUV image to JPEG bytes
     */
    private fun yuvToJpeg(image: ImageProxy, quality: Int): ByteArray? {
        return try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), quality, out)
            out.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Error converting YUV to JPEG", e)
            null
        }
    }

    /**
     * Release camera resources
     * Must be called from main thread
     */
    private fun releaseCamera() {
        try {
            cameraProvider?.unbindAll()
            camera = null
            imageAnalysis = null
            preview = null
            Log.d(TAG, "Camera released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing camera", e)
        }
    }
    
    /**
     * Helper to run code on main thread
     */
    private fun runOnMainThread(block: () -> Unit) {
        val mainExecutor = androidx.core.content.ContextCompat.getMainExecutor(this)
        mainExecutor.execute(block)
    }

    /**
     * Start HTTP web server
     */
    private fun startWebServer() {
        try {
            webServer = IPCamWebServer(config.serverPort, this)
            webServer?.start()
            Log.d(TAG, "Web server started on port ${config.serverPort}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start web server", e)
        }
    }

    /**
     * Start network monitoring
     */
    private fun startNetworkMonitoring() {
        try {
            networkMonitor = NetworkMonitor(this) {
                // Network reconnected - restart server
                Log.d(TAG, "Network reconnected, restarting server")
                serviceScope.launch {
                    delay(2000) // Wait for network to stabilize
                    webServer?.stop()
                    startWebServer()
                    updateNotification()
                }
            }
            networkMonitor?.start()
            Log.d(TAG, "Network monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start network monitoring", e)
        }
    }

    /**
     * Stop network monitoring
     */
    private fun stopNetworkMonitoring() {
        try {
            networkMonitor?.stop()
            networkMonitor = null
            Log.d(TAG, "Network monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop network monitoring", e)
        }
    }

    /**
     * Start watchdog for health monitoring
     */
    private fun startWatchdog() {
        watchdogJob = serviceScope.launch {
            var failureCount = 0
            
            while (isRunning) {
                delay(WATCHDOG_INTERVAL_MS)
                
                try {
                    // Check if camera is still producing frames
                    val timeSinceLastFrame = System.currentTimeMillis() - lastFrameTime
                    
                    if (timeSinceLastFrame > 10000) {
                        Log.w(TAG, "No frames for 10 seconds, attempting recovery")
                        failureCount++
                        
                        // Exponential backoff: 1s, 2s, 4s, 8s, 16s, 30s (max)
                        val backoffDelay = minOf(1000L * (1 shl failureCount), 30000L)
                        delay(backoffDelay)
                        
                        releaseCamera()
                        initializeCamera()
                    } else {
                        failureCount = 0
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Watchdog error", e)
                }
            }
        }
    }

    /**
     * Acquire wake locks to prevent CPU and WiFi sleep
     */
    private fun acquireWakeLocks() {
        try {
            // CPU wake lock
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "IPCam::CameraServiceWakeLock"
            ).apply {
                acquire()
            }
            
            // WiFi wake lock
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "IPCam::CameraServiceWifiLock"
            ).apply {
                acquire()
            }
            
            Log.d(TAG, "Wake locks acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring wake locks", e)
        }
    }

    /**
     * Release wake locks
     */
    private fun releaseWakeLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
            
            wifiLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wifiLock = null
            
            Log.d(TAG, "Wake locks released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake locks", e)
        }
    }

    /**
     * Release all resources
     */
    private fun releaseResources() {
        releaseCamera()
        cameraExecutor.shutdown()
    }

    /**
     * Create notification channel for Android 8.0+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.service_notification_channel_desc)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create foreground service notification
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val serverUrl = if (isRunning) getServerUrl() else "Not running"
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(String.format(getString(R.string.service_notification_text), serverUrl))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    /**
     * Update notification with current status
     */
    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    /**
     * Get server URL for display
     */
    fun getServerUrl(): String {
        val ip = getIPAddress()
        return if (ip != null) {
            "http://$ip:${config.serverPort}"
        } else {
            "No network connection"
        }
    }

    /**
     * Get device IP address
     */
    private fun getIPAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    
                    if (!address.isLoopbackAddress && !address.isLinkLocalAddress) {
                        val ip = address.hostAddress
                        if (ip != null && ip.indexOf(':') < 0) { // IPv4
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP address", e)
        }
        return null
    }

    // Public API for external access
    fun getConfig(): StreamingConfig = config
    fun isServiceRunning(): Boolean = isRunning
    fun getLatestFrame(): ByteArray? = latestFrame
    fun getActiveConnections(): Int = activeConnections.get()
    fun setActiveConnections(count: Int) = activeConnections.set(count)
    fun incrementConnections() = activeConnections.incrementAndGet()
    fun decrementConnections() = activeConnections.decrementAndGet()
    
    fun addFrameListener(listener: FrameListener) {
        frameListeners.add(listener)
    }
    
    fun removeFrameListener(listener: FrameListener) {
        frameListeners.remove(listener)
    }
}
