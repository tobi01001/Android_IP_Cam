package com.example.androidipcam

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
import com.example.androidipcam.databinding.ActivityMainBinding

/**
 * MainActivity - Main UI for IP_Cam Application
 * 
 * Responsibilities:
 * - Request camera and notification permissions
 * - Bind to CameraService
 * - Display camera preview via CameraX Preview use case (managed by CameraService)
 * - Receive immediate state change notifications via StateChangeListener callback
 * - Provide controls for start/stop, camera switch, flashlight
 * - Display real-time status (server URL, connections, camera)
 * - Request battery optimization exemption
 * 
 * Note on Single Source of Truth Architecture:
 * - MainActivity does NOT access the camera directly
 * - CameraService is the ONLY component that manages camera lifecycle
 * - MainActivity receives updates via StateChangeListener callbacks (REQ-SST-004)
 * - Preview is rendered via CameraX Preview use case for optimal performance
 * - All camera operations (start, stop, switch) go through CameraService
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    
    // Service binding
    private var cameraService: CameraService? = null
    private var serviceBound = false
    
    // Cache last UI state to avoid unnecessary updates
    private var lastIsRunning: Boolean? = null
    private var lastServerUrl: String? = null
    private var lastConnections: Int? = null
    private var lastCameraType: String? = null
    
    // State change listener for immediate updates
    private val stateChangeListener = object : CameraService.StateChangeListener {
        override fun onServiceStateChanged(isRunning: Boolean) {
            runOnUiThread {
                updateServiceState(isRunning)
            }
        }
        
        override fun onCameraChanged(cameraType: String) {
            runOnUiThread {
                updateCameraType(cameraType)
            }
        }
        
        override fun onConfigChanged(config: StreamingConfig) {
            runOnUiThread {
                updateConfig(config)
            }
        }
        
        override fun onConnectionsChanged(count: Int) {
            runOnUiThread {
                updateConnections(count)
            }
        }
    }
    
    // Service connection
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CameraService.LocalBinder
            cameraService = binder.getService()
            serviceBound = true
            Log.d(TAG, "Service connected")
            
            // Set preview surface provider
            cameraService?.setPreviewSurfaceProvider(binding.previewView.surfaceProvider)
            
            // Register state change listener for immediate updates
            cameraService?.addStateChangeListener(stateChangeListener)
            
            // Initial UI update
            updateUI()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            cameraService?.removeStateChangeListener(stateChangeListener)
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
        
        // Remove state change listener
        cameraService?.removeStateChangeListener(stateChangeListener)
        
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
        }
        
        binding.toggleFlashlightButton.setOnClickListener {
            cameraService?.toggleFlashlight()
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
    }

    /**
     * Update UI with current service status (full update)
     * Only updates changed values to prevent disrupting user interaction (e.g., text selection)
     */
    private fun updateUI() {
        val service = cameraService ?: return
        
        // Get current state
        val isRunning = service.isServiceRunning()
        val serverUrl = if (isRunning) service.getServerUrl() else "-"
        val connections = service.getActiveConnections()
        val config = service.getConfig()
        val cameraType = config.cameraType
        
        // Update all components
        updateServiceState(isRunning, serverUrl)
        updateConnections(connections)
        updateCameraType(cameraType)
        updateButtonStates(isRunning, cameraType)
    }
    
    /**
     * Update service state UI components
     */
    private fun updateServiceState(isRunning: Boolean, serverUrl: String? = null) {
        // Update service status only if changed
        if (lastIsRunning != isRunning) {
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
            
            lastIsRunning = isRunning
        }
        
        // Update server URL if provided and changed
        val url = serverUrl ?: (if (isRunning) cameraService?.getServerUrl() else "-") ?: "-"
        if (lastServerUrl != url) {
            binding.serverUrlText.text = url
            lastServerUrl = url
        }
        
        // Update button states
        val cameraType = cameraService?.getConfig()?.cameraType ?: "back"
        updateButtonStates(isRunning, cameraType)
    }
    
    /**
     * Update connections count display
     */
    private fun updateConnections(connections: Int) {
        if (lastConnections != connections) {
            binding.connectionsText.text = connections.toString()
            lastConnections = connections
        }
    }
    
    /**
     * Update camera type display
     */
    private fun updateCameraType(cameraType: String) {
        if (lastCameraType != cameraType) {
            binding.cameraText.text = if (cameraType == "back") {
                getString(R.string.back_camera)
            } else {
                getString(R.string.front_camera)
            }
            lastCameraType = cameraType
            
            // Update button states
            val isRunning = cameraService?.isServiceRunning() ?: false
            updateButtonStates(isRunning, cameraType)
        }
    }
    
    /**
     * Update configuration-dependent UI
     */
    private fun updateConfig(config: StreamingConfig) {
        updateCameraType(config.cameraType)
    }
    
    /**
     * Enable/disable camera controls based on service status
     */
    private fun updateButtonStates(isRunning: Boolean, cameraType: String) {
        binding.switchCameraButton.isEnabled = isRunning
        binding.toggleFlashlightButton.isEnabled = isRunning && cameraType == "back"
    }
}
