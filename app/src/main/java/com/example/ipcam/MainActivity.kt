package com.example.ipcam

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.ipcam.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * MainActivity - Main UI for IP_Cam Application
 * 
 * Responsibilities:
 * - Request camera and notification permissions
 * - Bind to CameraService
 * - Display camera preview via callback
 * - Provide controls for start/stop, camera switch, flashlight
 * - Display real-time status (server URL, connections, camera)
 * - Request battery optimization exemption
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    
    // Service binding
    private var cameraService: CameraService? = null
    private var serviceBound = false
    
    // UI update coroutine
    private val uiScope = CoroutineScope(Dispatchers.Main + Job())
    private var statusUpdateJob: Job? = null
    
    // Frame listener for preview
    private val frameListener = object : CameraService.FrameListener {
        override fun onFrameAvailable(frame: ByteArray) {
            // Note: PreviewView is handled by CameraX directly
            // This callback can be used for custom preview if needed
        }
    }
    
    // Service connection
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CameraService.LocalBinder
            cameraService = binder.getService()
            serviceBound = true
            Log.d(TAG, "Service connected")
            updateUI()
            startStatusUpdates()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            cameraService = null
            serviceBound = false
            Log.d(TAG, "Service disconnected")
        }
    }
    
    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            true
        }
        
        if (cameraGranted && notificationGranted) {
            Log.d(TAG, "Permissions granted")
            requestBatteryOptimizationExemption()
        } else {
            Toast.makeText(
                this,
                getString(R.string.camera_permission_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize view binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup button listeners
        setupButtons()
        
        // Check and request permissions
        checkAndRequestPermissions()
        
        // Bind to service
        bindCameraService()
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Stop status updates
        statusUpdateJob?.cancel()
        
        // Remove frame listener
        cameraService?.removeFrameListener(frameListener)
        
        // Unbind service
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    /**
     * Setup button click listeners
     */
    private fun setupButtons() {
        binding.startStopButton.setOnClickListener {
            if (cameraService?.isServiceRunning() == true) {
                stopCameraService()
            } else {
                startCameraService()
            }
        }
        
        binding.switchCameraButton.setOnClickListener {
            cameraService?.switchCamera()
            Toast.makeText(this, "Switching camera...", Toast.LENGTH_SHORT).show()
            
            // Update UI after short delay
            uiScope.launch {
                delay(500)
                updateUI()
            }
        }
        
        binding.toggleFlashlightButton.setOnClickListener {
            cameraService?.toggleFlashlight()
            
            // Update UI after short delay
            uiScope.launch {
                delay(200)
                updateUI()
            }
        }
    }

    /**
     * Check and request necessary permissions
     */
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        
        // Add notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val missingPermissions = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            requestBatteryOptimizationExemption()
        }
    }

    /**
     * Request battery optimization exemption for 24/7 operation
     */
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName
            
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder(this)
                    .setTitle("Battery Optimization")
                    .setMessage("For reliable 24/7 operation, please disable battery optimization for IP_Cam.")
                    .setPositiveButton("Settings") { _, _ ->
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to open battery optimization settings", e)
                        }
                    }
                    .setNegativeButton("Skip", null)
                    .show()
            }
        }
    }

    /**
     * Bind to CameraService
     */
    private fun bindCameraService() {
        val intent = Intent(this, CameraService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * Start CameraService
     */
    private fun startCameraService() {
        val intent = Intent(this, CameraService::class.java).apply {
            action = CameraService.ACTION_START_SERVICE
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        Toast.makeText(this, "Starting streaming...", Toast.LENGTH_SHORT).show()
        
        // Update UI after short delay
        uiScope.launch {
            delay(1000)
            updateUI()
        }
    }

    /**
     * Stop CameraService
     */
    private fun stopCameraService() {
        val intent = Intent(this, CameraService::class.java).apply {
            action = CameraService.ACTION_STOP_SERVICE
        }
        startService(intent)
        
        Toast.makeText(this, "Stopping streaming...", Toast.LENGTH_SHORT).show()
        
        // Update UI after short delay
        uiScope.launch {
            delay(500)
            updateUI()
        }
    }

    /**
     * Start periodic status updates
     */
    private fun startStatusUpdates() {
        statusUpdateJob?.cancel()
        statusUpdateJob = uiScope.launch {
            while (isActive) {
                updateUI()
                delay(2000) // Update every 2 seconds
            }
        }
    }

    /**
     * Update UI with current service status
     */
    private fun updateUI() {
        val service = cameraService ?: return
        
        // Update service status
        val isRunning = service.isServiceRunning()
        binding.serverStatusText.text = if (isRunning) {
            getString(R.string.service_running)
        } else {
            getString(R.string.service_stopped)
        }
        
        binding.serverStatusText.setTextColor(
            if (isRunning) {
                ContextCompat.getColor(this, R.color.green)
            } else {
                ContextCompat.getColor(this, R.color.red)
            }
        )
        
        // Update start/stop button
        binding.startStopButton.text = if (isRunning) {
            getString(R.string.stop_service)
        } else {
            getString(R.string.start_service)
        }
        
        // Update server URL
        binding.serverUrlText.text = if (isRunning) {
            service.getServerUrl()
        } else {
            "-"
        }
        
        // Update connections count
        binding.connectionsText.text = service.getActiveConnections().toString()
        
        // Update camera type
        val config = service.getConfig()
        binding.cameraText.text = if (config.cameraType == "back") {
            getString(R.string.back_camera)
        } else {
            getString(R.string.front_camera)
        }
        
        // Enable/disable camera controls based on service status
        binding.switchCameraButton.isEnabled = isRunning
        binding.toggleFlashlightButton.isEnabled = isRunning && config.cameraType == "back"
    }
}
