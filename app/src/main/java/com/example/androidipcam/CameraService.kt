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
 * 
 * ============================================================================
 * PERSISTENCE & RELIABILITY FEATURES (REQ-PER-XXX)
 * ============================================================================
 * 
 * This service implements comprehensive persistence mechanisms for 24/7 operation:
 * 
 * 1. FOREGROUND SERVICE (REQ-PER-001)
 *    - Runs as foreground service with persistent notification
 *    - Uses android:foregroundServiceType="camera" in manifest
 *    - Notification displays server URL and connection status
 * 
 * 2. AUTOMATIC RESTART (REQ-PER-002, REQ-PER-003)
 *    - Returns START_STICKY from onStartCommand() for automatic restart after system kill
 *    - Implements onTaskRemoved() to restart service when app is swiped away
 *    - Service persists independently of MainActivity lifecycle
 * 
 * 3. WAKE LOCKS (REQ-PER-004, REQ-PER-005)
 *    - CPU wake lock (PARTIAL_WAKE_LOCK) prevents CPU sleep during streaming
 *    - WiFi wake lock (WIFI_MODE_FULL_HIGH_PERF) maintains high-performance WiFi
 *    - Uses timeout-based acquisition (10 minutes) to prevent battery drain on crashes
 *    - Automatically renews locks every 8 minutes via background coroutine
 * 
 * 4. HEALTH MONITORING (REQ-PER-006, REQ-PER-007)
 *    - Watchdog coroutine runs every 5 seconds checking frame production
 *    - Detects camera failures (no frames for 10+ seconds)
 *    - Exponential backoff for recovery: 1s → 2s → 4s → 8s → 16s → 30s (max)
 *    - Automatically restarts camera on main thread (required by CameraX)
 *    - Resets failure count on successful recovery
 * 
 * 5. SETTINGS PERSISTENCE (REQ-PER-008, REQ-PER-009)
 *    - All settings saved to SharedPreferences immediately on change
 *    - Settings restored from SharedPreferences on service startup
 *    - Persists: camera type, rotation, resolution, port, quality, fps, flashlight, etc.
 *    - Survives app restarts, device reboots, and service kills
 * 
 * 6. BATTERY OPTIMIZATION (REQ-PER-010)
 *    - MainActivity requests battery optimization exemption on first launch
 *    - Ensures service isn't killed by aggressive battery optimization
 *    - Critical for reliable 24/7 surveillance operation
 * 
 * 7. NETWORK MONITORING (REQ-PER-011)
 *    - NetworkMonitor detects WiFi connectivity changes
 *    - Automatically restarts HTTP server on network reconnection
 *    - Handles WiFi state changes, airplane mode, network switches
 *    - Updates notification with new IP address after reconnection
 * 
 * THREADING MODEL:
 * - Main thread: Camera operations (CameraX requires main thread for bindToLifecycle)
 * - Camera executor: Frame capture and processing (single thread)
 * - HTTP pool: 32 threads for handling client requests
 * - Service scope: Background coroutines for watchdog, wake lock renewal, network monitoring
 * 
 * LIFECYCLE:
 * 1. onCreate() → Load settings, create notification, start foreground
 * 2. onStartCommand() → Handle actions (start, stop, switch, flashlight)
 * 3. startStreaming() → Acquire locks, initialize camera, start server/watchdog
 * 4. stopStreaming() → Stop watchdog, release camera, release locks
 * 5. onTaskRemoved() → Restart service when app is swiped away
 * 6. onDestroy() → Clean up resources, cancel coroutines
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
    private var wakeLockRenewalJob: Job? = null

    // Status tracking
    private var isRunning = false
    private var lastFrameTime = 0L
    private var frameCount = 0L
    
    // Wake lock timeout and renewal interval
    private val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L  // 10 minutes
    private val WAKE_LOCK_RENEWAL_INTERVAL_MS = 8 * 60 * 1000L  // 8 minutes (renew before expiry)

    /**
     * FrameListener - Callback interface for receiving camera frames
     * Used by web clients to receive JPEG frames for streaming
     */
    interface FrameListener {
        fun onFrameAvailable(frame: ByteArray)
    }
    
    /**
     * StateChangeListener - Callback interface for immediate state change notifications
     * 
     * This interface satisfies REQ-SST-004: "Camera state changes SHALL propagate to all
     * consumers immediately". Instead of polling, MainActivity receives immediate callbacks
     * when service state, camera type, configuration, or connections change.
     * 
     * This ensures:
     * - UI updates happen immediately (no 2-second polling delay)
     * - Reduced battery usage (no periodic polling)
     * - Better user experience (instant feedback)
     */
    interface StateChangeListener {
        fun onServiceStateChanged(isRunning: Boolean)
        fun onCameraChanged(cameraType: String)
        fun onConfigChanged(config: StreamingConfig)
        fun onConnectionsChanged(count: Int)
    }
    
    private val stateChangeListeners = CopyOnWriteArrayList<StateChangeListener>()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        
        // REQ-PER-009: Restore settings from SharedPreferences on startup
        config = StreamingConfig.load(this)
        Log.d(TAG, "Configuration loaded: camera=${config.cameraType}, port=${config.serverPort}")
        
        // Create notification channel
        createNotificationChannel()
        
        // REQ-PER-001: Start as foreground service with persistent notification
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
        
        // REQ-PER-002: START_STICKY ensures service restarts after system kill
        // System will recreate service and call onStartCommand with null intent
        // Service state is restored from SharedPreferences in onCreate()
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed - restarting service to maintain 24/7 operation")
        
        // REQ-PER-003: Restart service when app is swiped away from recent apps
        // This ensures streaming continues even when MainActivity is destroyed
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
     * 
     * Initializes all components required for 24/7 operation:
     * 1. Acquires wake locks (CPU + WiFi) with auto-renewal
     * 2. Initializes camera with current configuration
     * 3. Starts HTTP web server for MJPEG streaming
     * 4. Starts network monitor for WiFi reconnection handling
     * 5. Starts watchdog for health monitoring and recovery
     * 6. Updates notification with server URL
     * 7. Notifies state change listeners (MainActivity)
     */
    fun startStreaming() {
        if (isRunning) {
            Log.d(TAG, "Already streaming")
            return
        }
        
        Log.d(TAG, "Starting streaming")
        isRunning = true
        
        // REQ-PER-004, REQ-PER-005: Acquire wake locks for 24/7 operation
        acquireWakeLocks()
        
        // Start camera
        initializeCamera()
        
        // Start HTTP server
        startWebServer()
        
        // REQ-PER-011: Start network monitoring for WiFi reconnection
        startNetworkMonitoring()
        
        // REQ-PER-006: Start watchdog for health monitoring
        startWatchdog()
        
        // Update notification
        updateNotification()
        
        // Notify state change listeners
        notifyServiceStateChanged(true)
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
        
        // Notify state change listeners
        notifyServiceStateChanged(false)
    }

    /**
     * Switch between front and back camera
     * 
     * REQ-PER-008: Settings persisted immediately to SharedPreferences
     * REQ-SST-005: Camera switching updates both app UI and web stream
     */
    fun switchCamera() {
        Log.d(TAG, "Switching camera")
        
        config = config.copy(
            cameraType = if (config.cameraType == "back") "front" else "back"
        )
        // REQ-PER-008: Persist settings immediately
        StreamingConfig.save(this, config)
        
        // Notify camera change
        notifyCameraChanged(config.cameraType)
        notifyConfigChanged(config)
        
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
     * 
     * Note: While this appears to give MainActivity direct camera access, it actually maintains
     * the Single Source of Truth principle because:
     * 1. CameraService is the ONLY component that calls bindToLifecycle()
     * 2. MainActivity never accesses the camera directly
     * 3. The Preview use case is bound by CameraService, not MainActivity
     * 4. This is an efficient hardware rendering path (vs. callback-based preview)
     * 5. All camera lifecycle management remains centralized in CameraService
     * 
     * This design satisfies REQ-SST-001 (CameraService sole manager) and REQ-SST-002
     * (MainActivity callback-only) while providing optimal preview performance.
     * 
     * Must be called from main thread
     */
    fun setPreviewSurfaceProvider(surfaceProvider: Preview.SurfaceProvider) {
        previewSurfaceProvider = surfaceProvider
        preview?.setSurfaceProvider(surfaceProvider)
    }

    /**
     * Toggle flashlight (back camera only)
     * 
     * REQ-PER-008: Settings persisted immediately to SharedPreferences
     */
    fun toggleFlashlight() {
        if (config.cameraType != "back") {
            Log.d(TAG, "Flashlight only available for back camera")
            return
        }
        
        config = config.copy(flashlightEnabled = !config.flashlightEnabled)
        // REQ-PER-008: Persist settings immediately
        StreamingConfig.save(this, config)
        
        camera?.cameraControl?.enableTorch(config.flashlightEnabled)
        Log.d(TAG, "Flashlight: ${config.flashlightEnabled}")
        
        // Notify config change
        notifyConfigChanged(config)
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
     * 
     * REQ-PER-006: Health watchdog running every 5 seconds
     * REQ-PER-007: Exponential backoff for recovery (1s → 30s max)
     * 
     * Monitors camera frame production and attempts recovery if frames stop.
     * Uses exponential backoff to avoid excessive recovery attempts.
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
                        Log.w(TAG, "No frames for 10 seconds, attempting recovery (failure count: $failureCount)")
                        failureCount++
                        
                        // Exponential backoff: 1s, 2s, 4s, 8s, 16s, 30s (max)
                        val backoffDelay = minOf(1000L * (1 shl failureCount), 30000L)
                        Log.d(TAG, "Waiting ${backoffDelay}ms before recovery attempt")
                        delay(backoffDelay)
                        
                        // Camera operations must run on main thread
                        withContext(Dispatchers.Main) {
                            releaseCamera()
                            initializeCamera()
                        }
                    } else {
                        // Reset failure count on successful frame production
                        if (failureCount > 0) {
                            Log.d(TAG, "Camera recovered, resetting failure count")
                            failureCount = 0
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Watchdog error", e)
                }
            }
        }
    }

    /**
     * Acquire wake locks to prevent CPU and WiFi sleep
     * 
     * REQ-PER-004: Maintain CPU wake lock during streaming
     * REQ-PER-005: Maintain high-performance WiFi lock
     * 
     * CPU wake lock uses timeout-based acquisition to prevent battery drain if service crashes.
     * WiFi lock doesn't support timeout, so it's managed manually with try-finally patterns.
     * Both locks are automatically renewed every 8 minutes via background coroutine.
     */
    private fun acquireWakeLocks() {
        try {
            // CPU wake lock with timeout
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "IPCam::CameraServiceWakeLock"
            ).apply {
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
            
            // WiFi wake lock (no timeout support, managed by renewal job)
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "IPCam::CameraServiceWifiLock"
            ).apply {
                acquire()
            }
            
            Log.d(TAG, "Wake locks acquired (CPU: ${WAKE_LOCK_TIMEOUT_MS / 1000}s timeout, WiFi: no timeout)")
            
            // Start wake lock renewal job
            startWakeLockRenewal()
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring wake locks", e)
        }
    }

    /**
     * Release wake locks
     */
    private fun releaseWakeLocks() {
        try {
            // Stop wake lock renewal
            wakeLockRenewalJob?.cancel()
            wakeLockRenewalJob = null
            
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
     * Start periodic wake lock renewal to prevent timeout
     * 
     * Wake locks are acquired with a timeout to prevent battery drain if service crashes.
     * This job renews the locks every 8 minutes (before the 10-minute timeout expires).
     * Only CPU wake lock needs renewal (WiFi lock doesn't support timeout).
     */
    private fun startWakeLockRenewal() {
        wakeLockRenewalJob = serviceScope.launch {
            while (isRunning) {
                delay(WAKE_LOCK_RENEWAL_INTERVAL_MS)
                
                try {
                    // Renew CPU wake lock (has timeout)
                    wakeLock?.let {
                        if (it.isHeld) {
                            it.acquire(WAKE_LOCK_TIMEOUT_MS)
                            Log.d(TAG, "CPU wake lock renewed")
                        }
                    }
                    
                    // WiFi wake lock doesn't need renewal (no timeout)
                    // Just verify it's still held
                    wifiLock?.let {
                        if (!it.isHeld) {
                            Log.w(TAG, "WiFi wake lock lost, reacquiring")
                            it.acquire()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error renewing wake locks", e)
                }
            }
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
    fun setActiveConnections(count: Int) {
        activeConnections.set(count)
        notifyConnectionsChanged(count)
    }
    fun incrementConnections(): Int {
        val count = activeConnections.incrementAndGet()
        notifyConnectionsChanged(count)
        return count
    }
    fun decrementConnections(): Int {
        val count = activeConnections.decrementAndGet()
        notifyConnectionsChanged(count)
        return count
    }
    
    fun addFrameListener(listener: FrameListener) {
        frameListeners.add(listener)
    }
    
    fun removeFrameListener(listener: FrameListener) {
        frameListeners.remove(listener)
    }
    
    fun addStateChangeListener(listener: StateChangeListener) {
        stateChangeListeners.add(listener)
    }
    
    fun removeStateChangeListener(listener: StateChangeListener) {
        stateChangeListeners.remove(listener)
    }
    
    private fun notifyServiceStateChanged(isRunning: Boolean) {
        stateChangeListeners.forEach { listener ->
            try {
                listener.onServiceStateChanged(isRunning)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying state change listener", e)
            }
        }
    }
    
    private fun notifyCameraChanged(cameraType: String) {
        stateChangeListeners.forEach { listener ->
            try {
                listener.onCameraChanged(cameraType)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying camera change listener", e)
            }
        }
    }
    
    private fun notifyConfigChanged(config: StreamingConfig) {
        stateChangeListeners.forEach { listener ->
            try {
                listener.onConfigChanged(config)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying config change listener", e)
            }
        }
    }
    
    private fun notifyConnectionsChanged(count: Int) {
        stateChangeListeners.forEach { listener ->
            try {
                listener.onConnectionsChanged(count)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying connections change listener", e)
            }
        }
    }
}
